package com.example.medicaseclinico.ui.screens.geofence

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.medicaseclinico.R
import com.example.medicaseclinico.domain.geo.GeoPoint
import com.example.medicaseclinico.domain.model.GeofenceLimits
import com.example.medicaseclinico.ui.components.MediCaseBackButton
import com.example.medicaseclinico.ui.components.MediCaseLoadingState
import com.example.medicaseclinico.ui.components.MediCaseMessageState
import com.example.medicaseclinico.ui.components.map.MapCenterPicker
import com.example.medicaseclinico.ui.components.map.MapCircle
import com.example.medicaseclinico.ui.components.map.MediCaseMap
import com.example.medicaseclinico.ui.theme.MediCaseTheme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val ScreenPadding = 16.dp
private val MapHeight = 220.dp
private val CenterPinSize = 44.dp
private val SliderThumbSize = 20.dp
private val SliderTrackHeight = 4.dp
private val FieldShape = RoundedCornerShape(12.dp)
private val ZoneCardShape = RoundedCornerShape(14.dp)

/**
 * Entrada de "Configurar Geocerca": conecta el ViewModel con la pantalla sin
 * estado y muestra el aviso de guardado.
 */
@Composable
fun GeofenceConfigRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GeofenceConfigViewModel = viewModel(factory = GeofenceConfigViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val message = (uiState as? GeofenceConfigUiState.Success)?.message
    val savedText = stringResource(R.string.geofence_saved_message)
    val failedText = stringResource(R.string.geofence_save_failed)
    val deletedText = stringResource(R.string.geofence_deleted_message)
    val deleteFailedText = stringResource(R.string.geofence_delete_failed)
    LaunchedEffect(message) {
        if (message == null) return@LaunchedEffect
        // Se da por mostrado antes de enseñarlo: si no, al girar el teléfono
        // mientras está en pantalla volvería a salir.
        viewModel.onMessageShown()
        scope.launch {
            snackbarHostState.showSnackbar(
                when (message) {
                    GeofenceConfigMessage.SAVED -> savedText
                    GeofenceConfigMessage.SAVE_FAILED -> failedText
                    GeofenceConfigMessage.DELETED -> deletedText
                    GeofenceConfigMessage.DELETE_FAILED -> deleteFailedText
                }
            )
        }
    }

    GeofenceConfigScreen(
        uiState = uiState,
        zoneName = viewModel.zoneName,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onNameChange = viewModel::onNameChange,
        onRadiusChange = viewModel::onRadiusChange,
        onCenterChanged = viewModel::onCenterChanged,
        onSave = viewModel::save,
        onDeleteZone = viewModel::deleteZone,
        onRetry = viewModel::retry,
        modifier = modifier
    )
}

/**
 * Pantalla "Configurar Geocerca": el usuario mueve el mapa para situar la
 * zona, le pone nombre y radio, y la guarda. Debajo, las zonas ya guardadas.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeofenceConfigScreen(
    uiState: GeofenceConfigUiState,
    zoneName: String,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onNameChange: (String) -> Unit,
    onRadiusChange: (Int) -> Unit,
    onCenterChanged: (GeoPoint) -> Unit,
    onSave: () -> Unit,
    onDeleteZone: (Long) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.geofence_config_title),
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
        },
        bottomBar = {
            // Fuera del scroll para que guardar esté siempre a mano.
            if (uiState is GeofenceConfigUiState.Success) {
                SaveButton(
                    enabled = uiState.canSave,
                    isSaving = uiState.isSaving,
                    onClick = {
                        focusManager.clearFocus()
                        onSave()
                    },
                    modifier = Modifier.padding(horizontal = ScreenPadding, vertical = 12.dp)
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Text(
                text = stringResource(R.string.geofence_config_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = ScreenPadding)
            )
            Spacer(modifier = Modifier.height(12.dp))

            when (uiState) {
                GeofenceConfigUiState.Loading ->
                    MediCaseLoadingState(message = stringResource(R.string.geofence_config_loading))

                is GeofenceConfigUiState.Success ->
                    GeofenceConfigContent(
                        state = uiState,
                        zoneName = zoneName,
                        onNameChange = onNameChange,
                        onRadiusChange = onRadiusChange,
                        onCenterChanged = onCenterChanged,
                        onDeleteZone = onDeleteZone
                    )

                GeofenceConfigUiState.Error ->
                    MediCaseMessageState(
                        icon = Icons.Outlined.ErrorOutline,
                        title = stringResource(R.string.geofence_config_error_title),
                        message = stringResource(R.string.geofence_config_error_message),
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
 * El mapa queda fijo arriba y solo se desplaza el formulario: aquí arrastrar
 * el mapa es la forma de elegir el lugar, y dentro de un scroll se pelearía
 * con él por el gesto.
 */
