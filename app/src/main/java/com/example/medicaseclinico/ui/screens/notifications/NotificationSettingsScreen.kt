package com.example.medicaseclinico.ui.screens.notifications

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.medicaseclinico.R
import com.example.medicaseclinico.data.notification.AlertChannel
import com.example.medicaseclinico.domain.model.AlertType
import com.example.medicaseclinico.domain.model.NotificationPreferences
import com.example.medicaseclinico.ui.components.MediCaseLoadingState
import com.example.medicaseclinico.ui.components.MediCaseMessageState
import com.example.medicaseclinico.ui.components.defaultLoadingMessage
import java.util.Locale

@Composable
fun NotificationSettingsRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NotificationSettingsViewModel = viewModel(factory = NotificationSettingsViewModel.Factory)
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val saveFailed by viewModel.saveFailed.collectAsStateWithLifecycle()

    var systemNotificationsBlocked by remember { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) {
        systemNotificationsBlocked = !NotificationManagerCompat.from(context).areNotificationsEnabled()
        onPauseOrDispose { }
    }

    NotificationSettingsScreen(
        uiState = uiState,
        systemNotificationsBlocked = systemNotificationsBlocked,
        saveFailed = saveFailed,
        onSaveErrorShown = viewModel::onSaveErrorShown,
        onBack = onBack,
        onAlertToggled = viewModel::setAlertEnabled,
        onSilencedToggled = viewModel::setTemporarilySilenced,
        onQuietHoursToggled = viewModel::setQuietHoursEnabled,
        onQuietHoursStartChanged = viewModel::setQuietHoursStart,
        onQuietHoursEndChanged = viewModel::setQuietHoursEnd,
        onOpenChannelSettings = { context.openChannelSettings(AlertChannel.TEMPERATURE.id) },
        onOpenAppNotificationSettings = { context.openAppNotificationSettings() },
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(
    uiState: NotificationSettingsUiState,
    systemNotificationsBlocked: Boolean,
    saveFailed: Boolean,
    onSaveErrorShown: () -> Unit,
    onBack: () -> Unit,
    onAlertToggled: (AlertType, Boolean) -> Unit,
    onSilencedToggled: (Boolean) -> Unit,
    onQuietHoursToggled: (Boolean) -> Unit,
    onQuietHoursStartChanged: (Int) -> Unit,
    onQuietHoursEndChanged: (Int) -> Unit,
    onOpenChannelSettings: () -> Unit,
    onOpenAppNotificationSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val saveErrorMessage = stringResource(R.string.settings_save_error)

    LaunchedEffect(saveFailed) {
        if (saveFailed) {
            snackbarHostState.showSnackbar(saveErrorMessage)
            onSaveErrorShown()
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.notif_settings_title),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)

        when (uiState) {
            NotificationSettingsUiState.Loading ->
                MediCaseLoadingState(message = defaultLoadingMessage(), modifier = contentModifier)
            NotificationSettingsUiState.Error ->
                MediCaseMessageState(
                    icon = Icons.Outlined.NotificationsOff,
                    title = stringResource(R.string.notif_settings_error_title),
                    message = stringResource(R.string.notif_settings_error_message),
                    accentColor = MaterialTheme.colorScheme.error,
                    accentContainerColor = MaterialTheme.colorScheme.errorContainer,
                    modifier = contentModifier
                )
            is NotificationSettingsUiState.Success ->
                SettingsContent(
                    prefs = uiState.preferences,
                    systemNotificationsBlocked = systemNotificationsBlocked,
                    onAlertToggled = onAlertToggled,
                    onSilencedToggled = onSilencedToggled,
                    onQuietHoursToggled = onQuietHoursToggled,
                    onQuietHoursStartChanged = onQuietHoursStartChanged,
                    onQuietHoursEndChanged = onQuietHoursEndChanged,
                    onOpenChannelSettings = onOpenChannelSettings,
                    onOpenAppNotificationSettings = onOpenAppNotificationSettings,
                    modifier = contentModifier
                )
        }
    }
}

