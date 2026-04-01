package com.womanglobal.connecther.data


import java.io.Serializable

data class User(
    val id: String,
    val first_name: String,
    val last_name: String,
    val title: String?,
    val user_name: String,
    val nat_id: String?,
    val dob: String?,
    val gender: String?,
    val occupation: String?,
    val pic: String?,
    val isIdVerified: Boolean?,
    val isMobileVerified: Boolean?,
    val isAvailable: Boolean = true,
    val details: String?,
    val phoneNumber: String?,
    val country: String?,
    val county: String?,
    val area_name: String?,
    val email: String? = null,
    val userDbId: Int? = null,
    val isServiceProvider: Boolean? = null,
    val isProviderApplicationPending: Boolean? = null,
    val workingHours: String? = null,
    val availableForBooking: Boolean? = null,
    val professionalTitle: String? = null
) : Serializable
