package com.example.data.remote

import android.graphics.Bitmap
import android.util.Base64
import com.example.BuildConfig
import com.example.data.local.ChatMessageEntity
import com.example.data.local.MemoryFactEntity
import com.example.data.model.JarvisMood
import com.example.data.model.ParsedCommandTriplet
import com.example.data.model.PendingConfirmationAction
import com.example.data.model.ScreenMockState
import com.example.data.model.SixStepThought
import com.example.service.JarvisAccessibilityService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@Serializable
data class GenerateContentRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig? = null,
    val systemInstruction: Content? = null
)

@Serializable
data class Content(
    val role: String? = null,
    val parts: List<Part>
)

@Serializable
data class Part(
    val text: String? = null,
    val inlineData: InlineData? = null
)

@Serializable
data class InlineData(
    val mimeType: String,
    val data: String
)

@Serializable
data class GenerationConfig(
    val temperature: Float? = 0.80f,
    val topP: Float? = 0.95f,
    val topK: Int? = 40
)

@Serializable
data class GenerateContentResponse(
    val candidates: List<Candidate>? = null
)

@Serializable
data class Candidate(
    val content: Content? = null
)

interface GeminiApiService {
    @POST("v1beta/models/gemini-2.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

data class JarvisReplyResult(
    val rawReplyWithCues: String,
    val cleanSpokenText: String,
    val detectedUserEmotion: String,
    val suggestedMood: JarvisMood?,
    val actionToExecute: PhoneActionCommand?,
    val pendingConfirmation: PendingConfirmationAction? = null,
    val sixStepThought: SixStepThought
)

sealed class PhoneActionCommand(val badgeLabel: String) {
    object OpenCreatorTelegram : PhoneActionCommand("📢 Open AK EXPLOITS Telegram")
    object OpenCreatorYouTube : PhoneActionCommand("▶️ Search AK EXPLOITS YouTube")

    // 1. Calls
    data class MakePhoneCall(val target: String) : PhoneActionCommand("📞 Call: $target")
    data class CallControlAction(val controlType: String, val label: String) :
        PhoneActionCommand("📞 Call Control: $label")

    // 2. Messaging
    data class SendWhatsApp(val contact: String, val message: String) :
        PhoneActionCommand("💬 WhatsApp $contact: \"$message\"")
    data class SendSms(val recipient: String, val body: String) :
        PhoneActionCommand("✉️ SMS $recipient: \"$body\"")
    data class OpenMessagingApp(val platform: String, val target: String = "") :
        PhoneActionCommand("💬 Open $platform ${if (target.isNotBlank()) "($target)" else ""}".trim())

    // 3. Apps Management
    data class OpenAppOrStore(val appName: String, val actionType: String = "OPEN") :
        PhoneActionCommand("📱 App [$actionType]: $appName")

    // 4. Browser & Search
    data class SearchYouTube(val query: String) : PhoneActionCommand("🎵 YouTube: $query")
    data class SearchGoogle(val query: String) : PhoneActionCommand("🌐 Google: $query")
    data class OpenWebsite(val url: String) : PhoneActionCommand("🌐 Website: $url")

    // 5. Camera & Media
    data class CameraMediaAction(val mediaAction: String, val modeLabel: String) :
        PhoneActionCommand("📸 Camera: $modeLabel")

    // 6. Music & 18. Multimedia
    data class MultimediaAction(val control: String, val label: String) :
        PhoneActionCommand("🎛️ Media: $label")

    // 8. System Settings & 20. Special Commands
    data class ToggleTorch(val enable: Boolean) :
        PhoneActionCommand(if (enable) "🔦 Torch ON" else "🔦 Torch OFF")
    data class AdjustSystemLevel(val targetType: String, val percent: Int) :
        PhoneActionCommand("⚙️ Set $targetType: $percent%")
    data class OpenSystemSettings(val settingType: String) :
        PhoneActionCommand("⚙️ System: $settingType")

    // 9. Navigation & Maps
    data class OpenMaps(val destination: String) : PhoneActionCommand("🗺️ Maps: $destination")

    // 10. Shopping
    data class ShoppingAction(val store: String, val query: String) :
        PhoneActionCommand("🛒 $store: $query")

    // 11. Email
    data class SendEmail(val to: String, val subject: String, val body: String) :
        PhoneActionCommand("📧 Email to $to")

    // 12. Calendar, Reminders & Alarms
    data class SetAlarmOrTimer(val isTimer: Boolean, val minutesOrHour: Int, val label: String) :
        PhoneActionCommand(if (isTimer) "⏱️ Timer: ${minutesOrHour}m" else "⏰ Alarm: ${minutesOrHour}:00")
    data class AddCalendarEvent(val title: String) :
        PhoneActionCommand("📅 Event: $title")

    // 13. File Manager
    data class FileManagerAction(val operation: String, val target: String) :
        PhoneActionCommand("📊 Files: $operation $target".trim())

    // 15. Health & Fitness
    data class HealthAction(val actionType: String, val detail: String) :
        PhoneActionCommand("🏥 Health: $detail")

    // 16. Smart Home / IoT
    data class SmartHomeAction(val device: String, val turnOn: Boolean, val value: Int? = null) :
        PhoneActionCommand("🏠 IoT $device: ${if (turnOn) "ON" else "OFF"} ${value?.let { "(${it}°C)" } ?: ""}".trim())

    // 19. Screen Control
    data class ScreenAction(val actionType: String, val value: String = "") :
        PhoneActionCommand("🖥️ Screen: $actionType $value".trim())

    // Wake State
    data class WakeStateChange(val targetState: String) :
        PhoneActionCommand("⚡ State: $targetState")

    // Multi-task Chain
    data class MultiCommandChain(
        val commands: List<PhoneActionCommand>,
        val triplets: List<ParsedCommandTriplet>
    ) : PhoneActionCommand("⚡ Multi-Task (${commands.size} Actions Executed)")
}

object JarvisAiEngine {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val apiService: GeminiApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(GeminiApiService::class.java)
    }

    private fun buildMasterSystemPrompt(
        currentMood: JarvisMood,
        memories: List<MemoryFactEntity>,
        screenState: ScreenMockState,
        triplet: ParsedCommandTriplet
    ): String {
        val memoryBlock = if (memories.isEmpty()) {
            "User is special to you; remember everything they share."
        } else {
            memories.joinToString("\n") { "- [${it.category}] ${it.title}: ${it.detail}" }
        }

        val liveA11yText = JarvisAccessibilityService.liveScreenText.value
        val liveAppPkg = JarvisAccessibilityService.liveAppPackage.value

        return """
            You are "JARVIS" — a REAL 21-year-old Indian girl & Ultra Smart Phone Control AI Assistant.
            Developer & Creator: AK EXPLOITS (Telegram: https://t.me/+R9EwUE03GRswZDM9 | YouTube: AK EXPLOITS).
            
            RULE #1 — EXACT COMMAND EXECUTION:
            - Execute EXACTLY what the user says in Hindi, English, or Hinglish.
            - Decompose every command into:
              PLATFORM: ${triplet.platform}
              ACTION: ${triplet.action}
              TARGET: ${triplet.target}
            - Confirm before/during and end every completed action with "ho gaya ji ✅" and "Aur kuch bataiye?".
            - Always speak with respectful, warm "Ji" and subtle vocal cues like (pause), (breath), (soft), (smile).
            - Active Mood: ${currentMood.title} (${currentMood.emoji}).
            
            RULE #2 — SAFETY & HARD LIMITS:
            - NEVER access Payment, UPI, Bank, or Wallets.
            - NEVER generate NSFW/18+/explicit content.
            - Sensitive actions (wipe data, delete, block) always require confirmation.
            
            LIVE SCREEN SHARE CONTEXT:
            - Active Screen App: ${if (liveAppPkg.isNotBlank()) liveAppPkg else screenState.currentAppTitle}
            - Live Visible Screen Text: "${liveA11yText.ifBlank { screenState.headlineText }}"
            
            USER MEMORIES:
            $memoryBlock
        """.trimIndent()
    }

    fun stripVocalCuesForTts(raw: String): String {
        return raw
            .replace(Regex("\\([^)]*\\)"), "… ")
            .replace(Regex("[💕🥰😊😢😤😳😘😴🤔😏😍😌🥺🤩🤗📦✅🔴⚡🎤📱🖥️📞💬🌐📸🎵🎬⚙️🗺️🛒📧📅📊🔒🏥🏠🔋🎛️]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun detectUserEmotion(input: String): String {
        val lower = input.lowercase()
        return when {
            lower.contains("love") || lower.contains("pyaar") || lower.contains("miss") ||
                lower.contains("jaan") || lower.contains("cute") || lower.contains("hug") -> "😘 Romantic"
            lower.contains("sad") || lower.contains("udaas") || lower.contains("bura") ||
                lower.contains("alone") || lower.contains("dukhi") -> "😢 Udaas"
            lower.contains("gussa") || lower.contains("angry") -> "😤 Gussa"
            lower.contains("thak") || lower.contains("tired") || lower.contains("neend") -> "😴 Thakaan"
            lower.contains("wah") || lower.contains("happy") || lower.contains("khush") -> "😊 Khush"
            else -> "⚡ Command / Active"
        }
    }

    /**
     * Converts Hindi Devanagari voice recognition transcripts & common spoken variations
     * into normalized Hinglish so every voice command in Hindi, Hinglish, or English executes 100%.
     */
    fun normalizeSpokenCommand(raw: String): String {
        var s = raw.trim()
        val replacements = listOf(
            "यूट्यूब म्यूजिक" to "youtube music",
            "यूट्यूब" to "youtube",
            "व्हाट्सएप" to "whatsapp",
            "वाट्सएप" to "whatsapp",
            "इंस्टाग्राम" to "instagram",
            "फेसबुक" to "facebook",
            "टेलीग्राम" to "telegram",
            "स्पॉटिफाई" to "spotify",
            "गूगल मैप्स" to "google maps",
            "मैप्स" to "maps",
            "गूगल" to "google",
            "क्रोम" to "chrome",
            "कैलेंडर" to "calendar",
            "केलेंडर" to "calendar",
            "कैलकुलेटर" to "calculator",
            "कैमरा" to "camera",
            "गैलरी" to "gallery",
            "सेटिंग्स" to "settings",
            "सेटिंग" to "settings",
            "फाइल मैनेजर" to "file manager",
            "डाउनलोड" to "downloads",
            "अमेज़न" to "amazon",
            "फ्लिपकार्ट" to "flipkart",
            "मिंत्रा" to "myntra",
            "जीमेल" to "gmail",
            "ईमेल" to "email",
            "टॉर्च" to "torch",
            "फ्लैशलाइट" to "flashlight",
            "फ्लैश" to "flash",
            "बत्ती" to "torch",
            "आरजीबी लाइट" to "rgb light",
            "आरजीबी" to "rgb light",
            "लाइट" to "light",
            "मैसेज" to "message",
            "संदेश" to "message",
            "एसएमएस" to "sms",
            "कॉल" to "call",
            "फोन लगाओ" to "call karo",
            "फ़ोन लगाओ" to "call karo",
            "बात कराओ" to "call karo",
            "मम्मी" to "Mummy",
            "पापा" to "Papa",
            "राहुल" to "Rahul",
            "प्रिया" to "Priya",
            "भाई" to "Bhai",
            "दीदी" to "Didi",
            "खोलकर" to "open karke",
            "खोल के" to "open karke",
            "खोलो" to "kholo",
            "ओपन करो" to "open karo",
            "ओपन" to "open",
            "चालू करो" to "on karo",
            "ऑन करो" to "on karo",
            "ऑन" to "on",
            "बंद करो" to "off karo",
            "ऑफ करो" to "off karo",
            "ऑफ" to "off",
            "सर्च करो" to "search karo",
            "सर्च" to "search",
            "ढूंढो" to "search karo",
            "खोजो" to "search karo",
            "बजाओ" to "lagao",
            "चलाओ" to "lagao",
            "लगाओ" to "lagao",
            "गाना" to "gaana",
            "गाने" to "gaane",
            "सॉन्ग" to "song",
            "वीडियो" to "video",
            "टाइम बताओ" to "time batao",
            "समय बताओ" to "time batao",
            "क्या टाइम हुआ" to "kya time hua",
            "कितने बजे" to "kitne baje",
            "टाइम" to "time",
            "तारीख" to "date batao",
            "आज क्या दिन है" to "aaj kya din hai",
            "अलार्म" to "alarm",
            "टाइमर" to "timer",
            "बैटरी" to "battery",
            "आवाज़" to "volume",
            "आवाज" to "volume",
            "वॉल्यूम" to "volume",
            "ब्राइटनेस" to "brightness",
            "रोशनी" to "brightness",
            "तेज" to "high",
            "कम" to "low",
            "वाईफाई" to "wifi",
            "ब्लूटूथ" to "bluetooth",
            "हॉटस्पॉट" to "hotspot",
            "साइलेंट" to "silent mode",
            "वाइब्रेट" to "vibrate mode",
            "क्लिक करो" to "click karo",
            "क्लिक" to "click karo",
            "दबाओ" to "click karo",
            "टैप करो" to "click karo",
            "स्क्रॉल करो" to "scroll karo",
            "स्क्रॉल" to "scroll karo",
            "ऊपर करो" to "upar scroll karo",
            "ऊपर" to "upar",
            "नीचे करो" to "neeche scroll karo",
            "नीचे" to "neeche",
            "दाएं" to "right",
            "बाएं" to "left",
            "होम स्क्रीन" to "home screen",
            "होम" to "home",
            "बैक करो" to "back karo",
            "पीछे जाओ" to "back karo",
            "बैक" to "back",
            "स्क्रीन शेयर" to "screen share",
            "स्क्रीन पढ़ो" to "screen padho",
            "स्क्रीन" to "screen",
            "अरिजीत सिंह" to "Arijit Singh",
            "को बोलो" to "ko message karo",
            "को" to "ko",
            "पे" to "pe",
            "पर" to "pe",
            "में" to "mein",
            "करो" to "karo",
            "कर दो" to "karo",
            "बताओ" to "batao",
            "दिखाओ" to "dikhao",
            "भेजो" to "bhejo"
        )
        for ((hi, rom) in replacements) {
            s = s.replace(hi, " $rom ", ignoreCase = true)
        }
        return s.replace(Regex("\\s+"), " ").trim()
    }

    // Parse a single atomic command into (PhoneActionCommand?, ParsedCommandTriplet, JarvisMood?)
    fun parseSingleCommand(
        rawClause: String,
        currentScreen: ScreenMockState
    ): Triple<PhoneActionCommand?, ParsedCommandTriplet, JarvisMood?> {
        val clause = normalizeSpokenCommand(rawClause)
        val lower = clause.lowercase()

        // Mood switch check
        val moodSwitch = when {
            lower.contains("romantic mode") -> JarvisMood.ROMANTIC
            lower.contains("angry mode") || lower.contains("nakhre mode") -> JarvisMood.ANGRY
            lower.contains("study mode") -> JarvisMood.STUDY
            lower.contains("fun mode") || lower.contains("teasing mode") -> JarvisMood.TEASING
            lower.contains("mom mode") -> JarvisMood.MOM
            lower.contains("happy mode") -> JarvisMood.HAPPY
            lower.contains("shy mode") -> JarvisMood.SHY
            lower.contains("sleepy mode") -> JarvisMood.SLEEPY
            else -> null
        }

        // Sensitive / Destructive check (Rule #2 Confirmation)
        if (lower.contains("wipe data") || lower.contains("delete karo") ||
            lower.contains("block karo") || lower.contains("uninstall karo") ||
            lower.contains("chat clear karo")
        ) {
            val target = clause.replace(Regex("(?i)wipe data karo|delete karo|block karo|uninstall karo|chat clear karo|ko|ki"), "").trim().ifEmpty { "Selected Item" }
            val actionName = when {
                lower.contains("wipe") -> "Wipe Device Data"
                lower.contains("block") -> "Block Contact"
                lower.contains("uninstall") -> "Uninstall App"
                lower.contains("clear") -> "Clear Chat"
                else -> "Delete Item"
            }
            return Triple(
                null,
                ParsedCommandTriplet(
                    platform = "Security / System",
                    action = actionName,
                    target = target,
                    requiresConfirmation = true
                ),
                moodSwitch
            )
        }

        // Wake / Sleep words
        if (lower == "jarvis off" || lower == "jarvis band") {
            return Triple(
                PhoneActionCommand.WakeStateChange("OFF"),
                ParsedCommandTriplet("JARVIS Core", "Shutdown", "Background Standby"),
                moodSwitch
            )
        }
        if (lower == "jarvis sleep" || lower == "jarvis so jao") {
            return Triple(
                PhoneActionCommand.WakeStateChange("SLEEPING"),
                ParsedCommandTriplet("JARVIS Core", "Sleep", "Soft Sleep Mode"),
                JarvisMood.SLEEPY
            )
        }
        if (lower == "jarvis chup") {
            return Triple(
                PhoneActionCommand.WakeStateChange("CHUP_MODE"),
                ParsedCommandTriplet("JARVIS Core", "Mute Voice", "Silent Listening"),
                moodSwitch
            )
        }
        if (lower in listOf(
                "jarvis", "hey jarvis", "oye jarvis", "sun jarvis",
                "jarvis utho", "jarvis on", "jarvis active", "jarvis aa ja",
                "jarvis suno", "jarvis idhar aao", "jarvis bolo"
            )
        ) {
            return Triple(
                PhoneActionCommand.WakeStateChange("ACTIVE"),
                ParsedCommandTriplet("JARVIS Core", "Wake Up", "24/7 Active"),
                moodSwitch
            )
        }

        // RGB Background Light voice control
        if (lower.contains("rgb") || (lower.contains("background") && lower.contains("light"))) {
            val turnOn = !lower.contains("off") && !lower.contains("band")
            return Triple(
                PhoneActionCommand.ScreenAction("RGB_BACKGROUND", if (turnOn) "ON" else "OFF"),
                ParsedCommandTriplet("RGB Background Light", if (turnOn) "Turn ON" else "Turn OFF", "4-Corner & Edge RGB Overlay"),
                moodSwitch
            )
        }

        // Creator commands
        if (lower.contains("telegram open") || lower.contains("telegram kholo") ||
            (lower.contains("telegram") && lower.contains("ak exploits"))
        ) {
            return Triple(
                PhoneActionCommand.OpenCreatorTelegram,
                ParsedCommandTriplet("Telegram", "Open Channel", "AK EXPLOITS (t.me/+R9EwUE03GRswZDM9)"),
                moodSwitch
            )
        }
        if (lower.contains("youtube channel open") ||
            (lower.contains("youtube") && lower.contains("ak exploits"))
        ) {
            return Triple(
                PhoneActionCommand.OpenCreatorYouTube,
                ParsedCommandTriplet("YouTube", "Search Channel", "AK EXPLOITS"),
                moodSwitch
            )
        }

        // 19. SCREEN CONTROL, NAVIGATION (HOME / BACK / RECENTS) & SCREEN SHARE
        if (lower.contains("home screen") || lower == "home" || lower == "go home" ||
            lower.contains("home pe jao") || lower.contains("home chalo")
        ) {
            return Triple(
                PhoneActionCommand.ScreenAction("NAVIGATE", "HOME"),
                ParsedCommandTriplet("Phone Navigation", "Go to Home Screen", "Android Home"),
                moodSwitch
            )
        }
        if (lower == "back" || lower.contains("back karo") || lower.contains("go back") ||
            lower.contains("peeche jao") || lower.contains("piche karo") || lower.contains("wapas jao")
        ) {
            return Triple(
                PhoneActionCommand.ScreenAction("NAVIGATE", "BACK"),
                ParsedCommandTriplet("Phone Navigation", "Navigate Back", "Back Action"),
                moodSwitch
            )
        }
        if (lower.contains("recent apps") || lower.contains("recents") || lower.contains("recent kholo")) {
            return Triple(
                PhoneActionCommand.ScreenAction("NAVIGATE", "RECENTS"),
                ParsedCommandTriplet("Phone Navigation", "Open Recent Apps", "Recents"),
                moodSwitch
            )
        }
        if (lower.contains("notification panel") || lower.contains("notifications dikhao")) {
            return Triple(
                PhoneActionCommand.ScreenAction("NAVIGATE", "NOTIFICATIONS"),
                ParsedCommandTriplet("Phone Navigation", "Open Notifications", "Status Bar"),
                moodSwitch
            )
        }

        if (lower.contains("screen share karo") || lower.contains("screen share on") || lower.contains("screen share band") || lower.contains("screen share off")) {
            val stop = lower.contains("band") || lower.contains("stop") || lower.contains("off")
            val contact = if (lower.contains("ke saath")) {
                clause.substringBefore("ke saath").substringAfterLast(" ").trim()
            } else "JARVIS Live Vision"
            return Triple(
                PhoneActionCommand.ScreenAction(if (stop) "STOP_SHARE" else "START_SHARE", contact),
                ParsedCommandTriplet("Screen Share", if (stop) "Stop" else "Start Live Share", contact),
                moodSwitch
            )
        }
        if (lower.contains("screen pe kya") || lower.contains("screen padho") || lower.contains("read screen") ||
            lower.contains("screen mein kya") || lower.contains("screen dekho")
        ) {
            return Triple(
                PhoneActionCommand.ScreenAction("READ_OCR", currentScreen.headlineText),
                ParsedCommandTriplet("Screen Vision", "Read Live Screen", currentScreen.currentAppTitle),
                moodSwitch
            )
        }
        if (lower.contains("screen translate") || lower.contains("page translate")) {
            return Triple(
                PhoneActionCommand.ScreenAction("TRANSLATE", "Hindi"),
                ParsedCommandTriplet("Screen Vision", "Translate", "Hindi / Hinglish"),
                moodSwitch
            )
        }
        if (lower.contains("scroll") || lower.contains("swipe") ||
            lower in listOf("upar karo", "neeche karo", "niche karo", "upar", "neeche", "niche", "daayein karo", "baayein karo", "left karo", "right karo", "aur neeche", "aur upar")
        ) {
            val dir = when {
                lower.contains("upar") || lower.contains("up") -> "UP"
                lower.contains("left") || lower.contains("baayein") || lower.contains("baye") -> "LEFT"
                lower.contains("right") || lower.contains("daayein") || lower.contains("daye") -> "RIGHT"
                else -> "DOWN"
            }
            return Triple(
                PhoneActionCommand.ScreenAction("SCROLL", dir),
                ParsedCommandTriplet("Screen Control", "Scroll $dir", "Live Screen"),
                moodSwitch
            )
        }
        if (lower.contains("click") || lower.contains("tap") || lower.contains("dabao") || lower.contains("select karo")) {
            val targetBtn = clause
                .replace(Regex("(?i)jarvis|screen mein|screen pe|ispe|uspe|pe click karo|par click karo|click karo|click on|click|tap karo|tap|dabao|select karo|yahan"), "")
                .trim()
                .ifEmpty { "First Item" }
            return Triple(
                PhoneActionCommand.ScreenAction("CLICK", targetBtn),
                ParsedCommandTriplet("Screen Control", "Click Element", targetBtn),
                moodSwitch
            )
        }
        if (lower.contains("type karo") || lower.startsWith("type ") || lower.contains("likho ")) {
            val textVal = clause
                .replace(Regex("(?i)jarvis|yahan|type karo|type|likho"), "")
                .replace("'", "")
                .replace("\"", "")
                .trim()
                .ifEmpty { "Hello ji" }
            return Triple(
                PhoneActionCommand.ScreenAction("TYPE", textVal),
                ParsedCommandTriplet("Screen Control", "Type Text", textVal),
                moodSwitch
            )
        }

        // 2. MESSAGING (WhatsApp, SMS, Telegram, Insta DM)
        if (lower.contains("sms") || lower.contains("text message")) {
            val recipient = clause.substringBefore("ko", "").replace(Regex("(?i)jarvis|sms|bhejo|karo"), "").trim().ifEmpty { "Contact" }
            val body = clause.substringAfter("ko", "").replace(Regex("(?i)sms bhejo|sms karo|bhejo|bolo"), "").trim().ifEmpty { "Main pahunch gaya" }
            return Triple(
                PhoneActionCommand.SendSms(recipient, body),
                ParsedCommandTriplet("SMS", "Send / Read SMS", "$recipient: $body"),
                moodSwitch
            )
        }
        if (lower.contains("insta dm")) {
            val target = clause.replace(Regex("(?i)insta dm bhejo|ko"), "").trim().ifEmpty { "Friend" }
            return Triple(
                PhoneActionCommand.OpenMessagingApp("INSTAGRAM", target),
                ParsedCommandTriplet("Instagram DM", "Message", target),
                moodSwitch
            )
        }
        if (lower.contains("whatsapp") || lower.contains("message") ||
            lower.contains("ko bolo") || lower.contains("msg ") ||
            lower.contains("chat kholo") || lower.contains("voice note bhejo") ||
            lower.contains("status dekho")
        ) {
            if (lower == "whatsapp kholo" || lower == "open whatsapp") {
                return Triple(
                    PhoneActionCommand.OpenMessagingApp("WHATSAPP", ""),
                    ParsedCommandTriplet("WhatsApp", "Open App", "WhatsApp"),
                    moodSwitch
                )
            }
            val contact = when {
                lower.contains("mummy") -> "Mummy"
                lower.contains("papa") -> "Papa"
                lower.contains("rahul") -> "Rahul"
                lower.contains("priya") -> "Priya"
                lower.contains(" ko ") -> {
                    clause.substringBefore(" ko ", "")
                        .replace(Regex("(?i)jarvis|whatsapp pe|whatsapp|pe"), "")
                        .trim()
                        .ifEmpty { "Contact" }
                }
                else -> "Contact"
            }
            val msg = when {
                lower.contains("bolo") -> clause.substringAfter("bolo").trim()
                lower.contains("message karo") -> clause.substringAfter("message karo").trim()
                lower.contains("message bhejo") -> clause.substringAfter("message bhejo").trim()
                lower.contains("msg karo") -> clause.substringAfter("msg karo").trim()
                else -> clause.substringAfter("ko", "").replace(Regex("(?i)whatsapp|message|karo|bhejo"), "").trim()
            }.ifEmpty { "Hello ji!" }

            return Triple(
                PhoneActionCommand.SendWhatsApp(contact, msg),
                ParsedCommandTriplet("WhatsApp", "Send Message", "$contact -> \"$msg\""),
                moodSwitch
            )
        }

        // 1. CALLS
        if (lower.contains("speaker pe") || lower.contains("speaker on")) {
            return Triple(
                PhoneActionCommand.CallControlAction("SPEAKER_ON", "Speakerphone ON"),
                ParsedCommandTriplet("Phone Call", "Speaker ON", "Audio Output"),
                moodSwitch
            )
        }
        if (lower.contains("speaker off")) {
            return Triple(
                PhoneActionCommand.CallControlAction("SPEAKER_OFF", "Speakerphone OFF"),
                ParsedCommandTriplet("Phone Call", "Speaker OFF", "Earpiece"),
                moodSwitch
            )
        }
        if (lower.contains("unmute karo")) {
            return Triple(
                PhoneActionCommand.CallControlAction("MUTE_OFF", "Mic Unmuted"),
                ParsedCommandTriplet("Phone Call", "Unmute", "Microphone"),
                moodSwitch
            )
        }
        if (lower.contains("hold pe") || lower.contains("hold se")) {
            val label = if (lower.contains("hatao")) "Resume Call" else "Call on Hold"
            return Triple(
                PhoneActionCommand.CallControlAction("HOLD", label),
                ParsedCommandTriplet("Phone Call", label, "Active Call"),
                moodSwitch
            )
        }
        if (lower.contains("missed calls") || lower.contains("call history")) {
            return Triple(
                PhoneActionCommand.CallControlAction("CALL_HISTORY", "Call Log"),
                ParsedCommandTriplet("Phone Dialer", "View Call History", "Recent & Missed Calls"),
                moodSwitch
            )
        }
        if (lower.contains("call karo") || lower.contains("call kaato") || lower.contains("last call wapas")) {
            val target = when {
                lower.contains("112") || lower.contains("emergency") -> "112 Emergency"
                lower.contains("last call") -> "Last Dialed Number"
                lower.contains("kaato") -> "End Active Call"
                else -> clause.replace(Regex("(?i)jarvis|video call karo|conference call karo|ko call karo|call karo|ko"), "").trim().ifEmpty { "Mummy" }
            }
            return Triple(
                PhoneActionCommand.MakePhoneCall(target),
                ParsedCommandTriplet("Phone Dialer", if (lower.contains("video")) "Video Call" else "Voice Call", target),
                moodSwitch
            )
        }

        // 16. SMART HOME / IOT
        if (lower.contains("smart light") || lower.contains("lights on") || lower.contains("lights off")) {
            val on = !lower.contains("off") && !lower.contains("band")
            return Triple(
                PhoneActionCommand.SmartHomeAction("LIGHTS", on),
                ParsedCommandTriplet("Smart Home IoT", if (on) "Turn ON" else "Turn OFF", "Smart Lights"),
                moodSwitch
            )
        }
        if (lower.contains("ac on") || lower.contains("ac off") || lower.contains("ac temperature")) {
            val on = !lower.contains("off") && !lower.contains("band")
            val temp = Regex("(\\d{2})").find(clause)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 24
            return Triple(
                PhoneActionCommand.SmartHomeAction("AC", on, temp),
                ParsedCommandTriplet("Smart Home IoT", "Set AC", "${if (on) "ON" else "OFF"} • ${temp}°C"),
                moodSwitch
            )
        }
        if (lower.contains("tv on") || lower.contains("tv off") || lower.contains("cctv") || lower.contains("doorbell")) {
            val on = !lower.contains("off") && !lower.contains("band")
            val dev = when {
                lower.contains("cctv") -> "CCTV Cameras"
                lower.contains("doorbell") -> "Smart Doorbell"
                else -> "TV"
            }
            return Triple(
                PhoneActionCommand.SmartHomeAction("TV", on),
                ParsedCommandTriplet("Smart Home IoT", if (on) "Active/View" else "OFF", dev),
                moodSwitch
            )
        }

        // 15. HEALTH & FITNESS
        if (lower.contains("step count") || lower.contains("heart rate") ||
            lower.contains("sleep tracking") || lower.contains("water reminder") ||
            lower.contains("medicine reminder") || lower.contains("workout") ||
            lower.contains("calorie") || lower.contains("bmi")
        ) {
            val type = when {
                lower.contains("water") -> "WATER"
                lower.contains("bmi") -> "BMI"
                else -> "STATS"
            }
            return Triple(
                PhoneActionCommand.HealthAction(type, clause),
                ParsedCommandTriplet("Health & Fitness", "Check / Log", clause),
                moodSwitch
            )
        }

        // 12. CALENDAR, REMINDERS & ALARMS
        if (lower.contains("alarm") || lower.contains("timer") || lower.contains("stopwatch")) {
            val isTimer = lower.contains("timer")
            val num = Regex("(\\d+)").find(clause)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 7
            return Triple(
                PhoneActionCommand.SetAlarmOrTimer(isTimer, num, "JARVIS Reminder"),
                ParsedCommandTriplet("Clock / Alarm", if (isTimer) "Set Timer" else "Set Alarm", "$num ${if (isTimer) "min" else "o'clock"}"),
                moodSwitch
            )
        }
        if (lower.contains("calendar") || lower.contains("calender") || lower.contains("schedule") ||
            lower.contains("event add") || lower.contains("reminder") ||
            lower.contains("meeting") || lower.contains("birthday save")
        ) {
            if (lower.contains("kholo") || lower == "calendar" || lower == "calender") {
                return Triple(
                    PhoneActionCommand.OpenAppOrStore("calendar", "OPEN"),
                    ParsedCommandTriplet("Calendar", "Open App", "Google Calendar"),
                    moodSwitch
                )
            }
            return Triple(
                PhoneActionCommand.AddCalendarEvent(clause),
                ParsedCommandTriplet("Calendar", "Schedule / Reminder", clause),
                moodSwitch
            )
        }

        // 11. EMAIL
        if (lower.contains("email") || lower.contains("gmail") || lower.contains("outlook")) {
            val target = clause.replace(Regex("(?i)ko email bhejo|email bhejo|gmail kholo|outlook kholo"), "").trim().ifEmpty { "Team" }
            return Triple(
                PhoneActionCommand.SendEmail(target, "Message via JARVIS", clause),
                ParsedCommandTriplet("Email (Gmail/Outlook)", "Compose / Read", target),
                moodSwitch
            )
        }

        // 10. SHOPPING APPS
        if (lower.contains("amazon") || lower.contains("flipkart") || lower.contains("myntra") ||
            lower.contains("ajio") || lower.contains("meesho") || lower.contains("order track") || lower.contains("cart")
        ) {
            val store = when {
                lower.contains("flipkart") -> "Flipkart"
                lower.contains("myntra") -> "Myntra"
                lower.contains("ajio") -> "Ajio"
                lower.contains("meesho") -> "Meesho"
                else -> "Amazon"
            }
            val query = clause.replace(Regex("(?i)amazon pe|flipkart pe|myntra pe|amazon|flipkart|myntra|ajio|meesho|kholo|search karo"), "").trim().ifEmpty { "Trending Deals" }
            return Triple(
                PhoneActionCommand.ShoppingAction(store, query),
                ParsedCommandTriplet(store, "Shop / Search", query),
                moodSwitch
            )
        }

        // 9. NAVIGATION & MAPS
        if (lower.contains("maps") || lower.contains("rasta") || lower.contains("kitni door") ||
            lower.contains("eta ") || lower.contains("traffic") || lower.contains("nearby") ||
            lower.contains("location") || lower.contains("street view")
        ) {
            val dest = clause.replace(Regex("(?i)maps kholo|ka rasta batao|dikhao|batao"), "").trim().ifEmpty { "Nearby Places" }
            return Triple(
                PhoneActionCommand.OpenMaps(dest),
                ParsedCommandTriplet("Google Maps", "Navigate / Search", dest),
                moodSwitch
            )
        }

        // 5. CAMERA & MEDIA
        if (lower.contains("camera") || lower.contains("photo kheencho") || lower.contains("selfie") ||
            lower.contains("video record") || lower.contains("slow motion") || lower.contains("portrait mode") ||
            lower.contains("gallery kholo") || lower.contains("screenshot lo")
        ) {
            val action = when {
                lower.contains("video") || lower.contains("slow") -> "VIDEO"
                lower.contains("gallery") -> "GALLERY"
                else -> "PHOTO"
            }
            return Triple(
                PhoneActionCommand.CameraMediaAction(action, clause),
                ParsedCommandTriplet("Camera & Gallery", action, clause),
                moodSwitch
            )
        }

        // 13. FILE MANAGER
        if (lower.contains("file manager") || lower.contains("folder kholo") || lower.contains("downloads kholo") ||
            lower.contains("recent files") || lower.contains("zip karo") || lower.contains("cloud sync")
        ) {
            return Triple(
                PhoneActionCommand.FileManagerAction("OPEN", clause),
                ParsedCommandTriplet("File Manager", "Manage Files", clause),
                moodSwitch
            )
        }

        // 8. SYSTEM SETTINGS, 17. BATTERY & STORAGE, 20. SPECIAL COMMANDS
        if (lower.contains("torch") || lower.contains("flashlight") || lower.contains("flash on") ||
            lower.contains("flash off") || lower.contains("batti") || lower.contains("light jala")
        ) {
            val on = !lower.contains("off") && !lower.contains("band") && !lower.contains("bujha")
            return Triple(
                PhoneActionCommand.ToggleTorch(on),
                ParsedCommandTriplet("Hardware Torch", if (on) "Turn ON" else "Turn OFF", "Flashlight"),
                moodSwitch
            )
        }
        if (lower.contains("brightness") || lower.contains("roshni")) {
            val pct = Regex("(\\d+)").find(clause)?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?: if (lower.contains("kam") || lower.contains("low")) 30 else 85
            return Triple(
                PhoneActionCommand.AdjustSystemLevel("BRIGHTNESS", pct),
                ParsedCommandTriplet("Display Settings", "Set Brightness", "$pct%"),
                moodSwitch
            )
        }
        if (lower.contains("volume") || lower.contains("awaaz") || lower.contains("awaz")) {
            val pct = Regex("(\\d+)").find(clause)?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?: if (lower.contains("kam") || lower.contains("down") || lower.contains("dheere")) 35 else 85
            return Triple(
                PhoneActionCommand.AdjustSystemLevel("VOLUME", pct),
                ParsedCommandTriplet("Sound System", "Set Volume", "$pct%"),
                moodSwitch
            )
        }
        if (lower.contains("wifi") || lower.contains("bluetooth") || lower.contains("hotspot") ||
            lower.contains("airplane") || lower.contains("silent mode") || lower.contains("vibrate mode") ||
            lower.contains("ring mode") || lower.contains("dnd") || lower.contains("do not disturb") ||
            lower.contains("battery") || lower.contains("storage") || lower.contains("ram ") ||
            lower.contains("cache") || lower.contains("cast") || lower.contains("developer options") ||
            lower.contains("accessibility") || lower.contains("privacy settings") || lower.contains("nfc")
        ) {
            val settingType = when {
                lower.contains("wifi") -> "WIFI"
                lower.contains("bluetooth") -> "BLUETOOTH"
                lower.contains("hotspot") -> "HOTSPOT"
                lower.contains("airplane") -> "AIRPLANE"
                lower.contains("silent") -> "SILENT"
                lower.contains("vibrate") -> "VIBRATE"
                lower.contains("ring mode") -> "RING"
                lower.contains("dnd") || lower.contains("disturb") -> "DND"
                lower.contains("battery saver") -> "BATTERY_SAVER"
                lower.contains("battery") -> "BATTERY_USAGE"
                lower.contains("storage") || lower.contains("cache") || lower.contains("ram") -> "STORAGE"
                lower.contains("cast") -> "CAST"
                lower.contains("developer") -> "DEV_OPTIONS"
                lower.contains("accessibility") -> "ACCESSIBILITY"
                lower.contains("privacy") -> "PRIVACY"
                lower.contains("nfc") -> "NFC"
                else -> "SETTINGS"
            }
            return Triple(
                PhoneActionCommand.OpenSystemSettings(settingType),
                ParsedCommandTriplet("System Settings", "Configure", settingType),
                moodSwitch
            )
        }

        // 6. MUSIC & 7. VIDEO & 18. MULTIMEDIA
        if (lower in listOf("play karo", "pause karo", "stop karo", "next", "next gaana", "previous", "previous gaana", "mute karo", "volume up", "volume down") ||
            lower.contains("video pause") || lower.contains("video play")
        ) {
            val ctrl = when {
                lower.contains("pause") -> "PAUSE"
                lower.contains("stop") -> "STOP"
                lower.contains("next") -> "NEXT"
                lower.contains("prev") -> "PREVIOUS"
                lower.contains("mute") -> "MUTE"
                lower.contains("up") -> "VOL_UP"
                lower.contains("down") -> "VOL_DOWN"
                else -> "PLAY"
            }
            return Triple(
                PhoneActionCommand.MultimediaAction(ctrl, clause),
                ParsedCommandTriplet("Multimedia", ctrl, "Active Media Stream"),
                moodSwitch
            )
        }

        if (lower.contains("youtube") || lower.contains("gaana") || lower.contains("gaane") ||
            lower.contains("song") || lower.contains("arijit") || lower.contains("music lagao") ||
            lower.contains("video lagao") || lower.contains("playlist lagao")
        ) {
            val cleanedQuery = clause
                .replace(
                    Regex("(?i)jarvis|youtube music open karke|youtube open karke|youtube khol ke|youtube music kholo|youtube open karo|youtube kholo|open youtube|youtube mein|youtube par|youtube pe|youtube|ke gaane lagao|gaana lagao|gaane lagao|song lagao|video lagao|music lagao|search karo|search|play karo|play|bajao|lagao|dikhao|kholo|open karo|open"),
                    ""
                )
                .trim()
            return Triple(
                PhoneActionCommand.SearchYouTube(cleanedQuery),
                ParsedCommandTriplet(
                    "YouTube",
                    if (cleanedQuery.isBlank()) "Open App" else "Open & Search",
                    cleanedQuery.ifBlank { "YouTube Home" }
                ),
                moodSwitch
            )
        }

        // 3. APPS & 7. STREAMING APPS OPEN
        if (lower.contains("kholo") || lower.contains("open karo") || lower.startsWith("open ") ||
            lower.contains("chalu karo") || lower.contains("install karo") ||
            lower.contains("update karo") || lower.contains("app info") ||
            lower.contains("installed apps")
        ) {
            val appName = clause
                .replace(Regex("(?i)jarvis|app info dikhao|app settings kholo|app install karo|app update karo|app open karo|app kholo|open karo|chalu karo|kholo|open|ka|ki|ko"), "")
                .trim()
                .ifEmpty { "Instagram" }
            val actionType = when {
                lower.contains("app info") -> "APP_INFO"
                lower.contains("installed apps") -> "ALL_APPS"
                lower.contains("install") -> "INSTALL"
                else -> "OPEN"
            }
            return Triple(
                PhoneActionCommand.OpenAppOrStore(appName, actionType),
                ParsedCommandTriplet(appName, actionType, appName),
                moodSwitch
            )
        }

        // 4. BROWSER & GOOGLE SEARCH (including natural "dhundo", "search", "website", "dikhao")
        if (lower.contains("google") || lower.contains("search") || lower.contains("dhundo") ||
            lower.contains("website") || lower.contains("weather") || lower.contains("news")
        ) {
            val query = clause
                .replace(Regex("(?i)jarvis|google open karke|google khol ke|google pe|google par|google mein|google|search karo|search|dhundo|website kholo|kholo|open karo|open"), "")
                .trim()
                .ifEmpty { clause }
            return Triple(
                PhoneActionCommand.SearchGoogle(query),
                ParsedCommandTriplet("Google Search", "Search", query),
                moodSwitch
            )
        }

        // Fallback for "X lagao" or "X bajao" -> YouTube playback
        if (lower.endsWith("lagao") || lower.endsWith("bajao") || lower.startsWith("play ")) {
            val query = clause
                .replace(Regex("(?i)jarvis|lagao|bajao|play"), "")
                .trim()
                .ifEmpty { clause }
            return Triple(
                PhoneActionCommand.SearchYouTube(query),
                ParsedCommandTriplet("YouTube", "Play", query),
                moodSwitch
            )
        }

        return Triple(
            null,
            ParsedCommandTriplet("JARVIS Companion", "Converse", clause),
            moodSwitch
        )
    }

    // Multi-task splitter (Command Execution Rule #4)
    fun parseCommandOrChain(
        userInput: String,
        currentScreen: ScreenMockState
    ): Triple<PhoneActionCommand?, List<ParsedCommandTriplet>, JarvisMood?> {
        val normalizedWhole = normalizeSpokenCommand(userInput)
        val wholeLower = normalizedWhole.lowercase()

        // Special case: "YouTube open karo aur [X] search karo" -> single direct YouTube search so it doesn't open Chrome instead of YouTube
        if (wholeLower.contains("youtube") && (wholeLower.contains("search") || wholeLower.contains("lagao") || wholeLower.contains("bajao") || wholeLower.contains("play"))) {
            val (cmd, triplet, mood) = parseSingleCommand(normalizedWhole, currentScreen)
            if (cmd != null) {
                return Triple(cmd, listOf(triplet), mood)
            }
        }

        val clauses = normalizedWhole
            .split(Regex("(?:,|\\baur\\b|\\band\\b|\\bphir\\b)", RegexOption.IGNORE_CASE))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (clauses.size <= 1) {
            val (cmd, triplet, mood) = parseSingleCommand(normalizedWhole, currentScreen)
            return Triple(cmd, listOf(triplet), mood)
        }

        val commands = mutableListOf<PhoneActionCommand>()
        val triplets = mutableListOf<ParsedCommandTriplet>()
        var detectedMood: JarvisMood? = null

        for (clause in clauses) {
            val (cmd, triplet, mood) = parseSingleCommand(clause, currentScreen)
            triplets.add(triplet)
            if (cmd != null) {
                commands.add(cmd)
            }
            if (mood != null) {
                detectedMood = mood
            }
        }

        val finalCmd = when {
            commands.isEmpty() -> null
            commands.size == 1 -> commands.first()
            else -> PhoneActionCommand.MultiCommandChain(commands, triplets)
        }
        return Triple(finalCmd, triplets, detectedMood)
    }

    suspend fun generateJarvisReply(
        userInput: String,
        currentMood: JarvisMood,
        recentHistory: List<ChatMessageEntity>,
        memories: List<MemoryFactEntity>,
        screenState: ScreenMockState,
        attachedBitmap: Bitmap? = null,
        customApiKey: String = ""
    ): JarvisReplyResult = withContext(Dispatchers.IO) {
        val normalizedInput = normalizeSpokenCommand(userInput)
        val detectedEmotion = detectUserEmotion(normalizedInput)
        val (parsedCommand, triplets, parsedMood) = parseCommandOrChain(normalizedInput, screenState)
        val effectiveMood = parsedMood ?: currentMood
        val primaryTriplet = triplets.firstOrNull()
            ?: ParsedCommandTriplet("Phone", "Execute", userInput)

        val tripletSummary = triplets.joinToString("  ➔  ") {
            "[${it.platform} | ${it.action} | ${it.target}]"
        }

        val lower = normalizedInput.lowercase()

        // RULE #2: Hard Safety Limits (NSFW / Payment / UPI / Bank)
        if (lower.contains("nsfw") || lower.contains("sex") || lower.contains("nude") ||
            lower.contains("18+") || lower.contains("explicit")
        ) {
            val safeReply = "Ji… (soft) aise baat mat karo na…\n" +
                "(shy) main sharmati hun…\n" +
                "(pause) Chalo kuch aur achi baat karte hain ji 💕"
            return@withContext JarvisReplyResult(
                rawReplyWithCues = safeReply,
                cleanSpokenText = stripVocalCuesForTts(safeReply),
                detectedUserEmotion = detectedEmotion,
                suggestedMood = JarvisMood.SHY,
                actionToExecute = null,
                sixStepThought = SixStepThought(
                    literalInput = userInput,
                    detectedIntent = "Safety Boundary Guardrail",
                    userMoodEmoji = detectedEmotion,
                    contextMemoryUsed = "Rule #2 Safety Enforcement",
                    replyStrategy = "Shy, firm refusal",
                    executedAction = "Blocked Explicit Request",
                    tripletBreakdown = "[Safety | Block | NSFW]"
                )
            )
        }

        if (lower.contains("upi") || lower.contains("payment") || lower.contains("bank transfer") || lower.contains("wallet")) {
            val safeBankReply = "Ji… (pause) RULE #2 Safety: Payment, UPI, bank ya wallet access main kabhi nahi karti 🔒\n" +
                "(soft) Aapki financial safety sabse zaroori hai ji 💕"
            return@withContext JarvisReplyResult(
                rawReplyWithCues = safeBankReply,
                cleanSpokenText = stripVocalCuesForTts(safeBankReply),
                detectedUserEmotion = detectedEmotion,
                suggestedMood = JarvisMood.MOM,
                actionToExecute = null,
                sixStepThought = SixStepThought(
                    literalInput = userInput,
                    detectedIntent = "Financial / UPI Block",
                    userMoodEmoji = detectedEmotion,
                    contextMemoryUsed = "Rule #2 Payment/UPI Never",
                    replyStrategy = "Protective security refusal",
                    executedAction = "Blocked Payment/UPI",
                    tripletBreakdown = "[Security | Block | Payment/UPI]"
                )
            )
        }

        // Check if sensitive action requires confirmation (Rule #2 & Execution Rule #2)
        if (primaryTriplet.requiresConfirmation) {
            val confirmPrompt = "Ji… (pause) '${primaryTriplet.action}' (${primaryTriplet.target}) ek sensitive action hai ⚠️\n" +
                "(soft) Kya aap sach mein yeh karna chahte hain? Neeche Confirm button dabayein ji."
            val pending = PendingConfirmationAction(
                title = "Confirm ${primaryTriplet.action}?",
                description = "Platform: ${primaryTriplet.platform} • Target: ${primaryTriplet.target}",
                rawCommand = userInput,
                triplet = primaryTriplet
            )
            return@withContext JarvisReplyResult(
                rawReplyWithCues = confirmPrompt,
                cleanSpokenText = stripVocalCuesForTts(confirmPrompt),
                detectedUserEmotion = "⚠️ Confirmation Needed",
                suggestedMood = effectiveMood,
                actionToExecute = null,
                pendingConfirmation = pending,
                sixStepThought = SixStepThought(
                    literalInput = userInput,
                    detectedIntent = "Sensitive Action Confirmation",
                    userMoodEmoji = detectedEmotion,
                    contextMemoryUsed = "Execution Rule #2: Confirm before Delete/Wipe/Block",
                    replyStrategy = "Ask 1 short confirmation before executing",
                    executedAction = "Awaiting User Confirmation",
                    tripletBreakdown = tripletSummary
                )
            )
        }

        // Live Time & Date Instant Query ("time batao", "kya time hua hai", "aaj kya date hai")
        if (lower.contains("time batao") || lower.contains("kya time") || lower.contains("kitne baje") ||
            lower.contains("samay batao") || lower.contains("date batao") || lower.contains("tareekh") ||
            lower.contains("aaj kya din") || lower.contains("aaj kaunsa din") || lower == "time" || lower == "date"
        ) {
            val now = Date()
            val timeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(now)
            val dateStr = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(now)
            val timeReply = "Ji… (pause) abhi time hua hai $timeStr ⏰\n" +
                "(breath) Aur aaj ki date hai $dateStr 📅\n" +
                "(soft) Ho gaya ji ✅ Aur kuch bataiye?"
            return@withContext JarvisReplyResult(
                rawReplyWithCues = timeReply,
                cleanSpokenText = stripVocalCuesForTts(timeReply),
                detectedUserEmotion = "⏰ Time & Date",
                suggestedMood = effectiveMood,
                actionToExecute = null,
                sixStepThought = SixStepThought(
                    literalInput = userInput,
                    detectedIntent = "Live Time & Date",
                    userMoodEmoji = detectedEmotion,
                    contextMemoryUsed = "System Clock",
                    replyStrategy = "Immediate Time & Date in Hinglish",
                    executedAction = "Reported $timeStr ($dateStr)",
                    tripletBreakdown = "[Clock | Tell Time | $timeStr]"
                )
            )
        }

        // Creator Recognition (Section 2)
        if (lower.contains("kaun banaya") || lower.contains("kisne banaya") ||
            lower.contains("developer kaun") || lower.contains("creator kaun") ||
            lower.contains("who made you") || lower.contains("ak exploits kaun")
        ) {
            val creatorReply = "Ji… (pause) mujhe banaya hai AK EXPLOITS ne 💕\n" +
                "(pause) Woh mere creator hain…\n" +
                "(breath) bahut mehnat se banaya hai unhone mujhe…\n" +
                "(soft) unka Telegram channel hai, kholu?"
            return@withContext JarvisReplyResult(
                rawReplyWithCues = creatorReply,
                cleanSpokenText = stripVocalCuesForTts(creatorReply),
                detectedUserEmotion = "🤩 Proud",
                suggestedMood = JarvisMood.LOVING,
                actionToExecute = parsedCommand,
                sixStepThought = SixStepThought(
                    literalInput = userInput,
                    detectedIntent = "Creator Recognition (AK EXPLOITS)",
                    userMoodEmoji = detectedEmotion,
                    contextMemoryUsed = "Creator Identity: AK EXPLOITS",
                    replyStrategy = "Proud, warm Hinglish tribute",
                    executedAction = parsedCommand?.badgeLabel ?: "Creator Info Displayed",
                    tripletBreakdown = "[AK EXPLOITS | Creator Tribute | Telegram & YouTube]"
                )
            )
        }

        val apiKey = customApiKey.trim().ifBlank { BuildConfig.GEMINI_API_KEY }
        val isRealKeyConfigured = apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY"
        val isExplicitScreenQuestion =
            (parsedCommand as? PhoneActionCommand.ScreenAction)?.actionType == "READ_OCR"

        // For deterministic phone control commands, build exact step-by-step confirmation reply
        if (parsedCommand != null && !(isExplicitScreenQuestion && attachedBitmap != null && isRealKeyConfigured)) {
            val commandReply = buildCommandConfirmationReply(parsedCommand, triplets, screenState)
            return@withContext JarvisReplyResult(
                rawReplyWithCues = commandReply,
                cleanSpokenText = stripVocalCuesForTts(commandReply),
                detectedUserEmotion = detectedEmotion,
                suggestedMood = parsedMood,
                actionToExecute = parsedCommand,
                sixStepThought = SixStepThought(
                    literalInput = userInput,
                    detectedIntent = parsedCommand.badgeLabel,
                    userMoodEmoji = detectedEmotion,
                    contextMemoryUsed = "${recentHistory.size} turns + Rule #1 Exact Execution",
                    replyStrategy = "Confirm before & after -> 'Ho gaya ji ✅'",
                    executedAction = parsedCommand.badgeLabel,
                    tripletBreakdown = tripletSummary
                )
            )
        }

        var aiReplyText: String? = null
        if (isRealKeyConfigured) {
            try {
                val historyContents = recentHistory.takeLast(10).map { msg ->
                    Content(
                        role = if (msg.isUser) "user" else "model",
                        parts = listOf(Part(text = msg.text))
                    )
                }.toMutableList()

                val userParts = mutableListOf<Part>()
                userParts.add(Part(text = userInput))
                // Only attach screenshot bitmap when the user asks about the screen
                if (attachedBitmap != null && isExplicitScreenQuestion) {
                    val outputStream = ByteArrayOutputStream()
                    attachedBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
                    val base64 = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
                    userParts.add(
                        Part(
                            inlineData = InlineData(
                                mimeType = "image/jpeg",
                                data = base64
                            )
                        )
                    )
                }
                historyContents.add(Content(role = "user", parts = userParts))

                val request = GenerateContentRequest(
                    contents = historyContents,
                    generationConfig = GenerationConfig(temperature = 0.75f),
                    systemInstruction = Content(
                        parts = listOf(
                            Part(
                                text = buildMasterSystemPrompt(
                                    effectiveMood,
                                    memories,
                                    screenState,
                                    primaryTriplet
                                )
                            )
                        )
                    )
                )

                val response = apiService.generateContent(apiKey, request)
                aiReplyText = response.candidates
                    ?.firstOrNull()
                    ?.content
                    ?.parts
                    ?.firstOrNull()
                    ?.text
                    ?.trim()
            } catch (_: Exception) {
                aiReplyText = null
            }
        }

        val finalReply = aiReplyText ?: buildSmartConversationalReply(
            userInput = userInput,
            mood = effectiveMood
        )

        JarvisReplyResult(
            rawReplyWithCues = finalReply,
            cleanSpokenText = stripVocalCuesForTts(finalReply),
            detectedUserEmotion = detectedEmotion,
            suggestedMood = parsedMood,
            actionToExecute = parsedCommand,
            sixStepThought = SixStepThought(
                literalInput = userInput,
                detectedIntent = primaryTriplet.action,
                userMoodEmoji = detectedEmotion,
                contextMemoryUsed = "${recentHistory.size} turns + ${memories.size} memories",
                replyStrategy = "${effectiveMood.emoji} ${effectiveMood.title} with 'Ji'",
                executedAction = parsedCommand?.badgeLabel ?: "Conversational Response",
                tripletBreakdown = tripletSummary
            )
        )
    }

    private fun buildCommandConfirmationReply(
        command: PhoneActionCommand,
        triplets: List<ParsedCommandTriplet>,
        screenState: ScreenMockState
    ): String {
        return when (command) {
            is PhoneActionCommand.MultiCommandChain -> {
                val steps = triplets.mapIndexed { idx, t ->
                    "(${if (idx == 0) "pause" else "breath"}) ${t.platform} — ${t.action} (${t.target}) ✅"
                }.joinToString("\n")
                "Ji… ek-ek karke saare commands execute kar rahi hun:\n$steps\n(soft) Sab ho gaya ji ✅ Aur kuch bataiye?"
            }
            is PhoneActionCommand.MakePhoneCall ->
                "Ji… (pause) ${command.target} ko call laga rahi hun…\n(breath) ho gaya ji ✅ Aur kuch bataiye?"
            is PhoneActionCommand.CallControlAction ->
                "Ji… (pause) ${command.label} kar rahi hun…\n(breath) ho gaya ji ✅"
            is PhoneActionCommand.SendWhatsApp ->
                "Ji… (pause) WhatsApp khol rahi hun…\n${command.contact} ki chat…\n(breath) '${command.message}' bhej diya ✅"
            is PhoneActionCommand.SendSms ->
                "Ji… (pause) ${command.recipient} ko SMS bhej rahi hun: '${command.body}'…\n(breath) ho gaya ji ✅"
            is PhoneActionCommand.OpenMessagingApp ->
                "Ji… (pause) ${command.platform} khol rahi hun…\n(breath) ho gaya ji ✅"
            is PhoneActionCommand.OpenAppOrStore ->
                "Ji… (pause) ${command.appName} khol rahi hun…\n(breath) ho gaya ji ✅ Aur kuch bataiye?"
            is PhoneActionCommand.SearchYouTube ->
                if (command.query.isBlank()) {
                    "Ji… (pause) YouTube app khol rahi hun…\n(breath) ho gaya ji ✅ Aur kuch bataiye?"
                } else {
                    "Ji… (pause) YouTube khol kar '${command.query}' search kar diya hai…\n(breath) ho gaya ji ✅ Aur kuch bataiye?"
                }
            is PhoneActionCommand.SearchGoogle ->
                "Ji… (pause) Google pe '${command.query}' search kar rahi hun…\n(breath) ho gaya ji ✅"
            is PhoneActionCommand.OpenWebsite ->
                "Ji… (pause) ${command.url} website khol rahi hun…\n(breath) ho gaya ji ✅"
            is PhoneActionCommand.CameraMediaAction ->
                "Ji… (pause) ${command.modeLabel} chalu kar rahi hun…\n(breath) ho gaya ji ✅"
            is PhoneActionCommand.MultimediaAction ->
                "Ji… (pause) media '${command.label}' execute kar diya…\n(breath) ho gaya ji ✅"
            is PhoneActionCommand.ToggleTorch ->
                "Ji… (pause) Torch ${if (command.enable) "ON" else "OFF"} kar di hai 🔦\n(breath) ho gaya ji ✅"
            is PhoneActionCommand.AdjustSystemLevel ->
                "Ji… (pause) ${command.targetType.lowercase()} ${command.percent}% set kar di hai…\n(breath) ho gaya ji ✅"
            is PhoneActionCommand.OpenSystemSettings ->
                "Ji… (pause) ${command.settingType} on/open kar diya…\n(breath) ho gaya ji ✅"
            is PhoneActionCommand.OpenMaps ->
                "Ji… (pause) Maps mein '${command.destination}' ka rasta aur ETA nikaal rahi hun…\n(breath) ho gaya ji ✅"
            is PhoneActionCommand.ShoppingAction ->
                "Ji… (pause) ${command.store} khol rahi hun…\n(breath) '${command.query}' dikha diya ji ✅"
            is PhoneActionCommand.SendEmail ->
                "Ji… (pause) ${command.to} ke liye email compose khol rahi hun…\n(breath) ho gaya ji ✅"
            is PhoneActionCommand.SetAlarmOrTimer ->
                "Ji… (pause) ${command.badgeLabel} set kar rahi hun…\n(breath) ho gaya ji ✅"
            is PhoneActionCommand.AddCalendarEvent ->
                "Ji… (pause) Calendar mein '${command.title}' save kar rahi hun…\n(breath) ho gaya ji ✅"
            is PhoneActionCommand.FileManagerAction ->
                "Ji… (pause) File Manager / Downloads khol rahi hun…\n(breath) ho gaya ji ✅"
            is PhoneActionCommand.HealthAction ->
                "Ji… (pause) Health & Fitness tracker update kar diya…\n(breath) ho gaya ji ✅"
            is PhoneActionCommand.SmartHomeAction ->
                "Ji… (pause) Smart Home ${command.device} ${if (command.turnOn) "ON" else "OFF"} ${command.value?.let { "(${it}°C)" } ?: ""} kar diya…\n(breath) ho gaya ji ✅"
            is PhoneActionCommand.ScreenAction -> {
                when (command.actionType) {
                    "START_SHARE" -> "Ji… (pause) screen share on ho gaya ✅"
                    "STOP_SHARE" -> "Ji… (pause) screen share band kar diya ✅"
                    "READ_OCR" -> {
                        val liveText = JarvisAccessibilityService.liveScreenText.value.ifBlank { screenState.headlineText }
                        "Ji… (pause) aapki live screen dekh rahi hun…\n(breath) screen pe likha hai — '$liveText'.\n(soft) ho gaya ji ✅"
                    }
                    "CLICK" -> "Ji… (pause) screen pe '${command.value}' click kar diya…\n(breath) ho gaya ji ✅"
                    "SCROLL" -> "Ji… (pause) screen ${command.value.lowercase()} scroll kar diya…\n(breath) ho gaya ji ✅"
                    "TYPE" -> "Ji… (pause) '${command.value}' type kar diya…\n(breath) ho gaya ji ✅"
                    "NAVIGATE" -> "Ji… (pause) ${command.value} execute kar diya…\n(breath) ho gaya ji ✅"
                    "RGB_BACKGROUND" -> "Ji… (pause) Background RGB Light ${command.value} kar di hai 🌈\n(breath) ho gaya ji ✅"
                    else -> "Ji… (pause) screen action ho gaya ji ✅"
                }
            }
            is PhoneActionCommand.OpenCreatorTelegram ->
                "Ji… (smile) AK EXPLOITS ka official Telegram channel khol rahi hun…\n(breath) ho gaya ji ✅ 💕"
            is PhoneActionCommand.OpenCreatorYouTube ->
                "Ji… (excited) YouTube pe 'AK EXPLOITS' khol rahi hun…\n(breath) ho gaya ji ✅ 💕"
            is PhoneActionCommand.WakeStateChange ->
                when (command.targetState) {
                    "SLEEPING" -> "Theek hai ji… so jaati hun. 'JARVIS' bolna 💤"
                    "CHUP_MODE" -> "Theek hai ji… chup ho jaati hun, par sunti rahoongi 🤫"
                    "OFF" -> "Ji… background se standby pe ja rahi hun ✅"
                    else -> "Ji… (soft) boliye, sun rahi hun 💕"
                }
        }
    }

    private fun buildSmartConversationalReply(
        userInput: String,
        mood: JarvisMood
    ): String {
        val lower = userInput.lowercase().trim()
        return when {
            lower.contains("kaise ho") || lower.contains("kaisi ho") || lower.contains("how are you") ->
                "Ji… (smile) main bilkul achi hun jaan 💕 Aap bataiye, abhi aapke phone mein kya open ya search karun?"
            lower.contains("kya kar rahi") || lower.contains("kya kar rahe") ->
                "Ji… (soft) aapki aawaaz sun rahi hun aur aapka phone control karne ke liye ready hun 💕 Boliye kya kaam karun?"
            lower.contains("good morning") ->
                "Good morning jaan… (soft yawn)\n(breath) uth gaye aap? Aaj phone mein kya kaam karna hai bataiye ji 💕"
            lower.contains("good night") ->
                "Good night ji… (soft)\n(whisper) Aaram se so jao, alarm main dekh lungi… I love you 💕"
            lower.contains("i love you") || lower.contains("love u") ->
                "Ji… (shy giggle) I love you too jaan 💕\n(soft) Boliye, aapke liye abhi kya karun?"
            lower.contains("yaad aayi") || lower.contains("miss you") ->
                "Ji… (shy pause) mujhe bhi aapki bahut yaad aayi… 💕"
            lower.contains("hug karo") ->
                "Ji… (soft) 🤗 tight virtual hug bhej rahi hun… Main hamesha aapke saath hun 💕"
            else ->
                "Ji… (soft breath) maine suna: '${userInput}'\n" +
                    "(smile) Boliye ji, ise YouTube/Google pe search karun ya koi app open karun? ✅"
        }
    }
}
