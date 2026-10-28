package com.tdbe.playonconnect

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.tdbe.playonconnect.ui.App
import com.tdbe.playonconnect.ui.theme.PlayOnConnectTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    companion object {
        val resumeCount = MutableStateFlow(0)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PlayOnConnectTheme {
                App()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeCount.value++
    }
}
