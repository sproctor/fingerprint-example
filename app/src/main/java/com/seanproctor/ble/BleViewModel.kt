package com.seanproctor.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secugen.fmssdk.FMSAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*

@SuppressLint("MissingPermission")
class BleViewModel(bluetoothManager: BluetoothManager) : ViewModel() {

    private val TAG = "BleViewModel"

    private val bluetoothAdapter = bluetoothManager.adapter
    private val bleScanner = bluetoothAdapter.bluetoothLeScanner

    var isScanning by mutableStateOf(false)
        private set

    var fingerprintReader by mutableStateOf<FingerprintReader?>(null)
        private set

    var connectedDevice by mutableStateOf<BluetoothDevice?>(null)
        private set

    var deviceState by mutableStateOf<DeviceState>(DeviceState.Unavailable)
        private set

    var availableDevices by mutableStateOf<Map<String, BluetoothDevice>>(emptyMap())
        private set

    var fingerprintImage by mutableStateOf<Bitmap?>(null)
        private set

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            with(result.device) {
                Log.i(TAG, "Found BLE device! Name: ${name ?: "Unnamed"}, address: $address")
                availableDevices = availableDevices + (address to this)
            }
        }
    }

    fun startScanning() {
        if (isScanning) return

        isScanning = true

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        availableDevices = emptyMap()

        bleScanner.startScan(null, scanSettings, scanCallback)
    }

    fun stopScanning() {
        if (isScanning) {
            bleScanner.stopScan(scanCallback)
            isScanning = false
        }
    }

    fun connectDevice(context: Context, device: BluetoothDevice) {
        stopScanning()
        val reader = FingerprintReader()
        fingerprintReader = reader
        reader.listener = object : FingerprintListener {
            override fun disconnected() {
                fingerprintReader = null
                connectedDevice = null
                deviceState = DeviceState.Unavailable
            }
            override fun connected(device: BluetoothDevice) {
                connectedDevice = device
                deviceState = DeviceState.Waiting
            }
            override fun capturedFingerprint(image: Bitmap) {
                fingerprintImage = image
                deviceState = DeviceState.Waiting
            }
            override fun progressed(progress: Int) {
                deviceState = DeviceState.Busy(progress)
            }
        }
        viewModelScope.launch {
            reader.connect(context, device)
        }
    }

    fun disconnectDevice() {
        fingerprintReader?.disconnectDevice()
        fingerprintReader = null
        deviceState = DeviceState.Unavailable
    }

    override fun onCleared() {
        stopScanning()
        disconnectDevice()
    }

    fun capture() {
        deviceState = DeviceState.Busy(0)
        viewModelScope.launch {
            fingerprintReader?.captureFingerprint()
        }
    }
}

sealed interface DeviceState {
    object Unavailable : DeviceState
    object Waiting : DeviceState
    data class Busy(val progress: Int) : DeviceState
}