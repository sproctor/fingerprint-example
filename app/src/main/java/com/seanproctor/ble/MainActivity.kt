@file:SuppressLint("MissingPermission")

package com.seanproctor.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role.Companion.Image
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionRequired
import com.google.accompanist.permissions.rememberPermissionState
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
                        DeviceInfo(device = viewModel.connectedDevice)

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
    val permissionState =
        rememberPermissionState(permission = Manifest.permission.ACCESS_FINE_LOCATION)
    PermissionRequired(
        permissionState = permissionState,
        permissionNotGrantedContent = {
            Text("Location permission is needed to use the scanner")
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { permissionState.launchPermissionRequest() }) {
                Text("Request permission")
            }
        },
        permissionNotAvailableContent = {
            Text("Access fine location permission denied")
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
    val context = LocalContext.current

    AlertDialog(
        modifier = Modifier
            .widthIn(min = 300.dp)
            .fillMaxHeight(),
        onDismissRequest = { viewModel.stopScanning() },
        text = {
            val devices = viewModel.availableDevices

            if (devices.isEmpty()) {
                Text("No devices found")
            } else {
                Column {
                    devices.forEach { entry ->
                        val device = entry.value
                        ListItem(
                            modifier = Modifier.clickable {
                                viewModel.connectDevice(context, device)
                            },
                            text = { Text(device.name ?: "Unnamed device") },
                            secondaryText = { Text(device.address ?: "") },
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