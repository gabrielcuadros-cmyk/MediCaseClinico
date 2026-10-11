package com.example.medicaseclinico.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.example.medicaseclinico.ui.navigation.MediCaseNavGraph
import com.example.medicaseclinico.ui.navigation.MediCaseNavigationBar
import com.example.medicaseclinico.ui.navigation.currentTopLevelDestination
import com.example.medicaseclinico.ui.navigation.navigateToTopLevel

/**
 * Raíz de MediCase Clínico: barra inferior persistente + grafo de navegación.
 */
@Composable
fun MediCaseApp(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            MediCaseNavigationBar(
                currentDestination = navController.currentTopLevelDestination(),
                onNavigate = { destination -> navController.navigateToTopLevel(destination) }
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        MediCaseNavGraph(
            navController = navController,
            modifier = Modifier.padding(innerPadding)
        )
    }
}