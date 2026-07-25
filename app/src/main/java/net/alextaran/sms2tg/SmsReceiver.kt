package net.alextaran.sms2tg

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import androidx.work.Constraints
import android.os.Build
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class SmsReceiver : BroadcastReceiver() {
    private val TAG = "SMS2TG Receiver"

    override fun onReceive(context: Context?, intent: Intent?) {
        Log.i(TAG, "onReceive")
        if (Telephony.Sms.Intents.SMS_RECEIVED_ACTION != intent?.action) {
            Log.i(TAG, "Received incorrect action: ${intent?.action}")
            return
        }
        if (context == null) {
            Log.i(TAG, "Context is null")
            return
        }
        val subscriptionIndex = intent.getIntExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, -1)
        val simSlotIndex = intent.getIntExtra(SubscriptionManager.EXTRA_SLOT_INDEX, -1)
        val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager

        if (subscriptionManager == null) {
            Log.i(TAG, "Subscription manager is null")
            return
        }

        val carrier = if (subscriptionIndex >= 0) {
            try {
                val subscriptionInfo =
                    subscriptionManager.getActiveSubscriptionInfo(subscriptionIndex)
                if (subscriptionInfo.carrierName == subscriptionInfo.displayName) {
                    subscriptionInfo.carrierName.toString()
                } else {
                    "${subscriptionInfo.displayName} (${subscriptionInfo.carrierName})"
                }
            } catch (e: SecurityException) {
                Log.i(TAG, "SecurityException when getting carrier")
                "(permission error)"
            }
        } else {
            ""
        }

        val phoneNumber: String = if (subscriptionIndex >= 0) {
            try {
                val phoneNumber = subscriptionManager.getPhoneNumber(subscriptionIndex)
                phoneNumber
            } catch (e: SecurityException) {
                Log.i(TAG, "SecurityException when getting phone number")
                "(permission error)"
            }
        } else {
            ""
        }

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)

        if (messages.isNullOrEmpty()) {
            Log.i(TAG, "onReceive finished early - No messages in intent")
            return
        }

        val firstSender = messages[0].originatingAddress
        val allSameSender = messages.all { it.originatingAddress == firstSender }

        if (allSameSender) {
            // Building a single message from all fragments
            val firstMessage = messages[0]
            val fullMessageBody = messages.joinToString("") { it.messageBody ?: "" }
            val fullDisplayMessageBody = messages.joinToString("") { it.displayMessageBody ?: "" }

            val text = buildMessageText(
                header = "*New SMS Received*",
                simSlotIndex = simSlotIndex,
                phoneNumber = phoneNumber,
                carrier = carrier,
                originatingAddress = firstMessage.originatingAddress,
                displayOriginatingAddress = firstMessage.displayOriginatingAddress,
                messageBody = fullMessageBody,
                displayMessageBody = fullDisplayMessageBody,
                timestampMillis = firstMessage.timestampMillis
            )

            val workReq = SmsWorker.createWorkRequest(text)
            Log.i(TAG, "enqueuing combined work request")
            WorkManager.getInstance(context).enqueue(workReq)

        } else {
            // Fallback: sending as separate messages
            Log.w(TAG, "Messages have different senders in one intent! Falling back to separate processing.")

            messages.forEach { smsMessage ->
                val text = buildMessageText(
                    header = "*New SMS Received (Fragment)*",
                    simSlotIndex = simSlotIndex,
                    phoneNumber = phoneNumber,
                    carrier = carrier,
                    originatingAddress = smsMessage.originatingAddress,
                    displayOriginatingAddress = smsMessage.displayOriginatingAddress,
                    messageBody = smsMessage.messageBody,
                    displayMessageBody = smsMessage.displayMessageBody,
                    timestampMillis = smsMessage.timestampMillis
                )

                val workReq = SmsWorker.createWorkRequest(text)
                Log.i(TAG, "enqueuing fragmented work request")
                WorkManager.getInstance(context).enqueue(workReq)
            }
        }
        Log.i(TAG, "onReceive finished")
    }

    private fun buildMessageText(
        header: String,
        simSlotIndex: Int,
        phoneNumber: String,
        carrier: String,
        originatingAddress: String?,
        displayOriginatingAddress: String?,
        messageBody: String?,
        displayMessageBody: String?,
        timestampMillis: Long
    ): String {
        var text = "$header\n\n"
        text += "*Device*: ${Build.MANUFACTURER.escapeTgMarkdown()} ${Build.MODEL.escapeTgMarkdown()}\n"
        text += "*SIM Slot Index:* ${simSlotIndex.toString().escapeTgMarkdown()}\n"

        if (phoneNumber.isNotEmpty()) {
            text += "*Phone:* ${phoneNumber.escapeTgMarkdown()}\n"
        }
        if (carrier.isNotEmpty()) {
            text += "*Carrier:* ${carrier.escapeTgMarkdown()}\n"
        }

        if (originatingAddress == displayOriginatingAddress) {
            text += "*OriginatingAddress:* `${originatingAddress?.escapeTgMarkdown()}`\n"
        } else {
            text += "*OriginatingAddress:* `${originatingAddress?.escapeTgMarkdown()}`\n" +
                    "*DisplayOriginatingAddress:* `${displayOriginatingAddress?.escapeTgMarkdown()}`\n"
        }

        if (messageBody == displayMessageBody) {
            text += "*MessageBody:* `${messageBody?.escapeTgMarkdown()}`\n"
        } else {
            text += "*MessageBody:* `${messageBody?.escapeTgMarkdown()}`\n" +
                    "*DisplayMessageBody:* `${displayMessageBody?.escapeTgMarkdown()}`\n"
        }

        text += "*Time:* ${LocalDateTime.ofInstant(Instant.ofEpochMilli(timestampMillis), ZoneId.systemDefault()).toString().escapeTgMarkdown()}"

        return text
    }
}