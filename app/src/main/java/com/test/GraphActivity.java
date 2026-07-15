package com.test;

import android.content.Intent;
import android.database.sqlite.SQLiteException;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.test.broadcast.BroadcastReceiver;
import com.test.broadcast.BroadcastService;
import com.test.db.DbTools;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class GraphActivity extends AppCompatActivity {
    private TextView user_email;
    private TextView user_pass;
    private LineChart chartView;
    private ArrayList<String> allLabels = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_statistics);

// Инициализация графика
        chartView = findViewById(R.id.chart_view);
        loadChartData();

        BottomNavigationView bottomNavigationView = findViewById(R.id.bottomNavigationView);
        bottomNavigationView.setSelectedItemId(R.id.action_graph);
        bottomNavigationView.setOnItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.action_graph) {
                return true;
            } else if (id == R.id.action_profile) {
                startActivity(new Intent(this, ProfileActivity.class));
                overridePendingTransition(0, 0);
                finish();
                return true;
            } else if (id == R.id.action_glucose) {
                startActivity(new Intent(this, MainActivity.class));
                overridePendingTransition(0, 0);
                finish();
                return true;
            }
            return false;
        });
    }

    private void loadChartData() {
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        List<Float> glucoseValues = new ArrayList<>();
        List<String> timestamps = new ArrayList<>();
        try{
            Map<String, List<?>> data = DbTools.get_data(today, today);
            glucoseValues = (List<Float>) data.get("glucose");
            timestamps = new ArrayList<>();}
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
        ArrayList<Entry> entries = new ArrayList<>();
        for (int i = 0; i < glucoseValues.size(); i++) {
            entries.add(new Entry(i, glucoseValues.get(i)));
        }

// Метки для оси X
        ArrayList<String> labels = new ArrayList<>();
        for (String timestamp : timestamps) {
            labels.add(timestamp.substring(11, 16)); // "HH:mm"
        }
        allLabels.clear();
        for (String timestamp : timestamps) {
            allLabels.add(timestamp.substring(11, 16));
        }
        LineDataSet dataSet = new LineDataSet(entries, "Уровень глюкозы");
        dataSet.setColor(0xFF2196F3);
        dataSet.setCircleColor(0xFF2196F3);
        dataSet.setLineWidth(2f);
        dataSet.setCircleRadius(4f);
        dataSet.setValueTextSize(12f);
        dataSet.setValueTextColor(Color.GREEN);

        chartView.getLegend().setTextColor(Color.GREEN);
        chartView.getXAxis().setTextColor(Color.GREEN);
        chartView.getAxisLeft().setTextColor(Color.GREEN);
        chartView.setScaleEnabled(false);

        LineData lineData = new LineData(dataSet);
        chartView.setData(lineData);

        chartView.getXAxis().setValueFormatter(new IndexAxisValueFormatter(allLabels));
        chartView.getXAxis().setGranularity(1f);
        //chartView.getXAxis().setLabelCount(labels.size());

        chartView.invalidate();
    }
    public void CalculateButton(View view) {
        EditText he_glucose_button = findViewById(R.id.he_glucose);
        String text = he_glucose_button.getText().toString();

        if (text.isEmpty()) {
            Toast.makeText(this, "Введите количество ХЕ", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            float he_value = Float.parseFloat(text);
            //float gluc_now = DbTools.current_glucose();
            float gluc_now = 9.8f;
            if (gluc_now <= 0) {
                Toast.makeText(this, "Нет данных о текущей глюкозе", Toast.LENGTH_SHORT).show();
                return;
            }
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String timestamp = sdf.format(new Date());
            Map<String, List<?>> data = GlucosePredictor.main(gluc_now, timestamp, he_value, 15);
            AddPredictGraph(data);
            List<Float> peak = (List<Float>) data.get("peak");
            GlucosePredictor.getAdvice(peak.get(0),this);
            he_glucose_button.setText("");

            //startActivity(new Intent(this, MainActivity.class));
            //overridePendingTransition(0, 0);
            //finish();
        } catch (NumberFormatException | ParseException e) {
            Toast.makeText(this, "Ошибка: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void AddPredictGraph(Map<String, List<?>> data) {
        List<Float> predictValues = (List<Float>) data.get("glucose");
        List<String> predictDates = (List<String>) data.get("dates");


        if (predictValues == null || predictValues.isEmpty()) return;

        LineChart chartView = findViewById(R.id.chart_view);

        // Добавляем метки для прогноза
        for (int i = 0; i < predictDates.size(); i++) {
            String time = predictDates.get(i);
            int minutes = Integer.parseInt(time.substring(14, 16));
            if (minutes % 15 == 0 || i == predictDates.size() - 1) {
                allLabels.add(time.substring(11, 16));
            }
        }

        // Получаем текущие данные
        LineData currentData = chartView.getData();
        ArrayList<ILineDataSet> dataSets = new ArrayList<>();

        if (currentData != null) {
            for (ILineDataSet set : currentData.getDataSets()) {
                dataSets.add(set);
            }
        }

        // Добавляем прогноз
        ArrayList<Entry> predictEntries = new ArrayList<>();
        float lastX = currentData != null ? currentData.getXMax() : 0;

        for (int i = 0; i < predictValues.size(); i++) {
            predictEntries.add(new Entry(lastX + i + 1, predictValues.get(i)));
        }

        LineDataSet predictSet = new LineDataSet(predictEntries, "Прогноз");
        predictSet.setColor(0xFFFF0000);
        predictSet.setCircleColor(0xFFFF0000);
        predictSet.setValueTextColor(Color.GREEN);
        predictSet.setValueTextSize(12f); // Размер в sp

        predictSet.setLineWidth(2f);
        predictSet.enableDashedLine(10f, 5f, 0f);
        predictSet.setDrawValues(true);
        dataSets.add(predictSet);

        // Устанавливаем обновленные метки
        chartView.getXAxis().setValueFormatter(new IndexAxisValueFormatter(allLabels));
        chartView.getXAxis().setGranularity(1f);
        //chartView.getXAxis().setLabelCount(Math.min(allLabels.size(), 15));
        chartView.getAxisRight().setEnabled(false);
        chartView.setData(new LineData(dataSets));
        chartView.invalidate();
    }

    @Override
    protected void onResume() {
        super.onResume();

    }

    @Override
    protected void onPause() {
        super.onPause();

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //Intent intent = new Intent(this, BroadcastService.class);
        //stopService(intent);
        //BroadcastReceiver.setCallback(null);
    }

}