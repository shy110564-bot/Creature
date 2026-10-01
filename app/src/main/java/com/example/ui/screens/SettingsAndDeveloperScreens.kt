package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.BuildConfig
import com.example.data.local.MemoryFactEntity
import com.example.data.model.OrbVisualState
import com.example.data.model.VoiceSettings
import com.example.data.model.WakeState
import com.example.ui.components.GlassCard
import com.example.ui.components.JarvisOrb
import com.example.ui.components.NeonButton
import com.example.ui.components.NeonIconButton
import com.example.ui.components.NeonSliderRow
import com.example.ui.components.NeonToggleRow
import com.example.ui.components.RgbNeonDivider
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.ElevatedGlass
import com.example.ui.theme.GlassBorderCyan
import com.example.ui.theme.GlassBorderLight
import com.example.ui.theme.GlassBorderPink
import com.example.ui.theme.HotPink
import com.example.ui.theme.JarvisGradients
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.NeonRed
import com.example.ui.theme.SoraFontFamily
import com.example.ui.theme.SurfaceAlt
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.util.PhoneControlExecutor
import java.util.Locale

// SIMPLE TOP-RIGHT SETTINGS: GEMINI API KEY + BACKGROUND RGB LIGHT + ALL PERMISSIONS
@Composable
fun SettingsScreen(
    customApiKey: String = "",
    onSaveCustomApiKey: (String) -> Unit = {},
    isBackgroundRgbActive: Boolean = false,
    onToggleBackgroundRgb: () -> Unit = {},
    wakeState: WakeState,
    isForegroundServiceRunning: Boolean,
    memories: List<MemoryFactEntity> = emptyList(),
    phoneControl: PhoneControlExecutor,
    onChangeWakeState: (WakeState) -> Unit = {},
    onToggleForegroundService: () -> Unit,
    onAddMemory: (String, String, String) -> Unit = { _, _, _ -> },
    onDeleteMemory: (Long) -> Unit = {},
    onOpenVoiceSettings: () -> Unit = {},
    onOpenDeveloperAbout: () -> Unit = {},
    onOpenLockWidget: () -> Unit = {},
    onTestVoice: () -> Unit = {},
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    var apiKeyInput by remember(customApiKey) { mutableStateOf(customApiKey) }
    var keySavedFeedback by remember { mutableStateOf(false) }
    var permRefreshTick by remember { mutableIntStateOf(0) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permRefreshTick++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permissionsList = remember(permRefreshTick) {
        phoneControl.getAllPermissionsStatus()
    }

    val multiplePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        permRefreshTick++
    }

    val hasBuiltInKey = BuildConfig.GEMINI_API_KEY.isNotBlank() &&
        BuildConfig.GEMINI_API_KEY != "MY_GEMINI_API_KEY"
    val isKeyActive = customApiKey.isNotBlank() || hasBuiltInKey

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag("settings_screen")
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NeonIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back to Chat",
                onClick = onBack,
                testTag = "settings_back_btn"
            )
            Text(
                text = "⚙️ PERMISSIONS & API KEY",
                style = MaterialTheme.typography.titleMedium,
                color = NeonCyan,
                fontWeight = FontWeight.Bold
            )
            NeonIconButton(
                icon = Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = "Test Voice",
                onClick = onTestVoice,
                tint = NeonGreen,
                testTag = "settings_test_voice_btn"
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        RgbNeonDivider()
        Spacer(modifier = Modifier.height(12.dp))

        // 1. 🔑 GEMINI API KEY SECTION
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            borderColor = NeonCyan
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = "Gemini API Key",
                        tint = NeonCyan,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "🔑 Gemini API Key",
                        style = MaterialTheme.typography.titleMedium,
                        color = NeonCyan,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = if (isKeyActive) "Active ✅" else "Add Key ⚡",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isKeyActive) NeonGreen else HotPink,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = apiKeyInput,
                onValueChange = {
                    apiKeyInput = it
                    keySavedFeedback = false
                },
                placeholder = {
                    Text(
                        text = "Yahan apni Gemini API Key paste karein (AIzaSy…)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonCyan,
                    unfocusedBorderColor = GlassBorderLight,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedContainerColor = SurfaceAlt,
                    unfocusedContainerColor = SurfaceAlt
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("gemini_api_key_input")
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NeonButton(
                    text = if (keySavedFeedback) "Saved ✅" else "Save API Key ✅",
                    onClick = {
                        onSaveCustomApiKey(apiKeyInput)
                        keySavedFeedback = true
                    },
                    accentColor = NeonGreen,
                    secondaryColor = NeonCyan,
                    modifier = Modifier.weight(1f),
                    testTag = "save_api_key_btn"
                )
                if (apiKeyInput.isNotBlank()) {
                    NeonButton(
                        text = "Clear",
                        onClick = {
                            apiKeyInput = ""
                            onSaveCustomApiKey("")
                            keySavedFeedback = false
                        },
                        accentColor = HotPink,
                        testTag = "clear_api_key_btn"
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 2. 🌈 BACKGROUND RGB LIGHT PERMISSION & ALWAYS-ON SYSTEM
        val isOverlayGranted = permissionsList.find { it.id == "overlay" }?.isGranted == true
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            borderColor = HotPink
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🌈 Background RGB Light System",
                    style = MaterialTheme.typography.titleMedium,
                    color = HotPink,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isBackgroundRgbActive) "RUNNING 24/7 🌈" else if (isOverlayGranted) "Permission Ready ✅" else "Needs Permission ⚡",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isBackgroundRgbActive || isOverlayGranted) NeonGreen else AmberWarning,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Isko ON karne ke baad phone ke charo corners aur border par RGB Light YouTube, WhatsApp aur Home Screen ke upar background mein lagatar chalti rahegi jab tak aap OFF na karein.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(10.dp))

            if (!isOverlayGranted) {
                NeonButton(
                    text = "1️⃣ Allow RGB Background Permission (Overlay)",
                    onClick = { phoneControl.openSpecialPermissionScreen("overlay") },
                    accentColor = AmberWarning,
                    secondaryColor = HotPink,
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "allow_rgb_overlay_perm_btn"
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            NeonButton(
                text = if (isBackgroundRgbActive) "⏹ Turn OFF Background RGB Light"
                else "🌈 Turn ON Background RGB Light (Always Work)",
                onClick = onToggleBackgroundRgb,
                accentColor = if (isBackgroundRgbActive) NeonRed else NeonGreen,
                secondaryColor = NeonCyan,
                modifier = Modifier.fillMaxWidth(),
                testTag = "toggle_bg_rgb_light_btn"
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 3. 🛡️ ALL ASSISTANT & SCREEN PERMISSIONS SECTION
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            borderColor = NeonGreen
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = "All Permissions",
                        tint = NeonGreen,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "🛡️ All Assistant Permissions",
                        style = MaterialTheme.typography.titleMedium,
                        color = NeonGreen,
                        fontWeight = FontWeight.Bold
                    )
                }
                val grantedCount = permissionsList.count { it.isGranted }
                Text(
                    text = "$grantedCount/${permissionsList.size} Allowed",
                    style = MaterialTheme.typography.labelSmall,
                    color = NeonCyan
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            NeonButton(
                text = "⚡ Allow All Permissions (Ek Tap Mein Sab On)",
                icon = Icons.Default.CheckCircle,
                onClick = {
                    multiplePermissionLauncher.launch(phoneControl.getAllRuntimePermissionStrings())
                },
                accentColor = NeonGreen,
                secondaryColor = NeonCyan,
                modifier = Modifier.fillMaxWidth(),
                testTag = "grant_all_permissions_btn"
            )

            Spacer(modifier = Modifier.height(10.dp))

            permissionsList.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(SurfaceAlt)
                        .border(
                            width = 1.dp,
                            color = if (item.isGranted) NeonGreen.copy(alpha = 0.55f) else AmberWarning.copy(alpha = 0.65f),
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clickable {
                            if (item.isSpecialSystemAccess) {
                                phoneControl.openSpecialPermissionScreen(item.id)
                            } else if (item.runtimePermissions.isNotEmpty()) {
                                multiplePermissionLauncher.launch(item.runtimePermissions.toTypedArray())
                            }
                        }
                        .padding(12.dp)
                        .testTag("perm_row_${item.id}"),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.labelLarge,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = item.description,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(
                                if (item.isGranted) NeonGreen.copy(alpha = 0.20f)
                                else HotPink.copy(alpha = 0.24f)
                            )
                            .border(
                                1.dp,
                                if (item.isGranted) NeonGreen else HotPink,
                                RoundedCornerShape(999.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (item.isGranted) "Allowed ✅" else "Allow ⚡",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (item.isGranted) NeonGreen else HotPink,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 3. ⚡ 24/7 BACKGROUND VOICE SERVICE & CREATOR
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            borderColor = GlassBorderPink
        ) {
            NeonToggleRow(
                title = "⚡ 24/7 Background Voice Active",
                subtitle = "Phone lock hone par bhi JARVIS aapki awaaz sunegi",
                checked = isForegroundServiceRunning,
                accentColor = NeonGreen,
                onCheckedChange = { onToggleForegroundService() }
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NeonButton(
                    text = "📢 Telegram",
                    icon = Icons.Default.Send,
                    onClick = { phoneControl.openCreatorTelegram() },
                    accentColor = NeonCyan,
                    modifier = Modifier.weight(1f),
                    testTag = "open_telegram_btn"
                )
                NeonButton(
                    text = "▶️ AK EXPLOITS",
                    icon = Icons.Default.PlayCircleFilled,
                    onClick = { phoneControl.openCreatorYouTube() },
                    accentColor = HotPink,
                    modifier = Modifier.weight(1f),
                    testTag = "open_youtube_btn"
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun VoiceSettingsScreen(
    settings: VoiceSettings,
    onUpdateSettings: (VoiceSettings) -> Unit,
    onTestVoice: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        NeonSliderRow(
            label = "Speed",
            valueText = "${(settings.speed * 100).toInt()}%",
            value = settings.speed,
            valueRange = 0.65f..1.25f,
            accentColor = NeonCyan,
            onValueChange = { onUpdateSettings(settings.copy(speed = it)) }
        )
        NeonSliderRow(
            label = "Pitch",
            valueText = String.format(Locale.US, "%.2fx", settings.pitch),
            value = settings.pitch,
            valueRange = 0.80f..1.50f,
            accentColor = HotPink,
            onValueChange = { onUpdateSettings(settings.copy(pitch = it)) }
        )
        NeonButton(
            text = "🔊 Test Voice",
            onClick = onTestVoice,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun DeveloperAboutScreen(
    onOpenTelegram: () -> Unit,
    onOpenYouTube: () -> Unit,
    onSpeakCreatorTribute: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        JarvisOrb(
            orbSize = 120.dp,
            visualState = OrbVisualState.SPEAKING_WAVE,
            moodColor = HotPink,
            showHologramAvatar = true,
            onClick = onSpeakCreatorTribute
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Crafted by AK EXPLOITS",
            style = TextStyle(
                fontFamily = SoraFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                brush = JarvisGradients.PrimaryNeon
            ),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        NeonButton(text = "Open Telegram", onClick = onOpenTelegram, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        NeonButton(text = "Open YouTube", onClick = onOpenYouTube, modifier = Modifier.fillMaxWidth())
    }
}
