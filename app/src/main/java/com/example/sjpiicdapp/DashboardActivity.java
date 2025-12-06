package com.example.sjpiicdapp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class DashboardActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_dashboard);

        TextView tvWelcome = findViewById(R.id.tvWelcome);
        CardView cardBook = findViewById(R.id.cardBook);
        CardView cardMy = findViewById(R.id.cardMyAppointments);
        CardView cardEclearance = findViewById(R.id.cardEclearance);
        CardView cardDailyCapacity = findViewById(R.id.cardDailyCapacity);
        Button btnSignOut = findViewById(R.id.btnSignOut);

        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        if (u != null) {
            String nameOrEmail = (u.getDisplayName() != null && !u.getDisplayName().isEmpty())
                    ? u.getDisplayName()
                    : u.getEmail();
            tvWelcome.setText("Welcome, " + (nameOrEmail != null ? nameOrEmail : "Student"));
        } else {
            tvWelcome.setText("Welcome, Student");
        }

        cardBook.setOnClickListener(v -> startActivity(new Intent(this, BookingActivity.class)));
        cardMy.setOnClickListener(v -> startActivity(new Intent(this, MyAppointmentsActivity.class)));
        cardEclearance.setOnClickListener(v -> startActivity(new Intent(this, EClearance.class)));

        // NEW: open AppointmentCapacityActivity
        cardDailyCapacity.setOnClickListener(v -> startActivity(new Intent(this, AppointmentCapacityActivity.class)));

        btnSignOut.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            Intent i = new Intent(this, LoginActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
            finish();
        });
    }
}
