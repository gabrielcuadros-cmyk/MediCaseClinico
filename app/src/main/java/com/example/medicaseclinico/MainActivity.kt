package com.example.medicaseclinico

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.medicaseclinico.ui.MediCaseApp
import com.example.medicaseclinico.ui.navigation.LaunchDestination
import com.example.medicaseclinico.ui.navigation.TopLevelDestination
import com.example.medicaseclinico.ui.theme.MediCaseAppearanceTheme

/**
 * Única Activity de la app (arquitectura Single-Activity).
 *
 * También recibe los accesos directos del widget: la pestaña pedida queda
 * pendiente hasta que la app la abre, aunque antes haya que iniciar sesión.
 */
class MainActivity : ComponentActivity() {

    private var pendingDestination by mutableStateOf<TopLevelDestination?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Tras rotar la pantalla el Intent es el mismo: no se vuelve a navegar.
        if (savedInstanceState == null) {
            pendingDestination = LaunchDestination.from(intent)
        }
        setContent {
            MediCaseAppearanceTheme {
                MediCaseApp(
                    launchDestination = pendingDestination,
                    onLaunchDestinationHandled = { pendingDestination = null }
                )
            }
        }
    }

    /** La app ya estaba abierta y el widget pide otra pestaña. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        LaunchDestination.from(intent)?.let { pendingDestination = it }
    }
}
