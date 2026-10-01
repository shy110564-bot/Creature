package com.example.ui.screens

import android.Manifest
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.SwipeVertical
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Translate
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.ChatMessageEntity
import com.example.data.model.JarvisMood
import com.example.data.model.PendingConfirmationAction
import com.example.data.model.ScreenMockState
import com.example.ui.components.ChatBubble
import com.example.ui.components.GlassCard
import com.example.ui.components.NeonButton
import com.example.ui.components.NeonIconButton
import com.example.ui.components.RgbNeonDivider
import com.example.ui.components.VoiceWaveform
import com.example.ui.theme.ElevatedGlass
import com.example.ui.theme.GlassBorderCyan
import com.example.ui.theme.GlassBorderLight
import com.example.ui.theme.GlassBorderPink
import com.example.ui.theme.HotPink
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.NeonRed
import com.example.ui.theme.SurfaceAlt
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

// SCREEN 3: CLEAN VOICE CHAT SCREEN
@Composable
fun VoiceChatScreen(
    messages: List<ChatMessageEntity>,
    selectedMood: JarvisMood,
    pendingConfirmation: PendingConfirmationAction? = null,
    isListening: Boolean,
    isSpeaking: Boolean,
    audioAmplitude: Float,
    liveTranscript: String,
    onSendMessage: (String) -> Unit,
    onConfirmSensitiveAction: () -> Unit = {},
    onCancelSensitiveAction: () -> Unit = {},
    onStartVoiceListen: () -> Unit,
    onStopVoiceListen: () -> Unit,
    onSpeakText: (String) -> Unit,
    onClearChat: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onStartVoiceListen()
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
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
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .testTag("voice_chat_screen")
    ) {
        // Clean Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NeonIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back to Home",
                onClick = onBack,
                testTag = "chat_back_btn"
            )

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "◉ JARVIS",
                    style = MaterialTheme.typography.titleMedium,
                    color = NeonCyan
                )
                Text(
                    text = "${selectedMood.emoji} ${selectedMood.title}",
                    style = MaterialTheme.typography.labelSmall,
                    color = HotPink
                )
            }

            NeonIconButton(
                icon = Icons.Default.DeleteSweep,
                contentDescription = "Clear Chat",
                onClick = onClearChat,
                tint = NeonRed,
                testTag = "clear_chat_btn"
            )
        }

        Spacer(modifier = Modifier.height(6.dp))
        RgbNeonDivider()

        // Sensitive Action Confirmation Card (only visible when needed)
        AnimatedVisibility(visible = pendingConfirmation != null) {
            if (pendingConfirmation != null) {
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .testTag("sensitive_confirmation_card"),
                    borderColor = NeonRed,
                    backgroundColor = ElevatedGlass,
                    contentPadding = PaddingValues(12.dp)
                ) {
                    Text(
                        text = "⚠️ ${pendingConfirmation.title}",
                        style = MaterialTheme.typography.labelLarge,
                        color = NeonRed
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = pendingConfirmation.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        NeonButton(
                            text = "Confirm ✅",
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

        Spacer(modifier = Modifier.height(8.dp))

        // Quick Voice & Phone Commands
        val quickChips = listOf(
            "Mummy ko call karo",
            "WhatsApp pe Rahul ko bolo main aa raha hun",
            "Instagram kholo",
            "Arijit Singh ke gaane lagao",
            "WiFi on karo aur brightness 50%",
            "YouTube kholo",
            "Tumhe kaun banaya?"
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            quickChips.forEachIndexed { idx, chip ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(SurfaceAlt)
                        .border(1.dp, GlassBorderCyan, RoundedCornerShape(999.dp))
                        .clickable { onSendMessage(chip) }
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

        Spacer(modifier = Modifier.height(8.dp))

        // Chat Bubbles
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                ChatBubble(
                    message = msg,
                    onSpeakAgain = onSpeakText
                )
            }
        }

        // Bottom Voice Waveform & Mic Input Card
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            borderColor = if (isListening) NeonCyan else GlassBorderPink,
            contentPadding = PaddingValues(12.dp)
        ) {
            if (liveTranscript.isNotBlank() || isListening) {
                Text(
                    text = if (liveTranscript.isNotBlank()) liveTranscript else "Sun rahi hun ji… boliye 🎤",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NeonCyan,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }

            VoiceWaveform(
                isActive = isListening || isSpeaking,
                amplitude = audioAmplitude,
                accentColor = NeonCyan,
                secondaryColor = HotPink,
                modifier = Modifier.height(30.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

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
                            text = "Boliye ya type karein…",
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
                            onSendMessage(inputText)
                            inputText = ""
                        }
                    },
                    tint = NeonCyan,
                    testTag = "send_message_button"
                )

                Box(
                    modifier = Modifier
                        .size(62.dp)
                        .scale(micPulse)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                if (isListening) listOf(NeonRed, HotPink)
                                else listOf(NeonCyan, NeonPurple, HotPink)
                            )
                        )
                        .border(2.dp, Color.White.copy(alpha = 0.7f), CircleShape)
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
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(74.dp))
    }
}

