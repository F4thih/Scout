package com.pocketcore.assistant.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketcore.assistant.core.CommandActionType
import com.pocketcore.assistant.core.CommandDispatcher
import com.pocketcore.assistant.core.CommandResult
import com.pocketcore.assistant.data.PocketCoreDatabase
import com.pocketcore.assistant.data.entity.CommandLogEntity
import com.pocketcore.assistant.ui.theme.PocketCoreTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*

/**
 * AssistantOverlayActivity:
 * Lightweight transparent bottom-sheet overlay triggered exclusively by
 * physical button shortcuts, corner swipe gestures, or the Quick Settings tile.
 * It immediately prompts for offline voice input (or quick typing), executes the command,
 * speaks/displays the response, and auto-dismisses itself.
 */
class AssistantOverlayActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private val dispatcher = CommandDispatcher()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize offline TTS engine
        try {
            tts = TextToSpeech(this, this)
        } catch (e: Exception) {
            // TTS unavailable, fallback to visual feedback
        }

        setContent {
            PocketCoreTheme {
                OverlayBottomSheet(
                    onExecuteCommand = { input, onResult ->
                        executeAndRespond(input, onResult)
                    },
                    onDismiss = { finish() }
                )
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.getDefault()
            isTtsReady = true
        }
    }

    private fun executeAndRespond(input: String, onResult: (CommandResult) -> Unit) {
        CoroutineScope(Dispatchers.Main).launch {
            val result = dispatcher.dispatch(this@AssistantOverlayActivity, input)
            onResult(result)

            // Log locally into SQLite Room
            CoroutineScope(Dispatchers.IO).launch {
                val db = PocketCoreDatabase.getInstance(this@AssistantOverlayActivity)
                db.commandLogDao().insertLog(
                    CommandLogEntity(
                        rawInput = input,
                        feedbackMessage = result.feedbackMessage,
                        success = result.success
                    )
                )
            }

            // Speak feedback message offline via TTS
            if (isTtsReady && result.feedbackMessage.isNotBlank()) {
                tts?.speak(result.feedbackMessage, TextToSpeech.QUEUE_FLUSH, null, "POCKETCORE_TTS")
            }

            // Auto-dismiss the overlay after short delay so the user reads/hears the result
            val dismissDelay = if (result.success) 2200L else 3200L
            Handler(Looper.getMainLooper()).postDelayed({
                if (!isFinishing && !isDestroyed) {
                    finish()
                }
            }, dismissDelay)
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}

@Composable
fun OverlayBottomSheet(
    onExecuteCommand: (String, (CommandResult) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var inputText by remember { mutableStateOf("") }
    var isExecuting by remember { mutableStateOf(false) }
    var resultState by remember { mutableStateOf<CommandResult?>(null) }

    // Direct offline speech recognizer launcher
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val spokenMatches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spokenText = spokenMatches?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                inputText = spokenText
                isExecuting = true
                onExecuteCommand(spokenText) { res ->
                    isExecuting = false
                    resultState = res
                }
            } else {
                onDismiss()
            }
        } else {
            // If user cancelled voice input without speaking, keep sheet open for text input
        }
    }

    val launchOfflineVoice: () -> Unit = {
        try {
            val speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Scout Listening...")
            }
            speechLauncher.launch(speechIntent)
        } catch (e: Exception) {
            Toast.makeText(context, "Voice input engine unavailable. Use text prompt.", Toast.LENGTH_SHORT).show()
        }
    }

    // Auto-launch voice prompt immediately when invoked by gesture/hardware key
    LaunchedEffect(Unit) {
        launchOfflineVoice()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = false) {}, // Prevent dismiss when tapping dialog itself
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Bolt,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isExecuting) "Processing..." else "Scout Assistant",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                    }
                }

                // If result available, show animated feedback message
                AnimatedVisibility(visible = resultState != null) {
                    resultState?.let { res ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (res.success) Color(0xFF1E3A2E) else Color(0xFF3E2325)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (res.success) Icons.Default.CheckCircle else Icons.Default.Error,
                                    contentDescription = null,
                                    tint = if (res.success) Color(0xFF4CAF50) else Color(0xFFE57373)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = res.feedbackMessage,
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }

                // Command text input + mic button
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            placeholder = { Text("Speak or type command...") },
                            modifier = Modifier.weight(1f),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            singleLine = true
                        )

                        IconButton(
                            onClick = launchOfflineVoice,
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                                .size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Speak",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        IconButton(
                            onClick = {
                                if (inputText.isNotBlank() && !isExecuting) {
                                    isExecuting = true
                                    onExecuteCommand(inputText) { res ->
                                        isExecuting = false
                                        resultState = res
                                    }
                                }
                            },
                            enabled = inputText.isNotBlank() && !isExecuting
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = if (inputText.isNotBlank()) MaterialTheme.colorScheme.primary else Color.Gray
                            )
                        }
                    }
                }


            }
        }
    }
}
