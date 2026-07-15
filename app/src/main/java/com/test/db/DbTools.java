package com.test.db;
import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DbTools {

    static DBHelper dbHelper;
    public static void init(DBHelper helper) {
        dbHelper = helper;
    }
    public static void add_data(float glucoseValue, long timestamp){
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        Log.d("Glucose_db", "--- Insert in glucose_data: ---");
        cv.put("glucose", glucoseValue);
        cv.put("day", timestamp);
        long rowID = db.insert("glucose_values", null, cv);
        Log.d("Glucose_db", "row inserted, ID = " + rowID);
        dbHelper.close();
    }
    public static Map<String, List<?>> get_data(String fromDate, String toDate){
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        if (dbHelper == null) return new HashMap<>();
        Cursor cursor = db.rawQuery(
                "SELECT * FROM glucose_values WHERE timestamp BETWEEN ? AND ?",
                new String[]{fromDate, toDate}
        );
        ArrayList<Float> glucose_values = new ArrayList<>();
        ArrayList<String> date_values= new ArrayList<>();

        while (cursor.moveToNext()) {
            int glucoseIndex = cursor.getColumnIndex("glucose");
            int timestampIndex = cursor.getColumnIndex("timestamp");

            if (glucoseIndex >= 0 && timestampIndex >= 0) {
                float glucose = cursor.getFloat(glucoseIndex);
                String timestamp = cursor.getString(timestampIndex);

                glucose_values.add(glucose);
                date_values.add(timestamp);
            }
        }
        cursor.close();
        Map<String, List<?>> result = new HashMap<>();
        result.put("glucose", glucose_values);
        result.put("dates", date_values);
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