@Composable
private fun GeofenceConfigContent(
    state: GeofenceConfigUiState.Success,
    zoneName: String,
    onNameChange: (String) -> Unit,
    onRadiusChange: (Int) -> Unit,
    onCenterChanged: (GeoPoint) -> Unit,
    onDeleteZone: (Long) -> Unit
) {
    // Estado solo visual: la zona por la que se está preguntando. Se guarda
    // el id para que el diálogo sobreviva a un giro del teléfono.
    var pendingDeleteId by rememberSaveable { mutableStateOf<Long?>(null) }
    val pendingDelete = state.savedZones.firstOrNull { it.id == pendingDeleteId }

    if (pendingDelete != null) {
        DeleteZoneDialog(
            zoneName = pendingDelete.name,
            onConfirm = {
                pendingDeleteId = null
                onDeleteZone(pendingDelete.id)
            },
            onDismiss = { pendingDeleteId = null }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ZonePickerMap(
            state = state,
            onCenterChanged = onCenterChanged,
            modifier = Modifier
                .fillMaxWidth()
                .height(MapHeight)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ZoneNameField(name = zoneName, onNameChange = onNameChange)
            RadiusSlider(radiusMeters = state.radiusMeters, onRadiusChange = onRadiusChange)
            SavedZones(zones = state.savedZones, onDeleteClick = { pendingDeleteId = it.id })
        }
    }
}

/**
 * Mapa en modo "elegir un punto": el pin del centro no se mueve y el usuario
 * desliza el mapa por debajo. El círculo muestra el radio elegido en metros reales.
 */
@Composable
private fun ZonePickerMap(
    state: GeofenceConfigUiState.Success,
    onCenterChanged: (GeoPoint) -> Unit,
    modifier: Modifier = Modifier
) {
    val circles = remember(state.savedZones) {
        state.savedZones.map {
            MapCircle(latitude = it.latitude, longitude = it.longitude, radiusMeters = it.radiusMeters)
        }
    }
    val picker = remember(state.radiusMeters, state.mapStart) {
        MapCenterPicker(radiusMeters = state.radiusMeters.toDouble(), start = state.mapStart)
    }

    MediCaseMap(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        marker = state.casePosition,
        circles = circles,
        centerPicker = picker,
        onCenterChanged = onCenterChanged
    ) {
        // La punta del pin debe caer justo en el centro del mapa.
        Icon(
            imageVector = Icons.Filled.LocationOn,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = -CenterPinSize / 2)
                .size(CenterPinSize)
        )

        // Sin un lugar conocido desde el que abrir, el mapa muestra el mundo
        // entero: se explica qué hacer en vez de dejar al usuario adivinarlo.
        if (!state.hasCenter) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 32.dp, vertical = 12.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.8f)
            ) {
                Text(
                    text = stringResource(R.string.geofence_move_map_hint),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun ZoneNameField(
    name: String,
    onNameChange: (String) -> Unit
) {
    val focusManager = LocalFocusManager.current
    val label = stringResource(R.string.geofence_name_label)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FieldLabel(text = label)
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = label },
            placeholder = { Text(text = stringResource(R.string.geofence_name_placeholder)) },
            singleLine = true,
            shape = FieldShape,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RadiusSlider(
    radiusMeters: Int,
    onRadiusChange: (Int) -> Unit
) {
    // La posición del dedo vive aquí para que el control no vaya a saltos
    // mientras el valor viaja al ViewModel y vuelve.
    var position by remember { mutableFloatStateOf(radiusMeters.toFloat()) }
    val label = stringResource(R.string.geofence_radius_label)
    val range = GeofenceLimits.MIN_RADIUS_METERS..GeofenceLimits.MAX_RADIUS_METERS

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FieldLabel(text = label, modifier = Modifier.weight(1f))
            Text(
                text = stringResource(R.string.geofence_radius_value, position.roundToInt()),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = position,
            onValueChange = { raw ->
                // Se ajusta al paso a mano: los pasos propios del Slider
                // dibujan una marca por valor y el diseño tiene la pista lisa.
                val step = GeofenceLimits.RADIUS_STEP_METERS
                val snapped = ((raw / step).roundToInt() * step).coerceIn(range.first, range.last)
                position = snapped.toFloat()
                onRadiusChange(snapped)
            },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            modifier = Modifier.semantics { contentDescription = label },
            thumb = {
                Box(
                    modifier = Modifier
                        .size(SliderThumbSize)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                )
            },
            track = { sliderState ->
                val span = sliderState.valueRange.endInclusive - sliderState.valueRange.start
                val fraction = ((sliderState.value - sliderState.valueRange.start) / span).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(SliderTrackHeight)
                        .background(MaterialTheme.colorScheme.outline, CircleShape)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                    )
                }
            }
        )
    }
}

