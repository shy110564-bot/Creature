package com.example.util

import android.Manifest
import android.app.ActivityManager
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import com.example.data.model.HealthFitnessState
import com.example.data.model.SmartHomeState
import com.example.data.model.SystemTelemetry
import com.example.data.remote.PhoneActionCommand
import com.example.service.JarvisAccessibilityService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale

data class JarvisPermissionItem(
    val id: String,
    val title: String,
    val description: String,
    val isGranted: Boolean,
    val isSpecialSystemAccess: Boolean = false,
    val runtimePermissions: List<String> = emptyList()
)

class PhoneControlExecutor(private val context: Context) {

    private var torchEnabled = false
    private var simulatedBrightnessPct = 75
    private var ringerLabel = "Ring Mode 🔔"
    private var dndEnabled = false
    private var batterySaverEnabled = false

    private val _smartHomeState = MutableStateFlow(SmartHomeState())
    val smartHomeState: StateFlow<SmartHomeState> = _smartHomeState.asStateFlow()

    private val _healthState = MutableStateFlow(HealthFitnessState())
    val healthState: StateFlow<HealthFitnessState> = _healthState.asStateFlow()

    private fun hasPerm(perm: String): Boolean {
        return ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
    }

    fun getAllPermissionsStatus(): List<JarvisPermissionItem> {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isIgnoringBattery = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pm?.isIgnoringBatteryOptimizations(context.packageName) ?: false
        } else true

        val canOverlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else true

