package com.example.medicaseclinico.ui.screens.lossmode

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.medicaseclinico.R
import com.example.medicaseclinico.domain.model.RelativeTime
import com.example.medicaseclinico.ui.components.MediCaseBackButton
import com.example.medicaseclinico.ui.components.MediCaseLoadingState
import com.example.medicaseclinico.ui.components.MediCaseMessageState
import com.example.medicaseclinico.ui.components.defaultLoadingMessage
import com.example.medicaseclinico.ui.components.relativeTimeLabel
import com.example.medicaseclinico.ui.screens.map.components.coordinatesLabel
import com.example.medicaseclinico.ui.screens.map.mapsWebUrl
import com.example.medicaseclinico.ui.screens.security.components.ConfirmCasePinDialog
import com.example.medicaseclinico.ui.theme.MediCaseTheme
import kotlinx.coroutines.launch

private val ScreenPadding = 16.dp
private val CardShape = RoundedCornerShape(14.dp)
private val ButtonShape = RoundedCornerShape(12.dp)
private val ButtonHeight = 52.dp
private const val TEXT_MIME_TYPE = "text/plain"

/**
 * Teléfono de emergencias de la Policía Nacional del Perú. Configuración de
 * fábrica: la app solo abre el marcador con él, nunca llama por su cuenta.
 */
private const val POLICE_EMERGENCY_NUMBER = "105"

/**
 * Entrada de "Modo Pérdida": conecta el ViewModel con la pantalla sin estado y
 * lanza los `Intent` de compartir la ubicación y de abrir el marcador.
 */
@Composable
fun LossModeRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LossModeViewModel = viewModel(factory = LossModeViewModel.Factory)
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val message = when (val state = uiState) {
        is LossModeUiState.Success -> state.message
        is LossModeUiState.Inactive -> state.message
        else -> null
    }
    val messageText = message?.let { stringResource(it.textRes()) }
    LaunchedEffect(message) {
        if (messageText == null) return@LaunchedEffect
        // Se da por mostrado antes de enseñarlo: si no, al girar el teléfono
        // mientras está en pantalla volvería a salir.
        viewModel.onMessageShown()
        scope.launch { snackbarHostState.showSnackbar(messageText) }
    }

    val position = (uiState as? LossModeUiState.Success)?.position
    val shareText = position?.let {
        stringResource(
            R.string.loss_mode_share_text,
            coordinatesLabel(it.latitude, it.longitude),
            mapsWebUrl(it.latitude, it.longitude)
        )
    }
    val shareSubject = stringResource(R.string.loss_mode_share_subject)
    val shareChooserTitle = stringResource(R.string.loss_mode_share_location)
    val noAppMessage = stringResource(R.string.loss_mode_no_app)

    LossModeScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onActivate = viewModel::activate,
        onDeactivate = viewModel::deactivate,
        onRequestLock = viewModel::requestLock,
        onEnableLock = viewModel::enableLock,
        onLockOfferDismissed = viewModel::onLockOfferDismissed,
        onAlarmToggled = viewModel::setAlarm,
        onHighFrequencyGpsToggled = viewModel::setHighFrequencyGps,
        onShareLocation = {
            val opened = shareText != null &&
                context.shareText(text = shareText, subject = shareSubject, chooserTitle = shareChooserTitle)
            if (!opened) scope.launch { snackbarHostState.showSnackbar(noAppMessage) }
        },
        onDialPolice = {
            if (!context.openDialer(POLICE_EMERGENCY_NUMBER)) {
                scope.launch { snackbarHostState.showSnackbar(noAppMessage) }
            }
        },
        onRetry = viewModel::retry,
        modifier = modifier
    )
}

/** Abre el menú de compartir con un texto. Protegido: devuelve false si ninguna app puede recibirlo. */
private fun Context.shareText(text: String, subject: String, chooserTitle: String): Boolean {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = TEXT_MIME_TYPE
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    return try {
        startActivity(Intent.createChooser(sendIntent, chooserTitle))
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }
}

/**
 * Abre el marcador con el número ya escrito. `ACTION_DIAL` no llama: el
 * usuario decide si pulsa el botón de llamada, y no hace falta ningún permiso.
 */
