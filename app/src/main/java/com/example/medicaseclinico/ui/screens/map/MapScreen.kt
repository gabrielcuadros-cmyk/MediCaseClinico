package com.example.medicaseclinico.ui.screens.map

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.medicaseclinico.R
import com.example.medicaseclinico.domain.geo.GeoPoint
import com.example.medicaseclinico.domain.model.RelativeTime
import com.example.medicaseclinico.domain.time.DateTimeLabels
import com.example.medicaseclinico.ui.components.MediCaseBackButton
import com.example.medicaseclinico.ui.components.MediCaseLoadingState
import com.example.medicaseclinico.ui.components.MediCaseMessageState
import com.example.medicaseclinico.ui.components.map.MapCircle
import com.example.medicaseclinico.ui.components.map.MediCaseMap
import com.example.medicaseclinico.ui.screens.map.components.CaseLocationCard
import com.example.medicaseclinico.ui.theme.MediCaseTheme
import kotlinx.coroutines.launch

private val ScreenPadding = 16.dp
private val ButtonShape = RoundedCornerShape(12.dp)
private val ButtonHeight = 52.dp
private val AttributionClearance = 32.dp

/**
 * Entrada de la pestaña Mapa: conecta el ViewModel con la pantalla sin estado
 * y lanza el `Intent` que abre la posición en Google Maps.
 */
@Composable
fun MapRoute(
    onBack: () -> Unit,
    onOpenLossMode: () -> Unit,
    onConfigureGeofence: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MapViewModel = viewModel(factory = MapViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val openFailedMessage = stringResource(R.string.map_open_external_failed)

    MapScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onRetry = viewModel::retry,
        onOpenInGoogleMaps = { latitude, longitude ->
            if (!openInGoogleMaps(context, latitude, longitude)) {
                scope.launch { snackbarHostState.showSnackbar(openFailedMessage) }
            }
        },
        onOpenLossMode = onOpenLossMode,
        onConfigureGeofence = onConfigureGeofence,
        modifier = modifier
    )
}

/**
 * Pantalla "Geolocalización": mapa con el estuche y sus zonas, tarjeta de
 * coordenadas y accesos al Modo Pérdida y a la configuración de geocercas.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    uiState: MapUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenInGoogleMaps: (latitude: Double, longitude: Double) -> Unit,
    onOpenLossMode: () -> Unit,
    onConfigureGeofence: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.map_title),
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
                text = stringResource(R.string.map_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = ScreenPadding)
            )
            Spacer(modifier = Modifier.height(12.dp))

            when (uiState) {
                MapUiState.Loading ->
                    MediCaseLoadingState(message = stringResource(R.string.map_loading))

                is MapUiState.Empty ->
                    EmptyContent(
                        liveStatus = uiState.liveStatus,
                        onConfigureGeofence = onConfigureGeofence
                    )

                is MapUiState.Success ->
                    MapContent(
                        state = uiState,
                        onOpenInGoogleMaps = onOpenInGoogleMaps,
                        onOpenLossMode = onOpenLossMode,
                        onConfigureGeofence = onConfigureGeofence
                    )

                MapUiState.Error ->
                    MediCaseMessageState(
                        icon = Icons.Outlined.ErrorOutline,
                        title = stringResource(R.string.map_error_title),
                        message = stringResource(R.string.map_error_message),
                        accentColor = MaterialTheme.colorScheme.error,
                        accentContainerColor = MaterialTheme.colorScheme.errorContainer,
                        actionLabel = stringResource(R.string.action_retry),
                        onAction = onRetry
                    )
            }
        }
    }
}

/**
 * Sin ninguna posición del estuche no hay mapa que enseñar, pero las zonas de
 * seguridad se pueden dejar preparadas igualmente: el acceso a configurarlas
 * no debe depender de que el estuche esté conectado.
 */
@Composable
private fun EmptyContent(
    liveStatus: CaseLiveStatus,
    onConfigureGeofence: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        MediCaseMessageState(
            icon = Icons.Outlined.LocationOff,
            title = stringResource(liveStatus.emptyTitleRes()),
            message = stringResource(liveStatus.emptyMessageRes()),
            accentColor = MaterialTheme.colorScheme.primary,
            accentContainerColor = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.weight(1f)
        )
        OutlinedButton(
            onClick = onConfigureGeofence,
            shape = ButtonShape,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .fillMaxWidth()
                .padding(ScreenPadding)
                .heightIn(min = ButtonHeight)
        ) {
            ButtonLabel(
                icon = Icons.Outlined.Settings,
                text = stringResource(R.string.map_configure_geofence)
            )
        }
    }
}

