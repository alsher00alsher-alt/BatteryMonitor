package com.batterymonitor;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 100);
            }
        }
        Intent serviceIntent = new Intent(this, BatteryService.class);
        ContextCompat.startForegroundService(this, serviceIntent);
        TextView tv = findViewById(R.id.tvStatus);
        tv.setText("التطبيق يعمل في الخلفية...\nسيظهر إشعار فيه كل تفاصيل الشحن.");
    }
}
