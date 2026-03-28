package com.womanglobal.connecther

import ApiServiceFactory
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.womanglobal.connecther.adapters.ConversationAdapter
import com.womanglobal.connecther.services.ApiService
import com.womanglobal.connecther.services.Conversation
import com.womanglobal.connecther.supabase.SupabaseData
import com.womanglobal.connecther.utils.ConversationWebSocketManager
import com.womanglobal.connecther.utils.ServiceBuilder
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ConversationsActivity : AppCompatActivity(), ConversationWebSocketManager.ConversationWebSocketCallback {
    private lateinit var conversationsRecyclerView: RecyclerView
    private lateinit var conversationAdapter: ConversationAdapter
    private val conversations = mutableListOf<Conversation>()
    private val apiService: ApiService by lazy { ApiServiceFactory.createApiService() }

    private val handler = Handler(Looper.getMainLooper())
    private var conversationWs: ConversationWebSocketManager? = null
    private var usingWebSocket = false

    private val conversationRefreshRunnable = object : Runnable {
        override fun run() {
            if (!usingWebSocket) {
                loadConversations()
                handler.postDelayed(this, 5000)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_conversations)

        conversationsRecyclerView = findViewById(R.id.conversationsRecyclerView)
        conversationAdapter = ConversationAdapter(conversations, this)
        conversationsRecyclerView.layoutManager = LinearLayoutManager(this)
        conversationsRecyclerView.adapter = conversationAdapter
        val backButton: ImageView = findViewById(R.id.backButton)
        backButton.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        connectWebSocket()
    }

    // ── WebSocket ───────────────────────────────────────────────

    private fun connectWebSocket() {
        val token = ServiceBuilder.getAuthToken()
        if (token.isNullOrEmpty()) {
            fallbackToPolling()
            return
        }
        conversationWs = ConversationWebSocketManager(token, this)
        conversationWs?.connect()
    }

    override fun onConversationsReceived(convos: List<Conversation>) {
        usingWebSocket = true
        conversations.clear()
        conversations.addAll(convos ?: emptyList())
        conversationAdapter.notifyDataSetChanged()
    }

    override fun onConnectionFailed() {
        Log.w("ConversationsActivity", "WebSocket failed, falling back to REST polling")
        fallbackToPolling()
    }

    private fun fallbackToPolling() {
        usingWebSocket = false
        startConversationRefreshing()
    }

    // ── REST polling fallback ───────────────────────────────────

    private fun loadConversations() {
        if (SupabaseData.isConfigured()) {
            lifecycleScope.launch {
                val list = SupabaseData.getConversations()
                conversations.clear()
                conversations.addAll(list)
                conversationAdapter.notifyDataSetChanged()
            }
        } else {
            apiService.getConversations().enqueue(object : Callback<List<Conversation>> {
                override fun onResponse(call: Call<List<Conversation>>, response: Response<List<Conversation>>) {
                    if (response.isSuccessful) {
                        conversations.clear()
                        conversations.addAll(response.body() ?: emptyList())
                        conversationAdapter.notifyDataSetChanged()
                    }
                }

                override fun onFailure(call: Call<List<Conversation>>, t: Throwable) {
                    Toast.makeText(this@ConversationsActivity, "Network error: ${t.message}", Toast.LENGTH_SHORT).show()
                }
            })
        }
    }

    private fun startConversationRefreshing() {
        handler.post(conversationRefreshRunnable)
    }

    private fun stopConversationRefreshing() {
        handler.removeCallbacks(conversationRefreshRunnable)
    }

    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        conversationWs?.disconnect()
        stopConversationRefreshing()
    }

}
