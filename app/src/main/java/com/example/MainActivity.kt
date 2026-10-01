package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.NotificationOverlayBanner
import com.example.ui.components.RgbEdgeAndBackgroundContainer
import com.example.ui.screens.CodingStudioScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.JarvisScreen
import com.example.ui.viewmodel.JarvisViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                JarvisRootApp()
            }
        }
    }
}

@Composable
fun JarvisRootApp(
    viewModel: JarvisViewModel = viewModel()
) {
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val memories by viewModel.memories.collectAsStateWithLifecycle()
    val selectedMood by viewModel.selectedMood.collectAsStateWithLifecycle()
    val wakeState by viewModel.wakeState.collectAsStateWithLifecycle()
    val voiceSettings by viewModel.voiceSettings.collectAsStateWithLifecycle()
    val customApiKey by viewModel.customApiKey.collectAsStateWithLifecycle()
    val orbState by viewModel.orbVisualState.collectAsStateWithLifecycle()
    val showHologramAvatar by viewModel.showHologramAvatar.collectAsStateWithLifecycle()
    val pendingConfirmation by viewModel.pendingConfirmation.collectAsStateWithLifecycle()
    val screenMockState by viewModel.screenMockState.collectAsStateWithLifecycle()
    val capturedBitmap by viewModel.capturedScreenBitmap.collectAsStateWithLifecycle()
    val overlayNotification by viewModel.overlayNotification.collectAsStateWithLifecycle()
    val isServiceRunning by viewModel.isForegroundServiceRunning.collectAsStateWithLifecycle()
    val isScreenSharingLive by viewModel.screenShareManager.isScreenSharing.collectAsStateWithLifecycle()
    val isBackgroundRgbActive by viewModel.isBackgroundRgbActive.collectAsStateWithLifecycle()
    val codingMessages by viewModel.codingMessages.collectAsStateWithLifecycle()
    val isGeneratingCode by viewModel.isGeneratingCode.collectAsStateWithLifecycle()

    val isContinuousMicOn by viewModel.speechManager.isContinuousMicOn.collectAsStateWithLifecycle()
    val isListening by viewModel.speechManager.isListening.collectAsStateWithLifecycle()
    val isSpeaking by viewModel.speechManager.isSpeaking.collectAsStateWithLifecycle()
    val audioAmplitude by viewModel.speechManager.audioAmplitude.collectAsStateWithLifecycle()
    val liveTranscript by viewModel.speechManager.livePartialTranscript.collectAsStateWithLifecycle()

    RgbEdgeAndBackgroundContainer {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = {
                    (slideInHorizontally(animationSpec = tween(220)) { it / 6 } +
                        fadeIn(animationSpec = tween(220))) togetherWith
                        (slideOutHorizontally(animationSpec = tween(180)) { -it / 6 } +
                            fadeOut(animationSpec = tween(180)))
                },
                label = "jarvis_simple_transition"
            ) { screen ->
                when (screen) {
                    JarvisScreen.CODING_STUDIO -> {
                        CodingStudioScreen(
                            codingMessages = codingMessages,
                            isGeneratingCode = isGeneratingCode,
                            onSendCodingPrompt = { prompt -> viewModel.sendCodingPrompt(prompt) },
                            onClearCodingChat = { viewModel.clearCodingChat() },
                            onBack = { viewModel.navigateTo(JarvisScreen.HOME) }
                        )
                    }

                    JarvisScreen.SETTINGS -> {
                        SettingsScreen(
                            customApiKey = customApiKey,
                            onSaveCustomApiKey = { key -> viewModel.saveCustomApiKey(key) },
                            isBackgroundRgbActive = isBackgroundRgbActive,
                            onToggleBackgroundRgb = { viewModel.toggleBackgroundRgbLight() },
                            wakeState = wakeState,
                            isForegroundServiceRunning = isServiceRunning,
                            memories = memories,
                            phoneControl = viewModel.phoneControl,
                            onChangeWakeState = { state -> viewModel.updateWakeState(state) },
                            onToggleForegroundService = { viewModel.toggleForegroundService() },
                            onTestVoice = { viewModel.testVoiceSample() },
                            onBack = { viewModel.navigateTo(JarvisScreen.HOME) }
                        )
                    }

                    else -> {
                        HomeScreen(
                            selectedMood = selectedMood,
                            wakeState = wakeState,
                            orbState = orbState,
                            showHologramAvatar = showHologramAvatar,
                            isListening = isListening,
                            isContinuousMicOn = isContinuousMicOn,
                            isSpeaking = isSpeaking,
                            isScreenSharingLive = isScreenSharingLive,
                            isBackgroundRgbActive = isBackgroundRgbActive,
                            audioAmplitude = audioAmplitude,
                            liveTranscript = liveTranscript,
                            recentMessages = messages,
                            pendingConfirmation = pendingConfirmation,
                            screenState = screenMockState,
                            capturedBitmap = capturedBitmap,
                            onToggleAvatar = { viewModel.toggleHologramAvatar() },
                            onStartVoiceListen = { viewModel.speechManager.startListening() },
                            onStopVoiceListen = { viewModel.speechManager.stopListening() },
                            onStartLiveScreenShare = { resultCode, data ->
                                viewModel.startLiveScreenShare(resultCode, data)
                            },
                            onStopLiveScreenShare = { viewModel.stopLiveScreenShare() },
                            onToggleBackgroundRgb = { viewModel.toggleBackgroundRgbLight() },
                            onQuickPrompt = { prompt -> viewModel.handleUserMessage(prompt) },
                            onSpeakText = { cleanText ->
                                viewModel.speechManager.speak(
                                    cleanText = cleanText,
                                    voiceSettings = voiceSettings,
                                    mood = selectedMood,
                                    isChupMode = false
                                )
                            },
                            onClearChat = { viewModel.clearConversation() },
                            onConfirmSensitiveAction = { viewModel.confirmPendingAction() },
                            onCancelSensitiveAction = { viewModel.cancelPendingAction() },
                            onSetCapturedBitmap = { bmp -> viewModel.setCapturedScreenBitmap(bmp) },
                            onPerformScreenAction = { action, payload ->
                                viewModel.performScreenShareControl(action, payload)
                            },
                            onOpenCodingStudio = { viewModel.navigateTo(JarvisScreen.CODING_STUDIO) },
                            onOpenSettings = { viewModel.navigateTo(JarvisScreen.SETTINGS) }
                        )
                    }
                }
            }

            NotificationOverlayBanner(
                notification = overlayNotification,
                onDismiss = { viewModel.dismissOverlayNotification() },
                onActionClick = { viewModel.navigateTo(JarvisScreen.HOME) },
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}
