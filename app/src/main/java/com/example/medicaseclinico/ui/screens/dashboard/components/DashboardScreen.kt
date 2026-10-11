package com.example.medicaseclinico.ui.screens.dashboard

import android.text.format.DateUtils
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.medicaseclinico.R
import com.example.medicaseclinico.ui.screens.dashboard.components.BatteryCard
import com.example.medicaseclinico.ui.screens.dashboard.components.DashboardEmptyContent
import com.example.medicaseclinico.ui.screens.dashboard.components.DashboardErrorContent
import com.example.medicaseclinico.ui.screens.dashboard.components.DashboardLoadingContent
import com.example.medicaseclinico.ui.screens.dashboard.components.DashboardTopBar
import com.example.medicaseclinico.ui.screens.dashboard.components.TemperatureCard

/**
 * Punto de entrada del Dashboard desde la navegación.
 */
@Composable
fun DashboardRoute(
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = viewModel(factory = DashboardViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    DashboardScreen(
        uiState = uiState,
        onRetry = viewModel::retry,
        modifier = modifier
    )
}

/**
 * Pantalla Inicio de MediCase Clínico. 100 % sin estado y sin lógica.
 */
@Composable
fun DashboardScreen(
    uiState: DashboardUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        topBar = { DashboardTopBar(connectionStatus = uiState.connectionStatus) },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        AnimatedContent(
            targetState = uiState,
            contentKey = { it::class },
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "dashboardState",
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) { state ->
            when (state) {
                is DashboardUiState.Loading -> DashboardLoadingContent()
                is DashboardUiState.Empty -> DashboardEmptyContent()
                is DashboardUiState.Error -> DashboardErrorContent(
                    reason = state.reason,
                    canRetry = state.canRetry,
                    onRetry = onRetry
                )
                is DashboardUiState.Success -> DashboardSuccessContent(state = state)
            }
        }
    }
}

/** Contenido con datos reales: tarjeta de temperatura, batería y sincronización. */
@Composable
private fun DashboardSuccessContent(
    state: DashboardUiState.Success,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TemperatureCard(
            temperatureCelsius = state.currentTemperature,
            status = state.temperatureStatus,
            isLiveData = state.isLiveData
        )
        BatteryCard(
            batteryLevel = state.batteryLevel,
            isLow = state.isBatteryLow,
            modifier = Modifier.fillMaxWidth()
        )
        val relativeTime = DateUtils.getRelativeTimeSpanString(
            state.lastReadingTimestamp,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS
        ).toString()
        Text(
            text = stringResource(R.string.dashboard_last_sync, relativeTime),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .align(Alignment.CenterHorizontally)
        )
    }
}