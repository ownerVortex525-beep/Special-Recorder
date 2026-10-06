package com.ownervortex.nyxrecorder

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.ownervortex.nyxrecorder.core.recording.RecordingStatus
import com.ownervortex.nyxrecorder.ui.AppRoot
import com.ownervortex.nyxrecorder.ui.theme.NixTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NixTheme {
                val status by RecordingStatus.state.collectAsState()
                if (status.isRecording && !status.isPaused) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                AppRoot()
            }
        }
    }
}
