package com.batterymonitor;

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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

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

    // دالة لجلب النسبة العشرية الحقيقية من ملفات النظام
    private double getExactPercentage() {
        try {
            File chargeCounterFile = new File("/sys/class/power_supply/battery/charge_counter");
            File chargeFullFile = new File("/sys/class/power_supply/battery/charge_full");
            
            if (!chargeCounterFile.exists()) {
                chargeCounterFile = new File("/sys/class/power_supply/battery/battery_charging_enabled"); // بديل
            }
            if (!chargeFullFile.exists()) {
                chargeFullFile = new File("/sys/class/power_supply/battery/charge_full_design");
            }

            if (chargeCounterFile.exists() && chargeFullFile.exists()) {
                BufferedReader brCounter = new BufferedReader(new FileReader(chargeCounterFile));
                BufferedReader brFull = new BufferedReader(new FileReader(chargeFullFile));
                String counterStr = brCounter.readLine();
                String fullStr = brFull.readLine();
                brCounter.close();
                brFull.close();
                
                if (counterStr != null && fullStr != null) {
                    double counter = Double.parseDouble(counterStr.trim());
                    double full = Double.parseDouble(fullStr.trim());
                    if (full > 0) {
                        return (counter / full) * 100.0;
                    }
                }
            }
        } catch (Exception e) {
            // في حالة فشل القراءة، نرجع للطريقة العادية
        }
        
        // الطريقة العادية (لو الملفات مش متاحة)
        BatteryManager bm = (BatteryManager) getSystemService(BATTERY_SERVICE);
        Intent batteryIntent = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (bm != null && batteryIntent != null) {
            int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            if (level != -1 && scale != -1) {
                return ((double) level / (double) scale) * 100.0;
            }
        }
        return -1;
    }

    private void updateUI() {
        BatteryManager bm = (BatteryManager) getSystemService(BATTERY_SERVICE);
        Intent batteryIntent = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (bm == null || batteryIntent == null) return;

        // 1. النسبة العشرية
        double percentage = getExactPercentage();
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

        // 3. حالة الشحن والوقت
        int status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL;
        if (!isCharging) {
            tvStatus.setText("الرجاء توصيل الشاحن");
            tvTimeLeft.setText("--");
        } else {
            tvStatus.setText("جاري الشحن");
            if (currentUa > 0 && percentage < 100) {
                double remainingMah = (100 - percentage) * 40.0; // افتراض سعة 4000 mAh
                double hours = remainingMah / (currentUa / 1000.0);
                if (hours > 0 && hours < 24) {
                    int h = (int) hours;
                    int m = (int) ((hours - h) * 60);
                    tvTimeLeft.setText(h + " س " + m + " د");
                }
            } else if (percentage >= 100) {
                tvTimeLeft.setText("مكتمل");
            }
        }

        // 4. صحة البطارية
        int health = batteryIntent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1);
        String healthStr = "غير معروفة";
        if (health == BatteryManager.BATTERY_HEALTH_GOOD) healthStr = "جيدة (100%)";
        else if (health == BatteryManager.BATTERY_HEALTH_OVERHEAT) healthStr = "سخنة";
        else if (health == BatteryManager.BATTERY_HEALTH_DEAD) healthStr = "تالفة";
        tvHealth.setText(healthStr);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(updater);
    }
}