// SCREEN 4: CLEAN SCREEN SHARE + VISION SCREEN
@Composable
fun ScreenShareVisionScreen(
    screenState: ScreenMockState,
    capturedBitmap: Bitmap?,
    onSetCapturedBitmap: (Bitmap?) -> Unit,
    onPerformAction: (String, String) -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    var customTypeInput by remember { mutableStateOf("Main aa raha hun ji 💕") }

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
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag("screen_share_screen")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NeonIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                onClick = onBack,
                testTag = "vision_back_btn"
            )
            Text(
                text = "🖥️ SCREEN VISION & SHARE",
                style = MaterialTheme.typography.titleMedium,
                color = NeonCyan
            )
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (screenState.isSharingLive) NeonRed.copy(alpha = 0.22f) else SurfaceAlt)
                    .border(
                        1.dp,
                        if (screenState.isSharingLive) NeonRed else GlassBorderLight,
                        RoundedCornerShape(999.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (screenState.isSharingLive) "🔴 LIVE" else "⏸ PAUSED",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (screenState.isSharingLive) NeonRed else TextSecondary,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        RgbNeonDivider()
        Spacer(modifier = Modifier.height(12.dp))

        // 16:9 Live Screen Preview
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
            borderColor = NeonCyan,
            backgroundColor = ElevatedGlass,
            contentPadding = PaddingValues(14.dp)
        ) {
            if (capturedBitmap != null) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Image(
                        bitmap = capturedBitmap.asImageBitmap(),
                        contentDescription = "Uploaded Screenshot",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp))
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .background(ElevatedGlass, RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        Text(
                            text = "◉ JARVIS Vision Active",
                            style = MaterialTheme.typography.labelSmall,
                            color = NeonCyan
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "📱 ${screenState.currentAppTitle}",
                            style = MaterialTheme.typography.labelSmall,
                            color = NeonCyan
                        )
                        Text(
                            text = "Scroll: ${screenState.scrollOffset}px",
                            style = MaterialTheme.typography.labelSmall,
                            color = HotPink
                        )
                    }

                    Column {
                        Text(
                            text = screenState.headlineText,
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = screenState.subText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = NeonGreen
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(NeonCyan.copy(alpha = 0.25f))
                                .border(1.dp, NeonCyan, RoundedCornerShape(10.dp))
                                .clickable { onPerformAction("CLICK", "Blue Button") }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "🔵 Blue Action Button",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextPrimary
                            )
                        }

                        Text(
                            text = if (screenState.typedFieldValue.isNotBlank())
                                "\"${screenState.typedFieldValue}\""
                            else "Ready",
                            style = MaterialTheme.typography.labelSmall,
                            color = HotPink
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            NeonButton(
                text = "📸 Load Screenshot",
                icon = Icons.Default.AddPhotoAlternate,
                onClick = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                accentColor = NeonCyan,
                secondaryColor = NeonPurple,
                modifier = Modifier.weight(1f),
                testTag = "pick_screenshot_btn"
            )
            if (capturedBitmap != null) {
                NeonButton(
                    text = "Reset",
                    onClick = { onSetCapturedBitmap(null) },
                    accentColor = HotPink,
                    testTag = "reset_screenshot_btn"
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 2x2 Control Grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            NeonButton(
                text = "Click",
                icon = Icons.Default.TouchApp,
                onClick = { onPerformAction("CLICK", "Blue Button") },
                accentColor = NeonCyan,
                secondaryColor = NeonPurple,
                modifier = Modifier.weight(1f),
                testTag = "vision_click_btn"
            )
            NeonButton(
                text = "Scroll",
                icon = Icons.Default.SwipeVertical,
                onClick = { onPerformAction("SCROLL", "DOWN") },
                accentColor = NeonPurple,
                secondaryColor = HotPink,
                modifier = Modifier.weight(1f),
                testTag = "vision_scroll_btn"
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            NeonButton(
                text = "Type Text",
                icon = Icons.Default.Keyboard,
                onClick = { onPerformAction("TYPE", customTypeInput) },
                accentColor = HotPink,
                secondaryColor = NeonCyan,
                modifier = Modifier.weight(1f),
                testTag = "vision_type_btn"
            )
            NeonButton(
                text = if (screenState.isSharingLive) "Stop Share" else "Start Share",
                icon = Icons.Default.StopCircle,
                onClick = { onPerformAction("STOP_TOGGLE", "") },
                accentColor = NeonRed,
                secondaryColor = NeonPurple,
                modifier = Modifier.weight(1f),
                testTag = "vision_stop_btn"
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            NeonButton(
                text = "Read Screen",
                icon = Icons.Default.DocumentScanner,
                onClick = { onPerformAction("READ_OCR", "") },
                accentColor = NeonGreen,
                secondaryColor = NeonCyan,
                modifier = Modifier.weight(1f),
                testTag = "vision_ocr_btn"
            )
            NeonButton(
                text = "Translate",
                icon = Icons.Default.Translate,
                onClick = { onPerformAction("TRANSLATE", "") },
                accentColor = NeonCyan,
                secondaryColor = HotPink,
                modifier = Modifier.weight(1f),
                testTag = "vision_translate_btn"
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            borderColor = GlassBorderPink
        ) {
            OutlinedTextField(
                value = customTypeInput,
                onValueChange = { customTypeInput = it },
                singleLine = true,
                label = { Text("Text to Auto-Type on Screen") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = HotPink,
                    unfocusedBorderColor = GlassBorderLight,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("vision_type_input")
            )
        }

        Spacer(modifier = Modifier.height(84.dp))
    }
}
