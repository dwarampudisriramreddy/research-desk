package com.ram.researchdesk

import android.content.Context
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
import kotlinx.coroutines.CompletableDeferred

private const val TAG = "LlmEngine"

data class LlmConfig(
    val topK: Int = 40,
    val topP: Float = 0.95f,
    val temperature: Float = 0.7f,
    val maxTokens: Int = 2048,
    val systemPrompt: String = "You are a helpful research assistant.",
)

class LlmEngine(private val context: Context) {

    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private val initDeferred = CompletableDeferred<Boolean>()
    var isReady = false
        private set
    var error: String? = null
        private set

    val ready: Boolean get() = isReady

    suspend fun initialize(modelPath: String, config: LlmConfig = LlmConfig()) {
        if (isReady) return
        error = null

        val backends = listOf(
            "GPU" to Backend.gpu(),
            "CPU" to Backend.cpu(threadCount = 4),
        )

        for ((name, backend) in backends) {
            try {
                engine?.close()
                engine = null

                Log.d(TAG, "Trying $name backend with model: $modelPath")
                val engineConfig = EngineConfig(
                    modelPath = modelPath,
                    backend = backend,
                    maxNumTokens = config.maxTokens,
                )
                val eng = Engine(engineConfig)
                eng.initialize()

                val conv = eng.createConversation(
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

                engine = eng
                conversation = conv
                isReady = true
                error = null
                Log.d(TAG, "=== ENGINE READY ($name) ===")
                initDeferred.complete(true)
                return
            } catch (e: Exception) {
                Log.e(TAG, "$name init FAILED: ${e.message}")
                error = "$name: ${e.message}"
            }
        }
        error = "All backends failed. $error"
        Log.e(TAG, "=== LLM INIT FAILED ===")
        initDeferred.complete(false)
    }

    suspend fun awaitReady(): Boolean = initDeferred.await()

    /**
     * Streaming inference — exact pattern from Gallery's LlmChatModelHelper.runInference.
     * Uses conversation.sendMessageAsync() with MessageCallback.
     */
    fun sendMessage(
        input: String,
        resultListener: (partialResult: String, done: Boolean) -> Unit,
        onError: (String) -> Unit = {},
    ) {
        val conv = conversation
        if (conv == null) {
            onError("Engine not initialized")
            return
        }

        try {
            // Build contents list — text after optional images/audio, same as Gallery
            val contents = mutableListOf<Content>()
            if (input.trim().isNotEmpty()) {
                contents.add(Content.Text(input))
            }

            conv.sendMessageAsync(
                Contents.of(contents),
                object : MessageCallback {
                    override fun onMessage(message: Message) {
                        resultListener(message.toString(), false)
                    }

                    override fun onDone() {
                        resultListener("", true)
                    }

                    override fun onError(throwable: Throwable) {
                        Log.e(TAG, "Inference error", throwable)
                        onError("Error: ${throwable.message}")
                        resultListener("", true)
                    }
                },
                emptyMap<String, Any>(),
            )
        } catch (e: Exception) {
            onError("Send failed: ${e.message}")
        }
    }

    /**
     * Non-streaming inference using sendMessage.
     */
    suspend fun sendMessageSync(input: String): String {
        val conv = conversation ?: throw IllegalStateException("Engine not initialized")
        val contents = mutableListOf<Content>()
        if (input.trim().isNotEmpty()) {
            contents.add(Content.Text(input))
        }
        val response = conv.sendMessage(Contents.of(contents))
        return response.toString()
    }

    fun stopResponse() {
        try {
            conversation?.cancelProcess()
        } catch (e: Exception) {
            Log.w(TAG, "Stop failed: ${e.message}")
        }
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
        try {
            conversation?.close()
        } catch (_: Exception) {}
        try {
            engine?.close()
        } catch (_: Exception) {}
        engine = null
        conversation = null
        isReady = false
        Log.d(TAG, "Engine closed")
    }
}
