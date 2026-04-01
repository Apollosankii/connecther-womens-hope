package com.womanglobal.connecther.services

data class Conversation(
    val quote_code: String,
    val chat_id: String,
    val provider: String,
    val client: String,
    val service: String,
    val text: String,
    val time: String,
    /** Other party (matches auth user: client → provider, provider → client). From `get_conversations`. */
    val peerName: String = "",
    val peerPic: String? = null,
)

data class ChatMessage(
    val id: String,
    val sender_id: String,
    val receiverId: String,
    val message: String,
    val timestamp: Long,
    val isRead: Boolean
)

data class ChatMessageRequest(
    val content: String
)
