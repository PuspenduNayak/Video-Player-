package com.example.videoplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge // Keep this if you want edge-to-edge content
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color // Import Color
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.videoplayer.ui.theme.VideoPlayerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            VideoPlayerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val viewModel: VideoPlayerViewModel = viewModel(
                        factory = object : ViewModelProvider.Factory {
                            @Suppress("UNCHECKED_CAST")
                            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                return VideoPlayerViewModel(application) as T
                            }
                        }
                    )
                    // Pass the WindowInsetsControllerCompat to the Composable
                    // so it can hide/show the system bars
                    MediaPlayerScreen(
                        viewModel = viewModel,
                        windowInsetsController = WindowCompat.getInsetsController(window, window.decorView),
                        window = window
                    )
                }
            }
        }
    }
}