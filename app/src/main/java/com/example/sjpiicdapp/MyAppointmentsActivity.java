package com.example.sjpiicdapp;

import android.app.DatePickerDialog;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MyAppointmentsActivity extends AppCompatActivity {
    private static final String TAG = "MyAppointmentActivity";
    public static final String EXTRA_APPT_ID = "APPT_ID"; // legacy key, supported for compatibility

    private LinearLayout appointmentContainer;
    private ProgressBar progressBar;
    private TextView tvError;
    private ScrollView scrollView;
    private FirebaseFirestore db;
    private String prettyPaymentMethod(String code) {
        if (code == null) return "Unknown";
        switch (code) {
            case "E_WALLET":
                return "E-Wallet / Bank Transfer";
            case "PAY_AT_SCHOOL":
                return "Pay at School (Cashier)";
            default:
                // fallback: show the raw code but make it nicer
                return code.replace('_', ' ').toUpperCase();
        }
    }




    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_appointment);

        appointmentContainer = findViewById(R.id.appointmentContainer);
        progressBar = findViewById(R.id.progressBar);
        tvError = findViewById(R.id.tvError);
        scrollView = findViewById(R.id.scrollView);

        db = FirebaseFirestore.getInstance();

        // read either key: "appointmentId" (from BookingActivity) or legacy EXTRA_APPT_ID
        Intent intent = getIntent();
        String apptId = null;
        if (intent != null) {
            apptId = intent.getStringExtra("appointmentId");
            if (apptId == null) apptId = intent.getStringExtra(EXTRA_APPT_ID);
        }

        if (apptId != null && !apptId.isEmpty()) {
            // if we have a specific appointment id -> show single doc
            loadSingleAppointment(apptId);
        } else {
            // otherwise show the list for the current user
            loadUserAppointments();
        }
    }

    private void loadSingleAppointment(String apptId) {
        showLoading(true);
        tvError.setVisibility(View.GONE);
        appointmentContainer.removeAllViews();

        db.collection("appointments").document(apptId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    showLoading(false);
                    if (documentSnapshot.exists()) {
                        renderAppointment(documentSnapshot);
                    } else {
                        tvError.setText("Appointment not found.");
                        tvError.setVisibility(View.VISIBLE);
                    }
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    tvError.setText("Failed to load appointment: " + e.getMessage());
                    tvError.setVisibility(View.VISIBLE);
                    Log.e(TAG, "Error loading appointment", e);
                });
    }

    private void loadUserAppointments() {
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        if (u == null) {
            Toast.makeText(this, "Please sign in to view appointments", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        final String uid = u.getUid();
        showLoading(true);
        tvError.setVisibility(View.GONE);
        appointmentContainer.removeAllViews();

        // Primary (expected) query: filter by userId and order by createdAt desc.
        db.collection("appointments")
                .whereEqualTo("userId", uid)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    showLoading(false);
                    List<DocumentSnapshot> docs = querySnapshot.getDocuments();
                    if (docs.isEmpty()) {
                        tvError.setText("No appointments found.");
                        tvError.setVisibility(View.VISIBLE);
                        return;
                    }
                    renderList(docs);
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Log.e(TAG, "Error loading appointments", e);

                    // If Firestore says "requires an index", fallback to client-side approach
                    if (e instanceof com.google.firebase.firestore.FirebaseFirestoreException
                            && ((com.google.firebase.firestore.FirebaseFirestoreException) e).getCode()
                            == com.google.firebase.firestore.FirebaseFirestoreException.Code.FAILED_PRECONDITION) {

                            Toast.makeText(this, "Firestore index missing — using fallback (slower). Create composite index in Firebase Console for best performance.", Toast.LENGTH_LONG).show();
                        // Fallback: query only by userId, then sort locally by createdAt
                        db.collection("appointments")
                                .whereEqualTo("userId", uid)
                                .get()
                                .addOnSuccessListener(fallbackSnapshot -> {
                                    List<DocumentSnapshot> docs = fallbackSnapshot.getDocuments();
                                    if (docs.isEmpty()) {
                                        tvError.setText("No appointments found.");
                                        tvError.setVisibility(View.VISIBLE);
                                        return;
                                    }
                                    // sort docs by createdAt descending (documents may have createdAt as Timestamp)
                                    docs.sort((a, b) -> {
                                        Timestamp ta = a.getTimestamp("createdAt");
                                        Timestamp tb = b.getTimestamp("createdAt");
                                        if (ta == null && tb == null) return 0;
                                        if (ta == null) return 1;
                                        if (tb == null) return -1;
                                        return tb.compareTo(ta); // descending
                                    });
                                    renderList(docs);
                                })
                                .addOnFailureListener(e2 -> {
                                    Log.e(TAG, "Fallback load also failed", e2);
                                    tvError.setText("Failed to load appointments: " + e2.getMessage());
                                    tvError.setVisibility(View.VISIBLE);
                                });

                    } else {
                        tvError.setText("Failed to load appointments: " + e.getMessage());
                        tvError.setVisibility(View.VISIBLE);
                    }
                });
    }

    private void renderList(@NonNull List<DocumentSnapshot> docs) {
        appointmentContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);

        for (DocumentSnapshot doc : docs) {
            View item = inflater.inflate(R.layout.item_appointment, appointmentContainer, false);

            // --- find the UI controls using the correct types ---
            TextView tvDateWindow = item.findViewById(R.id.tvDateWindow);
            TextView tvStatus = item.findViewById(R.id.tvStatus);
            TextView tvPaymentMethod = item.findViewById(R.id.tvPaymentMethod);

            // Buttons at bottom of item (you used ImageView in layout)
            android.widget.ImageView btnEdit = item.findViewById(R.id.btnEdit);
            android.widget.ImageView btnDelete = item.findViewById(R.id.btnDelete);

            // safe-guard null checks
            final String docId = doc.getId();

            // populate fields
            String date = doc.contains("date") ? doc.getString("date") : "Unknown date";
            String window = doc.contains("window") ? doc.getString("window") : "Unknown window";
            String status = doc.contains("status") ? doc.getString("status") : "Unknown";
            String paymentCode = doc.contains("paymentMethod") ? doc.getString("paymentMethod") : null;
            String friendly = prettyPaymentMethod(paymentCode);
            tvPaymentMethod.setText(friendly);


            tvDateWindow.setText(date + " - " + window);
            tvStatus.setText("Status: " + status);
            tvPaymentMethod.setText("Payment: " + paymentCode);

            // set tag so we can find later if needed
            item.setTag(docId);

            // DELETE handler
            if (btnDelete != null) {
                btnDelete.setOnClickListener(v -> {
                    new androidx.appcompat.app.AlertDialog.Builder(this)
                            .setTitle("Delete Appointment")
                            .setMessage("Are you sure you want to delete this appointment?")
                            .setPositiveButton("Delete", (dialog, which) -> {
                                btnDelete.setEnabled(false);
                                FirebaseFirestore.getInstance()
                                        .collection("appointments")
                                        .document(docId)
                                        .delete()
                                        .addOnSuccessListener(aVoid -> {
                                            Toast.makeText(this, "Appointment deleted", Toast.LENGTH_SHORT).show();
                                            // reload list
                                            loadUserAppointments();
                                        })
                                        .addOnFailureListener(e -> {
                                            btnDelete.setEnabled(true);
                                            Toast.makeText(this, "Delete failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                            android.util.Log.e(TAG, "Delete failed", e);
                                        });
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                });
            }

            // EDIT / Reschedule handler
            if (btnEdit != null) {
                btnEdit.setOnClickListener(v -> {
                    // build reschedule dialog (same pattern you used)
                    View dialogView = getLayoutInflater().inflate(R.layout.dialog_reschedule, null);
                    TextView tvCurrent = dialogView.findViewById(R.id.tvCurrentSchedule);
                    Button btnPickDate = dialogView.findViewById(R.id.btnPickDate);
                    Spinner spinnerSlots = dialogView.findViewById(R.id.spinnerTimeSlots);
                    Button btnConfirm = dialogView.findViewById(R.id.btnConfirm);
                    Button btnCancel  = dialogView.findViewById(R.id.btnCancel);

                    // show current schedule
                    String curDate = doc.contains("date") ? doc.getString("date") : "";
                    String curWindow = doc.contains("window") ? doc.getString("window") : "";
                    tvCurrent.setText((curDate != null ? curDate : "") + "\n" + (curWindow != null ? curWindow : ""));

                    final String[] selectedDate = { curDate };
                    final String[] selectedWindow = { curWindow };

                    // wire date picker
                    btnPickDate.setOnClickListener(x -> {
                        showDatePicker(dateSelected -> {
                            selectedDate[0] = dateSelected;
                            btnPickDate.setText(dateSelected);
                        });
                    });

                    // populate spinner
                    java.util.List<String> windows = java.util.Arrays.asList(
                            "09:00-10:00","10:00-11:00","11:00-12:00","13:00-14:00");
                    ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                            android.R.layout.simple_spinner_dropdown_item, windows);
                    spinnerSlots.setAdapter(adapter);

                    // initial selection
                    if (curWindow != null) {
                        int pos = adapter.getPosition(curWindow);
                        if (pos >= 0) spinnerSlots.setSelection(pos);
                    }

                    AlertDialog dialog = new AlertDialog.Builder(this)
                            .setView(dialogView)
                            .create();

                    btnCancel.setOnClickListener(x -> dialog.dismiss());

                    btnConfirm.setOnClickListener(x -> {
                        selectedWindow[0] = spinnerSlots.getSelectedItem().toString();
                        btnConfirm.setEnabled(false);

                        java.util.Map<String,Object> updates = new java.util.HashMap<>();
                        updates.put("date", selectedDate[0]);
                        updates.put("window", selectedWindow[0]);

                        FirebaseFirestore.getInstance()
                                .collection("appointments")
                                .document(docId)
                                .update(updates)
                                .addOnSuccessListener(aVoid -> {
                                    Toast.makeText(this, "Rescheduled!", Toast.LENGTH_SHORT).show();
                                    dialog.dismiss();
                                    loadUserAppointments();
                                })
                                .addOnFailureListener(e -> {
                                    btnConfirm.setEnabled(true);
                                    Toast.makeText(this, "Reschedule failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                });
                    });

                    dialog.show();
                });
            }

            // add the item to the container
            appointmentContainer.addView(item);
        }
    }


    private void renderAppointment(@NonNull DocumentSnapshot snap) {
        LayoutInflater inflater = LayoutInflater.from(this);
        View item = inflater.inflate(R.layout.item_appointment, appointmentContainer, false);

        TextView tvDateWindow = item.findViewById(R.id.tvDateWindow);
        TextView tvStatus = item.findViewById(R.id.tvStatus);
        TextView tvPaymentMethod = item.findViewById(R.id.tvPaymentMethod);

        String date = snap.contains("date") ? snap.getString("date") : "Unknown date";
        String window = snap.contains("window") ? snap.getString("window") : "Unknown window";
        String status = snap.contains("status") ? snap.getString("status") : "Unknown";
        String paymentCode = snap.contains("paymentMethod") ? snap.getString("paymentMethod") : null;
        String friendly = prettyPaymentMethod(paymentCode);
        tvPaymentMethod.setText(friendly);


        tvDateWindow.setText(date + " - " + window);
        tvStatus.setText("Status: " + status);
        tvPaymentMethod.setText("Payment: " + paymentCode);

        appointmentContainer.removeAllViews();
        appointmentContainer.addView(item);
    }

    private void showLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private void showDatePicker(OnDateSelected callback) {
        Calendar c = Calendar.getInstance();
        DatePickerDialog dp = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    // month is 0-based
                    String mm = String.format(Locale.US, "%02d", month + 1);
                    String dd = String.format(Locale.US, "%02d", dayOfMonth);
                    String iso = year + "-" + mm + "-" + dd; // yyyy-MM-dd
                    callback.onSelected(iso);
                },
                c.get(Calendar.YEAR),
                c.get(Calendar.MONTH),
                c.get(Calendar.DAY_OF_MONTH)
        );
        dp.show();
    }

    private interface OnDateSelected {
        void onSelected(String date); // date in yyyy-MM-dd
    }

}
