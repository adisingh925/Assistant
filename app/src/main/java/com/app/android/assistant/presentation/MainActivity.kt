package com.app.android.assistant.presentation

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Sync
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.times
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Text
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Task
import io.socket.client.IO
import io.socket.client.Socket
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.File
import java.io.IOException

class MainActivity : ComponentActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    companion object {
        const val LOCATION_PERMISSION_CODE = 201
    }

    private var shouldShowPermissionScreen =
        false // Track if we need to show manual permission screen

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        setContent {
            if (shouldShowPermissionScreen) {
                PermissionScreen(
                    requestPermissions = ::requestPermissionsManually
                )
            } else {
                requestPermissions() // Auto-request permissions on app start
            }
        }
    }

    // Automatically request both permissions on start
    private fun requestPermissions() {
        val permissionsNeeded = mutableListOf<String>()

        if (!checkLocationPermission()) {
            permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (permissionsNeeded.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this, permissionsNeeded.toTypedArray(), LOCATION_PERMISSION_CODE
            )
        } else {
            Log.d("Permissions", "Location permission already granted.")
            onPermissionsGranted()
        }
    }

    // Manually request permissions when the button is clicked
    private fun requestPermissionsManually() {
        requestPermissions()
    }

    // Handle the result of the permission request
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        // Check if all permissions are granted
        if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            onPermissionsGranted()
        } else {
            // If any permission is denied, show the manual permission screen
            shouldShowPermissionScreen = true
            setContent {
                PermissionScreen(
                    requestPermissions = ::requestPermissionsManually
                )
            }
            Log.d("Permissions", "One or more permissions were denied.")
        }
    }

    // Called when both permissions are granted
    private fun onPermissionsGranted() {
        getLastKnownLocation()

        setContent {
            WearApp(
                checkLocationPermission = ::checkLocationPermission,
                fetchUserLocation()
            )
        }
    }

    private fun getLastKnownLocation() {
        try {
            if (checkLocationPermission()) {
                val locationResult: Task<Location> = fusedLocationClient.lastLocation
                locationResult.addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        task.result?.let { location ->
                            Log.d(
                                "Location Info",
                                "Latitude: ${location.latitude}, Longitude: ${location.longitude}"
                            )
                            storeUserLocation(location.latitude, location.longitude)
                        }
                    } else {
                        Log.d("Location Info", "Location not found")
                    }
                }
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    private fun storeUserLocation(latitude: Double, longitude: Double) {
        val sharedPreferences = getSharedPreferences("UserLocation", MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putString("Latitude", latitude.toString())
        editor.putString("Longitude", longitude.toString())
        editor.apply()
    }

    private fun fetchUserLocation(): Pair<Double, Double>? {
        val sharedPreferences = getSharedPreferences("UserLocation", MODE_PRIVATE)

        val latitude = sharedPreferences.getString("Latitude", null)
        val longitude = sharedPreferences.getString("Longitude", null)

        return if (latitude != null && longitude != null) {
            Pair(latitude.toDouble(), longitude.toDouble())
        } else {
            null // Handle the case where location is not stored
        }
    }

    private fun checkLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
}

@Composable
fun PermissionScreen(
    requestPermissions: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = "Permissions required to proceed.")
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { requestPermissions() },
                modifier = Modifier.size(48.dp),
                shape = CircleShape
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Check, // Icon at the top
                        contentDescription = "Grant",
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Grant",
                        fontSize = 10.sp // Smaller font size
                    )
                }
            }
        }
    }
}

