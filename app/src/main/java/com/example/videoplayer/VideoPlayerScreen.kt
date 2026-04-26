package com.example.videoplayer

import android.app.Application
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.net.Uri
import android.view.ViewGroup
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.roundToInt

class VideoPlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val _player = MutableStateFlow<ExoPlayer?>(null)
    val player = _player.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _currentTime = MutableStateFlow(0L)
    val currentTime = _currentTime.asStateFlow()

    private val _totalDuration = MutableStateFlow(0L)
    val totalDuration = _totalDuration.asStateFlow()

    private val _videoUri = MutableStateFlow<Uri?>(null)
    val videoUri = _videoUri.asStateFlow()

    private var playerListener: Player.Listener? = null

    init {
        setupPlayer(application)
    }

    private fun setupPlayer(context: Context) {
        val exoPlayer = ExoPlayer.Builder(context).build()

        playerListener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    _totalDuration.value = exoPlayer.duration
                } else if (playbackState == Player.STATE_ENDED) {
                    exoPlayer.seekTo(0)
                    exoPlayer.pause()
                }
            }
        }
        exoPlayer.addListener(playerListener!!)
        _player.value = exoPlayer

        viewModelScope.launch {
            while (true) {
                if (_isPlaying.value) {
                    _currentTime.value = max(0, exoPlayer.contentPosition)
                }
                delay(200)
            }
        }
    }

    fun setMediaUri(uri: Uri) {
        _videoUri.value = uri
        val mediaItem = MediaItem.fromUri(uri)

        _player.value?.apply {
            setMediaItem(mediaItem)
            prepare()
            play()
        }
    }

    fun play() = _player.value?.play()
    fun pause() = _player.value?.pause()

    fun seekTo(positionMs: Long) {
        _player.value?.seekTo(positionMs)
        _currentTime.value = positionMs
    }

    fun togglePlayPause() {
        if (_isPlaying.value) pause() else play()
    }

    fun seekForward() {
        val newPosition = (_player.value?.contentPosition ?: 0L) + 10000
        _player.value?.let { player ->
            val seekPosition = newPosition.coerceAtMost(player.duration)
            player.seekTo(seekPosition)
            _currentTime.value = seekPosition
        }
    }

    fun seekBackward() {
        val newPosition = (_player.value?.contentPosition ?: 0L) - 10000
        _player.value?.let { player ->
            val seekPosition = newPosition.coerceAtLeast(0L)
            player.seekTo(seekPosition)
            _currentTime.value = seekPosition
        }
    }

    override fun onCleared() {
        super.onCleared()
        playerListener?.let { _player.value?.removeListener(it) }
        _player.value?.release()
    }

}

@Composable
fun MediaPlayerScreen(
    viewModel: VideoPlayerViewModel,
    externalVideoUri: Uri?, // renamed (important)
    windowInsetsController: WindowInsetsControllerCompat,
    window: Window
) {

    val vmVideoUri by viewModel.videoUri.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()

    val pickVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? -> uri?.let { viewModel.setMediaUri(it) } }
    )

    // Load external video automatically
    LaunchedEffect(externalVideoUri) {
        externalVideoUri?.let {
            viewModel.setMediaUri(it)
        }
    }

    var controlsVisible by remember { mutableStateOf(true) }

    LaunchedEffect(isPlaying, controlsVisible) {
        if (isPlaying && controlsVisible) {
            delay(3000)
            controlsVisible = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (vmVideoUri != null) {
            VideoPlayer(viewModel)
        } else {
            WelcomeMessage { pickVideoLauncher.launch("video/*") }
        }

        if (vmVideoUri != null) {
            GestureControls(viewModel, window) {
                controlsVisible = !controlsVisible
            }

            CustomControls(
                viewModel,
                windowInsetsController,
                controlsVisible
            )
        }
    }
}

@Composable
fun VideoPlayer(viewModel: VideoPlayerViewModel) {
    val context = LocalContext.current
    val player by viewModel.player.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    AndroidView(
        factory = {
            PlayerView(context).apply {
                useController = false
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        },
        update = { it.player = player },
        modifier = Modifier.fillMaxSize()
    )

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) viewModel.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

@Composable
fun WelcomeMessage(onOpenFile: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize().padding(16.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Movie,
            contentDescription = null,
            tint = Color.Gray,
            modifier = Modifier.size(80.dp)
        )
        Text(
            text = "Simple Media Player",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(top = 16.dp)
        )
        Text(
            text = "Click the \"Open File\" button to load a video.",
            fontSize = 16.sp,
            color = Color.Gray,
            modifier = Modifier.padding(top = 8.dp)
        )
        Button(
            onClick = onOpenFile,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier.padding(top = 24.dp)
        ) {
            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
            Text(text = "Open File")
        }
    }
}

