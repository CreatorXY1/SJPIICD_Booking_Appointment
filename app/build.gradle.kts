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
        multiDexEnabled = true
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

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    // MultiDex support
    implementation("androidx.multidex:multidex:2.0.1")

    // Firebase BOM (Bill of Materials) - controls all Firebase versions
    implementation(platform(libs.firebase.bom))

    // Firebase dependencies (versions managed by BOM)
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.storage)
    implementation(libs.firebase.functions)
    implementation(libs.firebase.appcheck.debug)
    implementation(libs.firebase.appcheck.playintegrity)

    // AndroidX libraries
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)

    // Third-party libraries
    implementation(libs.okhttp)
    implementation(libs.gson)
    implementation(libs.firebase.database)

    // Testing libraries
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}