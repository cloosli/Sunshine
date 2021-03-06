/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.loosli.christian.sunshine.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.graphics.Palette;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.TextUtils;
import android.util.Log;
import android.view.SurfaceHolder;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;

import java.lang.ref.WeakReference;
import java.text.DateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Analog watch face with a ticking second hand. In ambient mode, the second hand isn't
 * shown. On devices with low-bit ambient mode, the hands are drawn without anti-aliasing in ambient
 * mode. The watch face is drawn with less contrast in mute mode.
 */
public class SunshineAnalogWatchFace extends CanvasWatchFaceService {

    private static final String TAG = SunshineAnalogWatchFace.class.getSimpleName();
    /*
     * Update rate in milliseconds for interactive mode. We update once a second to advance the
     * second hand.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private class Engine extends CanvasWatchFaceService.Engine
            implements GoogleApiClient.ConnectionCallbacks,
            GoogleApiClient.OnConnectionFailedListener,
            DataApi.DataListener {

        private static final String REQ_WEATHER_PATH = "/weather-req";
        private static final String REQ_PATH = "/weather";
        private static final String KEY_PACKAGE = "com.loosli.christian.sunshine.app.wearable.key.";
        private static final String KEY_WEATHER_ID = KEY_PACKAGE + "weather_id";
        private static final String KEY_CONDITION_ID = KEY_PACKAGE + "condition_id";
        private static final String KEY_TEMP_MAX = KEY_PACKAGE + "temp_max";
        private static final String KEY_TEMP_MIN = KEY_PACKAGE + "temp_min";
        private static final String KEY_LOCATION = KEY_PACKAGE + "location";

        private Typeface WATCH_TEXT_TYPEFACE = Typeface.create(Typeface.DEFAULT, Typeface.BOLD);
        private Typeface WATCH_DATE_TEXT_TYPEFACE = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL);

        private static final float HOUR_STROKE_WIDTH = 5f;
        private static final float MINUTE_STROKE_WIDTH = 3f;
        private static final float SECOND_TICK_STROKE_WIDTH = 2f;

        private static final float CENTER_GAP_AND_CIRCLE_RADIUS = 4f;

        private static final int SHADOW_RADIUS = 6;

        private Calendar mCalendar;
        private boolean mRegisteredTimeZoneReceiver = false;
        private boolean mMuteMode;

        private float mCenterX;
        private float mCenterY;

        private float mSecondHandLength;
        private float sMinuteHandLength;
        private float sHourHandLength;

        /* Colors for all hands (hour, minute, seconds, ticks) based on photo loaded. */
        private int mWatchHandColor;
        private int mWatchHandHighlightColor;
        private int mWatchHandShadowColor;

        private Paint mHourPaint;
        private Paint mMinutePaint;
        private Paint mSecondPaint;
        private Paint mTickAndCirclePaint;

        private Paint mForecastHighPaint;
        private Paint mForecastLowPaint;
        private Paint mDatePaint;

        private Paint mBackgroundPaint;
        private Paint mBackgroundColorPaint;
        private Paint mBackgroundWhitePaint;
        private Paint mWeatherIconPaint;
        private Bitmap mBackgroundBitmap;
        private Bitmap mGrayBackgroundBitmap;
        private Bitmap mLogoBitmap;

        private boolean mAmbient;
        private boolean mLowBitAmbient;
        private boolean mBurnInProtection;

        private int mWeatherId;
        private int mWeatherConditionId;
        private double mWeatherMaxTemp;
        private double mWeatherMinTemp;
        private String mWeatherLocation;

        // bitmaps
        Bitmap mBitmapStatus;
        Bitmap mBitmapClear;
        Bitmap mBitmapClouds;
        Bitmap mBitmapFog;
        Bitmap mBitmapLightClouds;
        Bitmap mBitmapLightRain;
        Bitmap mBitmapRain;
        Bitmap mBitmapSnow;
        Bitmap mBitmapStorm;

        private final Rect mPeekCardBounds = new Rect();

        private GoogleApiClient mGoogleApiClient;

