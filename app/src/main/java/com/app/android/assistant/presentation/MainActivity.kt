package com.app.android.assistant.presentation

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Sync
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Icon
import io.socket.client.IO
import io.socket.client.Socket
import java.util.Locale

class MainActivity : ComponentActivity() {

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WearApp(
                checkPermissions = ::checkMicrophonePermissions,
                this
            )
        }
    }

    private fun checkMicrophonePermissions(): Boolean {
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO), 200
            )
            return false
        }
        return true
    }
}

@Composable
fun WearApp(
    checkPermissions: () -> Boolean,
    context: Context,
) {
    var buttonState by remember { mutableStateOf(ButtonState.Connecting) }
    var textForVoiceInput by remember { mutableStateOf("") }
    val socket = remember {
        try {
            val options = IO.Options().apply {
                auth = mapOf("token" to "abc123")
            }
            Log.d("SocketIO", "Creating the Socket instance")
            IO.socket("wss://ws.dreadtools.com", options)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    var textToSpeech: TextToSpeech? by remember { mutableStateOf(null) }

    LaunchedEffect(Unit) {
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                Log.d("TTS", "TextToSpeech initialized successfully")
                textToSpeech?.language = Locale.US // Set language to US English
            } else {
                Log.d("TTS", "Failed to initialize TextToSpeech")
            }
        }
    }

    val receivedMessage: StringBuilder by remember { mutableStateOf(StringBuilder()) }

    DisposableEffect(Unit) {
        onDispose {
            textToSpeech?.shutdown()
        }
    }

    val voiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { activityResult ->
        // This is where you process the intent and extract the speech text from the intent.
        activityResult.data?.let { data ->
            val results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            textForVoiceInput = results?.get(0) ?: "None"
        }
    }

    LaunchedEffect(Unit) {
        // Handle socket events
        socket?.on(Socket.EVENT_CONNECT) {
            Log.d("SocketIO", "Connected to the server")
            // Successfully connected
            buttonState = ButtonState.Connected
            socket.emit("register", "client")
        }?.on(Socket.EVENT_DISCONNECT) {
            // Handle disconnect
            buttonState = ButtonState.Connecting // or another state based on your logic
        }?.on("chat_stream") {
            // Handle incoming messages
            val message = it[0] as String
            if (message.isNotEmpty()) {
                Log.d("SocketIO", "Received message: $message")
                if (message != "-SOM-" && message != "-EOM-") {
                    receivedMessage.append(message)
                } else if (message == "-EOM-") {
                    buttonState = ButtonState.Connected
                    textToSpeech?.speak(receivedMessage, TextToSpeech.QUEUE_FLUSH, null, null)
                    receivedMessage.clear()
                }
            }
        }

        // Connect to the socket server
        socket?.connect()
    }

    LaunchedEffect(textForVoiceInput) {
        Log.d("VoiceInput", "Received text: $textForVoiceInput")
        if (textForVoiceInput.isNotEmpty()) {
            socket?.emit("chat_message", textForVoiceInput)
            buttonState = ButtonState.Processing
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center, // Center the content within the Box
    ) {
        CircularStateButton(
            currentState = buttonState,
            onClick = {
                // Cycle through states on each click
                val voiceIntent: Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                    )

                    putExtra(
                        RecognizerIntent.EXTRA_PROMPT,
                        "test"
                    )
                }

                when (buttonState) {
                    ButtonState.Connecting ->  // Do nothing
                        Unit

                    ButtonState.Connected -> {
                        if (checkPermissions()) {
                            voiceLauncher.launch(voiceIntent)
                            buttonState = ButtonState.Recording
                        }
                    }

                    ButtonState.Recording -> {
                        buttonState = ButtonState.Processing
                    }

                    ButtonState.Processing -> Unit
                }
            }
        )
    }
}

@Composable
fun CircularStateButton(
    currentState: ButtonState,
    onClick: () -> Unit
) {
    // Get screen width and height
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp

    // Calculate default button size
    val defaultButtonSize = 0.8f * minOf(screenWidth, screenHeight)

    // Define the size animation
    val animatedButtonSize by animateDpAsState(
        targetValue = if (currentState == ButtonState.Connected) defaultButtonSize * 1.2f else defaultButtonSize,
        animationSpec = if (currentState == ButtonState.Connected) {
            infiniteRepeatable(
                animation = keyframes {
                    durationMillis = 2000 // Animation duration
                    defaultButtonSize at 0 using FastOutSlowInEasing
                    defaultButtonSize * 1.2f at 1000 using FastOutSlowInEasing
                    defaultButtonSize at 2000 using FastOutSlowInEasing
                },
                repeatMode = RepeatMode.Reverse
            )
        } else {
            tween(
                durationMillis = 300, // Short animation for other states
                easing = FastOutSlowInEasing // Apply easing
            )
        },
        label = ""
    )

    // Define rotation animation for the icon in Processing state
    val rotationAngle by animateFloatAsState(
        targetValue = if (currentState == ButtonState.Processing) 360f else 0f,
        animationSpec = if (currentState == ButtonState.Processing) {
            infiniteRepeatable(
                animation = keyframes {
                    durationMillis = 2000 // Slow rotation duration
                    360f at 0
                    0f at 2000
                },
                repeatMode = RepeatMode.Restart
            )
        } else {
            tween(durationMillis = 0) // No rotation for other states
        }, label = ""
    )

    val backgroundColor = when (currentState) {
        ButtonState.Connecting -> Color.Gray
        ButtonState.Connected -> Color(0xFF0d4b48) // Dark green
        ButtonState.Recording -> Color(0xFFB22222)
        ButtonState.Processing -> Color.Blue
    }

    val text = when (currentState) {
        ButtonState.Connecting -> "Connecting"
        ButtonState.Connected -> "Connected"
        ButtonState.Recording -> "Recording"
        ButtonState.Processing -> "Processing"
    }

    val icon: ImageVector? = when (currentState) {
        ButtonState.Connecting -> Icons.Filled.Sensors
        ButtonState.Connected -> null
        ButtonState.Recording -> Icons.Filled.Mic
        ButtonState.Processing -> Icons.Filled.Sync
    }

    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(backgroundColor = backgroundColor),
        shape = CircleShape,
        modifier = Modifier.size(animatedButtonSize) // Use the animated size
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                modifier = Modifier
                    .size(animatedButtonSize * 0.3f) // Adjust icon size relative to button size
                    .graphicsLayer(rotationZ = rotationAngle) // Apply the rotation effect conditionally
            )
        }
    }
}

enum class ButtonState {
    Connecting,
    Connected,
    Recording,
    Processing
}