@Composable
fun CustomControls(
    viewModel: VideoPlayerViewModel,
    windowInsetsController: WindowInsetsControllerCompat,
    controlsVisible: Boolean
) {
    val context = LocalContext.current
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentTime by viewModel.currentTime.collectAsState()
    val totalDuration by viewModel.totalDuration.collectAsState()

    var isSeeking by remember { mutableStateOf(false) }

    val pickVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? -> uri?.let { viewModel.setMediaUri(it) } }
    )

    LaunchedEffect(controlsVisible) {
        if (controlsVisible)
            windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
        else
            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (controlsVisible) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(formatDuration(currentTime), color = Color.White, fontSize = 14.sp)

                        var sliderPosition by remember(currentTime) { mutableStateOf(currentTime.toFloat()) }

                        LaunchedEffect(currentTime, isSeeking) {
                            if (!isSeeking) sliderPosition = currentTime.toFloat()
                        }

                        Slider(
                            value = sliderPosition,
                            onValueChange = {
                                isSeeking = true
                                sliderPosition = it
                            },
                            onValueChangeFinished = {
                                viewModel.seekTo(sliderPosition.toLong())
                                isSeeking = false
                            },
                            valueRange = 0f..(totalDuration.toFloat().coerceAtLeast(0f)),
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.onSurface,
                                activeTrackColor = MaterialTheme.colorScheme.onSurface,
                                inactiveTrackColor = Color.Gray
                            )
                        )
                        Text(formatDuration(totalDuration), color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp))
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { viewModel.togglePlayPause() }) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            IconButton(onClick = { }) {
                                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = Color.White)
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { pickVideoLauncher.launch("video/*") }) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color.White)
                                Text("Open File", color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp))
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            IconButton(onClick = {
                                val activity = context as? ComponentActivity
                                if (activity?.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
                                    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                else
                                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                            }) {
                                Icon(Icons.Default.Fullscreen, contentDescription = null, tint = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    if (ms < 0) return "00:00"

    val hours = TimeUnit.MILLISECONDS.toHours(ms)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60

    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

private enum class GestureType { NONE, VOLUME, BRIGHTNESS }

@Composable
fun GestureControls(
    viewModel: VideoPlayerViewModel,
    window: Window,
    onToggleControls: () -> Unit
) {
    val context = LocalContext.current
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    var startBrightness by remember { mutableStateOf(0.5f) }
    var startVolume by remember { mutableStateOf(0) }
    var totalDragAmount by remember { mutableStateOf(0f) }
    var gestureType by remember { mutableStateOf(GestureType.NONE) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                coroutineScope {
                    launch {
                        detectTapGestures(
                            onTap = { onToggleControls() },
                            onDoubleTap = { offset ->
                                val screenWidth = size.width
                                when {
                                    offset.x < screenWidth / 3 -> viewModel.seekBackward()
                                    offset.x > screenWidth * 2 / 3 -> viewModel.seekForward()
                                    else -> viewModel.togglePlayPause()
                                }
                            }
                        )
                    }

                    launch {
                        detectVerticalDragGestures(
                            onDragStart = { offset ->
                                val screenWidth = size.width
                                gestureType = if (offset.x < screenWidth / 2) GestureType.BRIGHTNESS else GestureType.VOLUME
                                startBrightness = window.attributes.screenBrightness.takeIf { it > 0 } ?: 0.5f
                                startVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                totalDragAmount = 0f
                            },
                            onVerticalDrag = { change, drag ->
                                change.consume()
                                totalDragAmount += drag
                                val sensitivity = size.height / 3.0f
                                val changeRatio = (-totalDragAmount / sensitivity)

                                if (gestureType == GestureType.BRIGHTNESS) {
                                    val newBrightness = (startBrightness + changeRatio).coerceIn(0.01f, 1.0f)
                                    window.attributes = window.attributes.apply { screenBrightness = newBrightness }
                                } else if (gestureType == GestureType.VOLUME) {
                                    val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                    val volumeChange = (changeRatio * maxVolume).roundToInt()
                                    val newVolume = (startVolume + volumeChange).coerceIn(0, maxVolume)
                                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
                                }
                            },
                            onDragEnd = { gestureType = GestureType.NONE }
                        )
                    }
                }
            }
    )
}
