package com.womanglobal.connecther

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.womanglobal.connecther.adapters.NotificationsAdapter
import com.womanglobal.connecther.services.ApiService
import com.womanglobal.connecther.data.Notification
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class NotificationsActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var notificationsAdapter: NotificationsAdapter
    private val apiService: ApiService by lazy { ApiServiceFactory.createApiService() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notifications)

        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        loadNotifications()
    }

    private fun loadNotifications() {
        val call: Call<List<Notification>> = apiService.getNotifications()
        call.enqueue(object : Callback<List<Notification>> {
            override fun onResponse(
                call: Call<List<Notification>>,
                response: Response<List<Notification>>
            ) {
                if (response.isSuccessful) {
                    val notifications = response.body() ?: emptyList()
                    notificationsAdapter = NotificationsAdapter(notifications)
                    recyclerView.adapter = notificationsAdapter
                } else {
                    Toast.makeText(
                        this@NotificationsActivity,
                        "Failed to load notifications",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onFailure(call: Call<List<Notification>>, t: Throwable) {
                Toast.makeText(
                    this@NotificationsActivity,
                    "Error: ${t.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
    }
}
