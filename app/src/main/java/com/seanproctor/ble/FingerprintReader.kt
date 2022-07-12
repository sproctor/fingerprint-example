package com.seanproctor.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGattDescriptor
import android.graphics.Bitmap
import android.util.Log
import com.juul.kable.Advertisement
import com.juul.kable.Peripheral
import com.juul.kable.peripheral
import com.secugen.fmssdk.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeoutOrNull
import java.util.*

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
class FingerprintReader(
    private val scope: CoroutineScope,
) {

    val TAG = "FingerprintReader"

    private var peripheral: Peripheral? = null

    private var remainingSize = 0
    private var bytesRead = 0
    private val imgBuffer = ByteArray(FMSAPI.PACKET_HEADER_SIZE + FMSImage.IMG_SIZE_MAX + 1)

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

    suspend fun connect(advertisement: Advertisement) {
        Log.d(TAG, "Connecting to device: ${advertisement.address} ==================")
        val peripheral = scope.peripheral(advertisement) {
            this.onServicesDiscovered {
                requestMtu(REQUEST_MTU_SIZE)
            }
        }
        this.peripheral = peripheral
        peripheral.connect()
        val services = peripheral.services ?: error("Services have not been discovered")
        val descriptor = services
            .first { it.serviceUuid == SERVICE_SECUGEN_SPP_OVER_BLE }
            .characteristics
            .first { it.characteristicUuid == CHARACTERISTIC_READ_NOTIFY }
            .descriptors
            .first { it.descriptorUuid == CLIENT_CHARACTERISTIC_NOTIFY_CONFIG }
        Log.d(TAG, "Setting notification")
        peripheral.write(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
    }

    suspend fun disconnectDevice() {
        withTimeoutOrNull(5_000L) {
            peripheral?.disconnect()
        }
    }

    fun captureFingerprint(): Flow<FingerprintEvent> =
        flow {
            // Flush old data
            remainingSize = 0
            bytesRead = 0

            Log.d(TAG, "Capturing fingerprint")
            val peripheral = peripheral ?: error("No peripheral connected")
            val services = peripheral.services ?: error("Services have not been discovered")
            val service = services
                .first { it.serviceUuid == SERVICE_SECUGEN_SPP_OVER_BLE }
            val writeCharacteristic = service.characteristics
                .first { it.characteristicUuid == CHARACTERISTIC_WRITE }
            peripheral.write(writeCharacteristic, FMSAPI.cmdFPCapture(FMSAPI.IMAGE_SIZE_HALF))
            val readCharacteristic = service.characteristics
                .first { it.characteristicUuid == CHARACTERISTIC_READ_NOTIFY }
            emit(FingerprintEvent.Progress(0))
            while (true) {
                val data = peripheral.read(readCharacteristic)
                handleReadCharacteristic(data) {
                    emit(it)
                }
            }
        }

    private suspend fun handleReadCharacteristic(
        data: ByteArray,
        onEvent: suspend (FingerprintEvent) -> Unit
    ) {
        if (data.size == FMSAPI.PACKET_HEADER_SIZE.toInt() && data[0] == 0x4E.toByte()) {
            Log.d(TAG, "Detected Notify message, try read data")
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
                onEvent(FingerprintEvent.Progress(progress))

                if (remainingSize == 0) {
                    processBuffer(onEvent)
                }
            } else {
                val stringBuilder = StringBuilder(data.size)
                for (byteChar in data) {
                    stringBuilder.append(String.format("%02X ", byteChar))
                }
                Log.d(TAG, "Received read notify: $stringBuilder")

                handleRead(data, onEvent)
            }
        }
    }

    private suspend fun handleRead(
        readResponseBuf: ByteArray,
        onEvent: suspend (FingerprintEvent) -> Unit
    ) {
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
                        onEvent(FingerprintEvent.Captured(img.get()))
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
                        onEvent(FingerprintEvent.Captured(img.get()))
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

    private suspend fun processBuffer(onEvent: suspend (FingerprintEvent) -> Unit) {
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
        handleRead(imgBuffer, onEvent)
    }

    private fun updateBytesRemaining(data: ByteArray) {
        if (data.size == FMSAPI.PACKET_HEADER_SIZE.toInt()) {
            val header = FMSHeader(data)
            if (header.pkt_command == FMSAPI.CMD_FP_CAPTURE && header.pkt_checksum == data[FMSAPI.PACKET_HEADER_SIZE - 1]) {
                remainingSize =
                    FMSAPI.PACKET_HEADER_SIZE + (header.pkt_datasize1.toInt() and 0x0000FFFF or (header.pkt_datasize2.toInt() shl 16 and -0x10000))
                bytesRead = 0

                Log.d(TAG, "RemainingSize: $remainingSize")
                Log.d(TAG, "data.size: ${data.size}")
            }
        }
    }
}

sealed interface FingerprintEvent {
    data class Progress(val percent: Int) : FingerprintEvent

    data class Captured(val image: Bitmap) : FingerprintEvent

    data class Error(val message: String?) : FingerprintEvent
}