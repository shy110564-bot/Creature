package com.example.util

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.example.data.model.JarvisMood
import com.example.data.model.VoiceSettings
import com.example.service.JarvisVoiceService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue

class JarvisSpeechManager(
    private val context: Context,
    private val onSpeechResult: (String) -> Unit
) : TextToSpeech.OnInitListener {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var speechRecognizer: SpeechRecognizer? = null
    private var lastListenStartMs = 0L

    // Tracks what JARVIS is currently speaking so we can filter out self-echo while still hearing the user!
    @Volatile
    private var currentTtsCleanText: String = ""

    // Tracks partial speech recognized while JARVIS is speaking in case SpeechRecognizer finishes without onResults
    @Volatile
    private var lastPartialWhileSpeaking: String = ""

    // Queue of user commands spoken while JARVIS was talking — processed immediately when utterance finishes!
    private val pendingUserSpeechQueue = ConcurrentLinkedQueue<String>()

    private val _isContinuousMicOn = MutableStateFlow(false)
    val isContinuousMicOn: StateFlow<Boolean> = _isContinuousMicOn.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _livePartialTranscript = MutableStateFlow("")
    val livePartialTranscript: StateFlow<String> = _livePartialTranscript.asStateFlow()

    private val _audioAmplitude = MutableStateFlow(0.2f)
    val audioAmplitude: StateFlow<Float> = _audioAmplitude.asStateFlow()

    // Watchdog that guarantees the mic keeps listening in the background 24/7 (even while speaking!)
    private val backgroundWatchdog = object : Runnable {
        override fun run() {
            if (_isContinuousMicOn.value) {
                val now = System.currentTimeMillis()
                if (!_isListening.value || (now - lastListenStartMs > 12000L)) {
                    triggerListenCycle()
                }
                mainHandler.postDelayed(this, 2000L)
            }
        }
    }

    init {
        runCatching {
            tts = TextToSpeech(context.applicationContext, this)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val hindiLocale = Locale.forLanguageTag("hi-IN")
            val indianEnglishLocale = Locale.forLanguageTag("en-IN")
            val res = tts?.setLanguage(hindiLocale)
            if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(indianEnglishLocale)
            }
            // Pick a smooth natural female Indian voice if available in system voices
            runCatching {
                val voices = tts?.voices
                val bestFemaleVoice = voices?.firstOrNull { v ->
                    (v.locale.language == "hi" || (v.locale.language == "en" && v.locale.country.equals("IN", ignoreCase = true))) &&
                    (v.name.contains("female", ignoreCase = true) || v.name.contains("hie", ignoreCase = true) || v.name.contains("c-local", ignoreCase = true))
                } ?: voices?.firstOrNull { v ->
                    v.locale.language == "hi" || (v.locale.language == "en" && v.locale.country.equals("IN", ignoreCase = true))
                }
                if (bestFemaleVoice != null) {
                    tts?.voice = bestFemaleVoice
                }
            }

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _isSpeaking.value = true
                    _audioAmplitude.value = 0.85f
                    // Keep listening even while JARVIS is speaking!
                    if (_isContinuousMicOn.value && !_isListening.value) {
                        scheduleRestartIfContinuous(150L)
                    }
                }

                override fun onDone(utteranceId: String?) {
                    onTtsFinished()
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    onTtsFinished()
                }
            })
            isTtsReady = true
        }
    }

    private fun onTtsFinished() {
        _isSpeaking.value = false
        currentTtsCleanText = ""
        _audioAmplitude.value = 0.15f

        val leftoverPartial = lastPartialWhileSpeaking.trim()
        lastPartialWhileSpeaking = ""
        if (leftoverPartial.isNotEmpty() && pendingUserSpeechQueue.isEmpty()) {
            pendingUserSpeechQueue.offer(leftoverPartial)
        }

        // If the user spoke while JARVIS was talking, immediately process their queued command now!
        val nextUserSpeech = pendingUserSpeechQueue.poll()
        if (!nextUserSpeech.isNullOrBlank()) {
            mainHandler.post {
                _livePartialTranscript.value = ""
                onSpeechResult(nextUserSpeech)
            }
        }
        scheduleRestartIfContinuous(160L)
    }

    private fun isEchoOfJarvisTts(recognized: String): Boolean {
        val ttsText = currentTtsCleanText.lowercase().trim()
        if (!_isSpeaking.value || ttsText.isBlank()) return false
        val recLower = recognized.lowercase().trim()
        if (recLower.length < 3) return true
        // If user says a clear command keyword, NEVER treat it as echo
        val commandKeywords = listOf(
            "open", "kholo", "search", "dhundo", "call", "message", "whatsapp",
            "youtube", "scroll", "upar", "neeche", "niche", "click", "view channel",
            "channel", "video", "chalao", "lagao", "bajao", "back", "home", "torch",
            "stop", "ruko", "chup", "rgb", "time", "alarm", "calendar", "banaya", "exploits"
        )
        if (commandKeywords.any { recLower.contains(it) && !ttsText.contains(recLower) }) {
            return false
        }
        // Check word overlap with JARVIS's own ongoing TTS
        val recWords = recLower.split(" ").filter { it.length > 2 }
        if (recWords.isEmpty()) return false
        val matchCount = recWords.count { word -> ttsText.contains(word) }
        return (matchCount.toFloat() / recWords.size.toFloat()) >= 0.75f
    }

    private fun cleanTextForNaturalSpeech(text: String): String {
        return text
            .replace(Regex("\\[CMD:[^\\]]*\\]"), "")
            .replace(Regex("\\(pause\\)|\\(breath\\)|\\(smile\\)|\\(soft\\)|\\(excited\\)|\\(giggle\\)|\\(sigh\\)|\\(shy\\)|\\(proud\\)|\\(loving\\)|\\(teasing\\)|\\(angry\\)"), "")
            .replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1")
            .replace(Regex("\\*([^*]+)\\*"), "$1")
            .replace(Regex("`([^`]+)`"), "$1")
            .replace(Regex("(?i)\\b(https?://\\S+|www\\.\\S+)\\b"), "")
            .replace(Regex("[✅⚡🔴🧠💻👆⬆️⬇️🔙🏠📑📸📖🎬▶️👁️⏰📞💬🔦📅🎵💕🔒🛡️⚠️👌✨🎉🔥]"), "")
            .replace(Regex("[\\p{So}\\p{Cn}]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun speak(
        cleanText: String,
        voiceSettings: VoiceSettings,
        mood: JarvisMood,
        isChupMode: Boolean = false
    ) {
        val sanitized = cleanTextForNaturalSpeech(cleanText)
        if (isChupMode || sanitized.isBlank()) {
            scheduleRestartIfContinuous(200L)
            return
        }
        if (!isTtsReady) {
            scheduleRestartIfContinuous(200L)
            return
        }

        currentTtsCleanText = sanitized

        // Rule #3 & #8: Voice speed 85-88% for clear, natural girl pronunciation, pitch medium-high
        val finalPitch = (voiceSettings.pitch * mood.pitchMultiplier).coerceIn(0.95f, 1.20f)
        val finalSpeed = (voiceSettings.speed * mood.speedMultiplier).coerceIn(0.82f, 1.02f)

        tts?.setPitch(finalPitch)
        tts?.setSpeechRate(finalSpeed)

        val utteranceId = UUID.randomUUID().toString()
        _isSpeaking.value = true
        tts?.speak(sanitized, TextToSpeech.QUEUE_FLUSH, null, utteranceId)

        // Ensure mic stays active while speaking so user can speak anytime and get answered right after!
        if (_isContinuousMicOn.value && !_isListening.value) {
            scheduleRestartIfContinuous(150L)
        }
    }

    fun stopSpeaking() {
        tts?.stop()
        _isSpeaking.value = false
        currentTtsCleanText = ""
        _audioAmplitude.value = 0.15f
    }

    fun startListening() {
        _isContinuousMicOn.value = true
        ensureBackgroundVoiceServiceRunning()
        mainHandler.removeCallbacks(backgroundWatchdog)
        mainHandler.postDelayed(backgroundWatchdog, 2000L)
        triggerListenCycle()
    }

    private fun ensureBackgroundVoiceServiceRunning() {
        runCatching {
            val serviceIntent = Intent(context, JarvisVoiceService::class.java).apply {
                putExtra(JarvisVoiceService.EXTRA_STATUS, "🎤 Always-On Mic Active — Bolte Rahiye Ji 💕")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }

    private fun scheduleRestartIfContinuous(delayMs: Long) {
        if (!_isContinuousMicOn.value) return
        mainHandler.removeCallbacks(restartRunnable)
        mainHandler.postDelayed(restartRunnable, delayMs)
    }

    private val restartRunnable = Runnable {
        if (_isContinuousMicOn.value) {
            triggerListenCycle()
        }
    }

    private fun selectBestSpeechCandidate(matches: List<String>?): String {
        if (matches.isNullOrEmpty()) return ""
        if (matches.size == 1) return matches[0].trim()

        val actionKeywords = listOf(
            "scroll", "upar", "neeche", "niche", "swipe", "click", "tap", "dabao",
            "kholo", "open", "chalao", "play", "lagao", "bajao", "search", "dhundo",
            "call", "message", "whatsapp", "torch", "back", "home", "recents", "padho",
            "padh", "read", "channel", "video", "exploits", "ak exploits", "banaya"
        )

        for (cand in matches) {
            val lower = cand.lowercase()
            if (actionKeywords.any { lower.contains(it) }) {
                return cand.trim()
            }
        }
        return matches[0].trim()
    }

    private fun recreateRecognizer() {
        runCatching {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        }
        speechRecognizer = null
    }

    private fun triggerListenCycle() {
        mainHandler.post {
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                _isListening.value = false
                return@post
            }
            runCatching {
                if (speechRecognizer == null) {
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context.applicationContext).apply {
                        setRecognitionListener(object : RecognitionListener {
                            override fun onReadyForSpeech(params: Bundle?) {
                                _isListening.value = true
                                lastListenStartMs = System.currentTimeMillis()
                                if (pendingUserSpeechQueue.isEmpty()) {
                                    _livePartialTranscript.value = "🎤 Always-On Mic: Sun rahi hun ji…"
                                }
                            }

                            override fun onBeginningOfSpeech() {
                                _isListening.value = true
                                lastListenStartMs = System.currentTimeMillis()
                                // RULE #1 — SUNNE KI SHAKTI: User bolna shuru kare -> TURANT CHUP ho jao
                                if (_isSpeaking.value) {
                                    stopSpeaking()
                                }
                            }

                            override fun onRmsChanged(rmsdB: Float) {
                                val normalized = ((rmsdB + 2f) / 12f).coerceIn(0.15f, 1.0f)
                                _audioAmplitude.value = normalized
                            }

                            override fun onBufferReceived(buffer: ByteArray?) {}

                            override fun onEndOfSpeech() {
                                _isListening.value = false
                                _audioAmplitude.value = 0.2f
                            }

                            override fun onError(error: Int) {
                                _isListening.value = false
                                _audioAmplitude.value = 0.15f

                                val savedPartial = lastPartialWhileSpeaking.trim()
                                lastPartialWhileSpeaking = ""

                                val liveCaptured = _livePartialTranscript.value
                                    .removePrefix("🎤 Always-On Mic: Sun rahi hun ji…")
                                    .removePrefix("👂 Sun rahi hun: ")
                                    .trim()

                                val textToProcess = when {
                                    savedPartial.isNotEmpty() && !isEchoOfJarvisTts(savedPartial) -> savedPartial
                                    liveCaptured.isNotEmpty() && !isEchoOfJarvisTts(liveCaptured) -> liveCaptured
                                    else -> ""
                                }

                                if (textToProcess.isNotEmpty()) {
                                    _livePartialTranscript.value = ""
                                    onSpeechResult(textToProcess)
                                }

                                if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ||
                                    error == SpeechRecognizer.ERROR_CLIENT ||
                                    error == SpeechRecognizer.ERROR_SERVER
                                ) {
                                    recreateRecognizer()
                                }
                                if (_isContinuousMicOn.value) {
                                    scheduleRestartIfContinuous(200L)
                                }
                            }

                            override fun onResults(results: Bundle?) {
                                _isListening.value = false
                                lastPartialWhileSpeaking = ""
                                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                val best = selectBestSpeechCandidate(matches)

                                if (best.isNotEmpty() && !isEchoOfJarvisTts(best)) {
                                    if (_isSpeaking.value) {
                                        // User spoke while JARVIS was speaking! Queue it so JARVIS answers as soon as current sentence finishes
                                        pendingUserSpeechQueue.offer(best)
                                        _livePartialTranscript.value = "⏳ Sun liya: \"$best\" (Abhi jawab deti hun…)"
                                    } else {
                                        _livePartialTranscript.value = ""
                                        onSpeechResult(best)
                                    }
                                }
                                if (_isContinuousMicOn.value) {
                                    scheduleRestartIfContinuous(180L)
                                }
                            }

                            override fun onPartialResults(partialResults: Bundle?) {
                                val partial = partialResults
                                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                    ?.firstOrNull()
                                    .orEmpty()
                                    .trim()
                                if (partial.isNotBlank() && !isEchoOfJarvisTts(partial)) {
                                    if (_isSpeaking.value) {
                                        stopSpeaking()
                                        lastPartialWhileSpeaking = partial
                                        _livePartialTranscript.value = "👂 Sun rahi hun: $partial"
                                    } else {
                                        _livePartialTranscript.value = partial
                                    }
                                }
                            }

                            override fun onEvent(eventType: Int, params: Bundle?) {}
                        })
                    }
                } else {
                    runCatching { speechRecognizer?.cancel() }
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                    )
                    // High-accuracy multilingual Hindi & Indian English recognition
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "hi-IN")
                    putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("en-IN", "hi-IN", "en-US"))
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1800L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1400L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1500L)
                }
                lastListenStartMs = System.currentTimeMillis()
                speechRecognizer?.startListening(intent)
                _isListening.value = true
            }.onFailure {
                _isListening.value = false
                recreateRecognizer()
                if (_isContinuousMicOn.value) {
                    scheduleRestartIfContinuous(500L)
                }
            }
        }
    }

    fun stopListening() {
        _isContinuousMicOn.value = false
        pendingUserSpeechQueue.clear()
        mainHandler.removeCallbacks(restartRunnable)
        mainHandler.removeCallbacks(backgroundWatchdog)
        mainHandler.post {
            runCatching {
                speechRecognizer?.stopListening()
                speechRecognizer?.cancel()
            }
            _isListening.value = false
            _livePartialTranscript.value = ""
        }
    }

    fun release() {
        _isContinuousMicOn.value = false
        pendingUserSpeechQueue.clear()
        mainHandler.removeCallbacks(restartRunnable)
        mainHandler.removeCallbacks(backgroundWatchdog)
        runCatching {
            speechRecognizer?.destroy()
            tts?.stop()
            tts?.shutdown()
        }
    }
}
