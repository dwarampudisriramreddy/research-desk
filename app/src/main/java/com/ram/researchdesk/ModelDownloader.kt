package com.ram.researchdesk

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

private const val TAG = "ModelDownloader"

data class DownloadProgress(
    val bytesReceived: Long,
    val totalBytes: Long,
    val bytesPerSecond: Long = 0,
) {
    val percent: Float get() = if (totalBytes > 0) bytesReceived.toFloat() / totalBytes else 0f
    val isComplete: Boolean get() = totalBytes > 0 && bytesReceived >= totalBytes
}

object ModelDownloader {

    private const val PREFS_NAME = "llm_model"
    private const val KEY_MODEL_ID = "selected_model_id"

    fun selectedModel(context: Context): LlmModel {
        val id = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_MODEL_ID, null)
        return if (id != null) LlmModel.fromId(id) else LlmModel.DEFAULT
    }

    fun setSelectedModel(context: Context, model: LlmModel) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_MODEL_ID, model.id).apply()
    }

    fun modelPath(context: Context, model: LlmModel = selectedModel(context)): String {
        return File(context.filesDir, model.filename).absolutePath
    }

    fun isDownloaded(context: Context, model: LlmModel = selectedModel(context)): Boolean {
        val f = File(modelPath(context, model))
        return f.exists() && f.length() > 10_000_000
    }

    suspend fun download(
        context: Context,
        model: LlmModel = selectedModel(context),
        onProgress: (DownloadProgress) -> Unit = {},
    ): Result<String> = withContext(Dispatchers.IO) {
        val targetFile = File(context.filesDir, model.filename)
        val tmpFile = File(context.filesDir, "${model.filename}.tmp")

        if (isDownloaded(context, model)) {
            Log.d(TAG, "Model already cached: ${model.filename}")
            return@withContext Result.success(modelPath(context, model))
        }

        // Clean up other model files to save space
        LlmModel.entries.filter { it != model }.forEach { other ->
            val f = File(context.filesDir, other.filename)
            if (f.exists()) { f.delete(); Log.d(TAG, "Cleaned other model: ${other.filename}") }
            val tmp = File(context.filesDir, "${other.filename}.tmp")
            if (tmp.exists()) tmp.delete()
        }
        // Clean up stale files from previous versions
        listOf(
            "gemma3-1b-it-int4.litertlm",
            "Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.litertlm",
            "Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.litertlm.tmp",
            "gemma-4-E2B-it.litertlm",
            "gemma-4-E2B-it-int4.litertlm",
            "gemma-4-E2B-it-gpu.litertlm",
        ).forEach { old ->
            val f = File(context.filesDir, old)
            if (f.exists()) { f.delete(); Log.d(TAG, "Cleaned stale: $old") }
        }

        // Resume from partial download
        var downloadedBytes = 0L
        if (tmpFile.exists()) {
            downloadedBytes = tmpFile.length()
            Log.d(TAG, "Resuming from ${downloadedBytes / 1024 / 1024} MB")
        }

        var conn: HttpURLConnection? = null
        try {
            val url = URL(model.url)
            conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 30_000
            conn.readTimeout = 30_000
            if (downloadedBytes > 0) {
                conn.setRequestProperty("Range", "bytes=$downloadedBytes-")
            }
            conn.connect()

            val code = conn.responseCode
            val isResume = code == 206
            val totalBytes = if (isResume) {
                downloadedBytes + conn.contentLengthLong
            } else {
                if (downloadedBytes > 0) {
                    downloadedBytes = 0
                    tmpFile.delete()
                }
                conn.contentLengthLong
            }

            Log.d(TAG, "Starting download: ${(totalBytes / 1024 / 1024)} MB, resume=$isResume")

            val input = conn.inputStream
            val output = if (isResume) {
                tmpFile.appendOutputStream()
            } else {
                tmpFile.outputStream()
            }

            val buffer = ByteArray(8192)
            var bytesRead: Int
            var totalRead = downloadedBytes
            val startTime = System.currentTimeMillis()

            var lastNotifyTime = 0L
            while (input.read(buffer).also { bytesRead = it } != -1) {
                output.write(buffer, 0, bytesRead)
                totalRead += bytesRead

                val now = System.currentTimeMillis()
                if (now - lastNotifyTime >= 250 || totalRead >= totalBytes) {
                    lastNotifyTime = now
                    val elapsed = (now - startTime) / 1000.0
                    val bps = if (elapsed > 0) (totalRead / elapsed).toLong() else 0
                    onProgress(
                        DownloadProgress(
                            bytesReceived = totalRead,
                            totalBytes = totalBytes,
                            bytesPerSecond = bps,
                        )
                    )
                }
            }

            output.flush()
            output.close()
            input.close()

            if (targetFile.exists()) targetFile.delete()
            tmpFile.renameTo(targetFile)

            Log.d(TAG, "=== DOWNLOAD COMPLETE === Size: ${(targetFile.length() / 1024 / 1024)} MB")
            Result.success(targetFile.absolutePath)
        } catch (e: Exception) {
            Log.e(TAG, "Download failed: ${e.message}")
            Result.failure(e)
        } finally {
            conn?.disconnect()
        }
    }

    fun deleteModel(context: Context, model: LlmModel = selectedModel(context)) {
        val f = File(modelPath(context, model))
        if (f.exists()) {
            f.delete()
            Log.d(TAG, "Model deleted: ${model.filename}")
        }
        val tmp = File(context.filesDir, "${model.filename}.tmp")
        if (tmp.exists()) tmp.delete()
        // Clean up all model files
        LlmModel.entries.forEach { other ->
            val oldFile = File(context.filesDir, other.filename)
            if (oldFile.exists()) {
                oldFile.delete()
                Log.d(TAG, "Deleted model: ${other.filename}")
            }
        }
    }
}

private fun File.appendOutputStream(): FileOutputStream = FileOutputStream(this, true)
