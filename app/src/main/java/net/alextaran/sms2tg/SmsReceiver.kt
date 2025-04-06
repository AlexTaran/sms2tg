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

        messages.forEach { smsMessage ->
            var text = "*New SMS Received*\n\n"
            text += "*Device*: ${Build.MANUFACTURER.escapeTgMarkdown()} ${Build.MODEL.escapeTgMarkdown()}\n"
            text += "*SIM Slot Index:* ${simSlotIndex.toString().escapeTgMarkdown()}\n"
            if (phoneNumber.isNotEmpty()) {
                text += "*Phone:* ${phoneNumber.escapeTgMarkdown()}\n"
            }
            if (carrier.isNotEmpty()){
                text += "*Carrier:* ${carrier.escapeTgMarkdown()}\n"
            }
            text += if (smsMessage.originatingAddress == smsMessage.displayOriginatingAddress) {
                "*OriginatingAddress:* `${smsMessage.originatingAddress?.escapeTgMarkdown()}`\n"
            } else {
                "*OriginatingAddress:* `${smsMessage.originatingAddress?.escapeTgMarkdown()}`\n" +
                "*DisplayOriginatingAddress:* `${smsMessage.displayOriginatingAddress?.escapeTgMarkdown()}`\n"
            }
            text += if (smsMessage.messageBody == smsMessage.displayMessageBody) {
                "*MessageBody:* `${smsMessage.messageBody.escapeTgMarkdown()}`\n"
            } else {
                "*MessageBody:* `${smsMessage.messageBody.escapeTgMarkdown()}`\n" +
                "*DisplayMessageBody:* `${smsMessage.displayMessageBody.escapeTgMarkdown()}`\n"
            }
            text += "*Time:* ${LocalDateTime.ofInstant(Instant.ofEpochMilli(smsMessage.timestampMillis), ZoneId.systemDefault()).toString().escapeTgMarkdown()}"
            val workReq = SmsWorker.createWorkRequest(text)
            Log.i(TAG, "enqueuing work request")
            WorkManager.getInstance(context).enqueue(workReq)
        }
        Log.i(TAG, "onReceive finished")
    }
}