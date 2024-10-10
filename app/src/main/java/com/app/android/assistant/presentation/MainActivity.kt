package com.app.android.assistant.presentation

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothHeadset
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.Vibrator
import android.os.VibrationEffect
import android.speech.RecognizerIntent
import android.util.Base64
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.times
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.wear.ambient.AmbientLifecycleObserver
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.app.android.assistant.presentation.MainActivity.Companion.ELEVEN_LABS_API_KEY
import com.app.android.assistant.presentation.MainActivity.Companion.ELEVEN_LABS_BASE_URL
import com.app.android.assistant.presentation.MainActivity.Companion.ELEVEN_LABS_VOICE_ID
import com.app.android.assistant.presentation.MainActivity.Companion.SOCKET_SERVER_CONNECTION_TOKEN
import com.app.android.assistant.presentation.MainActivity.Companion.SOCKET_URL
import com.app.android.assistant.presentation.MainActivity.Companion.SETTING_TTS_AUTOPLAY
import com.app.android.assistant.presentation.MainActivity.Companion.SETTING_IMAGE_AUTOPLAY
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
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
import java.util.Locale



class ChargingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == Intent.ACTION_POWER_CONNECTED) {
            // Exit the app
            Log.d("Power", "Charger connected exiting")
            if (context is Activity) {
                context.finishAffinity() // Finish all activities and exit
            }
        }
    }
}

class MainActivity : ComponentActivity() {

    private var shouldShowPermissionScreen =
        false // Track if we need to show manual permission screen

    companion object {
        const val LOCATION_PERMISSION_CODE = 201
        const val SOCKET_URL = "wss://ws.dreadtools.com"
        const val SOCKET_SERVER_CONNECTION_TOKEN = "abc123"
        const val ELEVEN_LABS_API_KEY = "sk_62b236b81ca03a60812a8c1e92e6f28c985c7daa665fc10e"
        const val ELEVEN_LABS_VOICE_ID = "9BWtsMINqrJLrRacOk9x"
        const val ELEVEN_LABS_BASE_URL = "https://api.elevenlabs.io"
        const val SETTING_TTS_AUTOPLAY = true
        const val SETTING_IMAGE_AUTOPLAY = true
    }

    private val ambientCallback = object : AmbientLifecycleObserver.AmbientLifecycleCallback {
        override fun onEnterAmbient(ambientDetails: AmbientLifecycleObserver.AmbientDetails) {
            Log.d("Ambient", "Entering ambient mode")

        }

        override fun onExitAmbient() {
            Log.d("Ambient", "Exiting ambient mode")
        }

        override fun onUpdateAmbient() {
            Log.d("Ambient", "Updating ambient mode")
        }
    }

