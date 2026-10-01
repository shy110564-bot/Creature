package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.local.ChatMessageEntity
import com.example.data.model.JarvisMood
import com.example.data.model.OrbVisualState
import com.example.data.model.WakeState
import com.example.ui.theme.AmoledBlack
import com.example.ui.theme.ElevatedGlass
import com.example.ui.theme.GlassBorderCyan
import com.example.ui.theme.GlassBorderLight
import com.example.ui.theme.HotPink
import com.example.ui.theme.JarvisGradients
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.SurfaceAlt
import com.example.ui.theme.SurfaceGlass
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import com.example.ui.viewmodel.JarvisScreen
import com.example.ui.viewmodel.OverlayNotification
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

val FullRgbSpectrum = listOf(
    Color(0xFFFF0055), // Neon Red-Pink
    Color(0xFFFF6D00), // Electric Orange
    Color(0xFFFFEA00), // Cyber Yellow
    Color(0xFF00FF66), // Matrix Green
    Color(0xFF00E5FF), // Neon Cyan
    Color(0xFF2979FF), // Electric Blue
    Color(0xFFB14EFF), // Neon Purple
    Color(0xFFFF00AA), // Hot Magenta
    Color(0xFFFF0055)  // Loop back seamlessly
)

// CONTINUOUS RGB EDGE, CORNER, LINE & BACKGROUND LIGHTING SYSTEM
@Composable
fun RgbEdgeAndBackgroundContainer(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "rgb_master_lighting")

    val rgbAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rgb_sweep_angle"
    )

    val bgWavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28318f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rgb_bg_wave"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AmoledBlack)
    ) {
        // 1. Continuous Animated RGB Background Aura + Travelling Corner & Edge Border Lines
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val center = Offset(w / 2f, h / 2f)

            // Moving multi-color RGB background aura blobs
            val orb1Center = Offset(
                x = w * (0.25f + 0.18f * cos(bgWavePhase)),
                y = h * (0.20f + 0.12f * sin(bgWavePhase))
            )
            val orb2Center = Offset(
                x = w * (0.75f + 0.18f * sin(bgWavePhase * 0.8f)),
                y = h * (0.78f + 0.12f * cos(bgWavePhase * 0.8f))
            )
            val orb3Center = Offset(
                x = w * (0.50f + 0.22f * sin(bgWavePhase * 1.2f)),
                y = h * (0.48f + 0.16f * cos(bgWavePhase * 1.2f))
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF00E5FF).copy(alpha = 0.16f), Color.Transparent),
                    center = orb1Center,
                    radius = size.minDimension * 0.65f
                ),
                radius = size.minDimension * 0.65f,
                center = orb1Center
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFF0055).copy(alpha = 0.15f), Color.Transparent),
                    center = orb2Center,
                    radius = size.minDimension * 0.68f
                ),
                radius = size.minDimension * 0.68f,
                center = orb2Center
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF00FF66).copy(alpha = 0.11f), Color(0xFFB14EFF).copy(alpha = 0.08f), Color.Transparent),
                    center = orb3Center,
                    radius = size.minDimension * 0.60f
                ),
                radius = size.minDimension * 0.60f,
                center = orb3Center
            )

            // 4-Corner RGB Intense Glow Halos
            val cornerRadiusGlow = size.minDimension * 0.35f
            val cornerColors = listOf(
                Color(0xFFFF0055),
                Color(0xFF00E5FF),
                Color(0xFF00FF66),
                Color(0xFFB14EFF)
            )
            val shiftIdx = ((rgbAngle / 90f).toInt()) % 4
            val corners = listOf(
                Offset(0f, 0f),
                Offset(w, 0f),
                Offset(w, h),
                Offset(0f, h)
            )
            corners.forEachIndexed { idx, pt ->
                val c = cornerColors[(idx + shiftIdx) % cornerColors.size]
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(c.copy(alpha = 0.28f), Color.Transparent),
                        center = pt,
                        radius = cornerRadiusGlow
                    ),
                    radius = cornerRadiusGlow,
                    center = pt
                )
            }
        }

        // Main App Content
        content()

        // 2. Top-Layer Travelling RGB Edge & Corner Line Overlay (Non-blocking touch)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val center = Offset(w / 2f, h / 2f)
            val cornerRad = 28.dp.toPx()

            // Soft outer RGB border glow
            rotate(degrees = rgbAngle, pivot = center) {
                val sweepBrush = Brush.sweepGradient(
                    colors = FullRgbSpectrum,
                    center = center
                )
                drawRoundRect(
                    brush = sweepBrush,
                    topLeft = Offset(3.dp.toPx(), 3.dp.toPx()),
                    size = Size(w - 6.dp.toPx(), h - 6.dp.toPx()),
                    cornerRadius = CornerRadius(cornerRad, cornerRad),
                    style = Stroke(width = 6.dp.toPx())
                )
            }

            // Crisp inner travelling RGB line around all 4 edges & corners
            rotate(degrees = -rgbAngle * 1.2f, pivot = center) {
                val sharpSweep = Brush.sweepGradient(
                    colors = FullRgbSpectrum,
                    center = center
                )
                drawRoundRect(
                    brush = sharpSweep,
                    topLeft = Offset(1.5.dp.toPx(), 1.5.dp.toPx()),
                    size = Size(w - 3.dp.toPx(), h - 3.dp.toPx()),
                    cornerRadius = CornerRadius(cornerRad, cornerRad),
                    style = Stroke(width = 2.5.dp.toPx())
                )
            }
        }
    }
}

