package com.loosli.christian.sunshine.app.wearable;

import android.util.Log;

import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

public class MyWearableListenerService extends WearableListenerService
        implements DataApi.DataListener {
    private static final String TAG = MyWearableListenerService.class.getSimpleName();

    private static final String REQ_WEATHER_PATH = "/weather-req";

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate");

        super.onCreate();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");

        super.onDestroy();
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        Log.d(TAG, "onMessageReceived: " + messageEvent);

        String path = messageEvent.getPath();
        Log.d(TAG, "message path: " + path);
    }
}
