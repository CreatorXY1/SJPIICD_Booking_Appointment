package com.example.sjpiicdapp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

public class AdminDashboardActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_admin_dashboard);

        Button btnManageClearance = findViewById(R.id.btnManageClearance);
        Button btnViewAppointments = findViewById(R.id.btnViewAppointments);

        btnManageClearance.setOnClickListener(v -> {
            // To implement: activity to list students and manage their clearance
            startActivity(new Intent(this, AdminManageClearanceActivity.class)); // implement when ready
        });

        btnViewAppointments.setOnClickListener(v -> {
            // Could reuse your appointment list UI but with admin privileges
            startActivity(new Intent(this, MyAppointmentsActivity.class));
        });
    }
}
