package com.test.db;
import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import com.test.db.DBHelper;
import android.util.Log;

public class dbCallback {

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
}
