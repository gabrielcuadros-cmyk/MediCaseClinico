package com.example.medicaseclinico.ui.screens.alerts

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.medicaseclinico.R
import com.example.medicaseclinico.domain.model.AlertFilter
import com.example.medicaseclinico.domain.model.AlertType
import com.example.medicaseclinico.domain.model.RelativeTime
import com.example.medicaseclinico.ui.components.MediCaseBackButton
import com.example.medicaseclinico.ui.components.MediCaseLoadingState
import com.example.medicaseclinico.ui.components.MediCaseMessageState
import com.example.medicaseclinico.ui.screens.alerts.components.AlertCard
import com.example.medicaseclinico.ui.screens.alerts.components.AlertFilterChips
import com.example.medicaseclinico.ui.theme.MediCaseTheme

private val ScreenPadding = 16.dp
private val CardSpacing = 12.dp

/**
 * Entrada de la pestaña Alertas: conecta el ViewModel con la pantalla sin estado.
 *
 * @param requestedFilter filtro con el que otra pantalla pide abrir la lista,
 * o null para dejar el que ya hubiera.
 * @param onRequestedFilterApplied el filtro pedido ya se aplicó.
 */
@Composable
fun AlertsRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    requestedFilter: AlertFilter? = null,
    onRequestedFilterApplied: () -> Unit = {},
    viewModel: AlertsViewModel = viewModel(factory = AlertsViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Otra pantalla pidió abrir la lista ya filtrada (p. ej. solo Seguridad).
    // Se aplica una vez: después manda lo que el usuario toque en los chips.
    LaunchedEffect(requestedFilter) {
        if (requestedFilter != null) {
            viewModel.onFilterSelected(requestedFilter)
            onRequestedFilterApplied()
        }
    }

    AlertsScreen(
        uiState = uiState,
        onBack = onBack,
        onFilterSelected = viewModel::onFilterSelected,
        onRetry = viewModel::retry,
        modifier = modifier
    )
}

/**
 * Pantalla "Alertas de Seguridad": chips de filtro y lista de alertas emitidas.
 *
 * Los chips siguen visibles cuando un filtro no tiene alertas, para que el
 * usuario pueda cambiar a otro sin salir de la pantalla.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertsScreen(
    uiState: AlertsUiState,
    onBack: () -> Unit,
    onFilterSelected: (AlertFilter) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.alerts_title),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    MediCaseBackButton(
                        onClick = onBack,
                        modifier = Modifier.padding(start = 12.dp, end = 8.dp)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Text(
                text = stringResource(R.string.alerts_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = ScreenPadding)
            )
            Spacer(modifier = Modifier.height(12.dp))

            when (uiState) {
                AlertsUiState.Loading ->
                    MediCaseLoadingState(message = stringResource(R.string.alerts_loading))

                is AlertsUiState.Empty -> {
                    FilterRow(
                        selectedFilter = uiState.selectedFilter,
                        onFilterSelected = onFilterSelected
                    )
                    MediCaseMessageState(
                        icon = Icons.Outlined.NotificationsNone,
                        title = stringResource(uiState.selectedFilter.emptyTitleRes()),
                        message = stringResource(uiState.selectedFilter.emptyMessageRes()),
                        accentColor = MaterialTheme.colorScheme.primary,
                        accentContainerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                }

                is AlertsUiState.Success -> {
                    FilterRow(
                        selectedFilter = uiState.selectedFilter,
                        onFilterSelected = onFilterSelected
                    )
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = ScreenPadding,
                            end = ScreenPadding,
                            top = CardSpacing,
                            bottom = ScreenPadding
                        ),
                        verticalArrangement = Arrangement.spacedBy(CardSpacing)
                    ) {
                        items(items = uiState.alerts, key = { it.id }) { alert ->
                            AlertCard(alert = alert)
                        }
                    }
                }

                AlertsUiState.Error ->
                    MediCaseMessageState(
                        icon = Icons.Outlined.ErrorOutline,
                        title = stringResource(R.string.alerts_error_title),
                        message = stringResource(R.string.alerts_error_message),
                        accentColor = MaterialTheme.colorScheme.error,
                        accentContainerColor = MaterialTheme.colorScheme.errorContainer,
                        actionLabel = stringResource(R.string.action_retry),
                        onAction = onRetry
                    )
            }
        }
    }
}

@Composable
private fun FilterRow(
    selectedFilter: AlertFilter,
    onFilterSelected: (AlertFilter) -> Unit
) {
    AlertFilterChips(
        selectedFilter = selectedFilter,
        onFilterSelected = onFilterSelected,
        contentPadding = PaddingValues(horizontal = ScreenPadding)
    )
}

@StringRes
private fun AlertFilter.emptyTitleRes(): Int = when (this) {
    AlertFilter.ALL -> R.string.alerts_empty_all_title
    AlertFilter.TEMPERATURE -> R.string.alerts_empty_temperature_title
    AlertFilter.LOCATION -> R.string.alerts_empty_location_title
    AlertFilter.SECURITY -> R.string.alerts_empty_security_title
}

@StringRes
private fun AlertFilter.emptyMessageRes(): Int = when (this) {
    AlertFilter.ALL -> R.string.alerts_empty_all_message
    AlertFilter.TEMPERATURE -> R.string.alerts_empty_temperature_message
    AlertFilter.LOCATION -> R.string.alerts_empty_location_message
    AlertFilter.SECURITY -> R.string.alerts_empty_security_message
}

// ------------------------------------------------------------------
//  Previews (valores de ejemplo solo para el IDE)
// ------------------------------------------------------------------
@Composable
private fun AlertsScreenPreview(uiState: AlertsUiState) {
    MediCaseTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            AlertsScreen(
                uiState = uiState,
                onBack = {},
                onFilterSelected = {},
                onRetry = {}
            )
        }
    }
}

@Preview(showBackground = true, name = "Alertas - Loading")
@Composable
private fun AlertsLoadingPreview() {
    AlertsScreenPreview(AlertsUiState.Loading)
}

@Preview(showBackground = true, name = "Alertas - Empty (Ubicación)")
@Composable
private fun AlertsEmptyPreview() {
    AlertsScreenPreview(AlertsUiState.Empty(selectedFilter = AlertFilter.LOCATION))
}

@Preview(showBackground = true, name = "Alertas - Error")
@Composable
private fun AlertsErrorPreview() {
    AlertsScreenPreview(AlertsUiState.Error)
}

@Preview(showBackground = true, name = "Alertas - Success")
@Composable
private fun AlertsSuccessPreview() {
    AlertsScreenPreview(
        AlertsUiState.Success(
            selectedFilter = AlertFilter.ALL,
            alerts = listOf(
                AlertItem(
                    id = 1,
                    type = AlertType.TEMPERATURE,
                    value = 8.4f,
                    elapsed = RelativeTime.Minutes(5)
                ),
                AlertItem(
                    id = 2,
                    type = AlertType.BATTERY,
                    value = 15f,
                    elapsed = RelativeTime.Hours(3)
                ),
                AlertItem(
                    id = 3,
                    type = AlertType.CONNECTION,
                    value = null,
                    elapsed = RelativeTime.Hours(12)
                )
            )
        )
    )
}
