plugins {
    id("com.android.application")
    id("com.google.gms.google-services") version "4.4.4"
}


android {
    namespace = "com.example.sjpiicdapp"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.sjpiicdapp"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    // If you're using Kotlin, enable this (remove if not)
    // kotlinOptions { jvmTarget = "11" }
}

dependencies {
    // BOM ensures consistent versions for firebase libs
    implementation(platform("com.google.firebase:firebase-bom:34.6.0"))

    // Prefer KTX variants if you're writing modern code
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-storage")
    implementation("com.google.firebase:firebase-functions")
    implementation("com.google.firebase:firebase-appcheck-debug:16.0.0")          // dev only
    implementation("com.google.firebase:firebase-appcheck-playintegrity:16.0.0") // release
    implementation("com.google.firebase:firebase-appcheck-debug")
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    implementation("com.google.code.gson:gson:2.10.1")


    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.storage)
    implementation(libs.firebase.functions)

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
