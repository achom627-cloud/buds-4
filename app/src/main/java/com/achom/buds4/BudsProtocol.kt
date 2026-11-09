package com.achom.buds4

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream

data class BudsBattery(val left: Int = 100, val right: Int = 100, val case: Int = 100)

object BudsProtocol {
    private var socket: BluetoothSocket? = null
    private var outStream: OutputStream? = null
    private var inStream: InputStream? = null
    private var seq: Byte = 1

    val connected = MutableStateFlow(false)
    val deviceName = MutableStateFlow("OnePlus Buds 4")
    // 2 = Шумодав, 1 = Выкл, 4 = Прозрачность (поменяли местами 2 и 4)
    val ancMode = MutableStateFlow(2)
    val battery = MutableStateFlow(BudsBattery())

    private fun buildPacket(cat: Int, op: Int, payload: ByteArray): ByteArray {
        val plen = payload.size
        val len = 7 + plen
        val s = seq++
        return byteArrayOf(
            0xAA.toByte(), len.toByte(), 0x00, 0x00,
            cat.toByte(), op.toByte(), s,
            (plen and 0xFF).toByte(), ((plen shr 8) and 0xFF).toByte()
        ) + payload
    }

    @SuppressLint("MissingPermission")
    suspend fun connect(targetMac: String? = null): Boolean = withContext(Dispatchers.IO) {
        try {
            if (socket?.isConnected == true) return@withContext true
            val adapter = BluetoothAdapter.getDefaultAdapter() ?: return@withContext false
            val device: BluetoothDevice = adapter.bondedDevices.firstOrNull {
                (targetMac != null && it.address == targetMac) ||
                it.name?.contains("Buds", ignoreCase = true) == true ||
                it.name?.contains("OnePlus", ignoreCase = true) == true
            } ?: return@withContext false

            deviceName.value = device.name ?: "OnePlus Buds 4"

            val m = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
            val s = m.invoke(device, 15) as BluetoothSocket
            s.connect()
            socket = s
            outStream = s.outputStream
            inStream = s.inputStream
            connected.value = true

            sendRaw(0x0F, 0x04, byteArrayOf(0x01))
            sendRaw(0x0C, 0x01, byteArrayOf(0x03, 0x01))
            sendRaw(0x0C, 0x01, byteArrayOf(0x02, 0x01))
            listenLoop()
            true
        } catch (e: Exception) {
            connected.value = false
            false
        }
    }

    private fun listenLoop() {
        Thread {
            val buf = ByteArray(1024)
            try {
                while (socket?.isConnected == true) {
                    val n = inStream?.read(buf) ?: break
                    if (n <= 0) break
                    parseIncoming(buf.copyOfRange(0, n))
                }
            } catch (_: Exception) {
            } finally {
                connected.value = false
            }
        }.start()
    }

    private fun parseIncoming(data: ByteArray) {
        var i = 0
        while (i + 9 <= data.size) {
            if (data[i] != 0xAA.toByte()) { i++; continue }
            val cat = data[i + 4].toInt() and 0xFF
            val plen = (data[i + 7].toInt() and 0xFF) or ((data[i + 8].toInt() and 0xFF) shl 8)
            if (i + 9 + plen <= data.size) {
                val payload = data.copyOfRange(i + 9, i + 9 + plen)
                if (cat == 0x04 && payload.size >= 3 && payload[0] == 0x01.toByte() && payload[1] == 0x01.toByte()) {
                    ancMode.value = payload[2].toInt() and 0xFF
                }
                if (cat == 0x0C && payload.size >= 4) {
                    // Ищем реальные значения заряда (от 10 до 100), пропуская заголовки TLV (01, 02, 03, 06, 00)
                    val vals = payload.map { it.toInt() and 0xFF }.filter { it in 10..100 }
                    if (vals.size >= 2) {
                        val l = vals[0]
                        val r = vals[1]
                        val c = vals.getOrElse(2) { battery.value.case }
                        battery.value = BudsBattery(l, r, c)
                    }
                }
                i += 9 + plen
            } else break
        }
    }

    private suspend fun sendRaw(cat: Int, op: Int, payload: ByteArray) = withContext(Dispatchers.IO) {
        try {
            outStream?.write(buildPacket(cat, op, payload))
            outStream?.flush()
        } catch (_: Exception) {
            connected.value = false
        }
    }

    suspend fun setAncMode(mode: Int) {
        ancMode.value = mode
        sendRaw(0x04, 0x04, byteArrayOf(0x01, 0x01, mode.toByte()))
    }
}
