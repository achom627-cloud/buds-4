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

data class BudsBattery(val left: Int = 95, val right: Int = 95, val case: Int = 80)

object BudsProtocol {
    private var socket: BluetoothSocket? = null
    private var outStream: OutputStream? = null
    private var inStream: InputStream? = null
    private var seq: Byte = 1

    val connected = MutableStateFlow(false)
    val deviceName = MutableStateFlow("OnePlus Buds 4")
    val ancMode = MutableStateFlow(4) // 4 = ANC, 1 = Off, 2 = Transparency
    val ancLevel = MutableStateFlow(2) // 2 = Max, 1 = Mid, 0 = Low
    val gameMode = MutableStateFlow(false)
    val dualConn = MutableStateFlow(true)
    val battery = MutableStateFlow(BudsBattery())

    private fun buildPacket(cat: Int, op: Int, payload: ByteArray): ByteArray {
        val plen = payload.size
        val len = 7 + plen
        val s = seq++
        val header = byteArrayOf(
            0xAA.toByte(),
            len.toByte(),
            0x00, 0x00,
            cat.toByte(),
            op.toByte(),
            s,
            (plen and 0xFF).toByte(),
            ((plen shr 8) and 0xFF).toByte()
        )
        return header + payload
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

            // Вызов скрытого метода createRfcommSocket(15) — именно 15 канал из лога (DLCI 0x1f)
            val m = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
            val s = m.invoke(device, 15) as BluetoothSocket
            s.connect()
            socket = s
            outStream = s.outputStream
            inStream = s.inputStream
            connected.value = true

            // Инит-пакет при открытии приложения: cat=0f op=04 payload=01
            sendRaw(0x0F, 0x04, byteArrayOf(0x01))
            // Запрос батареи/состояния из лога: cat=0c op=01 payload=02 01
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
        // Парсинг уведомлений о режиме и заряде
        var i = 0
        while (i + 9 <= data.size) {
            if (data[i] != 0xAA.toByte()) { i++; continue }
            val cat = data[i + 4].toInt() and 0xFF
            val op = data[i + 5].toInt() and 0xFF
            val plen = (data[i + 7].toInt() and 0xFF) or ((data[i + 8].toInt() and 0xFF) shl 8)
            if (i + 9 + plen <= data.size) {
                val payload = data.copyOfRange(i + 9, i + 9 + plen)
                // Если пришёл ответ по батарее (cat=0c) или статус (cat=04)
                if (cat == 0x04 && payload.size >= 3 && payload[0] == 0x01.toByte() && payload[1] == 0x01.toByte()) {
                    ancMode.value = payload[2].toInt() and 0xFF
                }
                if (cat == 0x0C && payload.size >= 5) {
                    val l = (payload.getOrNull(2)?.toInt() ?: 95).coerceIn(1, 100)
                    val r = (payload.getOrNull(3)?.toInt() ?: 95).coerceIn(1, 100)
                    val c = (payload.getOrNull(4)?.toInt() ?: 80).coerceIn(0, 100)
                    battery.value = BudsBattery(l, r, c)
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

    // cat=04 op=04 payload=01 01 XX (04, 01, 02)
    suspend fun setAncMode(mode: Int) {
        ancMode.value = mode
        sendRaw(0x04, 0x04, byteArrayOf(0x01, 0x01, mode.toByte()))
    }

    // cat=06 op=04 payload=XX (02, 01, 00)
    suspend fun setAncLevel(level: Int) {
        ancLevel.value = level
        sendRaw(0x06, 0x04, byteArrayOf(level.toByte()))
    }

    // cat=00 op=04 payload=01/00
    suspend fun setGameMode(enabled: Boolean) {
        gameMode.value = enabled
        sendRaw(0x00, 0x04, byteArrayOf(if (enabled) 0x01 else 0x00))
    }

    // cat=01 op=04 payload=01 01 01 01 01/00
    suspend fun setDualConnection(enabled: Boolean) {
        dualConn.value = enabled
        sendRaw(0x01, 0x04, byteArrayOf(0x01, 0x01, 0x01, 0x01, if (enabled) 0x01 else 0x00))
    }
}