private fun Context.openDialer(number: String): Boolean =
    try {
        startActivity(Intent(Intent.ACTION_DIAL, "tel:$number".toUri()))
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }

/**
 * Pantalla "Modo Pérdida": herramientas para recuperar el estuche.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LossModeScreen(
    uiState: LossModeUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onActivate: () -> Unit,
    onDeactivate: () -> Unit,
    onRequestLock: () -> Unit,
    onEnableLock: (currentPin: String) -> Unit,
    onLockOfferDismissed: () -> Unit,
    onAlarmToggled: (Boolean) -> Unit,
    onHighFrequencyGpsToggled: (Boolean) -> Unit,
    onShareLocation: () -> Unit,
    onDialPolice: () -> Unit,
    onRetry: () -> Unit,
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
                        text = stringResource(R.string.loss_mode_title),
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
                text = stringResource(R.string.loss_mode_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = ScreenPadding)
            )

            when (uiState) {
                LossModeUiState.Loading ->
                    MediCaseLoadingState(message = defaultLoadingMessage())

                is LossModeUiState.Inactive ->
                    InactiveContent(isBusy = uiState.isBusy, onActivate = onActivate)

                is LossModeUiState.Success ->
                    ActiveContent(
                        state = uiState,
                        onDeactivate = onDeactivate,
                        onRequestLock = onRequestLock,
                        onEnableLock = onEnableLock,
                        onLockOfferDismissed = onLockOfferDismissed,
                        onAlarmToggled = onAlarmToggled,
                        onHighFrequencyGpsToggled = onHighFrequencyGpsToggled,
                        onShareLocation = onShareLocation,
                        onDialPolice = onDialPolice
                    )

                LossModeUiState.Error ->
                    MediCaseMessageState(
                        icon = Icons.Outlined.ErrorOutline,
                        title = stringResource(R.string.loss_mode_error_title),
                        message = stringResource(R.string.loss_mode_error_message),
                        accentColor = MaterialTheme.colorScheme.error,
                        accentContainerColor = MaterialTheme.colorScheme.errorContainer,
                        actionLabel = stringResource(R.string.action_retry),
                        onAction = onRetry
                    )
            }
        }
    }
}

/** Modo desactivado: qué hace y el botón para activarlo. */
@Composable
private fun InactiveContent(
    isBusy: Boolean,
    onActivate: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = CardShape,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.loss_mode_inactive_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.loss_mode_inactive_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Button(
            onClick = onActivate,
            enabled = !isBusy,
            shape = ButtonShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError
            ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = ButtonHeight)
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.loss_mode_activate),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ActiveContent(
    state: LossModeUiState.Success,
    onDeactivate: () -> Unit,
    onRequestLock: () -> Unit,
    onEnableLock: (currentPin: String) -> Unit,
    onLockOfferDismissed: () -> Unit,
    onAlarmToggled: (Boolean) -> Unit,
    onHighFrequencyGpsToggled: (Boolean) -> Unit,
    onShareLocation: () -> Unit,
    onDialPolice: () -> Unit
) {
    // Estado solo visual: si el diálogo de confirmación está abierto.
    var confirmingDial by rememberSaveable { mutableStateOf(false) }

    if (state.offerLock) {
        // La contraseña vive solo dentro del diálogo: se envía y se olvida.
        ConfirmCasePinDialog(
            enabling = true,
            onConfirm = onEnableLock,
            onDismiss = onLockOfferDismissed
        )
    }
    if (confirmingDial) {
        DialPoliceDialog(
            onConfirm = {
                confirmingDial = false
                onDialPolice()
            },
            onDismiss = { confirmingDial = false }
        )
    }

    val controlsEnabled = !state.isBusy && state.controls == CaseControlsAvailability.READY

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        LossModeBanner(
            lock = state.lock,
            canEnableLock = controlsEnabled,
            onEnableLock = onRequestLock
        )

        Text(
            text = stringResource(
                if (state.position?.isLive == true) {
                    R.string.loss_mode_tracking_live
                } else {
                    R.string.loss_mode_tracking_stopped
                }
            ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
        PositionCard(position = state.position, gpsIntervalSeconds = state.gpsIntervalSeconds)

        SwitchCard(
            title = stringResource(R.string.loss_mode_alarm_title),
            description = stringResource(R.string.loss_mode_alarm_description),
            checked = state.alarmOn,
            enabled = controlsEnabled,
            onCheckedChange = onAlarmToggled
        )
        SwitchCard(
            title = stringResource(R.string.loss_mode_gps_title),
            description = stringResource(R.string.loss_mode_gps_description),
            checked = state.highFrequencyGps,
            enabled = controlsEnabled,
            onCheckedChange = onHighFrequencyGpsToggled
        )
        state.controls.unavailableRes()?.let { reason ->
            Text(
                text = stringResource(reason),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        OutlinedButton(
            onClick = onShareLocation,
            // Sin una posición real no hay nada que enviar.
            enabled = state.position != null,
            shape = ButtonShape,
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = ButtonHeight)
        ) {
            Text(
                text = stringResource(R.string.loss_mode_share_location),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
        OutlinedButton(
            onClick = { confirmingDial = true },
            shape = ButtonShape,
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.error
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = ButtonHeight)
        ) {
            Text(
                text = stringResource(R.string.loss_mode_report_theft),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
        Button(
            onClick = onDeactivate,
            enabled = !state.isBusy,
            shape = ButtonShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MediCaseTheme.statusColors.safe,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = ButtonHeight)
        ) {
            Text(
                text = stringResource(R.string.loss_mode_deactivate),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Banner rojo del diseño. El título dice que el modo está activo; el texto
 * dice lo que el estuche informa de su seguro, sin darlo por bloqueado.
 */
@Composable
private fun LossModeBanner(
    lock: LossLockStatus,
    canEnableLock: Boolean,
    onEnableLock: () -> Unit
) {
    val critical = MediCaseTheme.statusColors.critical

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        shape = CardShape,
        color = MediCaseTheme.statusColors.criticalContainer,
        border = BorderStroke(1.dp, critical)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(shape = CircleShape, color = critical, modifier = Modifier.size(48.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onError,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.loss_mode_banner_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = critical,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(lock.descriptionRes()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            if (lock == LossLockStatus.UNLOCKED) {
                TextButton(
                    onClick = onEnableLock,
                    enabled = canEnableLock,
                    colors = ButtonDefaults.textButtonColors(contentColor = critical)
                ) {
                    Text(
                        text = stringResource(R.string.loss_mode_enable_lock),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun PositionCard(
    position: LossPosition?,
    gpsIntervalSeconds: Int
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.LocationOn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = stringResource(
                        if (position?.isLive == false) {
                            R.string.loss_mode_position_last_known
                        } else {
                            R.string.loss_mode_position_live
                        }
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    // Sin posición no se inventa ninguna: se dice que no hay señal.
                    text = if (position != null) {
                        coordinatesLabel(position.latitude, position.longitude)
                    } else {
                        stringResource(R.string.map_empty_no_signal_title)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (position != null) {
                    Text(
                        text = if (position.isLive) {
                            stringResource(R.string.loss_mode_position_updating, gpsIntervalSeconds)
                        } else {
                            stringResource(
                                R.string.loss_mode_position_received,
                                relativeTimeLabel(position.elapsed)
                            )
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (position.isLive) {
                            MediCaseTheme.statusColors.safe
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
    }
}

/** Toda la tarjeta es el interruptor, para que se lea y se toque como una sola cosa. */
@Composable
private fun SwitchCard(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier
                .toggleable(
                    value = checked,
                    enabled = enabled,
                    role = Role.Switch,
                    onValueChange = onCheckedChange
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            // onCheckedChange = null: el toque lo gestiona la fila entera.
            Switch(checked = checked, onCheckedChange = null, enabled = enabled)
        }
    }
}

/**
 * Antes de abrir el marcador se pregunta: es un número de emergencias y no
 * debe marcarse por un toque accidental.
 */
@Composable
private fun DialPoliceDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.loss_mode_dial_title)) },
        text = {
            Text(text = stringResource(R.string.loss_mode_dial_message, POLICE_EMERGENCY_NUMBER))
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text(
                    text = stringResource(R.string.loss_mode_dial_confirm),
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.action_cancel)) }
        }
    )
}

@StringRes
private fun LossLockStatus.descriptionRes(): Int = when (this) {
    LossLockStatus.LOCKED -> R.string.loss_mode_lock_locked
    LossLockStatus.UNLOCKED -> R.string.loss_mode_lock_unlocked
    LossLockStatus.UNKNOWN -> R.string.loss_mode_lock_unknown
    LossLockStatus.NOT_SUPPORTED -> R.string.loss_mode_lock_not_supported
}

@StringRes
private fun CaseControlsAvailability.unavailableRes(): Int? = when (this) {
    CaseControlsAvailability.READY -> null
    CaseControlsAvailability.DISCONNECTED -> R.string.loss_mode_controls_disconnected
    CaseControlsAvailability.NOT_SUPPORTED -> R.string.security_case_not_supported
}

@StringRes
private fun LossModeMessage.textRes(): Int = when (this) {
    LossModeMessage.LOCK_ENABLED -> R.string.security_msg_lock_enabled
    LossModeMessage.NOT_SAVED -> R.string.settings_save_error
    LossModeMessage.CASE_NOT_CONNECTED -> R.string.loss_mode_controls_disconnected
    LossModeMessage.CASE_NOT_SUPPORTED -> R.string.security_case_not_supported
    LossModeMessage.PAIRING_REQUIRED -> R.string.security_msg_pairing_required
    LossModeMessage.WRONG_PIN -> R.string.security_msg_wrong_pin
    LossModeMessage.CASE_LOCKED_OUT -> R.string.security_msg_locked_out
    LossModeMessage.INVALID_PIN -> R.string.security_msg_invalid_pin
    LossModeMessage.CASE_FAILED -> R.string.security_msg_case_failed
    LossModeMessage.DEACTIVATED_CASE_NOT_UPDATED -> R.string.loss_mode_deactivated_case_not_updated
}

// ---------------------------------------------------------------------
//  Previews (los valores de ejemplo solo existen en el IDE)
// ---------------------------------------------------------------------
@Composable
private fun LossModeScreenPreview(uiState: LossModeUiState) {
    MediCaseTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            LossModeScreen(
                uiState = uiState,
                snackbarHostState = remember { SnackbarHostState() },
                onBack = {},
                onActivate = {},
                onDeactivate = {},
                onRequestLock = {},
                onEnableLock = {},
                onLockOfferDismissed = {},
                onAlarmToggled = {},
                onHighFrequencyGpsToggled = {},
                onShareLocation = {},
                onDialPolice = {},
                onRetry = {}
            )
        }
    }
}

@Preview(showBackground = true, heightDp = 900, name = "Modo Pérdida · activo")
@Composable
private fun LossModeActivePreview() {
    LossModeScreenPreview(
        LossModeUiState.Success(
            lock = LossLockStatus.LOCKED,
            position = LossPosition(
                latitude = 40.4167,
                longitude = -3.7037,
                isLive = true,
                elapsed = RelativeTime.JustNow
            ),
            controls = CaseControlsAvailability.READY,
            alarmOn = true,
            highFrequencyGps = false,
            gpsIntervalSeconds = 5,
            offerLock = false,
            isBusy = false,
            message = null
        )
    )
}

@Preview(showBackground = true, heightDp = 900, name = "Modo Pérdida · sin enlace")
@Composable
private fun LossModeDisconnectedPreview() {
    LossModeScreenPreview(
        LossModeUiState.Success(
            lock = LossLockStatus.UNKNOWN,
            position = null,
            controls = CaseControlsAvailability.DISCONNECTED,
            alarmOn = false,
            highFrequencyGps = false,
            gpsIntervalSeconds = 5,
            offerLock = false,
            isBusy = false,
            message = null
        )
    )
}

@Preview(showBackground = true, name = "Modo Pérdida · desactivado")
@Composable
private fun LossModeInactivePreview() {
    LossModeScreenPreview(LossModeUiState.Inactive(isBusy = false, message = null))
}
