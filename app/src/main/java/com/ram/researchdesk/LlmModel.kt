package com.ram.researchdesk

enum class LlmModel(
    val id: String,
    val displayName: String,
    val shortName: String,
    val sizeMB: Int,
    val url: String,
    val filename: String,
) {
    QWEN3_0_6B(
        id = "qwen3-0.6b",
        displayName = "Qwen3 0.6B",
        shortName = "0.6B",
        sizeMB = 328,
        url = "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm",
        filename = "Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm",
    ),
    ;

    companion object {
        val DEFAULT = QWEN3_0_6B
        fun fromId(id: String): LlmModel = entries.find { it.id == id } ?: DEFAULT
    }
}
