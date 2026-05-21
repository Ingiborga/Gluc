package com.test.broadcast;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.test.ble.BleDataCallback;

public class AidexBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = "LibreReceiver";
    private static BleDataCallback callback;

    private static final String ACTION_GLUCOSE = "com.librelink.app.ThirdPartyIntegration.GLUCOSE_READING";

    public static void setCallback(BleDataCallback cb) {
        callback = cb;
        Log.d(TAG, "Callback set");
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        if (ACTION_GLUCOSE.equals(action)) {
            Bundle bundle = intent.getExtras();
            if (bundle == null) {
                Log.e(TAG, "Bundle is null");
                if (callback != null) {
                    callback.onError("No data in broadcast");
                }
                return;
            }

            double glucoseMgDl = bundle.getDouble("glucose", -1);
            long timestamp = bundle.getLong("timestamp", System.currentTimeMillis());

            Log.d(TAG, "Glucose (mg/dL): " + glucoseMgDl);

            if (glucoseMgDl > 0 && callback != null) {
                float glucoseMmolL = (float) (glucoseMgDl / 18.0);
                callback.onGlucoseDataReceived(glucoseMmolL, timestamp);
                callback.onDeviceStatusChanged(String.format("Libre: %.1f mmol/L", glucoseMmolL));
            } else if (glucoseMgDl <= 0 && callback != null) {
                callback.onError("Invalid glucose value: " + glucoseMgDl);
            }
        }
    }
}