// ANIMATED RGB DIVIDER LINE
@Composable
fun RgbNeonDivider(
    modifier: Modifier = Modifier,
    height: Dp = 2.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "rgb_line")
    val offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rgb_line_shift"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        drawLine(
            brush = Brush.linearGradient(
                colors = FullRgbSpectrum,
                start = Offset(offset % size.width - size.width, 0f),
                end = Offset(offset % size.width + size.width, 0f)
            ),
            start = Offset(0f, size.height / 2f),
            end = Offset(size.width, size.height / 2f),
            strokeWidth = size.height,
            cap = StrokeCap.Round
        )
    }
}

// 1. GLASS CARD (with animated RGB border shimmer)
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    borderColor: Color = GlassBorderLight,
    backgroundColor: Color = SurfaceGlass,
    useRgbBorder: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val shape = RoundedCornerShape(cornerRadius)
    val clickMod = if (onClick != null) {
        Modifier.clickable {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        }
    } else Modifier

    val infiniteTransition = rememberInfiniteTransition(label = "card_rgb")
    val shift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(
            animation = tween(3500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "card_rgb_shift"
    )

    val borderModifier = if (useRgbBorder) {
        Modifier.border(
            width = 1.2.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    NeonCyan.copy(alpha = 0.7f),
                    NeonPurple.copy(alpha = 0.7f),
                    HotPink.copy(alpha = 0.7f),
                    NeonGreen.copy(alpha = 0.7f),
                    NeonCyan.copy(alpha = 0.7f)
                ),
                start = Offset(shift - 600f, 0f),
                end = Offset(shift, 600f)
            ),
            shape = shape
        )
    } else {
        Modifier.border(width = 1.dp, color = borderColor, shape = shape)
    }

    Column(
        modifier = modifier
            .clip(shape)
            .background(backgroundColor)
            .background(JarvisGradients.GlassSurface)
            .then(borderModifier)
            .then(clickMod)
            .padding(contentPadding),
        content = content
    )
}

// 2. NEON BUTTON
@Composable
fun NeonButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    accentColor: Color = NeonCyan,
    secondaryColor: Color = NeonPurple,
    testTag: String = "neon_button"
) {
    val haptic = LocalHapticFeedback.current
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1.0f,
        animationSpec = tween(durationMillis = 150),
        label = "neon_btn_scale"
    )
    val shape = RoundedCornerShape(16.dp)

    Box(
        modifier = modifier
            .scale(scale)
            .minimumInteractiveComponentSize()
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(accentColor.copy(alpha = 0.25f), secondaryColor.copy(alpha = 0.25f))
                )
            )
            .border(
                width = 1.3.dp,
                brush = Brush.linearGradient(listOf(accentColor, secondaryColor, HotPink)),
                shape = shape
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                    },
                    onTap = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onClick()
                    }
                )
            }
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = text,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// 3. VOICE WAVEFORM (Multi-color RGB animated bars)
@Composable
fun VoiceWaveform(
    isActive: Boolean,
    amplitude: Float,
    accentColor: Color = NeonCyan,
    secondaryColor: Color = HotPink,
    modifier: Modifier = Modifier,
    barCount: Int = 22
) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(42.dp)
    ) {
        val totalWidth = size.width
        val maxBarHeight = size.height
        val barWidth = (totalWidth / (barCount * 2.2f)).coerceAtLeast(4f)
        val spacing = (totalWidth - (barWidth * barCount)) / (barCount + 1).coerceAtLeast(1)

        for (i in 0 until barCount) {
            val waveFactor = if (isActive) {
                val dynamic = sin(phase + i * 0.55f) * 0.5f + 0.5f
                (0.22f + dynamic * amplitude.coerceIn(0.25f, 1.0f)).coerceIn(0.18f, 1.0f)
            } else {
                0.16f + (sin(phase * 0.5f + i * 0.45f) * 0.08f)
            }
            val barHeight = (maxBarHeight * waveFactor).coerceAtLeast(6f)
            val x = spacing + i * (barWidth + spacing)
            val topY = (maxBarHeight - barHeight) / 2f
            val bottomY = topY + barHeight

            val topColor = FullRgbSpectrum[i % (FullRgbSpectrum.size - 1)]
            val bottomColor = FullRgbSpectrum[(i + 3) % (FullRgbSpectrum.size - 1)]

            drawLine(
                brush = Brush.verticalGradient(
                    colors = listOf(topColor, bottomColor)
                ),
                start = Offset(x, topY),
                end = Offset(x, bottomY),
                strokeWidth = barWidth,
                cap = StrokeCap.Round
            )
        }
    }
}

