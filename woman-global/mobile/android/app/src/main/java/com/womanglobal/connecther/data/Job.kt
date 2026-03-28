package com.womanglobal.connecther.data

import com.google.gson.annotations.SerializedName

data class Job(
    val client: String,
    val provider: String,
    @SerializedName("Service") val service: String,
    @SerializedName("Price") val price: Double,
    val location: String,
    val job_id: Int,
    val rated: Boolean,
    val score : Float
)
