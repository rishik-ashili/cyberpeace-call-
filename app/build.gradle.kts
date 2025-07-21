plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.namashiva"
    compileSdk = 36

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.example.namashiva"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Expose API keys from local.properties
        val geminiApiKey: String? = project.findProperty("GEMINI_API_KEY") as String?
        val elevenLabsApiKey: String? = project.findProperty("ELEVENLABS_API_KEY") as String?
        buildConfigField("String", "GEMINI_API_KEY", "\"${geminiApiKey ?: ""}\"")
        buildConfigField("String", "ELEVENLABS_API_KEY", "\"${elevenLabsApiKey ?: ""}\"")
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
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation(libs.retrofit2)
    implementation(libs.converter.gson)
    implementation(libs.okhttp3.logging.interceptor)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}