package com.test;

import android.database.sqlite.SQLiteException;
import android.graphics.Color;
import android.util.Log;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.LimitLine;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.test.db.DbTools;
import com.test.DataHelp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class GraphHelper {
    public static Map<String, List<?>>loadChartData() {
        List<Float> glucoseValues = new ArrayList<>();
        List<String> timestamps = new ArrayList<>();
        String today=DataHelp.get_today_date();
        try {
            Map<String, List<?>> data = DbTools.get_data(today, today);
            glucoseValues = (List<Float>) data.get("glucose");
            timestamps = (List<String>) data.get("dates");
        }
        catch(SQLiteException e){
            Log.e("GraphActivity", "Ошибка БД: " + e.getMessage());

            glucoseValues.add(5.5f); timestamps.add("2026-07-14 16:45:00");
            glucoseValues.add(6.2f); timestamps.add("2026-07-14 17:00:00");
            glucoseValues.add(7.8f); timestamps.add("2026-07-14 17:15:00");
            glucoseValues.add(8.0f); timestamps.add("2026-07-14 17:30:00");
            glucoseValues.add(7.5f); timestamps.add("2026-07-14 17:45:00");
            glucoseValues.add(6.2f); timestamps.add("2026-07-14 18:00:00");
            glucoseValues.add(6.8f); timestamps.add("2026-07-14 18:15:00");
            glucoseValues.add(6.0f); timestamps.add("2026-07-14 18:15:00");
            glucoseValues.add(7.5f); timestamps.add("2026-07-14 18:30:00");
            glucoseValues.add(7.8f);  timestamps.add("2026-07-14 18:45:00");
        }

        int minSize = Math.min(glucoseValues.size(), timestamps.size());
        Log.d("GraphHelper", "glucoseValues: " + glucoseValues.size() + ", timestamps: " + timestamps.size() + ", minSize: " + minSize);
        if (glucoseValues.size() != timestamps.size()) {
            Log.w("GraphHelper", "Size mismatch! Truncating to " + minSize);
            glucoseValues = new ArrayList<>(glucoseValues.subList(0, minSize));
            timestamps = new ArrayList<>(timestamps.subList(0, minSize));
        }

        ArrayList<Entry> entries = new ArrayList<>();
        ArrayList<String> labels = new ArrayList<>();

        for (int i = 0; i < glucoseValues.size(); i++) {
            float rounded = Math.round(glucoseValues.get(i) * 10) / 10.0f;
            entries.add(new Entry(i, rounded));
            String timestamp = timestamps.get(i);
            if (timestamp != null && timestamp.length() >= 16) {
                labels.add(timestamp.substring(11, 16));
            } else {
                labels.add("--:--");
            }
        }

        Log.d("GraphHelper", "Entries: " + entries.size() + ", Labels: " + labels.size());


        Map<String, List<?>> result = new HashMap<>();
        result.put("glucose", entries);
        result.put("dates", labels);
        Log.d("GraphHelper", "Returning " + entries.size() + " entries");
        Log.d("GraphHelper", "Setting " + labels.size() + " labels");

        return result;
    }
    public static Map<String, List<?>> get_glucose_dataset(List<Float> glucoseValues,List<String> timestamps){
        // Метки для оси Y
        ArrayList<Entry> entries = new ArrayList<>();
        for (int i = 0; i < glucoseValues.size(); i++) {
            float rounded = Math.round(glucoseValues.get(i) * 10) / 10.0f;
            entries.add(new Entry(i, rounded));
        }
        // Метки для оси X
        ArrayList<String> labels = new ArrayList<>();
        for (String timestamp : timestamps) {
            labels.add(timestamp.substring(11, 16)); // "HH:mm"
        }
        Map<String, List<?>> result = new HashMap<>();
        result.put("glucose", glucoseValues);
        result.put("dates", timestamps);
        return result;
    }
    public static LineDataSet points_settings(LineDataSet dataSet){
        dataSet.setColor(0xFF2196F3);
        dataSet.setCircleColor(0xFF2196F3);
        dataSet.setLineWidth(2f);
        dataSet.setCircleRadius(4f);
        dataSet.setValueTextSize(12f);
        dataSet.setValueTextColor(Color.GREEN);
        dataSet.setDrawValues(false);
        return dataSet;
    }
    public static LineDataSet points_pred_settings(LineDataSet predictSet){
        predictSet.setColor(0xFFFF0000);
        predictSet.setCircleColor(0xFFFF0000);
        predictSet.setLineWidth(2f);
        predictSet.setCircleRadius(4f);
        predictSet.setValueTextSize(12f);
        predictSet.setValueTextColor(Color.GREEN);
        predictSet.setDrawValues(false);
        predictSet.setDrawCircles(true);      // ← ДОЛЖНО БЫТЬ true

        predictSet.enableDashedLine(10f, 5f, 0f);

        return predictSet;
    }
    public static LineChart Chart_settings(LineChart chartView,ArrayList<String> labels,LineData lineData){
        chartView.getLegend().setTextColor(Color.GREEN);
        chartView.getXAxis().setTextColor(Color.GREEN);
        chartView.getAxisLeft().setTextColor(Color.GREEN);
        chartView.setScaleEnabled(true);
        chartView.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM); // ← Показывать снизу
        chartView.getXAxis().setLabelCount(Math.min(labels.size(), 10), true); // ← Не больше 10 меток
        chartView.setData(lineData);
        chartView.getXAxis().setValueFormatter(new IndexAxisValueFormatter(labels));
        chartView.getXAxis().setGranularity(1f);
        chartView.getXAxis().setLabelCount(6, true);

        return chartView;
    }
    public static LineChart set_size_x(LineChart chartView, List<Entry> entries){
        float minX = entries.get(0).getX();
        float maxX = entries.get(entries.size() - 1).getX();
// Добавить отступ 10% от ширины
        float padding = (maxX - minX) * 0.1f;
// Установить расширенный диапазон
        chartView.getXAxis().setAxisMinimum(minX - padding);
        chartView.getXAxis().setAxisMaximum(maxX + padding);
        return chartView;
    }
    public static LineChart set_size_y(LineChart chartView, List<Entry> entries){
        float minY = Float.MAX_VALUE;
        float maxY = Float.MIN_VALUE;
        for (Entry entry : entries) {
            if (entry.getY() < minY) minY = entry.getY();
            if (entry.getY() > maxY) maxY = entry.getY();
        }

        float paddingY = (maxY - minY) * 0.15f;

        chartView.getAxisLeft().setAxisMinimum(0 - paddingY);
        chartView.getAxisRight().setAxisMinimum(0 - paddingY);
        chartView.getAxisLeft().setAxisMaximum(20 + paddingY);
        chartView.getAxisRight().setAxisMaximum(20 + paddingY);
        return chartView;
    }
    public static XAxis bottom_label(XAxis xAxis,ArrayList<String> labels){
        if (labels == null || labels.isEmpty()) {
            Log.e("GraphHelper", "Labels is empty!");
            return xAxis;
        }
        Log.d("GraphHelper", "Setting " + labels.size() + " labels");

        xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));
        int maxLabels = Math.min(labels.size(), 8);

        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setLabelCount(maxLabels, true);
        return xAxis;
    }
    public static LimitLine add_extr_graph(String extremum,String label){
        Float float_extremum;
        try {
            float_extremum = Float.parseFloat(extremum);
        } catch (NumberFormatException e) {
            float_extremum = 4.0f;
        }
        LimitLine horizontalLine = new LimitLine(float_extremum, label);
        horizontalLine.setLineColor(Color.RED);
        horizontalLine.setLineWidth(2f);
        horizontalLine.setTextColor(Color.RED);
        horizontalLine.setTextSize(12f);
        return horizontalLine;
    }
}
