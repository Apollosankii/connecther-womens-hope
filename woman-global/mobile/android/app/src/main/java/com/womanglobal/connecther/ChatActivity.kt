package com.womanglobal.connecther

import android.content.Intent
import android.os.Bundle
import android.util.Log
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
import com.bumptech.glide.Glide
import com.bumptech.glide.signature.ObjectKey
import com.google.android.material.button.MaterialButton
import com.womanglobal.connecther.adapters.ChatAdapter
import com.womanglobal.connecther.data.local.AppOfflineCache
import com.womanglobal.connecther.services.ChatMessage
import com.womanglobal.connecther.supabase.SupabaseData
import com.womanglobal.connecther.utils.NetworkStatus
import kotlinx.coroutines.launch

class ChatActivity : AppCompatActivity() {
    private lateinit var backButton: ImageView
    private lateinit var sendButton: MaterialButton
    private lateinit var messageInput: EditText
    private lateinit var chatRecyclerView: RecyclerView
    private lateinit var userNameTextView: TextView
    private lateinit var serviceNameTextView: TextView
    private lateinit var userImage: ImageView
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
        val intentPeer = intent.getStringExtra("peer_display_name").orEmpty()
            .ifBlank { intent.getStringExtra("providerName").orEmpty() }
        val serviceName = intent.getStringExtra("serviceName").orEmpty()
        val intentPic = intent.getStringExtra("peer_pic").orEmpty().takeIf { it.isNotBlank() }

        backButton = findViewById(R.id.backButton)
        sendButton = findViewById(R.id.sendButton)
        messageInput = findViewById(R.id.messageInput)
        chatRecyclerView = findViewById(R.id.chatRecyclerView)
        userNameTextView = findViewById(R.id.userNameTextView)
        serviceNameTextView = findViewById(R.id.serviceNameTextView)
        userImage = findViewById(R.id.userImage)

        applyHeader(intentPeer.ifBlank { "Conversation" }, intentPic, serviceName)

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

            val header = if (chatCode.isNotBlank()) {
                runCatching { SupabaseData.getChatHeader(chatCode) }.getOrNull()
            } else {
                null
            }
            val name = header?.peer_name?.trim().orEmpty().ifBlank { intentPeer }.ifBlank { "Conversation" }
            val pic = header?.peer_pic?.trim()?.takeIf { it.isNotEmpty() } ?: intentPic
            val svc = header?.service_name?.trim().orEmpty().ifBlank { serviceName }
            applyHeader(name, pic, svc)

            loadChatMessages()
            handler.post(refreshRunnable)
        }
    }

    private fun applyHeader(peerName: String, peerPic: String?, serviceName: String) {
        userNameTextView.text = peerName
        serviceNameTextView.text = serviceName
        Glide.with(this)
            .load(peerPic?.takeIf { it.isNotBlank() })
            .placeholder(R.drawable.ic_avatar_neutral)
            .error(R.drawable.ic_avatar_neutral)
            .signature(ObjectKey(peerPic ?: peerName))
            .circleCrop()
            .into(userImage)
    }

    private fun loadChatMessages() {
        if (chatCode.isBlank()) {
            Toast.makeText(this, "Chat is unavailable", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            val online = NetworkStatus.isOnline(this@ChatActivity)
            val messages = when {
                online -> {
                    val fetched = runCatching { SupabaseData.getChatMessages(chatCode) }.getOrElse { e ->
                        Log.w("ChatActivity", "getChatMessages failed: ${e.message}")
                        Toast.makeText(this@ChatActivity, "Failed to load messages", Toast.LENGTH_SHORT).show()
                        null
                    }
                    val list = fetched ?: AppOfflineCache.readChatMessages(this@ChatActivity, chatCode).orEmpty()
                    if (fetched != null) {
                        AppOfflineCache.writeChatMessages(this@ChatActivity, chatCode, fetched)
                    }
                    list
                }
                else -> {
                    val cached = AppOfflineCache.readChatMessages(this@ChatActivity, chatCode).orEmpty()
                    if (cached.isEmpty()) {
                        Toast.makeText(
                            this@ChatActivity,
                            getString(R.string.offline_no_cached_messages),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                    cached
                }
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
