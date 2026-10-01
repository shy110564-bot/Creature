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

class JarvisSpeechManager(
    private val context: Context,
    private val onSpeechResult: (String) -> Unit
) : TextToSpeech.OnInitListener {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var speechRecognizer: SpeechRecognizer? = null
    private var lastListenStartMs = 0L

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

    // Watchdog that guarantees the mic keeps listening in the background 24/7 while Continuous Mode is ON
    private val backgroundWatchdog = object : Runnable {
        override fun run() {
            if (_isContinuousMicOn.value) {
                val now = System.currentTimeMillis()
                if (!_isSpeaking.value && (!_isListening.value || (now - lastListenStartMs > 14000L))) {
                    triggerListenCycle()
                }
                mainHandler.postDelayed(this, 2600L)
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
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _isSpeaking.value = true
                    _audioAmplitude.value = 0.85f
                }

                override fun onDone(utteranceId: String?) {
                    _isSpeaking.value = false
                    _audioAmplitude.value = 0.15f
                    scheduleRestartIfContinuous(350L)
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    _isSpeaking.value = false
                    _audioAmplitude.value = 0.15f
                    scheduleRestartIfContinuous(350L)
                }
            })
            isTtsReady = true
        }
    }

    fun speak(
        cleanText: String,
        voiceSettings: VoiceSettings,
        mood: JarvisMood,
        isChupMode: Boolean = false
    ) {
        if (isChupMode || cleanText.isBlank()) {
            scheduleRestartIfContinuous(400L)
            return
        }
        if (!isTtsReady) {
            scheduleRestartIfContinuous(400L)
            return
        }

        // Pause mic briefly while JARVIS speaks so it doesn't hear its own TTS output
        mainHandler.post {
            runCatching { speechRecognizer?.cancel() }
            _isListening.value = false
        }

        val finalPitch = (voiceSettings.pitch * mood.pitchMultiplier).coerceIn(0.6f, 1.8f)
        val finalSpeed = (if (voiceSettings.whisperMode) voiceSettings.speed * 0.82f else voiceSettings.speed * mood.speedMultiplier)
            .coerceIn(0.5f, 1.5f)

        tts?.setPitch(finalPitch)
        tts?.setSpeechRate(finalSpeed)

        val utteranceId = UUID.randomUUID().toString()
        _isSpeaking.value = true
        tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun stopSpeaking() {
        tts?.stop()
        _isSpeaking.value = false
        _audioAmplitude.value = 0.15f
    }

    fun startListening() {
        _isContinuousMicOn.value = true
        ensureBackgroundVoiceServiceRunning()
        stopSpeaking()
        mainHandler.removeCallbacks(backgroundWatchdog)
        mainHandler.postDelayed(backgroundWatchdog, 2600L)
        triggerListenCycle()
    }

    private fun ensureBackgroundVoiceServiceRunning() {
        runCatching {
            val serviceIntent = Intent(context, JarvisVoiceService::class.java).apply {
                putExtra(JarvisVoiceService.EXTRA_STATUS, "🎤 Always-On Background Mic Active — Boliye Ji 💕")
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
        if (_isContinuousMicOn.value && !_isSpeaking.value) {
            triggerListenCycle()
        }
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
                                _livePartialTranscript.value = "🎤 Always-On Mic: Sun rahi hun ji…"
                            }

                            override fun onBeginningOfSpeech() {
                                _isListening.value = true
                                lastListenStartMs = System.currentTimeMillis()
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
                                _livePartialTranscript.value = ""
                                _audioAmplitude.value = 0.15f
                                if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ||
                                    error == SpeechRecognizer.ERROR_CLIENT ||
                                    error == SpeechRecognizer.ERROR_SERVER
                                ) {
                                    recreateRecognizer()
                                }
                                if (_isContinuousMicOn.value && !_isSpeaking.value) {
                                    scheduleRestartIfContinuous(450L)
                                }
                            }

                            override fun onResults(results: Bundle?) {
                                _isListening.value = false
                                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                val best = matches?.firstOrNull()?.trim().orEmpty()
                                _livePartialTranscript.value = ""
                                if (best.isNotEmpty()) {
                                    onSpeechResult(best)
                                } else if (_isContinuousMicOn.value) {
                                    scheduleRestartIfContinuous(350L)
                                }
                            }

                            override fun onPartialResults(partialResults: Bundle?) {
                                val partial = partialResults
                                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                    ?.firstOrNull()
                                    .orEmpty()
                                if (partial.isNotBlank()) {
                                    _livePartialTranscript.value = partial
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
                    // en-IN recognizes Hinglish + English app names + Hindi phrases cleanly
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-IN")
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1600L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1300L)
                }
                lastListenStartMs = System.currentTimeMillis()
                speechRecognizer?.startListening(intent)
                _isListening.value = true
            }.onFailure {
                _isListening.value = false
                recreateRecognizer()
                if (_isContinuousMicOn.value) {
                    scheduleRestartIfContinuous(900L)
                }
            }
        }
    }

    fun stopListening() {
        _isContinuousMicOn.value = false
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
        mainHandler.removeCallbacks(restartRunnable)
        mainHandler.removeCallbacks(backgroundWatchdog)
        runCatching {
            speechRecognizer?.destroy()
            tts?.stop()
            tts?.shutdown()
        }
    }
}
