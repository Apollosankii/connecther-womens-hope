package com.womanglobal.connecther.utils.mock

import com.womanglobal.connecther.R
import com.womanglobal.connecther.data.Job
import com.womanglobal.connecther.data.Notification
import com.womanglobal.connecther.data.ProblemReport
import com.womanglobal.connecther.data.Service
import com.womanglobal.connecther.data.User
import com.womanglobal.connecther.data.Worker
import com.womanglobal.connecther.services.ApiService
import com.womanglobal.connecther.services.ChangePasswordRequest
import com.womanglobal.connecther.services.ChatMessage
import com.womanglobal.connecther.services.ChatMessageRequest
import com.womanglobal.connecther.services.Conversation
import com.womanglobal.connecther.services.DeviceTokenRequest
import com.womanglobal.connecther.services.EngageRequest
import com.womanglobal.connecther.services.GetProfileResponse
import com.womanglobal.connecther.services.HireRequest
import com.womanglobal.connecther.services.HireResponse
import com.womanglobal.connecther.services.LocationRequestBody
import com.womanglobal.connecther.services.LocationResponse
import com.womanglobal.connecther.services.LoginRequest
import com.womanglobal.connecther.services.LoginResponse
import com.womanglobal.connecther.services.RateJobRequest
import com.womanglobal.connecther.services.GbvEmergencyRequest
import com.womanglobal.connecther.services.ProviderSignUpRequest
import com.womanglobal.connecther.services.ProviderSignUpResponse
import com.womanglobal.connecther.services.RegisterRequest
import com.womanglobal.connecther.services.RegisterResponse
import com.womanglobal.connecther.services.ServiceResponse
import com.womanglobal.connecther.services.UpdateUserRequest
import okhttp3.MultipartBody
import okhttp3.Request
import okio.Timeout
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MockApiService : ApiService {

    // Mock data list
    private val users = listOf(
        User(
            id = "1",
            first_name = "Alice Johnson",
            occupation = "Housekeeper",
            pic = "https://example.com/image1.jpg",
            isIdVerified = true,
            isMobileVerified = true,
            isAvailable = true,
            details = "Experienced housekeeper with 5 years in professional cleaning.",
            phoneNumber = "+1234567890",
            last_name = "Johnson",
            title = "Ms",
            user_name = "alice_johnson",
            nat_id = null,
            dob = null,
            gender = "Female",
            country = "Kenya",
            county = "Nairobi",
            area_name = "CBD",
        ),
        User(
            id = "2",
            first_name = "Bob Smith",
            occupation = "Gardener",
            pic = "https://example.com/image2.jpg",
            isIdVerified = false,
            isMobileVerified = true,
            isAvailable = false,
            details = "Skilled in landscape maintenance and organic gardening.",
            phoneNumber = "+1987654321",
            last_name = "Smith",
            title = "Mr",
            user_name = "bob_smith",
            nat_id = null,
            dob = null,
            gender = "Male",
            country = "Kenya",
            county = "Nairobi",
            area_name = "Westlands",
        )
    )

    override fun getJobHistory(): Call<List<Job>> {
        val jobHistory = listOf(
            Job(
                job_id = 1,
                client = "Test Client",
                provider = "Test Provider",
                service = "Housekeeping",
                price = 1000.0,
                location = "Nairobi",
                rated = false,
                score = 0f
            )
        )
        return createFakeCall(Response.success(jobHistory))
    }


    override fun getWorkers(): Call<List<Worker>> {
        val workers = users.map { user ->
            Worker(name = ""+user.first_name, imageUrl = user.pic ?: "", location = ""+user.occupation)
        }
        return createFakeCall(Response.success(workers))
    }

    override fun searchUsers(query: String): Call<List<User>> {
        val filteredUsers = users.filter { user ->
            user.first_name.contains(query, ignoreCase = true) || (user.occupation!= null  && user.occupation.contains(query, ignoreCase = true))
        }
        return createFakeCall(Response.success(filteredUsers))
    }

    override fun registerUser(request: RegisterRequest): Call<RegisterResponse> {
        return if (request.email.contains("@") && request.password.length >= 6) {
            createFakeCall(Response.success(null))
        } else {
            createFakeFailureCall(Throwable("Registration failed due to invalid email or weak password"))
        }
    }

    override fun loginUser(request: LoginRequest): Call<LoginResponse> {
        return if (request.phone == "test@example.com" && request.password == "password") {
            createFakeCall(Response.success(null))
        } else {
            createFakeFailureCall(Throwable("Login failed: Invalid credentials"))
        }
    }

    override fun getNotifications(): Call<List<Notification>> {
        val notifications = listOf(
            Notification(id = "1", title = "Booking Confirmed", message = "Your booking with Alice Johnson has been confirmed.", timestamp = "2024-11-01 10:00"),
            Notification(id = "2", title = "New Message", message = "You have a new message from Bob Smith.", timestamp = "2024-11-01 09:30")
        )
        return createFakeCall(Response.success(notifications))
    }

    override fun getServices(): Call<ServiceResponse> {
        val services = listOf(
            Service("0","Mama Fua", "https://example.com/house_managers_image.jpg", fallbackImageResId = R.mipmap.mama_fua),
            Service("0","Caregivers", "https://example.com/caregivers_image.jpg", fallbackImageResId = R.mipmap.caregivers)
        )

        val serviceResponse = ServiceResponse(services = services)

        return createFakeCall(Response.success(serviceResponse))
    }


    override fun reportProblem(problemReport: ProblemReport): Call<Void> {
        return if (problemReport.description.isNotBlank()) {
            createFakeCall(Response.success(null))
        } else {
            createFakeFailureCall(Throwable("Problem description cannot be empty"))
        }
    }

    override fun getUsersForCategory(category: String): Call<List<User>> {
        val users = when (category.lowercase()) {
            "housekeeper" -> listOf(
                User(
                    id = "1",
                    first_name = "Alice Johnson",
                    occupation = "Housekeeper",
                    pic = null,
                    isIdVerified = true,
                    isMobileVerified = true,
                    details = "Experienced housekeeper",
                    phoneNumber = "1234567890",
                    last_name = "Johnson",
                    title = "Ms",
                    user_name = "alice_johnson",
                    nat_id = null,
                    dob = null,
                    gender = "Female",
                    isAvailable = true,
                    country = "Kenya",
                    county = "Nairobi",
                    area_name = "CBD",
                )
            )
            else -> listOf()
        }
        return createFakeCall(Response.success(users))
    }

    override fun getJobsForDate(date: Long): Call<List<Job>> {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val formattedDate = sdf.format(Date(date))

        val jobs = listOf(
            Job(
                provider = "Test Provider",
                service = "Housekeeping",
                price = 1000.0,
                location = "Nairobi",
                job_id = 1,
                client = "Test Client",
                rated = false,
                score = 0f
            )
        )
        return createFakeCall(Response.success(jobs))
    }

    override fun getBookedDates(): Call<List<String>> {
        val bookedDates = listOf("2023-11-15", "2023-11-16", "2023-11-17")
        return createFakeCall(Response.success(bookedDates))
    }

    override fun updateUserInfo(request: UpdateUserRequest): Call<Void> {
        return createFakeCall(Response.success(null))
    }

    // Helper function to create a fake Retrofit Call<T> for testing
    private fun <T> createFakeCall(response: Response<T>): Call<T> {
        return object : Call<T> {
            override fun enqueue(callback: Callback<T>) {
                callback.onResponse(this, response)
            }

            override fun execute(): Response<T> = response
            override fun isExecuted(): Boolean = true
            override fun clone(): Call<T> = createFakeCall(response)
            override fun isCanceled(): Boolean = false
            override fun cancel() {}
            override fun request(): Request = Request.Builder().url("http://localhost/").build()
            override fun timeout(): Timeout = Timeout.NONE
        }
    }

    // Helper function to create a fake failure Call<T>
    private fun <T> createFakeFailureCall(throwable: Throwable): Call<T> {
        return object : Call<T> {
            override fun enqueue(callback: Callback<T>) {
                callback.onFailure(this, throwable)
            }

            override fun execute(): Response<T> {
                throw throwable
            }

            override fun isExecuted(): Boolean = true
            override fun clone(): Call<T> = createFakeFailureCall(throwable)
            override fun isCanceled(): Boolean = false
            override fun cancel() {}
            override fun request(): Request = Request.Builder().url("http://localhost/").build()
            override fun timeout(): Timeout = Timeout.NONE
        }
    }

    override fun helpRequest(): Call<Void> {
        return createFakeCall(Response.success(null))
    }

    override fun getProvidersNearMe(serviceId: String): Call<List<User>> {
        val mockProviders = when (serviceId) {
            "1" -> listOf(
                User(
                    id = "101",
                    first_name = "Alice Johnson",
                    occupation = "Housekeeper",
                    pic = "https://example.com/image1.jpg",
                    isIdVerified = true,
                    isMobileVerified = true,
                    isAvailable = true,
                    details = "Experienced housekeeper with 5 years in professional cleaning.",
                    phoneNumber = "+1234567890",
                    last_name = "Johnson",
                    title = "Ms",
                    user_name = "alice_johnson",
                    nat_id = null,
                    dob = null,
                    gender = "Female",
                    country = "Kenya",
                    county = "Nairobi",
                    area_name = "CBD",
                )
            )
            "2" -> listOf(
                User(
                    id = "102",
                    first_name = "Bob Smith",
                    occupation = "Gardener",
                    pic = "https://example.com/image2.jpg",
                    isIdVerified = false,
                    isMobileVerified = true,
                    isAvailable = false,
                    details = "Skilled in landscape maintenance and organic gardening.",
                    phoneNumber = "+1987654321",
                    last_name = "Smith",
                    title = "Mr",
                    user_name = "bob_smith",
                    nat_id = null,
                    dob = null,
                    gender = "Male",
                    country = "Kenya",
                    county = "Nairobi",
                    area_name = "Westlands",
                )
            )
            else -> emptyList()
        }

        return createFakeCall(Response.success(mockProviders))
    }

    override fun sendLocationUpdate(request: LocationRequestBody): Call<Void> {
        return createFakeCall(Response.success(null)) // Simulate successful response
    }

    override fun getUserLocation(): Call<LocationResponse> {
        val mockLocation = LocationResponse(latitude = -1.286389, longitude = 36.817223) // Nairobi, Kenya (Example)
        return createFakeCall(Response.success(mockLocation))
    }

    override fun getChatHistory(userId: String): Call<List<ChatMessage>> {
        val messages = listOf(
            ChatMessage(
                id = "1",
                sender_id = userId,
                receiverId = "support",
                message = "Hello!",
                timestamp = System.currentTimeMillis(),
                isRead = true,
            ),
            ChatMessage(
                id = "2",
                sender_id = "support",
                receiverId = userId,
                message = "Hi — how can we help?",
                timestamp = System.currentTimeMillis(),
                isRead = true,
            ),
        )
        return createFakeCall(Response.success(messages))
    }

    override fun sendMessage(request: ChatMessageRequest): Call<Void> {
        return createFakeCall(Response.success(null))
    }

    override fun getUserProfile(): Call<GetProfileResponse> {
        val profile = GetProfileResponse(
            profile = users.first(),
        )
        return createFakeCall(Response.success(profile))
    }



//    override fun getUserProfile(token: String): Call<GetProfileResponse> {
//        TODO("Not yet implemented")
//    }

    override fun engageProvider(request: EngageRequest): Call<Any> {
        return createFakeCall(Response.success(mapOf("ok" to true)))
    }

    override fun getConversations(): Call<List<Conversation>> {
        return createFakeCall(Response.success(emptyList()))
    }

    override fun getChatMessages(chatCode: String): Call<List<ChatMessage>> {
        return createFakeCall(Response.success(emptyList()))
    }

    override fun sendChatMessage(chatCode: String, message: ChatMessageRequest): Call<String> {
        return createFakeCall(Response.success("ok"))
    }

    override fun hireUser(url: String): Call<String> {
        return createFakeCall(Response.success("ok"))
    }

    override fun hireUser(url: String, request: HireRequest): Call<HireResponse> {
        return createFakeCall(Response.success(HireResponse(msg = "ok")))
    }

    override fun getPendingJobs(): Call<List<Job>> {
        return createFakeCall(Response.success(emptyList()))
    }

    override fun getCompletedJobs(): Call<List<Job>> {
        return createFakeCall(Response.success(emptyList()))
    }

    override fun completeJob(jobId: Int): Call<Void> {
        return createFakeCall(Response.success(null))
    }

    override fun changePassword(request: ChangePasswordRequest): Call<String> {
        return createFakeCall(Response.success("ok"))
    }

    override fun uploadProfilePic(image: MultipartBody.Part): Call<String> {
        return createFakeCall(Response.success("ok"))
    }

    override fun rateJob(request: RateJobRequest): Call<String> {
        return createFakeCall(Response.success("ok"))
    }

    override fun updateDeviceToken(request: DeviceTokenRequest): Call<String> {
        return createFakeCall(Response.success("ok"))
    }

    override fun reportGbvEmergency(request: GbvEmergencyRequest): Call<String> {
        return createFakeCall(Response.success("ok"))
    }

    override fun submitProviderApplication(request: ProviderSignUpRequest): Call<ProviderSignUpResponse> {
        return createFakeCall(Response.success(ProviderSignUpResponse(info = "ok")))
    }

}
