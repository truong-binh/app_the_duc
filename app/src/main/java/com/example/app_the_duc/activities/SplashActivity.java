package com.example.app_the_duc.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;
import com.example.app_the_duc.R;
import com.example.app_the_duc.util.Prefs;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import java.util.Calendar;
import com.example.app_the_duc.reminder.ReminderReceiver;

public class SplashActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        String userId = Prefs.getUserId(this);
        if (userId != null && !userId.isEmpty()) {
            startActivity(new Intent(this, DashboardActivity.class));
            finish();
            return;
        }

        Button btn = findViewById(R.id.btnContinue);
        btn.setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
        });

        scheduleDailyReminder();
    }

    private void scheduleDailyReminder() {
        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent i = new Intent(this, ReminderReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(this, 0, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 8);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        long trigger = c.getTimeInMillis();
        if (System.currentTimeMillis() > trigger) trigger += 24L * 60 * 60 * 1000; // ngày mai nếu đã qua giờ
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, trigger, AlarmManager.INTERVAL_DAY, pi);
    }
}
