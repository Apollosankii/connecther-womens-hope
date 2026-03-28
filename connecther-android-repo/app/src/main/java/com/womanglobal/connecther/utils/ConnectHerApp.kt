package com.womanglobal.connecther.utils

import android.app.Application
import android.util.Log
import java.util.concurrent.Executors

class ConnectHerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, t ->
            Log.e("ConnectHerApp", "Uncaught: ${t.message}", t)
            defaultHandler?.uncaughtException(thread, t)
        }
        ThemeHelper.applyTheme(this)
        val app = this
        Executors.newSingleThreadExecutor().execute {
            ServiceBuilder.init(app)
            CurrentUser.initialize(app)
        }
    }
}
