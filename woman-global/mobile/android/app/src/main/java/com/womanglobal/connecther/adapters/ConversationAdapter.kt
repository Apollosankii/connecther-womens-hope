package com.womanglobal.connecther.adapters

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.womanglobal.connecther.ChatActivity
import com.womanglobal.connecther.R
import com.womanglobal.connecther.services.Conversation
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class ConversationAdapter(
    private val conversations: List<Conversation>,
    private val context: Context,
    private val isProvider: Boolean,
) : RecyclerView.Adapter<ConversationAdapter.ConversationViewHolder>() {
    private val sameDayFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
    private val dayFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConversationViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_conversation, parent, false)
        return ConversationViewHolder(view)
    }

    override fun onBindViewHolder(holder: ConversationViewHolder, position: Int) {
        val conversation = conversations[position]
        holder.bind(conversation)
    }

    override fun getItemCount(): Int = conversations.size

    inner class ConversationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val providerName: TextView = itemView.findViewById(R.id.providerName)
        private val serviceName: TextView = itemView.findViewById(R.id.serviceName)
        private val timeStamp: TextView = itemView.findViewById(R.id.messageTime)
        private val lastMessage: TextView = itemView.findViewById(R.id.lastMessage)

        fun bind(conversation: Conversation) {
            val peerName = if (isProvider) conversation.client else conversation.provider
            providerName.text = peerName
            serviceName.text = conversation.service
            timeStamp.text = formatConversationTime(conversation.time)
            lastMessage.text = conversation.text.ifBlank { "Start chatting" }

            itemView.setOnClickListener {
                val intent = Intent(context, ChatActivity::class.java).apply {
                    putExtra("chat_code", conversation.chat_id)
                    putExtra("quote_id", conversation.quote_code)
                    putExtra("providerName", peerName)
                    putExtra("serviceName",conversation.service )
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                context.startActivity(intent)
            }
        }
    }

    private fun formatConversationTime(raw: String): String {
        return runCatching {
            val instant = Instant.parse(raw)
            val zoned = instant.atZone(ZoneId.systemDefault())
            val today = java.time.LocalDate.now(ZoneId.systemDefault())
            if (zoned.toLocalDate() == today) sameDayFormatter.format(zoned) else dayFormatter.format(zoned)
        }.getOrElse {
            raw.take(16)
        }
    }
}
