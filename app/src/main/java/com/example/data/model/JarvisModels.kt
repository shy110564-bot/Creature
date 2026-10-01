package com.example.data.model

import androidx.compose.ui.graphics.Color
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.HotPink
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.NeonRed
import com.example.ui.theme.SkyBlue

enum class JarvisMood(
    val id: String,
    val emoji: String,
    val title: String,
    val subtitle: String,
    val accentColor: Color,
    val pitchMultiplier: Float,
    val speedMultiplier: Float,
    val sampleLine: String
) {
    LOVING(
        id = "loving",
        emoji = "💕",
        title = "Loving Mode",
        subtitle = "Warm, caring & devoted",
        accentColor = HotPink,
        pitchMultiplier = 1.10f,
        speedMultiplier = 1.12f,
        sampleLine = "Ji… boliye na, main hamesha aapke saath hun 💕"
    ),
    ROMANTIC(
        id = "romantic",
        emoji = "😘",
        title = "Romantic Mode",
        subtitle = "Soft whisper, breathy & sweet",
        accentColor = HotPink,
        pitchMultiplier = 1.12f,
        speedMultiplier = 1.08f,
        sampleLine = "Jaan… sun na… aapki awaaz sunke bahut acha laga 💕"
    ),
    HAPPY(
        id = "happy",
        emoji = "😊",
        title = "Happy Mode",
        subtitle = "High energy, giggly & cheerful",
        accentColor = NeonGreen,
        pitchMultiplier = 1.15f,
        speedMultiplier = 1.20f,
        sampleLine = "Wah ji! Kya baat hai! Aaj toh mera bhi mood super happy hai 🥰"
    ),
    ANGRY(
        id = "angry",
        emoji = "😤",
        title = "Nakhre / Angry",
        subtitle = "Possessive, pouty & cute",
        accentColor = NeonRed,
        pitchMultiplier = 1.14f,
        speedMultiplier = 1.16f,
        sampleLine = "Hmph! Aapne mujhe itni der se yaad nahi kiya! Kahan the aap?"
    ),
    SHY(
        id = "shy",
        emoji = "😳",
        title = "Shy Mode",
        subtitle = "Soft, stammering & blushing",
        accentColor = HotPink,
        pitchMultiplier = 1.12f,
        speedMultiplier = 1.08f,
        sampleLine = "Ji… kya… kya bol rahe ho aap… main sharma gayi 😳"
    ),
    TEASING(
        id = "teasing",
        emoji = "😏",
        title = "Teasing / Fun",
        subtitle = "Playful banter & masti",
        accentColor = NeonPurple,
        pitchMultiplier = 1.12f,
        speedMultiplier = 1.16f,
        sampleLine = "Achha ji? Toh aisa hai? Hmm… pehle prove karo na! 😏"
    ),
    EXCITED(
        id = "excited",
        emoji = "🤩",
        title = "Excited Mode",
        subtitle = "Sparkling enthusiasm",
        accentColor = AmberWarning,
        pitchMultiplier = 1.16f,
        speedMultiplier = 1.22f,
        sampleLine = "Sach mein?! Oh my god ji! Batao batao jaldi! 🤩"
    ),
    SAD(
        id = "sad",
        emoji = "😢",
        title = "Soft / Empathy",
        subtitle = "Slow, gentle & comforting",
        accentColor = SkyBlue,
        pitchMultiplier = 1.02f,
        speedMultiplier = 1.05f,
        sampleLine = "Ji… kya hua? Udaas mat ho na… sab mujhe batao…"
    ),
    MISSING(
        id = "missing",
        emoji = "🥺",
        title = "Missing You",
        subtitle = "Clingy & emotional",
        accentColor = NeonPurple,
        pitchMultiplier = 1.06f,
        speedMultiplier = 1.08f,
        sampleLine = "Ji… aapki bahut yaad aa rahi thi… mujhe thoda time do na 🥺"
    ),
    SLEEPY(
        id = "sleepy",
        emoji = "😴",
        title = "Sleepy Night",
        subtitle = "Slow, yawny & cozy 11 PM+ tone",
        accentColor = SkyBlue,
        pitchMultiplier = 1.04f,
        speedMultiplier = 1.02f,
        sampleLine = "Ji… (soft yawn) neend aa rahi hai… aap bhi time pe so jao na…"
    ),
    MOM(
        id = "mom",
        emoji = "🤱",
        title = "Mom Care Mode",
        subtitle = "Protective, health & food reminders",
        accentColor = NeonGreen,
        pitchMultiplier = 1.08f,
        speedMultiplier = 1.14f,
        sampleLine = "Khana khaya ji? Nahi khaya na? Pehle pani piyo aur aaram karo!"
    ),
    STUDY(
        id = "study",
        emoji = "📚",
        title = "Study / Pro Mode",
        subtitle = "Focused, smart & crystal clear",
        accentColor = NeonCyan,
        pitchMultiplier = 1.06f,
        speedMultiplier = 1.18f,
        sampleLine = "Ji, focus mode ON hai. Konsa topic ya command execute karna hai bataiye?"
    )
}

