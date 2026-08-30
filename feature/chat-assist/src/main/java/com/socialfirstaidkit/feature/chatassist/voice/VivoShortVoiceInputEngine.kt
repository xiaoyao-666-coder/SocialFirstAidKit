package com.socialfirstaidkit.feature.chatassist.voice

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class VivoShortVoiceInputEngine @Inject constructor(
    @Named("vivo_aigc_base_url") private val baseUrl: String,
    @Named("vivo_aigc_api_key") private val appKey: String
) {

    companion object {
        private const val TAG = "VivoShortVoiceInput"
        private const val DEFAULT_BASE_URL = "https://api-ai.vivo.com.cn"
    }

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    private val isListening = AtomicBoolean(false)
    private var webSocket: WebSocket? = null
    private var audioChannel: Channel<ByteArray>? = null

    fun startListening(): Flow<ShortVoiceInputResult> = callbackFlow {
        if (appKey.isBlank()) {
            trySend(ShortVoiceInputResult.Error("短语音识别 API Key 为空"))
            close()
            return@callbackFlow
        }
        if (!isListening.compareAndSet(false, true)) {
            trySend(ShortVoiceInputResult.Error("语音输入正在进行中"))
            close()
            return@callbackFlow
        }

        audioChannel = Channel(Channel.BUFFERED)
        val requestId = UUID.randomUUID().toString()
        val request = Request.Builder()
            .url(buildWebSocketUrl(requestId))
            .header("Authorization", "Bearer $appKey")
            .build()
        Log.d(TAG, "Starting short ASR requestId=$requestId keyConfigured=${appKey.isNotBlank()}")

        val listener = object : WebSocketListener() {
            @Volatile
            private var handshakeDone = false
            private var audioSender: Job? = null

            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "Short ASR WebSocket opened requestId=$requestId")
                val startedMessage = JSONObject().apply {
                    put("type", "started")
                    put("request_id", requestId)
                    put("asr_info", JSONObject().apply {
                        put("audio_type", "pcm")
                        put("lang", "cn")
                        put("punctuation", 1)
                        put("chinese2digital", 1)
                        put("end_vad_time", 1440)
                        put("mini_speech_time", 300)
                    })
                }
                webSocket.send(startedMessage.toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching {
                    val json = JSONObject(text)
                    val action = json.optString("action")
                    val code = json.optInt("code")
                    when (action) {
                        "started" -> {
                            if (code == 0) {
                                Log.d(TAG, "Short ASR handshake success requestId=$requestId")
                                handshakeDone = true
                                audioSender = CoroutineScope(Dispatchers.IO).launchAudio(webSocket)
                            } else {
                                Log.w(TAG, "Short ASR handshake failed requestId=$requestId code=$code")
                                trySend(ShortVoiceInputResult.Error(json.optString("desc", "短语音握手失败")))
                                close()
                            }
                        }
                        "result" -> {
                            Log.d(TAG, "Short ASR result requestId=$requestId code=$code")
                            val data = json.optJSONObject("data")
                            val isLast = data?.optBoolean("is_last", false) ?: false
                            when (code) {
                                8 -> partialText(data)
                                    ?.takeIf { it.isNotBlank() }
                                    ?.let { trySend(ShortVoiceInputResult.Partial(it)) }
                                0, 9 -> {
                                    val finalText = finalText(data).orEmpty()
                                    if (finalText.isNotBlank()) {
                                        trySend(ShortVoiceInputResult.Final(finalText, isLast = isLast))
                                    }
                                }
                            }
                        }
                        "error" -> {
                            Log.w(TAG, "Short ASR server error requestId=$requestId code=$code")
                            trySend(ShortVoiceInputResult.Error(json.optString("desc", "短语音识别失败")))
                        }
                    }
                }.onFailure {
                    Log.w(TAG, "Failed to parse ASR message", it)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "Short ASR WebSocket failure", t)
                if (isListening.get()) {
                    trySend(ShortVoiceInputResult.Error(t.message ?: "短语音服务连接失败"))
                }
                close()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                isListening.set(false)
                audioSender?.cancel()
            }

            private fun CoroutineScope.launchAudio(webSocket: WebSocket) =
                launch {
                    val channel = audioChannel ?: return@launch
                    for (audioData in channel) {
                        if (isListening.get() && handshakeDone) {
                            webSocket.send(ByteString.of(*audioData))
                        }
                    }
                }
        }

        webSocket = client.newWebSocket(request, listener)

        awaitClose {
            stopListening()
        }
    }

    suspend fun sendAudioData(data: ByteArray) {
        if (isListening.get()) {
            audioChannel?.send(data)
        }
    }

    fun finishAudioInput() {
        if (!isListening.get()) return
        audioChannel?.close()
        audioChannel = null
        runCatching {
            Log.d(TAG, "Short ASR finish audio input")
            webSocket?.send(ByteString.of(*"--end--".toByteArray()))
        }
    }

    fun stopListening() {
        finishAudioInput()
        if (!isListening.getAndSet(false)) return
        audioChannel?.close()
        audioChannel = null
        webSocket?.close(1000, "stopped")
        webSocket = null
    }

    private fun partialText(data: JSONObject?): String? {
        if (data == null) return null
        return listOf("var", "text")
            .firstNotNullOfOrNull { key -> data.optString(key).takeIf { it.isNotBlank() } }
    }

    private fun finalText(data: JSONObject?): String? {
        if (data == null) return null
        return listOf("onebest", "text")
            .firstNotNullOfOrNull { key -> data.optString(key).takeIf { it.isNotBlank() } }
    }

    private fun buildWebSocketUrl(requestId: String): String {
        val params = listOf(
            "client_version=${encode("1.0.0")}",
            "package=${encode("com.socialfirstaidkit")}",
            "sdk_version=${encode("1.0.0")}",
            "user_id=${encode("chat_voice_input")}",
            "android_version=${encode("12")}",
            "system_time=${System.currentTimeMillis()}",
            "net_type=1",
            "engineid=${encode("shortasrinput")}",
            "user_info=1",
            "requestId=$requestId"
        )
        return "${configuredWebSocketEndpoint()}?${params.joinToString("&")}"
    }

    private fun configuredWebSocketEndpoint(): String {
        val trimmedBaseUrl = baseUrl.trim().ifBlank { DEFAULT_BASE_URL }.trimEnd('/')
        val wsBaseUrl = when {
            trimmedBaseUrl.startsWith("wss://", ignoreCase = true) ||
                trimmedBaseUrl.startsWith("ws://", ignoreCase = true) -> trimmedBaseUrl
            trimmedBaseUrl.startsWith("https://", ignoreCase = true) ->
                "wss://${trimmedBaseUrl.removePrefix("https://")}"
            trimmedBaseUrl.startsWith("http://", ignoreCase = true) ->
                "ws://${trimmedBaseUrl.removePrefix("http://")}"
            else -> "wss://$trimmedBaseUrl"
        }
        return if (wsBaseUrl.endsWith("/asr/v2")) wsBaseUrl else "$wsBaseUrl/asr/v2"
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}
