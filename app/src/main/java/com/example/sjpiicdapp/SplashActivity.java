package com.example.sjpiicdapp;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Simple splash screen activity.
 * Shows activity_splash.xml then navigates to HomeActivity after a short delay.
 */
public class SplashActivity extends AppCompatActivity {
    private static final long SPLASH_DELAY_MS = 1000L; // 1 second, change if you want longer
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable goHome = () -> {
        startActivity(new Intent(SplashActivity.this, HomeActivity.class));
        finish();
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // Post delayed navigation to home screen
        handler.postDelayed(goHome, SPLASH_DELAY_MS);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // remove callbacks to avoid leaking activity if it's destroyed early
        handler.removeCallbacks(goHome);
    }
}
