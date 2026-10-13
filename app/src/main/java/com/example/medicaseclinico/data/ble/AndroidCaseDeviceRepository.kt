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
import com.example.medicaseclinico.domain.model.AccessEvent
import com.example.medicaseclinico.domain.model.CaseCommandError
import com.example.medicaseclinico.domain.model.CaseCommandException
import com.example.medicaseclinico.domain.model.CaseControlState
import com.example.medicaseclinico.domain.model.CaseLocationState
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
import kotlinx.coroutines.flow.update
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
 * temperatura, batería, ubicación y control → reconexión automática con
 * espera progresiva.
 *
 * El formato de cada trama y lo que debe hacer el firmware está en
 * `hardware/ble-protocol.md`.
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

    private val _caseLocation =
        MutableStateFlow<CaseLocationState>(CaseLocationState.Unavailable)
    override val caseLocation: StateFlow<CaseLocationState> = _caseLocation.asStateFlow()

    private val _controlState =
        MutableStateFlow<CaseControlState>(CaseControlState.Unavailable)
    override val controlState: StateFlow<CaseControlState> = _controlState.asStateFlow()

    private val _accessEvents = MutableSharedFlow<AccessEvent>(
        extraBufferCapacity = ACCESS_EVENT_BUFFER_SIZE,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val accessEvents: Flow<AccessEvent> = _accessEvents.asSharedFlow()

    // ------------------------------------------------------------------
    //  Estado interno de la conexión
    // ------------------------------------------------------------------
    private var monitoringJob: Job? = null
    @Volatile private var activeGatt: BluetoothGatt? = null
    @Volatile private var connectionResult: CompletableDeferred<Boolean>? = null
    @Volatile private var pendingGattOperation: CompletableDeferred<Boolean>? = null

    // Las escrituras de control necesitan el código que devuelve el estuche
    // (PIN incorrecto, falta emparejar…), no solo si salió bien.
    @Volatile private var pendingWriteStatus: CompletableDeferred<Int>? = null

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
        _caseLocation.value = CaseLocationState.Unavailable
        _controlState.value = CaseControlState.Unavailable
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
                _caseLocation.value = CaseLocationState.Unavailable
                _controlState.value = CaseControlState.Unavailable
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
        _caseLocation.value = CaseLocationState.Unavailable
        _controlState.value = CaseControlState.Unavailable
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
    //  3) Suscripción a temperatura, batería y ubicación
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
        subscribeToLocation(gatt)
        subscribeToControl(gatt)
        return true
    }

    /**
     * La ubicación es opcional, como la batería: si el estuche no la ofrece o
     * no se puede activar, el enlace sigue siendo válido para la temperatura.
     */
    private suspend fun subscribeToLocation(gatt: BluetoothGatt) {
        val location = gatt.findCharacteristic(GattSpec.LOCATION_AND_SPEED)
        val subscribed = location != null && enableUpdates(gatt, location)
        _caseLocation.value = if (subscribed) {
            CaseLocationState.Unavailable
        } else {
            CaseLocationState.NotSupported
        }
    }

    /**
     * El servicio de control también es opcional: un estuche sin él sigue
     * midiendo temperatura. Cada característica se activa por separado para
     * que un firmware a medio hacer ofrezca lo que ya tiene.
     */
    private suspend fun subscribeToControl(gatt: BluetoothGatt) {
        if (gatt.getService(GattSpec.CONTROL_SERVICE) == null) {
            _controlState.value = CaseControlState.NotSupported
            return
        }
        // Se marca listo antes de leer: las lecturas rellenan este estado.
        _controlState.value = CaseControlState.Ready()

        gatt.findCharacteristic(GattSpec.LOCK_STATE)?.let { lockState ->
            runGattOperation { gatt.readCharacteristic(lockState) }
            enableUpdates(gatt, lockState)
        }
        gatt.findCharacteristic(GattSpec.PIN_REQUIRED)?.let { pinRequired ->
            runGattOperation { gatt.readCharacteristic(pinRequired) }
        }
        gatt.findCharacteristic(GattSpec.ACCESS_EVENT)?.let { accessEvent ->
            enableUpdates(gatt, accessEvent)
        }
    }

    // ------------------------------------------------------------------
    //  Órdenes al estuche (servicio de control)
    // ------------------------------------------------------------------
    override suspend fun setPinRequired(required: Boolean, currentPin: String): Result<Unit> =
        writeControl(GattSpec.PIN_REQUIRED, ControlCodec.encodePinRequired(required, currentPin))
            .onSuccess {
                // El estuche no avisa de este cambio: se refleja lo que acaba de aceptar.
                updateControl { it.copy(pinRequired = required) }
            }

    override suspend fun changePin(currentPin: String, newPin: String): Result<Unit> =
        writeControl(GattSpec.PIN_CHANGE, ControlCodec.encodePinChange(currentPin, newPin))

    override suspend fun setAlarm(on: Boolean): Result<Unit> =
        writeControl(GattSpec.ALARM, ControlCodec.encodeAlarm(on))

    override suspend fun setGpsIntervalSeconds(seconds: Int): Result<Unit> =
        writeControl(GattSpec.GPS_INTERVAL, ControlCodec.encodeGpsInterval(seconds))

    /**
     * Escribe una orden y espera la respuesta del estuche.
     *
     * @param payload trama ya codificada, o null si el valor no era válido.
     */
    private suspend fun writeControl(uuid: java.util.UUID, payload: ByteArray?): Result<Unit> {
        if (payload == null) return commandFailure(CaseCommandError.INVALID_VALUE)
        val gatt = activeGatt
        if (gatt == null || _connectionStatus.value != ConnectionStatus.CONNECTED) {
            return commandFailure(CaseCommandError.NOT_CONNECTED)
        }
        return try {
            val characteristic = gatt.findCharacteristic(uuid)
                ?: return commandFailure(CaseCommandError.NOT_SUPPORTED)
            val status = runGattWrite(gatt, characteristic, payload)
                ?: return commandFailure(CaseCommandError.FAILED)
            val error = ControlCodec.errorFromWriteStatus(status) ?: return Result.success(Unit)
            if (error == CaseCommandError.PAIRING_REQUIRED) requestBond(gatt.device)
            commandFailure(error)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            commandFailure(CaseCommandError.FAILED)
        }
    }

    private fun commandFailure(error: CaseCommandError): Result<Unit> =
        Result.failure(CaseCommandException(error))

    /**
     * @return el código de respuesta del estuche, o null si la escritura no
     * pudo empezar o no hubo respuesta a tiempo.
     */
    private suspend fun runGattWrite(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        payload: ByteArray
    ): Int? = gattOperationMutex.withLock {
        val completion = CompletableDeferred<Int>()
        pendingWriteStatus = completion
        val started = try {
            writeCharacteristicCompat(gatt, characteristic, payload)
        } catch (e: SecurityException) {
            false
        }
        val status = if (started) {
            withTimeoutOrNull(CONTROL_WRITE_TIMEOUT_MS) { completion.await() }
        } else {
            null
        }
        pendingWriteStatus = null
        status
    }

    @Suppress("DEPRECATION")
    private fun writeCharacteristicCompat(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(
                characteristic,
                value,
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            ) == BluetoothStatusCodes.SUCCESS
        } else {
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            characteristic.value = value
            gatt.writeCharacteristic(characteristic)
        }

    /**
     * El estuche rechazó la orden porque el teléfono no está emparejado: se
     * pide a Android que abra el emparejamiento. La orden no se reintenta
     * sola; el usuario la repite cuando termine de emparejar.
     */
    private fun requestBond(device: BluetoothDevice) {
        try {
            if (device.bondState == BluetoothDevice.BOND_NONE) device.createBond()
        } catch (e: SecurityException) {
            // Sin permiso no se puede emparejar: el error de la orden ya lo dice.
        }
    }

    private fun updateControl(transform: (CaseControlState.Ready) -> CaseControlState.Ready) {
        _controlState.update { current ->
            (current as? CaseControlState.Ready)?.let(transform) ?: current
        }
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
                pendingWriteStatus?.complete(BluetoothGatt.GATT_FAILURE)
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

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            pendingWriteStatus?.complete(status)
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
            GattSpec.LOCATION_AND_SPEED ->
                GattParser.parseLocationAndSpeed(value)?.let { reading ->
                    _caseLocation.value = CaseLocationState.Reported(reading, now)
                }
            GattSpec.LOCK_STATE ->
                ControlCodec.parseLockState(value)?.let { lockState ->
                    updateControl { it.copy(lockState = lockState) }
                }
            GattSpec.PIN_REQUIRED ->
                ControlCodec.parsePinRequired(value)?.let { required ->
                    updateControl { it.copy(pinRequired = required) }
                }
            GattSpec.ACCESS_EVENT ->
                ControlCodec.parseAccessEvent(value)?.let { frame ->
                    // El estuche no tiene reloj: la hora del evento es la de recepción.
                    _accessEvents.tryEmit(
                        AccessEvent(
                            type = frame.type,
                            failedAttempts = frame.failedAttempts,
                            receivedAt = now
                        )
                    )
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
        const val ACCESS_EVENT_BUFFER_SIZE = 16

        // Más largo que una operación normal: puede incluir el diálogo de
        // emparejamiento de Android, que espera al usuario.
        const val CONTROL_WRITE_TIMEOUT_MS = 30_000L
    }
}