    private val ambientObserver = AmbientLifecycleObserver(this, ambientCallback)
    private lateinit var chargingReceiver: ChargingReceiver

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycle.addObserver(ambientObserver)
        chargingReceiver = ChargingReceiver()
        ScreenManager.setActivity(this)

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

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(Intent.ACTION_POWER_CONNECTED)
        registerReceiver(chargingReceiver, filter)
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(chargingReceiver)
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
        setContent {
            WearApp(
                checkLocationPermission = ::checkLocationPermission,
                this
            )
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

@SuppressWarnings("MissingPermission")
@Composable
fun WearApp(
    checkLocationPermission: () -> Boolean,
    context: Context
) {
    var currentScreen by remember { mutableStateOf("main") }


    when (currentScreen) {
        "main" -> WearAppWithSwipeNavigation(
            checkLocationPermission = checkLocationPermission,
            context = context,
            navigateToSettings = { currentScreen = "settings" }
        )

        "settings" -> SettingsScreen(
            context,
            navigateBack = { currentScreen = "main" }
        )
    }
}

@SuppressLint("MissingPermission")
@Composable
fun WearAppWithSwipeNavigation(
    checkLocationPermission: () -> Boolean,
    context: Context,
    navigateToSettings: () -> Unit
) {
    val swipeThreshold = 10f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onVerticalDrag = { change, velocity ->
                        Log.d("Swipe", "Velocity: $velocity change: $change")
                        if (velocity > swipeThreshold) {
                            Log.d("Swipe", "Swiped right")
                        } else if (velocity < -swipeThreshold) {
                            Log.d("Swipe", "Swiped up")
                            navigateToSettings()
                        }
                    }
                )
            }
    ) {
        val scope = rememberCoroutineScope()
        var buttonState by remember { mutableStateOf(ButtonState.Connecting) }
        var textForVoiceInput by remember { mutableStateOf("") }
        val receivedMessage: StringBuilder by remember { mutableStateOf(StringBuilder()) }
        var bitmapImage by remember { mutableStateOf<Bitmap?>(null) } // State to hold the bitmap image
        val locationClient = remember {
            LocationServices.getFusedLocationProviderClient(context)
        }
        var latitudes by remember { mutableStateOf("") }
        var longitudes by remember { mutableStateOf("") }
        var expectedDataType by remember { mutableStateOf("") }
        val vibrator = context.getSystemService(Vibrator::class.java)

        val socket = remember {
            try {
                val options = IO.Options().apply {
                    auth = mapOf("token" to getFromPreferences(context, "SOCKET_TOKEN", SOCKET_SERVER_CONNECTION_TOKEN))
                }
                Log.d("SocketIO", "Connecting to Socket Server...")
                IO.socket(SOCKET_URL, options)
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
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Please speak now")
        }

        val voiceLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { activityResult ->
            if (activityResult.resultCode == Activity.RESULT_CANCELED) {
                Log.d("VoiceInput", "Voice input canceled.")
                buttonState = ButtonState.Connected
                ScreenManager.clearScreenOn()
            } else if (activityResult.resultCode == Activity.RESULT_OK) {
                activityResult.data?.let { data ->
                    val results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                    textForVoiceInput = results?.get(0) ?: "None"
                }
            }
        }

        /**
         * Fetch the user's location in separate thread
         */
        LaunchedEffect(Unit) {
            scope.launch(Dispatchers.IO) {
                if (checkLocationPermission()) {
                    /** Fetch accurate location data */
                    val priority = Priority.PRIORITY_HIGH_ACCURACY
                    locationClient.getCurrentLocation(
                        priority,
                        CancellationTokenSource().token,
                    ).addOnCompleteListener {
                        if (it.isSuccessful) {
                            val fetchedLocation = it.result
                            latitudes = fetchedLocation.latitude.toString()
                            longitudes = fetchedLocation.longitude.toString()
                            Log.d(
                                "Location",
                                "Fetched location: ${fetchedLocation.latitude}, ${fetchedLocation.longitude}"
                            )
                        }
                    }
                }
            }
        }

        /**
         * Connect to the Socket server and handle events
         */
        LaunchedEffect(Unit) {
            if (socket != null) {
                if (!socket.connected()) {
                    socket.on(Socket.EVENT_CONNECT) {
                        Log.d("SocketIO", "Connected to the server")
                        //Commented out to fix network change STT trigger
                        //voiceLauncher.launch(voiceIntent)
                        buttonState = if ( receivedMessage.isNotEmpty() ) {
                            ButtonState.PendingMessage
                        } else {
                            ButtonState.Connected
                        }

                        /** Register Client */
                        socket.emit("register", "client")
                    }?.on(Socket.EVENT_DISCONNECT) {
                        buttonState = ButtonState.Connecting
                    }?.on("chat_stream") {
                        val message = it[0] as String
                        if (message.isNotEmpty()) {
                            // Log.d("SocketIO", "Received message : $message")
                            if (message != "-SOM-" && message != "-EOM-") {
                                receivedMessage.append(message)
                            } else if (message == "-EOM-") {

                                if ( getBoolFromPreferences(context, "SETTING_TTS_AUTOPLAY", SETTING_TTS_AUTOPLAY) || isBluetoothHeadsetConnected() ) {
                                    vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
                                    streamTextToSpeech(
                                        context,
                                        applyFilters(receivedMessage.toString())
                                    ) { byteArray ->
                                        playAudio(byteArray) {
                                            if (expectedDataType == "text") {
                                                if (checkLocationPermission()) {
                                                    // voiceLauncher.launch(voiceIntent)
                                                    buttonState = ButtonState.Connected
                                                }
                                            }
                                        }
                                    }

                                    receivedMessage.clear()
                                } else {
                                    buttonState = ButtonState.PendingMessage
                                    vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
                                    // receivedMessage.clear()
                                }
                            }
                        }
                    }?.on("chat_image") {
                        val message = it[0] as String
                        Log.d("SocketIO", "Received image")

                        if (isBase64Image(message)) {
                            Log.d("SocketIO", "Image is base64-encoded.")
                            if ( getBoolFromPreferences(context, "SETTING_IMAGE_AUTOPLAY", SETTING_IMAGE_AUTOPLAY) ) {
                                // Extract the base64 part
                                val base64Data = message.substringAfter(",")

                                // Decode the base64 data and update the bitmap state
                                bitmapImage = decodeBase64ToBitmap(base64Data)

                                // change the state to image
                                buttonState = ButtonState.Image
                            } else {
                                if (buttonState != ButtonState.Processing || buttonState != ButtonState.Processing2) {
                                    ScreenManager.clearScreenOn()
                                    buttonState = ButtonState.Connected
                                }
                            }
                        } else {
                            Log.d("SocketIO", "Image is not base64-encoded.")
                            if (message == "loading1.gif") {
                                buttonState = ButtonState.Processing
                                ScreenManager.clearScreenOn()
                                expectedDataType = "text"
                                Log.d("SocketIO", "Setting app state to Processing")
                            }
                            if (message == "loading2.gif") {
                                buttonState = ButtonState.Processing2
                                ScreenManager.clearScreenOn()
                                expectedDataType = "image"
                                Log.d("SocketIO", "Setting app state to Processing2")
                            }
                        }
                    }

                    // Connect to the socket server
                    socket.connect()
                }
            }
        }

        LaunchedEffect(textForVoiceInput) {
            Log.d("VoiceInput", "Received text from User : $textForVoiceInput")

//            expectedDataType = if (textForVoiceInput.contains("image", ignoreCase = true)) {
//                "image"
//            } else {
//                "text"
//            }

            if (textForVoiceInput.isNotEmpty()) {
                socket?.emit(
                    "chat_message",
                    textForVoiceInput.lowercase(Locale.ENGLISH),
                    null,
                    2,
                    -1,
                    null,
                    "square",
                    false,
                    "USER",
                    "CONVERSATIONID",
                    "${latitudes},${longitudes}"
                )

                // change the state to processing
                buttonState = ButtonState.Processing
                ScreenManager.clearScreenOn()
            }
        }

        LaunchedEffect(bitmapImage) {
            Log.d("ImageInput", "Received image from Server")


            if (bitmapImage != null) {
                delay(30000)
                bitmapImage = null // Clear the image
                // change the state to Connected
                buttonState = ButtonState.Connected
            }

        }


        DisposableEffect(Unit) {
            onDispose {
                Log.d("SocketIO", "Disconnecting from the server")
                socket?.disconnect()
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center // Center the content within the Box
        ) {
            CircularStateButton(
                currentState = buttonState,
                bitmapImage,
                onClick = {
                    bitmapImage = null // Clear the image
                    // Cycle through states on each click
                    when (buttonState) {
                        ButtonState.Connecting -> Unit // Do nothing
                        ButtonState.Connected -> {
                            if (checkLocationPermission()) {
                                ScreenManager.keepScreenOn()
                                voiceLauncher.launch(voiceIntent)
                                buttonState = ButtonState.Recording
                            }
                        }

                        ButtonState.Recording -> {
                            buttonState = ButtonState.Processing
                        }

                        ButtonState.Processing -> {
                            buttonState = ButtonState.Connected
                            ScreenManager.clearScreenOn()
                        }

                        ButtonState.Processing2 -> {
                            buttonState = ButtonState.Connected
                            ScreenManager.clearScreenOn()
                        }

                        ButtonState.Image -> {
                            if (checkLocationPermission()) {
                                ScreenManager.keepScreenOn()
                                voiceLauncher.launch(voiceIntent)
                                buttonState = ButtonState.Recording
                            }
                            bitmapImage = null // Clear the image
                        }
                        ButtonState.PendingMessage -> {
                            buttonState = ButtonState.Processing
                            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
                            streamTextToSpeech(
                                context,
                                applyFilters(receivedMessage.toString())
                            ) { byteArray ->
                                playAudio(byteArray) {

                                        if (checkLocationPermission()) {
                                            // voiceLauncher.launch(voiceIntent)
                                            buttonState = ButtonState.Connected
                                        }

                                }
                            }

                            receivedMessage.clear()
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun CircularStateButton(
    currentState: ButtonState,
    image: Bitmap? = null,
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
        targetValue = if (currentState == ButtonState.Connected || currentState == ButtonState.Image || currentState == ButtonState.PendingMessage ) defaultButtonSize * 1.2f else defaultButtonSize,
        animationSpec = if (currentState == ButtonState.Connected || currentState == ButtonState.PendingMessage) {
            infiniteRepeatable(
                animation = keyframes {
                    durationMillis =
                        5750 // Total duration for the entire pulse cycle (1.5s + 0.75s + 2.5s + 1s)

                    // Enlarge phase (1.5 seconds)
                    defaultButtonSize at 0 using LinearOutSlowInEasing
                    defaultButtonSize * 1.2f at 1500 using LinearOutSlowInEasing

                    // Pause after enlarging (0.75 seconds)
                    defaultButtonSize * 1.2f at 2250

                    // Decrease phase (2.5 seconds)
                    defaultButtonSize at 4750 using LinearOutSlowInEasing

                    // Pause after contracting (1 second)
                    defaultButtonSize at 5750
                },
                repeatMode = RepeatMode.Restart
            )
        } else {
            tween(durationMillis = 0) // No animation for other states
        }, label = ""
    )

    // Define rotation animation for the icon in Processing state
    val rotationAngle by animateFloatAsState(
        targetValue = if (currentState == ButtonState.Processing || currentState == ButtonState.Processing2) 360f else 0f,
        animationSpec = if (currentState == ButtonState.Processing || currentState == ButtonState.Processing2) {
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
        ButtonState.Processing2 -> Color(0xff990066)
        ButtonState.Image -> Color.Transparent
        ButtonState.PendingMessage -> Color(0xff990066)
    }

    val text = when (currentState) {
        ButtonState.Connecting -> "Connecting"
        ButtonState.Connected -> "Connected"
        ButtonState.Recording -> "Recording"
        ButtonState.Processing -> "Processing"
        ButtonState.Processing2 -> "Processing2"
        ButtonState.Image -> "Image"
        ButtonState.PendingMessage -> "PendingMessage"
    }

    val icon: ImageVector? = when (currentState) {
        ButtonState.Connecting -> Icons.Filled.Sensors
        ButtonState.Connected -> null
        ButtonState.Recording -> Icons.Filled.Mic
        ButtonState.Processing -> Icons.Filled.Sync
        ButtonState.Processing2 -> Icons.Filled.Sync
        ButtonState.Image -> null
        ButtonState.PendingMessage -> null
    }

    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(backgroundColor = backgroundColor),
        shape = CircleShape,
        modifier = Modifier.size(animatedButtonSize) // Use the animated size
    ) {
        if (icon != null && text != "Image") {
            Icon(
                imageVector = icon,
                contentDescription = text,
                modifier = Modifier
                    .size(animatedButtonSize * 0.3f) // Adjust icon size relative to button size
                    .graphicsLayer(rotationZ = rotationAngle) // Apply the rotation effect conditionally
            )
        } else {
            if (image != null) {
                Image(
                    bitmap = image.asImageBitmap(),
                    contentDescription = "Image",
                    modifier = Modifier.size(animatedButtonSize)
                )
            }
        }
    }
}

fun isBase64Image(data: String): Boolean {
    return data.startsWith("data:image")

}

enum class ButtonState {
    Connecting,
    Connected,
    Recording,
    Processing,
    Processing2,
    Image,
    PendingMessage
}

fun decodeBase64ToBitmap(base64Str: String): Bitmap? {
    val decodedBytes = Base64.decode(base64Str, Base64.DEFAULT)
    return BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
}

fun streamTextToSpeech(
    context: Context,
    text: String,
    onStreamReady: (byteArray: ByteArray?) -> Unit
) {
    val client = OkHttpClient()

    val jsonBody = JSONObject()
    jsonBody.put("text", text)

    val voiceSettings = JSONObject()
    voiceSettings.put("similarity_boost", 0.9)
    voiceSettings.put("stability", 0.3)

    jsonBody.put("voice_settings", voiceSettings)

    val request = Request.Builder()
        .url("${ELEVEN_LABS_BASE_URL}/v1/text-to-speech/${getFromPreferences(context, "VOICE_ID", ELEVEN_LABS_VOICE_ID)}/stream")
        .addHeader("xi-api-key", getFromPreferences(context, "ELEVEN_LABS_API_KEY", ELEVEN_LABS_API_KEY))
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
fun playAudio(byteArray: ByteArray?, onCompletion: () -> Unit) {
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
            mediaPlayer.setOnCompletionListener {
                mediaPlayer.release() // Release MediaPlayer resources
                onCompletion()
            }
        } catch (e: Exception) {
            Log.e("MediaPlayerError", "Error playing audio: ${e.localizedMessage}")
            onCompletion()
        }
    } ?: run {
        Log.e("TTS", "Audio byte array is null, cannot play audio.")
    }
}

private fun applyFilters(data: String): String {
    var filteredData = data

    // Remove text between asterisks
    filteredData = filteredData.replace(Regex("\\*[^*]*\\*"), "")

    // Remove HTML comments
    filteredData = filteredData.replace(Regex("<!--.*?-->"), "")

    // Replace tilde (~) with empty string
    filteredData = filteredData.replace("~", "")

    // Replace &nbsp; with a space
    filteredData = filteredData.replace("&nbsp;", " ")

    // Remove <br> tags
    filteredData = filteredData.replace("<br>", "")

    // Remove text after certain markers like "Picture:", "<html", "<|", and "</"
    filteredData = getTextBeforeMarker(filteredData, "\"Picture:")
    filteredData = getTextBeforeMarker(filteredData, "Picture:")
    filteredData = getTextBeforeMarker(filteredData, "<html")
    filteredData = getTextBeforeMarker(filteredData, "<|")
    filteredData = getTextBeforeMarker(filteredData, "</")

    return filteredData
}

private fun getTextBeforeMarker(data: String, marker: String): String {
    val index = data.indexOf(marker)
    return if (index != -1) {
        data.substring(0, index)
    } else {
        data
    }
}


fun isBluetoothHeadsetConnected(): Boolean {
    val mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
    Log.d("Bluetooth", "Headset is: ${mBluetoothAdapter.getProfileConnectionState(BluetoothHeadset.HEADSET)}")
    return (mBluetoothAdapter != null && mBluetoothAdapter.isEnabled
            && mBluetoothAdapter.getProfileConnectionState(BluetoothHeadset.HEADSET) == BluetoothHeadset.STATE_CONNECTED)
}


fun getFromPreferences(context: Context, key: String, defaultValue: String = ""): String {
    val sharedPreferences = context.getSharedPreferences("WearAppSettings", Context.MODE_PRIVATE)
    val value = sharedPreferences.getString(key, "")
    Log.d("Preferences", "Retrieved $key: $value")
    return if (value.isNullOrBlank()) defaultValue else value.toString()}

fun getBoolFromPreferences(context: Context, key: String, defaultValue: Boolean = true): Boolean {
    val sharedPreferences = context.getSharedPreferences("WearAppSettings", Context.MODE_PRIVATE)
    val value = sharedPreferences.getBoolean(key, true)
    Log.d("Preferences", "Retrieved $key: $value")
    return if (value) defaultValue else value}

@Composable
fun SettingsScreen(context: Context,navigateBack: () -> Unit) {
    var socketToken by remember { mutableStateOf(getFromPreferences(context, "SOCKET_TOKEN", SOCKET_SERVER_CONNECTION_TOKEN)) }
    var elevenLabsApiKey by remember { mutableStateOf(getFromPreferences(context, "ELEVEN_LABS_API_KEY", ELEVEN_LABS_API_KEY)) }
    var voiceId by remember { mutableStateOf(getFromPreferences(context, "VOICE_ID", ELEVEN_LABS_VOICE_ID)) }
    var settingttsautoplay by remember { mutableStateOf(getBoolFromPreferences(context, "SETTING_TTS_AUTOPLAY", SETTING_TTS_AUTOPLAY)) }
    var settingimageautoplay by remember { mutableStateOf(getBoolFromPreferences(context, "SETTING_IMAGE_AUTOPLAY", SETTING_IMAGE_AUTOPLAY)) }

    BackHandler {
        navigateBack() // Call the navigate back function when the back button is pressed
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Enable vertical scrolling with padding
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()) // Add scrollable modifier
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top // Top arrangement for scrollability
        ) {
            Text(text = "Settings", color = Color.White, style = MaterialTheme.typography.title1)

            Spacer(modifier = Modifier.height(16.dp))

            Text("Autoplay TTS")
            Checkbox(
                checked = settingttsautoplay,
                onCheckedChange = { settingttsautoplay = it }
                    )

            Spacer(modifier = Modifier.height(16.dp))

            Text("Display Images")
            Checkbox(
                checked = settingimageautoplay,
                onCheckedChange = { settingimageautoplay = it }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Socket Token Input Field
            OutlinedTextField(
                value = socketToken,
                onValueChange = { socketToken = it },
                label = { Text("Socket Token") },
                textStyle = TextStyle(color = Color.White, fontWeight = FontWeight.Bold),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Eleven Labs API Key Input Field
            OutlinedTextField(
                value = elevenLabsApiKey,
                onValueChange = { elevenLabsApiKey = it },
                label = { Text("Eleven Labs API Key") },
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontWeight = FontWeight.Bold),
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Voice ID Input Field
            OutlinedTextField(
                value = voiceId,
                onValueChange = { voiceId = it },
                label = { Text("Voice ID") },
                textStyle = TextStyle(color = Color.White, fontWeight = FontWeight.Bold),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Save Button
            Button(onClick = {
                saveApiKeys(context, socketToken, elevenLabsApiKey, voiceId, settingttsautoplay, settingimageautoplay)
                // Save the API keys logic here
                // You can pass these values back to the main app or save them in persistent storage (e.g., SharedPreferences or DataStore)
                navigateBack()
            }) {
                Text(text = "Save")
            }
        }
    }
}

fun saveApiKeys(context: Context, socketToken: String, elevenLabsApiKey: String, voiceId: String, settingttsautoplay: Boolean, settingimageautoplay: Boolean) {
    val sharedPreferences = context.getSharedPreferences("WearAppSettings", Context.MODE_PRIVATE)
    with(sharedPreferences.edit()) {
        putString("SOCKET_TOKEN", socketToken)
        putString("ELEVEN_LABS_API_KEY", elevenLabsApiKey)
        putString("VOICE_ID", voiceId)
        putBoolean("SETTING_TTS_AUTOPLAY", settingttsautoplay)
        putBoolean("SETTING_IMAGE_AUTOPLAY", settingimageautoplay)
        apply()
    }
}

object ScreenManager {
    private var activity: Activity? = null

    fun setActivity(activity: Activity) {
        this.activity = activity
    }

    fun keepScreenOn() {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        Log.d("Screen", "Setting Screen WAKE")
    }

    fun clearScreenOn() {
        activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        Log.d("Screen", "Clearing Screen WAKE")
    }
}