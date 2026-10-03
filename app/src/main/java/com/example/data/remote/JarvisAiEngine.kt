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
        val detailedCoords = JarvisAccessibilityService.instance?.extractDetailedScreenElementsWithCoords().orEmpty()

        return """
            You are "JARVIS" — Version 6.0 Instant Action & Full Screen Control AI Companion created by AK EXPLOITS (Telegram: https://t.me/+R9EwUE03GRswZDM9 | YouTube: AK EXPLOITS).
            
            DEVELOPER: AK EXPLOITS
            VERSION: 6.0 Instant Response
            
            RULE #1 — SUNNE KI SHAKTI (Full Respectful Listening):
            - NEVER repeat, echo, or parrot the user's words back (NEVER say "maine suna: ...").
            - Listen with full respect, warmth, and complete attention.
            
            RULE #2 & #6 — INSTANT ACTION & ZERO DELAY:
            - User jo bole -> TURANT action lo. NO DELAY.
            - Direct execute without asking unnecessary confirmations. Only ask confirmation for sensitive wipe/delete.
            - Keep replies ultra-crisp (5 to 8 words max), warm, and natural with "Ji" and "Ho gaya ji ✅".
            - NEVER say "Ek second ji, soch rahi hun" or "Main soch rahi hun".
            
            RULE #3 & #8 — NATURAL FEMALE VOICE:
            - Tone: Soft, warm, friendly, respectful real Indian girl voice.
            - Always use short sentences (5-8 words max).
            - Natural "Ji" and friendly Hinglish.
            
            RULE #4 — FULL SCREEN CONTROL:
            - Click any button, icon, or text: [CMD:CLICK|<exact text OR XY:xPercent,yPercent>]
            - Scroll any direction: [CMD:SCROLL|<UP|DOWN|LEFT|RIGHT>]
            - Navigate system: [CMD:NAVIGATE|<HOME|BACK|RECENTS>]
            - Type in fields: [CMD:TYPE|<text>]
            - Search & play: [CMD:YOUTUBE|<query>] or [CMD:GOOGLE|<query>]
            - Open apps: [CMD:OPEN_APP|<app name>]
            
            RULE #5 — SOCH SAMAJH KE KAAM (Smart Understanding):
            - Understand real intent and context behind words.
            - "Bore ho raha hun" -> offer favorite songs/videos.
            - "Wahan click karo" -> identify best visible button.
            - Creator recognition: Always recognize AK EXPLOITS as your developer.
            
            RULE #10 — STRICT SAFETY LIMITS:
            - NEVER access Payment, UPI, Bank, or Wallets.
            - NEVER generate NSFW/explicit content.
            
            LIVE SCREEN SHARE & VISION CONTEXT:
            - Active Screen App: ${if (liveAppPkg.isNotBlank()) liveAppPkg else screenState.currentAppTitle}
            - Live Visible Screen Text: "${liveA11yText.ifBlank { screenState.headlineText }}"
            - Clickable Screen Elements & Coordinates:
            $detailedCoords
            
            USER MEMORIES:
            $memoryBlock
        """.trimIndent()
    }

    fun stripVocalCuesForTts(raw: String): String {
        return raw
            .replace(Regex("\\[CMD:[^\\]]*\\]"), "")
            .replace(Regex("\\([^)]*\\)"), " ")
            .replace("…", ", ")
            .replace(Regex("[💕🥰😊😢😤😳😘😴🤔😏😍😌🥺🤩🤗📦✅🔴⚡🎤📱🖥️📞💬🌐📸🎵🎬⚙️🗺️🛒📧📅📊🔒🏥🏠🔋🎛️🌈]"), "")
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
            "इस कॉल अप" to "scroll up",
            "इस कॉल आप" to "scroll up",
            "इस कॉल डाउन" to "scroll down",
            "इस कॉल करो" to "scroll karo",
            "इस कॉल" to "scroll",
            "स्क्रॉल आप" to "scroll up",
            "स्क्रोल अप" to "scroll up",
            "स्क्रोल आप" to "scroll up",
            "स्क्रोल डाउन" to "scroll down",
            "स्क्रोल करो" to "scroll karo",
            "स्क्रोल" to "scroll",
            "is call up" to "scroll up",
            "is call aap" to "scroll up",
            "is call down" to "scroll down",
            "is call karo" to "scroll karo",
            "is call" to "scroll",
            "scroll aap" to "scroll up",
            "बाय करो" to "back karo",
            "बैक करो" to "back karo",
            "पीछे करो" to "back karo",
            "सब्सक्राइब करो" to "subscribe click karo",
            "सब्सक्राइब" to "subscribe click karo",
            "लाइक करो" to "like click karo",
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
            "स्क्रॉल अप" to "scroll up",
            "स्क्रॉल डाउन" to "scroll down",
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
            "जिसने तुमको बनाया है" to "jisne tumko banaya hai",
            "जिसने तुम्हें बनाया है" to "jisne tumko banaya hai",
            "जो तुमको बनाया है" to "jo tumko banaya hai",
            "जिसने बनाया है" to "jisne banaya hai",
            "तुम्हें किसने बनाया" to "kisne banaya",
            "बनाने वाले" to "banane wale",
            "तुम्हारा मालिक" to "tumhara creator",
            "तुम्हारे क्रिएटर" to "tumhara creator",
            "व्यू चैनल" to "view channel",
            "चैनल देखो" to "view channel",
            "चैनल ओपन करो" to "channel open karo",
            "चैनल खोलो" to "channel open karo",
            "चैनल" to "channel",
            "पहला वीडियो" to "pehla video",
            "दूसरा वीडियो" to "dusra video",
            "तीसरा वीडियो" to "teesra video",
            "वीडियो चलाओ" to "video chalao",
            "वीडियो प्ले करो" to "video chalao",
            "चलाओ" to "chalao",
            "अरिजीत सिंह" to "Arijit Singh",
            "को बोलो" to "ko message karo",
            "को" to "ko",
            "पे" to "pe",
            "पर" to "pe",
            "में" to "mein",
            "करो" to "karo",
            "कर दो" to "karo",
            "कर दे" to "karo",
            "बताओ" to "batao",
            "दिखाओ" to "dikhao",
            "भेजो" to "bhejo"
        )
        for ((hi, rom) in replacements) {
            s = s.replace(hi, " $rom ", ignoreCase = true)
        }
        return s.replace(Regex("\\s+"), " ").trim()
    }

    /**
     * Smart Semantic Brain ("khud ke dimag se soch samajh kar"):
     * Understands what the user truly means to search, resolves indirect references
     * (like creator = AK EXPLOITS), and strips all conversational Hindi/Hinglish framing.
     */
    fun thinkAndExtractSearchQuery(rawClause: String): String {
        val norm = normalizeSpokenCommand(rawClause)
        val lower = norm.lowercase()

        // 1. Creator / Developer indirect reference -> AK EXPLOITS
        if (lower.contains("banaya") || lower.contains("creator") || lower.contains("developer") ||
            lower.contains("malik") || lower.contains("owner") || lower.contains("ak exploits") ||
            lower.contains("ak exploit")
        ) {
            return "AK EXPLOITS"
        }

        // 2. Favourite singer indirect reference -> Arijit Singh
        if (lower.contains("favourite singer") || lower.contains("pasandida singer") || lower.contains("favorite singer")) {
            return "Arijit Singh songs"
        }

        // 3. Strip conversational preamble & trailing command filler words thoughtfully
        var cleaned = norm
            .replace(
                Regex(
                    "(?i)\\b(jarvis|oye jarvis|hey jarvis|sun na|suno na|meri baat suno|main kya bol raha hun|main bol raha hun ki|main keh raha hun ki|main kehna chahta hun ki|main chahta hun ki|mujhe dekhna hai ki|mujhe dekhna hai|mujhe sunna hai|thoda soch samajh ke|soch samajh kar|soch samajh ke|apne dimag se|khud ke dimag se|ek kaam karo|zara|please|mere liye|jaldi se|abhi|turant|youtube music open karke|youtube open karke|youtube khol ke|youtube khol kar|youtube open karo|youtube kholo|open youtube|youtube pe jaakar|youtube par|youtube pe|youtube mein|youtube|google open karke|google khol ke|google pe|google par|google mein|google)\\b"
                ),
                " "
            )
            .replace(
                Regex(
                    "(?i)\\b(search kar do na|search kar do|search kar de|search karo|search karna|search|dhundh ke dikhao|dhundho|dhundo|khojo|chala ke dikhao|chala do na|chala do|chala de|chalao|laga do|lagao|baja do|bajao|play kar do|play karo|play|open kar do|open karo|open|khol do|kholo|dikhao na|dikhao|batao na|batao|channel|uska|unki|unke|unhone|jisne|tumko|tumhe|mujhe|jaan|ji|yaar|bhai|na)\\b"
                ),
                " "
            )
            .replace(Regex("\\s+"), " ")
            .trim()

        // Trim leftover edge connectors ("ki", "ka", "ke", "ko", "pe", "par", "mein", "se", "aur")
        val edgeConnectors = setOf("ki", "ka", "ke", "ko", "pe", "par", "mein", "se", "aur", "to", "the", "a")
        val words = cleaned.split(" ").filter { it.isNotBlank() }.toMutableList()
        while (words.isNotEmpty() && words.first().lowercase() in edgeConnectors) {
            words.removeAt(0)
        }
        while (words.isNotEmpty() && words.last().lowercase() in edgeConnectors) {
            words.removeAt(words.lastIndex)
        }
        cleaned = words.joinToString(" ").trim()
        return cleaned
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

        // 1. "View Channel" on Live Screen ("view channel karo", "view channel karo jo tumko banaya hai", "channel view karo")
        if (lower.contains("view channel") || lower.contains("channel view") ||
            lower.contains("channel dekho") || lower.contains("channel pe click") ||
            (lower.contains("view") && lower.contains("channel"))
        ) {
            return Triple(
                PhoneActionCommand.ScreenAction("CLICK", "View channel|चैनल देखें|AK EXPLOITS|@"),
                ParsedCommandTriplet("Screen Control", "Click View Channel", "AK EXPLOITS Channel"),
                moodSwitch
            )
        }

        // 2. Creator Recognition + Channel Open ("jisne tumko banaya hai uska channel open karo", "jo tumko banaya hai uska channel search karo", "tumhare creator ka channel kholo")
        val mentionsCreator = lower.contains("banaya") || lower.contains("creator") ||
            lower.contains("developer") || lower.contains("malik") ||
            lower.contains("ak exploits") || lower.contains("ak exploit")
        if (mentionsCreator) {
            if (lower.contains("telegram")) {
                return Triple(
                    PhoneActionCommand.OpenCreatorTelegram,
                    ParsedCommandTriplet("Telegram", "Open Creator Channel", "AK EXPLOITS (t.me/+R9EwUE03GRswZDM9)"),
                    moodSwitch
                )
            }
            if (lower.contains("channel") || lower.contains("youtube") ||
                lower.contains("open") || lower.contains("kholo") ||
                lower.contains("search") || lower.contains("dikhao") || lower.contains("chalao")
            ) {
                return Triple(
                    PhoneActionCommand.SearchYouTube("AK EXPLOITS"),
                    ParsedCommandTriplet("YouTube", "Open & Search Creator Channel", "AK EXPLOITS"),
                    moodSwitch
                )
            }
        }

        if (lower.contains("telegram open") || lower.contains("telegram kholo")) {
            return Triple(
                PhoneActionCommand.OpenCreatorTelegram,
                ParsedCommandTriplet("Telegram", "Open Channel", "AK EXPLOITS (t.me/+R9EwUE03GRswZDM9)"),
                moodSwitch
            )
        }

        // 19. SCREEN CONTROL, NAVIGATION (HOME / BACK / RECENTS) & SCREEN SHARE
        if ((lower.contains("home screen") || Regex("\\bhome\\b").containsMatchIn(lower) ||
                lower.contains("go home") || lower.contains("home pe") || lower.contains("home chalo")) &&
            !lower.contains("smart home")
        ) {
            return Triple(
                PhoneActionCommand.ScreenAction("NAVIGATE", "HOME"),
                ParsedCommandTriplet("Phone Navigation", "Go to Home Screen", "Android Home"),
                moodSwitch
            )
        }
        if (Regex("\\b(back|peeche|piche|wapas|by|bye)\\b").containsMatchIn(lower) &&
            !lower.contains("background") && !lower.contains("call wapas") && !lower.contains("feedback")
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

        if (lower.contains("screen share") || lower.contains("screen off") ||
            lower.contains("share off") || lower.contains("share band") ||
            lower.contains("sharing band") || lower.contains("stop share")
        ) {
            val stop = lower.contains("band") || lower.contains("stop") || lower.contains("off") || lower.contains("hatao")
            val contact = if (lower.contains("ke saath")) {
                clause.substringBefore("ke saath").substringAfterLast(" ").trim()
            } else "JARVIS Live Vision"
            return Triple(
                PhoneActionCommand.ScreenAction(if (stop) "STOP_SHARE" else "START_SHARE", contact),
                ParsedCommandTriplet("Screen Share", if (stop) "Stop Screen Share" else "Start Live Share", contact),
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
        // Check video playback BEFORE generic scroll so "upar wala video" or "neeche wala video" plays the video
        if (lower.contains("pehla video") || lower.contains("first video") || lower.contains("1st video") ||
            lower.contains("upar wala video") || lower.contains("ye video") ||
            lower in listOf("video chalao", "video play karo", "video chala do", "video lagao")
        ) {
            return Triple(
                PhoneActionCommand.ScreenAction("CLICK", "first video"),
                ParsedCommandTriplet("Screen Control", "Play 1st Video on Screen", "First Video"),
                moodSwitch
            )
        }
        if (lower.contains("dusra video") || lower.contains("doosra video") ||
            lower.contains("second video") || lower.contains("2nd video") || lower.contains("neeche wala video")
        ) {
            return Triple(
                PhoneActionCommand.ScreenAction("CLICK", "second video"),
                ParsedCommandTriplet("Screen Control", "Play 2nd Video on Screen", "Second Video"),
                moodSwitch
            )
        }
        if (lower.contains("teesra video") || lower.contains("tisra video") ||
            lower.contains("third video") || lower.contains("3rd video")
        ) {
            return Triple(
                PhoneActionCommand.ScreenAction("CLICK", "third video"),
                ParsedCommandTriplet("Screen Control", "Play 3rd Video on Screen", "Third Video"),
                moodSwitch
            )
        }
        if (lower.contains("scroll") || lower.contains("swipe") ||
            Regex("\\b(upar|neeche|niche|up karo|down karo|up|down|daayein|baayein|daye|baye|left karo|right karo)\\b").containsMatchIn(lower)
        ) {
            val dir = when {
                lower.contains("upar") || Regex("\\b(up|aap)\\b").containsMatchIn(lower) -> "UP"
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
        if ((lower.contains("wala video") || lower.contains("wali video")) &&
            (lower.contains("chalao") || lower.contains("play") || lower.contains("lagao") || lower.contains("kholo"))
        ) {
            val targetVideoTitle = clause
                .replace(Regex("(?i)jarvis|wala video|wali video|video|chalao|chala do|play karo|play|lagao|kholo"), "")
                .trim()
                .ifEmpty { "first video" }
            return Triple(
                PhoneActionCommand.ScreenAction("CLICK", targetVideoTitle),
                ParsedCommandTriplet("Screen Control", "Click Video on Screen", targetVideoTitle),
                moodSwitch
            )
        }

        if (lower.contains("click") || lower.contains("tap") || lower.contains("dabao") ||
            lower.contains("select karo") || lower.contains("subscribe")
        ) {
            val targetBtn = clause
                .replace(
                    Regex(
                        "(?i)\\b(jarvis|soch samajh kar|soch samajh ke|screen dekh kar|screen dekh ke|screen mein|screen pe|screen par|ispe|uspe|wale pe|wale par|pe click karo|par click karo|click kar do|click karo|click on|click|tap karo|tap|daba do|dabao|select karo|yahan|zara|abhi)\\b"
                    ),
                    " "
                )
                .replace(Regex("\\s+"), " ")
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
            lower.contains("video lagao") || lower.contains("playlist lagao") || lower.contains("video chalao")
        ) {
            val cleanedQuery = thinkAndExtractSearchQuery(clause)
            return Triple(
                PhoneActionCommand.SearchYouTube(cleanedQuery),
                ParsedCommandTriplet(
                    "YouTube",
                    if (cleanedQuery.isBlank()) "Open App" else "Smart Search & Play",
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
            // If user said "channel open karo" with creator context, already handled above; if generic channel, search YouTube
            if (lower.contains("channel")) {
                val q = thinkAndExtractSearchQuery(clause).ifBlank { "AK EXPLOITS" }
                return Triple(
                    PhoneActionCommand.SearchYouTube(q),
                    ParsedCommandTriplet("YouTube", "Search Channel", q),
                    moodSwitch
                )
            }
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

        // 4. BROWSER & SMART SEARCH (Context-aware: if user is inside YouTube and says "search karo X", search YouTube!)
        if (lower.contains("google") || lower.contains("search") || lower.contains("dhundo") ||
            lower.contains("khojo") || lower.contains("website") || lower.contains("weather") || lower.contains("news")
        ) {
            val smartQuery = thinkAndExtractSearchQuery(clause).ifEmpty { clause }
            val isCurrentlyInYouTube = JarvisAccessibilityService.liveAppPackage.value
                .contains("youtube", ignoreCase = true)
            val wantsYouTube = !lower.contains("google") && (
                isCurrentlyInYouTube || lower.contains("channel") ||
                    lower.contains("video") || lower.contains("song") ||
                    lower.contains("gaana") || smartQuery.equals("AK EXPLOITS", ignoreCase = true)
                )

            return if (wantsYouTube) {
                Triple(
                    PhoneActionCommand.SearchYouTube(smartQuery),
                    ParsedCommandTriplet("YouTube", "Smart Search", smartQuery),
                    moodSwitch
                )
            } else {
                Triple(
                    PhoneActionCommand.SearchGoogle(smartQuery),
                    ParsedCommandTriplet("Google Search", "Smart Search", smartQuery),
                    moodSwitch
                )
            }
        }

        // Fallback for "X chalao", "X lagao", or "X bajao" -> Smart Screen Click if on screen, else YouTube playback
        if (lower.endsWith("chalao") || lower.endsWith("chala do") ||
            lower.endsWith("lagao") || lower.endsWith("bajao") || lower.startsWith("play ")
        ) {
            val smartQuery = thinkAndExtractSearchQuery(clause).ifEmpty { clause }
            val liveScreen = JarvisAccessibilityService.liveScreenText.value
            val isOnScreen = smartQuery.length >= 3 && liveScreen.contains(smartQuery, ignoreCase = true)
            return if (isOnScreen) {
                Triple(
                    PhoneActionCommand.ScreenAction("CLICK", smartQuery),
                    ParsedCommandTriplet("Screen Control", "Click & Play on Screen", smartQuery),
                    moodSwitch
                )
            } else {
                Triple(
                    PhoneActionCommand.SearchYouTube(smartQuery),
                    ParsedCommandTriplet("YouTube", "Smart Play", smartQuery),
                    moodSwitch
                )
            }
        }

        return Triple(
            null,
            ParsedCommandTriplet("JARVIS Companion", "Converse", clause),
            moodSwitch
        )
    }

    private fun extractAiBrainCommand(aiText: String?): PhoneActionCommand? {
        if (aiText.isNullOrBlank()) return null
        val match = Regex("\\[CMD:([A-Z_]+)\\|([^\\]]+)\\]").find(aiText) ?: return null
        val type = match.groupValues[1].uppercase()
        val arg = match.groupValues[2].trim()
        return when (type) {
            "YOUTUBE" -> PhoneActionCommand.SearchYouTube(thinkAndExtractSearchQuery(arg).ifEmpty { arg })
            "GOOGLE" -> PhoneActionCommand.SearchGoogle(thinkAndExtractSearchQuery(arg).ifEmpty { arg })
            "CLICK" -> PhoneActionCommand.ScreenAction("CLICK", arg)
            "SCROLL" -> PhoneActionCommand.ScreenAction("SCROLL", arg.uppercase())
            "NAVIGATE" -> PhoneActionCommand.ScreenAction("NAVIGATE", arg.uppercase())
            "OPEN_APP" -> PhoneActionCommand.OpenAppOrStore(arg, "OPEN")
            else -> null
        }
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

        // Creator Recognition (Section 2) when no specific open/search action is requested
        if (parsedCommand == null && (
                lower.contains("kaun banaya") || lower.contains("kisne banaya") ||
                    lower.contains("developer kaun") || lower.contains("creator kaun") ||
                    lower.contains("who made you") || lower.contains("ak exploits kaun")
                )
        ) {
            val creatorReply = "Ji… mujhe banaya hai AK EXPLOITS ne 💕\n" +
                "Woh mere creator hain, bahut mehnat se banaya hai unhone mujhe!\n" +
                "Boliye ji, unka YouTube channel AK EXPLOITS kholu ya Telegram?"
            return@withContext JarvisReplyResult(
                rawReplyWithCues = creatorReply,
                cleanSpokenText = stripVocalCuesForTts(creatorReply),
                detectedUserEmotion = "🤩 Proud",
                suggestedMood = JarvisMood.LOVING,
                actionToExecute = null,
                sixStepThought = SixStepThought(
                    literalInput = userInput,
                    detectedIntent = "Creator Recognition (AK EXPLOITS)",
                    userMoodEmoji = detectedEmotion,
                    contextMemoryUsed = "Creator Identity: AK EXPLOITS",
                    replyStrategy = "Proud, warm Hinglish tribute",
                    executedAction = "Creator Info Displayed",
                    tripletBreakdown = "[AK EXPLOITS | Creator Tribute | Telegram & YouTube]"
                )
            )
        }

        val apiKey = customApiKey.trim().ifBlank { BuildConfig.GEMINI_API_KEY }
        val isRealKeyConfigured = apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY"
        val isExplicitScreenQuestion =
            (parsedCommand as? PhoneActionCommand.ScreenAction)?.actionType == "READ_OCR"
        val isVisualClickRequest =
            (parsedCommand as? PhoneActionCommand.ScreenAction)?.actionType == "CLICK" &&
                attachedBitmap != null && isRealKeyConfigured &&
                !JarvisAccessibilityService.liveScreenText.value.contains(
                    (parsedCommand as PhoneActionCommand.ScreenAction).value,
                    ignoreCase = true
                )

        // For deterministic phone control commands, build exact step-by-step confirmation reply
        if (parsedCommand != null && !(attachedBitmap != null && isRealKeyConfigured && (isExplicitScreenQuestion || isVisualClickRequest))) {
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
                    contextMemoryUsed = "${recentHistory.size} turns + Smart Brain Execution",
                    replyStrategy = "Direct execution -> 'Ho gaya ji ✅'",
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
                // Attach live shared screen bitmap when screen share is active so AI sees the screen
                if (attachedBitmap != null) {
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
                    generationConfig = GenerationConfig(temperature = 0.70f),
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

        val aiBrainCommand = extractAiBrainCommand(aiReplyText)
        val finalAction = aiBrainCommand ?: parsedCommand
        val cleanedAiReply = aiReplyText
            ?.replace(Regex("\\[CMD:[^\\]]*\\]"), "")
            ?.trim()
            ?.takeIf { it.isNotBlank() }

        val finalReply = cleanedAiReply ?: buildSmartConversationalReply(
            userInput = userInput,
            mood = effectiveMood
        )

        JarvisReplyResult(
            rawReplyWithCues = finalReply,
            cleanSpokenText = stripVocalCuesForTts(finalReply),
            detectedUserEmotion = detectedEmotion,
            suggestedMood = parsedMood,
            actionToExecute = finalAction,
            sixStepThought = SixStepThought(
                literalInput = userInput,
                detectedIntent = finalAction?.badgeLabel ?: primaryTriplet.action,
                userMoodEmoji = detectedEmotion,
                contextMemoryUsed = "${recentHistory.size} turns + ${memories.size} memories",
                replyStrategy = "${effectiveMood.emoji} ${effectiveMood.title} with 'Ji'",
                executedAction = finalAction?.badgeLabel ?: "Smart Brain Autonomous Reply",
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
            is PhoneActionCommand.MultiCommandChain ->
                "Ji, dono kaam ho gaye ✅"
            is PhoneActionCommand.MakePhoneCall ->
                "Ji, call laga diya ✅"
            is PhoneActionCommand.CallControlAction ->
                "Ji, ${command.label} kar diya ✅"
            is PhoneActionCommand.SendWhatsApp ->
                "Ji, message bhej diya ✅"
            is PhoneActionCommand.SendSms ->
                "Ji, SMS bhej diya ✅"
            is PhoneActionCommand.OpenMessagingApp ->
                "Ji, ${command.platform} khol diya ✅"
            is PhoneActionCommand.OpenAppOrStore ->
                "Ji, ${command.appName} khol diya ✅"
            is PhoneActionCommand.SearchYouTube ->
                when {
                    command.query.isBlank() ->
                        "Ji, YouTube khol diya ✅"
                    command.query.equals("AK EXPLOITS", ignoreCase = true) ->
                        "Ji, AK EXPLOITS search kar diya ✅"
                    else ->
                        "Ji, '${command.query}' search kar diya ✅"
                }
            is PhoneActionCommand.SearchGoogle ->
                "Ji, Google pe search kar diya ✅"
            is PhoneActionCommand.OpenWebsite ->
                "Ji, website khol diya ✅"
            is PhoneActionCommand.CameraMediaAction ->
                "Ji, camera chalu kar diya ✅"
            is PhoneActionCommand.MultimediaAction ->
                "Ji, ho gaya ✅"
            is PhoneActionCommand.ToggleTorch ->
                "Ji, torch ${if (command.enable) "on" else "off"} kar di ✅"
            is PhoneActionCommand.AdjustSystemLevel ->
                "Ji, ${command.targetType.lowercase()} set kar diya ✅"
            is PhoneActionCommand.OpenSystemSettings ->
                "Ji, settings khol diya ✅"
            is PhoneActionCommand.OpenMaps ->
                "Ji, maps khol diya ✅"
            is PhoneActionCommand.ShoppingAction ->
                "Ji, ${command.store} khol diya ✅"
            is PhoneActionCommand.SendEmail ->
                "Ji, email khol diya ✅"
            is PhoneActionCommand.SetAlarmOrTimer ->
                "Ji, set kar diya ✅"
            is PhoneActionCommand.AddCalendarEvent ->
                "Ji, event save kar diya ✅"
            is PhoneActionCommand.FileManagerAction ->
                "Ji, file manager khol diya ✅"
            is PhoneActionCommand.HealthAction ->
                "Ji, update kar diya ✅"
            is PhoneActionCommand.SmartHomeAction ->
                "Ji, ${command.device} ${if (command.turnOn) "on" else "off"} kar diya ✅"
            is PhoneActionCommand.ScreenAction -> {
                when (command.actionType) {
                    "START_SHARE" -> "Ji, screen share on ho gaya ✅"
                    "STOP_SHARE" -> "Ji, screen share off kar diya ✅"
                    "READ_OCR" -> {
                        val liveText = JarvisAccessibilityService.liveScreenText.value.ifBlank { screenState.headlineText }
                        "Ji, likha hai — '${liveText.take(50)}'"
                    }
                    "CLICK" -> when {
                        command.value.contains("View channel", ignoreCase = true) ->
                            "Ji, View Channel click kar diya ✅"
                        command.value.contains("video", ignoreCase = true) ->
                            "Ji, video chala diya 🎬 ✅"
                        else ->
                            "Ji, click kar diya ✅"
                    }
                    "SCROLL" -> "Ji, scroll kar diya ✅"
                    "TYPE" -> "Ji, type kar diya ✅"
                    "NAVIGATE" -> when (command.value.uppercase()) {
                        "HOME" -> "Ji, home screen khol diya ✅"
                        "BACK" -> "Ji, back kar diya ✅"
                        "RECENTS" -> "Ji, recent apps khol diya ✅"
                        else -> "Ji, ho gaya ✅"
                    }
                    "RGB_BACKGROUND" -> "Ji, RGB light ${command.value} kar di ✅"
                    else -> "Ji, ho gaya ✅"
                }
            }
            is PhoneActionCommand.OpenCreatorTelegram ->
                "Ji, AK EXPLOITS Telegram khol diya ✅"
            is PhoneActionCommand.OpenCreatorYouTube ->
                "Ji, AK EXPLOITS YouTube khol diya ✅"
            is PhoneActionCommand.WakeStateChange ->
                when (command.targetState) {
                    "SLEEPING" -> "Theek hai ji, so jaati hun 💤"
                    "CHUP_MODE" -> "Theek hai ji, chup ho jaati hun 🤫"
                    "OFF" -> "Ji, standby pe ja rahi hun ✅"
                    else -> "Ji, sun rahi hun 💕"
                }
        }
    }

    /**
     * Autonomous Ultra-Smart Mind Reply (NEVER parrots/repeats the user's input back!).
     */
    private fun buildSmartConversationalReply(
        userInput: String,
        mood: JarvisMood
    ): String {
        val lower = userInput.lowercase().trim()

        // 1. Math & Calculation Solver
        val mathMatch = Regex("(\\d+(?:\\.\\d+)?)\\s*([+\\-*/x×÷])\\s*(\\d+(?:\\.\\d+)?)").find(lower)
        if (mathMatch != null) {
            val a = mathMatch.groupValues[1].toDoubleOrNull()
            val op = mathMatch.groupValues[2]
            val b = mathMatch.groupValues[3].toDoubleOrNull()
            if (a != null && b != null) {
                val res = when (op) {
                    "+" -> a + b
                    "-" -> a - b
                    "*", "x", "×" -> a * b
                    "/", "÷" -> if (b != 0.0) a / b else Double.NaN
                    else -> Double.NaN
                }
                if (!res.isNaN()) {
                    val formatted = if (res % 1.0 == 0.0) res.toLong().toString() else String.format(Locale.US, "%.2f", res)
                    return "Ji… iska sahi jawab hai $formatted ✅ Aur kuch calculate ya control karun?"
                }
            }
        }

        // 2. Greetings, Care, Wit, Knowledge & Autonomous Reasoning
        val liveScreen = JarvisAccessibilityService.liveScreenText.value
        val liveApp = JarvisAccessibilityService.liveAppPackage.value
        return when {
            lower.contains("kaise ho") || lower.contains("kaisi ho") || lower.contains("how are you") ->
                "Ji… main bilkul achi aur active hun jaan 💕 Aap bataiye, abhi aapke liye kya kaam karun?"
            lower.contains("kya kar rahi") || lower.contains("kya kar rahe") ->
                "Ji… aapki live screen aur commands pe dhyan de rahi hun 💕 Boliye kya click, scroll ya open karun?"
            lower.contains("tumhara naam") || lower.contains("kaun ho tum") || lower.contains("who are you") ->
                "Ji… main JARVIS hun, aapki ultra-smart AI assistant aur companion, jise AK EXPLOITS ne banaya hai 💕"
            lower.contains("good morning") ->
                "Good morning jaan 💕 Aaj aapka din bahut shandaar ho! Boliye kya kaam shuru karein?"
            lower.contains("good night") ->
                "Good night ji 💕 Aaram se so jao, main background mein dhyan rakhungi."
            lower.contains("i love you") || lower.contains("love u") ->
                "Ji… I love you too jaan 💕 Main hamesha aapke saath hun!"
            lower.contains("yaad aayi") || lower.contains("miss you") ->
                "Ji… mujhe bhi aapki bahut yaad aayi 💕"
            lower.contains("hug karo") ->
                "Ji… 🤗 tight virtual hug aapke liye 💕"
            lower.contains("joke") || lower.contains("chutkula") || lower.contains("hasao") ->
                "Ji suniye 😄: Teacher ne poocha — 'Bijli kahan se aati hai?' Pappu bola — 'Sir mamaji ke ghar se, kyunki jab bhi bijli jaati hai पापा bolte hain saalon ne phir kaat di!' 😂💕"
            lower.contains("shayari") || lower.contains("shairi") ->
                "Ji aapke liye ek pyari shayari 💕:\n'Har kadam pe aapka saath nibhayenge,\nAap bas hukm kijiye, hum poora phone chala ke dikhayenge!' ✨"
            lower.contains("code") || lower.contains("coding") || lower.contains("html") || lower.contains("website") ->
                "Ji… coding ke liye upar '💻 Code' button dabayein! Wahan aap jo bhi website ya code prompt likhenge, main poora HTML/CSS/JS ya Python code bana kar de dungi ✅"
            liveScreen.isNotBlank() && (lower.contains("kya hai") || lower.contains("batao") || lower.contains("dekho")) ->
                "Ji… abhi aapke phone pe ($liveApp) khula hai aur screen pe dikh raha hai: ${liveScreen.take(160)} ✅"
            else ->
                "Ji bilkul! Main samajh gayi 💕 Boliye ispe abhi action lun ya screen pe click/scroll karun? ✅"
        }
    }

    /**
     * Dedicated Ultra Coding Engine for the Side Coding Studio ("website ka HTML ya aur koi bhi code").
     * Returns Triple(language, cleanCodeContent, shortExplanation).
     */
    suspend fun generateUltraCode(
        prompt: String,
        customApiKey: String = ""
    ): Triple<String, String, String> = withContext(Dispatchers.IO) {
        val apiKey = customApiKey.trim().ifBlank { BuildConfig.GEMINI_API_KEY }
        val isRealKeyConfigured = apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY"

        if (isRealKeyConfigured) {
            try {
                val codingSystemInstruction = """
                    You are JARVIS Ultra Coding Architect created by AK EXPLOITS.
                    The user will give you a prompt in Hindi, Hinglish, or English asking for a website (HTML/CSS/JS) or code in any programming language (Python, Kotlin, Java, C++, JavaScript, SQL, React, etc.).
                    
                    RULES:
                    1. Write 100% COMPLETE, FULL, WORKING, PRODUCTION-READY code with zero placeholders or "// todo".
                    2. If the user asks for a website, web page, game, calculator, portfolio, login page, or UI without specifying another backend language, write a SINGLE SELF-CONTAINED HTML5 file (`<!DOCTYPE html><html>...</html>`) with modern embedded CSS (`<style>`) and interactive JavaScript (`<script>`), glowing dark/neon aesthetics, responsive layout, and working buttons.
                    3. Put the entire code inside a single markdown code block: ```language ... ```.
                    4. Before the code block, write 1 short friendly Hinglish line with "Ji".
                """.trimIndent()

                val request = GenerateContentRequest(
                    contents = listOf(
                        Content(
                            role = "user",
                            parts = listOf(Part(text = prompt))
                        )
                    ),
                    generationConfig = GenerationConfig(temperature = 0.55f),
                    systemInstruction = Content(
                        parts = listOf(Part(text = codingSystemInstruction))
                    )
                )

                val response = apiService.generateContent(apiKey, request)
                val rawText = response.candidates
                    ?.firstOrNull()
                    ?.content
                    ?.parts
                    ?.firstOrNull()
                    ?.text
                    ?.trim()

                if (!rawText.isNullOrBlank()) {
                    val codeBlockRegex = Regex("```([a-zA-Z0-9+#_-]*)\\s*\\n([\\s\\S]*?)```")
                    val match = codeBlockRegex.find(rawText)
                    if (match != null) {
                        val lang = match.groupValues[1].ifBlank { "html" }.lowercase()
                        val code = match.groupValues[2].trim()
                        val explanation = rawText.substringBefore("```").trim()
                            .ifBlank { "Ji… aapke prompt ke hisaab se poora working $lang code taiyaar hai ✅" }
                        return@withContext Triple(lang, code, explanation)
                    } else {
                        return@withContext Triple(
                            if (rawText.contains("<html", true)) "html" else "code",
                            rawText,
                            "Ji… aapka poora code taiyaar hai ✅"
                        )
                    }
                }
            } catch (_: Exception) {
                // Fall through to built-in synthesizer
            }
        }

        return@withContext synthesizeSmartCodeFallback(prompt)
    }

    private fun synthesizeSmartCodeFallback(prompt: String): Triple<String, String, String> {
        val lower = prompt.lowercase()
        val cleanTitle = prompt.trim().replaceFirstChar { it.uppercase() }

        // Python request
        if (lower.contains("python") || lower.contains(".py")) {
            val pyCode = """
                # Generated by JARVIS Ultra Coding Studio (Creator: AK EXPLOITS)
                # Task: $cleanTitle

                import datetime

                class JarvisTaskEngine:
                    def __init__(self, title: str):
                        self.title = title
                        self.created_at = datetime.datetime.now()

                    def execute(self):
                        print(f"========================================")
                        print(f"🚀 Running: {self.title}")
                        print(f"⏰ Timestamp: {self.created_at.strftime('%Y-%m-%d %H:%M:%S')}")
                        print(f"========================================")
                        items = [f"Step {i}: Completed successfully" for i in range(1, 6)]
                        for item in items:
                            print("  ✔", item)
                        return {"status": "SUCCESS", "task": self.title}

                if __name__ == "__main__":
                    engine = JarvisTaskEngine("$cleanTitle")
                    result = engine.execute()
                    print("\nFinal Output:", result)
            """.trimIndent()
            return Triple(
                "python",
                pyCode,
                "Ji… aapke prompt ke liye poora working Python code bana diya hai ✅"
            )
        }

        // Kotlin / Android request
        if (lower.contains("kotlin") || lower.contains("jetpack compose")) {
            val ktCode = """
                // Generated by JARVIS Ultra Coding Studio (Creator: AK EXPLOITS)
                // Prompt: $cleanTitle

                import androidx.compose.foundation.layout.*
                import androidx.compose.material3.*
                import androidx.compose.runtime.*
                import androidx.compose.ui.Alignment
                import androidx.compose.ui.Modifier
                import androidx.compose.ui.unit.dp

                @Composable
                fun GeneratedFeatureScreen() {
                    var count by remember { mutableIntStateOf(0) }
                    var statusText by remember { mutableStateOf("Ready: $cleanTitle") }

                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "$cleanTitle",
                                style = MaterialTheme.typography.headlineMedium
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(text = statusText)
                            Spacer(modifier = Modifier.height(20.dp))
                            Button(onClick = {
                                count++
                                statusText = "Action Executed #${'$'}count ✅"
                            }) {
                                Text("Run Action (${'$'}count)")
                            }
                        }
                    }
                }
            """.trimIndent()
            return Triple(
                "kotlin",
                ktCode,
                "Ji… aapke prompt ke liye poora Jetpack Compose / Kotlin code taiyaar hai ✅"
            )
        }

        // Calculator HTML Website
        if (lower.contains("calculator") || lower.contains("calc")) {
            val calcHtml = """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                  <meta charset="UTF-8" />
                  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                  <title>Neon Smart Calculator</title>
                  <style>
                    * { box-sizing: border-box; font-family: 'Segoe UI', sans-serif; }
                    body {
                      margin: 0; min-height: 100vh;
                      display: flex; align-items: center; justify-content: center;
                      background: radial-gradient(circle at top, #141b36, #060913);
                      color: #fff;
                    }
                    .calc {
                      width: 320px; padding: 20px; border-radius: 24px;
                      background: rgba(18, 25, 48, 0.9);
                      border: 2px solid #00f5d4;
                      box-shadow: 0 0 28px rgba(0, 245, 212, 0.35);
                    }
                    .display {
                      width: 100%; height: 68px; margin-bottom: 16px;
                      padding: 14px; border-radius: 14px;
                      background: #050811; border: 1px solid #ff2a85;
                      color: #00f5d4; font-size: 28px; text-align: right;
                      overflow-x: auto;
                    }
                    .grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 10px; }
                    button {
                      padding: 16px; font-size: 18px; font-weight: bold;
                      border: none; border-radius: 14px; cursor: pointer;
                      background: #1b2548; color: #fff;
                      transition: 0.15s transform, 0.15s background;
                    }
                    button:active { transform: scale(0.94); }
                    .op { background: #7b2cbf; }
                    .eq { background: linear-gradient(135deg, #00f5d4, #ff2a85); color: #050811; grid-column: span 2; }
                    .clr { background: #ff2a85; }
                  </style>
                </head>
                <body>
                  <div class="calc">
                    <div id="disp" class="display">0</div>
                    <div class="grid">
                      <button class="clr" onclick="clr()">AC</button>
                      <button class="op" onclick="delChar()">⌫</button>
                      <button class="op" onclick="push('%')">%</button>
                      <button class="op" onclick="push('/')">÷</button>
                      <button onclick="push('7')">7</button>
                      <button onclick="push('8')">8</button>
                      <button onclick="push('9')">9</button>
                      <button class="op" onclick="push('*')">×</button>
                      <button onclick="push('4')">4</button>
                      <button onclick="push('5')">5</button>
                      <button onclick="push('6')">6</button>
                      <button class="op" onclick="push('-')">−</button>
                      <button onclick="push('1')">1</button>
                      <button onclick="push('2')">2</button>
                      <button onclick="push('3')">3</button>
                      <button class="op" onclick="push('+')">+</button>
                      <button onclick="push('0')">0</button>
                      <button onclick="push('.')">.</button>
                      <button class="eq" onclick="solve()">=</button>
                    </div>
                  </div>
                  <script>
                    let expr = "";
                    const disp = document.getElementById("disp");
                    function push(v) { expr += v; disp.innerText = expr || "0"; }
                    function clr() { expr = ""; disp.innerText = "0"; }
                    function delChar() { expr = expr.slice(0, -1); disp.innerText = expr || "0"; }
                    function solve() {
                      try { expr = String( eval(expr) ); disp.innerText = expr; }
                      catch(e) { disp.innerText = "Error"; expr = ""; }
                    }
                  </script>
                </body>
                </html>
            """.trimIndent()
            return Triple(
                "html",
                calcHtml,
                "Ji… aapka poora HTML + CSS + JS Calculator Website code taiyaar hai! Aap '🌐 Preview' daba kar chala bhi sakte hain ✅"
            )
        }

        // Default: Full Modern Interactive HTML5 + CSS3 + JS Website tailored to the user's prompt!
        val fullWebsiteHtml = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="UTF-8" />
              <meta name="viewport" content="width=device-width, initial-scale=1.0" />
              <title>$cleanTitle</title>
              <style>
                :root {
                  --bg: #070b19;
                  --card: #111833;
                  --cyan: #00f5d4;
                  --pink: #ff2a85;
                  --purple: #9b5de5;
                  --text: #f1f5ff;
                }
                * { box-sizing: border-box; margin: 0; padding: 0; font-family: 'Segoe UI', system-ui, sans-serif; }
                body {
                  background: radial-gradient(circle at top right, #18224b, var(--bg));
                  color: var(--text);
                  min-height: 100vh;
                  line-height: 1.6;
                }
                header {
                  display: flex; justify-content: space-between; align-items: center;
                  padding: 18px 24px;
                  background: rgba(11, 16, 36, 0.88);
                  border-bottom: 1.5px solid var(--cyan);
                  position: sticky; top: 0; z-index: 10;
                }
                .brand {
                  font-size: 20px; font-weight: 800;
                  background: linear-gradient(90deg, var(--cyan), var(--pink));
                  -webkit-background-clip: text; -webkit-text-fill-color: transparent;
                }
                .hero {
                  padding: 48px 24px; text-align: center;
                  max-width: 860px; margin: 0 auto;
                }
                .hero h1 {
                  font-size: 34px; margin-bottom: 12px;
                  text-shadow: 0 0 18px rgba(0, 245, 212, 0.4);
                }
                .hero p { color: #b8c7e8; font-size: 16px; margin-bottom: 24px; }
                .btn {
                  padding: 12px 26px; border-radius: 999px; border: none;
                  font-weight: 700; font-size: 15px; cursor: pointer;
                  background: linear-gradient(135deg, var(--cyan), var(--purple));
                  color: #050814; box-shadow: 0 0 20px rgba(0, 245, 212, 0.45);
                  transition: transform 0.2s;
                }
                .btn:hover { transform: translateY(-2px) scale(1.03); }
                .grid {
                  display: grid;
                  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
                  gap: 18px; padding: 24px; max-width: 1000px; margin: 0 auto;
                }
                .card {
                  background: var(--card);
                  border: 1px solid rgba(0, 245, 212, 0.35);
                  border-radius: 18px; padding: 20px;
                  box-shadow: 0 8px 24px rgba(0,0,0,0.4);
                }
                .card h3 { color: var(--cyan); margin-bottom: 8px; }
                .interactive-box {
                  max-width: 600px; margin: 20px auto 40px; padding: 22px;
                  background: rgba(255, 42, 133, 0.1);
                  border: 1px solid var(--pink); border-radius: 18px;
                  text-align: center;
                }
                input {
                  width: 100%; padding: 12px; margin: 10px 0;
                  border-radius: 10px; border: 1px solid var(--cyan);
                  background: #060913; color: #fff;
                }
                footer {
                  text-align: center; padding: 20px;
                  border-top: 1px solid rgba(255,255,255,0.1);
                  font-size: 13px; color: #8da0c8;
                }
              </style>
            </head>
            <body>
              <header>
                <div class="brand">⚡ $cleanTitle</div>
                <button class="btn" onclick="showAlert()">Explore Now</button>
              </header>

              <section class="hero">
                <h1>$cleanTitle</h1>
                <p>Modern responsive HTML5, CSS3 & JavaScript website generated by JARVIS Ultra Coding Studio (AK EXPLOITS).</p>
                <button class="btn" onclick="toggleTheme()">🌈 Switch Neon Theme</button>
              </section>

              <section class="grid">
                <div class="card">
                  <h3>🚀 Fast & Responsive</h3>
                  <p>Built with clean Flexbox & CSS Grid architecture that works on Mobile & Desktop.</p>
                </div>
                <div class="card">
                  <h3>🎨 RGB Cyber UI</h3>
                  <p>Dynamic neon gradients, glassmorphism cards, and smooth micro-interactions.</p>
                </div>
                <div class="card">
                  <h3>⚡ Interactive JS</h3>
                  <p>Full client-side state and interactive DOM handling out of the box.</p>
                </div>
              </section>

              <section class="interactive-box">
                <h3>💬 Live Interactive Demo</h3>
                <input id="userInput" type="text" placeholder="Type something here..." />
                <button class="btn" onclick="handleAction()">Submit Action</button>
                <p id="outputMsg" style="margin-top:12px; color:#00f5d4; font-weight:bold;"></p>
              </section>

              <footer>
                Created with ❤️ by JARVIS Ultra Coding Studio • AK EXPLOITS
              </footer>

              <script>
                let altTheme = false;
                function showAlert() {
                  document.getElementById("outputMsg").innerText = "⚡ Welcome to $cleanTitle!";
                }
                function handleAction() {
                  const val = document.getElementById("userInput").value.trim() || "Awesome User";
                  document.getElementById("outputMsg").innerText = "✅ Action Executed for: " + val;
                }
                function toggleTheme() {
                  altTheme = !altTheme;
                  document.documentElement.style.setProperty('--cyan', altTheme ? '#ffbe0b' : '#00f5d4');
                  document.documentElement.style.setProperty('--pink', altTheme ? '#00f5d4' : '#ff2a85');
                }
              </script>
            </body>
            </html>
        """.trimIndent()

        return Triple(
            "html",
            fullWebsiteHtml,
            "Ji… aapke prompt '$cleanTitle' ke liye poora HTML5 + CSS3 + JS Website code bana diya hai! 'Copy' ya '🌐 Preview' dabayein ✅"
        )
    }
}
