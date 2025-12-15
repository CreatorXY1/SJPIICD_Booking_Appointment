package com.example.sjpiicdapp;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Transaction;

import java.util.HashMap;
import java.util.Map;

/**
 * Registration activity:
 * - Student flow: free registration, username required, reserved via Firestore transaction (collection "usernames")
 * - Admin flow: requires invite code (ADMIN_INVITE_CODE)
 *
 * After successful registration we DO NOT auto-sign-in the user; we sign them out so they must explicitly log in.
 */
public class AuthRegisterActivity extends AppCompatActivity {
    private static final String TAG = "AuthRegisterActivity";
    private static final String ADMIN_INVITE_CODE = "ADMIN-INVITE-2025";

    private EditText etName;
    private EditText etUsername;
    private EditText etEmail;
    private EditText etPassword;
    private EditText etInvite;
    private Button btnRegister;

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private boolean adminFlow = false;

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_register);

        etName = findViewById(R.id.etName);
        etUsername = findViewById(R.id.etUsername); // Make sure this exists in layout
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        etInvite = findViewById(R.id.etInvite);
        btnRegister = findViewById(R.id.btnRegister);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        String wanted = getIntent() != null ? getIntent().getStringExtra("role") : null;
        adminFlow = "admin".equalsIgnoreCase(wanted);

        // UI adjustment: show/hide invite and username fields appropriately
        if (adminFlow) {
            if (etInvite != null) etInvite.setVisibility(android.view.View.VISIBLE);
            if (etUsername != null) etUsername.setHint("Username (optional for admin)");
        } else {
            if (etInvite != null) etInvite.setVisibility(android.view.View.GONE);
            if (etUsername != null) etUsername.setVisibility(android.view.View.VISIBLE);
        }

        btnRegister.setOnClickListener(v -> attemptRegister());
    }

    private void attemptRegister() {
        final String name = safe(etName);
        final String usernameRaw = safe(etUsername);
        final String email = safe(etEmail);
        final String password = safe(etPassword);
        final String inviteProvided = safe(etInvite);

        if (TextUtils.isEmpty(name)) { toast("Full name required"); return; }

        if (!adminFlow) {
            if (TextUtils.isEmpty(usernameRaw)) { toast("Username required"); return; }
        }

        if (TextUtils.isEmpty(email)) { toast("Email required"); return; }
        if (TextUtils.isEmpty(password) || password.length() < 6) { toast("Password required (min 6 chars)"); return; }

        final String role = adminFlow ? "admin" : "student";

        if (adminFlow) {
            if (!ADMIN_INVITE_CODE.equals(inviteProvided)) {
                toast("Invalid admin invite code");
                return;
            }
        }

        final String username = usernameRaw == null ? "" : usernameRaw.trim().toLowerCase();

        btnRegister.setEnabled(false);
        btnRegister.setText("Registering...");

        // Step A: create auth user (email/password)
        auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    if (result == null || result.getUser() == null) {
                        handleFailureCleanup(null, username, new Exception("Auth creation failed (no user)"));
                        return;
                    }
                    final String uid = result.getUser().getUid();
                    Log.i(TAG, "Auth created uid=" + uid);

                    // If student and username required -> reserve username with Firestore transaction
                    if (!adminFlow && !TextUtils.isEmpty(username)) {
                        reserveUsernameTx(username, uid, email, () -> writeUserDocAndFinish(uid, name, email, role, username),
                                e -> handleFailureCleanup(uid, username, e));
                    } else {
                        // admin or no username: just write user doc
                        writeUserDocAndFinish(uid, name, email, role, TextUtils.isEmpty(username) ? null : username);
                    }
                })
                .addOnFailureListener(e -> {
                    btnRegister.setEnabled(true);
                    btnRegister.setText("Create Account");
                    // Provide clearer messages: if FirebaseAuth returns error codes you can parse them here
                    String msg = e.getMessage() != null ? e.getMessage() : "Registration failed";
                    Log.e(TAG, "createUserWithEmailAndPassword failed", e);
                    toast("Registration failed: " + msg);
                });
    }

    // Reserve username using a transaction on collection "usernames"
