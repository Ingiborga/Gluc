package com.test.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteException;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.test.DataHelp;
import com.test.GlucosePredictor;
import com.test.R;
import com.test.db.DbTools;
import com.test.GraphHelper;
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
    private ArrayList<String> labels = new ArrayList<>();
    private boolean isPredictAdded = false;
    private SharedPreferences prefs;
    private void initPrefs() {
        prefs = getSharedPreferences("MyPrefs", MODE_PRIVATE);
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_statistics);
        initPrefs();

// Инициализация графика
        chartView = findViewById(R.id.chart_view);
        create_statistics_graph(chartView);

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
        float heValue = getIntent().getFloatExtra("he_value", -1);
        if (heValue > 0) {
            Toast.makeText(this, " Попробуйте еще раз", Toast.LENGTH_SHORT).show();
        }
    }

    public void create_statistics_graph(LineChart chartView){
        Map<String, List<?>> datas=GraphHelper.loadChartData();
        List<Entry> entries = (List<Entry>) datas.get("glucose");
        labels = (ArrayList<String>) datas.get("dates");

        if (entries == null || entries.isEmpty()) {
            Log.e("Activity", "No data to display");
            return;
        }
        LineDataSet dataSet = new LineDataSet(entries, "Уровень глюкозы");

        GraphHelper.points_settings(dataSet);
        LineData lineData = new LineData(dataSet);
        GraphHelper.Chart_settings(chartView,labels,lineData);
        chartView.getAxisLeft().addLimitLine(GraphHelper.add_extr_graph(prefs.getString("lower_limit_glucose","4"), "Нижняя граница"));
        chartView.getAxisLeft().addLimitLine(GraphHelper.add_extr_graph(prefs.getString("upper_limit_glucose","10"), "Верхняя граница"));

        GraphHelper.set_size_x(chartView,entries);
        GraphHelper.set_size_y(chartView,entries);
        Log.d("Chart", "Labels size: " + labels.size());
        Log.d("Chart", "Labels: " + labels.toString());
        XAxis xAxis = chartView.getXAxis();
        GraphHelper.bottom_label(xAxis,labels);

        chartView.invalidate();
    }
    public void CalculateButton(View view) {
        EditText he_glucose_button = findViewById(R.id.he_glucose);
        String text = he_glucose_button.getText().toString();

        if (text.isEmpty()) {
            Toast.makeText(this, "Введите количество ХЕ", Toast.LENGTH_SHORT).show();
            return;
        }
        if (isPredictAdded) {
            Intent intent = new Intent(this, GraphActivity.class);
            intent.putExtra("he_value", text); // Передаем значение ХЕ
            Toast.makeText(this, "Попробуйте еще раз", Toast.LENGTH_SHORT).show();
            startActivity(intent);
            finish();
            return;
        }
        else{
            isPredictAdded=true;
        }

        try {
            float he_value = Float.parseFloat(text);
            float gluc_now = prefs.getFloat("last_glucose_Mmoll", -1f);
            if (gluc_now <= 0) {
                Toast.makeText(this, "Нет данных о текущей глюкозе", Toast.LENGTH_SHORT).show();
                return;
            }
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String timestamp_now = DataHelp.current_time();
            Map<String, List<?>> data = GlucosePredictor.main(gluc_now, timestamp_now, he_value, 15);
            Log.d("GraphHelper", "predictGlucoseValues: " + data.get("glucose").size() + ", timestamps: " + data.get("dates").size());

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

    private void AddPredictGraph(Map<String, List
            <?>> data) {
        List<Float> predictValues = (List<Float>) data.get("glucose");
        List<String> predictDates = (List<String>) data.get("dates");

        if (predictValues == null || predictValues.isEmpty()) return;

        LineChart chartView = findViewById(R.id.chart_view);

        // Добавляем метки для прогноза
        for (int i = 0; i < predictDates.size(); i++) {
            String time = predictDates.get(i);
            int minutes = Integer.parseInt(time.substring(14, 16));
            if (time != null && time.length() >= 16) {
                labels.add(time.substring(11, 16));
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
        GraphHelper.points_pred_settings(predictSet);

        dataSets.add(predictSet);

        float maxX = lastX + predictValues.size();
        chartView.getXAxis().setAxisMaximum(maxX);

        // Устанавливаем обновленные метки
        chartView.getXAxis().setValueFormatter(new IndexAxisValueFormatter(labels));
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