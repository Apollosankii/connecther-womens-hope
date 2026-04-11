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
    /** True when this row is the seeker (quote client); false when the provider. From get_pending_jobs / get_completed_jobs. */
    @SerializedName("i_am_client") val i_am_client: Boolean = false,
    /** Human-readable lines from job.location_extra (building, floor, unit, …). */
    val location_detail_display: String = "",
    @SerializedName("arrived_at") val arrived_at: String? = null,
    @SerializedName("work_started_at") val work_started_at: String? = null,
    @SerializedName("site_photo_path") val site_photo_path: String? = null,
    @SerializedName("service_id") val service_id: Int? = null,
    @SerializedName("safety_checkins_required") val safety_checkins_required: Boolean = false,
    @SerializedName("safety_checkin_interval_min") val safety_checkin_interval_min: Int = 60,
)
