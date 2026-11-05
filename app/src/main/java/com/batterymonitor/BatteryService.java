package com.batterymonitor;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.IBinder;
import androidx.core.app.NotificationCompat;

public class BatteryService extends Service {
    private static final String CHANNEL_ID = "battery_monitor_channel";
    private BroadcastReceiver batteryReceiver;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        batteryReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                updateNotification(intent);
            }
        };
        registerReceiver(batteryReceiver, filter);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "Battery Monitor", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    private void updateNotification(Intent batteryIntent) {
        BatteryManager bm = (BatteryManager) getSystemService(BATTERY_SERVICE);
        if (bm == null || batteryIntent == null) return;

        int level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        int currentUa = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
        int voltageUv = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_VOLTAGE_NOW);

        // حساب الواط
        double watts = (Math.abs((double) currentUa) * (double) voltageUv) / 1_000_000_000_000.0;
        
        // حساب الوقت المتبقي (تقريبي)
        String timeLeft = "غير متاح";
        if (currentUa > 0 && level < 100) {
            // سعة البطارية الافتراضية 4000 mAh (للتقريب)
            double remainingMah = (100 - level) * 40.0;
            double hours = remainingMah / (currentUa / 1000.0);
            if (hours > 0 && hours < 24) {
                int h = (int) hours;
                int m = (int) ((hours - h) * 60);
                timeLeft = h + " س " + m + " د";
            }
        }

        // صحة البطارية
        int health = batteryIntent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1);
        String healthStr = health == BatteryManager.BATTERY_HEALTH_GOOD ? "جيدة" : "غير معروفة";

        String content = String.format("⚡ %.2f W | 🔋 %d%% | ⏳ %s | ❤️ %s", watts, level, timeLeft, healthStr);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("مراقب الشحن")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build();

        startForeground(1, notification);
    }

    @Override
    public void onDestroy() {
        if (batteryReceiver != null) unregisterReceiver(batteryReceiver);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