// 4. JARVIS 3D ANIMATED ORB (with RGB rotating rings)
@Composable
fun JarvisOrb(
    orbSize: Dp,
    visualState: OrbVisualState,
    moodColor: Color,
    showHologramAvatar: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val infiniteTransition = rememberInfiniteTransition(label = "jarvis_orb")

    val outerRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (visualState == OrbVisualState.THINKING_PURPLE) 1600 else 4200,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "outer_ring"
    )

    val innerRotation by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (visualState == OrbVisualState.SPEAKING_WAVE) 1800 else 3200,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "inner_ring"
    )

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = when (visualState) {
            OrbVisualState.WAKE_EXPANDING -> 1.12f
            OrbVisualState.SPEAKING_WAVE -> 1.08f
            OrbVisualState.THINKING_PURPLE -> 1.05f
            OrbVisualState.IDLE_LISTENING -> 1.02f
        },
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb_pulse"
    )

    val primaryCoreColor = when (visualState) {
        OrbVisualState.THINKING_PURPLE -> NeonPurple
        OrbVisualState.WAKE_EXPANDING -> NeonCyan
        OrbVisualState.SPEAKING_WAVE -> HotPink
        OrbVisualState.IDLE_LISTENING -> moodColor
    }

    Box(
        modifier = modifier
            .size(orbSize)
            .scale(pulseScale)
            .clip(CircleShape)
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .testTag("jarvis_orb"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxR = size.minDimension / 2f

            // Ambient radial glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryCoreColor.copy(alpha = 0.48f),
                        NeonPurple.copy(alpha = 0.22f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = maxR
                ),
                radius = maxR,
                center = center
            )

            // Outer Rotating Full-RGB Ring 1
            rotate(degrees = outerRotation, pivot = center) {
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = FullRgbSpectrum,
                        center = center
                    ),
                    startAngle = 0f,
                    sweepAngle = 310f,
                    useCenter = false,
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round),
                    topLeft = Offset(center.x - maxR * 0.90f, center.y - maxR * 0.90f),
                    size = Size(maxR * 1.80f, maxR * 1.80f)
                )
            }

            // Inner Rotating RGB Ring 2
            rotate(degrees = innerRotation, pivot = center) {
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = FullRgbSpectrum.reversed(),
                        center = center
                    ),
                    startAngle = 35f,
                    sweepAngle = 270f,
                    useCenter = false,
                    style = Stroke(width = 2.8.dp.toPx(), cap = StrokeCap.Round),
                    topLeft = Offset(center.x - maxR * 0.74f, center.y - maxR * 0.74f),
                    size = Size(maxR * 1.48f, maxR * 1.48f)
                )
            }

            // 3D Sphere Core
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.92f),
                        primaryCoreColor,
                        NeonPurple.copy(alpha = 0.85f),
                        AmoledBlack
                    ),
                    center = Offset(center.x - maxR * 0.15f, center.y - maxR * 0.15f),
                    radius = maxR * 0.62f
                ),
                radius = maxR * 0.56f,
                center = center
            )
        }

        if (showHologramAvatar) {
            Image(
                painter = painterResource(id = R.drawable.img_jarvis_avatar_1790828748363),
                contentDescription = "JARVIS Avatar",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(orbSize * 0.66f)
                    .clip(CircleShape)
                    .border(2.dp, primaryCoreColor, CircleShape)
            )
        }
    }
}

