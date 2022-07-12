@file:SuppressLint("MissingPermission")

package com.seanproctor.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionsRequired
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.seanproctor.ble.ui.theme.BLEExampleTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val viewModel = BleViewModel(bluetoothManager)

        setContent {
            BLEExampleTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    Column {
                        val reader = viewModel.fingerprintReader
                        // DeviceInfo(device = viewModel.fingerprintReader?.)

                        FingerPrint(viewModel = viewModel)

                        if (reader == null) {
                            ConnectButton(viewModel)
                        } else {
                            Button(onClick = { viewModel.disconnectDevice() }) {
                                Text("Disconnect")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FingerPrint(viewModel: BleViewModel) {
    when (val deviceState = viewModel.deviceState) {
        is DeviceState.Waiting -> {
            Button(onClick = { viewModel.capture() }) {
                Text("Capture fingerprint")
            }
        }
        is DeviceState.Busy -> {
            Text("Working: ${deviceState.progress}")
        }
        else -> {}
    }
    val fingerprint = viewModel.fingerprintImage
    if (fingerprint != null) {
        Image(
            bitmap = fingerprint.asImageBitmap(),
            contentDescription = null,
        )
    }
}

@Composable
fun DeviceInfo(device: BluetoothDevice?) {
    val textToDisplay =
        if (device != null) {
            "Connected to ${device.name ?: device.address}"
        } else {
            "No device connected"
        }
    Card {
        Text(textToDisplay)
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ConnectButton(viewModel: BleViewModel) {
    val permissionList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        listOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN)
    } else {
        listOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    val permissionState = rememberMultiplePermissionsState(permissions = permissionList)
    PermissionsRequired(
        multiplePermissionsState = permissionState,
        permissionsNotGrantedContent = {
            Text("Permission is needed to use the scanner")
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { permissionState.launchMultiplePermissionRequest() }) {
                Text("Request permission")
            }
        },
        permissionsNotAvailableContent = {
            Text("Bluetooth access permission denied")
        }
    ) {
        val isScanning = viewModel.isScanning
        Button(
            onClick = { viewModel.startScanning() },
            enabled = !isScanning
        ) {
            Text("Scan for BLE devices")
        }

        if (isScanning) {
            ScanDialog(viewModel)
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun ScanDialog(viewModel: BleViewModel) {
    AlertDialog(
        modifier = Modifier
            .widthIn(min = 300.dp)
            .fillMaxHeight(),
        onDismissRequest = { viewModel.stopScanning() },
        text = {
            val advertisements = viewModel.advertisements

            if (advertisements.isEmpty()) {
                Text("No devices found")
            } else {
                Column {
                    advertisements.forEach { advertisement ->
                        ListItem(
                            modifier = Modifier.clickable {
                                viewModel.connectDevice(advertisement)
                            },
                            text = { Text(advertisement.name ?: "Unnamed device") },
                            secondaryText = { Text(advertisement.address) },
                        )
                    }
                }
            }
        },
        buttons = {
            Button(onClick = { viewModel.stopScanning() }) {
                Text("Cancel scan")
            }
        }
    )
}