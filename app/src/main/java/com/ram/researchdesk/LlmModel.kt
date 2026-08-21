package com.ram.researchdesk

enum class LlmModel(
    val id: String,
    val displayName: String,
    val shortName: String,
    val sizeMB: Int,
    val url: String,
    val filename: String,
) {
    GEMMA4_E4B(
        id = "gemma4-e4b",
        displayName = "Gemma 4 E4B",
        shortName = "4B",
        sizeMB = 2831,
        url = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it-gpu.litertlm",
        filename = "gemma-4-E4B-it-gpu.litertlm",
    ),
    ;

    companion object {
        val DEFAULT = GEMMA4_E4B
        fun fromId(id: String): LlmModel = entries.find { it.id == id } ?: DEFAULT
    }
}