// Document id = username. If it exists -> fail. Otherwise set { uid, email, createdAt }.
    private void reserveUsernameTx(final String username, final String uid, final String email, final Runnable onSuccess, final com.google.android.gms.tasks.OnFailureListener onFailure) {
        final String docId = username;
        final com.google.firebase.firestore.DocumentReference ref = db.collection("usernames").document(docId);

        db.runTransaction((Transaction.Function<Void>) transaction -> {
            DocumentSnapshot snap = transaction.get(ref);
            if (snap.exists()) {
                throw new RuntimeException("USERNAME_TAKEN");
            }
            Map<String, Object> payload = new HashMap<>();
            payload.put("uid", uid);
            payload.put("email", email);        // <- new: store email so clients can look it up
            payload.put("createdAt", Timestamp.now());
            transaction.set(ref, payload);
            return null;
        }).addOnSuccessListener(aVoid -> {
            Log.i(TAG, "reserveUsernameTx success for " + username);
            onSuccess.run();
        }).addOnFailureListener(e -> {
            Log.w(TAG, "reserveUsernameTx failed for " + username, e);
            onFailure.onFailure(e instanceof Exception ? (Exception) e : new Exception(e));
        });
    }


    // Release username - used on cleanup (best-effort)
    private void releaseUsername(String username) {
        if (TextUtils.isEmpty(username)) return;
        db.collection("usernames").document(username)
                .delete()
                .addOnSuccessListener(aVoid -> Log.i(TAG, "Released username " + username))
                .addOnFailureListener(e -> Log.w(TAG, "Failed to release username " + username, e));
    }

    // Write user profile doc, then sign out so user must explicitly log in
    // Write user profile doc, then sign out so user must explicitly log in
    private void writeUserDocAndFinish(final String uid, final String name, final String email, final String role, final String username) {
        Map<String, Object> u = new HashMap<>();
        u.put("uid", uid); // helpful to store uid as well
        u.put("name", name);
        u.put("email", email);
        u.put("role", role);
        if (!TextUtils.isEmpty(username)) u.put("username", username);
// NEW: initialize activeAppointments to 0 for new users
        u.put("activeAppointments", 0);
        u.put("createdAt", Timestamp.now());


        // <-- FIXED: write to "users" collection, NOT "usernames"
        db.collection("users").document(uid)
                .set(u)
                .addOnSuccessListener(aVoid -> {
                    // sign out (newly created user is signed in by createUserWithEmailAndPassword)
                    auth.signOut();
                    btnRegister.setEnabled(true);
                    btnRegister.setText("Create Account");
                    toast("Account created. Please sign in.");

                    // Redirect to login screen for that role
                    Intent i = new Intent(AuthRegisterActivity.this, AuthLoginActivity.class);
                    i.putExtra("role", role);
                    i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(i);
                    finish();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to write user doc for uid=" + uid, e);
                    // cleanup: release username (if created) and attempt to delete auth user
                    releaseUsername(username);
                    attemptDeleteAuthUser(uid, e);
                });
    }


    // Attempt to delete the created auth user (must be signed in as that user)
    private void attemptDeleteAuthUser(final String uid, final Exception original) {
        FirebaseUser cur = auth.getCurrentUser();
        if (cur != null && uid.equals(cur.getUid())) {
            cur.delete()
                    .addOnSuccessListener(aVoid -> {
                        Log.i(TAG, "Deleted created auth user " + uid);
                        btnRegister.setEnabled(true);
                        btnRegister.setText("Create Account");
                        toast("Registration failed: " + (original != null ? original.getMessage() : "unknown"));
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to delete newly created auth user " + uid, e);
                        btnRegister.setEnabled(true);
                        btnRegister.setText("Create Account");
                        toast("Registration failed and cleanup incomplete: " + e.getMessage());
                    });
        } else {
            // If we cannot delete auth user (not signed in as them) at least notify
            Log.w(TAG, "Cannot delete created auth user (not signed in as them). uid=" + uid);
            btnRegister.setEnabled(true);
            btnRegister.setText("Create Account");
            toast("Registration failed: " + (original != null ? original.getMessage() : "unknown") + ". Contact admin for cleanup.");
        }
    }

    // general cleanup wrapper used earlier
    private void handleFailureCleanup(final String uidToDelete, final String usernameToRelease, final Exception original) {
        if (!TextUtils.isEmpty(usernameToRelease)) {
            releaseUsername(usernameToRelease);
        }
        if (!TextUtils.isEmpty(uidToDelete)) {
            attemptDeleteAuthUser(uidToDelete, original);
        } else {
            btnRegister.setEnabled(true);
            btnRegister.setText("Create Account");
            toast("Registration failed: " + (original != null ? original.getMessage() : "unknown"));
        }
    }

    private String safe(EditText et) {
        return et == null || et.getText() == null ? "" : et.getText().toString().trim();
    }

    private void toast(String m) {
        Toast.makeText(this, m, Toast.LENGTH_LONG).show();
    }
}