package com.womanglobal.connecther.utils

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.womanglobal.connecther.services.Conversation
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * Manages a WebSocket connection for real-time conversation list updates.
 * Falls back to REST polling if the WebSocket connection fails.
 */
class ConversationWebSocketManager(
    private val token: String,
    private val listener: ConversationWebSocketCallback
) {
    interface ConversationWebSocketCallback {
        fun onConversationsReceived(conversations: List<Conversation>)
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
        val wsUrl = ChatWebSocketManager.buildWsUrl("ws/conversations", token)
        val request = Request.Builder().url(wsUrl).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                Log.d(TAG, "Conversations WS connected")
                isConnected = true
                reconnectAttempts = 0
            }

            override fun onMessage(ws: WebSocket, text: String) {
                try {
                    val json = gson.fromJson(text, Map::class.java)
                    val type = json["type"] as? String

                    if (type == "conversations" || type == "conversations_update") {
                        val convosJson = gson.toJson(json["conversations"])
                        val listType = object : TypeToken<List<Conversation>>() {}.type
                        val conversations: List<Conversation> = gson.fromJson(convosJson, listType) ?: emptyList()
                        mainHandler.post { listener.onConversationsReceived(conversations) }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing WS message", e)
                }
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "Conversations WS failed: ${t.message}")
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
                isConnected = false
                ws.close(1000, null)
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                isConnected = false
            }
        })
    }

    fun isConnected(): Boolean = isConnected

    fun disconnect() {
        webSocket?.close(1000, "Client closing")
        webSocket = null
        isConnected = false
        mainHandler.removeCallbacksAndMessages(null)
    }

    companion object {
        private const val TAG = "ConvoWebSocketManager"
        private const val MAX_RECONNECT_ATTEMPTS = 5
    }
}