@Composable
fun WearApp(
    checkLocationPermission: () -> Boolean,
    location: Pair<Double, Double>? = null
) {
    Log.d("Location", "User location: $location")

    var buttonState by remember { mutableStateOf(ButtonState.Connecting) }
    var textForVoiceInput by remember { mutableStateOf("") }
    val receivedMessage: StringBuilder by remember { mutableStateOf(StringBuilder()) }

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

    val voiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { activityResult ->
        if (activityResult.resultCode == Activity.RESULT_CANCELED) {
            // Handle the case where the user canceled the activity
            Log.d("VoiceInput", "Voice input canceled.")
            buttonState = ButtonState.Connected
        } else if (activityResult.resultCode == Activity.RESULT_OK) {
            activityResult.data?.let { data ->
                val results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                textForVoiceInput = results?.get(0) ?: "None"
            }
        }
    }

    LaunchedEffect(Unit) {
        // Handle socket events
        socket?.on(Socket.EVENT_CONNECT) {
            Log.d("SocketIO", "Connected to the server")
            // Successfully connected
            voiceLauncher.launch(voiceIntent)
            buttonState = ButtonState.Recording
            socket.emit("register", "client")
        }?.on(Socket.EVENT_DISCONNECT) {
            // Handle disconnect
            buttonState = ButtonState.Connecting
        }?.on("chat_stream") {
            // Handle incoming messages
            val message = it[0] as String
            if (message.isNotEmpty()) {
                Log.d("SocketIO", "Received message: $message")
                if (message != "-SOM-" && message != "-EOM-") {
                    receivedMessage.append(message)
                } else if (message == "-EOM-") {
                    streamTextToSpeech(
                        "sk_62b236b81ca03a60812a8c1e92e6f28c985c7daa665fc10e",
                        receivedMessage.toString(), "9BWtsMINqrJLrRacOk9x"
                    ) { byteArray ->
                        playAudio(byteArray)
                    }
                    buttonState = ButtonState.Connected
                    receivedMessage.clear()
                }
            }
        }?.on("chat_image") {
            val message = it[0] as String
            if(message == "loading1.gif") {
                Log.d("SocketIO", "Received image: $message")
            }
        }

        // Connect to the socket server
        socket?.connect()
    }

    LaunchedEffect(textForVoiceInput) {
        Log.d("VoiceInput", "Received text: $textForVoiceInput")
        if (textForVoiceInput.isNotEmpty()) {
            socket?.emit(
                "chat_message",
                textForVoiceInput,
                null,
                2,
                110,
                null,
                "portrait",
                false,
                "USER",
                "CONVERSATIONID",
                "${location?.first ?: "Unknown"}, ${location?.second ?: "Unknown"}"
            )
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
                when (buttonState) {
                    ButtonState.Connecting ->  // Do nothing
                        Unit

                    ButtonState.Connected -> {
                        if (checkLocationPermission()) {
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
                    durationMillis = 3000 // Slower, for a "breathing" effect
                    defaultButtonSize at 0 using LinearOutSlowInEasing
                    defaultButtonSize * 1.2f at 1500 using LinearOutSlowInEasing // Expanding phase
                    defaultButtonSize at 3000 using LinearOutSlowInEasing // Contracting phase
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

fun streamTextToSpeech(
    apiKey: String,
    text: String,
    voiceId: String,
    onStreamReady: (byteArray: ByteArray?) -> Unit
) {
    val client = OkHttpClient()

    val jsonBody = JSONObject()
    jsonBody.put("text", text)

    val voiceSettings = JSONObject()
    voiceSettings.put("similarity_boost", 1)
    voiceSettings.put("stability", 1)

    jsonBody.put("voice_settings", voiceSettings)

    val request = Request.Builder()
        .url("https://api.elevenlabs.io/v1/text-to-speech/${voiceId}/stream")
        .addHeader("xi-api-key", apiKey)
        .addHeader("content-type", "application/json")
        .post(RequestBody.create("application/json".toMediaTypeOrNull(), jsonBody.toString()))
        .build()

    client.newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            Log.e("TTS-Error", "Request failed: ${e.message}")
            onStreamReady(null)
        }

        override fun onResponse(call: Call, response: Response) {
            if (response.isSuccessful) {
                response.body?.byteStream()?.use { inputStream ->
                    val byteArray =
                        inputStream.readBytes() // Read the input stream into a byte array
                    onStreamReady(byteArray)
                }
            } else {
                Log.e("TTS-Error", "Failed to retrieve TTS stream: ${response.message}")
                onStreamReady(null)
            }
        }
    })
}

// Function to play the audio from the received URL
fun playAudio(byteArray: ByteArray?) {
    byteArray?.let {
        try {
            val mediaPlayer = MediaPlayer()
            // Create a temporary file to hold the audio
            val tempFile = File.createTempFile("tts_audio", ".mp3").apply {
                deleteOnExit() // Ensure it's deleted when the app exits
            }

            tempFile.outputStream().use { outputStream ->
                outputStream.write(it) // Write the byte array to the file
            }

            mediaPlayer.setDataSource(tempFile.absolutePath)
            mediaPlayer.prepareAsync()
            mediaPlayer.setOnPreparedListener { player ->
                player.start()
            }
        } catch (e: Exception) {
            Log.e("MediaPlayerError", "Error playing audio: ${e.localizedMessage}")
        }
    } ?: run {
        Log.e("TTS", "Audio byte array is null, cannot play audio.")
    }
}
