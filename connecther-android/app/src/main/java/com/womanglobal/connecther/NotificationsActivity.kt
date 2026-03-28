package com.womanglobal.connecther

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.womanglobal.connecther.adapters.NotificationsAdapter
import com.womanglobal.connecther.data.Notification
import com.womanglobal.connecther.services.ApiService
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class NotificationsActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyMessage: TextView
    private var notificationsAdapter: NotificationsAdapter = NotificationsAdapter(emptyList())
    private val apiService: ApiService by lazy { ApiServiceFactory.createApiService() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notifications)

        recyclerView = findViewById(R.id.recyclerView)
        emptyMessage = findViewById(R.id.emptyMessage)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = notificationsAdapter

        loadNotifications()
    }

    private fun showEmptyState(show: Boolean) {
        emptyMessage.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun loadNotifications() {
        val call: Call<List<Notification>> = apiService.getNotifications()
        call.enqueue(object : Callback<List<Notification>> {
            override fun onResponse(
                call: Call<List<Notification>>,
                response: Response<List<Notification>>
            ) {
                val notifications = if (response.isSuccessful) {
                    response.body() ?: emptyList()
                } else {
                    emptyList()
                }
                notificationsAdapter = NotificationsAdapter(notifications)
                recyclerView.adapter = notificationsAdapter
                showEmptyState(notifications.isEmpty())
            }

            override fun onFailure(call: Call<List<Notification>>, t: Throwable) {
                notificationsAdapter = NotificationsAdapter(emptyList())
                recyclerView.adapter = notificationsAdapter
                showEmptyState(true)
            }
        })
    }
}
