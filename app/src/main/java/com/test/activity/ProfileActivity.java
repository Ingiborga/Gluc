package com.test.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.test.R;

public class ProfileActivity extends AppCompatActivity {
    private SharedPreferences prefs;
    private  SharedPreferences.Editor editor;
    TextView upper_limit_glucose;
    TextView lower_limit_glucose;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account);
        initPrefs();
        TextView user_login=findViewById(R.id.user_login);
        user_login.setText(prefs.getString("username", ""));

        upper_limit_glucose=findViewById(R.id.upper_limit_glucose);
        upper_limit_glucose.setHint("Верхняя граница: "+prefs.getString("upper_limit_glucose", ""));

        lower_limit_glucose=findViewById(R.id.lower_limit_glucose);
        lower_limit_glucose.setHint("Нижняя граница: "+prefs.getString("lower_limit_glucose", ""));

        findViewById(R.id.ottai).setOnClickListener((view)->onRadioButtonClicked(view));
        // устанавливаем обработчики для кнопок
        findViewById(R.id.aidex).setOnClickListener((view)->onRadioButtonClicked(view));
        RadioGroup radioGroup = findViewById(R.id.group);
        String sensorType = prefs.getString("glucometer", "aidex");
        if (sensorType.equals("ottai")) {
            radioGroup.check(R.id.ottai);
        } else {
            radioGroup.check(R.id.aidex);
        }

        BottomNavigationView bottomNavigationView = findViewById(R.id.bottomNavigationView);
        bottomNavigationView.setSelectedItemId(R.id.action_profile);

        bottomNavigationView.setOnItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.action_profile) {
                // Остаемся на главном экране
                return true;
            } else if (id == R.id.action_glucose) {
                startActivity(new Intent(this, MainActivity.class));
                overridePendingTransition(0, 0);
                finish();
                return true;
            } else if (id == R.id.action_graph) {
                startActivity(new Intent(this, GraphActivity.class));
                overridePendingTransition(0, 0);
                finish();
                return true;
            }

            return false;
        });

        //проверка, если данные есть, то переход на следующую страницу
    }
    private void initPrefs() {
        prefs = getSharedPreferences("MyPrefs", MODE_PRIVATE);
        editor = prefs.edit();
    }
    public void onRadioButtonClicked(View view) {
        RadioButton radio = (RadioButton) view;
        // если переключатель отмечен
        boolean checked = radio.isChecked();
        // получаем текст нажатой радиокнопки
        String text = radio.getText().toString();
        // Получаем нажатый переключатель
        switch(text) {
            case "Ottai":
                editor.putString("glucometer", "ottai");
                break;
            case "Aidex":
                editor.putString("glucometer", "aidex");
                break;
        }
    }
    public void ApplySettings(View view){
        editor.putString("lower_limit_glucose", String.valueOf(lower_limit_glucose.getText()));
        editor.putString("upper_limit_glucose", String.valueOf(upper_limit_glucose.getText()));
        editor.apply();
    }
    public void LogoutAcc(View view){
        prefs.edit().remove("email").apply();
        prefs.edit().remove("password").apply();
        editor.apply();
        startActivity(new Intent(this, AuthActivity.class));
        overridePendingTransition(0, 0);
        finish();
    }
    @Override
    protected void onResume() {//при запуске страницы запускаются сервисы по чтению глюкозы
        super.onResume();

        //при нажатии кнопок сохранение данных локально и отправка на сервер

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