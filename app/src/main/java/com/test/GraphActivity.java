package com.test;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.test.broadcast.BroadcastReceiver;
import com.test.broadcast.BroadcastService;

public class GraphActivity extends AppCompatActivity {
    private TextView user_email;//логин
    private TextView user_pass;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_statistics);

        //добавить про радиогрупп!!!
        //и нижнюю границу

        //lower_bound = findViewById(R.id.editTextTextEmailAddress);
        //user_pass = findViewById(R.id.editTextTextPassword);

        //проверка, если данные есть, то переход на следующую страницу
        BottomNavigationView bottomNavigationView = findViewById(R.id.bottomNavigationView);
        bottomNavigationView.setSelectedItemId(R.id.action_graph);

        bottomNavigationView.setOnItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.action_graph) {
                // Остаемся на главном экране
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
        Intent intent = new Intent(this, BroadcastService.class);
        stopService(intent);
        BroadcastReceiver.setCallback(null);
    }

}