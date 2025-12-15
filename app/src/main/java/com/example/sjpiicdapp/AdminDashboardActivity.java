package com.example.sjpiicdapp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;

public class AdminDashboardActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_admin_dashboard);

        Button btnManageClearance = findViewById(R.id.btnManageClearance);
        Button btnViewAppointments = findViewById(R.id.btnViewAppointments);
        Button btnAdminSignOut = findViewById(R.id.btnAdminSignOut); // make sure layout includes this id

        btnManageClearance.setOnClickListener(v -> {
            startActivity(new Intent(this, AdminManageClearanceActivity.class));
        });

        btnViewAppointments.setOnClickListener(v -> {
            // Note: you might create a dedicated admin appointment view later.
            startActivity(new Intent(this, MyAppointmentsActivity.class));
        });

        btnAdminSignOut.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            Intent i = new Intent(this, HomeActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
            finish();
        });
    }
}