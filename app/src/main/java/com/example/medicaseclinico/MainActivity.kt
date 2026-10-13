package com.example.medicaseclinico

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.medicaseclinico.ui.MediCaseApp
import com.example.medicaseclinico.ui.theme.MediCaseAppearanceTheme

/**
 * Única Activity de la app (arquitectura Single-Activity).
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MediCaseAppearanceTheme {
                MediCaseApp()
            }
        }
    }
}