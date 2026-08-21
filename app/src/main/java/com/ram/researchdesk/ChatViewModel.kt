package com.ram.researchdesk

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "ChatViewModel"

data class ChatMessage(
    val role: String, // "user" or "model"
    val content: String,
    val isStreaming: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
)

sealed class UiState {
    data object Idle : UiState()
    data class Downloading(val progress: DownloadProgress) : UiState()
    data object Initializing : UiState()
    data class Chatting(val messages: List<ChatMessage>) : UiState()
    data class Error(val message: String, val canRetry: Boolean = true) : UiState()
}

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private var llmEngine: LlmEngine? = null
    private val context get() = getApplication<Application>()

    init {
        checkModel()
    }

    private fun checkModel() {
        if (ModelDownloader.isDownloaded(context)) {
            initializeEngine()
        } else {
            _uiState.value = UiState.Idle
        }
    }

    fun startDownload() {
        viewModelScope.launch {
            _uiState.value = UiState.Downloading(DownloadProgress(0, 1))
            ModelDownloader.download(context) { progress ->
                _uiState.value = UiState.Downloading(progress)
            }.onSuccess {
                initializeEngine()
            }.onFailure { e ->
                _uiState.value = UiState.Error("Download failed: ${e.message}")
            }
        }
    }

    private fun initializeEngine() {
        viewModelScope.launch {
            _uiState.value = UiState.Initializing
            val engine = LlmEngine(context)
            llmEngine = engine
            val modelPath = ModelDownloader.modelPath(context)
            engine.initialize(modelPath)

            if (engine.isReady) {
                _uiState.value = UiState.Chatting(emptyList())
                Log.d(TAG, "LLM ready")
            } else {
                _uiState.value = UiState.Error(
                    engine.error ?: "Unknown init error",
                    canRetry = true,
                )
            }
        }
    }

    fun retry() {
        llmEngine?.close()
        llmEngine = null
        checkModel()
    }

    fun deleteModel() {
        llmEngine?.close()
        llmEngine = null
        ModelDownloader.deleteModel(context)
        _messages.value = emptyList()
        _uiState.value = UiState.Idle
    }

    fun sendMessage(text: String) {
        val engine = llmEngine ?: return
        if (!engine.isReady || text.isBlank() || _isGenerating.value) return

        val userMsg = ChatMessage(role = "user", content = text)
        val modelMsg = ChatMessage(role = "model", content = "", isStreaming = true)

        _messages.value = _messages.value + userMsg + modelMsg
        _isGenerating.value = true
        _uiState.value = UiState.Chatting(_messages.value)

        val buffer = StringBuilder()

        engine.sendMessage(
            input = text,
            resultListener = { partial, done ->
                if (!done) {
                    buffer.append(partial)
                    updateLastModelMessage(buffer.toString(), isStreaming = true)
                } else {
                    updateLastModelMessage(buffer.toString(), isStreaming = false)
                    _isGenerating.value = false
                }
            },
            onError = { error ->
                updateLastModelMessage("Error: $error", isStreaming = false)
                _isGenerating.value = false
            },
        )
    }

    fun stopGeneration() {
        llmEngine?.stopResponse()
        _isGenerating.value = false
        updateLastModelMessage(
            _messages.value.lastOrNull { it.role == "model" }?.content ?: "",
            isStreaming = false,
        )
    }

    fun resetChat() {
        llmEngine?.resetConversation()
        _messages.value = emptyList()
        if (llmEngine?.isReady == true) {
            _uiState.value = UiState.Chatting(emptyList())
        }
    }

    private fun updateLastModelMessage(content: String, isStreaming: Boolean) {
        val current = _messages.value.toMutableList()
        val lastIndex = current.indexOfLast { it.role == "model" }
        if (lastIndex >= 0) {
            current[lastIndex] = current[lastIndex].copy(
                content = content,
                isStreaming = isStreaming,
            )
            _messages.value = current
            _uiState.value = UiState.Chatting(current)
        }
    }

    override fun onCleared() {
        super.onCleared()
        llmEngine?.close()
    }
}
