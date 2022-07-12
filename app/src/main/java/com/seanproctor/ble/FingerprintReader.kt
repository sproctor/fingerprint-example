package com.seanproctor.ble

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.util.Log
import com.benasher44.uuid.uuidFrom
import com.juul.kable.Advertisement
import com.juul.kable.Peripheral
import com.juul.kable.WriteType
import com.juul.kable.peripheral
import com.secugen.fmssdk.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeoutOrNull

const val REQUEST_MTU_SIZE = 301
val SERVICE_SECUGEN_SPP_OVER_BLE = "0000fda0-0000-1000-8000-00805f9b34fb"
val CHARACTERISTIC_READ_NOTIFY = "00002bb1-0000-1000-8000-00805f9b34fb"
val CHARACTERISTIC_WRITE = "00002bb2-0000-1000-8000-00805f9b34fb"
val CLIENT_CHARACTERISTIC_NOTIFY_CONFIG = "00002902-0000-1000-8000-00805f9b34fb"
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

    private var bytesRemaining = 0
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
    }

    suspend fun disconnectDevice() {
        withTimeoutOrNull(5_000L) {
            peripheral?.disconnect()
        }
    }

    fun captureFingerprint(): Flow<FingerprintEvent> =
        flow {
            Log.d(TAG, "Capturing fingerprint")
            val peripheral = peripheral ?: error("No peripheral connected")
            val services = peripheral.services ?: error("Services have not been discovered")
            val service = services
                .first { it.serviceUuid == uuidFrom(SERVICE_SECUGEN_SPP_OVER_BLE) }
            val writeCharacteristic = service.characteristics
                .first { it.characteristicUuid == uuidFrom(CHARACTERISTIC_WRITE) }
            Log.d(TAG, "write characteristic: $writeCharacteristic")
            peripheral.write(
                writeCharacteristic,
                FMSAPI.cmdFPCapture(FMSAPI.IMAGE_SIZE_HALF),
                writeType = WriteType.WithResponse
            )
            val readCharacteristic = service.characteristics
                .first { it.characteristicUuid == uuidFrom(CHARACTERISTIC_READ_NOTIFY) }
            val firstPacket = peripheral.observe(readCharacteristic).first()
            if (firstPacket.size == FMSAPI.PACKET_HEADER_SIZE.toInt() && firstPacket[0] == 0x4E.toByte()) {
                Log.d(TAG, "Detected Notify message, ${firstPacket.toHexString()}")
                while (true) {
                    val data = peripheral.read(readCharacteristic)
                    handleReadCharacteristic(data)
                    if (bytesRemaining <= 0) {
                        val img = processBuffer()
                        if (img != null) {
                            emit(FingerprintEvent.Captured(img))
                        } else {
                            emit(FingerprintEvent.Error("Error processing fingerprint"))
                        }
                        break
                    }
                    val progress: Int = bytesRead * 100 / (bytesRemaining + bytesRead)
                    emit(FingerprintEvent.Progress(progress))
                }
            } else {
                buildImage(firstPacket)
            }
        }

    private fun handleReadCharacteristic(
        data: ByteArray,
    ) {
        tryUpdateBytesRemaining(data)

        if (bytesRemaining > 0) {
            handleData(data)
        }
    }

    private fun handleData(data: ByteArray) {
        System.arraycopy(data, 0, imgBuffer, bytesRead, data.size)
        bytesRemaining -= data.size
        bytesRead += data.size

        Log.d(TAG, "BytesRead: ${data.size}, RemainingSize: $bytesRemaining")
    }

    // slightly misnomer because it does error handling as well... too hard to separate for now
    private fun buildImage(
        readResponseBuf: ByteArray,
    ): Bitmap? {
        Log.d(TAG, "handleRead: ${readResponseBuf.toHexString()}")
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
                         return img.get()
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
                        return img.get()
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
        return null
    }

    private fun processBuffer(): Bitmap? {
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
        return buildImage(imgBuffer)
    }

    private fun tryUpdateBytesRemaining(data: ByteArray) {
        if (data.size == FMSAPI.PACKET_HEADER_SIZE.toInt()) {
            val header = FMSHeader(data)
            if (header.pkt_command == FMSAPI.CMD_FP_CAPTURE && header.pkt_checksum == data[FMSAPI.PACKET_HEADER_SIZE - 1]) {
                bytesRemaining =
                    FMSAPI.PACKET_HEADER_SIZE + (header.pkt_datasize1.toInt() and 0x0000FFFF or (header.pkt_datasize2.toInt() shl 16 and -0x10000))
                bytesRead = 0

                Log.d(TAG, "Byte remaining: $bytesRemaining")
            }
        }
    }
}

sealed interface FingerprintEvent {
    data class Progress(val percent: Int) : FingerprintEvent

    data class Captured(val image: Bitmap) : FingerprintEvent

    data class Error(val message: String?) : FingerprintEvent
}