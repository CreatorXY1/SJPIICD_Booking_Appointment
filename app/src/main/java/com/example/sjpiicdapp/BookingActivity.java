package com.example.sjpiicdapp;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.functions.HttpsCallableResult;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class BookingActivity extends AppCompatActivity {
    private static final String TAG = "BookingActivity";

    private TextView tvWelcomeBooking;
    private Button btnPickDate;
    private Button btnConfirm;
    private Spinner spinnerWindows;

    private FirebaseFirestore db;
    private FirebaseFunctions functions;
    private FirebaseAuth auth;

    private String selectedDate;
    private String selectedWindow;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance();

        // Require auth before inflating UI
        FirebaseUser u = auth.getCurrentUser();
        if (u == null) {
            Toast.makeText(this, "Please sign in to continue", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, AuthLoginActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_booking);

        // Initialize Firebase services
        db = FirebaseFirestore.getInstance();
        functions = FirebaseFunctions.getInstance();

        tvWelcomeBooking = findViewById(R.id.tvWelcomeBooking);
        btnPickDate = findViewById(R.id.btnPickDate);
        btnConfirm = findViewById(R.id.btnConfirmBooking);
        spinnerWindows = findViewById(R.id.spinnerWindows);

        // Show welcome
        String nameOrEmail = (u.getDisplayName() != null && !u.getDisplayName().isEmpty())
                ? u.getDisplayName()
                : u.getEmail();
        tvWelcomeBooking.setText("Welcome, " + (nameOrEmail != null ? nameOrEmail : "Student"));

        loadWindows();

        btnPickDate.setOnClickListener(v -> showDatePicker());
        btnConfirm.setOnClickListener(v -> attemptBooking());
    }

    private void loadWindows() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                new String[]{"09:00-10:00", "10:00-11:00", "11:00-12:00", "13:00-14:00", "14:00-15:00"});
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerWindows.setAdapter(adapter);
    }

    private void showDatePicker() {
        Calendar c = Calendar.getInstance();
        DatePickerDialog dp = new DatePickerDialog(this,
                (DatePicker view, int year, int month, int dayOfMonth) -> {
                    Calendar chosen = Calendar.getInstance();
                    chosen.set(year, month, dayOfMonth);
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                    selectedDate = sdf.format(chosen.getTime());
                    btnPickDate.setText(selectedDate);
                },
                c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
        dp.show();
    }

    private void attemptBooking() {
        Log.i(TAG, "attemptBooking start: selectedDate=" + selectedDate + " selectedWindow=" + spinnerWindows.getSelectedItem());

        // Basic validation
        if (selectedDate == null || selectedDate.isEmpty()) {
            Toast.makeText(this, "Pick a date first", Toast.LENGTH_SHORT).show();
            return;
        }
        selectedWindow = (String) spinnerWindows.getSelectedItem();
        if (selectedWindow == null || selectedWindow.isEmpty()) {
            Toast.makeText(this, "Choose a time window", Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "You must sign in before booking", Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, AuthLoginActivity.class));
            finish();
            return;
        }
        final String uid = currentUser.getUid();

        // Disable UI while checking
        btnConfirm.setEnabled(false);
        btnConfirm.setText("Checking...");

        // Refresh the auth token before proceeding
        currentUser.getIdToken(true)
                .addOnSuccessListener(getTokenResult -> {
                    Log.i(TAG, "Token refreshed successfully");
                    // Now proceed with duplicate check
                    performDuplicateCheck(uid);
                })
                .addOnFailureListener(e -> {
                    btnConfirm.setEnabled(true);
                    btnConfirm.setText("Confirm Booking");
                    Log.e(TAG, "Failed to refresh token", e);
                    Toast.makeText(BookingActivity.this, "Authentication error. Please sign in again.", Toast.LENGTH_LONG).show();

                    // Sign out and redirect to login
                    auth.signOut();
                    startActivity(new Intent(this, AuthLoginActivity.class));
                    finish();
                });
    }

    private void performDuplicateCheck(String uid) {
        // Pre-flight duplicate check
        db.collection("appointments")
                .whereEqualTo("userId", uid)
                .whereEqualTo("date", selectedDate)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    boolean foundNonRejected = false;
                    boolean foundSameSlotNonRejected = false;

                    for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        String status = doc.contains("status") ? doc.getString("status") : null;
                        String window = doc.contains("window") ? doc.getString("window") : null;

                        boolean isRejected = "REJECTED".equalsIgnoreCase(status);

                        if (!isRejected) {
                            foundNonRejected = true;
                        }

                        if (!isRejected && window != null && window.equals(selectedWindow)) {
                            foundSameSlotNonRejected = true;
                        }
                    }

                    if (foundSameSlotNonRejected) {
                        btnConfirm.setEnabled(true);
                        btnConfirm.setText("Confirm Booking");
                        Toast.makeText(this, "You already have an appointment for this exact time slot.", Toast.LENGTH_LONG).show();
                        return;
                    }

                    if (foundNonRejected) {
                        btnConfirm.setEnabled(true);
                        btnConfirm.setText("Confirm Booking");
                        Toast.makeText(this, "You already have an appointment on " + selectedDate + ". Please reschedule or choose another date.", Toast.LENGTH_LONG).show();
                        return;
                    }

                    // No conflicting appointment found - call backend function
                    callCreateAppointment(selectedDate, selectedWindow);
                })
                .addOnFailureListener(e -> {
                    btnConfirm.setEnabled(true);
                    btnConfirm.setText("Confirm Booking");
                    Log.e(TAG, "Pre-flight duplicate check failed", e);
                    Toast.makeText(BookingActivity.this, "Failed to check existing appointments: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void callCreateAppointment(String date, String timeWindow) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("date", date);
        payload.put("window", timeWindow);

        functions.getHttpsCallable("createAppointment")
                .call(payload)
                .addOnSuccessListener((HttpsCallableResult result) -> {
                    String apptId = null;
                    Object data = result.getData();
                    if (data instanceof Map) {
                        Object id = ((Map) data).get("appointmentId");
                        if (id != null) apptId = id.toString();
                    }

                    btnConfirm.setEnabled(true);
                    btnConfirm.setText("Confirm Booking");
                    Toast.makeText(this, "Booked successfully! ID: " + (apptId != null ? apptId : "unknown"), Toast.LENGTH_LONG).show();

                    Intent i = new Intent(this, MyAppointmentsActivity.class);
                    if (apptId != null) i.putExtra("appointmentId", apptId);
                    startActivity(i);
                    finish();
                })
                .addOnFailureListener(e -> {
                    btnConfirm.setEnabled(true);
                    btnConfirm.setText("Confirm Booking");
                    Log.e(TAG, "createAppointment failed", e);

                    if (e instanceof com.google.firebase.functions.FirebaseFunctionsException) {
                        com.google.firebase.functions.FirebaseFunctionsException ffe = (com.google.firebase.functions.FirebaseFunctionsException) e;
                        Log.e(TAG, "Functions error code: " + ffe.getCode());
                        Log.e(TAG, "Functions error message: " + ffe.getMessage());
                        Toast.makeText(this, "Booking failed: " + ffe.getCode().name(), Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(this, "Booking failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }
}