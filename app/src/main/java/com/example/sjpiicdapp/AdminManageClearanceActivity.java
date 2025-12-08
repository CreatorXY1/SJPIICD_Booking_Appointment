package com.example.sjpiicdapp;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.CheckBox;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AdminManageClearanceActivity extends AppCompatActivity {
    private static final String TAG = "AdminManageClearance";
    private LinearLayout studentsContainer;
    private ProgressBar progressBar;
    private TextView tvError;
    private ScrollView scrollView;
    private EditText etSearch;
    private Button btnRefresh;

    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_manage_clearance);

        studentsContainer = findViewById(R.id.studentsContainer);
        progressBar = findViewById(R.id.progressBarManageClearance);
        tvError = findViewById(R.id.tvManageClearanceError);
        scrollView = findViewById(R.id.scrollManageClearance);
        etSearch = findViewById(R.id.etManageSearch);
        btnRefresh = findViewById(R.id.btnRefreshManageClearance);

        db = FirebaseFirestore.getInstance();

        btnRefresh.setOnClickListener(v -> loadStudents());
        findViewById(R.id.btnSearchManageClearance).setOnClickListener(v -> loadStudents());

        loadStudents();
    }

    private void loadStudents() {
        showLoading(true);
        tvError.setVisibility(View.GONE);
        studentsContainer.removeAllViews();

        String q = etSearch.getText() != null ? etSearch.getText().toString().trim() : "";
        Query query = db.collection("users").whereEqualTo("role", "student").orderBy("displayName");

        // If user typed a search term, we'll do client-side filter after getting small set.
        query.get()
                .addOnSuccessListener(qs -> {
                    showLoading(false);
                    List<DocumentSnapshot> docs = qs.getDocuments();
                    if (docs.isEmpty()) {
                        tvError.setText("No students found.");
                        tvError.setVisibility(View.VISIBLE);
                        return;
                    }

                    boolean any = false;
                    LayoutInflater inflater = LayoutInflater.from(this);
                    for (DocumentSnapshot d : docs) {
                        String name = d.contains("displayName") ? d.getString("displayName") : d.getId();
                        String email = d.contains("email") ? d.getString("email") : "";

                        if (!TextUtils.isEmpty(q) && !(name.toLowerCase().contains(q.toLowerCase()) || email.toLowerCase().contains(q.toLowerCase()))) {
                            continue;
                        }

                        any = true;
                        View item = inflater.inflate(R.layout.item_student_clearance, studentsContainer, false);
                        TextView tvName = item.findViewById(R.id.tvStudentName);
                        TextView tvEmail = item.findViewById(R.id.tvStudentEmail);
                        Button btnEdit = item.findViewById(R.id.btnEditClearance);

                        tvName.setText(name);
                        tvEmail.setText(email);

                        btnEdit.setOnClickListener(v -> openEditorForStudent(d.getId(), name));
                        studentsContainer.addView(item);
                    }

                    if (!any) {
                        tvError.setText("No matching students.");
                        tvError.setVisibility(View.VISIBLE);
                    }
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Log.e(TAG, "Failed to load students", e);
                    tvError.setText("Failed to load students: " + e.getMessage());
                    tvError.setVisibility(View.VISIBLE);
                });
    }

    private void openEditorForStudent(String uid, String displayName) {
        // Load current clearance doc for student (nested under users/{uid}/clearance or field users/{uid}.clearance)
        showLoading(true);
        db.collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    showLoading(false);
                    Map<String, Object> clearance = null;
                    if (doc.exists() && doc.contains("clearance")) {
                        Object obj = doc.get("clearance");
                        if (obj instanceof Map) {
                            //noinspection unchecked
                            clearance = (Map<String, Object>) obj;
                        }
                    }
                    if (clearance == null) clearance = new HashMap<>();

                    // Build dialog UI
                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle("Edit clearance: " + (displayName == null ? uid : displayName));
                    View dialogView = getLayoutInflater().inflate(R.layout.dialog_clearance_editor, null);
                    // Prelim
                    CheckBox cbPrelimAccounting = dialogView.findViewById(R.id.cbPrelimAccounting);
                    // Midterm
                    CheckBox cbMidtermAccounting = dialogView.findViewById(R.id.cbMidtermAccounting);
                    // Final -> many checkboxes
                    CheckBox cbFinalAccounting = dialogView.findViewById(R.id.cbFinalAccounting);
                    CheckBox cbFinalAdmission = dialogView.findViewById(R.id.cbFinalAdmission);
                    CheckBox cbFinalAVP = dialogView.findViewById(R.id.cbFinalAVP);
                    CheckBox cbFinalCampusMin = dialogView.findViewById(R.id.cbFinalCampusMin);
                    CheckBox cbFinalComExt = dialogView.findViewById(R.id.cbFinalComExt);
                    CheckBox cbFinalCompLab = dialogView.findViewById(R.id.cbFinalCompLab);
                    CheckBox cbFinalDigLab = dialogView.findViewById(R.id.cbFinalDigLab);
                    CheckBox cbFinalGuidance = dialogView.findViewById(R.id.cbFinalGuidance);
                    CheckBox cbFinalLibrary = dialogView.findViewById(R.id.cbFinalLibrary);
                    CheckBox cbFinalOSA = dialogView.findViewById(R.id.cbFinalOSA);
                    CheckBox cbFinalProgramHeads = dialogView.findViewById(R.id.cbFinalProgramHeads);
                    CheckBox cbFinalPropCust = dialogView.findViewById(R.id.cbFinalPropCust);
                    CheckBox cbFinalQMO = dialogView.findViewById(R.id.cbFinalQMO);
                    CheckBox cbFinalScholarship = dialogView.findViewById(R.id.cbFinalScholarship);
                    CheckBox cbFinalSciLab = dialogView.findViewById(R.id.cbFinalSciLab);
                    CheckBox cbFinalSSC = dialogView.findViewById(R.id.cbFinalSSC);

                    // Populate current values (safe casts)
                    // clearance structure example:
                    // clearance.prelim.accounting = true
                    // clearance.midterm.accounting = true
                    // clearance.final.accounting = true, final.admission = true, etc.
                    Map<String,Object> prelim = getSubMap(clearance, "prelim");
                    Map<String,Object> midterm = getSubMap(clearance, "midterm");
                    Map<String,Object> fin = getSubMap(clearance, "final");

                    cbPrelimAccounting.setChecked(getBooleanSafe(prelim, "accounting"));
                    cbMidtermAccounting.setChecked(getBooleanSafe(midterm, "accounting"));

                    cbFinalAccounting.setChecked(getBooleanSafe(fin, "accounting"));
                    cbFinalAdmission.setChecked(getBooleanSafe(fin, "admission"));
                    cbFinalAVP.setChecked(getBooleanSafe(fin, "avp_acad"));
                    cbFinalCampusMin.setChecked(getBooleanSafe(fin, "campus_ministry"));
                    cbFinalComExt.setChecked(getBooleanSafe(fin, "community_extension"));
                    cbFinalCompLab.setChecked(getBooleanSafe(fin, "computer_laboratory"));
                    cbFinalDigLab.setChecked(getBooleanSafe(fin, "digital_laboratory"));
                    cbFinalGuidance.setChecked(getBooleanSafe(fin, "guidance_office"));
                    cbFinalLibrary.setChecked(getBooleanSafe(fin, "library"));
                    cbFinalOSA.setChecked(getBooleanSafe(fin, "office_student_affairs"));
                    cbFinalProgramHeads.setChecked(getBooleanSafe(fin, "program_heads"));
                    cbFinalPropCust.setChecked(getBooleanSafe(fin, "property_custodian"));
                    cbFinalQMO.setChecked(getBooleanSafe(fin, "quality_management_office"));
                    cbFinalScholarship.setChecked(getBooleanSafe(fin, "scholarship"));
                    cbFinalSciLab.setChecked(getBooleanSafe(fin, "science_laboratory"));
                    cbFinalSSC.setChecked(getBooleanSafe(fin, "supreme_student_council"));

                    builder.setView(dialogView);
                    builder.setPositiveButton("Save", null);
                    builder.setNegativeButton("Cancel", (d, which) -> { /* dismiss */ });

                    AlertDialog dialog = builder.create();
                    dialog.setOnShowListener(dialog1 -> {
                        Button btnSave = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
                        btnSave.setOnClickListener(v -> {
                            // Build updated clearance map
                            Map<String,Object> newPrelim = new HashMap<>();
                            newPrelim.put("accounting", cbPrelimAccounting.isChecked());

                            Map<String,Object> newMidterm = new HashMap<>();
                            newMidterm.put("accounting", cbMidtermAccounting.isChecked());

                            Map<String,Object> newFinal = new HashMap<>();
                            newFinal.put("accounting", cbFinalAccounting.isChecked());
                            newFinal.put("admission", cbFinalAdmission.isChecked());
                            newFinal.put("avp_acad", cbFinalAVP.isChecked());
                            newFinal.put("campus_ministry", cbFinalCampusMin.isChecked());
                            newFinal.put("community_extension", cbFinalComExt.isChecked());
                            newFinal.put("computer_laboratory", cbFinalCompLab.isChecked());
                            newFinal.put("digital_laboratory", cbFinalDigLab.isChecked());
                            newFinal.put("guidance_office", cbFinalGuidance.isChecked());
                            newFinal.put("library", cbFinalLibrary.isChecked());
                            newFinal.put("office_student_affairs", cbFinalOSA.isChecked());
                            newFinal.put("program_heads", cbFinalProgramHeads.isChecked());
                            newFinal.put("property_custodian", cbFinalPropCust.isChecked());
                            newFinal.put("quality_management_office", cbFinalQMO.isChecked());
                            newFinal.put("scholarship", cbFinalScholarship.isChecked());
                            newFinal.put("science_laboratory", cbFinalSciLab.isChecked());
                            newFinal.put("supreme_student_council", cbFinalSSC.isChecked());

                            Map<String,Object> updates = new HashMap<>();
                            updates.put("clearance.prelim", newPrelim);
                            updates.put("clearance.midterm", newMidterm);
                            updates.put("clearance.final", newFinal);
                            updates.put("clearance.updatedAt", Timestamp.now());

                            // Save atomically to user doc
                            showLoading(true);
                            db.collection("users").document(uid)
                                    .update(updates)
                                    .addOnSuccessListener(aVoid -> {
                                        showLoading(false);
                                        Toast.makeText(AdminManageClearanceActivity.this, "Saved.", Toast.LENGTH_SHORT).show();
                                        dialog.dismiss();
                                    })
                                    .addOnFailureListener(e -> {
                                        showLoading(false);
                                        Log.e(TAG, "Failed saving clearance", e);
                                        Toast.makeText(AdminManageClearanceActivity.this, "Save failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                    });
                        });
                    });

                    dialog.show();
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Log.e(TAG, "Failed to load student doc", e);
                    Toast.makeText(this, "Failed to open student: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    @NonNull
    private Map<String,Object> getSubMap(Map<String,Object> root, String key) {
        if (root == null) return new HashMap<>();
        Object o = root.get(key);
        if (o instanceof Map) return (Map<String,Object>) o;
        return new HashMap<>();
    }

    private boolean getBooleanSafe(Map<String, Object> map, String key) {
        if (map == null) return false;
        Object o = map.get(key);
        if (o instanceof Boolean) return (Boolean) o;
        return false;
    }

    private void showLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
    }
}
