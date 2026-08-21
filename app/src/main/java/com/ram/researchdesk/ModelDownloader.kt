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
private const val MODEL_URL =
    "https://huggingface.co/prathameshchougale/saley-gemma-3-1b-it-litertlm/resolve/main/gemma3-1b-it-int4.litertlm"
private const val MODEL_FILENAME = "gemma3-1b-it-int4.litertlm"

data class DownloadProgress(
    val bytesReceived: Long,
    val totalBytes: Long,
    val bytesPerSecond: Long = 0,
) {
    val percent: Float get() = if (totalBytes > 0) bytesReceived.toFloat() / totalBytes else 0f
    val isComplete: Boolean get() = totalBytes > 0 && bytesReceived >= totalBytes
}

object ModelDownloader {

    fun modelPath(context: Context): String {
        return File(context.filesDir, MODEL_FILENAME).absolutePath
    }

    fun isDownloaded(context: Context): Boolean {
        val f = File(modelPath(context))
        return f.exists() && f.length() > 10_000_000
    }

    suspend fun download(
        context: Context,
        onProgress: (DownloadProgress) -> Unit = {},
    ): Result<String> = withContext(Dispatchers.IO) {
        val targetFile = File(context.filesDir, MODEL_FILENAME)
        val tmpFile = File(context.filesDir, "$MODEL_FILENAME.tmp")

        if (isDownloaded(context)) {
            Log.d(TAG, "Model already cached")
            return@withContext Result.success(modelPath(context))
        }

        // Resume from partial download
        var downloadedBytes = 0L
        if (tmpFile.exists()) {
            downloadedBytes = tmpFile.length()
            Log.d(TAG, "Resuming from ${downloadedBytes / 1024 / 1024} MB")
        }

        var conn: HttpURLConnection? = null
        try {
            val url = URL(MODEL_URL)
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

            while (input.read(buffer).also { bytesRead = it } != -1) {
                output.write(buffer, 0, bytesRead)
                totalRead += bytesRead

                val elapsed = (System.currentTimeMillis() - startTime) / 1000.0
                val bps = if (elapsed > 0) (totalRead / elapsed).toLong() else 0

                onProgress(
                    DownloadProgress(
                        bytesReceived = totalRead,
                        totalBytes = totalBytes,
                        bytesPerSecond = bps,
                    )
                )
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

    fun deleteModel(context: Context) {
        val f = File(modelPath(context))
        if (f.exists()) {
            f.delete()
            Log.d(TAG, "Model deleted")
        }
        val tmp = File(context.filesDir, "$MODEL_FILENAME.tmp")
        if (tmp.exists()) tmp.delete()
    }
}

private fun File.appendOutputStream(): FileOutputStream = FileOutputStream(this, true)
