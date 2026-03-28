package com.womanglobal.connecther.services

import com.google.gson.annotations.SerializedName
import com.womanglobal.connecther.data.Job
import com.womanglobal.connecther.data.Notification
import com.womanglobal.connecther.data.ProblemReport
import com.womanglobal.connecther.data.Service
import com.womanglobal.connecther.data.User
import com.womanglobal.connecther.data.Worker
import okhttp3.MultipartBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url

interface ApiService {

    @GET("users/search")
    fun searchUsers(
        @Query("query") query: String
    ): Call<List<User>>

    @GET("workers")
    fun getWorkers(): Call<List<Worker>>

    @Headers("Content-Type: application/json")
    @POST("/user/sign-up")
    fun registerUser(@Body request: RegisterRequest): Call<RegisterResponse>

    @Headers("Content-Type: application/json")
    @POST("/login")
    fun loginUser(@Body request: LoginRequest): Call<LoginResponse>

    @GET("notifications")
    fun getNotifications(): Call<List<Notification>>

    @GET("services") // Assuming "services" is the endpoint
    fun getServices(): Call<ServiceResponse>

    @Headers("Content-Type: application/json")
    @POST("/reportProblem")
    fun reportProblem(@Body problemReport: ProblemReport): Call<Void>

    @GET("users")
    fun getUsersForCategory(@Query("category") category: String): Call<List<User>>

    @GET("/booked-dates")
    fun getBookedDates(): Call<List<String>>

    @GET("jobs")
    fun getJobsForDate(@Query("date") date: Long): Call<List<Job>>

    @Headers("Content-Type: application/json")
    @POST("/updateUserInfo")
    fun updateUserInfo(@Body request: UpdateUserRequest): Call<Void>

    @GET("job/history")
    fun getJobHistory(): Call<List<Job>>

    @Headers("Content-Type: application/json")
    @GET("/help")
    fun helpRequest(): Call<Void>

    @GET("/provider/near/me/{service_id}")
    fun getProvidersNearMe(@Path("service_id") serviceId: String): Call<List<User>>


    @Headers("Content-Type: application/json")
    @POST("/liveloc/me")
    fun sendLocationUpdate(@Body request: LocationRequestBody): Call<Void>

    @Headers("Content-Type: application/json")
    @GET("/location/me")
    fun getUserLocation(): Call<LocationResponse>


    @Headers("Content-Type: application/json")
    @GET("/chats/{userId}")
    fun getChatHistory(@Path("userId") userId: String): Call<List<ChatMessage>>

    @Headers("Content-Type: application/json")
    @POST("/chats/send")
    fun sendMessage(@Body request: ChatMessageRequest): Call<Void>

    @Headers("Content-Type: application/json")
    @GET("users/me")
    fun getUserProfile(): Call<GetProfileResponse>


    @Headers("Content-Type: application/json")
    @POST("/provider/engage")
    fun engageProvider(@Body request: EngageRequest): Call<Any>

    @GET("/quotes")
    fun getConversations(): Call<List<Conversation>>

    @GET("/chats/{chat_code}")
    fun getChatMessages(@Path("chat_code") chatCode: String): Call<List<ChatMessage>>

    @POST("/chat/{chat_code}")
    fun sendChatMessage(@Path("chat_code") chatCode: String, @Body message: ChatMessageRequest): Call<String>

    @POST
    fun hireUser(@Url url: String): Call<String>

    @GET("/jobs/pending")
    fun getPendingJobs(): Call<List<Job>>

    @POST
    fun hireUser(@Url url: String, @Body request: HireRequest): Call<HireResponse>

    @GET("/jobs/complete")
    fun getCompletedJobs(): Call<List<Job>>

    @POST("/job/pay/{job_id}")
    fun completeJob(@Path("job_id") jobId: Int,) : Call<Void>

    @POST("/update/password")
    fun changePassword(@Body request : ChangePasswordRequest) : Call<String>

    @Multipart
    @POST("profpic/upload")
    fun uploadProfilePic(@Part image: MultipartBody.Part): Call<String>

    @POST("rate/job")
    fun rateJob(@Body request : RateJobRequest) : Call<String>
    
    @POST("update/device_token")
    fun updateDeviceToken(@Body request : DeviceTokenRequest) : Call<String>

    @Headers("Content-Type: application/json")
    @POST("/emergency/gbv")
    fun reportGbvEmergency(@Body request: GbvEmergencyRequest): Call<Void>

    @Headers("Content-Type: application/json")
    @POST("/emergency/medical")
    fun reportMedicalEmergency(@Body request: MedicalEmergencyRequest): Call<Void>

    @Headers("Content-Type: application/json")
    @POST("/user/provider/sign-up")
    fun submitProviderApplication(@Body request: ProviderSignUpRequest): Call<ProviderSignUpResponse>
}

data class DeviceTokenRequest(
    @SerializedName("reg_token")
    val regToken : String,
    val device : String
)

data class HireRequest(
    val price: Double
)
data class HireResponse(
    val msg: String
)
data class GetProfileResponse(
    val profile: User,
)

data class EngageRequest(
    val provider_id: String,
    val service_id: Int
)

data class EngageResponse(
    val chatCode: String
)

data class Conversation(
    val quote_code: String,
    val chat_id: String,
    val provider: String,
    val client: String,
    val service: String,
    val text : String,
    val time : String
)

data class ChatMessage(
    val id: String,
    val sender_id: String,
    val receiverId: String,
    val message: String,
    val timestamp: Long,
    val isRead: Boolean
)

data class ChatMessageRequest(
//    val senderId: String,
//    val receiverId: String,
//    val message: String
    val content: String
)


data class LoginResponse(
    val info: String,
    val token: String,
    val provider: Boolean
)

data class LocationResponse(
    val latitude: Double,
    val longitude: Double
)


data class LocationRequestBody(
    val latitude: Double,
    val longitude: Double
)


data class ServiceResponse(
    val services: List<Service>
)

data class UpdateUserRequest(
    val fullName: String,
    val email: String,
    val phone: String,
    val occupation: String
)

data class LoginRequest(
    val phone: String,
    val password: String
)

/*
* Commented attributes out instead of using null because they're not needed when user registers
* */
data class RegisterRequest(
    val title: String,
    val first_name: String,
    val last_name: String,
    val phone: String,
    val email: String,
    val user_id: String,
    val password: String
)

data class RegisterResponse(
    val info: String,
    val response: List<String>
)

data class ChangePasswordRequest(
    val old : String,
    val new : String,
    val confirm : String
)

data class RateJobRequest(
    val job_id : Int,
    val rate : Float
)

data class GbvEmergencyRequest(
    val userId: String,
    val latitude: Double?,
    val longitude: Double?
)

data class MedicalEmergencyRequest(
    val userId: String,
    val latitude: Double?,
    val longitude: Double?
)

/** Request body for POST /user/provider/sign-up (apply as service provider). */
data class ProviderSignUpRequest(
    val gender: String? = null,
    @SerializedName("birth_date") val birthDate: String? = null,
    val country: String? = null,
    val county: String? = null,
    @SerializedName("area_name") val areaName: String? = null,
    @SerializedName("prof_pic") val profPic: String? = null,
    @SerializedName("nat_id") val natId: String? = null,
    @SerializedName("emm_cont_1") val emmCont1: String? = null,
    @SerializedName("emm_cont_2") val emmCont2: String? = null
)

data class ProviderSignUpResponse(
    val msg: String? = null,
    val info: String? = null
)