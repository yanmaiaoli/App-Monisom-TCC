package com.example.monisom

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.monisom.ui.MoniSomApp
import com.example.monisom.ui.theme.MoniSomTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MoniSomTheme {
                MoniSomApp()
            }
        }
    }
}