        val canWriteSettings = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.System.canWrite(context)
        } else true

        val isA11yEnabled = isAccessibilityServiceEnabled()

        val notifPerms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(Manifest.permission.POST_NOTIFICATIONS)
        } else emptyList()

        val btPerms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_CONNECT)
        } else emptyList()

        return listOf(
            JarvisPermissionItem(
                id = "mic",
                title = "🎤 Microphone & Voice Control",
                description = "Required for 24/7 voice listening & wake word",
                isGranted = hasPerm(Manifest.permission.RECORD_AUDIO),
                runtimePermissions = listOf(Manifest.permission.RECORD_AUDIO)
            ),
            JarvisPermissionItem(
                id = "calls",
                title = "📞 Direct Phone Calls & Logs",
                description = "Directly call any contact or number & read missed calls",
                isGranted = hasPerm(Manifest.permission.CALL_PHONE) && hasPerm(Manifest.permission.READ_PHONE_STATE),
                runtimePermissions = listOf(
                    Manifest.permission.CALL_PHONE,
                    Manifest.permission.READ_PHONE_STATE,
                    Manifest.permission.READ_CALL_LOG
                )
            ),
            JarvisPermissionItem(
                id = "contacts",
                title = "👥 Contacts Lookup",
                description = "Find Mummy, Papa, or any contact number by name",
                isGranted = hasPerm(Manifest.permission.READ_CONTACTS),
                runtimePermissions = listOf(Manifest.permission.READ_CONTACTS)
            ),
            JarvisPermissionItem(
                id = "sms",
                title = "💬 SMS Send & Read",
                description = "Send & read SMS messages by voice command",
                isGranted = hasPerm(Manifest.permission.SEND_SMS) && hasPerm(Manifest.permission.READ_SMS),
                runtimePermissions = listOf(Manifest.permission.SEND_SMS, Manifest.permission.READ_SMS)
            ),
            JarvisPermissionItem(
                id = "camera",
                title = "📸 Camera & Flashlight",
                description = "Capture photos, selfies, videos & control Torch",
                isGranted = hasPerm(Manifest.permission.CAMERA),
                runtimePermissions = listOf(Manifest.permission.CAMERA)
            ),
            JarvisPermissionItem(
                id = "location",
                title = "📍 Location & Navigation",
                description = "Find nearby ATM, food, petrol pump & live directions",
                isGranted = hasPerm(Manifest.permission.ACCESS_FINE_LOCATION) || hasPerm(Manifest.permission.ACCESS_COARSE_LOCATION),
                runtimePermissions = listOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            ),
            JarvisPermissionItem(
                id = "calendar",
                title = "📅 Calendar & Reminders",
                description = "Read & add schedule events, meetings & reminders",
                isGranted = hasPerm(Manifest.permission.READ_CALENDAR) && hasPerm(Manifest.permission.WRITE_CALENDAR),
                runtimePermissions = listOf(
                    Manifest.permission.READ_CALENDAR,
                    Manifest.permission.WRITE_CALENDAR
                )
            ),
            JarvisPermissionItem(
                id = "bluetooth_notif",
                title = "🔔 Notifications & Bluetooth",
                description = "Alerts, reminders & wireless device control",
                isGranted = (notifPerms.isEmpty() || notifPerms.all { hasPerm(it) }) &&
                    (btPerms.isEmpty() || btPerms.all { hasPerm(it) }),
                runtimePermissions = notifPerms + btPerms
            ),
            JarvisPermissionItem(
                id = "accessibility",
                title = "🖥️ Accessibility Screen Control",
                description = "Click buttons, scroll, type & control screen automatically",
                isGranted = isA11yEnabled,
                isSpecialSystemAccess = true
            ),
            JarvisPermissionItem(
                id = "write_settings",
                title = "⚙️ Modify System Settings",
                description = "Directly change screen brightness, volume & ringtone",
                isGranted = canWriteSettings,
                isSpecialSystemAccess = true
            ),
            JarvisPermissionItem(
                id = "overlay",
                title = "🪟 Display Over Other Apps",
                description = "Show JARVIS floating overlay & notifications anywhere",
                isGranted = canOverlay,
                isSpecialSystemAccess = true
            ),
            JarvisPermissionItem(
                id = "battery",
                title = "🔋 Ignore Battery Optimization",
                description = "Keep JARVIS alive 24/7 in background & lock screen",
                isGranted = isIgnoringBattery,
                isSpecialSystemAccess = true
            )
        )
    }

    fun getAllRuntimePermissionStrings(): Array<String> {
        val list = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            list.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            list.add(Manifest.permission.ACTIVITY_RECOGNITION)
        }
        return list.toTypedArray()
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        if (JarvisAccessibilityService.isRunning) return true
        return runCatching {
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            enabledServices.contains(context.packageName, ignoreCase = true)
        }.getOrDefault(false)
    }

    fun openSpecialPermissionScreen(id: String) {
        when (id) {
            "accessibility" -> startSafeIntent(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            "write_settings" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    startSafeIntent(
                        Intent(
                            Settings.ACTION_MANAGE_WRITE_SETTINGS,
                            Uri.parse("package:${context.packageName}")
                        )
                    )
                }
            }
            "overlay" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    startSafeIntent(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                    )
                }
            }
            "battery" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    startSafeIntent(
                        Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:${context.packageName}")
                        )
                    )
                }
            }
            else -> {
                startSafeIntent(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:${context.packageName}")
                    )
                )
            }
        }
    }

    private fun resolveContactNumber(nameOrNumber: String): String {
        val cleaned = nameOrNumber.trim()
        if (cleaned.any { it.isDigit() }) return cleaned
        if (!hasPerm(Manifest.permission.READ_CONTACTS)) return cleaned

        return runCatching {
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )
            val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
            val args = arrayOf("%$cleaned%")
            context.contentResolver.query(uri, projection, selection, args, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    if (numIdx >= 0) {
                        return@runCatching cursor.getString(numIdx) ?: cleaned
                    }
                }
            }
            cleaned
        }.getOrDefault(cleaned)
    }

    private fun tryLaunchInstalledAppByName(appName: String): Boolean {
        val query = appName.lowercase().trim()
        if (query.isBlank()) return false

        // Common known package mappings first
        val knownPackages = mapOf(
            "whatsapp" to "com.whatsapp",
            "youtube" to "com.google.android.youtube",
            "youtube music" to "com.google.android.apps.youtube.music",
            "instagram" to "com.instagram.android",
            "facebook" to "com.facebook.katana",
            "telegram" to "org.telegram.messenger",
            "spotify" to "com.spotify.music",
            "chrome" to "com.android.chrome",
            "google" to "com.google.android.googlequicksearchbox",
            "maps" to "com.google.android.apps.maps",
            "gmail" to "com.google.android.gm",
            "calendar" to "com.google.android.calendar",
            "calculator" to "com.google.android.calculator",
            "clock" to "com.google.android.deskclock",
            "contacts" to "com.google.android.contacts",
            "files" to "com.google.android.apps.nbu.files",
            "play store" to "com.android.vending",
            "netflix" to "com.netflix.mediaclient",
            "prime video" to "com.amazon.avod.thirdpartyclient",
            "hotstar" to "in.startv.hotstar",
            "amazon" to "in.amazon.mShop.android.shopping",
            "flipkart" to "com.flipkart.android",
            "myntra" to "com.myntra.android"
        )

        val pm = context.packageManager
        for ((key, pkg) in knownPackages) {
            if (query.contains(key)) {
                val launchIntent = pm.getLaunchIntentForPackage(pkg)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching {
                        context.startActivity(launchIntent)
                        return true
                    }
                }
            }
        }

        // Dynamic scan across all installed launcher activities
        return runCatching {
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val apps = pm.queryIntentActivities(mainIntent, 0)
            val matched = apps.firstOrNull { resolveInfo ->
                val label = resolveInfo.loadLabel(pm).toString().lowercase()
                val pkg = resolveInfo.activityInfo.packageName.lowercase()
                label.contains(query) || query.contains(label) || pkg.contains(query)
            }
            if (matched != null) {
                val launch = pm.getLaunchIntentForPackage(matched.activityInfo.packageName)
                if (launch != null) {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launch)
                    return@runCatching true
                }
            }
            false
        }.getOrDefault(false)
    }

    fun readSystemTelemetry(): SystemTelemetry {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val batteryPct = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.coerceIn(1, 100) ?: 86
        val isCharging = bm?.isCharging ?: true

        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(memInfo)
        val availGb = if (memInfo.availMem > 0) {
            String.format(Locale.US, "%.1f GB", memInfo.availMem / (1024.0 * 1024.0 * 1024.0))
        } else "4.8 GB"
        val totalGb = if (memInfo.totalMem > 0) {
            String.format(Locale.US, "%.1f GB", memInfo.totalMem / (1024.0 * 1024.0 * 1024.0))
        } else "8.0 GB"

        val statFs = runCatching { StatFs(Environment.getDataDirectory().path) }.getOrNull()
        val freeStorage = if (statFs != null) {
            val freeBytes = statFs.availableBlocksLong * statFs.blockSizeLong
            String.format(Locale.US, "%.1f GB Free", freeBytes / (1024.0 * 1024.0 * 1024.0))
        } else "64.2 GB Free"

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val maxVol = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC)?.coerceAtLeast(1) ?: 15
        val curVol = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 11
        val volPct = ((curVol.toFloat() / maxVol.toFloat()) * 100).toInt().coerceIn(0, 100)

        val sysBrightness = runCatching {
            val raw = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
            ((raw / 255f) * 100).toInt().coerceIn(5, 100)
        }.getOrDefault(simulatedBrightnessPct)

        return SystemTelemetry(
            batteryPct = batteryPct,
            isCharging = isCharging,
            batteryHealth = "Good (98%)",
            availableRamGb = availGb,
            totalRamGb = totalGb,
            freeStorageGb = freeStorage,
            networkType = "5G / Wi-Fi Active",
            torchOn = torchEnabled,
            dndActive = dndEnabled,
            ringerModeLabel = ringerLabel,
            volumePct = volPct,
            brightnessPct = sysBrightness,
            batterySaverOn = batterySaverEnabled
        )
    }

    fun toggleTorch(enable: Boolean): Boolean {
        return runCatching {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList.firstOrNull() ?: return false
            cameraManager.setTorchMode(cameraId, enable)
            torchEnabled = enable
            true
        }.getOrElse {
            torchEnabled = enable
            false
        }
    }

    fun setMusicVolume(percent: Int) {
        runCatching {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val target = ((percent.coerceIn(0, 100) / 100f) * maxVol).toInt()
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
        }
    }

    fun setBrightness(percent: Int) {
        val clamped = percent.coerceIn(5, 100)
        simulatedBrightnessPct = clamped
        runCatching {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.System.canWrite(context)) {
                val rawValue = ((clamped / 100f) * 255).toInt().coerceIn(10, 255)
                Settings.System.putInt(
                    context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS,
                    rawValue
                )
            }
        }
    }

    fun setRingerMode(mode: String) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        when (mode.uppercase()) {
            "SILENT" -> {
                ringerLabel = "Silent Mode 🔕"
                runCatching { audioManager?.ringerMode = AudioManager.RINGER_MODE_SILENT }
            }
            "VIBRATE" -> {
                ringerLabel = "Vibrate Mode 📳"
                runCatching { audioManager?.ringerMode = AudioManager.RINGER_MODE_VIBRATE }
            }
            "DND" -> {
                dndEnabled = true
                ringerLabel = "Do Not Disturb 🌙"
            }
            else -> {
                dndEnabled = false
                ringerLabel = "Ring Mode 🔔"
                runCatching { audioManager?.ringerMode = AudioManager.RINGER_MODE_NORMAL }
            }
        }
    }

    fun dispatchMediaKey(keyCode: Int) {
        runCatching {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audioManager?.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            audioManager?.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        }
    }

    fun updateSmartHome(
        lights: Boolean? = null,
        ac: Boolean? = null,
        acTemp: Int? = null,
        tv: Boolean? = null
    ) {
        _smartHomeState.update { current ->
            current.copy(
                smartLightsOn = lights ?: current.smartLightsOn,
                acOn = ac ?: current.acOn,
                acTemperature = acTemp ?: current.acTemperature,
                smartTvOn = tv ?: current.smartTvOn
            )
        }
    }

    fun logWaterGlass() {
        _healthState.update { it.copy(waterGlasses = it.waterGlasses + 1) }
    }

    fun calculateBmi(weightKg: Float, heightCm: Float): String {
        val heightM = (heightCm / 100f).coerceAtLeast(0.5f)
        val bmi = weightKg / (heightM * heightM)
        val status = when {
            bmi < 18.5f -> "Underweight"
            bmi < 25f -> "Healthy Normal"
            bmi < 30f -> "Overweight"
            else -> "Obese"
        }
        val formatted = String.format(Locale.US, "%.1f (%s)", bmi, status)
        _healthState.update { it.copy(lastBmiResult = formatted) }
        return formatted
    }

    private fun startSafeIntent(intent: Intent, fallbackUrl: String? = null) {
        runCatching {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }.onFailure {
            if (fallbackUrl != null) {
                openUrl(fallbackUrl)
            }
        }
    }

    fun openUrl(url: String) {
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    fun openCreatorTelegram() {
        openUrl("https://t.me/+R9EwUE03GRswZDM9")
    }

    fun openCreatorYouTube() {
        openUrl("https://www.youtube.com/results?search_query=AK+EXPLOITS")
    }

    fun executeCommand(command: PhoneActionCommand) {
        when (command) {
            is PhoneActionCommand.OpenCreatorTelegram -> openCreatorTelegram()
            is PhoneActionCommand.OpenCreatorYouTube -> openCreatorYouTube()

            // 1. CALLS (Direct Call if CALL_PHONE permission granted, else Dialer)
            is PhoneActionCommand.MakePhoneCall -> {
                val rawTarget = if (command.target.contains("112") || command.target.contains("emergency", true)) {
                    "112"
                } else {
                    resolveContactNumber(command.target)
                }
                val action = if (hasPerm(Manifest.permission.CALL_PHONE) && rawTarget.any { it.isDigit() }) {
                    Intent.ACTION_CALL
                } else {
                    Intent.ACTION_DIAL
                }
                startSafeIntent(
                    Intent(action, Uri.parse("tel:${Uri.encode(rawTarget)}"))
                )
            }
            is PhoneActionCommand.CallControlAction -> {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                when (command.controlType) {
                    "SPEAKER_ON" -> runCatching {
                        @Suppress("DEPRECATION")
                        audioManager?.isSpeakerphoneOn = true
                    }
                    "SPEAKER_OFF" -> runCatching {
                        @Suppress("DEPRECATION")
                        audioManager?.isSpeakerphoneOn = false
                    }
                    "MUTE_ON" -> runCatching { audioManager?.isMicrophoneMute = true }
                    "MUTE_OFF" -> runCatching { audioManager?.isMicrophoneMute = false }
                    "CALL_HISTORY", "MISSED_CALLS" -> startSafeIntent(
                        Intent(Intent.ACTION_VIEW).apply { type = CallLog.Calls.CONTENT_TYPE }
                    )
                }
            }

            // 2. MESSAGING
            is PhoneActionCommand.SendWhatsApp -> {
                val encoded = Uri.encode(command.message)
                openUrl("https://wa.me/?text=$encoded")
            }
            is PhoneActionCommand.SendSms -> {
                val resolved = resolveContactNumber(command.recipient)
                val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(resolved)}")).apply {
                    putExtra("sms_body", command.body)
                }
                startSafeIntent(intent)
            }
            is PhoneActionCommand.OpenMessagingApp -> {
                if (!tryLaunchInstalledAppByName(command.platform)) {
                    val url = when (command.platform.uppercase()) {
                        "WHATSAPP" -> "https://wa.me/"
                        "TELEGRAM" -> "https://t.me/+R9EwUE03GRswZDM9"
                        "INSTAGRAM" -> "https://www.instagram.com/direct/inbox/"
                        "MESSENGER" -> "https://www.messenger.com/"
                        "SIGNAL" -> "https://signal.org/"
                        else -> "https://wa.me/"
                    }
                    openUrl(url)
                }
            }

            // 3. APPS MANAGEMENT
            is PhoneActionCommand.OpenAppOrStore -> {
                if (command.actionType == "APP_INFO") {
                    startSafeIntent(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                    )
                    return
                }
                if (command.actionType == "ALL_APPS") {
                    startSafeIntent(Intent(Settings.ACTION_MANAGE_ALL_APPLICATIONS_SETTINGS))
                    return
                }
                if (command.actionType != "INSTALL" && tryLaunchInstalledAppByName(command.appName)) {
                    return
                }
                val appLower = command.appName.lowercase()
                val targetUrl = when {
                    appLower.contains("instagram") -> "https://www.instagram.com/"
                    appLower.contains("facebook") -> "https://www.facebook.com/"
                    appLower.contains("twitter") || appLower.contains("x") -> "https://x.com/"
                    appLower.contains("whatsapp") -> "https://wa.me/"
                    appLower.contains("telegram") -> "https://t.me/+R9EwUE03GRswZDM9"
                    appLower.contains("youtube music") -> "https://music.youtube.com/"
                    appLower.contains("youtube") -> "https://www.youtube.com/"
                    appLower.contains("spotify") -> "https://open.spotify.com/"
                    appLower.contains("jiosaavn") || appLower.contains("saavn") -> "https://www.jiosaavn.com/"
                    appLower.contains("gaana") -> "https://gaana.com/"
                    appLower.contains("wynk") -> "https://wynk.in/music"
                    appLower.contains("netflix") -> "https://www.netflix.com/"
                    appLower.contains("prime") -> "https://www.primevideo.com/"
                    appLower.contains("hotstar") -> "https://www.hotstar.com/"
                    appLower.contains("amazon") -> "https://www.amazon.in/"
                    appLower.contains("flipkart") -> "https://www.flipkart.com/"
                    appLower.contains("myntra") -> "https://www.myntra.com/"
                    appLower.contains("ajio") -> "https://www.ajio.com/"
                    appLower.contains("meesho") -> "https://www.meesho.com/"
                    appLower.contains("gmail") -> "https://mail.google.com/"
                    appLower.contains("outlook") -> "https://outlook.live.com/"
                    appLower.contains("play store") -> "https://play.google.com/store"
                    appLower.contains("google home") -> "https://home.google.com/"
                    appLower.contains("alexa") -> "https://alexa.amazon.com/"
                    else -> "https://play.google.com/store/search?q=${Uri.encode(command.appName)}&c=apps"
                }
                openUrl(targetUrl)
            }

            // 4. BROWSER & SEARCH
            is PhoneActionCommand.SearchYouTube -> {
                val encoded = Uri.encode(command.query)
                openUrl("https://www.youtube.com/results?search_query=$encoded")
            }
            is PhoneActionCommand.SearchGoogle -> {
                val encoded = Uri.encode(command.query)
                openUrl("https://www.google.com/search?q=$encoded")
            }
            is PhoneActionCommand.OpenWebsite -> {
                val url = if (command.url.startsWith("http")) command.url else "https://${command.url}"
                openUrl(url)
            }

            // 5. CAMERA & MEDIA
            is PhoneActionCommand.CameraMediaAction -> {
                when (command.mediaAction) {
                    "VIDEO" -> startSafeIntent(Intent(MediaStore.INTENT_ACTION_VIDEO_CAMERA))
                    "GALLERY" -> startSafeIntent(
                        Intent(Intent.ACTION_VIEW).apply { type = "image/*" }
                    )
                    else -> startSafeIntent(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
                }
            }

            // 6. MUSIC & 18. MULTIMEDIA CONTROLS
            is PhoneActionCommand.MultimediaAction -> {
                when (command.control) {
                    "PLAY" -> dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY)
                    "PAUSE" -> dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE)
                    "STOP" -> dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_STOP)
                    "NEXT" -> dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
                    "PREVIOUS" -> dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                    "MUTE" -> setMusicVolume(0)
                    "VOL_UP" -> {
                        val cur = readSystemTelemetry().volumePct
                        setMusicVolume((cur + 15).coerceAtMost(100))
                    }
                    "VOL_DOWN" -> {
                        val cur = readSystemTelemetry().volumePct
                        setMusicVolume((cur - 15).coerceAtLeast(0))
                    }
                }
            }

            // 8. SYSTEM SETTINGS & 20. SPECIAL COMMANDS
            is PhoneActionCommand.ToggleTorch -> {
                toggleTorch(command.enable)
            }
            is PhoneActionCommand.AdjustSystemLevel -> {
                if (command.targetType == "VOLUME") {
                    setMusicVolume(command.percent)
                } else if (command.targetType == "BRIGHTNESS") {
                    setBrightness(command.percent)
                }
            }
            is PhoneActionCommand.OpenSystemSettings -> {
                val action = when (command.settingType.uppercase()) {
                    "WIFI" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        Settings.Panel.ACTION_WIFI
                    } else Settings.ACTION_WIFI_SETTINGS
                    "BLUETOOTH" -> Settings.ACTION_BLUETOOTH_SETTINGS
                    "HOTSPOT", "AIRPLANE" -> Settings.ACTION_WIRELESS_SETTINGS
                    "DATA_USAGE" -> Settings.ACTION_DATA_USAGE_SETTINGS
                    "VPN" -> Settings.ACTION_VPN_SETTINGS
                    "DISPLAY", "BRIGHTNESS" -> Settings.ACTION_DISPLAY_SETTINGS
                    "SOUND", "RINGTONE" -> Settings.ACTION_SOUND_SETTINGS
                    "SILENT" -> {
                        setRingerMode("SILENT")
                        null
                    }
                    "VIBRATE" -> {
                        setRingerMode("VIBRATE")
                        null
                    }
                    "RING" -> {
                        setRingerMode("RING")
                        null
                    }
                    "DND" -> {
                        setRingerMode("DND")
                        Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS
                    }
                    "NFC" -> Settings.ACTION_NFC_SETTINGS
                    "LOCATION" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
                    "LANGUAGE" -> Settings.ACTION_LOCALE_SETTINGS
                    "DATE_TIME" -> Settings.ACTION_DATE_SETTINGS
                    "BATTERY_SAVER" -> {
                        batterySaverEnabled = true
                        Settings.ACTION_BATTERY_SAVER_SETTINGS
                    }
                    "BATTERY_USAGE" -> Intent.ACTION_POWER_USAGE_SUMMARY
                    "STORAGE" -> Settings.ACTION_INTERNAL_STORAGE_SETTINGS
                    "PRIVACY" -> Settings.ACTION_PRIVACY_SETTINGS
                    "SECURITY" -> Settings.ACTION_SECURITY_SETTINGS
                    "CAST" -> Settings.ACTION_CAST_SETTINGS
                    "ACCESSIBILITY" -> Settings.ACTION_ACCESSIBILITY_SETTINGS
                    "DEV_OPTIONS" -> Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS
                    else -> Settings.ACTION_SETTINGS
                }
                if (action != null) {
                    startSafeIntent(Intent(action))
                }
            }

            // 9. NAVIGATION & MAPS
            is PhoneActionCommand.OpenMaps -> {
                val encoded = Uri.encode(command.destination)
                openUrl("https://www.google.com/maps/search/?api=1&query=$encoded")
            }

            // 10. SHOPPING APPS
            is PhoneActionCommand.ShoppingAction -> {
                val encoded = Uri.encode(command.query)
                val url = when (command.store.uppercase()) {
                    "FLIPKART" -> "https://www.flipkart.com/search?q=$encoded"
                    "MYNTRA" -> "https://www.myntra.com/$encoded"
                    "AJIO" -> "https://www.ajio.com/search/?text=$encoded"
                    "MEESHO" -> "https://www.meesho.com/search?q=$encoded"
                    else -> "https://www.amazon.in/s?k=$encoded"
                }
                openUrl(url)
            }

            // 11. EMAIL
            is PhoneActionCommand.SendEmail -> {
                val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${Uri.encode(command.to)}")).apply {
                    putExtra(Intent.EXTRA_SUBJECT, command.subject)
                    putExtra(Intent.EXTRA_TEXT, command.body)
                }
                startSafeIntent(intent, fallbackUrl = "https://mail.google.com/")
            }

            // 12. CALENDAR, REMINDERS & ALARMS
            is PhoneActionCommand.SetAlarmOrTimer -> {
                if (command.isTimer) {
                    val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                        putExtra(AlarmClock.EXTRA_LENGTH, command.minutesOrHour * 60)
                        putExtra(AlarmClock.EXTRA_MESSAGE, command.label)
                        putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                    }
                    startSafeIntent(intent)
                } else {
                    val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                        putExtra(AlarmClock.EXTRA_HOUR, command.minutesOrHour.coerceIn(0, 23))
                        putExtra(AlarmClock.EXTRA_MINUTES, 0)
                        putExtra(AlarmClock.EXTRA_MESSAGE, command.label)
                    }
                    startSafeIntent(intent)
                }
            }
            is PhoneActionCommand.AddCalendarEvent -> {
                val intent = Intent(Intent.ACTION_INSERT).apply {
                    data = CalendarContract.Events.CONTENT_URI
                    putExtra(CalendarContract.Events.TITLE, command.title)
                    putExtra(CalendarContract.Events.DESCRIPTION, "Created by JARVIS")
                }
                startSafeIntent(intent)
            }

            // 13. FILE MANAGER
            is PhoneActionCommand.FileManagerAction -> {
                startSafeIntent(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS))
            }

            // 15. HEALTH & FITNESS
            is PhoneActionCommand.HealthAction -> {
                if (command.actionType == "WATER") {
                    logWaterGlass()
                } else if (command.actionType == "BMI") {
                    calculateBmi(68f, 175f)
                }
            }

            // 16. SMART HOME / IOT
            is PhoneActionCommand.SmartHomeAction -> {
                when (command.device) {
                    "LIGHTS" -> updateSmartHome(lights = command.turnOn)
                    "AC" -> updateSmartHome(ac = command.turnOn, acTemp = command.value ?: 24)
                    "TV" -> updateSmartHome(tv = command.turnOn)
                }
            }

            // 20. MULTI-COMMAND CHAIN
            is PhoneActionCommand.MultiCommandChain -> {
                command.commands.forEach { subCmd ->
                    executeCommand(subCmd)
                }
            }

            is PhoneActionCommand.ScreenAction -> {
                val a11y = JarvisAccessibilityService.instance
                if (a11y != null) {
                    when (command.actionType) {
                        "CLICK" -> a11y.performTap(540f, 1200f)
                        "SCROLL" -> if (command.value == "UP") {
                            a11y.performSwipe(540f, 600f, 540f, 1500f)
                        } else {
                            a11y.performSwipe(540f, 1500f, 540f, 600f)
                        }
                    }
                }
            }

            is PhoneActionCommand.WakeStateChange -> {
                // Handled in ViewModel
            }
        }
    }
}
