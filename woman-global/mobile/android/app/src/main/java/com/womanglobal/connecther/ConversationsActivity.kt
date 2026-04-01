package com.womanglobal.connecther

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.womanglobal.connecther.adapters.ConversationAdapter
import com.womanglobal.connecther.data.local.AppOfflineCache
import com.womanglobal.connecther.services.Conversation
import com.womanglobal.connecther.supabase.SupabaseData
import com.womanglobal.connecther.utils.NetworkStatus
import kotlinx.coroutines.launch

class ConversationsActivity : AppCompatActivity() {
    private lateinit var conversationsRecyclerView: RecyclerView
    private lateinit var conversationAdapter: ConversationAdapter
    private val conversations = mutableListOf<Conversation>()

    private val handler = Handler(Looper.getMainLooper())
    private val conversationRefreshRunnable = object : Runnable {
        override fun run() {
            loadConversations()
            handler.postDelayed(this, 5000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_conversations)

        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        conversationsRecyclerView = findViewById(R.id.conversationsRecyclerView)
        val isProvider = getSharedPreferences("user_session", Context.MODE_PRIVATE)
            .getBoolean("isProvider", false)
        conversationAdapter = ConversationAdapter(conversations, this, isProvider)
        conversationsRecyclerView.layoutManager = LinearLayoutManager(this)
        conversationsRecyclerView.adapter = conversationAdapter

        startConversationRefreshing()
    }

    private fun loadConversations() {
        lifecycleScope.launch {
            val online = NetworkStatus.isOnline(this@ConversationsActivity)
            val rows = when {
                online -> {
                    val got = runCatching { SupabaseData.getConversations() }.getOrElse {
                        Toast.makeText(this@ConversationsActivity, "Failed to load conversations", Toast.LENGTH_SHORT).show()
                        null
                    }
                    val list = got ?: AppOfflineCache.readConversations(this@ConversationsActivity).orEmpty()
                    if (got != null) {
                        AppOfflineCache.writeConversations(this@ConversationsActivity, got)
                    }
                    list
                }
                else -> AppOfflineCache.readConversations(this@ConversationsActivity).orEmpty()
            }
            conversations.clear()
            conversations.addAll(rows)
            conversationAdapter.notifyDataSetChanged()
        }
    }

    private fun startConversationRefreshing() {
        handler.post(conversationRefreshRunnable)
    }

    private fun stopConversationRefreshing() {
        handler.removeCallbacks(conversationRefreshRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopConversationRefreshing()
    }
}