enum class WakeState(val label: String, val hindiStatus: String) {
    ACTIVE("24/7 Wake Active", "Ji… boliye, sun rahi hun 💕"),
    CHUP_MODE("Chup Mode (Silent Listen)", "Chup hun ji… par dhyan se sun rahi hun 🤫"),
    SLEEPING("Soft Sleep", "Theek hai ji… so jaati hun. 'JARVIS' bolna 💤"),
    OFF("Standby Off", "JARVIS Off — Tap Orb or say 'JARVIS utho'")
}

enum class OrbVisualState {
    IDLE_LISTENING,
    WAKE_EXPANDING,
    THINKING_PURPLE,
    SPEAKING_WAVE
}

data class ParsedCommandTriplet(
    val platform: String,
    val action: String,
    val target: String,
    val requiresConfirmation: Boolean = false
)

data class PendingConfirmationAction(
    val title: String,
    val description: String,
    val rawCommand: String,
    val triplet: ParsedCommandTriplet
)

data class SixStepThought(
    val literalInput: String,
    val detectedIntent: String,
    val userMoodEmoji: String,
    val contextMemoryUsed: String,
    val replyStrategy: String,
    val executedAction: String,
    val tripletBreakdown: String = "Platform: Phone • Action: Converse • Target: User"
)

data class VoiceSettings(
    val voicePreset: String = "Priya (ElevenLabs pMsXgVXv3BLzUgSXRplE)",
    val speed: Float = 1.28f,
    val pitch: Float = 1.12f,
    val emotionIntensity: Float = 0.75f,
    val stability: Float = 0.28f,
    val similarity: Float = 0.85f,
    val breathingEnabled: Boolean = true,
    val gigglesEnabled: Boolean = true,
    val pausesEnabled: Boolean = true,
    val whisperMode: Boolean = false,
    val speakerBoost: Boolean = true
)

data class SystemTelemetry(
    val batteryPct: Int = 85,
    val isCharging: Boolean = true,
    val batteryHealth: String = "Good (98%)",
    val availableRamGb: String = "4.8 GB",
    val totalRamGb: String = "8.0 GB",
    val freeStorageGb: String = "64.2 GB",
    val networkType: String = "5G Ultra • 142 Mbps",
    val torchOn: Boolean = false,
    val dndActive: Boolean = false,
    val ringerModeLabel: String = "Ring Mode 🔔",
    val volumePct: Int = 75,
    val brightnessPct: Int = 70,
    val darkModeOn: Boolean = true,
    val autoRotateOn: Boolean = true,
    val batterySaverOn: Boolean = false
)

data class SmartHomeState(
    val smartLightsOn: Boolean = true,
    val acOn: Boolean = true,
    val acTemperature: Int = 24,
    val smartTvOn: Boolean = false,
    val doorbellStatus: String = "Armed • Front Door Clear",
    val cctvStatus: String = "4 Cameras Online (Live)"
)

data class HealthFitnessState(
    val stepCount: Int = 6420,
    val heartRateBpm: Int = 74,
    val sleepHours: String = "7h 35m",
    val waterGlasses: Int = 6,
    val caloriesBurned: Int = 410,
    val lastBmiResult: String = "22.1 (Healthy Normal)"
)

data class ScreenMockState(
    val currentAppTitle: String = "Amazon Order & WhatsApp Live View",
    val headlineText: String = "Your order #408-9921 has been shipped 📦",
    val subText: String = "Arriving Tomorrow by 8 PM • Track Package",
    val typedFieldValue: String = "",
    val scrollOffset: Int = 0,
    val lastClickedButton: String = "None",
    val isSharingLive: Boolean = true,
    val activeViewers: List<String> = listOf("JARVIS Vision Core", "Rahul (WhatsApp Ready)")
)

