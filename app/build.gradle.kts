plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val supabaseUrl = System.getenv("SUPABASE_URL") ?: ""
val supabaseAnonKey = System.getenv("SUPABASE_ANON_KEY") ?: ""

android {
    namespace = "com.callersphere.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.callersphere.app"
        minSdk = 29
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0"

        // Public client configuration only. Never put a Supabase service-role key here.
        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}
