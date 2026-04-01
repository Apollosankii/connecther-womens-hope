package com.womanglobal.connecther.data

import com.google.gson.annotations.SerializedName

data class Job(
    val client: String,
    val provider: String,
    @SerializedName("Service") val service: String,
    @SerializedName("Price") val price: Double,
    val location: String,
    val job_id: Int,
    /** Legacy alias: you submitted a review for this job. */
    val rated: Boolean,
    /** Legacy alias: your star rating for this job. */
    val score: Float,
    @SerializedName("my_review_submitted") val my_review_submitted: Boolean = rated,
    @SerializedName("my_stars") val my_stars: Float = score,
    @SerializedName("their_review_submitted") val their_review_submitted: Boolean = false,
    @SerializedName("their_stars") val their_stars: Float = 0f,
    /** ISO-8601 from RPC when present: job created / booking accepted. */
    @SerializedName("started_at") val started_at: String? = null,
    /** ISO-8601 when the seeker marked the job complete (see complete_my_job). */
    @SerializedName("completed_at") val completed_at: String? = null,
)
