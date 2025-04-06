package net.alextaran.sms2tg

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.inputmethodservice.InputMethodService
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import android.view.LayoutInflater
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.getSystemService
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class MainActivity : AppCompatActivity() {

    private lateinit var batteryOptimizationStatus: TextView
    private lateinit var permissionStatus: Map<String, TextView> // permission -> status
    private lateinit var buttonOpenSettings: Button

    private lateinit var telegramUserIdText: TextView
    private lateinit var telegramUserIdUpdate: Button
    private lateinit var telegramTokenText: TextView
    private lateinit var telegramTokenUpdate: Button
    private lateinit var telegramTestButton: Button
    private lateinit var smsWorkerTestButton: Button

    private val telegramDataAccessor = TelegramDataAccessor(this)
    private val httpClient = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        batteryOptimizationStatus = findViewById(R.id.battery_optimization_status)
        permissionStatus = mapOf<String, TextView>(
            Manifest.permission.RECEIVE_SMS to findViewById(R.id.permission_sms_status),
            Manifest.permission.READ_PHONE_STATE to findViewById(R.id.permission_read_phone_state_status),
            Manifest.permission.READ_PHONE_NUMBERS to findViewById(R.id.permission_read_phone_numbers_status),
        )
        buttonOpenSettings = findViewById(R.id.button_open_settings)
        telegramUserIdText = findViewById(R.id.telegram_user_id_text)
        telegramUserIdUpdate = findViewById(R.id.telegram_user_id_update)
        telegramTokenText = findViewById(R.id.telegram_token_text)
        telegramTokenUpdate = findViewById(R.id.telegram_token_update)
        telegramTestButton = findViewById(R.id.telegram_test_button)
        smsWorkerTestButton = findViewById(R.id.sms_worker_test_button)

        buttonOpenSettings.setOnClickListener {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
            })
        }

        telegramUserIdUpdate.setOnClickListener {
            showTextInputDialog("Telegram User ID") {text ->
                telegramDataAccessor.updateTelegramUserId(text)
                updateTelegramDataStatus()
            }
        }

        telegramTokenUpdate.setOnClickListener {
            showTextInputDialog("Telegram Token") {text ->
                telegramDataAccessor.updateTelegramToken(text)
                updateTelegramDataStatus()
            }
        }

        telegramTestButton.setOnClickListener {
            telegramTestButton.isEnabled = false
            sendTelegramTestMessage {
                telegramTestButton.isEnabled = true
            }
        }

        smsWorkerTestButton.setOnClickListener {
            sendTestMessageViaWorker()
        }

        checkAndRequestPermissions {
            checkBatteryOptimization()
        }
    }

    override fun onResume() {
        super.onResume()

        permissionStatus.forEach {(permission, status) ->
            status.text = if (checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) TEXT_GRANTED else TEXT_DENIED
        }
        updateTelegramDataStatus()
        updateBatteryOptimizationStatus()
    }

    private fun checkAndRequestPermissions(next: () -> Unit) {
        val deniedPermissions = mutableListOf<String>()
        permissionStatus.forEach { (permission, status) ->
            if (checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) {
                status.text = TEXT_GRANTED
            } else {
                deniedPermissions += permission
            }
        }
        if (deniedPermissions.isEmpty()) {
            next()
            return
        }
        val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            permissionStatus.forEach { (permission, status) ->
                if (permissions.containsKey(permission)) {
                    status.text = if (permissions.getOrDefault(permission, false)) TEXT_GRANTED else TEXT_DENIED
                }
            }
            next()
        }
        requestPermissionLauncher.launch(deniedPermissions.toTypedArray())
    }


    private fun checkBatteryOptimization() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (powerManager.isIgnoringBatteryOptimizations(packageName)) {
            batteryOptimizationStatus.text = TEXT_GRANTED
            return
        }
        startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:$packageName")
        })
    }

    private fun updateBatteryOptimizationStatus() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        batteryOptimizationStatus.text = if (powerManager.isIgnoringBatteryOptimizations(packageName)) TEXT_GRANTED else TEXT_DENIED
    }

    private fun updateTelegramDataStatus() {
        val tgData = telegramDataAccessor.readTelegramData()
        telegramUserIdText.text = tgData.getUserIdSafe()
        telegramTokenText.text = tgData.getTokenSafe()
    }

    private fun showTextInputDialog(title: String, handler: (text: String) -> Unit) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_text_input, null)
        val inputData = dialogView.findViewById<TextInputEditText>(R.id.dialog_text_input_edit_text)
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setView(dialogView)
            .setPositiveButton("OK") { _, _ ->
                val text = inputData.text.toString().trim()
                handler(text)
            }.setNegativeButton("Cancel") { dialogInterface, _ ->
                dialogInterface.cancel()
            }.create()
        dialog.setOnShowListener {
            inputData.requestFocus()
            inputData.postDelayed({
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
                imm?.showSoftInput(inputData, InputMethodManager.SHOW_IMPLICIT)
            }, 100)

        }
        dialog.show()
    }

    private fun sendTelegramTestMessage(finally: () -> Unit) {
        val tgData = telegramDataAccessor.readTelegramData()
        if (!tgData.isValid()) {
            Toast.makeText(this,"Telegram data is invalid", Toast.LENGTH_SHORT).show()
            finally()
            return
        }

        val request = tgData.createSendMessageRequest("""
                *New SMS Received*
                
                *Device*: ${Build.MANUFACTURER.escapeTgMarkdown()} ${Build.MODEL.escapeTgMarkdown()}
                *OriginatingAddress:* `${"smsMessage.originatingAddress".escapeTgMarkdown()}`
                *DisplayOriginatingAddress:* `${"smsMessage.displayOriginatingAddress".escapeTgMarkdown()}`
                *MessageBody:* `${"smsMessage.messageBody".escapeTgMarkdown()}`
                *DisplayMessageBody:* `${"smsMessage.displayMessageBody".escapeTgMarkdown()}`
                *Time:* ${"smsMessage.timestampMillis".escapeTgMarkdown()}
            """.trimIndent())

        httpClient.newCall(request).enqueue(object :Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread{
                    showErrorDialog("IO Error", e.localizedMessage ?: "")
                    finally()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                runOnUiThread{
                    if (response.isSuccessful) {
                        Toast.makeText(this@MainActivity,"Message sent OK", Toast.LENGTH_SHORT).show()
                    } else {
                        showErrorDialog("Unsuccessful", "Error: $responseBody")
                    }
                    finally()
                }
            }
        })
    }

    private fun sendTestMessageViaWorker() {
        val workReq = SmsWorker.createWorkRequest("*SMS 2 TG:* Worker test message at ${LocalDateTime.now().toString().escapeTgMarkdown()}")
        WorkManager.getInstance(this).enqueue(workReq)
    }

    private fun showErrorDialog(title: String, message: String) {
        val dialogView = LayoutInflater.from(this@MainActivity).inflate(R.layout.dialog_error, null)
        dialogView.findViewById<TextView>(R.id.dialog_error_text).text = message
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setView(dialogView)
            .setPositiveButton("OK") {_, _ -> }
            .create()
        dialog.show()
    }

    companion object {
        private const val TEXT_GRANTED = "granted"
        private const val TEXT_DENIED = "denied"
    }
}