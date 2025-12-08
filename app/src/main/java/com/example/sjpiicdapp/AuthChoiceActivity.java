package com.example.sjpiicdapp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

public class AuthChoiceActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_auth_choice);

        Button btnStudent = findViewById(R.id.btnStudentLogin);
        Button btnAdmin = findViewById(R.id.btnAdminLogin);

        btnStudent.setOnClickListener(v -> {
            Intent i = new Intent(this, AuthLoginActivity.class);
            i.putExtra("role", "student");
            startActivity(i);
        });

        btnAdmin.setOnClickListener(v -> {
            Intent i = new Intent(this, AuthLoginActivity.class);
            i.putExtra("role", "admin");
            startActivity(i);
        });
    }
}
