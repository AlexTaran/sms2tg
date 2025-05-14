package net.alextaran.sms2tg

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.ColorInt
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.widget.Toolbar
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.work.WorkManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import java.time.LocalDateTime


class MainActivity : AppCompatActivity() {

    private lateinit var mainToolbar: Toolbar
    private lateinit var batteryOptimizationStatus: TextView
    private lateinit var permissionStatus: Map<String, TextView> // permission -> status
    private lateinit var receiverStatusText: TextView
    private lateinit var buttonSwitchReceiverStatus: Button
    private lateinit var buttonShowTutorial: Button
    private lateinit var buttonOpenSettings: Button
    private lateinit var buttonRecreateActivity: Button

    private lateinit var telegramUserIdText: TextView
    private lateinit var telegramUserIdUpdateButton: Button
    private lateinit var telegramOpenUserInfoBotButton: Button
    private lateinit var telegramTokenText: TextView
    private lateinit var telegramTokenUpdateButton: Button
    private lateinit var telegramOpenBotFatherButton: Button
    private lateinit var telegramTestButton: Button
    private lateinit var smsWorkerTestButton: Button

    private val telegramDataAccessor = TelegramDataAccessor(this)
    private val httpClient = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initializeNotificationChannel()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root_layout)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        mainToolbar = findViewById(R.id.main_toolbar)
        setSupportActionBar(mainToolbar)
        batteryOptimizationStatus = findViewById(R.id.battery_optimization_status)
        permissionStatus = mapOf<String, TextView>(
            Manifest.permission.RECEIVE_SMS to findViewById(R.id.permission_sms_status),
            Manifest.permission.READ_PHONE_STATE to findViewById(R.id.permission_read_phone_state_status),
            Manifest.permission.READ_PHONE_NUMBERS to findViewById(R.id.permission_read_phone_numbers_status),
            Manifest.permission.POST_NOTIFICATIONS to findViewById(R.id.permission_post_notifications_status),
        )
        receiverStatusText = findViewById(R.id.receiver_status_text)
        buttonSwitchReceiverStatus = findViewById(R.id.button_switch_receiver_status)
        buttonShowTutorial = findViewById(R.id.button_show_tutorial)
        buttonOpenSettings = findViewById(R.id.button_open_settings)
        buttonRecreateActivity = findViewById(R.id.button_recreate_activity)
        telegramUserIdText = findViewById(R.id.telegram_user_id_text)
        telegramUserIdUpdateButton = findViewById(R.id.telegram_user_id_update_button)
        telegramOpenUserInfoBotButton = findViewById(R.id.telegram_open_user_info_bot_button)
        telegramTokenText = findViewById(R.id.telegram_token_text)
        telegramTokenUpdateButton = findViewById(R.id.telegram_token_update_button)
        telegramOpenBotFatherButton = findViewById(R.id.telegram_open_bot_father_button)
        telegramTestButton = findViewById(R.id.telegram_test_button)
        smsWorkerTestButton = findViewById(R.id.sms_worker_test_button)

        buttonSwitchReceiverStatus.setOnClickListener {
            val desiredStatus = !telegramDataAccessor.readTelegramData().enabled
            if (desiredStatus) {
                if (!canEnable()) {
                    showErrorDialog(getString(R.string.not_enough_permissions_dialog_title), getString(R.string.not_enough_permissions_dialog_description))
                } else if (!telegramDataAccessor.readTelegramData().isValid()) {
                    showErrorDialog(getString(R.string.telegram_data_invalid_dialog_title), getString(R.string.telegram_data_invalid_dialog_description))
                } else {
                    telegramDataAccessor.updateEnabledFlag(true)
                    showManageAppIfUnusedWarningDialog()
                }
            } else {
                // Always can disable
                telegramDataAccessor.updateEnabledFlag(false)
            }

            updateTelegramDataStatus()
            updateNotificationStatus()
        }

        buttonShowTutorial.setOnClickListener {
            showTutorialDialog()
        }

        buttonOpenSettings.setOnClickListener {
            openAppSettings()
        }
        buttonRecreateActivity.setOnClickListener { recreate() }

        telegramUserIdUpdateButton.setOnClickListener {
            showTextInputDialog("Telegram User ID") {text ->
                telegramDataAccessor.updateTelegramUserId(text)
                updateTelegramDataStatus()
            }
        }

        telegramTokenUpdateButton.setOnClickListener {
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

        telegramOpenBotFatherButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse("https://t.me/BotFather")
            startActivity(intent)
        }

        telegramOpenUserInfoBotButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse("https://t.me/userinfobot")
            startActivity(intent)
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

        if (!canEnable()) {
            telegramDataAccessor.updateEnabledFlag(false)
        }

        updateNotificationStatus()

        permissionStatus.forEach {(permission, status) ->
            status.updateWithPermissionStatus(checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED)
        }
        updateTelegramDataStatus()
        updateBatteryOptimizationStatus()
    }

    private fun initializeNotificationChannel() {
        val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_DEFAULT)
        channel.description = getString(R.string.notification_channel_description)
        channel.setSound(null, null)
        channel.enableVibration(false)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun updateNotificationStatus() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (telegramDataAccessor.readTelegramData().enabled) {
            val newIntent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            val pendingIntent = PendingIntent.getActivity(this, 0, newIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val notification = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.baseline_forward_to_inbox_24)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(getString(R.string.notification_text))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setAutoCancel(false)
                .build()
            notificationManager.notify(NOTIFICATION_ID, notification)
        } else {
            notificationManager.cancel(NOTIFICATION_ID)
        }
    }

    private fun canEnable(): Boolean = areAllPermissionsGranted() && isBatteryOptimizationDisabled()
    private fun areAllPermissionsGranted(): Boolean {
        return permissionStatus.all { (permission, _) -> checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED }
    }

    private fun checkAndRequestPermissions(next: () -> Unit) {
        val deniedPermissions = mutableListOf<String>()
        permissionStatus.forEach { (permission, status) ->
            if (checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) {
                status.updateWithPermissionStatus(true)
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
                    status.updateWithPermissionStatus(permissions.getOrDefault(permission, false))
                }
            }
            next()
        }
        requestPermissionLauncher.launch(deniedPermissions.toTypedArray())
    }


    private fun checkBatteryOptimization() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (powerManager.isIgnoringBatteryOptimizations(packageName)) {
            batteryOptimizationStatus.updateWithPermissionStatus(true)
            return
        }
        startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:$packageName")
        })
    }

    private fun updateBatteryOptimizationStatus() {
        batteryOptimizationStatus.updateWithPermissionStatus(isBatteryOptimizationDisabled())
    }

    private fun permissionStatusText(permissionStatus: Boolean) =
        if (permissionStatus) getString(R.string.permission_granted) else getString(R.string.permission_denied)

    private fun permissionStatusColor(permissionStatus: Boolean): Int = getColor(
        if (permissionStatus) R.color.permission_granted else R.color.permission_denied
    )

    private fun TextView.updateWithPermissionStatus(permissionStatus: Boolean) {
        text = permissionStatusText(permissionStatus)
        setTextColor(permissionStatusColor(permissionStatus))
    }

    private fun isBatteryOptimizationDisabled(): Boolean {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(packageName)
    }

    private fun updateTelegramDataStatus() {
        val tgData = telegramDataAccessor.readTelegramData()
        telegramUserIdText.text = tgData.getUserIdSafe()
        telegramTokenText.text = tgData.getTokenSafe()
        receiverStatusText.text = if (tgData.enabled) "ON" else "OFF"
        buttonSwitchReceiverStatus.text = if (tgData.enabled) "Disable" else "Enable"
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
        val workReq = SmsWorker.createWorkRequest("*SMS 2 TG:* Worker test message on ${LocalDateTime.now().toString().escapeTgMarkdown()}")
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

    private fun showLicenseDialog() {
        val dialogView = LayoutInflater.from(this@MainActivity).inflate(R.layout.dialog_license, null)
        dialogView.findViewById<TextView>(R.id.dialog_license_copyright_notice).text = getString(R.string.copyright_notice, LocalDateTime.now().year)
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("License")
            .setView(dialogView)
            .setPositiveButton("OK") {_, _ -> }
            .create()
        dialog.show()
    }

    private fun showManageAppIfUnusedWarningDialog() {
        val dialogView = LayoutInflater.from(this@MainActivity).inflate(R.layout.dialog_error, null)
        dialogView.findViewById<TextView>(R.id.dialog_error_text).setText(R.string.manage_app_if_unused_dialog_description)
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.manage_app_if_unused_dialog_title)
            .setView(dialogView)
            .setPositiveButton("OK") {_, _ -> }
            .setNegativeButton("Open App settings") { _, _ -> openAppSettings()}
            .create()
        dialog.show()
    }

    private fun showTutorialDialog() {
        val dialogView = LayoutInflater.from(this@MainActivity).inflate(R.layout.dialog_error, null)
        dialogView.findViewById<TextView>(R.id.dialog_error_text).setText(R.string.tutorial_dialog_description)
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.tutorial_dialog_title)
            .setView(dialogView)
            .setPositiveButton("OK") {_, _ -> }
            .create()
        dialog.show()
    }

    private fun openAppSettings() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        })
    }

    private fun openLink(url: String) {
        try {
            val myIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(myIntent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(
                this, "No application can handle this request."
                        + " Please install a browser", Toast.LENGTH_LONG
            ).show()
            e.printStackTrace()
        }
    }
    private fun openAppSourceCode() = openLink("https://github.com/AlexTaran/sms2tg")

    private fun openReportProblem() = openLink("https://github.com/AlexTaran/sms2tg/issues")
    
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_toolbar_menu, menu)
        if (menu is MenuBuilder) {
            menu.setOptionalIconsVisible(true)
        }
        val textPrimaryColor = TypedValue().let {
            theme.resolveAttribute(android.R.attr.textColorPrimary, it, true)
            obtainStyledAttributes(
                it.data, intArrayOf(
                    android.R.attr.textColorPrimary
                )
            ).getColor(0, -1)
        }
        if (menu != null) {
            for (i in 0 until menu.size()) {
                menu.getItem(i).icon?.setTint(textPrimaryColor)
            }
        }

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.menu_action_license) {
            showLicenseDialog()
            return true
        }
        if (item.itemId == R.id.menu_action_source_code) {
            openAppSourceCode()
            return true
        }
        if (item.itemId == R.id.menu_action_report_problem) {
            openReportProblem()
            return true
        }
        if (item.itemId == R.id.menu_action_tutorial) {
            showTutorialDialog()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "SMS2TG_NOTIFICATION_CHANNEL"
        private const val NOTIFICATION_ID = 12345
    }
}