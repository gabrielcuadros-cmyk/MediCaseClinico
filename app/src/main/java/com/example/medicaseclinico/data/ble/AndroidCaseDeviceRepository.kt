package com.example.medicaseclinico.data.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import com.example.medicaseclinico.domain.model.CaseTelemetry
import com.example.medicaseclinico.domain.model.ConnectionStatus
import com.example.medicaseclinico.domain.model.DeviceIssue
import com.example.medicaseclinico.domain.repository.CaseDeviceRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume

/**
 * Cliente BLE del estuche MediCase: escaneo → conexión → suscripción a
 * temperatura y batería → reconexión automática con espera progresiva.
 */
@SuppressLint("MissingPermission")
class AndroidCaseDeviceRepository(
    context: Context,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis
) : CaseDeviceRepository {

    private val appContext = context.applicationContext
    private val bluetoothManager: BluetoothManager? =
        appContext.getSystemService(BluetoothManager::class.java)

    // ------------------------------------------------------------------
    //  Estado público (contrato CaseDeviceRepository)
    // ------------------------------------------------------------------
    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    override val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _deviceIssue = MutableStateFlow<DeviceIssue?>(null)
    override val deviceIssue: StateFlow<DeviceIssue?> = _deviceIssue.asStateFlow()

    private val _telemetry = MutableSharedFlow<CaseTelemetry>(
        extraBufferCapacity = TELEMETRY_BUFFER_SIZE,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val telemetry: Flow<CaseTelemetry> = _telemetry.asSharedFlow()

    // ------------------------------------------------------------------
    //  Estado interno de la conexión
    // ------------------------------------------------------------------
    private var monitoringJob: Job? = null
    @Volatile private var activeGatt: BluetoothGatt? = null
    @Volatile private var connectionResult: CompletableDeferred<Boolean>? = null
    @Volatile private var pendingGattOperation: CompletableDeferred<Boolean>? = null

    private val gattOperationMutex = Mutex()
    private val disconnectionSignal = Channel<Unit>(Channel.CONFLATED)

    // ------------------------------------------------------------------
    //  API pública
    // ------------------------------------------------------------------
    override fun startMonitoring() {
        if (monitoringJob?.isActive == true) return
        monitoringJob = scope.launch { runMonitoringLoop() }
    }

    override fun stopMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = null
        closeGatt()
        _connectionStatus.value = ConnectionStatus.DISCONNECTED
    }

    // ------------------------------------------------------------------
    //  Ciclo de monitoreo con reconexión
    // ------------------------------------------------------------------
    private suspend fun runMonitoringLoop() {
        var failedAttempts = 0
        var everConnected = false
        try {
            while (currentCoroutineContext().isActive) {
                checkPrerequisites()?.let { issue ->
                    publishFailure(issue)
                    return
                }
                _deviceIssue.value = null
                _connectionStatus.value = ConnectionStatus.CONNECTING
                val device = scanForCase()
                val linked = device != null && connectAndSubscribe(device)
                if (linked) {
                    everConnected = true
                    failedAttempts = 0
                    _connectionStatus.value = ConnectionStatus.CONNECTED
                    disconnectionSignal.receive()
                }
                closeGatt()
                _connectionStatus.value = ConnectionStatus.DISCONNECTED
                failedAttempts++
                if (failedAttempts > MAX_RECONNECT_ATTEMPTS) {
                    publishFailure(
                        if (everConnected) DeviceIssue.CONNECTION_LOST
                        else DeviceIssue.DEVICE_NOT_FOUND
                    )
                    return
                }
                delay(BASE_BACKOFF_MS shl (failedAttempts - 1))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SecurityException) {
            publishFailure(DeviceIssue.PERMISSIONS_MISSING)
        } catch (e: Exception) {
            publishFailure(DeviceIssue.CONNECTION_LOST)
        } finally {
            closeGatt()
        }
    }

    private fun checkPrerequisites(): DeviceIssue? = when {
        !BlePermissions.isBleSupported(appContext) -> DeviceIssue.BLE_NOT_SUPPORTED
        !BlePermissions.hasAllPermissions(appContext) -> DeviceIssue.PERMISSIONS_MISSING
        !BlePermissions.isBluetoothEnabled(appContext) -> DeviceIssue.BLUETOOTH_DISABLED
        else -> null
    }

    private fun publishFailure(issue: DeviceIssue) {
        _connectionStatus.value = ConnectionStatus.DISCONNECTED
        _deviceIssue.value = issue
    }

    // ------------------------------------------------------------------
    //  1) Escaneo filtrado por Health Thermometer Service
    // ------------------------------------------------------------------
    private suspend fun scanForCase(): BluetoothDevice? {
        val scanner = bluetoothManager?.adapter?.bluetoothLeScanner ?: return null
        val filters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(GattSpec.THERMOMETER_SERVICE))
                .build()
        )
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        return withTimeoutOrNull(SCAN_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                val callback = object : ScanCallback() {
                    override fun onScanResult(callbackType: Int, result: ScanResult) {
                        safeStopScan(this)
                        if (continuation.isActive) continuation.resume(result.device)
                    }

                    override fun onScanFailed(errorCode: Int) {
                        if (continuation.isActive) continuation.resume(null)
                    }
                }
                try {
                    scanner.startScan(filters, settings, callback)
                } catch (e: SecurityException) {
                    if (continuation.isActive) continuation.resume(null)
                }
                continuation.invokeOnCancellation { safeStopScan(callback) }
            }
        }
    }

    private fun safeStopScan(callback: ScanCallback) {
        try {
            bluetoothManager?.adapter?.bluetoothLeScanner?.stopScan(callback)
        } catch (e: Exception) {
            // Ignorado si el Bluetooth está apagado
        }
    }

    // ------------------------------------------------------------------
    //  2) Conexión GATT y descubrimiento de servicios
    // ------------------------------------------------------------------
    private suspend fun connectAndSubscribe(device: BluetoothDevice): Boolean {
        disconnectionSignal.tryReceive()
        val result = CompletableDeferred<Boolean>()
        connectionResult = result
        val gatt = device.connectGatt(
            appContext,
            false,
            gattCallback,
            BluetoothDevice.TRANSPORT_LE
        ) ?: return false
        activeGatt = gatt
        val servicesReady = withTimeoutOrNull(CONNECT_TIMEOUT_MS) { result.await() } ?: false
        connectionResult = null
        if (!servicesReady) return false
        return subscribeToMeasurements(gatt)
    }

    // ------------------------------------------------------------------
    //  3) Suscripción a temperatura y batería
    // ------------------------------------------------------------------
    private suspend fun subscribeToMeasurements(gatt: BluetoothGatt): Boolean {
        val temperature = gatt.findCharacteristic(GattSpec.TEMPERATURE_MEASUREMENT)
            ?: return false
        if (!enableUpdates(gatt, temperature)) return false

        gatt.findCharacteristic(GattSpec.BATTERY_LEVEL)?.let { battery ->
            if (battery.hasProperty(BluetoothGattCharacteristic.PROPERTY_READ)) {
                runGattOperation { gatt.readCharacteristic(battery) }
            }
            if (battery.hasProperty(BluetoothGattCharacteristic.PROPERTY_NOTIFY)) {
                enableUpdates(gatt, battery)
            }
        }
        return true
    }

    private suspend fun enableUpdates(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic
    ): Boolean {
        val descriptor = characteristic.getDescriptor(GattSpec.CLIENT_CHARACTERISTIC_CONFIG)
            ?: return false
        val enableValue = when {
            characteristic.hasProperty(BluetoothGattCharacteristic.PROPERTY_INDICATE) ->
                BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            characteristic.hasProperty(BluetoothGattCharacteristic.PROPERTY_NOTIFY) ->
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            else -> return false
        }
        if (!gatt.setCharacteristicNotification(characteristic, true)) return false
        return runGattOperation { writeDescriptorCompat(gatt, descriptor, enableValue) }
    }

    private suspend fun runGattOperation(start: () -> Boolean): Boolean =
        gattOperationMutex.withLock {
            val completion = CompletableDeferred<Boolean>()
            pendingGattOperation = completion
            val started = try {
                start()
            } catch (e: SecurityException) {
                false
            }
            val success = if (started) {
                withTimeoutOrNull(GATT_OPERATION_TIMEOUT_MS) { completion.await() } ?: false
            } else {
                false
            }
            pendingGattOperation = null
            success
        }

    @Suppress("DEPRECATION")
    private fun writeDescriptorCompat(
        gatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        value: ByteArray
    ): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(descriptor, value) == BluetoothStatusCodes.SUCCESS
        } else {
            descriptor.value = value
            gatt.writeDescriptor(descriptor)
        }

    // ------------------------------------------------------------------
    //  4) Callbacks del stack Bluetooth
    // ------------------------------------------------------------------
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val connected = status == BluetoothGatt.GATT_SUCCESS &&
                    newState == BluetoothProfile.STATE_CONNECTED
            if (connected) {
                val discovering = try {
                    gatt.discoverServices()
                } catch (e: SecurityException) {
                    false
                }
                if (!discovering) connectionResult?.complete(false)
            } else {
                connectionResult?.complete(false)
                pendingGattOperation?.complete(false)
                disconnectionSignal.trySend(Unit)
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            connectionResult?.complete(status == BluetoothGatt.GATT_SUCCESS)
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            pendingGattOperation?.complete(status == BluetoothGatt.GATT_SUCCESS)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleIncomingValue(characteristic, value)
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) handleIncomingValue(characteristic, value)
            pendingGattOperation?.complete(status == BluetoothGatt.GATT_SUCCESS)
        }

        @Deprecated("Usado solo en API < 33")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                characteristic.value?.let { handleIncomingValue(characteristic, it) }
            }
        }

        @Deprecated("Usado solo en API < 33")
        @Suppress("DEPRECATION")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    characteristic.value?.let { handleIncomingValue(characteristic, it) }
                }
                pendingGattOperation?.complete(status == BluetoothGatt.GATT_SUCCESS)
            }
        }
    }

    private fun handleIncomingValue(characteristic: BluetoothGattCharacteristic, value: ByteArray) {
        val now = clock()
        when (characteristic.uuid) {
            GattSpec.TEMPERATURE_MEASUREMENT ->
                GattParser.parseTemperatureCelsius(value)?.let { celsius ->
                    _telemetry.tryEmit(CaseTelemetry.Temperature(celsius, now))
                }
            GattSpec.BATTERY_LEVEL ->
                GattParser.parseBatteryPercent(value)?.let { percent ->
                    _telemetry.tryEmit(CaseTelemetry.Battery(percent, now))
                }
        }
    }

    private fun BluetoothGatt.findCharacteristic(uuid: java.util.UUID): BluetoothGattCharacteristic? =
        services.firstNotNullOfOrNull { it.getCharacteristic(uuid) }

    private fun BluetoothGattCharacteristic.hasProperty(property: Int): Boolean =
        properties and property != 0

    private fun closeGatt() {
        val gatt = activeGatt ?: return
        activeGatt = null
        try {
            gatt.disconnect()
            gatt.close()
        } catch (e: Exception) {
            // Manejado
        }
    }

    private companion object {
        const val SCAN_TIMEOUT_MS = 15_000L
        const val CONNECT_TIMEOUT_MS = 15_000L
        const val GATT_OPERATION_TIMEOUT_MS = 5_000L
        const val MAX_RECONNECT_ATTEMPTS = 5
        const val BASE_BACKOFF_MS = 2_000L
        const val TELEMETRY_BUFFER_SIZE = 32
    }
}