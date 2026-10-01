package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.StopScreenShare
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.ChatMessageEntity
import com.example.data.model.JarvisMood
import com.example.data.model.OrbVisualState
import com.example.data.model.PendingConfirmationAction
import com.example.data.model.ScreenMockState
import com.example.data.model.WakeState
import com.example.ui.components.ChatBubble
import com.example.ui.components.GlassCard
import com.example.ui.components.JarvisOrb
import com.example.ui.components.NeonButton
import com.example.ui.components.NeonIconButton
import com.example.ui.components.RgbNeonDivider
import com.example.ui.components.VoiceWaveform
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

@Composable
fun SplashScreen(
    onFinished: () -> Unit
) {
    LaunchedEffect(Unit) {
        onFinished()
    }
}

// SINGLE ALL-IN-ONE SIMPLE CHAT + ALWAYS-ON BACKGROUND VOICE + LIVE SCREEN SHARE SCREEN
@Composable
fun HomeScreen(
    selectedMood: JarvisMood,
    wakeState: WakeState,
    orbState: OrbVisualState,
    showHologramAvatar: Boolean,
    isListening: Boolean,
    isContinuousMicOn: Boolean = false,
    isSpeaking: Boolean,
    isScreenSharingLive: Boolean = false,
    isBackgroundRgbActive: Boolean = false,
    audioAmplitude: Float,
    liveTranscript: String,
    recentMessages: List<ChatMessageEntity>,
    pendingConfirmation: PendingConfirmationAction? = null,
    screenState: ScreenMockState = ScreenMockState(),
    capturedBitmap: Bitmap? = null,
    onToggleAvatar: () -> Unit = {},
    onStartVoiceListen: () -> Unit,
    onStopVoiceListen: () -> Unit = {},
    onStartLiveScreenShare: (Int, Intent) -> Unit = { _, _ -> },
    onStopLiveScreenShare: () -> Unit = {},
    onToggleBackgroundRgb: () -> Unit = {},
    onQuickPrompt: (String) -> Unit,
    onSpeakText: (String) -> Unit = {},
    onClearChat: () -> Unit = {},
    onConfirmSensitiveAction: () -> Unit = {},
    onCancelSensitiveAction: () -> Unit = {},
    onSetCapturedBitmap: (Bitmap?) -> Unit = {},
    onPerformScreenAction: (String, String) -> Unit = { _, _ -> },
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    var inputText by remember { mutableStateOf("") }
    var showScreenPanel by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onStartVoiceListen()
        } else {
            onOpenSettings()
        }
    }

    // Real Android System Screen Share Launcher (MediaProjectionManager)
    val screenShareLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            onStartLiveScreenShare(result.resultCode, data)
            showScreenPanel = true
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            runCatching {
                val bmp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val source = ImageDecoder.createSource(context.contentResolver, uri)
                    ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                        decoder.isMutableRequired = true
                    }
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
                onSetCapturedBitmap(bmp)
                showScreenPanel = true
            }
        }
    }

    fun requestSystemScreenShare() {
        runCatching {
            val mpm = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
            val captureIntent = mpm?.createScreenCaptureIntent()
            if (captureIntent != null) {
                screenShareLauncher.launch(captureIntent)
            }
        }
    }

    LaunchedEffect(recentMessages.size) {
        if (recentMessages.isNotEmpty()) {
            listState.animateScrollToItem(recentMessages.lastIndex)
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "mic_pulse")
    val micPulse by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (isListening || isContinuousMicOn) 1.15f else 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mic_scale"
    )
    val autoRgbHue by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "home_auto_rgb_hue"
    )
    val rgbColor1 = Color.hsv(autoRgbHue % 360f, 1f, 1f)
    val rgbColor2 = Color.hsv((autoRgbHue + 120f) % 360f, 1f, 1f)
    val rgbColor3 = Color.hsv((autoRgbHue + 240f) % 360f, 1f, 1f)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("home_screen")
    ) {
        // 1. TOP BAR: JARVIS ORB + STATUS ON LEFT, LIVE SCREEN SHARE & SETTINGS IN TOP-RIGHT CORNER
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                JarvisOrb(
                    orbSize = 52.dp,
                    visualState = orbState,
                    moodColor = selectedMood.accentColor,
                    showHologramAvatar = showHologramAvatar,
                    onClick = {
                        if (isContinuousMicOn) {
                            onStopVoiceListen()
                        } else {
                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(rgbColor1)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "JARVIS",
                            style = TextStyle(
                                fontFamily = SoraFontFamily,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 19.sp,
                                brush = Brush.linearGradient(listOf(rgbColor1, rgbColor2, rgbColor3))
                            )
                        )
                    }
                    Text(
                        text = when {
                            isContinuousMicOn -> "🎤 Always-On Background Mic ON"
                            isScreenSharingLive -> "🔴 Live Screen Share ON"
                            isSpeaking -> "🔊 Bol rahi hun ji… 💕"
                            else -> "⚡ Tap 🎤 Once for Continuous Talk"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isContinuousMicOn) NeonGreen else TextSecondary
                    )
                }
            }

            // Top-Right Corner: Background RGB Light, Live Screen Share, Clear Chat, and Settings
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Background RGB Light Pill Toggle
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            if (isBackgroundRgbActive) Brush.linearGradient(listOf(rgbColor1.copy(alpha = 0.32f), rgbColor2.copy(alpha = 0.32f)))
                            else Brush.linearGradient(listOf(SurfaceAlt, SurfaceAlt))
                        )
                        .border(
                            1.5.dp,
                            Brush.linearGradient(listOf(rgbColor1, rgbColor2, rgbColor3)),
                            RoundedCornerShape(999.dp)
                        )
                        .clickable { onToggleBackgroundRgb() }
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                        .testTag("top_rgb_bg_btn"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isBackgroundRgbActive) "🌈 RGB ON" else "🌈 RGB",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isBackgroundRgbActive) NeonGreen else TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Live Screen Share Pill Button
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            if (isScreenSharingLive) NeonRed.copy(alpha = 0.25f) else SurfaceAlt
                        )
                        .border(
                            1.5.dp,
                            if (isScreenSharingLive) NeonRed else NeonCyan,
                            RoundedCornerShape(999.dp)
                        )
                        .clickable {
                            showScreenPanel = true
                            if (!isScreenSharingLive) {
                                requestSystemScreenShare()
                            }
                        }
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                        .testTag("top_screen_share_btn"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isScreenSharingLive) Icons.Default.StopScreenShare else Icons.Default.ScreenShare,
                        contentDescription = "Live Screen Share",
                        tint = if (isScreenSharingLive) NeonRed else NeonCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = if (isScreenSharingLive) "LIVE" else "Screen",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isScreenSharingLive) NeonRed else NeonCyan,
                        fontWeight = FontWeight.Bold
                    )
                }

                NeonIconButton(
                    icon = Icons.Default.DeleteSweep,
                    contentDescription = "Clear Chat",
                    onClick = onClearChat,
                    tint = HotPink,
                    testTag = "clear_chat_btn"
                )

                // Top-Right Corner Settings Button (Permissions + Gemini API Key)
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(SurfaceAlt)
                        .border(1.5.dp, NeonGreen, RoundedCornerShape(999.dp))
                        .clickable { onOpenSettings() }
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                        .testTag("top_settings_btn"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings, Permissions & API Key",
                        tint = NeonGreen,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "Setting",
                        style = MaterialTheme.typography.labelSmall,
                        color = NeonGreen,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        RgbNeonDivider()

        // 2. LIVE SCREEN SHARE & CONTROL PANEL
        AnimatedVisibility(visible = showScreenPanel || isScreenSharingLive) {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                borderColor = if (isScreenSharingLive) NeonRed else NeonCyan,
                contentPadding = PaddingValues(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isScreenSharingLive) "🔴 JARVIS Aapki Live Screen Dekh Rahi Hai"
                        else "🖥️ Screen Share (JARVIS Ko Apni Screen Dikhayein)",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isScreenSharingLive) NeonGreen else NeonCyan,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isScreenSharingLive) "Hide ▲" else "Close ✕",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        modifier = Modifier.clickable { showScreenPanel = false }
                    )
                }

                if (capturedBitmap != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Image(
                        bitmap = capturedBitmap.asImageBitmap(),
                        contentDescription = "Live Shared Screen",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, NeonCyan, RoundedCornerShape(10.dp))
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    NeonButton(
                        text = if (isScreenSharingLive) "⏹ Stop Share" else "🖥️ Start Screen Share",
                        icon = if (isScreenSharingLive) Icons.Default.StopScreenShare else Icons.Default.ScreenShare,
                        onClick = {
                            if (isScreenSharingLive) {
                                onStopLiveScreenShare()
                            } else {
                                requestSystemScreenShare()
                            }
                        },
                        accentColor = if (isScreenSharingLive) NeonRed else NeonGreen,
                        secondaryColor = NeonCyan,
                        modifier = Modifier.weight(1.4f),
                        testTag = "start_live_screen_share_btn"
                    )

                    NeonButton(
                        text = "📸 Photo",
                        icon = Icons.Default.AddPhotoAlternate,
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        accentColor = NeonCyan,
                        modifier = Modifier.weight(1f),
                        testTag = "pick_screenshot_btn"
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    NeonButton(
                        text = "📖 Padho",
                        onClick = { onPerformScreenAction("READ_OCR", "") },
                        accentColor = HotPink,
                        modifier = Modifier.weight(1f),
                        testTag = "vision_ocr_btn"
                    )
                    NeonButton(
                        text = "👆 Click",
                        onClick = { onPerformScreenAction("CLICK", "First Item") },
                        accentColor = NeonGreen,
                        modifier = Modifier.weight(1f),
                        testTag = "vision_click_btn"
                    )
                    NeonButton(
                        text = "🏠 Home",
                        onClick = { onPerformScreenAction("NAV_HOME", "") },
                        accentColor = NeonCyan,
                        modifier = Modifier.weight(1f),
                        testTag = "vision_home_btn"
                    )
                    NeonButton(
                        text = "🔙 Back",
                        onClick = { onPerformScreenAction("NAV_BACK", "") },
                        accentColor = NeonPurple,
                        modifier = Modifier.weight(1f),
                        testTag = "vision_back_btn"
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    NeonButton(
                        text = "⬆️ Upar",
                        onClick = { onPerformScreenAction("SCROLL_UP", "") },
                        accentColor = NeonCyan,
                        modifier = Modifier.weight(1f),
                        testTag = "vision_scroll_up_btn"
                    )
                    NeonButton(
                        text = "⬇️ Neeche",
                        onClick = { onPerformScreenAction("SCROLL_DOWN", "") },
                        accentColor = NeonGreen,
                        modifier = Modifier.weight(1f),
                        testTag = "vision_scroll_down_btn"
                    )
                    NeonButton(
                        text = "⬅️ Left",
                        onClick = { onPerformScreenAction("SCROLL_LEFT", "") },
                        accentColor = HotPink,
                        modifier = Modifier.weight(1f),
                        testTag = "vision_scroll_left_btn"
                    )
                    NeonButton(
                        text = "➡️ Right",
                        onClick = { onPerformScreenAction("SCROLL_RIGHT", "") },
                        accentColor = NeonPurple,
                        modifier = Modifier.weight(1f),
                        testTag = "vision_scroll_right_btn"
                    )
                }
            }
        }

        // 3. SENSITIVE ACTION CONFIRMATION (Only when needed)
        AnimatedVisibility(visible = pendingConfirmation != null) {
            if (pendingConfirmation != null) {
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .testTag("sensitive_confirmation_card"),
                    borderColor = NeonRed,
                    backgroundColor = ElevatedGlass,
                    contentPadding = PaddingValues(10.dp)
                ) {
                    Text(
                        text = "⚠️ ${pendingConfirmation.title}: ${pendingConfirmation.description}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        NeonButton(
                            text = "Haan, Karo ✅",
                            onClick = onConfirmSensitiveAction,
                            accentColor = NeonGreen,
                            modifier = Modifier.weight(1f),
                            testTag = "confirm_sensitive_btn"
                        )
                        NeonButton(
                            text = "Cancel ❌",
                            onClick = onCancelSensitiveAction,
                            accentColor = NeonRed,
                            modifier = Modifier.weight(1f),
                            testTag = "cancel_sensitive_btn"
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 4. QUICK ONE-TAP COMMAND CHIPS
        val quickChips = listOf(
            "⏰ Time batao",
            "📞 Mummy ko call karo",
            "💬 Rahul ko message karo main aa raha hun",
            "🔦 Torch on karo",
            "📅 Calendar kholo",
            "🖥️ Screen padho",
            "🎵 Arijit Singh gaane lagao",
            "📶 WiFi on karo"
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            quickChips.forEachIndexed { idx, chip ->
                val cleanCmd = chip.substringAfter(" ")
                val chipColor = Color.hsv((autoRgbHue + idx * 45f) % 360f, 0.95f, 1f)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(SurfaceAlt)
                        .border(1.2.dp, chipColor.copy(alpha = 0.78f), RoundedCornerShape(999.dp))
                        .clickable { onQuickPrompt(cleanCmd) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .testTag("chat_quick_chip_$idx")
                ) {
                    Text(
                        text = chip,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextPrimary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 5. MAIN CHAT LIST
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 6.dp)
        ) {
            items(recentMessages, key = { it.id }) { msg ->
                ChatBubble(
                    message = msg,
                    onSpeakAgain = onSpeakText
                )
            }
        }

        // 6. BOTTOM CHAT + ALWAYS-ON MIC BAR
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            borderColor = if (isContinuousMicOn || isListening) NeonGreen else GlassBorderPink,
            contentPadding = PaddingValues(10.dp)
        ) {
            if (liveTranscript.isNotBlank() || isContinuousMicOn || isListening) {
                Text(
                    text = if (liveTranscript.isNotBlank()) liveTranscript
                    else "🎤 Always-On Mic Chalu Hai (Background Mein Bhi Boliye)…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NeonGreen,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            VoiceWaveform(
                isActive = isListening || isSpeaking || isContinuousMicOn,
                amplitude = audioAmplitude,
                accentColor = NeonCyan,
                secondaryColor = HotPink,
                modifier = Modifier.height(24.dp)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = {
                        Text(
                            text = "Kuch bhi bolo ya likho (Call, Message, Time, Torch)…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = GlassBorderLight,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedContainerColor = SurfaceAlt,
                        unfocusedContainerColor = SurfaceAlt
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_input_field")
                )

                NeonIconButton(
                    icon = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send Message",
                    onClick = {
                        if (inputText.isNotBlank()) {
                            onQuickPrompt(inputText)
                            inputText = ""
                        }
                    },
                    tint = NeonCyan,
                    testTag = "send_message_button"
                )

                // One-Tap Continuous Background Mic Toggle Button (with automatic RGB color shifting)
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .scale(micPulse)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(rgbColor1, rgbColor2, rgbColor3)
                            )
                        )
                        .border(2.dp, Color.White.copy(alpha = 0.90f), CircleShape)
                        .clickable {
                            if (isContinuousMicOn || isListening) {
                                onStopVoiceListen()
                            } else {
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                        .testTag("pulsing_mic_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isContinuousMicOn || isListening) Icons.Default.StopCircle else Icons.Default.Mic,
                        contentDescription = "Always-On Voice Mic Button",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}
