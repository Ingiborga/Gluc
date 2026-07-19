package com.test.db;
import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.util.Log;

import java.sql.Date;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DbTools {

    static DBHelper dbHelper;
    public static void init(DBHelper helper) {
        dbHelper = helper;
    }
    public static void add_data(float glucoseValue, long timestamp){
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        // Проверяем, есть ли уже запись
        Cursor cursor = db.rawQuery(
                "SELECT COUNT(*) FROM glucose_values WHERE timestamp = ?",
                new String[]{String.valueOf(timestamp)}
        );
        cursor.moveToFirst();
        int count = cursor.getInt(0);
        cursor.close();

        ContentValues cv = new ContentValues();
        cv.put("glucose", glucoseValue);
        cv.put("timestamp", timestamp);

        long rowID;
        if (count > 0) {
            ;
        } else {
            rowID = db.insert("glucose_values", null, cv);
            Log.d("Glucose_db", "row inserted, ID = " + rowID);
        }

        db.close();
    }
    public static Map<String, List<?>> get_data(String fromDate, String toDate){
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Map<String, List<?>> result = new HashMap<>();

        if (dbHelper == null) {
            Log.e("DbTools", "dbHelper is NULL!");
            return result;
        }

        ArrayList<Float> glucose_values = new ArrayList<>();
        ArrayList<String> date_values = new ArrayList<>();

        try {
            Log.d("DbTools", "Query: SELECT * FROM glucose_values WHERE DATE(timestamp) BETWEEN '" + fromDate + "' AND '" + toDate + "'");
            String query = "SELECT * FROM glucose_values " +
                    "WHERE datetime(timestamp / 1000, 'unixepoch') BETWEEN ? AND ? " +
                    "ORDER BY timestamp ASC";
            // Добавляем время к датам
            String fromDateTime = fromDate + " 00:00:00";
            String toDateTime = toDate + " 23:59:59";

            Log.d("DbTools", "Query: " + query);
            Log.d("DbTools", "From: " + fromDateTime + ", To: " + toDateTime);
            Cursor cursor = db.rawQuery(query, new String[]{fromDateTime, toDateTime});

            if (cursor.moveToFirst()) {
                int glucoseIndex = cursor.getColumnIndex("glucose");
                int timestampIndex = cursor.getColumnIndex("timestamp");
                Log.d("DbTools", "glucoseIndex: " + glucoseIndex + ", timestampIndex: " + timestampIndex);

                do {
                    if (glucoseIndex >= 0 && timestampIndex >= 0) {
                        float glucose = cursor.getFloat(glucoseIndex);
                        float rounded = Math.round(glucose * 100) / 100.0f;
                        glucose_values.add(rounded);                        long timestampLong = cursor.getLong(timestampIndex);

                        // Конвертируем timestamp в строку
                        String timestampStr = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                                .format(new Date(timestampLong));

                        glucose_values.add(glucose);
                        date_values.add(timestampStr);

                        Log.d("DbTools", "Found: glucose=" + glucose + ", timestamp=" + timestampStr);
                    }
                } while (cursor.moveToNext());
            }else {
                Log.d("DbTools", "Cursor is EMPTY! No data found.");
            }
            cursor.close();
        } catch (SQLiteException e) {
            Log.e("DbTools", "Ошибка БД: " + e.getMessage());
        } finally {
            db.close();
        }

        result.put("glucose", glucose_values);
        result.put("dates", date_values);
        Log.d("DbTools", "Returning " + glucose_values.size() + " records");

        return result;
    }
    public static float current_glucose(){
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        if (dbHelper == null) return -1;
        Cursor cursor = db.rawQuery("SELECT glucose FROM glucose_values ORDER BY id DESC LIMIT 1;",null);
        float glucose = -1;
        if (cursor.moveToFirst()) {
            glucose = cursor.getFloat(cursor.getColumnIndexOrThrow("glucose"));
        }
        cursor.close();
        db.close();
        return glucose;
    }
}
