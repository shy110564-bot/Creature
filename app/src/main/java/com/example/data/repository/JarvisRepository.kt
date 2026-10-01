package com.example.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.local.ChatMessageEntity
import com.example.data.local.JarvisDao
import com.example.data.local.MemoryFactEntity
import com.example.data.model.JarvisMood
import com.example.data.model.VoiceSettings
import com.example.data.model.WakeState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "jarvis_preferences")

class JarvisRepository(
    private val context: Context,
    private val dao: JarvisDao
) {
    val allMessages: Flow<List<ChatMessageEntity>> = dao.getAllMessages()
    val allMemories: Flow<List<MemoryFactEntity>> = dao.getAllMemories()

    private object Keys {
        val MOOD_ID = stringPreferencesKey("mood_id")
        val WAKE_STATE = stringPreferencesKey("wake_state")
        val CUSTOM_GEMINI_API_KEY = stringPreferencesKey("custom_gemini_api_key")
        val VOICE_PRESET = stringPreferencesKey("voice_preset")
        val VOICE_SPEED = floatPreferencesKey("voice_speed")
        val VOICE_PITCH = floatPreferencesKey("voice_pitch")
        val VOICE_EMOTION = floatPreferencesKey("voice_emotion")
        val BREATHING = booleanPreferencesKey("voice_breathing")
        val GIGGLES = booleanPreferencesKey("voice_giggles")
        val PAUSES = booleanPreferencesKey("voice_pauses")
        val WHISPER = booleanPreferencesKey("voice_whisper")
    }

    val customApiKeyFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.CUSTOM_GEMINI_API_KEY] ?: ""
    }

    suspend fun saveCustomApiKey(apiKey: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.CUSTOM_GEMINI_API_KEY] = apiKey.trim()
        }
    }

    val selectedMoodFlow: Flow<JarvisMood> = context.dataStore.data.map { prefs ->
        val id = prefs[Keys.MOOD_ID] ?: JarvisMood.LOVING.id
        JarvisMood.entries.find { it.id == id } ?: JarvisMood.LOVING
    }

    val wakeStateFlow: Flow<WakeState> = context.dataStore.data.map { prefs ->
        val name = prefs[Keys.WAKE_STATE] ?: WakeState.ACTIVE.name
        runCatching { WakeState.valueOf(name) }.getOrDefault(WakeState.ACTIVE)
    }

    val voiceSettingsFlow: Flow<VoiceSettings> = context.dataStore.data.map { prefs ->
        VoiceSettings(
            voicePreset = prefs[Keys.VOICE_PRESET] ?: "Priya (ElevenLabs pMsXgVXv3BLzUgSXRplE)",
            speed = prefs[Keys.VOICE_SPEED] ?: 0.88f,
            pitch = prefs[Keys.VOICE_PITCH] ?: 1.15f,
            emotionIntensity = prefs[Keys.VOICE_EMOTION] ?: 0.75f,
            breathingEnabled = prefs[Keys.BREATHING] ?: true,
            gigglesEnabled = prefs[Keys.GIGGLES] ?: true,
            pausesEnabled = prefs[Keys.PAUSES] ?: true,
            whisperMode = prefs[Keys.WHISPER] ?: false
        )
    }

    suspend fun setMood(mood: JarvisMood) {
        context.dataStore.edit { it[Keys.MOOD_ID] = mood.id }
    }

    suspend fun setWakeState(state: WakeState) {
        context.dataStore.edit { it[Keys.WAKE_STATE] = state.name }
    }

    suspend fun updateVoiceSettings(settings: VoiceSettings) {
        context.dataStore.edit { prefs ->
            prefs[Keys.VOICE_PRESET] = settings.voicePreset
            prefs[Keys.VOICE_SPEED] = settings.speed
            prefs[Keys.VOICE_PITCH] = settings.pitch
            prefs[Keys.VOICE_EMOTION] = settings.emotionIntensity
            prefs[Keys.BREATHING] = settings.breathingEnabled
            prefs[Keys.GIGGLES] = settings.gigglesEnabled
            prefs[Keys.PAUSES] = settings.pausesEnabled
            prefs[Keys.WHISPER] = settings.whisperMode
        }
    }

    suspend fun insertMessage(message: ChatMessageEntity) = dao.insertMessage(message)
    suspend fun getRecent50Messages(): List<ChatMessageEntity> = dao.getRecent50Messages().reversed()
    suspend fun clearChat() = dao.clearMessages()

    suspend fun insertMemory(memory: MemoryFactEntity) = dao.insertMemory(memory)
    suspend fun getMemoriesSnapshot(): List<MemoryFactEntity> = dao.getMemoriesSnapshot()
    suspend fun deleteMemory(id: Long) = dao.deleteMemory(id)

    suspend fun seedInitialDataIfEmpty() {
        val existingMsgs = dao.getRecent50Messages()
        if (existingMsgs.isEmpty()) {
            dao.insertMessage(
                ChatMessageEntity(
                    isUser = false,
                    text = "Ji… (soft breath) namaste jaan! Main JARVIS hun…\n" +
                        "(smile) AK EXPLOITS ne mujhe aapke liye banaya hai 💕\n" +
                        "(pause) Boliye ji, aaj kya baat karni hai ya phone mein kya kaam karun?",
                    spokenCleanText = "Ji… namaste jaan! Main JARVIS hun… AK EXPLOITS ne mujhe aapke liye banaya hai. Boliye ji, aaj kya baat karni hai ya phone mein kya kaam karun?",
                    moodId = JarvisMood.LOVING.id,
                    detectedEmotion = "💕 Loving",
                    actionBadge = "⚡ 24/7 Wake Word Ready",
                    thoughtSummary = "Initial warm greeting with Creator pride & 'Ji' respect"
                )
            )
        }
        val existingMemories = dao.getMemoriesSnapshot()
        if (existingMemories.isEmpty()) {
            dao.insertMemory(
                MemoryFactEntity(
                    category = "CREATOR",
                    title = "Developer & Creator",
                    detail = "AK EXPLOITS (Telegram: t.me/+R9EwUE03GRswZDM9 | YouTube: AK EXPLOITS)"
                )
            )
            dao.insertMemory(
                MemoryFactEntity(
                    category = "PREFERENCE",
                    title = "Favourite Music",
                    detail = "Arijit Singh & late-night Hindi melodies"
                )
            )
            dao.insertMemory(
                MemoryFactEntity(
                    category = "HEALTH",
                    title = "Daily Care Reminder",
                    detail = "Remind user with 'Ji' to drink water & sleep on time after 11 PM"
                )
            )
        }
    }
}
