package com.womanglobal.connecther.utils

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.womanglobal.connecther.data.User

object CurrentUser {
    private var user: User? = null
    private lateinit var sharedPreferences: SharedPreferences
    private val gson = Gson()

    fun initialize(context: Context) {
        sharedPreferences = context.getSharedPreferences("user_session", Context.MODE_PRIVATE)
        loadUser()
    }

    fun setUser(user: User) {
        this.user = user
        saveUser(user)
    }

    fun getUser(): User? {
        if (user == null) {
            loadUser() // Reload from SharedPreferences if `user` is null
        }
        return user
    }

    fun clear() {
        user = null
        sharedPreferences.edit().remove("current_user").apply()
    }

    private fun saveUser(user: User) {
        val userJson = gson.toJson(user)
        sharedPreferences.edit().putString("current_user", userJson).apply()
    }

    private fun loadUser() {
        val userJson = sharedPreferences.getString("current_user", null)
        userJson?.let {
            user = gson.fromJson(it, User::class.java)
        }
    }
}
