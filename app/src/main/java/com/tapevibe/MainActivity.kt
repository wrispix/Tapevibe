package com.tapevibe

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.tapevibe.audio.NativeAudioEngine
import com.tapevibe.data.ProjectStore
import com.tapevibe.ui.DawScreen
import com.tapevibe.ui.DawViewModel
import com.tapevibe.ui.theme.TapevibeTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import java.io.File
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    private val vm: DawViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val engineThread = Executors.newSingleThreadExecutor { r ->
                    Thread(r, "engine-control").apply { priority = Thread.NORM_PRIORITY + 1 }
                }.asCoroutineDispatcher()
                return DawViewModel(
                    engine = NativeAudioEngine(),
                    store = ProjectStore(File(filesDir, "projects")),
                    cacheDir = cacheDir,
                    engineDispatcher = engineThread,
                    ioDispatcher = Dispatchers.IO,
                ) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TapevibeTheme {
                DawScreen(vm)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        vm.onForeground()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) vm.onBackground()
    }
}
