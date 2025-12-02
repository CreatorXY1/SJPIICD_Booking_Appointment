package com.example.sjpiicdapp;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.Transaction;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class BookingActivity extends AppCompatActivity {
    private static final String TAG = "BookingActivity";
    private Button btnPickDate, btnConfirm;
    private Spinner spinnerWindows;
    private FirebaseFirestore db;
    private String selectedDate;
    private String selectedWindow;
    private int selectedPaymentMethodIndex = 0; // 0 ewallet, 1 school

    private RadioGroup rgPayment;
    private RadioButton rbEWALLET, rbSCHOOL;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "onCreate start");

        // AUTH check BEFORE inflating UI (you already had this but keep it strict)
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        if (u == null) {
            Log.i("AuthDebug", "No current user (null) - redirecting to LoginActivity");
            Toast.makeText(this, "Please sign in to continue", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        } else {
            Log.i("AuthDebug", "Signed in user: uid=" + u.getUid() + " email=" + u.getEmail());
        }

        setContentView(R.layout.activity_booking);
        Log.i(TAG, "setContentView done");

        db = FirebaseFirestore.getInstance();
        btnPickDate = findViewById(R.id.btnPickDate);
        spinnerWindows = findViewById(R.id.spinnerWindows);
        btnConfirm = findViewById(R.id.btnConfirmBooking);
        rgPayment = findViewById(R.id.rgPayment);
        rbEWALLET = findViewById(R.id.rbEWALLET);
        rbSCHOOL = findViewById(R.id.rbSCHOOL);

// default to e-wallet if you want
        if (rbEWALLET != null) rbEWALLET.setChecked(true);


        loadWindows();

        btnPickDate.setOnClickListener(v -> showDatePicker());

        btnConfirm.setOnClickListener(v -> {
            Log.i(TAG, "Confirm clicked");
            attemptBooking();
        });
    }

    private void loadWindows() {
        java.util.List<String> windows = Arrays.asList("09:00-10:00","10:00-11:00","11:00-12:00","13:00-14:00");
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, windows);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerWindows.setAdapter(adapter);
    }

    private void showDatePicker() {
        Calendar c = Calendar.getInstance();
        DatePickerDialog dp = new DatePickerDialog(this,
                (view, year, month, dayOfMonth) -> {
                    Calendar chosen = Calendar.getInstance();
                    chosen.set(year, month, dayOfMonth);
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                    selectedDate = sdf.format(chosen.getTime());
                    btnPickDate.setText(selectedDate);
                },
                c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
        dp.show();
    }

    // SAFE attemptBooking with auth check, UI locking, and transaction handling
    private void attemptBooking() {
        Log.i(TAG, "attemptBooking start: selectedDate=" + selectedDate + " selectedWindow=" + spinnerWindows.getSelectedItem());
        if (selectedDate == null || selectedDate.isEmpty()) {
            Toast.makeText(this, "Pick a date first", Toast.LENGTH_SHORT).show();
            return;
        }
        selectedWindow = (String) spinnerWindows.getSelectedItem();
        if (selectedWindow == null) {
            Toast.makeText(this, "Choose a time window", Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "You must sign in before booking", Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, LoginActivity.class));
            return;
        }
        final String uid = currentUser.getUid();

        // Prevent double clicks
        btnConfirm.setEnabled(false);
        btnConfirm.setText("Booking...");

        final String slotDocId = selectedDate + "_" + selectedWindow; // e.g., 2025-11-20_09:00-10:00
        final DocumentReference slotRef = db.collection("slots").document(slotDocId);
        final CollectionReference appointments = db.collection("appointments");

        final Map<String, Object> appointmentData = new HashMap<>();
        appointmentData.put("userId", uid);
        appointmentData.put("date", selectedDate);
        appointmentData.put("window", selectedWindow);
        appointmentData.put("status", "PENDING");
        // Determine payment method from UI
        String paymentMethod = "E_WALLET"; // default
        if (rgPayment != null) {
            int checked = rgPayment.getCheckedRadioButtonId();
            if (checked == R.id.rbSCHOOL) {
                paymentMethod = "PAY_AT_SCHOOL";
            } else {
                // covers R.id.rbEWALLET or any unexpected id -> E_WALLET
                paymentMethod = "E_WALLET";
            }
        }
        appointmentData.put("paymentMethod", paymentMethod);
        appointmentData.put("createdAt", FieldValue.serverTimestamp());

        db.runTransaction((Transaction.Function<String>) transaction -> {
            // READS: safe reads first
            DocumentSnapshot slotSnap = transaction.get(slotRef);

            boolean slotExists = slotSnap.exists();
            long bookedCount = 0L;
            long capacity = 100L; // default capacity

            if (slotExists) {
                Long bc = slotSnap.getLong("bookedCount");
                Long cap = slotSnap.getLong("capacity");
                if (bc != null) bookedCount = bc;
                if (cap != null) capacity = cap;
            } else {
                bookedCount = 0L;
                capacity = 100L;
            }

            // business rule check
            if (bookedCount >= capacity) {
                // abort transaction if full - throw a FirestoreException with ABORTED code
                throw new FirebaseFirestoreException("SLOT_FULL", FirebaseFirestoreException.Code.ABORTED);
            }

            // WRITES
            DocumentReference newApptRef = appointments.document();
            transaction.set(newApptRef, appointmentData);

            Map<String, Object> slotData = new HashMap<>();
            slotData.put("date", selectedDate);
            slotData.put("window", selectedWindow);
            slotData.put("capacity", capacity);
            slotData.put("bookedCount", bookedCount + 1);

            if (!slotExists) {
                transaction.set(slotRef, slotData);
            } else {
                transaction.update(slotRef, slotData);
            }

            return newApptRef.getId();
        }).addOnSuccessListener(appointmentId -> {
            // SUCCESS -> navigate user to MyAppointmentsActivity so they can see status
            Log.i(TAG, "Booking successful: id=" + appointmentId);
            Toast.makeText(this, "Booked! id=" + appointmentId, Toast.LENGTH_LONG).show();

            // restore button state
            btnConfirm.setEnabled(true);
            btnConfirm.setText("Confirm Booking");

            // start MyAppointmentsActivity and pass the appointmentId so it can highlight/scroll to it
            Intent i = new Intent(BookingActivity.this, MyAppointmentsActivity.class);
            i.putExtra("appointmentId", appointmentId);
            // we may want the MyAppointmentsActivity to be part of normal back stack,
            // so user can tap Back to return to Dashboard — do not clear task here.
            startActivity(i);

            // close BookingActivity so user is on MyAppointments
            finish();
        }).addOnFailureListener(e -> {
            // restore UI
            btnConfirm.setEnabled(true);
            btnConfirm.setText("Confirm Booking");

            if (e instanceof FirebaseFirestoreException &&
                    ((FirebaseFirestoreException) e).getCode() == FirebaseFirestoreException.Code.ABORTED) {
                Toast.makeText(this, "Selected slot is full. Pick another.", Toast.LENGTH_LONG).show();
                Log.i(TAG, "Booking aborted: slot full.");
            } else {
                // show readable message and log full exception
                String msg = e.getMessage() != null ? e.getMessage() : e.toString();
                Toast.makeText(this, "Booking failed: " + msg, Toast.LENGTH_LONG).show();
                Log.e(TAG, "Booking failed", e);
            }
        });
    }
}
