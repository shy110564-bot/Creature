package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.components.GlassCard
import com.example.ui.components.NeonIconButton
import com.example.ui.components.RgbNeonDivider
import com.example.ui.theme.HotPink
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.SurfaceAlt
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

data class CodingChatMessage(
    val id: Long = System.currentTimeMillis(),
    val isUser: Boolean,
    val promptOrTitle: String,
    val language: String = "html",
    val codeContent: String = "",
    val explanation: String = ""
)

@Composable
fun CodingStudioScreen(
    codingMessages: List<CodingChatMessage>,
    isGeneratingCode: Boolean,
    onSendCodingPrompt: (String) -> Unit,
    onClearCodingChat: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    var promptInput by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(codingMessages.size, isGeneratingCode) {
        if (codingMessages.isNotEmpty()) {
            listState.animateScrollToItem(codingMessages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // 1. TOP BAR
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NeonIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to Chat",
                    onClick = onBack,
                    tint = NeonCyan,
                    testTag = "coding_back_btn"
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "💻 JARVIS Coding Studio",
                        style = MaterialTheme.typography.titleMedium,
                        color = NeonCyan,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "Website HTML • CSS • JS • Python • Kotlin • Full Code",
                        style = MaterialTheme.typography.labelSmall,
                        color = NeonGreen
                    )
                }
            }

            NeonIconButton(
                icon = Icons.Default.DeleteSweep,
                contentDescription = "Clear Coding History",
                onClick = onClearCodingChat,
                tint = HotPink,
                testTag = "coding_clear_btn"
            )
        }

        Spacer(modifier = Modifier.height(6.dp))
        RgbNeonDivider()
        Spacer(modifier = Modifier.height(6.dp))

        // 2. CODING MESSAGES LIST
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 6.dp)
        ) {
            items(codingMessages, key = { it.id }) { msg ->
                if (msg.isUser) {
                    // User Prompt Bubble
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        GlassCard(
                            modifier = Modifier.fillMaxWidth(0.86f),
                            borderColor = NeonPurple,
                            backgroundColor = SurfaceAlt,
                            contentPadding = PaddingValues(12.dp)
                        ) {
                            Text(
                                text = "🧑‍💻 Prompt: ${msg.promptOrTitle}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                } else {
                    // AI Generated Code Card
                    CodingResultCard(
                        message = msg,
                        onCopyCode = { code ->
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            clipboard?.setPrimaryClip(ClipData.newPlainText("JARVIS Code", code))
                            Toast.makeText(context, "Code Copied! ✅", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }

            if (isGeneratingCode) {
                item {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        borderColor = NeonCyan,
                        contentPadding = PaddingValues(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                color = NeonCyan,
                                strokeWidth = 2.5.dp,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "⚡ JARVIS Ultra Coding Mind poora code likh rahi hai…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = NeonGreen,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 3. SIMPLE MESSAGE / PROMPT BAR AT BOTTOM
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            borderColor = NeonCyan,
            contentPadding = PaddingValues(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = promptInput,
                    onValueChange = { promptInput = it },
                    placeholder = {
                        Text(
                            text = "Coding prompt likhein (e.g. Portfolio Website HTML, Calculator, Python)…",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("coding_prompt_input"),
                    maxLines = 4,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = NeonPurple.copy(alpha = 0.6f),
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedContainerColor = SurfaceAlt.copy(alpha = 0.85f),
                        unfocusedContainerColor = SurfaceAlt.copy(alpha = 0.65f)
                    )
                )

                IconButton(
                    onClick = {
                        if (promptInput.isNotBlank() && !isGeneratingCode) {
                            onSendCodingPrompt(promptInput.trim())
                            promptInput = ""
                        }
                    },
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(listOf(NeonCyan, NeonPurple))
                        )
                        .testTag("coding_send_btn")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Generate Code",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun CodingResultCard(
    message: CodingChatMessage,
    onCopyCode: (String) -> Unit
) {
    val isHtmlPreviewable = message.language.equals("html", ignoreCase = true) ||
        message.codeContent.contains("<html", ignoreCase = true) ||
        message.codeContent.contains("<!DOCTYPE html", ignoreCase = true)

    var showLivePreview by remember { mutableStateOf(false) }

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        borderColor = NeonGreen,
        contentPadding = PaddingValues(12.dp)
    ) {
        // Header Row: Title + Copy Button + Live HTML Preview Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Code,
                    contentDescription = "Code",
                    tint = NeonGreen,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = message.promptOrTitle,
                    style = MaterialTheme.typography.labelLarge,
                    color = NeonGreen,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (isHtmlPreviewable && message.codeContent.isNotBlank()) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (showLivePreview) HotPink.copy(alpha = 0.25f) else SurfaceAlt)
                            .border(1.dp, if (showLivePreview) HotPink else NeonCyan, RoundedCornerShape(999.dp))
                            .clickable { showLivePreview = !showLivePreview }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("coding_preview_toggle_${message.id}"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (showLivePreview) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Preview Website",
                            tint = if (showLivePreview) HotPink else NeonCyan,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (showLivePreview) "Code" else "🌐 Preview",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (showLivePreview) HotPink else NeonCyan,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (message.codeContent.isNotBlank()) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(SurfaceAlt)
                            .border(1.dp, NeonGreen, RoundedCornerShape(999.dp))
                            .clickable { onCopyCode(message.codeContent) }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("coding_copy_btn_${message.id}"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy Code",
                            tint = NeonGreen,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Copy",
                            style = MaterialTheme.typography.labelSmall,
                            color = NeonGreen,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        if (message.explanation.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = message.explanation,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }

        if (message.codeContent.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))

            AnimatedVisibility(visible = showLivePreview && isHtmlPreviewable) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.5.dp, NeonCyan, RoundedCornerShape(12.dp))
                ) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                webViewClient = WebViewClient()
                            }
                        },
                        update = { webView ->
                            webView.loadDataWithBaseURL(
                                null,
                                message.codeContent,
                                "text/html",
                                "UTF-8",
                                null
                            )
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            AnimatedVisibility(visible = !showLivePreview) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background( Color(0xFF060913))
                        .border(1.dp, NeonCyan.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                        .padding(10.dp)
                        .horizontalScroll(rememberScrollState())
                ) {
                    SelectionContainer {
                        Text(
                            text = message.codeContent,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = Color(0xFFE2F1FF)
                        )
                    }
                }
            }
        }
    }
}
