package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.HealthFitnessState
import com.example.data.model.JarvisMood
import com.example.data.model.PhoneControlCategory
import com.example.data.model.SixStepThought
import com.example.data.model.SmartHomeState
import com.example.data.model.SystemTelemetry
import com.example.data.remote.PhoneActionCommand
import com.example.ui.components.GlassCard
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
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.NeonRed
import com.example.ui.theme.SkyBlue
import com.example.ui.theme.SurfaceAlt
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

// SCREEN 5: CLEAN PHONE CONTROL, SMART HOME, HEALTH & MOODS HUB
@Composable
fun MoodAndGodModeScreen(
    selectedMood: JarvisMood,
    telemetry: SystemTelemetry,
    smartHomeState: SmartHomeState,
    healthState: HealthFitnessState,
    latestThought: SixStepThought,
    onSelectMood: (JarvisMood) -> Unit,
    onToggleTorch: () -> Unit,
    onVolumeChange: (Int) -> Unit,
    onBrightnessChange: (Int) -> Unit,
    onRefreshTelemetry: () -> Unit,
    onExecutePhoneAction: (PhoneActionCommand) -> Unit,
    onRunVoiceOrTextCommand: (String) -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    var selectedTab by remember { mutableStateOf(0) }
    var activeCategory by remember { mutableStateOf(PhoneControlCategory.CALLS) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag("moods_god_mode_screen")
    ) {
        // Clean Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NeonIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                onClick = onBack,
                testTag = "mood_back_btn"
            )
            Text(
                text = "📱 PHONE CONTROL",
                style = MaterialTheme.typography.titleMedium,
                color = NeonCyan
            )
            NeonIconButton(
                icon = Icons.Default.Refresh,
                contentDescription = "Refresh",
                onClick = onRefreshTelemetry,
                tint = NeonGreen,
                testTag = "refresh_telemetry_btn"
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        RgbNeonDivider()
        Spacer(modifier = Modifier.height(12.dp))

        // Sub-navigation Tabs
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val tabs = listOf(
                "⚡ Controls",
                "🏠 IoT & Health",
                "💖 Moods"
            )
            tabs.forEachIndexed { idx, label ->
                val isSelected = selectedTab == idx
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isSelected) NeonCyan.copy(alpha = 0.22f) else SurfaceAlt)
                        .border(
                            1.2.dp,
                            if (isSelected) NeonCyan else GlassBorderLight,
                            RoundedCornerShape(14.dp)
                        )
                        .clickable { selectedTab = idx }
                        .padding(vertical = 10.dp)
                        .testTag("god_mode_tab_$idx"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isSelected) NeonCyan else TextSecondary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // TAB 0: PHONE CONTROLS
        AnimatedVisibility(visible = selectedTab == 0) {
            Column(modifier = Modifier.fillMaxWidth()) {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = GlassBorderCyan
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TelemetryBadge(
                            label = "🔋 Battery",
                            value = "${telemetry.batteryPct}% ${if (telemetry.isCharging) "⚡" else ""}",
                            color = NeonGreen
                        )
                        TelemetryBadge(
                            label = "🧠 RAM",
                            value = telemetry.availableRamGb,
                            color = NeonCyan
                        )
                        TelemetryBadge(
                            label = "💾 Storage",
                            value = telemetry.freeStorageGb,
                            color = HotPink
                        )
                        TelemetryBadge(
                            label = "🔔 Sound",
                            value = telemetry.ringerModeLabel,
                            color = AmberWarning
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    NeonSliderRow(
                        label = "🔊 Volume",
                        valueText = "${telemetry.volumePct}%",
                        value = telemetry.volumePct / 100f,
                        valueRange = 0f..1f,
                        accentColor = NeonCyan,
                        onValueChange = { onVolumeChange((it * 100).toInt()) }
                    )

                    NeonSliderRow(
                        label = "☀️ Brightness",
                        valueText = "${telemetry.brightnessPct}%",
                        value = telemetry.brightnessPct / 100f,
                        valueRange = 0.05f..1f,
                        accentColor = AmberWarning,
                        onValueChange = { onBrightnessChange((it * 100).toInt()) }
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        NeonIconButton(
                            icon = Icons.Default.SkipPrevious,
                            contentDescription = "Previous",
                            onClick = { onRunVoiceOrTextCommand("Previous gaana") },
                            tint = NeonCyan,
                            testTag = "media_prev_btn"
                        )
                        NeonIconButton(
                            icon = Icons.Default.PlayArrow,
                            contentDescription = "Play",
                            onClick = { onRunVoiceOrTextCommand("Play karo") },
                            tint = NeonGreen,
                            testTag = "media_play_btn"
                        )
                        NeonIconButton(
                            icon = Icons.Default.Stop,
                            contentDescription = "Stop",
                            onClick = { onRunVoiceOrTextCommand("Stop karo") },
                            tint = NeonRed,
                            testTag = "media_stop_btn"
                        )
                        NeonIconButton(
                            icon = Icons.Default.SkipNext,
                            contentDescription = "Next",
                            onClick = { onRunVoiceOrTextCommand("Next gaana") },
                            tint = HotPink,
                            testTag = "media_next_btn"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Categories Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PhoneControlCategory.entries.forEach { cat ->
                        val selected = cat == activeCategory
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (selected) cat.accentColor.copy(alpha = 0.24f) else SurfaceAlt
                                )
                                .border(
                                    width = if (selected) 1.5.dp else 1.dp,
                                    color = if (selected) cat.accentColor else GlassBorderLight,
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable { activeCategory = cat }
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                                .testTag("category_chip_${cat.number}")
                        ) {
                            Text(
                                text = "${cat.emoji} ${cat.title.substringAfter(". ")}",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selected) TextPrimary else TextSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Active Category Commands
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = activeCategory.accentColor
                ) {
                    Text(
                        text = "${activeCategory.emoji} ${activeCategory.title.substringAfter(". ")}",
                        style = MaterialTheme.typography.titleMedium,
                        color = activeCategory.accentColor,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    activeCategory.sampleCommands.forEachIndexed { idx, cmdText ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(SurfaceAlt)
                                .border(
                                    1.dp,
                                    activeCategory.accentColor.copy(alpha = 0.4f),
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { onRunVoiceOrTextCommand(cmdText) }
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                                .testTag("cat_${activeCategory.number}_cmd_$idx"),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = cmdText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Run ⚡",
                                style = MaterialTheme.typography.labelSmall,
                                color = activeCategory.accentColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Quick Launchers
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = GlassBorderPink
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        NeonButton(
                            text = if (telemetry.torchOn) "🔦 Torch ON" else "🔦 Torch OFF",
                            icon = Icons.Default.FlashlightOn,
                            onClick = onToggleTorch,
                            accentColor = if (telemetry.torchOn) AmberWarning else NeonCyan,
                            modifier = Modifier.weight(1f),
                            testTag = "god_mode_torch_btn"
                        )
                        NeonButton(
                            text = "📞 Call",
                            icon = Icons.Default.Call,
                            onClick = { onRunVoiceOrTextCommand("Mummy ko call karo") },
                            accentColor = NeonGreen,
                            modifier = Modifier.weight(1f),
                            testTag = "god_mode_call_btn"
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        NeonButton(
                            text = "💬 WhatsApp",
                            icon = Icons.AutoMirrored.Filled.Message,
                            onClick = {
                                onRunVoiceOrTextCommand("WhatsApp pe Rahul ko bolo main aa raha hun")
                            },
                            accentColor = NeonGreen,
                            secondaryColor = NeonCyan,
                            modifier = Modifier.weight(1f),
                            testTag = "god_mode_whatsapp_btn"
                        )
                        NeonButton(
                            text = "🎵 Music",
                            icon = Icons.Default.MusicNote,
                            onClick = {
                                onRunVoiceOrTextCommand("Arijit Singh ke gaane lagao")
                            },
                            accentColor = HotPink,
                            secondaryColor = NeonPurple,
                            modifier = Modifier.weight(1f),
                            testTag = "god_mode_music_btn"
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        NeonButton(
                            text = "🗺️ Maps",
                            icon = Icons.Default.Map,
                            onClick = { onRunVoiceOrTextCommand("Nearby ATM dikhao") },
                            accentColor = SkyBlue,
                            secondaryColor = NeonCyan,
                            modifier = Modifier.weight(1f),
                            testTag = "god_mode_maps_btn"
                        )
                        NeonButton(
                            text = "📶 Wi-Fi",
                            icon = Icons.Default.Wifi,
                            onClick = {
                                onRunVoiceOrTextCommand("WiFi on karo aur brightness 50%")
                            },
                            accentColor = NeonPurple,
                            secondaryColor = NeonCyan,
                            modifier = Modifier.weight(1f),
                            testTag = "god_mode_wifi_btn"
                        )
                    }
                }
            }
        }

        // TAB 1: SMART HOME IOT & HEALTH
        AnimatedVisibility(visible = selectedTab == 1) {
            Column(modifier = Modifier.fillMaxWidth()) {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = AmberWarning
                ) {
                    Text(
                        text = "🏠 Smart Home / IoT",
                        style = MaterialTheme.typography.titleMedium,
                        color = AmberWarning
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    NeonToggleRow(
                        title = "💡 Smart Lights",
                        subtitle = if (smartHomeState.smartLightsOn) "ON" else "OFF",
                        checked = smartHomeState.smartLightsOn,
                        accentColor = AmberWarning,
                        onCheckedChange = { on ->
                            onRunVoiceOrTextCommand(if (on) "Smart lights on karo" else "Smart lights off karo")
                        }
                    )

                    NeonToggleRow(
                        title = "❄️ Smart AC (${smartHomeState.acTemperature}°C)",
                        subtitle = if (smartHomeState.acOn) "Cooling Active" else "OFF",
                        checked = smartHomeState.acOn,
                        accentColor = NeonCyan,
                        onCheckedChange = { on ->
                            onRunVoiceOrTextCommand(if (on) "AC on karo" else "AC off karo")
                        }
                    )

                    NeonSliderRow(
                        label = "🌡️ AC Temperature",
                        valueText = "${smartHomeState.acTemperature}°C",
                        value = smartHomeState.acTemperature.toFloat(),
                        valueRange = 16f..30f,
                        accentColor = NeonCyan,
                        onValueChange = { temp ->
                            onRunVoiceOrTextCommand("AC temperature set karo ${temp.toInt()}")
                        }
                    )

                    NeonToggleRow(
                        title = "📺 Smart TV",
                        subtitle = if (smartHomeState.smartTvOn) "ON" else "OFF",
                        checked = smartHomeState.smartTvOn,
                        accentColor = HotPink,
                        onCheckedChange = { on ->
                            onRunVoiceOrTextCommand(if (on) "TV on karo" else "TV off karo")
                        }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = NeonGreen
                ) {
                    Text(
                        text = "🏥 Health & Fitness",
                        style = MaterialTheme.typography.titleMedium,
                        color = NeonGreen
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TelemetryBadge("👟 Steps", "${healthState.stepCount}", NeonGreen)
                        TelemetryBadge("❤️ Heart Rate", "${healthState.heartRateBpm} BPM", HotPink)
                        TelemetryBadge("😴 Sleep", healthState.sleepHours, NeonCyan)
                        TelemetryBadge("💧 Water", "${healthState.waterGlasses} Glasses", SkyBlue)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        NeonButton(
                            text = "💧 +1 Water",
                            onClick = { onRunVoiceOrTextCommand("Water reminder set karo") },
                            accentColor = SkyBlue,
                            modifier = Modifier.weight(1f),
                            testTag = "health_water_btn"
                        )
                        NeonButton(
                            text = "⚖️ BMI (${healthState.lastBmiResult.substringBefore(" ")})",
                            onClick = { onRunVoiceOrTextCommand("BMI calculate karo") },
                            accentColor = NeonGreen,
                            modifier = Modifier.weight(1f),
                            testTag = "health_bmi_btn"
                        )
                    }
                }
            }
        }

        // TAB 2: MOOD SELECTOR
        AnimatedVisibility(visible = selectedTab == 2) {
            Column(modifier = Modifier.fillMaxWidth()) {
                val moods = JarvisMood.entries
                for (i in moods.indices step 2) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MoodGridCard(
                            mood = moods[i],
                            isSelected = moods[i] == selectedMood,
                            onClick = { onSelectMood(moods[i]) },
                            modifier = Modifier.weight(1f)
                        )
                        if (i + 1 < moods.size) {
                            MoodGridCard(
                                mood = moods[i + 1],
                                isSelected = moods[i + 1] == selectedMood,
                                onClick = { onSelectMood(moods[i + 1]) },
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(84.dp))
    }
}

@Composable
private fun MoodGridCard(
    mood: JarvisMood,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.03f else 1.0f,
        animationSpec = tween(180),
        label = "mood_card_scale"
    )
    val shape = RoundedCornerShape(20.dp)

    Column(
        modifier = modifier
            .scale(scale)
            .clip(shape)
            .background(if (isSelected) ElevatedGlass else SurfaceAlt)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) mood.accentColor else GlassBorderLight,
                shape = shape
            )
            .clickable { onClick() }
            .padding(14.dp)
            .testTag("mood_card_${mood.id}"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = mood.emoji,
            fontSize = 44.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = mood.title,
            style = MaterialTheme.typography.titleMedium,
            color = if (isSelected) mood.accentColor else TextPrimary,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = mood.subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
    }
}

@Composable
private fun TelemetryBadge(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}
