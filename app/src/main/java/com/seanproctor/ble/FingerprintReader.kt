package com.seanproctor.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.secugen.fmssdk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*
import kotlin.experimental.and

const val REQUEST_MTU_SIZE = 301
val SERVICE_SECUGEN_SPP_OVER_BLE = UUID.fromString("0000fda0-0000-1000-8000-00805f9b34fb")!!
val CHARACTERISTIC_READ_NOTIFY = UUID.fromString("00002bb1-0000-1000-8000-00805f9b34fb")!!
val CHARACTERISTIC_WRITE = UUID.fromString("00002bb2-0000-1000-8000-00805f9b34fb")!!
val CLIENT_CHARACTERISTIC_NOTIFY_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")!!
const val SECUGEN_MAC_ADDRESS = "CC:35:5A"

class WSQInfoClass {
    var width = 0
    var height = 0
    var pixelDepth = 0
    var ppi = 0
    var lossyFlag = 0
}

@SuppressLint("MissingPermission")
class FingerprintReader {

    val TAG = "FingerprintReader"

    private var gattConnection: BluetoothGatt? = null

    private var remainingSize = 0
    private var bytesRead = 0
    private val imgBuffer = ByteArray(FMSAPI.PACKET_HEADER_SIZE + FMSImage.IMG_SIZE_MAX + 1)

    var listener: FingerprintListener? = null

