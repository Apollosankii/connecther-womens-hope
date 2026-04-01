package com.womanglobal.connecther.utils

import android.app.Application
import com.womanglobal.connecther.supabase.SupabaseTokenStore

class ConnectHerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ThemeHelper.applyTheme(this)
        CurrentUser.initialize(this)
        SupabaseTokenStore.init(this)
        // After cold start, sync token when Firebase returns it (works once JWT exists from prior session).
        PushRegistration.register(this)
    }
}
