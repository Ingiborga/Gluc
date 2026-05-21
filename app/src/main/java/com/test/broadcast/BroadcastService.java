package com.test.broadcast;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

public class BroadcastService extends Service {
    private static final String TAG = "BroadcastService";
    private com.test.broadcast.AidexBroadcastReceiver receiver;

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "BroadcastService CREATED");

        receiver = new com.test.broadcast.AidexBroadcastReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction("com.librelink.app.ThirdPartyIntegration.GLUCOSE_READING");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(receiver, filter);
        }
        Log.d(TAG, "BroadcastService registered for LibreLink");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (receiver != null) {
            unregisterReceiver(receiver);
        }
        Log.d(TAG, "BroadcastService stopped");
    }
}