/**
 * El mapa ocupa el alto que sobra y lo de abajo no se desplaza: un mapa dentro
 * de una lista con scroll se pelea con ella por el gesto de arrastrar.
 */
@Composable
private fun MapContent(
    state: MapUiState.Success,
    onOpenInGoogleMaps: (latitude: Double, longitude: Double) -> Unit,
    onOpenLossMode: () -> Unit,
    onConfigureGeofence: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        CaseMapArea(
            state = state,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )
        Column(
            modifier = Modifier.padding(ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CaseLocationCard(
                latitude = state.latitude,
                longitude = state.longitude,
                elapsed = state.elapsed,
                nearestGeofence = state.nearestGeofence,
                onAdjustGeofence = onConfigureGeofence
            )
            OutlinedButton(
                onClick = { onOpenInGoogleMaps(state.latitude, state.longitude) },
                shape = ButtonShape,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) {
                ButtonLabel(
                    icon = Icons.Outlined.Map,
                    text = stringResource(R.string.map_open_google_maps)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onOpenLossMode,
                    shape = ButtonShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MediCaseTheme.statusColors.critical,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = ButtonHeight)
                ) {
                    ButtonLabel(
                        icon = Icons.Outlined.WarningAmber,
                        text = stringResource(R.string.map_loss_mode)
                    )
                }
                OutlinedButton(
                    onClick = onConfigureGeofence,
                    shape = ButtonShape,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = ButtonHeight)
                ) {
                    ButtonLabel(
                        icon = Icons.Outlined.Settings,
                        text = stringResource(R.string.map_configure_geofence)
                    )
                }
            }
        }
    }
}

@Composable
private fun ButtonLabel(icon: ImageVector, text: String) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(18.dp)
    )
    Spacer(modifier = Modifier.width(6.dp))
    Text(
        text = text,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

/**
 * El mapa con lo que el diseño pone encima: botón para centrar, nombre de la
 * zona en la que está el estuche, hora de la posición y, si la posición no es
 * la actual, el aviso de por qué.
 */
@Composable
private fun CaseMapArea(
    state: MapUiState.Success,
    modifier: Modifier = Modifier
) {
    // Estado solo visual: cuántas veces se pidió centrar el mapa en el estuche.
    var recenterRequests by remember { mutableIntStateOf(0) }

    val marker = remember(state.latitude, state.longitude) {
        GeoPoint(latitude = state.latitude, longitude = state.longitude)
    }
    val circles = remember(state.geofences) {
        state.geofences.map {
            MapCircle(latitude = it.latitude, longitude = it.longitude, radiusMeters = it.radiusMeters)
        }
    }

    Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        MediCaseMap(
            modifier = Modifier.fillMaxSize(),
            marker = marker,
            circles = circles,
            recenterRequests = recenterRequests
        )

        RecenterButton(
            onClick = { recenterRequests++ },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(start = 64.dp, end = 64.dp, top = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (state.liveStatus != CaseLiveStatus.LIVE) {
                NotLiveBanner(liveStatus = state.liveStatus)
            }
            state.nearestGeofence?.takeIf { it.isInside }?.let { geofence ->
                ZoneChip(name = geofence.name)
            }
        }

        PositionTimeLabel(
            updatedAt = state.updatedAt,
            isLive = state.liveStatus == CaseLiveStatus.LIVE,
            // Una línea por encima de la atribución del mapa, para no taparla.
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 6.dp, bottom = AttributionClearance)
        )
    }
}

@Composable
private fun RecenterButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .size(40.dp)
            .semantics { role = Role.Button },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.primary,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Outlined.CenterFocusStrong,
                contentDescription = stringResource(R.string.map_recenter),
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

