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
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.Transaction;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.CollectionReference;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MyAppointmentsActivity extends AppCompatActivity {
    private static final String TAG = "MyAppointmentActivity";
    public static final String EXTRA_APPT_ID = "APPT_ID"; // legacy key, supported for compatibility
    private interface OnFailure {
        void onFailure(Exception e);
    }

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


    private void deleteAppointmentWithSlotUpdate(DocumentSnapshot apptDoc, Runnable onSuccess, java.util.function.Consumer<Exception> onFailure) {
        if (apptDoc == null) {
            onFailure.accept(new IllegalArgumentException("Appointment doc is null"));
            return;
        }

        String apptId = apptDoc.getId();
        String date = apptDoc.contains("date") ? apptDoc.getString("date") : null;
        String window = apptDoc.contains("window") ? apptDoc.getString("window") : null;

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        CollectionReference apptsRef = db.collection("appointments");

        // If date/window missing, simply delete the appointment doc (fallback).
        if (date == null || window == null) {
            apptsRef.document(apptId)
                    .delete()
                    .addOnSuccessListener(aVoid -> onSuccess.run())
                    .addOnFailureListener(onFailure::accept);
            return;
        }

        String slotId = date + "_" + window;
        DocumentReference slotRef = db.collection("slots").document(slotId);
        DocumentReference apptRef = apptsRef.document(apptId);

        db.runTransaction((Transaction.Function<Void>) transaction -> {
            // Ensure appointment still exists
            DocumentSnapshot currentAppt = transaction.get(apptRef);
            if (!currentAppt.exists()) {
                // Nothing to delete (already deleted)
                return null;
            }

            // Decrement slot bookedCount if slot exists
            DocumentSnapshot slotSnap = transaction.get(slotRef);
            if (slotSnap.exists()) {
                Long bookedCount = slotSnap.getLong("bookedCount");
                if (bookedCount == null) bookedCount = 0L;
                long newCount = Math.max(0L, bookedCount - 1L);
                Map<String, Object> slotUpdates = new HashMap<>();
                slotUpdates.put("bookedCount", newCount);
                // If you want to remove the slot doc when both bookedCount==0 and it's newly created, you could,
                // but safer to just set bookedCount to 0.
                transaction.update(slotRef, slotUpdates);
            }
            // Delete appointment doc
            transaction.delete(apptRef);

            return null;
        }).addOnSuccessListener(aVoid -> {
            onSuccess.run();
        }).addOnFailureListener(e -> {
            onFailure.accept((Exception)e);
        });
    }

    private void rescheduleAppointmentTransaction(
            @NonNull com.google.firebase.firestore.DocumentSnapshot apptDoc,
            @NonNull String newDate,
            @NonNull String newWindow,
            @NonNull Runnable onSuccess,
            @NonNull OnFailure onFailure) {

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        if (apptDoc == null || !apptDoc.exists()) {
            onFailure.onFailure(new IllegalArgumentException("Appointment missing"));
            return;
        }

        final String apptId = apptDoc.getId();
        final String oldDate = apptDoc.contains("date") ? apptDoc.getString("date") : null;
        final String oldWindow = apptDoc.contains("window") ? apptDoc.getString("window") : null;

        if (oldDate == null || oldWindow == null) {
            onFailure.onFailure(new IllegalStateException("Appointment has no date/window"));
            return;
        }

        final String oldSlotId = oldDate + "_" + oldWindow;
        final String newSlotId = newDate + "_" + newWindow;

        // No-op if nothing changed
        if (oldSlotId.equals(newSlotId)) {
            onSuccess.run();
            return;
        }

        final DocumentReference apptRef = db.collection("appointments").document(apptId);
        final DocumentReference oldSlotRef = db.collection("slots").document(oldSlotId);
        final DocumentReference newSlotRef = db.collection("slots").document(newSlotId);

        final long MAX_CAPACITY = 400L;

        db.runTransaction((Transaction.Function<Void>) transaction -> {
            // ---- 1) ALL READS FIRST (important) ----
            com.google.firebase.firestore.DocumentSnapshot freshAppt = transaction.get(apptRef);
            com.google.firebase.firestore.DocumentSnapshot oldSlotSnap = transaction.get(oldSlotRef);
            com.google.firebase.firestore.DocumentSnapshot newSlotSnap = transaction.get(newSlotRef);

            // Validate appointment still exists
            if (!freshAppt.exists()) {
                // Abort transaction with readable code
                throw new FirebaseFirestoreException("APPOINTMENT_MISSING", FirebaseFirestoreException.Code.ABORTED);
            }

            // Extract old slot counts
            long oldBooked = 0L;
            long oldCapacity = MAX_CAPACITY;
            boolean oldExists = oldSlotSnap.exists();
            if (oldExists) {
                Long ob = oldSlotSnap.getLong("bookedCount");
                Long oc = oldSlotSnap.getLong("capacity");
                if (ob != null) oldBooked = ob;
                if (oc != null) oldCapacity = oc;
            }

            // Extract new slot counts
            long newBooked = 0L;
            long newCapacity = MAX_CAPACITY;
            boolean newExists = newSlotSnap.exists();
            if (newExists) {
                Long nb = newSlotSnap.getLong("bookedCount");
                Long nc = newSlotSnap.getLong("capacity");
                if (nb != null) newBooked = nb;
                if (nc != null) newCapacity = nc;
            }

            // ---- 2) Business rules / validation (still no writes) ----
            if (newBooked >= newCapacity) {
                // signal caller that the new slot is full
                throw new FirebaseFirestoreException("NEW_SLOT_FULL", FirebaseFirestoreException.Code.ABORTED);
            }

            // ---- 3) ALL WRITES AFTER ALL READS ----
            // Update appointment doc
            Map<String, Object> apptUpdates = new HashMap<>();
            apptUpdates.put("date", newDate);
            apptUpdates.put("window", newWindow);
            apptUpdates.put("updatedAt", FieldValue.serverTimestamp());
            transaction.update(apptRef, apptUpdates);

            // Decrement old slot bookedCount (if it exists)
            if (oldExists) {
                long newOldBooked = Math.max(0L, oldBooked - 1L);
                Map<String, Object> oldSlotUpdates = new HashMap<>();
                oldSlotUpdates.put("bookedCount", newOldBooked);
                transaction.update(oldSlotRef, oldSlotUpdates);
            }

            // Increment or create new slot doc with bookedCount + 1
            long newNewBooked = newBooked + 1L;
            Map<String, Object> newSlotData = new HashMap<>();
            newSlotData.put("date", newDate);
            newSlotData.put("window", newWindow);
            newSlotData.put("capacity", newCapacity);
            newSlotData.put("bookedCount", newNewBooked);

            if (!newExists) {
                transaction.set(newSlotRef, newSlotData);
            } else {
                transaction.update(newSlotRef, newSlotData);
            }

            return null;
        }).addOnSuccessListener(aVoid -> {
            Log.i(TAG, "Reschedule transaction success for apptId=" + apptId);
            onSuccess.run();
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Reschedule failed", e);
            onFailure.onFailure((Exception) e);
        });
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
                // inside renderList loop, when you build item view and have doc variable
                btnDelete.setOnClickListener(v -> {
                    new androidx.appcompat.app.AlertDialog.Builder(this)
                            .setTitle("Delete Appointment")
                            .setMessage("Are you sure you want to delete this appointment?")
                            .setPositiveButton("Delete", (dialog, which) -> {
                                btnDelete.setEnabled(false);
                                deleteAppointmentWithSlotUpdate(doc, () -> {
                                    Toast.makeText(this, "Appointment deleted", Toast.LENGTH_SHORT).show();
                                    // refresh list
                                    loadUserAppointments();
                                }, e -> {
                                    btnDelete.setEnabled(true);
                                    Toast.makeText(this, "Delete failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                    Log.e(TAG, "Delete failed", e);
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

                    // when you have new selectedDate/new selectedWindow ready
                    btnConfirm.setOnClickListener(x -> {
                        String newDateSelected = selectedDate[0];
                        String newWindowSelected = spinnerSlots.getSelectedItem().toString();
                        btnConfirm.setEnabled(false);
                        rescheduleAppointmentTransaction(doc, newDateSelected, newWindowSelected, () -> {
                            Toast.makeText(this, "Rescheduled!", Toast.LENGTH_SHORT).show();
                            dialog.dismiss();
                            btnConfirm.setEnabled(true);
                            loadUserAppointments();
                        }, e -> {
                            btnConfirm.setEnabled(true);
                            String message = e.getMessage() != null ? e.getMessage() : e.toString();
                            if (e instanceof FirebaseFirestoreException && ((FirebaseFirestoreException) e).getCode() == FirebaseFirestoreException.Code.ABORTED) {
                                if (message.equals("NEW_SLOT_FULL")) {
                                    Toast.makeText(this, "Cannot reschedule: selected slot is full.", Toast.LENGTH_LONG).show();
                                } else {
                                    Toast.makeText(this, "Reschedule failed: " + message, Toast.LENGTH_LONG).show();
                                }
                            } else {
                                Toast.makeText(this, "Reschedule failed: " + message, Toast.LENGTH_LONG).show();
                            }
                            Log.e(TAG, "Reschedule failed", e);
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
