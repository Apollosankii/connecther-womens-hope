package com.womanglobal.connecther

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.womanglobal.connecther.adapters.ChatAdapter
import com.womanglobal.connecther.services.ChatMessage
import com.womanglobal.connecther.supabase.SupabaseData
import kotlinx.coroutines.launch

class ChatActivity : AppCompatActivity() {
    private lateinit var backButton: ImageView
    private lateinit var sendButton: MaterialButton
    private lateinit var messageInput: EditText
    private lateinit var chatRecyclerView: RecyclerView
    private lateinit var userNameTextView: TextView
    private lateinit var serviceNameTextView: TextView
    private lateinit var chatAdapter: ChatAdapter
    private val chatMessages = mutableListOf<ChatMessage>()
    private var currentUserId: String = ""

    private lateinit var chatCode: String
    private val handler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            loadChatMessages()
            handler.postDelayed(this, 5000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        chatCode = intent.getStringExtra("chat_code").orEmpty()
        val peerName = intent.getStringExtra("providerName").orEmpty().ifBlank { "Conversation" }
        val serviceName = intent.getStringExtra("serviceName").orEmpty()

        backButton = findViewById(R.id.backButton)
        sendButton = findViewById(R.id.sendButton)
        messageInput = findViewById(R.id.messageInput)
        chatRecyclerView = findViewById(R.id.chatRecyclerView)
        userNameTextView = findViewById(R.id.userNameTextView)
        serviceNameTextView = findViewById(R.id.serviceNameTextView)

        userNameTextView.text = peerName
        serviceNameTextView.text = serviceName

        val layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        chatRecyclerView.layoutManager = layoutManager
        chatAdapter = ChatAdapter(chatMessages, currentUserId)
        chatRecyclerView.adapter = chatAdapter

        backButton.setOnClickListener { goToConversations() }
        sendButton.setOnClickListener { sendMessage() }

        lifecycleScope.launch {
            currentUserId = SupabaseData.getMyUserId().orEmpty()
            chatAdapter = ChatAdapter(chatMessages, currentUserId)
            chatRecyclerView.adapter = chatAdapter
            loadChatMessages()
            handler.post(refreshRunnable)
        }
    }

    private fun loadChatMessages() {
        if (chatCode.isBlank()) {
            Toast.makeText(this, "Chat is unavailable", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            val messages = runCatching { SupabaseData.getChatMessages(chatCode) }.getOrElse {
                Toast.makeText(this@ChatActivity, "Failed to load messages", Toast.LENGTH_SHORT).show()
                emptyList()
            }
            chatMessages.clear()
            chatMessages.addAll(messages)
            chatAdapter.notifyDataSetChanged()
            if (chatMessages.isNotEmpty()) chatRecyclerView.scrollToPosition(chatMessages.size - 1)
        }
    }

    private fun sendMessage() {
        val messageText = messageInput.text.toString().trim()
        if (messageText.isBlank()) return
        if (chatCode.isBlank()) {
            Toast.makeText(this, "Chat is unavailable", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            val sent = SupabaseData.sendChatMessage(chatCode, messageText)
            if (sent) {
                messageInput.text?.clear()
                loadChatMessages()
            } else {
                Toast.makeText(this@ChatActivity, "Failed to send message", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun goToConversations() {
        val intent = Intent(this, ConversationsActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
