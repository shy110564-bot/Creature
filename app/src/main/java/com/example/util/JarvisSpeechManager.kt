package com.example.util

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.example.data.model.JarvisMood
import com.example.data.model.VoiceSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

class JarvisSpeechManager(
    private val context: Context,
    private val onSpeechResult: (String) -> Unit
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var speechRecognizer: SpeechRecognizer? = null

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _livePartialTranscript = MutableStateFlow("")
    val livePartialTranscript: StateFlow<String> = _livePartialTranscript.asStateFlow()

    private val _audioAmplitude = MutableStateFlow(0.2f)
    val audioAmplitude: StateFlow<Float> = _audioAmplitude.asStateFlow()

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
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    _isSpeaking.value = false
                    _audioAmplitude.value = 0.15f
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
        if (isChupMode || cleanText.isBlank()) return
        if (!isTtsReady) return

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
        stopSpeaking()
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _isListening.value = false
            return
        }
        runCatching {
            if (speechRecognizer == null) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {
                            _isListening.value = true
                            _livePartialTranscript.value = "Sun rahi hun ji… boliye 🎤"
                        }

                        override fun onBeginningOfSpeech() {
                            _isListening.value = true
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
                        }

                        override fun onResults(results: Bundle?) {
                            _isListening.value = false
                            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            val best = matches?.firstOrNull()?.trim().orEmpty()
                            _livePartialTranscript.value = ""
                            if (best.isNotEmpty()) {
                                onSpeechResult(best)
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
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }
            speechRecognizer?.startListening(intent)
            _isListening.value = true
        }.onFailure {
            _isListening.value = false
        }
    }

    fun stopListening() {
        runCatching {
            speechRecognizer?.stopListening()
        }
        _isListening.value = false
        _livePartialTranscript.value = ""
    }

    fun release() {
        runCatching {
            speechRecognizer?.destroy()
            tts?.stop()
            tts?.shutdown()
        }
    }

    companion object {
        fun buildSsmlPreview(settings: VoiceSettings): String {
            val ratePct = (settings.speed * 100).toInt()
            val pitchSt = String.format(Locale.US, "+%.1fst", (settings.pitch - 1.0f) * 10f)
            return """
<speak>
  <prosody pitch="$pitchSt" rate="$ratePct%">
    Ji… <break time="400ms"/>
    <prosody pitch="+2st" rate="85%">
      JARVIS sun rahi hun…
    </prosody>
    <break time="500ms"/>
    <prosody pitch="+4st" rate="92%">
      Abhi karti hun ji…
    </prosody>
    <break time="300ms"/>
    <prosody pitch="+2st" rate="88%">
      Ho gaya ji 💕
    </prosody>
  </prosody>
</speak>
            """.trimIndent()
        }
    }
}
