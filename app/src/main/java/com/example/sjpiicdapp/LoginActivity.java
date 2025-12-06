package com.example.sjpiicdapp;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;

public class LoginActivity extends AppCompatActivity {
    private static final String TAG = "AuthDebug";
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i("LoginDebug", "onCreate start");
        setContentView(R.layout.activity_login);
        Log.i("LoginDebug", "setContentView done");

        mAuth = FirebaseAuth.getInstance();

        EditText etEmail = findViewById(R.id.etEmail);
        EditText etPassword = findViewById(R.id.etPassword);
        Button btnSignIn = findViewById(R.id.btnSignIn);
        Button btnRegister = findViewById(R.id.btnRegister);

        if (etEmail == null || etPassword == null || btnSignIn == null || btnRegister == null) {
            Log.e(TAG, "One or more views are null! Check IDs in activity_login.xml");
            Toast.makeText(this, "UI wiring error: check IDs", Toast.LENGTH_LONG).show();
            return;
        }

        btnSignIn.setOnClickListener(v -> {
            Log.i("AuthDebug","SignIn clicked");
            String email = etEmail.getText().toString().trim();
            String pass = etPassword.getText().toString();
            if (email.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "Enter email & password", Toast.LENGTH_SHORT).show();
                return;
            }
            btnSignIn.setEnabled(false);
            btnRegister.setEnabled(false);
            Log.i("AuthDebug","Calling signInWithEmailAndPassword for " + email);
            mAuth.signInWithEmailAndPassword(email, pass)
                    .addOnCompleteListener(this, task -> {
                        btnSignIn.setEnabled(true);
                        btnRegister.setEnabled(true);
                        if (task.isSuccessful()) {
                            Log.i("AuthDebug","signIn: SUCCESS uid=" + (mAuth.getCurrentUser()!=null?mAuth.getCurrentUser().getUid():"null"));
                            Toast.makeText(this, "Signed in", Toast.LENGTH_SHORT).show();

                            // go to Dashboard and clear back stack so user can't go back to Login
                            Intent intent = new Intent(this, MainActivity.class);
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(intent);
                            // no finish needed because flags clear the task, but harmless:
                            finish();
                        } else {
                            Log.e("AuthDebug","signIn: FAIL", task.getException());
                            Toast.makeText(this, "Sign in failed: " + (task.getException()!=null ? task.getException().getMessage() : "unknown"), Toast.LENGTH_LONG).show();
                        }
                    })
                    .addOnFailureListener(e -> {
                        btnSignIn.setEnabled(true);
                        btnRegister.setEnabled(true);
                        Log.e("AuthDebug","signIn:onFailure", e);
                        Toast.makeText(this, "Sign in error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
        });

        btnRegister.setOnClickListener(v -> {
            Log.i("AuthDebug","Register clicked");
            String email = etEmail.getText().toString().trim();
            String pass = etPassword.getText().toString();
            if (email.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "Enter email & password", Toast.LENGTH_SHORT).show();
                return;
            }
            btnSignIn.setEnabled(false);
            btnRegister.setEnabled(false);
            Log.i("AuthDebug","Calling createUserWithEmailAndPassword for " + email);
            mAuth.createUserWithEmailAndPassword(email, pass)
                    .addOnCompleteListener(this, task -> {
                        btnSignIn.setEnabled(true);
                        btnRegister.setEnabled(true);
                        if (task.isSuccessful()) {
                            Log.i("AuthDebug","register: SUCCESS uid=" + (mAuth.getCurrentUser()!=null?mAuth.getCurrentUser().getUid():"null"));
                            Toast.makeText(this, "Registered", Toast.LENGTH_SHORT).show();
                            Intent intent = new Intent(this, MainActivity.class);
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(intent);
                            finish();
                        } else {
                            Log.e("AuthDebug","register: FAIL", task.getException());
                            Toast.makeText(this, "Register failed: " + (task.getException()!=null?task.getException().getMessage():"unknown"), Toast.LENGTH_LONG).show();
                        }
                    })
                    .addOnFailureListener(e -> {
                        btnSignIn.setEnabled(true);
                        btnRegister.setEnabled(true);
                        Log.e("AuthDebug","register:onFailure", e);
                        Toast.makeText(this, "Register error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
        });

        Log.i("LoginDebug", "listeners attached");
    }
}
