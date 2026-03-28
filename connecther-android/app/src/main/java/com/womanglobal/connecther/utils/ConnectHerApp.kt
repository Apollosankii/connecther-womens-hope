package com.womanglobal.connecther.utils

import android.app.Application
import android.util.Log
import com.clerk.api.Clerk
import com.womanglobal.connecther.BuildConfig
import java.util.concurrent.Executors

class ConnectHerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Log full stack trace for "Cannot read the array length" and similar crashes
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, t ->
            Log.e("ConnectHerApp", "Uncaught: ${t.message}", t)
            defaultHandler?.uncaughtException(thread, t)
        }
        val key = BuildConfig.CLERK_PUBLISHABLE_KEY
        if (key.startsWith("pk_test_") || key.startsWith("pk_live_")) {
            if (!key.contains("replace-with-your") && key.length > 20) {
                try {
                    Clerk.initialize(this, publishableKey = key)
                } catch (e: Exception) {
                    Log.e("ConnectHerApp", "Clerk init failed", e)
                }
            } else {
                Log.w("ConnectHerApp", "Clerk key is still a placeholder. Add CLERK_PUBLISHABLE_KEY to gradle.properties (get it from dashboard.clerk.com)")
            }
        } else {
            Log.w("ConnectHerApp", "Invalid CLERK_PUBLISHABLE_KEY format. Use pk_test_... or pk_live_...")
        }
        ThemeHelper.applyTheme(this)
        // Defer non-critical init to background so launcher activity shows faster
        val app = this
        Executors.newSingleThreadExecutor().execute {
            ServiceBuilder.init(app)
            CurrentUser.initialize(app)
        }
    }
}
