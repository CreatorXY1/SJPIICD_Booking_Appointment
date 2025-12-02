package com.example.sjpiicdapp;

import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.BuildConfig;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.functions.FirebaseFunctions;

public class FirebaseEmulatorConnector {

    /**
     * Call this early in Application.onCreate() but only for debug builds.
     * Change the host/ports here if you change firebase.json emulator ports.
     */
    public static void connectEmulatorsIfDebug() {
        if (!BuildConfig.DEBUG) return; // safe guard: only debug builds

        // When running on Android emulator use 10.0.2.2 to reach host machine's localhost.
        // If you run on a physical device replace this with your PC's LAN IP, e.g. "192.168.1.100"
        final String host = "10.0.2.2";

        // Auth emulator port (unchanged)
        FirebaseAuth.getInstance().useEmulator(host, 9099);

        // Firestore emulator port -> change to match your firebase.json (you set it to 8085)
        FirebaseFirestore.getInstance().useEmulator(host, 8080);

        // Storage emulator port (if you enabled storage emulator)
        FirebaseStorage.getInstance().useEmulator(host, 9199);

        // Functions emulator port
        FirebaseFunctions.getInstance().useEmulator(host, 5001);

        Log.i("FirebaseEmulator", "Connected to emulators: auth(9099), firestore(8085), storage(9199), functions(5001)");
    }
}

