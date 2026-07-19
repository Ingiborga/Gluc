package com.test.db;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class DBHelper extends SQLiteOpenHelper {
    public DBHelper(Context context) {
        // конструктор суперкласса
        super(context, "glucose_data", null, 1);
    }
    final String LOG_TAG = "BDLogs";

    @Override
    public void onCreate(SQLiteDatabase db) {
        Log.d(LOG_TAG, "--- onCreate database ---");
        // создаем таблицу с полями
        db.execSQL("CREATE TABLE glucose_values (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "glucose REAL, " +
                "timestamp INTEGER UNIQUE" +
                ");");
    }
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.d(LOG_TAG, "--- onUpgrade database ---");
        db.execSQL("DROP TABLE IF EXISTS glucose_values");
        onCreate(db);
    }
}