package net.alextaran.sms2tg

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import kotlinx.coroutines.CompletionHandler
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import java.lang.Exception
import java.time.LocalDateTime
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class SmsWorker(val ctx: Context, val params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        Log.i(TAG, "doWork")
        val text = inputData.getString(DATA_KEY_TEXT)
        if (text == null) {
            Log.e(TAG, "Missing text key in data")
            return Result.failure()
        }
        val tgData = TelegramDataAccessor(ctx).readTelegramData()
        if (!tgData.isValid()) {
            Log.e(TAG, "Telegram data is invalid")
            return Result.failure()
        }

        val httpClient = OkHttpClient()
        val request = tgData.createSendMessageRequest(text)

        try {
            val response = httpClient.newCall(request).await()
            val responseBody = response.body?.string()
            if (response.isSuccessful) {
                Log.e(TAG, "Sent to TG successfully")
                return Result.success()
            } else {
                Log.e(TAG, "Failure result: ${responseBody}")
                return Result.failure()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending to TG", e)
            return Result.failure()
        }
    }

    private suspend inline fun Call.await(): Response {
        return suspendCancellableCoroutine { continuation ->
            val callback = object: Callback, CompletionHandler {
                override fun onResponse(call: Call, response: Response) {
                    Log.i(TAG, "onResponse: resume")
                    continuation.resume(response)
                }

                override fun onFailure(call: Call, e: IOException) {
                    if (!call.isCanceled()) {
                        Log.i(TAG, "onFailure: resumeWithException")
                        continuation.resumeWithException(e)
                    } else {
                        Log.i(TAG, "onFailure: canceled, not resuming")
                    }
                }

                override fun invoke(cause: Throwable?) {
                    try {
                        Log.i(TAG, "Callback: invoke called")
                        cancel()
                    } catch (t: Throwable) {
                        Log.e(TAG, "Cancellation error: ${t.message}", t)
                    }
                }
            }
            enqueue(callback)
            continuation.invokeOnCancellation(callback)
        }
    }

    companion object {
        private const val TAG = "SMS2TG Worker"
        private const val DATA_KEY_TEXT = "text"

        private fun createData(text: String) = Data.Builder().putString(DATA_KEY_TEXT, text).build()

        fun createWorkRequest(text: String) = OneTimeWorkRequestBuilder<SmsWorker>()
            .setInputData(createData(text))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build())
            .build()
    }
}