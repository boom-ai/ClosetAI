package com.sanket.closetai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.sanket.closetai.ui.AppViewModel
import com.sanket.closetai.ui.ClosetApp
import com.sanket.closetai.ui.ClosetTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ClosetTheme { ClosetApp(vm) }
        }
    }
}
