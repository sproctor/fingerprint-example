package com.seanproctor.ble

import android.bluetooth.BluetoothGatt
import android.util.Log

fun BluetoothGatt.printGattTable() {
    if (services.isEmpty()) {
        Log.i("printGattTable", "No service and characteristic available, call discoverServices() first?")
        return
    }
    services.forEach { service ->
        val characteristicsTable = service.characteristics.joinToString(
            separator = "\n|--",
            prefix = "|--"
        ) { it.uuid.toString() }
        Log.i("printGattTable", "\nService ${service.uuid}\nCharacteristics:\n$characteristicsTable"
        )
    }
}

//Display byte array as hex string
fun ByteArray.toHexString(): String {
    val sb = java.lang.StringBuilder()
    for (i in 0 until size) {
        sb.append(String.format("%02x", this[i].toInt() and 0xff))
    }
    return sb.toString()
}