    companion object {
        init {
            System.loadLibrary("sgwsq-jni")
        }

        external fun jniSgWSQDecode(
            WSQInfoClass: WSQInfoClass,
            wsqImage: ByteArray,
            wsqImageLength: Int
        ): ByteArray
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val deviceAddress = gatt.device.address

            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.d(TAG, "Successfully connected to $deviceAddress")
                    gattConnection = gatt
                    gatt.requestMtu(REQUEST_MTU_SIZE)
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    assert(gatt == gattConnection)
                    Log.d(TAG, "Successfully disconnected from $deviceAddress")
                    gatt.close()
                    gattConnection = null
                    listener?.disconnected()
                }
            } else {
                Log.w(TAG, "Error $status encountered for $deviceAddress! Disconnecting...")
                gatt.close()
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Increased MTU size: $mtu")
                gatt.discoverServices()
            } else {
                Log.w(TAG, "Could not increase MTU size")
                disconnectDevice()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                with(gatt) {
                    Log.d(TAG, "Discovered ${services.size} services for ${device.address}")
                    listener?.connected(device)
                }

                val service = gatt.getService(SERVICE_SECUGEN_SPP_OVER_BLE)
                if (service != null) {
                    Log.d(TAG, "Custom gatt Service found")
                    val characteristic = service.getCharacteristic(CHARACTERISTIC_READ_NOTIFY)
                    if (characteristic.properties or BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) {
                        Log.d(TAG, "Setting notification")
                        gatt.setCharacteristicNotification(characteristic, true)
                        val descriptor = characteristic.getDescriptor(
                            CLIENT_CHARACTERISTIC_NOTIFY_CONFIG
                        )
                        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        gatt.writeDescriptor(descriptor)
                    }
                }
            } else {
                Log.w(TAG, "onServicesDiscovered received: $status")
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            Log.d(TAG, "onCharacteristicRead status: $status")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                handleCustomCharacheristic(characteristic)
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Wrote successfully: ${characteristic.value.toHexString()}")
            } else {
                Log.w(TAG, "Write failed, retrying: ${characteristic.value.toHexString()}")
                gatt.writeCharacteristic(characteristic)
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            Log.d(TAG, "onCharacteristicChanged (UUID: ${characteristic.uuid})")
            handleCustomCharacheristic(characteristic)
        }
    }

    suspend fun connect(context: Context, device: BluetoothDevice) {
        withContext(Dispatchers.Main) {
            Log.d(TAG, "Connecting to device: ${device.address} ==================")
            device.connectGatt(context, false, gattCallback)
        }
    }

    fun disconnectDevice() {
        gattConnection?.close()
        gattConnection = null
    }

    suspend fun captureFingerprint() {
        Log.d(TAG, "Capturing fingerprint")
        withContext(Dispatchers.IO) {
            writeCustomCharacteristic(FMSAPI.cmdFPCapture(FMSAPI.IMAGE_SIZE_HALF))
        }
    }

    private fun writeCustomCharacteristic(value: ByteArray) {
        val gatt = gattConnection ?: return
        Log.d(TAG, "Writing custom characteristic: ${value.toHexString()}")
        val service = gatt.getService(SERVICE_SECUGEN_SPP_OVER_BLE)
        val writeCharacteristic = service.getCharacteristic(CHARACTERISTIC_WRITE)
        writeCharacteristic.value = value
        if (!gatt.writeCharacteristic(writeCharacteristic)) {
            Log.w(TAG, "Failed to write characteristic")
        }
    }

    private fun readCustomCharacteristic() {
        val gatt = gattConnection ?: return

        val service = gatt.getService(SERVICE_SECUGEN_SPP_OVER_BLE)
        if (service == null) {
            Log.w(TAG, "Custom BLE Service not found")
            return
        }

        val readCharacteristic = service.getCharacteristic(CHARACTERISTIC_READ_NOTIFY)
        if (!gatt.readCharacteristic(readCharacteristic)) {
            Log.w(TAG, "Failed to read characteristic")
        }
    }

    private fun handleCustomCharacheristic(characteristic: BluetoothGattCharacteristic) {
        if (characteristic.uuid == CHARACTERISTIC_READ_NOTIFY) {
            handleReadCharacteristic(characteristic.value)
        } else {
            handleWriteCharacteristic(characteristic.value)
        }
    }

    private fun handleReadCharacteristic(data: ByteArray) {
        if (data.size == FMSAPI.PACKET_HEADER_SIZE.toInt() && data[0] == 0x4E.toByte()) {
            Log.d(TAG, "Detected Notify message, try read data")
            readCustomCharacteristic()
            return
        }
        if (data.isNotEmpty()) {
            updateBytesRemaining(data)

            if (remainingSize > 0) {
                System.arraycopy(data, 0, imgBuffer, bytesRead, data.size)
                remainingSize -= data.size
                bytesRead += data.size

                Log.d(TAG, "BytesRead: ${data.size}, RemainingSize: $remainingSize")

                val progress: Int = bytesRead * 100 / (remainingSize + bytesRead)
                listener?.progressed(progress)

                if (remainingSize > 0) {
                    readCustomCharacteristic()
                } else {
                    processBuffer()
                }
            } else {
                val stringBuilder = StringBuilder(data.size)
                for (byteChar in data) {
                    stringBuilder.append(String.format("%02X ", byteChar))
                }
                Log.d(TAG, "Received read notify: $stringBuilder")

                handleRead(data)
            }
        } else {
            Log.d(TAG, "characteristic.getValue length ${data.size}")
        }
    }

    private fun handleRead(readResponseBuf: ByteArray) {
        val rHeader = FMSHeader(readResponseBuf)
        var data: FMSData? = null
        if (!(rHeader.pkt_command == FMSAPI.CMD_FP_CAPTURE && rHeader.isFullSize)) // Not full size capture, full size image using file i/o
            data = FMSData(readResponseBuf)

        when (rHeader.pkt_command) {
            FMSAPI.CMD_FP_CAPTURE -> when (rHeader.pkt_error) {
                FMSAPI.ERR_NONE -> {
                    if (rHeader.isWSQ) {
                        val myInfo = WSQInfoClass()
                        val imgBuf = data!!.get() // Handle WSQ and full size case?
                        val rtValue: ByteArray = jniSgWSQDecode(myInfo, imgBuf, data.d_length)
                        Log.d(TAG, "WSQ Information: ")
                        Log.d(TAG, "     width: " + myInfo.width + "   height: " + myInfo.height)
                        Log.d(
                            TAG,
                            "     ppi: " + myInfo.ppi + "   pixeldepth: " + myInfo.pixelDepth
                        )
                        Log.d(TAG, "     bitrate: " + "15:1 (0.75)")
                        Log.d(TAG, "     wsq size: " + data.d_length)
                        val img = FMSImage(rtValue, myInfo.width * myInfo.height)
                        listener?.capturedFingerprint(img.get())
                    } else {
                        val img = if (rHeader.isFullSize) { // full size
                            val fmsimgsave = FMSImageSave()
                            FMSImage(fmsimgsave.imgBuf, fmsimgsave.imgSize)
                        } else { // half size
                            FMSImage(data!!.get(), data.d_length)
                        }
                        Log.d(TAG, "Image Information: ")
                        Log.d(
                            TAG,
                            "     width: " + img.getmWidth() + "   height: " + img.getmHeight()
                        )
                        Log.d(TAG, "     image size: " + img.getmWidth() * img.getmHeight())
                        listener?.capturedFingerprint(img.get())
                    }
                }
                else -> {
                    Log.w(TAG, "Error: [" + Integer.toHexString(rHeader.pkt_error.toInt()) + "]")
                }
            }
            else -> {
                Log.w(TAG, "Unhandled read event")
            }
        }
    }

    private fun processBuffer() {
        Log.d(TAG, "Processing buffer")
        val headerbuf = ByteArray(FMSAPI.PACKET_HEADER_SIZE.toInt())
        System.arraycopy(imgBuffer, 0, headerbuf, 0, FMSAPI.PACKET_HEADER_SIZE.toInt())
        val tmpHeader = FMSHeader(headerbuf)
        if (tmpHeader.pkt_command == FMSAPI.CMD_FP_CAPTURE && tmpHeader.pkt_param1.toInt() == 0x0001) { // full size capture
            val fmsImgSave = FMSImageSave()
            val imgSize = bytesRead - FMSAPI.PACKET_HEADER_SIZE
            val completedImgBuffer = ByteArray(imgSize)
            System.arraycopy(imgBuffer, 12, completedImgBuffer, 0, imgSize)
            fmsImgSave.Do(completedImgBuffer, imgSize)

            // reset obtaining buffer size
            bytesRead = FMSAPI.PACKET_HEADER_SIZE.toInt()
        }
        handleRead(imgBuffer)
    }

    private fun updateBytesRemaining(data: ByteArray) {
        if (data.size == FMSAPI.PACKET_HEADER_SIZE.toInt()) {
            val header = FMSHeader(data)
            if (header.pkt_command == FMSAPI.CMD_FP_CAPTURE && header.pkt_checksum == data[FMSAPI.PACKET_HEADER_SIZE - 1]) {
                remainingSize =
                    FMSAPI.PACKET_HEADER_SIZE + (header.pkt_datasize1.toInt() and 0x0000FFFF or (header.pkt_datasize2.toInt() shl 16 and -0x10000))
                bytesRead = 0

                listener?.progressed(0)
                Log.d(TAG, "RemainingSize: $remainingSize")
                Log.d(TAG, "data.size: ${data.size}")
            }
        }
    }

    private fun handleWriteCharacteristic(data: ByteArray) {
        // For all other profiles, writes the data formatted in HEX.
        if (data.isNotEmpty()) {
            val stringBuilder = java.lang.StringBuilder(data.size)
            for (byteChar in data) {
                stringBuilder.append(String.format("%02X ", byteChar))
            }
            Log.d(TAG, "Received write: $stringBuilder")
            // Ignore extra data for now
        }
    }
}

interface FingerprintListener {
    fun disconnected()

    fun connected(device: BluetoothDevice)

    fun capturedFingerprint(image: Bitmap)

    fun progressed(progress: Int)
}