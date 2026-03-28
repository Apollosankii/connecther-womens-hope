package com.womanglobal.connecther.ui.fragments

import ApiServiceFactory
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.womanglobal.connecther.adapters.ConversationAdapter
import com.womanglobal.connecther.databinding.FragmentMessagesBinding
import com.womanglobal.connecther.services.ApiService
import com.womanglobal.connecther.services.Conversation
import com.womanglobal.connecther.supabase.SupabaseData
import com.womanglobal.connecther.utils.ConversationWebSocketManager
import com.womanglobal.connecther.utils.ServiceBuilder
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class MessagesFragment : Fragment(), ConversationWebSocketManager.ConversationWebSocketCallback {
    private var _binding: FragmentMessagesBinding? = null
    private val binding get() = _binding!!

    private lateinit var conversationAdapter: ConversationAdapter
    private val conversations = mutableListOf<Conversation>()
    private val apiService: ApiService by lazy { ApiServiceFactory.createApiService() }
    private val handler = Handler(Looper.getMainLooper())
    private var conversationWs: ConversationWebSocketManager? = null
    private var usingWebSocket = false

    private val jobRefreshRunnable = object : Runnable {
        override fun run() {
            if (!usingWebSocket) {
                loadConversations()
                handler.postDelayed(this, 60000)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMessagesBinding.inflate(inflater, container, false)

        conversationAdapter = ConversationAdapter(conversations, requireContext())
        binding.conversationsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.conversationsRecyclerView.adapter = conversationAdapter

        connectWebSocket()

        return binding.root
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
        if (_binding == null) return
        usingWebSocket = true
        conversations.clear()
        conversations.addAll(convos ?: emptyList())
        updateEmptyState()
        conversationAdapter.notifyDataSetChanged()
    }

    override fun onConnectionFailed() {
        Log.w("MessagesFragment", "WebSocket failed, falling back to REST polling")
        fallbackToPolling()
    }

    private fun fallbackToPolling() {
        usingWebSocket = false
        startMessagesRefreshing()
    }

    private fun updateEmptyState() {
        if (_binding == null) return
        if (conversations.isEmpty()) {
            binding.noMessageLayout.visibility = View.VISIBLE
            binding.conversationsRecyclerView.visibility = View.GONE
        } else {
            binding.noMessageLayout.visibility = View.GONE
            binding.conversationsRecyclerView.visibility = View.VISIBLE
        }
    }

    // ── REST polling fallback ───────────────────────────────────

    private fun loadConversations() {
        if (_binding == null) return

        if (SupabaseData.isConfigured()) {
            lifecycleScope.launch {
                if (_binding == null) return@launch
                val list = SupabaseData.getConversations()
                conversations.clear()
                conversations.addAll(list)
                updateEmptyState()
                conversationAdapter.notifyDataSetChanged()
            }
        } else {
            apiService.getConversations().enqueue(object : Callback<List<Conversation>> {
                override fun onResponse(call: Call<List<Conversation>>, response: Response<List<Conversation>>) {
                    if (_binding == null) return

                    if (response.isSuccessful && response.body() != null) {
                        conversations.clear()
                        conversations.addAll(response.body() ?: emptyList())
                        updateEmptyState()
                        conversationAdapter.notifyDataSetChanged()
                    } else {
                        binding.noMessageLayout.visibility = View.VISIBLE
                        binding.conversationsRecyclerView.visibility = View.GONE
                    }
                }

                override fun onFailure(call: Call<List<Conversation>>, t: Throwable) {
                    if (_binding == null || !isAdded) return
                    view?.post {
                        if (isAdded) {
                            Toast.makeText(requireContext(), "Network error: ${t.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            })
        }
    }

    private fun startMessagesRefreshing() {
        handler.post(jobRefreshRunnable)
    }

    private fun stopMessagesRefreshing() {
        handler.removeCallbacks(jobRefreshRunnable)
    }

    override fun onResume() {
        super.onResume()
        if (!usingWebSocket) {
            loadConversations()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        conversationWs?.disconnect()
        stopMessagesRefreshing()
        _binding = null
    }

}
