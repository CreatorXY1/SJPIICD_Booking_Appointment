package com.example.sjpiicdapp;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public class AppointmentCapacityActivity extends AppCompatActivity {
    private static final String TAG = "AppointmentCapacity";
    private static final int DAILY_CAPACITY = 400;

    private LinearLayout capacityContainer;
    private ProgressBar progressBar;
    private TextView tvError;
    private ScrollView scrollView;
    private FirebaseFirestore db;
    private Button btnRefresh;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_capacity);

        // NOTE: these IDs must match those in activity_capacity.xml (you used suffix "1")
        capacityContainer = findViewById(R.id.capacityContainer1);
        progressBar = findViewById(R.id.progressBarCapacity1);
        tvError = findViewById(R.id.tvCapacityError1);
        scrollView = findViewById(R.id.scrollCapacity1);
        btnRefresh = findViewById(R.id.btnRefreshCapacity1);

        db = FirebaseFirestore.getInstance();

        btnRefresh.setOnClickListener(v -> loadCapacity());

        loadCapacity();
    }

    private void loadCapacity() {
        showLoading(true);
        tvError.setVisibility(View.GONE);
        capacityContainer.removeAllViews();

        // Query all slot documents and aggregate by "date" (yyyy-MM-dd)
        db.collection("slots")
                .get()
                .addOnSuccessListener(qs -> {
                    showLoading(false);

                    List<DocumentSnapshot> docs = qs.getDocuments();
                    if (docs == null || docs.isEmpty()) {
                        tvError.setText("No bookings found.");
                        tvError.setVisibility(View.VISIBLE);
                        return;
                    }

                    // TreeMap to sort by date string natural order (yyyy-MM-dd sorts lexicographically)
                    Map<String, Integer> totals = new TreeMap<>();

                    for (DocumentSnapshot d : docs) {
                        if (d == null) continue;
                        String date = d.contains("date") && d.get("date") != null ? d.getString("date") : null;
                        if (date == null) continue;

                        // try Long first; if null attempt to read Double
                        Integer bc = 0;
                        try {
                            Long bcLong = d.contains("bookedCount") ? d.getLong("bookedCount") : null;
                            if (bcLong != null) bc = bcLong.intValue();
                            else {
                                Double bd = d.contains("bookedCount") ? d.getDouble("bookedCount") : null;
                                if (bd != null) bc = bd.intValue();
                            }
                        } catch (Exception ex) {
                            Log.w(TAG, "Failed to parse bookedCount for doc " + d.getId(), ex);
                        }

                        Integer cur = totals.get(date);
                        if (cur == null) cur = 0;
                        totals.put(date, cur + bc);
                    }

                    // Remove dates with zero bookings (you wanted to hide empty rows)
                    totals.values().removeIf(v -> v == null || v == 0);

                    if (totals.isEmpty()) {
                        tvError.setText("No bookings found.");
                        tvError.setVisibility(View.VISIBLE);
                        return;
                    }

                    renderCapacityList(totals);
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Log.e(TAG, "Failed to load slots", e);
                    tvError.setText("Failed to load capacity: " + e.getMessage());
                    tvError.setVisibility(View.VISIBLE);
                });
    }

    private void renderCapacityList(@NonNull Map<String, Integer> totals) {
        capacityContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);

        // totals is sorted (TreeMap) ascending. If you want descending, iterate differently.
        for (Map.Entry<String, Integer> entry : totals.entrySet()) {
            String date = entry.getKey();
            int booked = entry.getValue() != null ? entry.getValue() : 0;

            // skip zeros (defensive)
            if (booked <= 0) continue;

            int remaining = DAILY_CAPACITY - booked;
            if (remaining < 0) remaining = 0;

            View item = inflater.inflate(R.layout.item_capacity, capacityContainer, false);
            TextView tvDate = item.findViewById(R.id.tvCapacityDate);
            TextView tvNumbers = item.findViewById(R.id.tvCapacityNumbers);
            TextView tvRemaining = item.findViewById(R.id.tvCapacityRemaining);
            ProgressBar pbDay = item.findViewById(R.id.progressBarDay);

            // format friendly
            tvDate.setText(date);
            tvNumbers.setText(String.format(Locale.getDefault(), "%d / %d booked", booked, DAILY_CAPACITY));
            tvRemaining.setText(String.format(Locale.getDefault(), "%d left", remaining));

            if (pbDay != null) {
                pbDay.setMax(DAILY_CAPACITY);
                // clamp
                int progress = Math.min(Math.max(booked, 0), DAILY_CAPACITY);
                pbDay.setProgress(progress);
            }

            // optional click behaviour: show summary toast or open bookings for that date
            item.setOnClickListener(v -> Toast.makeText(this, date + " — " + booked + "/" + DAILY_CAPACITY, Toast.LENGTH_SHORT).show());

            capacityContainer.addView(item);
        }
    }

    private void showLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
    }
}
