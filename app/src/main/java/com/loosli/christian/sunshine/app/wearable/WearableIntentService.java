package com.loosli.christian.sunshine.app.wearable;

import android.app.IntentService;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;
import com.loosli.christian.sunshine.app.Utility;
import com.loosli.christian.sunshine.app.data.WeatherContract;

import java.util.Date;

/**
 * Created by ChristianL on 19.10.16.
 */

public class WearableIntentService extends IntentService implements GoogleApiClient.ConnectionCallbacks {

    private static final String TAG = WearableIntentService.class.getSimpleName();
    private static final String[] FORECAST_COLUMNS = {
            WeatherContract.WeatherEntry.TABLE_NAME + "." + WeatherContract.WeatherEntry._ID,
            WeatherContract.WeatherEntry.COLUMN_DATE,
            WeatherContract.WeatherEntry.COLUMN_MAX_TEMP,
            WeatherContract.WeatherEntry.COLUMN_MIN_TEMP,
            WeatherContract.WeatherEntry.COLUMN_WEATHER_ID,
            WeatherContract.LocationEntry.COLUMN_CITY_NAME
    };

    static final int COL_WEATHER_ID = 0;
    static final int COL_WEATHER_DATE = 1;
    static final int COL_WEATHER_MAX_TEMP = 2;
    static final int COL_WEATHER_MIN_TEMP = 3;
    static final int COL_WEATHER_CONDITION_ID = 4;
    static final int COL_CITY_NAME = 5;

    private static final String REQ_PATH = "/weather";
    private static final String KEY_PACKAGE = "com.loosli.christian.sunshine.app.wearable.key.";
    private static final String KEY_WEATHER_ID = KEY_PACKAGE + "weather_id";
    private static final String KEY_CONDITION_ID = KEY_PACKAGE + "condition_id";
    private static final String KEY_TEMP_MAX = KEY_PACKAGE + "temp_max";
    private static final String KEY_TEMP_MIN = KEY_PACKAGE + "temp_min";
    private static final String KEY_LOCATION = KEY_PACKAGE + "location";

    private int mWeatherId;
    private int mWeatherConditionId;
    private double mWeatherMaxTemp;
    private double mWeatherMinTemp;
    private String mWeatherLocation;

    private GoogleApiClient mGoogleApiClient;

    public WearableIntentService() {
        super(WearableIntentService.class.getSimpleName());
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Log.d(TAG, "onHandleIntent");

        String locationSetting = Utility.getPreferredLocation(this);
        Uri weatherForLocationUri = WeatherContract.WeatherEntry.buildWeatherLocationWithDate(locationSetting, System.currentTimeMillis());
        String sortOrder = WeatherContract.WeatherEntry.COLUMN_DATE + " ASC";
        Cursor data = getContentResolver().query(weatherForLocationUri, FORECAST_COLUMNS, null, null, sortOrder);

        if (data == null) {
            return;
        }

        if (!data.moveToFirst()) {
            data.close();
            return;
        }

        mWeatherId = data.getInt(COL_WEATHER_DATE);
        mWeatherConditionId = data.getInt(COL_WEATHER_CONDITION_ID);
        mWeatherMaxTemp = data.getDouble(COL_WEATHER_MAX_TEMP);
        mWeatherMinTemp = data.getDouble(COL_WEATHER_MIN_TEMP);
        mWeatherLocation = data.getString(COL_CITY_NAME);
        data.close();

        if (mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(this)
                    .addApi(Wearable.API)
                    .build();
        }
        if (!mGoogleApiClient.isConnected()) {
            mGoogleApiClient.connect();
        }
    }

    @Override
    public void onDestroy() {
        if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
            mGoogleApiClient.disconnect();
        }

        super.onDestroy();
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Log.d(TAG, "onConnected");

        PutDataMapRequest putDataMapRequest = PutDataMapRequest.create(REQ_PATH);
        putDataMapRequest.setUrgent();

        putDataMapRequest.getDataMap().putInt(KEY_WEATHER_ID, mWeatherId);
        putDataMapRequest.getDataMap().putInt(KEY_CONDITION_ID, mWeatherConditionId);
        putDataMapRequest.getDataMap().putDouble(KEY_TEMP_MAX, mWeatherMaxTemp);
        putDataMapRequest.getDataMap().putDouble(KEY_TEMP_MIN, mWeatherMinTemp);
        putDataMapRequest.getDataMap().putString(KEY_LOCATION, mWeatherLocation);

        Log.d(TAG, "mWeatherId:         " + Integer.toString(mWeatherId));
        Log.d(TAG, "mWatherConditionId: " + Integer.toString(mWeatherConditionId));
        Log.d(TAG, "mMaxTemp:           " + Double.toString(mWeatherMaxTemp));
        Log.d(TAG, "mMinTemp:           " + Double.toString(mWeatherMinTemp));
        Log.d(TAG, "mWeatherLocation:   " + mWeatherLocation);

        putDataMapRequest.getDataMap().putLong("time", System.currentTimeMillis());
        PutDataRequest request = putDataMapRequest.asPutDataRequest();
        Wearable.DataApi.putDataItem(mGoogleApiClient, request)
                .setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
                                       @Override
                                       public void onResult(DataApi.DataItemResult dataItemResult) {
                                           if (dataItemResult.getStatus().isSuccess()) {
                                               Log.d(TAG, "Successfully sent");
                                           } else {
                                               Log.d(TAG, "Failed to send");
                                           }
                                       }
                                   }
                );
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.d(TAG, "onConectionSuspended: " + i);
    }
}
