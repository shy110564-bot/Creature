package com.example.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.ChatMessageEntity
import com.example.data.local.JarvisDatabase
import com.example.data.local.MemoryFactEntity
import com.example.data.model.HealthFitnessState
import com.example.data.model.JarvisMood
import com.example.data.model.OrbVisualState
import com.example.data.model.PendingConfirmationAction
import com.example.data.model.ScreenMockState
import com.example.data.model.SixStepThought
import com.example.data.model.SmartHomeState
import com.example.data.model.SystemTelemetry
import com.example.data.model.VoiceSettings
import com.example.data.model.WakeState
import com.example.data.remote.JarvisAiEngine
import com.example.data.remote.PhoneActionCommand
import com.example.data.repository.JarvisRepository
import com.example.service.JarvisVoiceService
import com.example.util.JarvisSpeechManager
import com.example.util.PhoneControlExecutor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class JarvisScreen(val routeId: String, val label: String) {
    SPLASH("splash", "Splash"),
    HOME("home", "Home"),
    VOICE_CHAT("voice_chat", "Voice Chat"),
    SCREEN_SHARE("screen_share", "Screen Vision"),
    MOODS_GOD_MODE("moods_god_mode", "Phone Control"),
    SETTINGS("settings", "Settings"),
    VOICE_SETTINGS("voice_settings", "Voice Studio"),
    DEVELOPER_ABOUT("developer_about", "AK EXPLOITS")
}

data class OverlayNotification(
    val title: String,
    val message: String,
    val actionLabel: String = "Talk Now 💕"
)

class JarvisViewModel(application: Application) : AndroidViewModel(application) {

    private val database = JarvisDatabase.getDatabase(application)
    private val repository = JarvisRepository(application, database.jarvisDao())
    val phoneControl = PhoneControlExecutor(application)

    val speechManager = JarvisSpeechManager(application) { recognizedSpeech ->
        handleUserMessage(recognizedSpeech, fromVoice = true)
    }

    private val _currentScreen = MutableStateFlow(JarvisScreen.HOME)
    val currentScreen: StateFlow<JarvisScreen> = _currentScreen.asStateFlow()

    private val _screenHistory = mutableListOf<JarvisScreen>()

