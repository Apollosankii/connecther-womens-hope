package com.womanglobal.connecther.utils

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.womanglobal.connecther.services.ChatMessage
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * Manages a WebSocket connection for a single chat room.
 * Falls back to REST polling if the WebSocket connection fails.
 */
class ChatWebSocketManager(
    private val chatCode: String,
    private val token: String,
    private val listener: ChatWebSocketCallback
) {
    interface ChatWebSocketCallback {
        fun onHistoryReceived(messages: List<ChatMessage>)
        fun onNewMessage(message: ChatMessage)
        fun onConnectionFailed()
    }

    private val gson = Gson()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var webSocket: WebSocket? = null
    private var isConnected = false
    private var reconnectAttempts = 0

    private val client = OkHttpClient.Builder()
        .retryOnConnectionFailure(true)
        .build()

    fun connect() {
        val wsUrl = buildWsUrl("ws/chat/$chatCode", token)
        val request = Request.Builder().url(wsUrl).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                Log.d(TAG, "Chat WS connected: $chatCode")
                isConnected = true
                reconnectAttempts = 0
            }

            override fun onMessage(ws: WebSocket, text: String) {
                try {
                    val json = gson.fromJson(text, Map::class.java)
                    val type = json["type"] as? String

                    when (type) {
                        "history" -> {
                            val messagesJson = gson.toJson(json["messages"])
                            val listType = object : TypeToken<List<ChatMessage>>() {}.type
                            val messages: List<ChatMessage> = gson.fromJson(messagesJson, listType) ?: emptyList()
                            mainHandler.post { listener.onHistoryReceived(messages) }
                        }
                        "new_message" -> {
                            val msg = ChatMessage(
                                id = "",
                                sender_id = json["sender_id"] as? String ?: "",
                                receiverId = "",
                                message = json["message"] as? String ?: "",
                                timestamp = System.currentTimeMillis(),
                                isRead = false
                            )
                            mainHandler.post { listener.onNewMessage(msg) }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing WS message", e)
                }
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "Chat WS failed: ${t.message}")
                isConnected = false
                if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                    reconnectAttempts++
                    val delay = (reconnectAttempts * 2000).toLong()
                    mainHandler.postDelayed({ connect() }, delay)
                } else {
                    mainHandler.post { listener.onConnectionFailed() }
                }
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "Chat WS closing: $code $reason")
                isConnected = false
                ws.close(1000, null)
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "Chat WS closed: $code $reason")
                isConnected = false
            }
        })
    }

    fun sendMessage(content: String) {
        if (!isConnected || webSocket == null) {
            Log.w(TAG, "WS not connected, cannot send")
            return
        }
        val payload = mapOf("type" to "message", "content" to content)
        webSocket?.send(gson.toJson(payload))
    }

    fun isConnected(): Boolean = isConnected

    fun disconnect() {
        webSocket?.close(1000, "Client closing")
        webSocket = null
        isConnected = false
        mainHandler.removeCallbacksAndMessages(null)
    }

    companion object {
        private const val TAG = "ChatWebSocketManager"
        private const val MAX_RECONNECT_ATTEMPTS = 5

        fun buildWsUrl(path: String, token: String): String {
            val baseUrl = ServiceBuilder.getBaseUrl()
            val wsBase = baseUrl
                .replace("https://", "wss://")
                .replace("http://", "ws://")
                .trimEnd('/')
            return "$wsBase/$path?token=$token"
        }
    }
}
