package com.example.sjpiicdapp;

/**
 * Cloudinary configuration class
 *
 * SETUP INSTRUCTIONS:
 * ===================
 *
 * 1. CREATE ACCOUNT:
 *    - Go to https://cloudinary.com and sign up (free tier available)
 *
 * 2. GET YOUR CLOUD NAME:
 *    - Login to Cloudinary Dashboard
 *    - You'll see it at the top: "Cloud name: your_cloud_name"
 *    - Example: "dkxyz12345" or "mycompany-prod"
 *    - Copy this EXACT value below
 *
 * 3. CREATE UNSIGNED UPLOAD PRESET:
 *    - In Dashboard, go to: Settings → Upload (left sidebar)
 *    - Scroll to "Upload presets" section
 *    - Click "Add upload preset" button
 *    - Configure:
 *      • Preset name: student_permits (or any name you choose)
 *      • Signing mode: UNSIGNED (very important!)
 *      • Folder: permits (optional but recommended)
 *    - Click "Save"
 *    - Copy the preset name you created
 *
 * 4. PASTE VALUES BELOW:
 *    - Replace CLOUD_NAME with your actual cloud name
 *    - Replace UPLOAD_PRESET with your preset name
 *
 * SECURITY NOTE:
 * ==============
 * Unsigned presets are safe for client uploads because:
 * - You can restrict upload folders
 * - You can set size limits
 * - You can enable moderation
 * - Configure these in your preset settings
 */
public class CloudinaryConfig {

    // ========================================
    // CONFIGURATION - UPDATE THESE VALUES
    // ========================================

    /**
     * Your Cloudinary cloud name
     * Find this at the top of your Cloudinary dashboard
     *
     * Example values: "dkxyz12345", "mycompany-cloud", "student-portal"
     *
     * CURRENT: "student_permit" (likely needs to be changed!)
     */
    public static final String CLOUD_NAME = "dnddtq1v4";

    /**
     * Your unsigned upload preset name
     * This is the preset you created in Settings → Upload
     *
     * Example values: "ml_default", "unsigned_uploads", "student_permits"
     *
     * CURRENT: "student_permit" (must be a preset you created!)
     */
    public static final String UPLOAD_PRESET = "student_permit";

    // ========================================
    // AUTO-GENERATED - DO NOT EDIT BELOW
    // ========================================

    /**
     * Generated upload URL (automatically constructed)
     */
    public static final String UPLOAD_URL =
            "https://api.cloudinary.com/v1_1/" + CLOUD_NAME + "/image/upload";

    /**
     * Placeholder values that indicate configuration is incomplete
     */
    private static final String PLACEHOLDER_CLOUD = "dnddtq1v4";
    private static final String PLACEHOLDER_PRESET = "student_permit";

    /**
     * Check if Cloudinary is properly configured
     *
     * Returns true if:
     * - CLOUD_NAME is not a placeholder value
     * - UPLOAD_PRESET is not a placeholder value
     * - Both values are not empty
     *
     * @return true if configured, false otherwise
     */
    public static boolean isConfigured() {
        // Check if still using placeholder values
        if (CLOUD_NAME.equals(PLACEHOLDER_CLOUD) ||
                UPLOAD_PRESET.equals(PLACEHOLDER_PRESET)) {
            return false;
        }

        // Check if values are empty
        if (CLOUD_NAME == null || CLOUD_NAME.trim().isEmpty() ||
                UPLOAD_PRESET == null || UPLOAD_PRESET.trim().isEmpty()) {
            return false;
        }

        // Additional sanity checks
        if (CLOUD_NAME.contains("your_") || CLOUD_NAME.contains("TODO") ||
                UPLOAD_PRESET.contains("your_") || UPLOAD_PRESET.contains("TODO")) {
            return false;
        }

        return true;
    }

    /**
     * Get a detailed configuration status message
     * Useful for debugging
     *
     * @return Configuration status message
     */
    public static String getConfigStatus() {
        if (!isConfigured()) {
            return "Cloudinary NOT configured. Please update CloudinaryConfig.java with your credentials.";
        }
        return "Cloudinary configured: " + CLOUD_NAME + " / " + UPLOAD_PRESET;
    }

    /**
     * Validate the configuration and throw if invalid
     * Call this before attempting uploads
     *
     * @throws IllegalStateException if not properly configured
     */
    public static void requireConfigured() {
        if (!isConfigured()) {
            throw new IllegalStateException(
                    "Cloudinary is not properly configured. " +
                            "Please update CloudinaryConfig.java with your cloud name and upload preset. " +
                            "See class documentation for setup instructions."
            );
        }
    }
}