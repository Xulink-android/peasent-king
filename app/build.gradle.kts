plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "fr.local.peasantassistant"
    compileSdk = 35

    defaultConfig {
        applicationId = "fr.local.peasantassistant"
        minSdk = 32
        targetSdk = 34
        versionCode = 14
        versionName = "1.4-diagnostic"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation("com.google.mlkit:text-recognition:16.0.1")
}
