package com.example.medicaseclinico.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.example.medicaseclinico.R
import com.example.medicaseclinico.ui.components.MediCaseLoadingState
import com.example.medicaseclinico.ui.navigation.AuthNavGraph
import com.example.medicaseclinico.ui.navigation.MediCaseNavGraph
import com.example.medicaseclinico.ui.navigation.MediCaseNavigationBar
import com.example.medicaseclinico.ui.navigation.currentTopLevelDestination
import com.example.medicaseclinico.ui.navigation.navigateToTopLevel

/**
 * Raíz de MediCase Clínico: la compuerta de sesión.
 *
 * Sin sesión solo existen las pantallas de cuenta; con sesión, la app. Son
 * dos grafos de navegación separados para que, tras cerrar sesión, "Atrás" no
 * pueda devolver a una pantalla con los datos del estuche.
 */
@Composable
fun MediCaseApp(
    modifier: Modifier = Modifier,
    viewModel: MediCaseAppViewModel = viewModel(factory = MediCaseAppViewModel.Factory)
) {
    val sessionState by viewModel.sessionState.collectAsStateWithLifecycle()

    when (sessionState) {
        SessionState.CHECKING ->
            AccountSurface(modifier = modifier) {
                MediCaseLoadingState(message = stringResource(R.string.session_checking))
            }

        SessionState.SIGNED_OUT ->
            AccountSurface(modifier = modifier) { AuthNavGraph() }

        SessionState.SIGNED_IN ->
            MainApp(viewModel = viewModel, onSignOut = viewModel::signOut, modifier = modifier)

        // Sin servicio de cuentas no hay sesión que cerrar.
        SessionState.NOT_REQUIRED ->
            MainApp(viewModel = viewModel, onSignOut = null, modifier = modifier)
    }
}

/**
 * Fondo de las pantallas sin barra inferior. La app dibuja de borde a borde,
 * así que aquí se dejan libres las barras del sistema y el teclado.
 */
@Composable
private fun AccountSurface(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(modifier = Modifier.safeDrawingPadding()) { content() }
    }
}

/** La app con sesión: barra inferior persistente + grafo de navegación. */
@Composable
private fun MainApp(
    viewModel: MediCaseAppViewModel,
    onSignOut: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    val unreadAlertsCount by viewModel.unreadAlertsCount.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            MediCaseNavigationBar(
                currentDestination = navController.currentTopLevelDestination(),
                onNavigate = { destination -> navController.navigateToTopLevel(destination) },
                activeAlertsCount = unreadAlertsCount
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        MediCaseNavGraph(
            navController = navController,
            onSignOut = onSignOut,
            modifier = Modifier.padding(innerPadding)
        )
    }
}
