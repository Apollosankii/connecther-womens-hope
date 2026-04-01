package com.womanglobal.connecther.ui.fragments

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.womanglobal.connecther.adapters.ConversationAdapter
import com.womanglobal.connecther.data.local.AppOfflineCache
import com.womanglobal.connecther.databinding.FragmentMessagesBinding
import com.womanglobal.connecther.services.Conversation
import com.womanglobal.connecther.supabase.SupabaseData
import com.womanglobal.connecther.utils.NetworkStatus
import kotlinx.coroutines.launch

class MessagesFragment : Fragment() {
    private var _binding: FragmentMessagesBinding? = null
    private val binding get() = _binding!!

    private lateinit var conversationAdapter: ConversationAdapter
    private val conversations = mutableListOf<Conversation>()
    private val handler = Handler(Looper.getMainLooper())
    private val jobRefreshRunnable = object : Runnable {
        override fun run() {
            loadConversations()
            handler.postDelayed(this, 60000)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMessagesBinding.inflate(inflater, container, false)

        val isProvider = requireContext()
            .getSharedPreferences("user_session", Context.MODE_PRIVATE)
            .getBoolean("isProvider", false)
        conversationAdapter = ConversationAdapter(conversations, requireContext(), isProvider)
        binding.conversationsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.conversationsRecyclerView.adapter = conversationAdapter

        startMessagesRefreshing()

        return binding.root
    }

    private fun loadConversations() {
        if (_binding == null) return  // Prevent crash

        viewLifecycleOwner.lifecycleScope.launch {
            val online = NetworkStatus.isOnline(requireContext())
            val rows = when {
                online -> {
                    val got = runCatching { SupabaseData.getConversations() }.getOrElse {
                        if (!isAdded || _binding == null) return@launch
                        Toast.makeText(requireContext(), "Failed to load conversations", Toast.LENGTH_SHORT).show()
                        null
                    }
                    val list = got ?: AppOfflineCache.readConversations(requireContext()).orEmpty()
                    if (got != null) {
                        AppOfflineCache.writeConversations(requireContext(), got)
                    }
                    list
                }
                else -> AppOfflineCache.readConversations(requireContext()).orEmpty()
            }
            if (_binding == null) return@launch
            conversations.clear()
            conversations.addAll(rows)
            binding.messagesMainContent.visibility = if (conversations.isEmpty()) View.GONE else View.VISIBLE
            binding.noMessageLayout.visibility = if (conversations.isEmpty()) View.VISIBLE else View.GONE
            conversationAdapter.notifyDataSetChanged()
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
        loadConversations()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopMessagesRefreshing()
        _binding = null
    }

}
