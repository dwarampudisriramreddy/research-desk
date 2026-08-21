package com.ram.researchdesk

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "LlmEngine"
private const val INIT_TIMEOUT_MS = 60_000L

data class LlmConfig(
    val topK: Int = 40,
    val topP: Float = 0.95f,
    val temperature: Float = 0.7f,
    val maxTokens: Int = 4096,
    val systemPrompt: String = "You are a helpful research assistant.",
)

class LlmEngine(private val context: Context) {

    @Volatile private var engine: Engine? = null
    @Volatile private var conversation: Conversation? = null
    @Volatile var isReady = false
        private set
    @Volatile var error: String? = null
        private set
    @Volatile var backendName: String = ""
        private set

    private val inferring = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())

    val ready: Boolean get() = isReady
    val isInferenceRunning: Boolean get() = inferring.get()

    suspend fun initialize(modelPath: String, config: LlmConfig = LlmConfig()) {
        if (isReady) return
        error = null

        val backends = listOf(
            "GPU" to Backend.GPU(),
            "CPU" to Backend.CPU(),
        )

        for ((name, backend) in backends) {
            try {
                engine?.close()
                engine = null
                Log.d(TAG, "Trying $name backend...")

                val eng = withContext(Dispatchers.IO) {
                    val cfg = EngineConfig(
                        modelPath = modelPath,
                        backend = backend,
                        maxNumTokens = config.maxTokens,
                        cacheDir = context.cacheDir.absolutePath,
                    )
                    val e = Engine(cfg)
                    e.initialize()
                    e
                }

                val conv = eng.createConversation(
                    ConversationConfig(
                        samplerConfig = SamplerConfig(
                            topK = config.topK,
                            topP = config.topP.toDouble(),
                            temperature = config.temperature.toDouble(),
                        ),
                    )
                )

                engine = eng
                conversation = conv
                isReady = true
                backendName = name
                Log.d(TAG, "=== ENGINE READY ($name) ===")
                return
            } catch (e: Exception) {
                Log.e(TAG, "$name init FAILED: ${e.message}")
                error = "$name: ${e.message}"
            }
        }
        error = "All backends failed. $error"
        Log.e(TAG, "=== LLM INIT FAILED ===")
    }

    /**
     * Streaming inference with main-thread callbacks.
     * Returns immediately; results arrive via resultListener on the main thread.
     */
    fun sendMessage(
        input: String,
        resultListener: (partialResult: String, done: Boolean) -> Unit,
        onError: (String) -> Unit = {},
    ) {
        val conv = conversation
        if (conv == null || !isReady) {
            onError("Engine not initialized")
            return
        }
        if (!inferring.compareAndSet(false, true)) {
            onError("Already generating a response")
            return
        }

        try {
            val contents = mutableListOf<Content>()
            if (input.trim().isNotEmpty()) {
                contents.add(Content.Text(input))
            }

            conv.sendMessageAsync(
                Contents.of(contents),
                object : MessageCallback {
                    override fun onMessage(message: Message) {
                        val text = message.toString()
                        mainHandler.post { resultListener(text, false) }
                    }

                    override fun onDone() {
                        inferring.set(false)
                        mainHandler.post { resultListener("", true) }
                    }

                    override fun onError(throwable: Throwable) {
                        inferring.set(false)
                        Log.e(TAG, "Inference error", throwable)
                        mainHandler.post {
                            onError("Error: ${throwable.message}")
                            resultListener("", true)
                        }
                    }
                },
                emptyMap<String, Any>(),
            )
        } catch (e: Exception) {
            inferring.set(false)
            onError("Send failed: ${e.message}")
        }
    }

    /**
     * Blocking (non-streaming) inference — call from background thread only.
     */
    suspend fun sendMessageSync(input: String): String = withContext(Dispatchers.IO) {
        val conv = conversation ?: throw IllegalStateException("Engine not initialized")
        val contents = mutableListOf<Content>()
        if (input.trim().isNotEmpty()) {
            contents.add(Content.Text(input))
        }
        val response = conv.sendMessage(Contents.of(contents))
        response.toString()
    }

    fun stopResponse() {
        try {
            conversation?.cancelProcess()
        } catch (e: Exception) {
            Log.w(TAG, "Stop failed: ${e.message}")
        }
        inferring.set(false)
    }

    fun resetConversation(config: LlmConfig = LlmConfig()) {
        val eng = engine ?: return
        try {
            conversation?.close()
            conversation = eng.createConversation(
                ConversationConfig(
                    samplerConfig = SamplerConfig(
                        topK = config.topK,
                        topP = config.topP.toDouble(),
                        temperature = config.temperature.toDouble(),
                    ),
                    systemInstruction = if (config.systemPrompt.isNotEmpty()) {
                        Contents.of(config.systemPrompt)
                    } else null,
                )
            )
            Log.d(TAG, "Conversation reset")
        } catch (e: Exception) {
            Log.e(TAG, "Reset failed: ${e.message}")
        }
    }

    fun close() {
        stopResponse()
        try { conversation?.close() } catch (_: Exception) {}
        try { engine?.close() } catch (_: Exception) {}
        engine = null
        conversation = null
        isReady = false
        Log.d(TAG, "Engine closed")
    }
}
