package com.example.sjpiicdapp;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.appcompat.app.AppCompatActivity;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * Simple splash/launcher activity.
 * Shows a short splash then routes to MainActivity if signed-in, otherwise LoginActivity.
 */
public class SplashActivity extends AppCompatActivity {
    // splash delay in milliseconds (keep short)
    private static final long SPLASH_DELAY_MS = 800L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash); // create a simple layout or reuse an existing one

        // Optional: animate or show logo here using the layout elements if provided
        ImageView logo = findViewById(R.id.ivSplashLogo);
        TextView tv = findViewById(R.id.tvAppName);

        // Defer routing with a small delay so splash is visible
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            routeAfterSplash();
        }, SPLASH_DELAY_MS);
    }

    private void routeAfterSplash() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        Intent next;
        if (user != null) {
            // already signed in -> go to MainActivity (which will show activity_main and allow Dashboard)
            next = new Intent(this, MainActivity.class);
        } else {
            // not signed in -> go to LoginActivity
            next = new Intent(this, LoginActivity.class);
        }

        // Make navigation clean: clear splash from back stack
        next.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(next);
        finish();
    }
}