// 5. MOOD CHIP
@Composable
fun MoodChip(
    mood: JarvisMood,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.05f else 1.0f,
        animationSpec = tween(180),
        label = "mood_chip_scale"
    )
    val shape = RoundedCornerShape(999.dp)

    Row(
        modifier = modifier
            .scale(scale)
            .minimumInteractiveComponentSize()
            .clip(shape)
            .background(
                if (isSelected) mood.accentColor.copy(alpha = 0.22f)
                else SurfaceAlt
            )
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) mood.accentColor else GlassBorderLight,
                shape = shape
            )
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .testTag("mood_chip_${mood.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = mood.emoji, fontSize = 16.sp)
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = mood.title.removeSuffix(" Mode"),
            style = MaterialTheme.typography.labelLarge,
            color = if (isSelected) TextPrimary else TextSecondary
        )
    }
}

// 6. CLEAN CHAT BUBBLE (No cluttered debug metadata)
@Composable
fun ChatBubble(
    message: ChatMessageEntity,
    onSpeakAgain: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isUser = message.isUser
    val bubbleShape = RoundedCornerShape(
        topStart = 20.dp,
        topEnd = 20.dp,
        bottomStart = if (isUser) 20.dp else 4.dp,
        bottomEnd = if (isUser) 4.dp else 20.dp
    )
    val borderColor = if (isUser) GlassBorderCyan else HotPink.copy(alpha = 0.60f)
    val bgBrush = if (isUser) {
        Brush.linearGradient(
            listOf(NeonCyan.copy(alpha = 0.16f), SurfaceAlt)
        )
    } else {
        Brush.linearGradient(
            listOf(HotPink.copy(alpha = 0.16f), NeonPurple.copy(alpha = 0.14f), ElevatedGlass)
        )
    }
    val timeText = remember(message.timestamp) {
        SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(message.timestamp))
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 330.dp)
                .clip(bubbleShape)
                .background(bgBrush)
                .border(1.dp, borderColor, bubbleShape)
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isUser) "You" else "◉ JARVIS",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isUser) NeonCyan else HotPink,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary
            )

            if (!message.actionBadge.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(NeonCyan.copy(alpha = 0.15f))
                        .border(1.dp, NeonCyan.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = "Action Executed",
                        tint = NeonGreen,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = message.actionBadge,
                        style = MaterialTheme.typography.labelSmall,
                        color = NeonGreen
                    )
                }
            }

            if (!isUser) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(HotPink.copy(alpha = 0.18f))
                            .clickable { onSpeakAgain(message.spokenCleanText) }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Replay JARVIS voice",
                            tint = HotPink,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Suno",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextPrimary
                        )
                    }
                }
            }
        }
    }
}

// 7. FLOATING GLASS BOTTOM NAV (With RGB Neon Border)
@Composable
fun JarvisBottomNav(
    currentScreen: JarvisScreen,
    onSelectScreen: (JarvisScreen) -> Unit,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        Triple(JarvisScreen.HOME, Icons.Default.Home, "Home"),
        Triple(JarvisScreen.VOICE_CHAT, Icons.Default.RecordVoiceOver, "Chat"),
        Triple(JarvisScreen.SCREEN_SHARE, Icons.Default.ScreenShare, "Screen"),
        Triple(JarvisScreen.MOODS_GOD_MODE, Icons.Default.Mood, "Control"),
        Triple(JarvisScreen.SETTINGS, Icons.Default.Settings, "Settings")
    )
    val haptic = LocalHapticFeedback.current

    val infiniteTransition = rememberInfiniteTransition(label = "nav_rgb")
    val shift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "nav_rgb_shift"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(ElevatedGlass)
                .border(
                    width = 1.5.dp,
                    brush = Brush.linearGradient(
                        colors = FullRgbSpectrum,
                        start = Offset(shift - 500f, 0f),
                        end = Offset(shift + 500f, 100f)
                    ),
                    shape = RoundedCornerShape(26.dp)
                )
                .padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { (screen, icon, label) ->
                val isSelected = currentScreen == screen ||
                    (screen == JarvisScreen.SETTINGS && (currentScreen == JarvisScreen.VOICE_SETTINGS || currentScreen == JarvisScreen.DEVELOPER_ABOUT))

                Column(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (isSelected) NeonCyan.copy(alpha = 0.18f) else Color.Transparent
                        )
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelectScreen(screen)
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .testTag("nav_${screen.routeId}"),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = if (isSelected) NeonCyan else TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) NeonCyan else TextSecondary
                    )
                }
            }
        }
    }
}

