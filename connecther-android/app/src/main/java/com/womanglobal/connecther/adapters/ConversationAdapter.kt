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

class ConversationAdapter(
    private val conversations: List<Conversation>,
    private val context: Context
) : RecyclerView.Adapter<ConversationAdapter.ConversationViewHolder>() {

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
            providerName.text = conversation.provider
            serviceName.text = "Service : ${conversation.service}"
            timeStamp.text = conversation.time
            lastMessage.text = conversation.text

            itemView.setOnClickListener {
                val intent = Intent(context, ChatActivity::class.java).apply {
                    putExtra("chat_code", conversation.chat_id)
                    putExtra("quote_id", conversation.quote_code)
                    putExtra("providerName", conversation.provider)
                    putExtra("serviceName",conversation.service )
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                context.startActivity(intent)
            }
        }
    }
}