@Composable
private fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
    )
}

@Composable
private fun SavedZones(
    zones: List<SavedZone>,
    onDeleteClick: (SavedZone) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.geofence_saved_zones_header),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (zones.isEmpty()) {
            Text(
                text = stringResource(R.string.geofence_saved_zones_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            zones.forEach { zone ->
                SavedZoneCard(zone = zone, onDeleteClick = { onDeleteClick(zone) })
            }
        }
    }
}

@Composable
private fun SavedZoneCard(
    zone: SavedZone,
    onDeleteClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = ZoneCardShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.LocationOn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = zone.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.geofence_zone_range, zone.radiusMeters.roundToInt()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Sin posición del estuche no se sabe si está dentro o fuera: no se pone chip.
            if (zone.isInside != null) {
                Spacer(modifier = Modifier.width(12.dp))
                PresenceChip(isInside = zone.isInside)
            }
            IconButton(onClick = onDeleteClick) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = stringResource(R.string.geofence_delete_zone, zone.name),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Borrar una zona deja al estuche sin vigilancia en ese lugar y no se puede
 * deshacer: se pregunta antes, diciendo qué zona es y qué se pierde.
 */
@Composable
private fun DeleteZoneDialog(
    zoneName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.geofence_delete_title)) },
        text = { Text(text = stringResource(R.string.geofence_delete_message, zoneName)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(
                    text = stringResource(R.string.geofence_delete_confirm),
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel))
            }
        }
    )
}

/** "Dentro" / "Fuera": lo dice el texto; el color solo lo acompaña. */
@Composable
private fun PresenceChip(isInside: Boolean) {
    val statusColors = MediCaseTheme.statusColors
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (isInside) statusColors.safeContainer else MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Text(
            text = stringResource(
                if (isInside) R.string.geofence_zone_inside else R.string.geofence_zone_outside
            ),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (isInside) statusColors.safe else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun SaveButton(
    enabled: Boolean,
    isSaving: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = FieldShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
    ) {
        if (isSaving) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = stringResource(R.string.geofence_save),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

// ---------------------------------------------------------------------
//  Previews (los valores de ejemplo solo existen en el IDE)
// ---------------------------------------------------------------------
@Composable
private fun GeofenceConfigPreview(uiState: GeofenceConfigUiState, zoneName: String = "") {
    MediCaseTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            GeofenceConfigScreen(
                uiState = uiState,
                zoneName = zoneName,
                snackbarHostState = remember { SnackbarHostState() },
                onBack = {},
                onNameChange = {},
                onRadiusChange = {},
                onCenterChanged = {},
                onSave = {},
                onDeleteZone = {},
                onRetry = {}
            )
        }
    }
}

@Preview(showBackground = true, name = "Geocerca · con zonas")
@Composable
private fun GeofenceConfigSuccessPreview() {
    GeofenceConfigPreview(
        uiState = GeofenceConfigUiState.Success(
            radiusMeters = 300,
            hasCenter = true,
            canSave = true,
            isSaving = false,
            mapStart = GeoPoint(40.4167, -3.7037),
            casePosition = GeoPoint(40.4167, -3.7037),
            savedZones = listOf(
                SavedZone(1, "Casa (Principal)", 40.4167, -3.7037, 150.0, isInside = true),
                SavedZone(2, "Clínica Médica Norte", 40.4300, -3.7000, 500.0, isInside = false)
            ),
            message = null
        ),
        zoneName = "Trabajo - Hospital Central"
    )
}

@Preview(showBackground = true, name = "Geocerca · sin zonas ni lugar")
@Composable
private fun GeofenceConfigEmptyPreview() {
    GeofenceConfigPreview(
        uiState = GeofenceConfigUiState.Success(
            radiusMeters = 300,
            hasCenter = false,
            canSave = false,
            isSaving = false,
            mapStart = null,
            casePosition = null,
            savedZones = emptyList(),
            message = null
        )
    )
}

@Preview(showBackground = true, name = "Geocerca · error")
@Composable
private fun GeofenceConfigErrorPreview() {
    GeofenceConfigPreview(uiState = GeofenceConfigUiState.Error)
}
