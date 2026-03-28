plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    id("com.google.gms.google-services")
}

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

fun loadEnvValue(key: String): String {
    val envCandidates = listOf(
        rootProject.file("../backend/.env"),
        rootProject.file("../../backend/.env"),
        rootProject.file(".env"),
    )
    val envFile = envCandidates.firstOrNull { it.exists() } ?: return ""
    val line = envFile.readLines().firstOrNull { raw ->
        val trimmed = raw.trim()
        trimmed.isNotEmpty() && !trimmed.startsWith("#") && trimmed.startsWith("$key=")
    } ?: return ""
    return line.substringAfter("=").trim().trim('"')
}

android {
    namespace = "com.womanglobal.connecther"
    compileSdk = 35

    // Fix: duplicate META-INF/versions/9/OSGI-INF/MANIFEST.MF from transitive jars.
    packaging {
        resources {
            excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
    }

    defaultConfig {
        applicationId = "com.womanglobal.connecther"
        minSdk = 24
        targetSdk = 34
        versionCode = 5
        versionName = "1.5"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Optional at runtime, but required for compilation of Supabase integration.
        // Populate via CI/secrets or local gradle.properties if you enable Supabase features.
        val supabaseUrl = loadEnvValue("SUPABASE_URL")
        val supabaseAnonKey = loadEnvValue("SUPABASE_ANON_KEY")
        val paystackPublicKey = loadEnvValue("PAYSTACK_PUBLIC_KEY").ifEmpty {
            (project.findProperty("PAYSTACK_PUBLIC_KEY") as? String)?.trim().orEmpty()
        }.ifEmpty {
            // Dev fallback only — override via backend/.env or gradle.properties for production.
            "pk_test_a4f61b52dfd676787a999a12a741037ad4e11792"
        }
        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")
        buildConfigField("String", "PAYSTACK_PUBLIC_KEY", "\"${paystackPublicKey.replace("\"", "\\\"")}\"")
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    buildFeatures {
        compose = true
        viewBinding = true // ✅ Ensure View Binding is enabled
        buildConfig = true
    }

}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
    }
}

dependencies {
    // Import the Firebase BoM
    implementation(platform("com.google.firebase:firebase-bom:33.11.0"))
    // Paystack native PaymentSheet (multi-channel: card, USSD, bank, mobile money, etc.)
    implementation("com.paystack.android:paystack-ui:0.0.10")
    // TODO: Add the dependencies for Firebase products you want to use
    // When using the BoM, don't specify versions in Firebase dependencies
    implementation("com.google.firebase:firebase-messaging")
    //Analtics
    implementation("com.google.firebase:firebase-analytics")

    // AndroidX Core dependencies
    implementation(libs.androidx.core.ktx)
    // Provides Theme.SplashScreen and windowSplashScreen* attrs used in res/values-night/themes.xml
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.activity)
    implementation(libs.play.services.location)

    // Auth
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.android.gms:play-services-auth:21.4.0")

    // Supabase Kotlin (Postgrest/Auth/Storage) + engine
    implementation("io.github.jan-tennert.supabase:postgrest-kt:3.4.1")
    implementation("io.github.jan-tennert.supabase:auth-kt:3.4.1")
    implementation("io.github.jan-tennert.supabase:storage-kt:3.4.1")
    implementation("io.ktor:ktor-client-okhttp:3.4.1")

    // Testing dependencies
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // AndroidX UI Components
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)

    // ✅ View Binding Dependency
    implementation(libs.androidx.viewbinding)

    // Material Components (for UI like BottomNavigationView)
    implementation(libs.material.v190)

    // ✅ Retrofit for HTTP API requests
    implementation(libs.retrofit)
    implementation(libs.converter.gson)

    // ✅ OkHttp for Networking
    implementation(libs.okhttp)
    implementation(libs.logging.interceptor)
    implementation(libs.okhttp.v491)  // Latest OkHttp version

    // ✅ Gson for JSON parsing
    implementation(libs.gson)

    // ✅ Glide for Image Loading
    implementation(libs.glide)
    annotationProcessor(libs.glide.compiler)

    // ✅ Material Calendar View
    implementation(libs.material.calendarview)

    // ✅ Mock Retrofit for Testing
    implementation(libs.retrofit)
    implementation(libs.converter.gson)
    testImplementation(libs.retrofit.mock)
}

// ✅ Fix NoSuchMethodError by forcing dependency resolution
configurations.all {
    resolutionStrategy {
        force("androidx.core:core:1.15.0") // Forces latest version of androidx.core
        force("androidx.activity:activity:1.10.0")
        force("androidx.activity:activity-ktx:1.10.0")
        force("androidx.activity:activity-compose:1.9.3")
        force("androidx.browser:browser:1.8.0")
    }
}
