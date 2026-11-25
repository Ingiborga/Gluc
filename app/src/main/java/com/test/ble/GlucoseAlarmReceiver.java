package com.test.ble;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.test.db.DbTools;

public class GlucoseAlarmReceiver extends BroadcastReceiver {

    private static final String TAG = "GlucoseAlarm";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Alarm fired — generating glucose value");

        // ✅ Генерируем новое значение
        float value = GlucoseSimulator.generateNextValue();
        long timestamp = System.currentTimeMillis();

        // ✅ Сохраняем в БД
        DbTools.add_data(value, timestamp);

        // ✅ Уведомляем UI (если приложение открыто)
        GlucoseSimulator.notifyCallback(value, timestamp);

        // ✅ Планируем следующий запуск через 5 минут
        GlucoseSimulator.scheduleNext(context);
    }
}