    val customApiKey: StateFlow<String> = repository.customApiKeyFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val messages: StateFlow<List<ChatMessageEntity>> = repository.allMessages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val memories: StateFlow<List<MemoryFactEntity>> = repository.allMemories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val selectedMood: StateFlow<JarvisMood> = repository.selectedMoodFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), JarvisMood.LOVING)

    val wakeState: StateFlow<WakeState> = repository.wakeStateFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WakeState.ACTIVE)

    val voiceSettings: StateFlow<VoiceSettings> = repository.voiceSettingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), VoiceSettings())

    private val _orbVisualState = MutableStateFlow(OrbVisualState.IDLE_LISTENING)
    val orbVisualState: StateFlow<OrbVisualState> = _orbVisualState.asStateFlow()

    private val _showHologramAvatar = MutableStateFlow(false)
    val showHologramAvatar: StateFlow<Boolean> = _showHologramAvatar.asStateFlow()

    private val _latestThought = MutableStateFlow(
        SixStepThought(
            literalInput = "JARVIS 5.0 Phone Control Ready",
            detectedIntent = "20-Category God Mode Active",
            userMoodEmoji = "⚡ Ready",
            contextMemoryUsed = "Rule #1 Exact Execution + Rule #2 Safety Active",
            replyStrategy = "Confirm Before & After -> 'Ho gaya ji ✅'",
            executedAction = "Listening for Hindi / English / Hinglish commands",
            tripletBreakdown = "[JARVIS Core | Ready | 20 Phone Categories]"
        )
    )
    val latestThought: StateFlow<SixStepThought> = _latestThought.asStateFlow()

    private val _systemTelemetry = MutableStateFlow(phoneControl.readSystemTelemetry())
    val systemTelemetry: StateFlow<SystemTelemetry> = _systemTelemetry.asStateFlow()

    val smartHomeState: StateFlow<SmartHomeState> = phoneControl.smartHomeState
    val healthState: StateFlow<HealthFitnessState> = phoneControl.healthState

    private val _screenMockState = MutableStateFlow(ScreenMockState())
    val screenMockState: StateFlow<ScreenMockState> = _screenMockState.asStateFlow()

    private val _capturedScreenBitmap = MutableStateFlow<Bitmap?>(null)
    val capturedScreenBitmap: StateFlow<Bitmap?> = _capturedScreenBitmap.asStateFlow()

    private val _overlayNotification = MutableStateFlow<OverlayNotification?>(null)
    val overlayNotification: StateFlow<OverlayNotification?> = _overlayNotification.asStateFlow()

    private val _pendingConfirmation = MutableStateFlow<PendingConfirmationAction?>(null)
    val pendingConfirmation: StateFlow<PendingConfirmationAction?> = _pendingConfirmation.asStateFlow()

    private val _showLockScreenWidget = MutableStateFlow(false)
    val showLockScreenWidget: StateFlow<Boolean> = _showLockScreenWidget.asStateFlow()

    private val _isForegroundServiceRunning = MutableStateFlow(false)
    val isForegroundServiceRunning: StateFlow<Boolean> = _isForegroundServiceRunning.asStateFlow()

    init {
        viewModelScope.launch {
            repository.seedInitialDataIfEmpty()
            refreshTelemetry()
        }
        viewModelScope.launch {
            speechManager.isListening.collect { listening ->
                if (listening) {
                    _orbVisualState.value = OrbVisualState.WAKE_EXPANDING
                } else if (!speechManager.isSpeaking.value) {
                    _orbVisualState.value = OrbVisualState.IDLE_LISTENING
                }
            }
        }
        viewModelScope.launch {
            speechManager.isSpeaking.collect { speaking ->
                if (speaking) {
                    _orbVisualState.value = OrbVisualState.SPEAKING_WAVE
                } else if (!speechManager.isListening.value) {
                    _orbVisualState.value = OrbVisualState.IDLE_LISTENING
                }
            }
        }
    }

    fun navigateTo(screen: JarvisScreen) {
        if (_currentScreen.value != screen) {
            if (_currentScreen.value != JarvisScreen.SPLASH) {
                _screenHistory.add(_currentScreen.value)
            }
            _currentScreen.value = screen
        }
    }

    fun navigateBack(): Boolean {
        return if (_screenHistory.isNotEmpty()) {
            _currentScreen.value = _screenHistory.removeAt(_screenHistory.lastIndex)
            true
        } else if (_currentScreen.value != JarvisScreen.HOME && _currentScreen.value != JarvisScreen.SPLASH) {
            _currentScreen.value = JarvisScreen.HOME
            true
        } else {
            false
        }
    }

    fun toggleHologramAvatar() {
        _showHologramAvatar.update { !it }
    }

    fun setLockScreenWidgetVisible(visible: Boolean) {
        _showLockScreenWidget.value = visible
    }

    fun confirmPendingAction() {
        val pending = _pendingConfirmation.value ?: return
        _pendingConfirmation.value = null
        viewModelScope.launch {
            val doneMsg = "Ji… (pause) aapke confirmation ke baad '${pending.triplet.action}' (${pending.triplet.target}) pura kar diya…\n(breath) Ho gaya ji ✅ Aur kuch bataiye?"
            repository.insertMessage(
                ChatMessageEntity(
                    isUser = false,
                    text = doneMsg,
                    spokenCleanText = JarvisAiEngine.stripVocalCuesForTts(doneMsg),
                    moodId = selectedMood.value.id,
                    detectedEmotion = "✅ Confirmed",
                    actionBadge = "✅ Confirmed: ${pending.triplet.action}",
                    thoughtSummary = "Confirmed [${pending.triplet.platform} | ${pending.triplet.action} | ${pending.triplet.target}]"
                )
            )
            speechManager.speak(
                cleanText = JarvisAiEngine.stripVocalCuesForTts(doneMsg),
                voiceSettings = voiceSettings.value,
                mood = selectedMood.value,
                isChupMode = wakeState.value == WakeState.CHUP_MODE
            )
        }
    }

    fun cancelPendingAction() {
        val pending = _pendingConfirmation.value ?: return
        _pendingConfirmation.value = null
        viewModelScope.launch {
            val cancelMsg = "Theek hai ji… '${pending.triplet.action}' cancel kar diya hai. Aapka data bilkul safe hai 🔒 ✅"
            repository.insertMessage(
                ChatMessageEntity(
                    isUser = false,
                    text = cancelMsg,
                    spokenCleanText = JarvisAiEngine.stripVocalCuesForTts(cancelMsg),
                    moodId = selectedMood.value.id,
                    detectedEmotion = "🛡️ Cancelled Safely",
                    actionBadge = "🛡️ Action Cancelled"
                )
            )
        }
    }

    fun triggerCareNotification(customTitle: String? = null, customMessage: String? = null) {
        val telemetry = phoneControl.readSystemTelemetry()
        val (title, msg) = if (customTitle != null && customMessage != null) {
            customTitle to customMessage
        } else if (telemetry.batteryPct <= 20 && !telemetry.isCharging) {
            "◉ JARVIS • Battery Alert 🔋" to "Ji, battery ${telemetry.batteryPct}% hai, charge lagao na 💕"
        } else {
            "◉ JARVIS • Phone Control Ready ⚡" to "Ji… 20-Category Phone Control active hai. Boliye kya open ya control karun? ✅"
        }
        _overlayNotification.value = OverlayNotification(title = title, message = msg)
        speechManager.speak(
            cleanText = JarvisAiEngine.stripVocalCuesForTts(msg),
            voiceSettings = voiceSettings.value,
            mood = selectedMood.value,
            isChupMode = wakeState.value == WakeState.CHUP_MODE
        )
    }

    fun dismissOverlayNotification() {
        _overlayNotification.value = null
    }

    fun refreshTelemetry() {
        _systemTelemetry.value = phoneControl.readSystemTelemetry()
    }

    fun toggleTorch() {
        val target = !_systemTelemetry.value.torchOn
        phoneControl.toggleTorch(target)
        refreshTelemetry()
        val msg = if (target) "Ji… flashlight ON kar di hai, ho gaya ji ✅" else "Ji… flashlight OFF kar di hai, ho gaya ji ✅"
        speechManager.speak(
            cleanText = msg,
            voiceSettings = voiceSettings.value,
            mood = selectedMood.value,
            isChupMode = wakeState.value == WakeState.CHUP_MODE
        )
    }

    fun updateSystemVolume(percent: Int) {
        phoneControl.setMusicVolume(percent)
        refreshTelemetry()
    }

    fun updateSystemBrightness(percent: Int) {
        phoneControl.setBrightness(percent)
        refreshTelemetry()
    }

    fun selectMood(mood: JarvisMood, speakGreeting: Boolean = true) {
        viewModelScope.launch {
            repository.setMood(mood)
            if (speakGreeting) {
                speechManager.speak(
                    cleanText = JarvisAiEngine.stripVocalCuesForTts(mood.sampleLine),
                    voiceSettings = voiceSettings.value,
                    mood = mood,
                    isChupMode = wakeState.value == WakeState.CHUP_MODE
                )
            }
        }
    }

    fun updateWakeState(state: WakeState) {
        viewModelScope.launch {
            repository.setWakeState(state)
            speechManager.speak(
                cleanText = JarvisAiEngine.stripVocalCuesForTts(state.hindiStatus),
                voiceSettings = voiceSettings.value,
                mood = selectedMood.value,
                isChupMode = state == WakeState.CHUP_MODE || state == WakeState.OFF
            )
        }
    }

    fun toggleForegroundService() {
        val app = getApplication<Application>()
        val nextState = !_isForegroundServiceRunning.value
        val serviceIntent = Intent(app, JarvisVoiceService::class.java).apply {
            putExtra(JarvisVoiceService.EXTRA_STATUS, wakeState.value.hindiStatus)
        }
        runCatching {
            if (nextState) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    app.startForegroundService(serviceIntent)
                } else {
                    app.startService(serviceIntent)
                }
            } else {
                app.stopService(serviceIntent)
            }
            _isForegroundServiceRunning.value = nextState
        }
    }

    fun updateVoiceSettings(newSettings: VoiceSettings) {
        viewModelScope.launch {
            repository.updateVoiceSettings(newSettings)
        }
    }

    fun saveCustomApiKey(apiKey: String) {
        viewModelScope.launch {
            repository.saveCustomApiKey(apiKey)
            val msg = if (apiKey.isNotBlank()) {
                "Ji… (smile) Gemini API Key save ho gayi hai ✅ Ab main aapki har baat aur sawaal ka jawab de sakti hun 💕"
            } else {
                "Ji… Custom API Key hata di gayi hai ✅"
            }
            speechManager.speak(
                cleanText = JarvisAiEngine.stripVocalCuesForTts(msg),
                voiceSettings = voiceSettings.value,
                mood = selectedMood.value,
                isChupMode = wakeState.value == WakeState.CHUP_MODE
            )
        }
    }

    fun testVoiceSample() {
        val sample = "Ji… JARVIS sun rahi hun… AK EXPLOITS ne mujhe banaya hai… Boliye ji, abhi karti hun… Ho gaya ji"
        speechManager.speak(
            cleanText = sample,
            voiceSettings = voiceSettings.value,
            mood = selectedMood.value,
            isChupMode = false
        )
    }

    fun setCapturedScreenBitmap(bitmap: Bitmap?) {
        _capturedScreenBitmap.value = bitmap
        if (bitmap != null) {
            _screenMockState.update {
                it.copy(
                    currentAppTitle = "Uploaded Screenshot (Live Vision OCR)",
                    headlineText = "Custom User Screen Loaded (${bitmap.width}x${bitmap.height})"
                )
            }
        }
    }

    fun performScreenShareControl(actionType: String, payload: String = "") {
        when (actionType) {
            "CLICK" -> {
                val btnName = payload.ifBlank { "Blue Confirm Button ('Track Order')" }
                _screenMockState.update {
                    it.copy(
                        lastClickedButton = btnName,
                        subText = "✅ Clicked '$btnName' — Ho gaya ji ✅"
                    )
                }
                handleUserMessage("Neeche wale blue button pe click karo")
            }
            "SCROLL" -> {
                val newOffset = (_screenMockState.value.scrollOffset + 240) % 960
                val nextHeadline = when (newOffset) {
                    240 -> "WhatsApp • Rahul: 'Bhai kab aa raha hai?'"
                    480 -> "YouTube Music • Playing: 'Arijit Singh Best Hits' 🎵"
                    720 -> "System Battery: ${_systemTelemetry.value.batteryPct}% Healthy ⚡"
                    else -> "Your order #408-9921 has been shipped 📦"
                }
                _screenMockState.update {
                    it.copy(
                        scrollOffset = newOffset,
                        headlineText = nextHeadline,
                        subText = "Scrolled to offset ${newOffset}px • Ho gaya ji ✅"
                    )
                }
                handleUserMessage("Scroll karo")
            }
            "TYPE" -> {
                val textToType = payload.ifBlank { "Main aa raha hun ji" }
                _screenMockState.update {
                    it.copy(
                        typedFieldValue = textToType,
                        subText = "Typed '$textToType' into active field • Ho gaya ji ✅"
                    )
                }
                handleUserMessage("Type karo '$textToType'")
            }
            "READ_OCR" -> {
                handleUserMessage("Screen padho")
            }
            "TRANSLATE" -> {
                _screenMockState.update {
                    it.copy(
                        subText = "Hindi Translation: 'Aapka order bhej diya gaya hai aur kal raat 8 baje tak pahunchega 📦'"
                    )
                }
                handleUserMessage("Screen translate karo")
            }
            "STOP_TOGGLE" -> {
                val nextLive = !_screenMockState.value.isSharingLive
                _screenMockState.update { it.copy(isSharingLive = nextLive) }
            }
        }
    }

    fun addMemoryFact(category: String, title: String, detail: String) {
        if (title.isBlank() || detail.isBlank()) return
        viewModelScope.launch {
            repository.insertMemory(
                MemoryFactEntity(
                    category = category,
                    title = title.trim(),
                    detail = detail.trim()
                )
            )
        }
    }

    fun deleteMemoryFact(id: Long) {
        viewModelScope.launch {
            repository.deleteMemory(id)
        }
    }

    fun clearConversation() {
        viewModelScope.launch {
            repository.clearChat()
            repository.seedInitialDataIfEmpty()
        }
    }

    fun handleUserMessage(
        rawInput: String,
        fromVoice: Boolean = false,
        attachedBitmap: Bitmap? = _capturedScreenBitmap.value
    ) {
        val text = rawInput.trim()
        if (text.isEmpty()) return

        viewModelScope.launch {
            autoExtractMemoryIfPresent(text)

            val userEmotion = JarvisAiEngine.detectUserEmotion(text)
            repository.insertMessage(
                ChatMessageEntity(
                    isUser = true,
                    text = text,
                    spokenCleanText = text,
                    moodId = selectedMood.value.id,
                    detectedEmotion = userEmotion
                )
            )

            _orbVisualState.value = OrbVisualState.THINKING_PURPLE
            delay(280)

            val recent50 = repository.getRecent50Messages()
            val memoryList = repository.getMemoriesSnapshot()

            val result = JarvisAiEngine.generateJarvisReply(
                userInput = text,
                currentMood = selectedMood.value,
                recentHistory = recent50,
                memories = memoryList,
                screenState = _screenMockState.value,
                attachedBitmap = attachedBitmap,
                customApiKey = customApiKey.value
            )

            _latestThought.value = result.sixStepThought

            if (result.pendingConfirmation != null) {
                _pendingConfirmation.value = result.pendingConfirmation
            }

            if (result.suggestedMood != null) {
                repository.setMood(result.suggestedMood)
            }

            result.actionToExecute?.let { cmd ->
                applyAndExecutePhoneCommand(cmd)
            }

            val finalMood = result.suggestedMood ?: selectedMood.value
            repository.insertMessage(
                ChatMessageEntity(
                    isUser = false,
                    text = result.rawReplyWithCues,
                    spokenCleanText = result.cleanSpokenText,
                    moodId = finalMood.id,
                    detectedEmotion = result.detectedUserEmotion,
                    actionBadge = result.actionToExecute?.badgeLabel,
                    thoughtSummary = result.sixStepThought.tripletBreakdown
                )
            )

            val isSilent = wakeState.value == WakeState.CHUP_MODE || wakeState.value == WakeState.OFF
            if (!isSilent) {
                speechManager.speak(
                    cleanText = result.cleanSpokenText,
                    voiceSettings = voiceSettings.value,
                    mood = finalMood,
                    isChupMode = false
                )
            } else {
                _orbVisualState.value = OrbVisualState.IDLE_LISTENING
            }
        }
    }

    private suspend fun applyAndExecutePhoneCommand(cmd: PhoneActionCommand) {
        when (cmd) {
            is PhoneActionCommand.WakeStateChange -> {
                val target = runCatching { WakeState.valueOf(cmd.targetState) }
                    .getOrDefault(WakeState.ACTIVE)
                repository.setWakeState(target)
            }
            is PhoneActionCommand.ScreenAction -> {
                when (cmd.actionType) {
                    "START_SHARE" -> _screenMockState.update {
                        it.copy(
                            isSharingLive = true,
                            activeViewers = (it.activeViewers + "${cmd.value} (Live Connected)").distinct()
                        )
                    }
                    "STOP_SHARE" -> _screenMockState.update { it.copy(isSharingLive = false) }
                    "CLICK" -> _screenMockState.update {
                        it.copy(lastClickedButton = cmd.value, subText = "✅ Clicked '${cmd.value}'")
                    }
                    "SCROLL" -> _screenMockState.update {
                        it.copy(scrollOffset = (it.scrollOffset + 240) % 960)
                    }
                    "TYPE" -> _screenMockState.update {
                        it.copy(typedFieldValue = cmd.value, subText = "Typed '${cmd.value}' ✅")
                    }
                }
            }
            is PhoneActionCommand.MultiCommandChain -> {
                cmd.commands.forEach { sub ->
                    applyAndExecutePhoneCommand(sub)
                }
            }
            else -> {
                phoneControl.executeCommand(cmd)
                refreshTelemetry()
            }
        }
    }

    private suspend fun autoExtractMemoryIfPresent(input: String) {
        val lower = input.lowercase()
        when {
            lower.contains("mera naam ") -> {
                val name = input.substringAfter("mera naam ", "").substringBefore(" hai").trim()
                if (name.isNotBlank()) {
                    repository.insertMemory(
                        MemoryFactEntity(
                            category = "PERSONAL",
                            title = "User's Name",
                            detail = name
                        )
                    )
                }
            }
            lower.contains("mujhe ") && lower.contains("pasand hai") -> {
                repository.insertMemory(
                    MemoryFactEntity(
                        category = "PREFERENCE",
                        title = "Favourite / Liked Item",
                        detail = input
                    )
                )
            }
            lower.contains("birthday") || lower.contains("anniversary") -> {
                repository.insertMemory(
                    MemoryFactEntity(
                        category = "IMPORTANT_DATE",
                        title = "Special Date",
                        detail = input
                    )
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        speechManager.release()
    }
}