        private final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };

        /* Handler to update the time once a second in interactive mode. */
        private final Handler mUpdateTimeHandler = new EngineHandler(this);


        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(SunshineAnalogWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .build());

            Resources resources = SunshineAnalogWatchFace.this.getResources();

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(Color.BLACK);
            mBackgroundBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.bg);
            mLogoBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.ic_logo);

            mBackgroundColorPaint = new Paint();
            mBackgroundColorPaint.setColor(getResources().getColor(R.color.primary));

            mBackgroundWhitePaint = new Paint();
            mBackgroundWhitePaint.setColor(Color.WHITE);

            /* Set defaults for colors */
            mWatchHandColor = Color.WHITE;
            mWatchHandHighlightColor = Color.RED;
            mWatchHandShadowColor = Color.BLACK;

            mWeatherIconPaint = new Paint();
            mWeatherIconPaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);

            mHourPaint = new Paint();
            mHourPaint.setColor(mWatchHandColor);
            mHourPaint.setStrokeWidth(HOUR_STROKE_WIDTH);
            mHourPaint.setAntiAlias(true);
            mHourPaint.setStrokeCap(Paint.Cap.ROUND);
            mHourPaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);

            mMinutePaint = new Paint();
            mMinutePaint.setColor(mWatchHandColor);
            mMinutePaint.setStrokeWidth(MINUTE_STROKE_WIDTH);
            mMinutePaint.setAntiAlias(true);
            mMinutePaint.setStrokeCap(Paint.Cap.ROUND);
            mMinutePaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);

            mSecondPaint = new Paint();
            mSecondPaint.setColor(mWatchHandHighlightColor);
            mSecondPaint.setStrokeWidth(SECOND_TICK_STROKE_WIDTH);
            mSecondPaint.setAntiAlias(true);
            mSecondPaint.setStrokeCap(Paint.Cap.ROUND);
            mSecondPaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);

            mTickAndCirclePaint = new Paint();
            mTickAndCirclePaint.setColor(mWatchHandColor);
            mTickAndCirclePaint.setStrokeWidth(SECOND_TICK_STROKE_WIDTH);
            mTickAndCirclePaint.setAntiAlias(true);
            mTickAndCirclePaint.setStyle(Paint.Style.STROKE);
            mTickAndCirclePaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);

            mForecastHighPaint = new Paint();
            mForecastHighPaint.setColor(mWatchHandColor);
            mForecastHighPaint.setAntiAlias(true);
            mForecastHighPaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);
            mForecastHighPaint.setTypeface(WATCH_TEXT_TYPEFACE);
            mForecastHighPaint.setTextSize(getResources().getDimension(R.dimen.text_size));

            mForecastLowPaint = new Paint();
            mForecastLowPaint.setColor(getResources().getColor(R.color.forecast_low));
            mForecastLowPaint.setAntiAlias(true);
            mForecastLowPaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);
            mForecastLowPaint.setTypeface(WATCH_TEXT_TYPEFACE);
            mForecastLowPaint.setTextSize(getResources().getDimension(R.dimen.text_size));

            mDatePaint = new Paint();
            mDatePaint.setColor(getResources().getColor(R.color.text_date));
            mDatePaint.setAntiAlias(true);
            mDatePaint.setTypeface(WATCH_DATE_TEXT_TYPEFACE);
            mDatePaint.setTextSize(getResources().getDimension(R.dimen.date_text_size));

            mCalendar = Calendar.getInstance();

            initializeBitmaps(resources);

            mGoogleApiClient = new GoogleApiClient.Builder(SunshineAnalogWatchFace.this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(Wearable.API)
                    .build();
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
            mBurnInProtection = properties.getBoolean(PROPERTY_BURN_IN_PROTECTION, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            mAmbient = inAmbientMode;

            updateWatchHandStyle();

            /* Check and trigger whether or not timer should be running (only in active mode). */
            updateTimer();
        }

        private void updateWatchHandStyle() {
            if (mAmbient) {
                mHourPaint.setColor(Color.WHITE);
                mMinutePaint.setColor(Color.WHITE);
                mSecondPaint.setColor(Color.WHITE);
                mTickAndCirclePaint.setColor(Color.WHITE);

                mHourPaint.setAntiAlias(false);
                mMinutePaint.setAntiAlias(false);
                mSecondPaint.setAntiAlias(false);
                mTickAndCirclePaint.setAntiAlias(false);
                mForecastHighPaint.setAntiAlias(false);
                mForecastLowPaint.setAntiAlias(false);

                mHourPaint.clearShadowLayer();
                mMinutePaint.clearShadowLayer();
                mSecondPaint.clearShadowLayer();
                mTickAndCirclePaint.clearShadowLayer();
            } else {
                mHourPaint.setColor(mWatchHandColor);
                mMinutePaint.setColor(mWatchHandColor);
                mSecondPaint.setColor(mWatchHandHighlightColor);
                mTickAndCirclePaint.setColor(mWatchHandColor);

                mHourPaint.setAntiAlias(true);
                mMinutePaint.setAntiAlias(true);
                mSecondPaint.setAntiAlias(true);
                mTickAndCirclePaint.setAntiAlias(true);
                mForecastHighPaint.setAntiAlias(true);
                mForecastLowPaint.setAntiAlias(true);

                mHourPaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);
                mMinutePaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);
                mSecondPaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);
                mTickAndCirclePaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);
            }
        }

        @Override
        public void onInterruptionFilterChanged(int interruptionFilter) {
            super.onInterruptionFilterChanged(interruptionFilter);
            boolean inMuteMode = (interruptionFilter == WatchFaceService.INTERRUPTION_FILTER_NONE);

            /* Dim display in mute mode. */
            if (mMuteMode != inMuteMode) {
                mMuteMode = inMuteMode;
                mHourPaint.setAlpha(inMuteMode ? 100 : 255);
                mMinutePaint.setAlpha(inMuteMode ? 100 : 255);
                mSecondPaint.setAlpha(inMuteMode ? 80 : 255);
                invalidate();
            }
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);

            /*
             * Find the coordinates of the center point on the screen, and ignore the window
             * insets, so that, on round watches with a "chin", the watch face is centered on the
             * entire screen, not just the usable portion.
             */
            mCenterX = width / 2f;
            mCenterY = height / 2f;

            /*
             * Calculate lengths of different hands based on watch screen size.
             */
            mSecondHandLength = (float) (mCenterX * 0.875);
            sMinuteHandLength = (float) (mCenterX * 0.75);
            sHourHandLength = (float) (mCenterX * 0.5);


            /* Scale loaded background image (more efficient) if surface dimensions change. */
            float scale = ((float) width) / (float) mBackgroundBitmap.getWidth();

            mBackgroundBitmap = Bitmap.createScaledBitmap(mBackgroundBitmap,
                    (int) (mBackgroundBitmap.getWidth() * scale),
                    (int) (mBackgroundBitmap.getHeight() * scale), true);

            /*
             * Create a gray version of the image only if it will look nice on the device in
             * ambient mode. That means we don't want devices that support burn-in
             * protection (slight movements in pixels, not great for images going all the way to
             * edges) and low ambient mode (degrades image quality).
             *
             * Also, if your watch face will know about all images ahead of time (users aren't
             * selecting their own photos for the watch face), it will be more
             * efficient to create a black/white version (png, etc.) and load that when you need it.
             */
            if (!mBurnInProtection && !mLowBitAmbient) {
                initGrayBackgroundBitmap();
            }
        }

        private void initGrayBackgroundBitmap() {
            mGrayBackgroundBitmap = Bitmap.createBitmap(
                    mBackgroundBitmap.getWidth(),
                    mBackgroundBitmap.getHeight(),
                    Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(mGrayBackgroundBitmap);
            Paint grayPaint = new Paint();
            ColorMatrix colorMatrix = new ColorMatrix();
            colorMatrix.setSaturation(0);
            ColorMatrixColorFilter filter = new ColorMatrixColorFilter(colorMatrix);
            grayPaint.setColorFilter(filter);
            canvas.drawBitmap(mBackgroundBitmap, 0, 0, grayPaint);
        }

//        /**
//         * Captures tap event (and tap type). The {@link WatchFaceService#TAP_TYPE_TAP} case can be
//         * used for implementing specific logic to handle the gesture.
//         */
//        @Override
//        public void onTapCommand(int tapType, int x, int y, long eventTime) {
//            switch (tapType) {
//                case TAP_TYPE_TOUCH:
//                    // The user has started touching the screen.
//                    break;
//                case TAP_TYPE_TOUCH_CANCEL:
//                    // The user has started a different gesture or otherwise cancelled the tap.
//                    break;
//                case TAP_TYPE_TAP:
//                    // The user has completed the tap gesture.
//                    // TODO: Add code to handle the tap gesture.
//                    Toast.makeText(getApplicationContext(), R.string.message, Toast.LENGTH_SHORT).show();
//                    requestWeatherUpdate();
//                    break;
//            }
//            invalidate();
//        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);

            if (mAmbient && (mLowBitAmbient || mBurnInProtection)) {
                canvas.drawColor(Color.BLACK);
            } else if (mAmbient) {
                canvas.drawRect(0, 0, mCenterX * 2, mCenterY * 2f, mBackgroundPaint);
            } else {
                canvas.drawRect(0, 0, mCenterX * 2, mCenterY * 1.7f, mBackgroundColorPaint);
                canvas.drawRect(0, mCenterY * 1.3f, mCenterX * 2, mCenterY * 2f, mBackgroundWhitePaint);
                canvas.drawBitmap(mLogoBitmap, mCenterX - (104 / 2), mCenterY * 0.75f, mBackgroundPaint);
            }

            /* weather data */
            if (mWeatherId != 0) {
                String highString = String.format(getString(R.string.format_temperature), mWeatherMaxTemp);
                String lowString = String.format(getString(R.string.format_temperature), mWeatherMinTemp);
                Bitmap icon = getBitmapForWeatherCondition(mWeatherConditionId);

                float totalWidth = icon.getWidth()
                        + mForecastHighPaint.measureText(highString, 0, highString.length())
                        + mForecastLowPaint.measureText(lowString, 0, lowString.length())
                        + 10 + 10;

                float startXPos = mCenterY - (totalWidth / 2);
                float startYPos = mCenterY * 0.5f;

                int xPos = (int) startXPos;
                int yPos = (int) (startYPos - (icon.getHeight() / 2));
                if (!mAmbient) {
                    canvas.drawBitmap(icon, xPos, yPos, mWeatherIconPaint);
                }

                xPos += icon.getWidth() + 10;
                yPos = (int) (startYPos - ((mForecastHighPaint.descent() + mForecastHighPaint.ascent()) / 2));
                canvas.drawText(highString, xPos, yPos, mForecastHighPaint);

                xPos += mForecastHighPaint.measureText(highString, 0, highString.length()) + 10;
                canvas.drawText(lowString, xPos, yPos, mForecastLowPaint);

                String dateText = DateFormat.getDateInstance(DateFormat.LONG).format(mCalendar.getTime());
                int width = (int) mDatePaint.measureText(dateText, 0, dateText.length());
                canvas.drawText(dateText, mCenterX - (width / 2), mCenterY * 1.5f, mDatePaint);

                if (!TextUtils.isEmpty(mWeatherLocation)) {
                    width = (int) mDatePaint.measureText(mWeatherLocation, 0, mWeatherLocation.length());
                    canvas.drawText(mWeatherLocation, mCenterX - (width / 2), mCenterY * 1.7f, mDatePaint);
                }
            }

            /*
             * Draw ticks. Usually you will want to bake this directly into the photo, but in
             * cases where you want to allow users to select their own photos, this dynamically
             * creates them on top of the photo.
             */
            float innerTickRadius = mCenterX - 10;
            float outerTickRadius = mCenterX;
            for (int tickIndex = 0; tickIndex < 12; tickIndex++) {
                float tickRot = (float) (tickIndex * Math.PI * 2 / 12);
                float innerX = (float) Math.sin(tickRot) * innerTickRadius;
                float innerY = (float) -Math.cos(tickRot) * innerTickRadius;
                float outerX = (float) Math.sin(tickRot) * outerTickRadius;
                float outerY = (float) -Math.cos(tickRot) * outerTickRadius;
                canvas.drawLine(mCenterX + innerX, mCenterY + innerY,
                        mCenterX + outerX, mCenterY + outerY, mTickAndCirclePaint);
            }

            /*
             * These calculations reflect the rotation in degrees per unit of time, e.g.,
             * 360 / 60 = 6 and 360 / 12 = 30.
             */
            final float seconds =
                    (mCalendar.get(Calendar.SECOND) + mCalendar.get(Calendar.MILLISECOND) / 1000f);
            final float secondsRotation = seconds * 6f;

            final float minutesRotation = mCalendar.get(Calendar.MINUTE) * 6f;

            final float hourHandOffset = mCalendar.get(Calendar.MINUTE) / 2f;
            final float hoursRotation = (mCalendar.get(Calendar.HOUR) * 30) + hourHandOffset;

            /*
             * Save the canvas state before we can begin to rotate it.
             */
            canvas.save();

            canvas.rotate(hoursRotation, mCenterX, mCenterY);
            canvas.drawLine(
                    mCenterX,
                    mCenterY - CENTER_GAP_AND_CIRCLE_RADIUS,
                    mCenterX,
                    mCenterY - sHourHandLength,
                    mHourPaint);

            canvas.rotate(minutesRotation - hoursRotation, mCenterX, mCenterY);
            canvas.drawLine(
                    mCenterX,
                    mCenterY - CENTER_GAP_AND_CIRCLE_RADIUS,
                    mCenterX,
                    mCenterY - sMinuteHandLength,
                    mMinutePaint);

            /*
             * Ensure the "seconds" hand is drawn only when we are in interactive mode.
             * Otherwise, we only update the watch face once a minute.
             */
            if (!mAmbient) {
                canvas.rotate(secondsRotation - minutesRotation, mCenterX, mCenterY);
                canvas.drawLine(
                        mCenterX,
                        mCenterY - CENTER_GAP_AND_CIRCLE_RADIUS,
                        mCenterX,
                        mCenterY - mSecondHandLength,
                        mSecondPaint);

            }
            canvas.drawCircle(
                    mCenterX,
                    mCenterY,
                    CENTER_GAP_AND_CIRCLE_RADIUS,
                    mTickAndCirclePaint);

            /* Restore the canvas' original orientation. */
            canvas.restore();

            /* Draw rectangle behind peek card in ambient mode to improve readability. */
            if (mAmbient) {
                canvas.drawRect(mPeekCardBounds, mBackgroundPaint);
            }
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                mGoogleApiClient.connect();

                registerReceiver();
                /* Update time zone in case it changed while we weren't visible. */
                mCalendar.setTimeZone(TimeZone.getDefault());

                invalidate();
            } else {
                unregisterReceiver();
                if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
                    Wearable.DataApi.removeListener(mGoogleApiClient, this);
                    mGoogleApiClient.disconnect();
                }
            }

            /* Check and trigger whether or not timer should be running (only in active mode). */
            updateTimer();
        }

        @Override
        public void onPeekCardPositionUpdate(Rect rect) {
            super.onPeekCardPositionUpdate(rect);
            mPeekCardBounds.set(rect);
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            SunshineAnalogWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            SunshineAnalogWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        /**
         * Starts/stops the {@link #mUpdateTimeHandler} timer based on the state of the watch face.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer
         * should only run in active mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !mAmbient;
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }

        @Override
        public void onConnected(Bundle connectionHint) {
            Log.d(TAG, "onConnected: " + connectionHint);
            Wearable.DataApi.addListener(mGoogleApiClient, Engine.this);
            requestWeatherUpdate();
        }

        @Override  // GoogleApiClient.ConnectionCallbacks
        public void onConnectionSuspended(int cause) {
            Log.d(TAG, "onConnectionSuspended: " + cause);
        }

        @Override  // GoogleApiClient.OnConnectionFailedListener
        public void onConnectionFailed(ConnectionResult result) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
            }
            Log.d(TAG, "onConnectionFailed: " + result);
        }

        @Override
        public void onDataChanged(DataEventBuffer dataEvents) {
            Log.d(TAG, "onDataChanged");

            for (DataEvent dataEvent : dataEvents) {
                if (dataEvent.getType() != DataEvent.TYPE_CHANGED) {
                    continue;
                }

                DataItem dataItem = dataEvent.getDataItem();
                if (!dataItem.getUri().getPath().equals(REQ_PATH)) {
                    continue;
                }

                DataMap dataMap = DataMapItem.fromDataItem(dataItem).getDataMap();
                mWeatherId = dataMap.getInt(KEY_WEATHER_ID);
                mWeatherConditionId = dataMap.getInt(KEY_CONDITION_ID);
                mWeatherMaxTemp = dataMap.getDouble(KEY_TEMP_MAX);
                mWeatherMinTemp = dataMap.getDouble(KEY_TEMP_MIN);
                mWeatherLocation = dataMap.getString(KEY_LOCATION);

                /* Extract colors from background image to improve watchface style. */
                Palette.from(getBitmapForWeatherCondition(mWeatherConditionId)).generate(new Palette.PaletteAsyncListener() {
                    @Override
                    public void onGenerated(Palette palette) {
                        if (palette != null) {
                            mWatchHandHighlightColor = palette.getVibrantColor(Color.RED);
                            mWatchHandColor = palette.getLightVibrantColor(Color.WHITE);
                            mWatchHandShadowColor = palette.getDarkMutedColor(Color.BLACK);
                            updateWatchHandStyle();
                        }
                    }
                });
            }
        }

        private void requestWeatherUpdate() {
            Log.d(TAG, "requestWeatherUpdate through Message API");

            Wearable.NodeApi.getConnectedNodes(mGoogleApiClient)
                    .setResultCallback(new ResultCallback<NodeApi.GetConnectedNodesResult>() {
                        @Override
                        public void onResult(NodeApi.GetConnectedNodesResult getConnectedNodesResult) {
                            final List<Node> nodes = getConnectedNodesResult.getNodes();

                            for (Node node : nodes) {
                                Log.d(TAG, "send message to node: " + node.getDisplayName());
                                Wearable.MessageApi.sendMessage(mGoogleApiClient
                                        , node.getId()
                                        , REQ_WEATHER_PATH
                                        , new byte[0]).setResultCallback(
                                        new ResultCallback<MessageApi.SendMessageResult>() {
                                            @Override
                                            public void onResult(MessageApi.SendMessageResult sendMessageResult) {
                                                if (sendMessageResult.getStatus().isSuccess()) {
                                                    Log.d(TAG, "Message successfully sent");
                                                } else {
                                                    Log.d(TAG, "Message failed to send");
                                                }
                                            }
                                        }
                                );
                            }
                        }
                    });
        }

        private void initializeBitmaps(Resources resources) {
            mBitmapStatus = BitmapFactory.decodeResource(resources, R.drawable.ic_status);
            mBitmapClear = BitmapFactory.decodeResource(resources, R.drawable.ic_clear);
            mBitmapClouds = BitmapFactory.decodeResource(resources, R.drawable.ic_cloudy);
            mBitmapFog = BitmapFactory.decodeResource(resources, R.drawable.ic_fog);
            mBitmapLightClouds = BitmapFactory.decodeResource(resources, R.drawable.ic_light_clouds);
            mBitmapLightRain = BitmapFactory.decodeResource(resources, R.drawable.ic_light_rain);
            mBitmapRain = BitmapFactory.decodeResource(resources, R.drawable.ic_rain);
            mBitmapSnow = BitmapFactory.decodeResource(resources, R.drawable.ic_snow);
            mBitmapStorm = BitmapFactory.decodeResource(resources, R.drawable.ic_storm);
        }

        private Bitmap getBitmapForWeatherCondition(int weatherId) {
            // Based on weather code data found at:
            // http://bugs.openweathermap.org/projects/api/wiki/Weather_Condition_Codes
            if (weatherId >= 200 && weatherId <= 232) {
                return mBitmapStorm;
            } else if (weatherId >= 300 && weatherId <= 321) {
                return mBitmapLightRain;
            } else if (weatherId >= 500 && weatherId <= 504) {
                return mBitmapRain;
            } else if (weatherId == 511) {
                return mBitmapSnow;
            } else if (weatherId >= 520 && weatherId <= 531) {
                return mBitmapRain;
            } else if (weatherId >= 600 && weatherId <= 622) {
                return mBitmapSnow;
            } else if (weatherId >= 701 && weatherId <= 761) {
                return mBitmapFog;
            } else if (weatherId == 761 || weatherId == 781) {
                return mBitmapStorm;
            } else if (weatherId == 800) {
                return mBitmapClear;
            } else if (weatherId == 801) {
                return mBitmapLightClouds;
            } else if (weatherId >= 802 && weatherId <= 804) {
                return mBitmapClouds;
            }

            // default bitmap
            return mBitmapStatus;
        }
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<SunshineAnalogWatchFace.Engine> mWeakReference;

        public EngineHandler(SunshineAnalogWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            SunshineAnalogWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }
}
