package com.womanglobal.connecther.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ServiceBuilder {
//    private const val BASE_URL = "http://10.0.2.2:8001/"
//    private const val BASE_URL = "https://5a00-41-90-176-210.ngrok-free.app/"
    private const val BASE_URL = "https://api.womanshope.org/"


//    private const val BASE_URL = "https://localhost:8001/"  // Replace with actual base URL
//    private const val BASE_URL = "https://api.womanglobal.com/"  // Replace with actual base URL

    private var sharedPreferences: SharedPreferences? = null

    // Function to initialize SharedPreferences
    fun init(context: Context) {
        sharedPreferences = context.getSharedPreferences("user_session", Context.MODE_PRIVATE)
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY // Log request & response details
    }

    // Interceptor for Authentication Header
    private val authInterceptor = Interceptor { chain ->
        val requestBuilder = chain.request().newBuilder()

        val token = sharedPreferences?.getString("auth_token", null)
        if (!token.isNullOrEmpty()) {
            Log.e("Patrice", "Adding Token: Bearer $token") // Debugging
            requestBuilder.addHeader("Authorization", "Bearer $token")
        } else {
            Log.e("Patrice", "No Token Found!") // Debugging
        }

        chain.proceed(requestBuilder.build())
    }


    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .addInterceptor(authInterceptor)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    fun <T> buildService(service: Class<T>): T {
        return retrofit.create(service)
    }

    fun getBaseUrl(): String = BASE_URL
}
