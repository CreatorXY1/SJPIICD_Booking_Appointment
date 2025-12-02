package com.example.sjpiicdapp;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class DashboardActivity extends AppCompatActivity {
    private static final String TAG = "DashboardActivity";

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_dashboard);

        TextView tvWelcome = findViewById(R.id.tvWelcome);
        View cardBook = findViewById(R.id.cardBook);
        View cardMy = findViewById(R.id.cardMyAppointments);
        View cardEclearance = findViewById(R.id.cardEclearance);
        View btnSignOut = findViewById(R.id.btnSignOut);

        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        if (u != null) {
            String nameOrEmail = (u.getDisplayName() != null && !u.getDisplayName().isEmpty())
                    ? u.getDisplayName()
                    : u.getEmail();
            tvWelcome.setText("Welcome, " + (nameOrEmail != null ? nameOrEmail : "Student"));
        } else {
            tvWelcome.setText("Welcome, Student");
        }

        if (cardBook != null) {
            cardBook.setOnClickListener(v -> startActivity(new Intent(this, BookingActivity.class)));
        } else {
            Log.w(TAG, "cardBook view not found (check activity_dashboard.xml id)");
        }

        if (cardMy != null) {
            cardMy.setOnClickListener(v -> startActivity(new Intent(this, MyAppointmentsActivity.class)));
        } else {
            Log.w(TAG, "cardMyAppointments view not found (check activity_dashboard.xml id)");
        }

        if (cardEclearance != null) {
            // NOTE: previously you referenced AccountStatusActivity in older files.
            // If your class is named AccountStatusActivity, keep the Intent below.
            // If instead you renamed it to EClearance (or another name), replace
            // AccountStatusActivity.class with that class.
            cardEclearance.setOnClickListener(v -> {
                try {
                    startActivity(new Intent(this, EClearance.class));
                } catch (Exception ex) {
                    // If AccountStatusActivity doesn't exist, try to open EClearance if you created it.
                    try {
                        Intent fallback = new Intent();
                        fallback.setClassName(getPackageName(), getPackageName() + ".EClearance");
                        startActivity(fallback);
                    } catch (Exception ex2) {
                        Log.e(TAG, "Failed to start AccountStatusActivity or EClearance. Create one of these activities.", ex2);
                    }
                }
            });
        } else {
            Log.w(TAG, "cardEclearance view not found (check activity_dashboard.xml id)");
        }

        if (btnSignOut != null) {
            btnSignOut.setOnClickListener(v -> {
                FirebaseAuth.getInstance().signOut();
                Intent i = new Intent(this, LoginActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(i);
                finish();
            });
        } else {
            Log.w(TAG, "btnSignOut view not found (check activity_dashboard.xml id)");
        }
    }
}
