package com.seanproctor.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanSettings
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.juul.kable.Advertisement
import com.juul.kable.ObsoleteKableApi
import com.juul.kable.Scanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch

@SuppressLint("MissingPermission")
class BleViewModel(bluetoothManager: BluetoothManager) : ViewModel() {

    private val TAG = "BleViewModel"

    var isScanning by mutableStateOf(false)
        private set

    var fingerprintReader by mutableStateOf<FingerprintReader?>(null)
        private set

    var deviceState by mutableStateOf<DeviceState>(DeviceState.Unavailable)
        private set

    var advertisements by mutableStateOf<List<Advertisement>>(emptyList())
        private set

    var fingerprintImage by mutableStateOf<Bitmap?>(null)
        private set

    @OptIn(ObsoleteKableApi::class)
    private val scanner = Scanner {
        scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
    }
    private val scanScope = CoroutineScope(viewModelScope.coroutineContext + Job())
    private val found = hashMapOf<String, Advertisement>()

    fun startScanning() {
        if (isScanning) return

        isScanning = true

        scanScope.launch {
            scanner
                .advertisements
                .catch { deviceState = DeviceState.Error(it.message ?: "Unknown error") }
                .onCompletion { isScanning = false }
                .filter { it.address.startsWith(SECUGEN_MAC_ADDRESS) }
                .collect { advertisement ->
                    found[advertisement.address] = advertisement
                    advertisements = found.values.toList()
                }
        }
    }

    fun stopScanning() {
        if (isScanning) {
            scanScope.cancel()
            isScanning = false
        }
    }

    fun connectDevice(advertisement: Advertisement) {
        stopScanning()
        val reader = FingerprintReader(viewModelScope)
        fingerprintReader = reader
        viewModelScope.launch {
            reader.connect(advertisement)
            deviceState = DeviceState.Waiting
        }
    }

    fun disconnectDevice() {
        viewModelScope.launch {
            fingerprintReader?.disconnectDevice()
            fingerprintReader = null
            deviceState = DeviceState.Unavailable
        }
    }

    override fun onCleared() {
        stopScanning()
        disconnectDevice()
    }

    fun capture() {
        deviceState = DeviceState.Busy(0)
        viewModelScope.launch {
            fingerprintReader?.captureFingerprint()
                ?.collect {
                    when (it) {
                        is FingerprintEvent.Progress -> deviceState = DeviceState.Busy(it.percent)
                        is FingerprintEvent.Captured -> {
                            fingerprintImage = it.image
                            deviceState = DeviceState.Waiting
                        }
                        is FingerprintEvent.Error -> {

                        }
                    }
                }
        }
    }
}

sealed interface DeviceState {
    data class Error(val message: String) : DeviceState
    object Unavailable : DeviceState
    object Waiting : DeviceState
    data class Busy(val progress: Int) : DeviceState
}