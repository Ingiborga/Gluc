package com.test.broadcast;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;


public class CatchAllReceiver extends BroadcastReceiver {
    private static final String TAG = "CatchAllReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action != null) {
            Log.d(TAG, "=== CAUGHT BROADCAST ===");
            Log.d(TAG, "Action: " + action);
            Log.d(TAG, "Package: " + intent.getPackage());

            // Проверяем, не наше ли это сообщение
            if (action.contains("cgms") || action.contains("glucose") || action.contains("bg")) {
                Log.d(TAG, "*** POTENTIAL GLUCOSE BROADCAST ***");
                if (intent.getExtras() != null) {
                    for (String key : intent.getExtras().keySet()) {
                        Log.d(TAG, "Extra: " + key + " = " + intent.getExtras().get(key));
                    }
                }
            }
        }
    }
}