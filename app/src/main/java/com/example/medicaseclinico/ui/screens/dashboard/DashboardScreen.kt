package com.example.medicaseclinico.ui.screens.dashboard

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.medicaseclinico.R
import com.example.medicaseclinico.data.ble.BlePermissions
import com.example.medicaseclinico.data.notification.NotificationPermission
import com.example.medicaseclinico.domain.model.DeviceIssue
import com.example.medicaseclinico.ui.components.relativeTimeLabel
import com.example.medicaseclinico.ui.screens.dashboard.components.BatteryCard
import com.example.medicaseclinico.ui.screens.dashboard.components.DashboardEmptyContent
import com.example.medicaseclinico.ui.screens.dashboard.components.DashboardErrorContent
import com.example.medicaseclinico.ui.screens.dashboard.components.DashboardLoadingContent
import com.example.medicaseclinico.ui.screens.dashboard.components.DashboardTopBar
import com.example.medicaseclinico.ui.screens.dashboard.components.DeviceIssueBanner
import com.example.medicaseclinico.ui.screens.dashboard.components.TemperatureCard

@Composable
fun DashboardRoute(
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = viewModel(factory = DashboardViewModel.Factory)
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val deviceIssue by viewModel.deviceIssue.collectAsStateWithLifecycle()
    val userName by viewModel.userName.collectAsStateWithLifecycle()

    var permissionsPermanentlyDenied by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.isNotEmpty() && results.values.all { it }) {
            permissionsPermanentlyDenied = false
            viewModel.onBlePrerequisiteResolved()
        } else {
            val activity = context.findActivity()
            permissionsPermanentlyDenied = activity != null &&
                    BlePermissions.requiredPermissions.any { permission ->
                        !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
                    }
        }
    }

    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.onBlePrerequisiteResolved()
    }

    LifecycleResumeEffect(Unit) {
        viewModel.onScreenResumed()
        onPauseOrDispose { }
    }

    // ------------------------------------------------------------------
    //  Permiso de notificaciones (Android 13+)
    // ------------------------------------------------------------------
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    var notificationPermissionAsked by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(deviceIssue) {
        val blePermissionsPending = deviceIssue == DeviceIssue.PERMISSIONS_MISSING
        if (!notificationPermissionAsked &&
            !blePermissionsPending &&
            NotificationPermission.shouldRequest(context)
        ) {
            notificationPermissionAsked = true
            notificationPermissionLauncher.launch(NotificationPermission.PERMISSION)
        }
    }

    DashboardScreen(
        uiState = uiState,
        deviceIssue = deviceIssue,
        userName = userName,
        permissionsPermanentlyDenied = permissionsPermanentlyDenied,
        onRetry = viewModel::retry,
        onIssueAction = { issue ->
            when (issue) {
                DeviceIssue.PERMISSIONS_MISSING ->
                    if (permissionsPermanentlyDenied) {
                        context.openAppSettings()
                    } else {
                        permissionLauncher.launch(BlePermissions.requiredPermissions)
                    }
                DeviceIssue.BLUETOOTH_DISABLED ->
                    try {
                        enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                    } catch (e: Exception) {
                        context.openSettings(Settings.ACTION_BLUETOOTH_SETTINGS)
                    }
                DeviceIssue.DEVICE_NOT_FOUND,
                DeviceIssue.CONNECTION_LOST -> viewModel.retry()
                DeviceIssue.BLE_NOT_SUPPORTED -> Unit
            }
        },
        modifier = modifier
    )
}

@Composable
fun DashboardScreen(
    uiState: DashboardUiState,
    deviceIssue: DeviceIssue?,
    permissionsPermanentlyDenied: Boolean,
    onRetry: () -> Unit,
    onIssueAction: (DeviceIssue) -> Unit,
    modifier: Modifier = Modifier,
    userName: String? = null
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            DashboardTopBar(connectionStatus = uiState.connectionStatus, userName = userName)
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedVisibility(
                visible = deviceIssue != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                deviceIssue?.let { issue ->
                    DeviceIssueBanner(
                        issue = issue,
                        permissionsPermanentlyDenied = permissionsPermanentlyDenied,
                        onAction = onIssueAction,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            AnimatedContent(
                targetState = uiState,
                contentKey = { it::class },
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "dashboardState",
                modifier = Modifier.fillMaxSize()
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
}

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
        // El tiempo va en medio de la frase: "…vía Bluetooth hace 5 min".
        val relativeTime = relativeTimeLabel(state.lastReadingElapsed)
            .replaceFirstChar { it.lowercase() }
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

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun Context.openAppSettings() {
    try {
        startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null)
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (e: Exception) {
        openSettings(Settings.ACTION_SETTINGS)
    }
}

private fun Context.openSettings(action: String) {
    try {
        startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        // Ignorado si la pantalla de ajustes no existe
    }
}