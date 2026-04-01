buildscript {
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        google()
        maven { url = uri("https://repo1.maven.org/maven2/") }
        mavenCentral()
    }
    dependencies {
        classpath("com.google.gms:google-services:4.4.2")
    }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

import java.util.Properties
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

fun loadEnvValue(key: String): String {
    val envCandidates = listOf(
        rootProject.file("../../.env"),           // woman-global/.env  (canonical)
        rootProject.file("../.env"),              // woman-global/mobile/.env
        rootProject.file(".env"),                 // woman-global/mobile/android/.env
    )
    val envFile = envCandidates.firstOrNull { it.exists() } ?: return ""
    val line = envFile.readLines().firstOrNull { raw ->
        val trimmed = raw.trim()
        trimmed.isNotEmpty() && !trimmed.startsWith("#") && trimmed.startsWith("$key=")
    } ?: return ""
    return line.substringAfter("=").trim().trim('"')
}

/** True if tasks being run include an Android *Release* assemble/bundle. */
fun isReleaseGradleTask(): Boolean =
    gradle.startParameter.taskNames.any { name ->
        name.contains("Release", ignoreCase = true) &&
            (name.contains("assemble", ignoreCase = true) ||
                name.contains("bundle", ignoreCase = true))
    }

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(keystorePropertiesFile.inputStream())
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
        }
        if (paystackPublicKey.isBlank()) {
            println(
                "WARNING: PAYSTACK_PUBLIC_KEY is missing. " +
                    "Add it to woman-global/.env (or gradle.properties). " +
                    "Otherwise Paystack PaymentSheet will fail. " +
                    "If the server uses sk_live_*, you MUST use pk_live_* here or you get 'Access code not found'.",
            )
        } else {
            val mode =
                when {
                    paystackPublicKey.startsWith("pk_live_") -> "pk_live"
                    paystackPublicKey.startsWith("pk_test_") -> "pk_test"
                    else -> "unknown_prefix"
                }
            println("ConnectHer: PAYSTACK_PUBLIC_KEY loaded ($mode) from env / gradle.properties")
        }
        check(!(paystackPublicKey.isBlank() && isReleaseGradleTask())) {
            "PAYSTACK_PUBLIC_KEY is required for release builds. Set in woman-global/.env or gradle.properties."
        }
        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")
        buildConfigField("String", "PAYSTACK_PUBLIC_KEY", "\"${paystackPublicKey.replace("\"", "\\\"")}\"")
    }

    signingConfigs {
        create("release") {
            require(keystorePropertiesFile.exists()) {
                "Release signing requires android/keystore.properties (copy keystore.properties.example)."
            }
            val storePath = keystoreProperties.getProperty("storeFile")
                ?: error("keystore.properties: missing storeFile")
            storeFile = rootProject.file(storePath)
            storePassword = keystoreProperties.getProperty("storePassword")
                ?: error("keystore.properties: missing storePassword")
            keyAlias = keystoreProperties.getProperty("keyAlias")
                ?: error("keystore.properties: missing keyAlias")
            keyPassword = keystoreProperties.getProperty("keyPassword")
                ?: error("keystore.properties: missing keyPassword")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
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

    // Kotlin 2.x + UAST: NonNullableMutableLiveDataDetector crashes (KaCallableMemberCall class vs interface).
    lint {
        disable += "NullSafeMutableLiveData"
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
    implementation("com.paystack.android:paystack-ui:0.0.11")
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

    // ✅ OkHttp (used by Supabase/Ktor client)
    implementation(libs.okhttp)

    // ✅ Gson for JSON parsing
    implementation(libs.gson)

    // ✅ Glide for Image Loading
    implementation(libs.glide)
    annotationProcessor(libs.glide.compiler)

    // ✅ Material Calendar View
    implementation(libs.material.calendarview)
}

// ✅ Fix NoSuchMethodError by forcing dependency resolution
configurations.all {
    resolutionStrategy {
        // storage-kt-android 3.4.1 POM pins -android-debug; release needs releaseApiElements from -android.
        dependencySubstitution {
            substitute(module("com.russhwolf:multiplatform-settings-no-arg-android-debug:1.3.0"))
                .using(module("com.russhwolf:multiplatform-settings-no-arg-android:1.3.0"))
            substitute(module("com.russhwolf:multiplatform-settings-coroutines-android-debug:1.3.0"))
                .using(module("com.russhwolf:multiplatform-settings-coroutines-android:1.3.0"))
        }
        force("androidx.core:core:1.15.0") // Forces latest version of androidx.core
        force("androidx.activity:activity:1.10.0")
        force("androidx.activity:activity-ktx:1.10.0")
        force("androidx.activity:activity-compose:1.9.3")
        force("androidx.browser:browser:1.8.0")
    }
}

apply(plugin = "com.google.gms.google-services")