/** Etiqueta blanca del diseño con el nombre del lugar; aquí, la zona en la que está el estuche. */
@Composable
private fun ZoneChip(name: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.LocationOn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Aviso de que el marcador no es la posición actual. Va con ícono y texto, no
 * solo con color, y se anuncia a los lectores de pantalla cuando aparece.
 */
@Composable
private fun NotLiveBanner(liveStatus: CaseLiveStatus) {
    Surface(
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        shape = RoundedCornerShape(10.dp),
        color = MediCaseTheme.statusColors.warningContainer,
        border = BorderStroke(1.dp, MediCaseTheme.statusColors.warning)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.LocationOff,
                contentDescription = null,
                tint = MediCaseTheme.statusColors.warning,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(liveStatus.bannerRes()),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun PositionTimeLabel(
    updatedAt: Long,
    isLive: Boolean,
    modifier: Modifier = Modifier
) {
    val dateTimeLabels = remember { DateTimeLabels.system() }
    val time = remember(dateTimeLabels, updatedAt) { dateTimeLabels.positionTime(updatedAt) }
    MapOverlayLabel(
        text = stringResource(
            if (isLive) R.string.map_timestamp_live else R.string.map_timestamp_last_known,
            time
        ),
        modifier = modifier
    )
}

/** Texto claro sobre fondo oscuro translúcido, legible encima de cualquier parte del mapa. */
@Composable
private fun MapOverlayLabel(
    text: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.8f)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.inverseOnSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@StringRes
private fun CaseLiveStatus.emptyTitleRes(): Int = when (this) {
    CaseLiveStatus.NO_GPS_SIGNAL -> R.string.map_empty_no_signal_title
    CaseLiveStatus.GPS_NOT_SUPPORTED -> R.string.map_empty_not_supported_title
    CaseLiveStatus.NO_LINK, CaseLiveStatus.LIVE -> R.string.map_empty_no_link_title
}

@StringRes
private fun CaseLiveStatus.emptyMessageRes(): Int = when (this) {
    CaseLiveStatus.NO_GPS_SIGNAL -> R.string.map_empty_no_signal_message
    CaseLiveStatus.GPS_NOT_SUPPORTED -> R.string.map_empty_not_supported_message
    CaseLiveStatus.NO_LINK, CaseLiveStatus.LIVE -> R.string.map_empty_no_link_message
}

@StringRes
private fun CaseLiveStatus.bannerRes(): Int = when (this) {
    CaseLiveStatus.NO_GPS_SIGNAL -> R.string.map_status_no_signal
    CaseLiveStatus.GPS_NOT_SUPPORTED -> R.string.map_status_not_supported
    CaseLiveStatus.NO_LINK, CaseLiveStatus.LIVE -> R.string.map_status_no_link
}

// ---------------------------------------------------------------------
//  Previews (los valores de ejemplo solo existen en el IDE)
// ---------------------------------------------------------------------
@Composable
private fun MapScreenPreview(uiState: MapUiState) {
    MediCaseTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            MapScreen(
                uiState = uiState,
                snackbarHostState = remember { SnackbarHostState() },
                onBack = {},
                onRetry = {},
                onOpenInGoogleMaps = { _, _ -> },
                onOpenLossMode = {},
                onConfigureGeofence = {}
            )
        }
    }
}

@Preview(showBackground = true, name = "Mapa · en vivo")
@Composable
private fun MapScreenLivePreview() {
    MapScreenPreview(
        MapUiState.Success(
            latitude = 40.4167,
            longitude = -3.7037,
            updatedAt = 1_760_000_000_000L,
            elapsed = RelativeTime.Minutes(2),
            liveStatus = CaseLiveStatus.LIVE,
            geofences = listOf(
                MapGeofence(
                    id = 1,
                    name = "Clínica Médica Norte",
                    latitude = 40.4167,
                    longitude = -3.7037,
                    radiusMeters = 500.0,
                    isInside = true
                )
            )
        )
    )
}

@Preview(showBackground = true, name = "Mapa · última ubicación conocida")
@Composable
private fun MapScreenLastKnownPreview() {
    MapScreenPreview(
        MapUiState.Success(
            latitude = -16.3989,
            longitude = -71.5369,
            updatedAt = 1_760_000_000_000L,
            elapsed = RelativeTime.Hours(3),
            liveStatus = CaseLiveStatus.NO_GPS_SIGNAL,
            geofences = emptyList()
        )
    )
}

@Preview(showBackground = true, name = "Mapa · sin ubicación")
@Composable
private fun MapScreenEmptyPreview() {
    MapScreenPreview(MapUiState.Empty(liveStatus = CaseLiveStatus.NO_LINK))
}

@Preview(showBackground = true, name = "Mapa · error")
@Composable
private fun MapScreenErrorPreview() {
    MapScreenPreview(MapUiState.Error)
}
