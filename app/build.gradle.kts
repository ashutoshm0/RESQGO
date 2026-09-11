import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.google.services)
}

// Secrets live in local.properties (gitignored), never in source.
// The map itself needs no key — it uses osmdroid with Esri tiles.
val fast2smsKey: String = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}.getProperty("FAST2SMS_API_KEY", "")

android {
    namespace = "com.example.resqgo"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.resqgo"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Fast2SMS credential is injected from local.properties so it never
        // lands in source control. Empty key simply disables that SOS layer.
        buildConfigField("String", "FAST2SMS_API_KEY", "\"" + fast2smsKey + "\"")
    }

    buildTypes {
        release {
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
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.viewpager2)
    implementation(libs.androidx.security.crypto)
    
    // Lifecycle
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    
    // Coroutines
    implementation(libs.coroutines.android)
    
    // Room Database
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    
    // Play Services Location & Maps
    implementation(libs.play.services.location)
    implementation(libs.okhttp)
    
    // WorkManager
    implementation(libs.workmanager.ktx)

    // OpenStreetMap map view — no API key, no billing
    implementation(libs.osmdroid.android)

    // Firebase (BOM)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth.ktx)
    implementation(libs.firebase.firestore.ktx)
    implementation(libs.firebase.messaging.ktx)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}