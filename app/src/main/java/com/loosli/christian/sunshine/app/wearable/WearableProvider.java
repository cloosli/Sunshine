package com.loosli.christian.sunshine.app.wearable;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.loosli.christian.sunshine.app.sync.SunshineSyncAdapter;

/**
 * Created by ChristianL on 19.10.16.
 */
public class WearableProvider extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (SunshineSyncAdapter.ACTION_DATA_UPDATED.equals(intent.getAction())) {
            context.startService(new Intent(context, WearableIntentService.class));
        }
    }
}
