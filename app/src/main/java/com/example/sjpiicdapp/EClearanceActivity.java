package com.example.sjpiicdapp;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.LinkedHashMap;
import java.util.Map;

public class EClearanceActivity extends AppCompatActivity {
    private static final String TAG = "EClearanceActivity";

    private ProgressBar progressBar;
    private TextView tvError;
    private Button btnPrelim, btnMidterm, btnFinal, btnRefresh;
    private LinearLayout layoutPrelim, layoutMidterm, layoutFinal;
    private TextView tvPrelimStatus, tvMidtermStatus, tvFinalStatus;
    private ScrollView scrollView;

    private FirebaseFirestore db;
    private FirebaseUser user;

    // requirement lists (keeps order)
    private static final String[] PRELIM_REQS = {"Accounting Office"};
    private static final String[] MIDTERM_REQS = {"Accounting Office"};
    private static final String[] FINAL_REQS = {
            "Accounting Office","Admission Office","AVP-ACAD","Campus Ministry",
            "Community Extension","Computer Laboratory","Digital Laboratory",
            "Guidance Office","Library","Office of Student Affairs",
            "Program Heads","Property Custodian","Quality Management Office",
            "Scholarship and Grants Office","Science Laboratory","Supreme Student Council"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_eclearance);

        progressBar = findViewById(R.id.progressBarEC);
        tvError = findViewById(R.id.tvECError);
        btnPrelim = findViewById(R.id.btnPrelim);
        btnMidterm = findViewById(R.id.btnMidterm);
        btnFinal = findViewById(R.id.btnFinal);
        btnRefresh = findViewById(R.id.btnRefreshEC);

        layoutPrelim = findViewById(R.id.layoutPrelim);
        layoutMidterm = findViewById(R.id.layoutMidterm);
        layoutFinal = findViewById(R.id.layoutFinal);

        tvPrelimStatus = findViewById(R.id.tvPrelimStatus);
        tvMidtermStatus = findViewById(R.id.tvMidtermStatus);
        tvFinalStatus = findViewById(R.id.tvFinalStatus);

        scrollView = findViewById(R.id.scrollEC);

        db = FirebaseFirestore.getInstance();
        user = FirebaseAuth.getInstance().getCurrentUser();

        // wire buttons
        btnPrelim.setOnClickListener(v -> showTab(Tab.PRELIM));
        btnMidterm.setOnClickListener(v -> showTab(Tab.MIDTERM));
        btnFinal.setOnClickListener(v -> showTab(Tab.FINAL));
        btnRefresh.setOnClickListener(v -> loadClearance());

        // build UI rows (static) once
        buildRows(layoutPrelim, PRELIM_REQS);
        buildRows(layoutMidterm, MIDTERM_REQS);
        buildRows(layoutFinal, FINAL_REQS);

        // default tab
        showTab(Tab.PRELIM);

        // load
        loadClearance();
    }

    private enum Tab { PRELIM, MIDTERM, FINAL }

    private void showTab(Tab t) {
        layoutPrelim.setVisibility(t == Tab.PRELIM ? View.VISIBLE : View.GONE);
        layoutMidterm.setVisibility(t == Tab.MIDTERM ? View.VISIBLE : View.GONE);
        layoutFinal.setVisibility(t == Tab.FINAL ? View.VISIBLE : View.GONE);

        tvPrelimStatus.setVisibility(t == Tab.PRELIM ? View.VISIBLE : View.GONE);
        tvMidtermStatus.setVisibility(t == Tab.MIDTERM ? View.VISIBLE : View.GONE);
        tvFinalStatus.setVisibility(t == Tab.FINAL ? View.VISIBLE : View.GONE);
    }

    private void buildRows(LinearLayout container, String[] offices) {
        container.removeAllViews();
        for (String office : offices) {
            View row = getLayoutInflater().inflate(R.layout.item_clearance_row, container, false);
            TextView tvOffice = row.findViewById(R.id.tvOfficeName);
            CheckBox cb = row.findViewById(R.id.cbOfficeCleared);
            tvOffice.setText(office);
            cb.setEnabled(false); // readonly for students
            container.addView(row);
        }
    }

    private void loadClearance() {
        if (user == null) {
            Toast.makeText(this, "Please sign in", Toast.LENGTH_SHORT).show();
            return;
        }
        progressBar.setVisibility(View.VISIBLE);
        tvError.setVisibility(View.GONE);

        db.collection("clearances").document(user.getUid())
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    progressBar.setVisibility(View.GONE);
                    if (!documentSnapshot.exists()) {
                        tvError.setText("No clearance record found.");
                        tvError.setVisibility(View.VISIBLE);
                        // make sure checkboxes are all unchecked
                        applyClears(null, layoutPrelim, PRELIM_REQS);
                        applyClears(null, layoutMidterm, MIDTERM_REQS);
                        applyClears(null, layoutFinal, FINAL_REQS);
                        tvPrelimStatus.setText("Not Cleared");
                        tvMidtermStatus.setText("Not Cleared");
                        tvFinalStatus.setText("Not Cleared");
                        return;
                    }
                    applyDocument(documentSnapshot);
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    tvError.setText("Failed to load clearance: " + e.getMessage());
                    tvError.setVisibility(View.VISIBLE);
                });
    }

    private void applyDocument(@NonNull DocumentSnapshot doc) {
        // read nested maps
        Map<String, Object> prelimMap = doc.contains("prelim") ? (Map<String, Object>) doc.get("prelim") : null;
        Map<String, Object> midMap = doc.contains("midterm") ? (Map<String, Object>) doc.get("midterm") : null;
        Map<String, Object> finalMap = doc.contains("final") ? (Map<String, Object>) doc.get("final") : null;

        applyClears(prelimMap, layoutPrelim, PRELIM_REQS);
        applyClears(midMap, layoutMidterm, MIDTERM_REQS);
        applyClears(finalMap, layoutFinal, FINAL_REQS);

        tvPrelimStatus.setText(calculateStatus(prelimMap, PRELIM_REQS));
        tvMidtermStatus.setText(calculateStatus(midMap, MIDTERM_REQS));
        tvFinalStatus.setText(calculateStatus(finalMap, FINAL_REQS));
    }

    private void applyClears(Map<String, Object> map, LinearLayout container, String[] offices) {
        // container children in same order as 'offices'
        for (int i = 0; i < offices.length; i++) {
            View row = container.getChildAt(i);
            if (row == null) continue;
            CheckBox cb = row.findViewById(R.id.cbOfficeCleared);
            String office = offices[i];
            boolean checked = false;
            if (map != null && map.containsKey(office)) {
                Object o = map.get(office);
                if (o instanceof Boolean) checked = (Boolean) o;
                else if (o instanceof String) checked = Boolean.parseBoolean((String)o);
            }
            cb.setChecked(checked);
        }
    }

    private String calculateStatus(Map<String, Object> map, String[] offices) {
        if (map == null) return "Not Cleared";
        int total = offices.length;
        int cleared = 0;
        for (String office : offices) {
            Object o = map.get(office);
            boolean ok = false;
            if (o instanceof Boolean) ok = (Boolean)o;
            else if (o instanceof String) ok = Boolean.parseBoolean((String)o);
            if (ok) cleared++;
        }
        if (cleared == total) return "Cleared";
        if (cleared == 0) return "Not Cleared";
        return "Partially Cleared";
    }
}
