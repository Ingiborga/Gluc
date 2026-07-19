package com.test;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DataHelp {
    public static String get_today_date(){
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        return today;
    }
    public static String current_time(){
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        return sdf.format(new Date());
    }
}
