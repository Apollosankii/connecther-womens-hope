package com.womanglobal.connecther

import ApiServiceFactory
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.womanglobal.connecther.adapters.ChatAdapter
import com.womanglobal.connecther.services.ApiService
import com.womanglobal.connecther.services.ChatMessage
import com.womanglobal.connecther.services.ChatMessageRequest
import com.womanglobal.connecther.services.HireRequest
import com.womanglobal.connecther.services.HireResponse
import com.womanglobal.connecther.ui.fragments.JobsFragment
import com.womanglobal.connecther.utils.ChatWebSocketManager
import com.womanglobal.connecther.utils.CurrentUser
import com.womanglobal.connecther.utils.ServiceBuilder
import com.womanglobal.connecther.supabase.SupabaseData
import com.womanglobal.connecther.utils.UIHelper
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ChatActivity : AppCompatActivity(), ChatWebSocketManager.ChatWebSocketCallback {
    private lateinit var backButton: ImageView
    private lateinit var hireButton: Button
    private lateinit var sendButton: ImageView
    private lateinit var messageInput: EditText
    private lateinit var chatRecyclerView: RecyclerView
    private lateinit var userNameTextView: TextView
    private lateinit var serviceNameTextView: TextView
    private lateinit var chatAdapter: ChatAdapter
    private val chatMessages = mutableListOf<ChatMessage>()

    private val apiService: ApiService by lazy { ApiServiceFactory.createApiService() }
    private lateinit var chatCode: String
    private lateinit var quoteId: String
    private lateinit var providerName: String
    private lateinit var serviceName: String
    private val handler = Handler(Looper.getMainLooper())
    private val currentUser = CurrentUser.getUser()

    private var chatWebSocket: ChatWebSocketManager? = null
    private var usingWebSocket = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)


        // Retrieve isProvider from SharedPreferences
        val sharedPreferences = getSharedPreferences("user_session", Context.MODE_PRIVATE)
        val isProvider = sharedPreferences.getBoolean("isProvider", false) // Default to false

        // Get chatCode, quoteId, and providerName from intent
        chatCode = intent.getStringExtra("chat_code") ?: ""
        quoteId = intent.getStringExtra("quote_id") ?: ""
        providerName = intent.getStringExtra("providerName") ?: "Unknown User"
        serviceName = intent.getStringExtra("serviceName") ?: ""


        // Initialize views
        backButton = findViewById(R.id.backButton)
        hireButton = findViewById(R.id.hireButton)
        sendButton = findViewById(R.id.sendButton)
        messageInput = findViewById(R.id.messageInput)
        chatRecyclerView = findViewById(R.id.chatRecyclerView)
        userNameTextView = findViewById(R.id.userNameTextView)
        serviceNameTextView = findViewById(R.id.serviceNameTextView)

        if (isProvider) {
            hireButton.visibility = View.GONE
        }

        // Set provider's name in TextView
        userNameTextView.text = providerName
        serviceNameTextView.text = serviceName

        Log.e("PATRICE USER", "" + currentUser)

        // Set up RecyclerView with proper layout configuration
        val layoutManager = LinearLayoutManager(this)
        layoutManager.stackFromEnd = true
        layoutManager.reverseLayout = false
        chatRecyclerView.layoutManager = layoutManager

        chatAdapter = ChatAdapter(chatMessages, currentUser?.user_name ?: "")
        chatRecyclerView.adapter = chatAdapter

        // Set click listeners
        sendButton.setOnClickListener { sendMessage() }
        backButton.setOnClickListener { goToConversations() }
        hireButton.setOnClickListener { confirmHiring() }

        // Try WebSocket first, fall back to REST polling
        connectWebSocket()
    }

    // ── WebSocket connection ────────────────────────────────────

    private fun connectWebSocket() {
        val token = ServiceBuilder.getAuthToken()
        if (token.isNullOrEmpty() || chatCode.isEmpty()) {
            fallbackToPolling()
            return
        }

        chatWebSocket = ChatWebSocketManager(chatCode, token, this)
        chatWebSocket?.connect()
    }

    override fun onHistoryReceived(messages: List<ChatMessage>) {
        usingWebSocket = true
        chatMessages.clear()
        chatMessages.addAll(messages ?: emptyList())
        chatAdapter.notifyDataSetChanged()
        if (chatMessages.isNotEmpty()) {
            chatRecyclerView.scrollToPosition(chatMessages.size - 1)
        }
    }

    override fun onNewMessage(message: ChatMessage) {
        chatMessages.add(message)
        chatAdapter.notifyItemInserted(chatMessages.size - 1)
        chatRecyclerView.scrollToPosition(chatMessages.size - 1)
    }

    override fun onConnectionFailed() {
        Log.w("ChatActivity", "WebSocket failed, falling back to REST polling")
        fallbackToPolling()
    }

    private fun fallbackToPolling() {
        usingWebSocket = false
        loadChatMessages()
        startChatUpdates()
    }

    // ── REST fallback methods ───────────────────────────────────

    private fun loadChatMessages() {
        if (quoteId.isEmpty() && chatCode.isEmpty()) {
            Toast.makeText(this, "Invalid quote ID!", Toast.LENGTH_SHORT).show()
            return
        }

        if (SupabaseData.isConfigured() && chatCode.isNotEmpty()) {
            lifecycleScope.launch {
                val list = SupabaseData.getChatMessages(chatCode)
                chatMessages.clear()
                chatMessages.addAll(list)
                chatAdapter.notifyDataSetChanged()
                if (chatMessages.isNotEmpty()) chatRecyclerView.scrollToPosition(chatMessages.size - 1)
            }
        } else {
            apiService.getChatMessages(quoteId.ifEmpty { chatCode }).enqueue(object : Callback<List<ChatMessage>> {
                override fun onResponse(call: Call<List<ChatMessage>>, response: Response<List<ChatMessage>>) {
                    if (response.isSuccessful) {
                        chatMessages.clear()
                        chatMessages.addAll(response.body() ?: emptyList())
                        chatAdapter.notifyDataSetChanged()
                        chatRecyclerView.scrollToPosition(chatMessages.size - 1)
                    } else {
                        Toast.makeText(this@ChatActivity, "Failed to load chat", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<List<ChatMessage>>, t: Throwable) {
                    Toast.makeText(this@ChatActivity, "Network error: ${t.message}", Toast.LENGTH_SHORT).show()
                }
            })
        }
    }

    /**
     * Sends a chat message via WebSocket if connected, otherwise via REST.
     */
    private fun sendMessage() {
        val messageText = messageInput.text.toString().trim()
        if (messageText.isEmpty()) return

        if (chatCode.isEmpty()) {
            Toast.makeText(this, "Chat code is missing!", Toast.LENGTH_SHORT).show()
            return
        }

        if (usingWebSocket && chatWebSocket?.isConnected() == true) {
            chatWebSocket?.sendMessage(messageText)
            messageInput.text.clear()
            return
        }

        if (SupabaseData.isConfigured()) {
            lifecycleScope.launch {
                val ok = SupabaseData.sendChatMessage(chatCode, messageText)
                if (ok) {
                    messageInput.text.clear()
                    loadChatMessages()
                } else {
                    Toast.makeText(this@ChatActivity, "Failed to send message", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            val chatRequest = ChatMessageRequest(content = messageText)
            apiService.sendChatMessage(chatCode, chatRequest).enqueue(object : Callback<String> {
                override fun onResponse(call: Call<String>, response: Response<String>) {
                    if (response.isSuccessful) {
                        messageInput.text.clear()
                        loadChatMessages()
                    } else {
                        Toast.makeText(this@ChatActivity, "Failed to send message", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<String>, t: Throwable) {
                    Toast.makeText(this@ChatActivity, "Network error: ${t.message}", Toast.LENGTH_SHORT).show()
                }
            })
        }
    }

    /**
     * Show confirmation dialog before hiring
     */
    private fun confirmHiring() {
        val input = EditText(this)
        input.hint = "Enter price"
        input.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL

        AlertDialog.Builder(this)
            .setTitle("Confirm Hiring")
            .setMessage("Are you sure you want to hire $providerName?")
            .setView(input)
            .setPositiveButton("Hire") { _, _ ->
                val priceText = input.text.toString()
                if (priceText.isEmpty()) {
                    Toast.makeText(this, "Price is required!", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val price = priceText.toDoubleOrNull()
                if (price == null || price <= 0) {
                    Toast.makeText(this, "Enter a valid price!", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                hireUser(price)
                openJobsFragment(providerName, price)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openJobsFragment(providerName: String, price: Double) {
        val fragment = JobsFragment().apply {
            arguments = Bundle().apply {
                putString("providerName", providerName)
                putDouble("price", price)
            }
        }

        // Hide chat UI
        findViewById<RecyclerView>(R.id.chatRecyclerView).visibility = View.GONE
        findViewById<ConstraintLayout>(R.id.messageContainer).visibility = View.GONE
        findViewById<ConstraintLayout>(R.id.topBar).visibility = View.GONE

        // Replace the fragment
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .addToBackStack(null) // Allow back navigation
            .commit()
    }




    /**
     * Send a request to hire the user
     */
    private fun hireUser(price: Double) {
        val hireUrl = "/hire/$quoteId"
        val hireRequest = HireRequest(price)

        UIHelper.showProgressDialog(this, "Hiring user...")

        apiService.hireUser(hireUrl, hireRequest).enqueue(object : Callback<HireResponse> {
            override fun onResponse(call: Call<HireResponse>, response: Response<HireResponse>) {
                UIHelper.dismissProgressDialog()
                if (response.isSuccessful) {
                    UIHelper.showToastShort(this@ChatActivity, "User Hired Successfully!")
                } else {
                    UIHelper.showToastShort(this@ChatActivity, "Failed to Hire User")
                }
            }

            override fun onFailure(call: Call<HireResponse>, t: Throwable) {
                Log.e("HIRE ERROR", ""+t.message +"\n" +t.toString())
                UIHelper.dismissProgressDialog()
                UIHelper.showToastShort(this@ChatActivity, "Network Error: ${t.message}")
            }
        })
    }

    /**
     * REST polling fallback — only used when WebSocket is unavailable.
     */
    private fun startChatUpdates() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                if (!usingWebSocket) {
                    loadChatMessages()
                    handler.postDelayed(this, 5000)
                }
            }
        }, 5000)
    }

    /**
     * Navigates back to conversations
     */
    private fun goToConversations() {
        val intent = Intent(this, ConversationsActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        startActivity(intent)
        finish()
    }

    override fun onBackPressed() {
        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStack()
            // Show chat UI again
            findViewById<RecyclerView>(R.id.chatRecyclerView).visibility = View.VISIBLE
            findViewById<ConstraintLayout>(R.id.messageContainer).visibility = View.VISIBLE
            findViewById<ConstraintLayout>(R.id.topBar).visibility = View.VISIBLE
        } else {
            super.onBackPressed()
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        chatWebSocket?.disconnect()
        handler.removeCallbacksAndMessages(null)
    }
}
