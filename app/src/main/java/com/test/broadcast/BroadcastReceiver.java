package com.test.broadcast;

import static android.content.Context.MODE_PRIVATE;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;

import com.test.ble.BleDataCallback;

public class BroadcastReceiver extends android.content.BroadcastReceiver {
    private static final String TAG = "LibreReceiver";
    private static BleDataCallback callback;

    private String ACTION_GLUCOSE;

    public static void setCallback(BleDataCallback cb) {
        callback = cb;
        Log.d(TAG, "Callback set");
    }
    private void init_glucometer(Context context){
        SharedPreferences prefs = context.getSharedPreferences("MyPrefs", MODE_PRIVATE);
        String sensorType = prefs.getString("glucometer", "aidex");
        Log.d(TAG, "BroadcastService registered for "+sensorType);

        if (sensorType.equals("ottai")) {
            ACTION_GLUCOSE = "com.librelink.app.ThirdPartyIntegration.GLUCOSE_READING";
        } else {
            ACTION_GLUCOSE = "com.librelink.app.ThirdPartyIntegration.GLUCOSE_READING";
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        init_glucometer(context);
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