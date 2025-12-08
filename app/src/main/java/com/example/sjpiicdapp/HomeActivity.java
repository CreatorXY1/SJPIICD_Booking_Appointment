package com.example.sjpiicdapp;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class HomeActivity extends AppCompatActivity {
    private Button btnGetStarted;
    private Button btnLoginTop;

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_main); // your home layout

        btnGetStarted = findViewById(R.id.btnGetStarted);
        btnLoginTop = findViewById(R.id.btnLoginTop);

        // Setup listeners
        btnGetStarted.setOnClickListener(v -> {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user != null) {
                // user already signed in -> redirect based on role
                redirectByRole(user.getUid());
            } else {
                // no user -> go to student login/choice
                Intent i = new Intent(this, AuthChoiceActivity.class);
                // you can also send role hint if you want:
                i.putExtra("role", "student");
                startActivity(i);
            }
        });

        btnLoginTop.setOnClickListener(v -> {
            // Let user choose login/register for student/admin
            startActivity(new Intent(this, AuthChoiceActivity.class));
        });

        // initial UI update
        updateUiForAuth();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Re-check auth state (handles coming back after login/logout)
        updateUiForAuth();
    }

    private void updateUiForAuth() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            // Hide the top login button if signed in
            if (btnLoginTop != null) btnLoginTop.setVisibility(View.GONE);
            // Optionally change Get Started label to "Continue" or "Open Dashboard"
            if (btnGetStarted != null) btnGetStarted.setText("Continue");
        } else {
            // Not signed in -> show top login
            if (btnLoginTop != null) btnLoginTop.setVisibility(View.VISIBLE);
            if (btnGetStarted != null) btnGetStarted.setText("Get Started");
        }
    }

    private void redirectByRole(String uid) {
        // fetch role from users collection. If missing or failure -> student dashboard fallback.
        FirebaseFirestore.getInstance().collection("users").document(uid)
                .get()
                .addOnSuccessListener(doc -> {
                    String role = doc.exists() ? doc.getString("role") : null;
                    if ("admin".equals(role)) {
                        startActivity(new Intent(this, AdminDashboardActivity.class));
                    } else {
                        startActivity(new Intent(this, DashboardActivity.class));
                    }
                    finish();
                })
                .addOnFailureListener(e -> {
                    // fallback to student dashboard
                    Toast.makeText(this, "Could not determine role, opening student dashboard.", Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(this, DashboardActivity.class));
                    finish();
                });
    }
}
