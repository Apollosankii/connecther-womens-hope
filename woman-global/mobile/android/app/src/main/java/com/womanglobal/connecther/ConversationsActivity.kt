package com.womanglobal.connecther

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.womanglobal.connecther.adapters.ConversationAdapter
import com.womanglobal.connecther.services.Conversation
import com.womanglobal.connecther.supabase.SupabaseData
import kotlinx.coroutines.launch

class ConversationsActivity : AppCompatActivity() {
    private lateinit var conversationsRecyclerView: RecyclerView
    private lateinit var conversationAdapter: ConversationAdapter
    private val conversations = mutableListOf<Conversation>()

    private val handler = Handler(Looper.getMainLooper())
    private val conversationRefreshRunnable = object : Runnable {
        override fun run() {
            loadConversations()
            handler.postDelayed(this, 5000) // Refresh every 5 seconds
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_conversations)

        conversationsRecyclerView = findViewById(R.id.conversationsRecyclerView)
        val isProvider = getSharedPreferences("user_session", Context.MODE_PRIVATE)
            .getBoolean("isProvider", false)
        conversationAdapter = ConversationAdapter(conversations, this, isProvider)
        conversationsRecyclerView.layoutManager = LinearLayoutManager(this)
        conversationsRecyclerView.adapter = conversationAdapter
        val backButton: ImageView = findViewById(R.id.backButton)
        backButton.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }


        startConversationRefreshing()
    }

    private fun loadConversations() {
        lifecycleScope.launch {
            val rows = runCatching { SupabaseData.getConversations() }.getOrElse {
                Toast.makeText(this@ConversationsActivity, "Failed to load conversations", Toast.LENGTH_SHORT).show()
                emptyList()
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

    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopConversationRefreshing()
    }

}