@Composable
private fun SettingsContent(
    prefs: NotificationPreferences,
    systemNotificationsBlocked: Boolean,
    onAlertToggled: (AlertType, Boolean) -> Unit,
    onSilencedToggled: (Boolean) -> Unit,
    onQuietHoursToggled: (Boolean) -> Unit,
    onQuietHoursStartChanged: (Int) -> Unit,
    onQuietHoursEndChanged: (Int) -> Unit,
    onOpenChannelSettings: () -> Unit,
    onOpenAppNotificationSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.notif_settings_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (systemNotificationsBlocked) {
            BlockedNotificationsBanner(onEnable = onOpenAppNotificationSettings)
        }

        // --- ALERTAS DEL ESTUCHE ---
        SectionHeader(R.string.notif_section_case_alerts)
        AlertToggles.forEach { toggle ->
            SettingsSwitchCard(
                titleRes = toggle.titleRes,
                descriptionRes = toggle.descriptionRes,
                checked = prefs.isTypeEnabled(toggle.type),
                onCheckedChange = { onAlertToggled(toggle.type, it) }
            )
        }

        // --- SONIDO Y ALERTAS ---
        SectionHeader(R.string.notif_section_sound)
        SettingsNavigationCard(
            titleRes = R.string.notif_tone_title,
            descriptionRes = R.string.notif_tone_description,
            onClick = onOpenChannelSettings
        )
        SettingsNavigationCard(
            titleRes = R.string.notif_vibration_title,
            descriptionRes = R.string.notif_vibration_description,
            onClick = onOpenChannelSettings
        )

        // --- MODO NO MOLESTAR ---
        SectionHeader(R.string.notif_section_dnd)
        SettingsSwitchCard(
            titleRes = R.string.notif_silence_title,
            descriptionRes = R.string.notif_silence_description,
            checked = prefs.temporarilySilenced,
            onCheckedChange = onSilencedToggled
        )
        QuietHoursCard(
            prefs = prefs,
            onToggled = onQuietHoursToggled,
            onStartChanged = onQuietHoursStartChanged,
            onEndChanged = onQuietHoursEndChanged
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

private data class AlertToggle(
    val type: AlertType,
    @get:StringRes val titleRes: Int,
    @get:StringRes val descriptionRes: Int
)

private val AlertToggles = listOf(
    AlertToggle(AlertType.TEMPERATURE, R.string.channel_temperature_name, R.string.channel_temperature_description),
    AlertToggle(AlertType.BATTERY, R.string.channel_battery_name, R.string.channel_battery_description),
    AlertToggle(AlertType.CONNECTION, R.string.channel_connection_name, R.string.channel_connection_description),
    AlertToggle(AlertType.GEOFENCE, R.string.channel_geofence_name, R.string.channel_geofence_description),
    AlertToggle(AlertType.SECURITY, R.string.channel_security_name, R.string.channel_security_description)
)

@Composable
private fun SectionHeader(@StringRes textRes: Int) {
    Text(
        text = stringResource(textRes),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) { content() }
}

@Composable
private fun SettingsSwitchCard(
    @StringRes titleRes: Int,
    @StringRes descriptionRes: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    SettingsCard {
        Row(
            modifier = Modifier
                .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SettingsTexts(titleRes, descriptionRes, Modifier.weight(1f))
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = checked,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary)
            )
        }
    }
}

@Composable
private fun SettingsNavigationCard(
    @StringRes titleRes: Int,
    @StringRes descriptionRes: Int,
    onClick: () -> Unit
) {
    SettingsCard {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SettingsTexts(titleRes, descriptionRes, Modifier.weight(1f))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingsTexts(
    @StringRes titleRes: Int,
    @StringRes descriptionRes: Int,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = stringResource(descriptionRes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun QuietHoursCard(
    prefs: NotificationPreferences,
    onToggled: (Boolean) -> Unit,
    onStartChanged: (Int) -> Unit,
    onEndChanged: (Int) -> Unit
) {
    var editingStart by rememberSaveable { mutableStateOf<Boolean?>(null) }

    SettingsCard {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(
                modifier = Modifier.toggleable(
                    value = prefs.quietHoursEnabled,
                    role = Role.Switch,
                    onValueChange = onToggled
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SettingsTexts(
                    titleRes = R.string.notif_quiet_hours_title,
                    descriptionRes = R.string.notif_quiet_hours_description,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Switch(
                    checked = prefs.quietHoursEnabled,
                    onCheckedChange = null,
                    colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary)
                )
            }
            Row(
                modifier = Modifier.padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { editingStart = true }) {
                    Text(formatMinuteOfDay(prefs.quietHoursStartMinute), fontWeight = FontWeight.Bold)
                }
                Text(text = "–", color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { editingStart = false }) {
                    Text(formatMinuteOfDay(prefs.quietHoursEndMinute), fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    editingStart?.let { isStart ->
        QuietHourPickerDialog(
            initialMinuteOfDay = if (isStart) prefs.quietHoursStartMinute else prefs.quietHoursEndMinute,
            titleRes = if (isStart) R.string.notif_quiet_start else R.string.notif_quiet_end,
            onConfirm = { minute ->
                if (isStart) onStartChanged(minute) else onEndChanged(minute)
                editingStart = null
            },
            onDismiss = { editingStart = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuietHourPickerDialog(
    initialMinuteOfDay: Int,
    @StringRes titleRes: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val state = rememberTimePickerState(
        initialHour = initialMinuteOfDay / 60,
        initialMinute = initialMinuteOfDay % 60,
        is24Hour = true
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(titleRes)) },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) {
                Text(stringResource(R.string.action_accept))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@Composable
private fun BlockedNotificationsBanner(onEnable: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = stringResource(R.string.notif_blocked_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                text = stringResource(R.string.notif_blocked_message),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Button(
                onClick = onEnable,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(top = 8.dp)
            ) {
                Text(stringResource(R.string.notif_blocked_action))
            }
        }
    }
}

private fun formatMinuteOfDay(minuteOfDay: Int): String =
    String.format(Locale.ROOT, "%02d:%02d", minuteOfDay / 60, minuteOfDay % 60)

private fun Context.openChannelSettings(channelId: String) {
    val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        .putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        startActivity(intent)
    } catch (e: Exception) {
        openAppNotificationSettings()
    }
}

private fun Context.openAppNotificationSettings() {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        startActivity(intent)
    } catch (e: Exception) {
        // Fallback
    }
}