package com.example.app_the_duc.reminder;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;

import androidx.core.app.NotificationCompat;

import com.example.app_the_duc.R;
import com.example.app_the_duc.activities.SplashActivity;

public class ReminderReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String channelId = "daily_reminder";
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(channelId, "Nhắc tập luyện", NotificationManager.IMPORTANCE_DEFAULT);
            ch.enableLights(true);
            ch.setLightColor(Color.RED);
            nm.createNotificationChannel(ch);
        }
        Intent open = new Intent(context, SplashActivity.class);
        PendingIntent pi = PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder b = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Đến giờ vận động!")
                .setContentText("Đi bộ 10 phút hoặc hoàn thành mục tiêu hôm nay nhé.")
                .setContentIntent(pi)
                .setAutoCancel(true);
        nm.notify(1001, b.build());
    }
}


