package com.example.sjpiicdapp;

import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.functions.FirebaseFunctions;

/**
 * Centralized Firebase Emulator Configuration
 *
 * Call connectEmulatorsIfDebug() once in MyApp.onCreate()
 * Automatically connects to local emulators in DEBUG builds only
 */
public class FirebaseEmulatorConnector {

    private static final String TAG = "FirebaseEmulator";
    private static boolean isConnected = false;

    /**
     * Connect to Firebase emulators for local development.
     * Only runs in DEBUG builds. Safe to call multiple times (idempotent).
     *
     * HOST CONFIGURATION:
     * - Android Emulator: Use "10.0.2.2"
     * - Genymotion: Use "10.0.3.2"
     * - Physical Device: Use your PC's IP (e.g., "192.168.1.100")
     *
     * PORTS (must match firebase.json):
     * - Auth: 9099
     * - Firestore: 8080
     * - Functions: 5001
     * - Storage: 9199 (if enabled)
     * - Realtime Database: 9000 (if enabled)
     */
    public static void connectEmulatorsIfDebug() {
        // Only run in debug builds
        if (!com.example.sjpiicdapp.BuildConfig.DEBUG) {
            Log.d(TAG, "Release build - skipping emulator connection");
            return;
        }

        // Prevent multiple connections
        if (isConnected) {
            Log.d(TAG, "Emulators already connected");
            return;
        }

        try {
            // Configure host based on your device
            // Change this if using physical device or Genymotion
            final String host = getEmulatorHost();

            // Connect to emulators (order matters - Auth first)
            FirebaseAuth.getInstance().useEmulator(host, 9099);
            Log.d(TAG, "✓ Auth emulator: " + host + ":9099");

            FirebaseFirestore.getInstance().useEmulator(host, 8080);
            Log.d(TAG, "✓ Firestore emulator: " + host + ":8080");

            FirebaseFunctions.getInstance().useEmulator(host, 5001);
            Log.d(TAG, "✓ Functions emulator: " + host + ":5001");

            // Optional: only if you're using Storage
            try {
                FirebaseStorage.getInstance().useEmulator(host, 9199);
                Log.d(TAG, "✓ Storage emulator: " + host + ":9199");
            } catch (Exception e) {
                Log.w(TAG, "Storage emulator not configured: " + e.getMessage());
            }

            // Optional: only if you're using Realtime Database
            try {
                FirebaseDatabase.getInstance().useEmulator(host, 9000);
                Log.d(TAG, "✓ Database emulator: " + host + ":9000");
            } catch (Exception e) {
                Log.w(TAG, "Database emulator not configured: " + e.getMessage());
            }

            isConnected = true;
            Log.i(TAG, "========================================");
            Log.i(TAG, "Firebase Emulators Connected!");
            Log.i(TAG, "Emulator UI: http://localhost:4000");
            Log.i(TAG, "========================================");

        } catch (Exception e) {
            Log.e(TAG, "Failed to connect to emulators", e);
        }
    }

    /**
     * Determine the correct host for emulator connection
     * Override this method if you need custom logic
     */
    private static String getEmulatorHost() {
        // You can add logic here to auto-detect device type
        // For now, return the default for Android Emulator
        return "10.0.2.2";

        // For physical device, uncomment and replace with your PC's IP:
        // return "192.168.1.100";

        // For Genymotion, uncomment:
        // return "10.0.3.2";
    }

    /**
     * Check if emulators are connected
     */
    public static boolean isConnectedToEmulators() {
        return isConnected;
    }

    /**
     * Get the emulator UI URL for debugging
     */
    public static String getEmulatorUIUrl() {
        return "http://localhost:4000";
    }
}