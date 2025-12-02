package com.example.sjpiicdapp;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class SplashActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // no layout needed — simple router
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        Intent dest;
        if (u == null) {
            dest = new Intent(this, LoginActivity.class);
        } else {
            dest = new Intent(this, DashboardActivity.class);
        }
        // Clear previous stack and go to destination
        dest.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(dest);
        finish();
    }
}
