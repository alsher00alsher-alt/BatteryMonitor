package com.batterymonitor;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private TextView tvPercentage, tvStatus, tvWatt, tvTimeLeft, tvHealth;
    private ProgressBar batteryProgress;
    private Handler handler = new Handler();
    private Runnable updater;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvPercentage = findViewById(R.id.tvPercentage);
        tvStatus = findViewById(R.id.tvStatus);
        tvWatt = findViewById(R.id.tvWatt);
        tvTimeLeft = findViewById(R.id.tvTimeLeft);
        tvHealth = findViewById(R.id.tvHealth);
        batteryProgress = findViewById(R.id.batteryProgress);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 100);
            }
        }

        Intent serviceIntent = new Intent(this, BatteryService.class);
        ContextCompat.startForegroundService(this, serviceIntent);

        updater = new Runnable() {
            @Override
            public void run() {
                updateUI();
                handler.postDelayed(this, 1000); // تحديث كل ثانية
            }
        };
        handler.post(updater);
    }

    private void updateUI() {
        BatteryManager bm = (BatteryManager) getSystemService(BATTERY_SERVICE);
        Intent batteryIntent = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (bm == null || batteryIntent == null) return;

        // 1. النسبة العشرية
        int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        float percentage = -1;
        if (level != -1 && scale != -1) {
            percentage = ((float) level / (float) scale) * 100.0f;
        }
        if (percentage >= 0) {
            tvPercentage.setText(String.format("%.2f%%", percentage));
            batteryProgress.setProgress((int) percentage);
        }

        // 2. السرعة والواط
        int currentUa = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
        int voltageMv = batteryIntent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
        double currentA = Math.abs((double) currentUa) / 1_000_000.0;
        double voltageV = (double) voltageMv / 1000.0;
        double watts = currentA * voltageV;
        tvWatt.setText(String.format("%.2f W", watts));

        // 3. حالة الشحن
        int status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL;
        if (!isCharging) {
            tvStatus.setText("الرجاء توصيل الشاحن");
            tvTimeLeft.setText("--");
        } else {
            tvStatus.setText("جاري الشحن");
            if (currentUa > 0 && level < 100) {
                double remainingMah = (100 - percentage) * 40.0; // افتراض سعة 4000 mAh
                double hours = remainingMah / (currentUa / 1000.0);
                if (hours > 0 && hours < 24) {
                    int h = (int) hours;
                    int m = (int) ((hours - h) * 60);
                    tvTimeLeft.setText(h + " س " + m + " د");
                }
            } else if (level >= 100) {
                tvTimeLeft.setText("مكتمل");
            }
        }

        // 4. صحة البطارية
        int health = batteryIntent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1);
        String healthStr = "غير معروفة";
        if (health == BatteryManager.BATTERY_HEALTH_GOOD) healthStr = "جيدة (100%)";
        else if (health == BatteryManager.BATTERY_HEALTH_OVERHEAT) healthStr = "سخنة";
        else if (health == BatteryManager.BATTERY_HEALTH_DEAD) healthStr = "تالفة";
        else if (health == BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE) healthStr = "جهد عالي";
        else if (health == BatteryManager.BATTERY_HEALTH_COLD) healthStr = "باردة";
        tvHealth.setText(healthStr);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(updater);
    }
}