// 8. NEON ICON BUTTON
@Composable
fun NeonIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = NeonCyan,
    testTag: String = "neon_icon_btn"
) {
    val haptic = LocalHapticFeedback.current
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(SurfaceAlt)
            .border(1.dp, tint.copy(alpha = 0.45f), CircleShape)
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
    }
}

// 9. NEON SLIDER & TOGGLE
@Composable
fun NeonSliderRow(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float> = 0.5f..1.5f,
    accentColor: Color = NeonCyan,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            Text(text = valueText, style = MaterialTheme.typography.labelSmall, color = accentColor)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor,
                inactiveTrackColor = SurfaceAlt
            )
        )
    }
}

@Composable
fun NeonToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    accentColor: Color = NeonCyan,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
            Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = AmoledBlack,
                checkedTrackColor = accentColor,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = SurfaceAlt
            )
        )
    }
}

// SCREEN 9: NOTIFICATION OVERLAY
@Composable
fun NotificationOverlayBanner(
    notification: OverlayNotification?,
    onDismiss: () -> Unit,
    onActionClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = notification != null,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier
    ) {
        if (notification != null) {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .testTag("notification_overlay_card"),
                borderColor = HotPink,
                backgroundColor = ElevatedGlass
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = notification.title,
                        style = MaterialTheme.typography.labelLarge,
                        color = NeonCyan
                    )
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss notification",
                        tint = TextSecondary,
                        modifier = Modifier
                            .size(24.dp)
                            .clickable { onDismiss() }
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = notification.message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    NeonButton(
                        text = notification.actionLabel,
                        onClick = {
                            onDismiss()
                            onActionClick()
                        },
                        accentColor = HotPink,
                        secondaryColor = NeonPurple,
                        testTag = "notification_action_btn"
                    )
                }
            }
        }
    }
}

// SCREEN 10: LOCK SCREEN WIDGET
@Composable
fun LockScreenWidgetModal(
    wakeState: WakeState,
    mood: JarvisMood,
    isServiceRunning: Boolean,
    onToggleService: () -> Unit,
    onWakeTrigger: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val timeString = remember {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
    }
    val dateString = remember {
        SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date())
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        RgbEdgeAndBackgroundContainer {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Lock Screen Mode",
                        tint = NeonCyan,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = timeString,
                        style = MaterialTheme.typography.displayLarge.copy(fontSize = 60.sp),
                        color = TextPrimary
                    )
                    Text(
                        text = dateString,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("lock_screen_widget_card"),
                        borderColor = NeonCyan,
                        backgroundColor = ElevatedGlass
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(NeonGreen)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "◉ JARVIS",
                                    style = MaterialTheme.typography.titleMedium,
                                                                        color = NeonCyan
                                )
                            }
                            Text(
                                text = "${mood.emoji} ${mood.title}",
                                style = MaterialTheme.typography.labelSmall,
                                color = HotPink
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "Listening… 'JARVIS' bolo 💕",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = wakeState.hindiStatus,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        VoiceWaveform(
                            isActive = true,
                            amplitude = 0.65f,
                            accentColor = NeonCyan,
                            secondaryColor = HotPink
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            NeonButton(
                                text = "Say 'Hey JARVIS'",
                                icon = Icons.Default.Mic,
                                onClick = {
                                    onWakeTrigger("Hey JARVIS")
                                    onDismiss()
                                },
                                modifier = Modifier.weight(1f),
                                testTag = "lock_widget_wake_btn"
                            )
                            NeonButton(
                                text = if (isServiceRunning) "24/7 Active" else "Enable 24/7",
                                onClick = onToggleService,
                                accentColor = if (isServiceRunning) NeonGreen else NeonPurple,
                                modifier = Modifier.weight(1f),
                                testTag = "lock_widget_service_btn"
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    NeonButton(
                        text = "Unlock & Return",
                        onClick = onDismiss,
                        accentColor = HotPink,
                        secondaryColor = NeonCyan,
                        testTag = "unlock_screen_btn"
                    )
                }
            }
        }
    }
}
