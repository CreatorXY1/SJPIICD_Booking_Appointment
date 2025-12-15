package com.example.sjpiicdapp;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.functions.FirebaseFunctions;

import java.util.HashMap;
import java.util.Map;

public class AuthLoginActivity extends AppCompatActivity {
    private static final String TAG = "AuthLoginActivity";

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_login); // reuse canonical login layout

        // Try to support both possible id names in your XML (etIdentifier or legacy etEmail).
        EditText etIdentifier = findViewById(getIdIfExists("etIdentifier", R.id.etEmail));
        EditText etPassword = findViewById(R.id.etPassword);
        Button btnSignIn = findViewById(R.id.btnSignIn);
        Button btnRegister = findViewById(R.id.btnRegister);

        // roleWanted may be "student" or "admin" depending on caller
        final String roleWanted = getIntent().getStringExtra("role");

        btnSignIn.setOnClickListener(v -> {
            String identifier = etIdentifier.getText() == null ? "" : etIdentifier.getText().toString().trim();
            String pw = etPassword.getText() == null ? "" : etPassword.getText().toString().trim();

            if (identifier.isEmpty() || pw.isEmpty()) {
                Toast.makeText(this, "Enter email/username and password", Toast.LENGTH_SHORT).show();
                return;
            }

            // Determine if identifier looks like email
            if (identifier.contains("@")) {
                // treat as email
                signInWithEmail(identifier, pw, roleWanted);
            } else {
                // treat as username -> use callable function
                // Direct Firestore lookup instead of functions (usernames are public-read per your rules)
                FirebaseFirestore.getInstance().collection("usernames").document(identifier)
                        .get()
                        .addOnSuccessListener(doc -> {
                            if (!doc.exists()) {
                                Toast.makeText(this, "Could not find that username.", Toast.LENGTH_LONG).show();
                                return;
                            }
                            String email = doc.getString("email");
                            if (email != null && !email.isEmpty()) {
                                signInWithEmail(email, pw, roleWanted);
                            } else {
                                // defensive: some older username docs may not have email yet
                                Toast.makeText(this, "Username exists but no email associated. Contact admin.", Toast.LENGTH_LONG).show();
                            }
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "username lookup failed", e);
                            Toast.makeText(this, "Error looking up username: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        });


            }
        });

        btnRegister.setOnClickListener(v -> {
            Intent i = new Intent(this, AuthRegisterActivity.class);
            if (roleWanted != null) i.putExtra("role", roleWanted);
            startActivity(i);
        });
    }

    // helper: sign in and redirect by role
    private void signInWithEmail(String email, String pw, String roleWanted) {
        FirebaseAuth.getInstance().signInWithEmailAndPassword(email, pw)
                .addOnSuccessListener(authResult -> {
                    if (authResult == null || authResult.getUser() == null) {
                        Toast.makeText(this, "Sign in failed (no user)", Toast.LENGTH_LONG).show();
                        return;
                    }
                    String uid = authResult.getUser().getUid();
                    // read role from users/{uid}
                    FirebaseFirestore.getInstance().collection("users").document(uid)
                            .get()
                            .addOnSuccessListener(doc -> {
                                String docRole = doc.exists() ? doc.getString("role") : null;
                                if (roleWanted != null && !roleWanted.equals(docRole)) {
                                    FirebaseAuth.getInstance().signOut();
                                    Toast.makeText(this, "Account does not have that role.", Toast.LENGTH_LONG).show();
                                    return;
                                }
                                if ("admin".equals(docRole)) {
                                    startActivity(new Intent(this, AdminDashboardActivity.class));
                                } else {
                                    startActivity(new Intent(this, DashboardActivity.class));
                                }
                                finish();
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Failed reading user doc after sign-in", e);
                                // fallback to student dashboard
                                startActivity(new Intent(this, DashboardActivity.class));
                                finish();
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "signIn failed", e);
                    Log.e(TAG, "signIn error class: " + e.getClass().getName() + " message: " + e.getMessage());
                    if (e instanceof com.google.firebase.auth.FirebaseAuthInvalidCredentialsException) {
                        Log.e(TAG, "Invalid credentials (check password).");
                    }
                    if (e instanceof com.google.firebase.auth.FirebaseAuthException) {
                        Log.e(TAG, "auth code: " + ((com.google.firebase.auth.FirebaseAuthException)e).getErrorCode());
                    }
                    Toast.makeText(this, "Sign in failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });

    }

    // small utility: return id if R.id.name exists otherwise fallbackId
    private int getIdIfExists(String name, int fallbackId) {
        try {
            int id = getResources().getIdentifier(name, "id", getPackageName());
            return id != 0 ? id : fallbackId;
        } catch (Exception ex) {
            return fallbackId;
        }
    }
}