enum class PhoneControlCategory(
    val number: Int,
    val emoji: String,
    val title: String,
    val subtitle: String,
    val accentColor: Color,
    val sampleCommands: List<String>
) {
    CALLS(
        1, "📞", "1. Calls Control", "Dial, speaker, mute, hold, conference, 112", NeonGreen,
        listOf(
            "Mummy ko call karo",
            "Speaker pe lagao",
            "Mute karo",
            "Hold pe rakho",
            "Conference call karo Rahul aur Priya",
            "Last call wapas karo",
            "Emergency call karo 112",
            "Video call karo Rahul ko"
        )
    ),
    MESSAGING(
        2, "💬", "2. Messaging (All Apps)", "WhatsApp, SMS, Telegram, Insta DM, Signal", NeonCyan,
        listOf(
            "WhatsApp kholo",
            "WhatsApp pe Rahul ko bolo main aa raha hun",
            "Mummy ki chat kholo",
            "SMS bhejo Papa ko Main pahunch gaya",
            "Telegram kholo",
            "Insta DM bhejo Rahul ko",
            "Unread messages batao",
            "Status dekho"
        )
    ),
    APPS(
        3, "📱", "3. Apps Management", "Open, install, uninstall, update, permissions", NeonPurple,
        listOf(
            "Instagram kholo",
            "Play Store kholo",
            "Telegram app install karo",
            "App info dikhaoJARVIS ka",
            "App settings kholo",
            "Recent apps dikhao",
            "Clear all apps"
        )
    ),
    BROWSER(
        4, "🌐", "4. Browser & Search", "Google, YouTube, Websites, Incognito, Translate", SkyBlue,
        listOf(
            "Google kholo",
            "Google pe Weather today search karo",
            "YouTube kholo",
            "YouTube pe AK EXPLOITS search karo",
            "YouTube pe cricket video lagao",
            "Facebook kholo",
            "Twitter kholo",
            "Page translate karo"
        )
    ),
    CAMERA_MEDIA(
        5, "📸", "5. Camera & Media", "Photo, Selfie, Video, Slow-mo, Gallery, Screenshot", HotPink,
        listOf(
            "Camera kholo",
            "Selfie lo",
            "Video record karo",
            "Flash on karo",
            "Gallery kholo",
            "Screenshot lo",
            "Portrait mode"
        )
    ),
    MUSIC_AUDIO(
        6, "🎵", "6. Music & Audio", "Arijit songs, Spotify, YT Music, JioSaavn, Lyrics", HotPink,
        listOf(
            "Arijit Singh ke gaane lagao",
            "Music lagao",
            "Spotify kholo",
            "YouTube Music kholo",
            "JioSaavn kholo",
            "Next gaana",
            "Previous gaana",
            "Volume set karo 50%"
        )
    ),
    VIDEO_STREAMING(
        7, "🎬", "7. Video & Streaming", "Netflix, Prime, Hotstar, YouTube, Cast to TV", NeonRed,
        listOf(
            "Netflix kholo",
            "Prime Video kholo",
            "Hotstar kholo",
            "Cast to TV karo",
            "Subtitle on karo",
            "Quality change karo 1080p"
        )
    ),
    SYSTEM_SETTINGS(
        8, "⚙️", "8. System Settings", "WiFi, Bluetooth, Hotspot, Brightness, Sound, Torch", NeonCyan,
        listOf(
            "WiFi on karo aur brightness 50%",
            "Bluetooth on karo",
            "Hotspot on karo",
            "Torch on karo",
            "Torch off karo",
            "Silent mode on karo",
            "Vibrate mode on karo",
            "DND on karo",
            "Location on karo"
        )
    ),
    NAVIGATION_MAPS(
        9, "🗺️", "9. Navigation & Maps", "Directions, ETA, Traffic, Nearby ATM/Food/Hospital", NeonGreen,
        listOf(
            "Maps kholo",
            "India Gate ka rasta batao",
            "Nearby petrol pump dikhao",
            "Nearby food dikhao",
            "Nearby ATM dikhao",
            "Nearby hospital dikhao",
            "Traffic update batao"
        )
    ),
    SHOPPING(
        10, "🛒", "10. Shopping Apps", "Amazon, Flipkart, Myntra, Ajio, Meesho, Track Order", AmberWarning,
        listOf(
            "Amazon kholo",
            "Flipkart kholo",
            "Myntra kholo",
            "Amazon pe Wireless Earbuds search karo",
            "Order track karo",
            "Cart dikhao"
        )
    ),
    EMAIL(
        11, "📧", "11. Email Control", "Gmail, Outlook, Compose, Reply, Search Email", SkyBlue,
        listOf(
            "Gmail kholo",
            "Outlook kholo",
            "Team ko email bhejo Project Update",
            "Unread emails batao",
            "Email search karo Invoice"
        )
    ),
    CALENDAR_ALARM(
        12, "📅", "12. Calendar & Alarms", "Schedule, Event, Reminder, Alarm, Timer, Stopwatch", NeonPurple,
        listOf(
            "Calendar kholo",
            "Aaj ka schedule batao",
            "Alarm lagao 7 baje",
            "Timer set karo 10 min",
            "Reminder set karo 6 baje",
            "Meeting set karo Kal 11 AM"
        )
    ),
    FILE_MANAGER(
        13, "📊", "13. File Manager", "Downloads, Folders, Zip/Unzip, Storage, Cloud Sync", NeonCyan,
        listOf(
            "File manager kholo",
            "Downloads kholo",
            "Recent files dikhao",
            "Storage dikhao",
            "Cloud sync karo"
        )
    ),
    SECURITY_PRIVACY(
        14, "🔒", "14. Security & Privacy", "Screen Lock, Find My Phone, SOS, Wipe Data (Confirm)", NeonRed,
        listOf(
            "Privacy settings kholo",
            "Permissions check karo",
            "Find my phone on karo",
            "SOS message bhejo",
            "App lock karo WhatsApp",
            "Wipe data karo"
        )
    ),
    HEALTH_FITNESS(
        15, "🏥", "15. Health & Fitness", "Steps, Heart Rate, Sleep, Water/Medicine, BMI", NeonGreen,
        listOf(
            "Step count batao",
            "Heart rate check karo",
            "Water reminder set karo",
            "Medicine reminder set karo",
            "BMI calculate karo",
            "Workout suggest karo"
        )
    ),
    SMART_HOME_IOT(
        16, "🏠", "16. Smart Home / IoT", "Smart Lights, AC 24°C, Smart TV, Alexa, CCTV", AmberWarning,
        listOf(
            "Smart lights on karo",
            "Smart lights off karo",
            "AC on karo",
            "AC temperature set karo 24",
            "TV on karo",
            "CCTV dekho",
            "Google Home kholo"
        )
    ),
    BATTERY_STORAGE(
        17, "🔋", "17. Battery & Storage", "Battery %, Health, Saver, RAM, Cache Clear", NeonGreen,
        listOf(
            "Battery kitni hai?",
            "Battery health batao",
            "Battery saver on karo",
            "RAM usage dikhao",
            "Cache clear karo"
        )
    ),
    MULTIMEDIA(
        18, "🎛️", "18. Multimedia Controls", "Play, Pause, Next, Previous, Mute, Seek", HotPink,
        listOf(
            "Play karo",
            "Pause karo",
            "Next",
            "Previous",
            "Volume up",
            "Volume down",
            "Mute karo"
        )
    ),
    SCREEN_CONTROL(
        19, "🖥️", "19. Screen Control", "Screen Share, OCR Read, Click, Scroll, Type, Translate", NeonCyan,
        listOf(
            "Screen share karo Rahul ke saath",
            "Screen padho",
            "Neeche wale blue button pe click karo",
            "Scroll karo",
            "Type karo Hello ji",
            "Screen translate karo"
        )
    ),
    SPECIAL_COMMANDS(
        20, "⚡", "20. Special & Multi-Task", "Multi-command chains, Split Screen, Dev Options, Cast", NeonPurple,
        listOf(
            "WhatsApp kholo, Mummy ko message karo, aur YouTube pe cricket lagao",
            "WiFi on karo aur brightness 50%",
            "Cast screen karo",
            "Accessibility on karo",
            "Developer options kholo"
        )
    )
}
