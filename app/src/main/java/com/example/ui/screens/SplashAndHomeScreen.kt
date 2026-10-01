package com.example.ui.screens

import android.Manifest
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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

// SINGLE ALL-IN-ONE SIMPLE CHAT + VOICE + SCREEN CONTROL SCREEN
@Composable
fun HomeScreen(
    selectedMood: JarvisMood,
    wakeState: WakeState,
    orbState: OrbVisualState,
    showHologramAvatar: Boolean,
    isListening: Boolean,
    isSpeaking: Boolean,
    audioAmplitude: Float,
    liveTranscript: String,
    recentMessages: List<ChatMessageEntity>,
    pendingConfirmation: PendingConfirmationAction? = null,
    screenState: ScreenMockState = ScreenMockState(),
    capturedBitmap: Bitmap? = null,
    onToggleAvatar: () -> Unit = {},
    onStartVoiceListen: () -> Unit,
    onStopVoiceListen: () -> Unit = {},
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

    LaunchedEffect(recentMessages.size) {
        if (recentMessages.isNotEmpty()) {
            listState.animateScrollToItem(recentMessages.lastIndex)
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "mic_pulse")
    val micPulse by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (isListening) 1.15f else 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mic_scale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("home_screen")
    ) {
        // 1. TOP BAR: JARVIS ORB + STATUS ON LEFT, SCREEN SHARE & SETTINGS/PERMISSIONS IN TOP-RIGHT CORNER
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
                    orbSize = 54.dp,
                    visualState = orbState,
                    moodColor = selectedMood.accentColor,
                    showHologramAvatar = showHologramAvatar,
                    onClick = {
                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isListening || wakeState == WakeState.ACTIVE) NeonGreen else HotPink)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "JARVIS",
                            style = TextStyle(
                                fontFamily = SoraFontFamily,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 20.sp,
                                brush = JarvisGradients.PrimaryNeon
                            )
                        )
                    }
                    Text(
                        text = when {
                            isListening -> "🎤 Sun rahi hun ji… boliye"
                            isSpeaking -> "🔊 Bol rahi hun ji… 💕"
                            else -> "⚡ All Phone & Screen Control Ready"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }

            // Top-Right Corner Controls: Screen Share Toggle, Clear Chat, and Settings (Permissions + Gemini API Key)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NeonIconButton(
                    icon = Icons.Default.ScreenShare,
                    contentDescription = "Screen Share & Control",
                    onClick = { showScreenPanel = !showScreenPanel },
                    tint = if (showScreenPanel || screenState.isSharingLive) NeonGreen else NeonCyan,
                    testTag = "top_screen_share_btn"
                )

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
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .testTag("top_settings_btn"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings, Permissions & API Key",
                        tint = NeonGreen,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
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

        // 2. INLINE SCREEN SHARE & CONTROL BAR (Toggled via Top Screen icon)
        AnimatedVisibility(visible = showScreenPanel) {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                borderColor = NeonCyan,
                contentPadding = PaddingValues(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🖥️ Screen Share & Control (${if (screenState.isSharingLive) "LIVE 🔴" else "Paused"})",
                        style = MaterialTheme.typography.labelLarge,
                        color = NeonCyan,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = " screenshot load / control",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }

                if (capturedBitmap != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Image(
                        bitmap = capturedBitmap.asImageBitmap(),
                        contentDescription = "Captured Screen",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    NeonButton(
                        text = "📸 Screen",
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
                    NeonButton(
                        text = "👆 Click",
                        onClick = { onPerformScreenAction("CLICK", "Button") },
                        accentColor = NeonGreen,
                        modifier = Modifier.weight(1f),
                        testTag = "vision_click_btn"
                    )
                    NeonButton(
                        text = "↕️ Scroll",
                        onClick = { onPerformScreenAction("SCROLL", "DOWN") },
                        accentColor = NeonPurple,
                        modifier = Modifier.weight(1f),
                        testTag = "vision_scroll_btn"
                    )
                    NeonButton(
                        text = "📖 Padho",
                        onClick = { onPerformScreenAction("READ_OCR", "") },
                        accentColor = HotPink,
                        modifier = Modifier.weight(1f),
                        testTag = "vision_ocr_btn"
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
            "📞 Mummy ko call karo",
            "💬 WhatsApp kholo",
            "🎵 Arijit Singh gaane lagao",
            "🔦 Torch on karo",
            "🖥️ Screen padho",
            "🌐 Google pe news search karo",
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
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(SurfaceAlt)
                        .border(1.dp, GlassBorderCyan, RoundedCornerShape(999.dp))
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

        // 6. BOTTOM CHAT + MIC BAR
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            borderColor = if (isListening) NeonCyan else GlassBorderPink,
            contentPadding = PaddingValues(10.dp)
        ) {
            if (liveTranscript.isNotBlank() || isListening) {
                Text(
                    text = if (liveTranscript.isNotBlank()) liveTranscript else "Sun rahi hun ji… boliye 🎤",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NeonCyan,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            VoiceWaveform(
                isActive = isListening || isSpeaking,
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
                            text = "Kuch bhi bolo ya likho (Call, App, Search, Baat)…",
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

                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .scale(micPulse)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                if (isListening) listOf(NeonRed, HotPink)
                                else listOf(NeonCyan, NeonPurple, HotPink)
                            )
                        )
                        .border(2.dp, Color.White.copy(alpha = 0.75f), CircleShape)
                        .clickable {
                            if (isListening) {
                                onStopVoiceListen()
                            } else {
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                        .testTag("pulsing_mic_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isListening) Icons.Default.StopCircle else Icons.Default.Mic,
                        contentDescription = "Voice Mic Button",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    }
}
