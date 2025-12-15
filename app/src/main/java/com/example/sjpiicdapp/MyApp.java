package com.example.sjpiicdapp;

import android.app.Application;
import android.util.Log;

import com.google.firebase.FirebaseApp;
import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory;
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory;

/**
 * Main Application class
 * Initializes Firebase and App Check
 */
public class MyApp extends Application {
    private static final String TAG = "MyApp";

    @Override
    public void onCreate() {
        super.onCreate();

        try {
            // Initialize Firebase (must be first)
            FirebaseApp.initializeApp(this);
            Log.d(TAG, "Firebase initialized");

            // Configure App Check
            configureAppCheck();

            // Connect to emulators (DEBUG builds only)
            FirebaseEmulatorConnector.connectEmulatorsIfDebug();

            Log.i(TAG, "Application initialization complete");
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize application", e);
        }
    }

    /**
     * Configure Firebase App Check
     * Uses Debug provider in DEBUG builds, Play Integrity in production
     */
    private void configureAppCheck() {
        FirebaseAppCheck firebaseAppCheck = FirebaseAppCheck.getInstance();

        if (BuildConfig.DEBUG) {
            // Debug provider for development/testing
            firebaseAppCheck.installAppCheckProviderFactory(
                    DebugAppCheckProviderFactory.getInstance()
            );
            Log.d(TAG, "App Check: Debug provider installed");
        } else {
            // Play Integrity provider for production
            firebaseAppCheck.installAppCheckProviderFactory(
                    PlayIntegrityAppCheckProviderFactory.getInstance()
            );
            Log.d(TAG, "App Check: Play Integrity provider installed");
        }
    }
}