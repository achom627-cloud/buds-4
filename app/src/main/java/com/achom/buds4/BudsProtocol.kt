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

data class BudsBattery(val left: Int = 100, val right: Int = 100, val case: Int? = null)

object BudsProtocol {
    private var socket: BluetoothSocket? = null
    private var outStream: OutputStream? = null
    private var inStream: InputStream? = null
    private var seq: Byte = 1

    val connected = MutableStateFlow(false)
    val deviceName = MutableStateFlow("OnePlus Buds 4")
    // Из свежего лога: 4 = Шумодав/Адаптив, 2 = Прозрачность, 1 = Выкл
    val ancMode = MutableStateFlow(4)
    // Эквалайзер (cat=06 op=04): 0 = Сбалансированный, 1 = Серенада, 2 = Басы
    val eqMode = MutableStateFlow(0)
    // Поиск наушников (cat=00 op=04): true = пищит (01), false = выкл (00)
    val findMyBuds = MutableStateFlow(false)
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

            // Handshake из лога (#828): cat=0f op=04 payload=01
            sendRaw(0x0F, 0x04, byteArrayOf(0x01))
            // Запрос текущего пресета эквалайзера (#1798): cat=0f op=01
            sendRaw(0x0F, 0x01, byteArrayOf())
            listenLoop()
            true
        } catch (e: Exception) {
            connected.value = false
            false
        }
    }

    private fun listenLoop() {
        Thread {
            val buf = ByteArray(2048)
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
            // Пропускаем varint длину (1 или 2 байта)
            var j = i + 1
            var len = 0
            var shift = 0
            while (j < data.size) {
                val b = data[j].toInt() and 0xFF
                j++
                len = len or ((b and 0x7F) shl shift)
                if ((b and 0x80) == 0) break
                shift += 7
            }
            if (j + len > data.size || len < 7) break
            val cat = data[j + 2].toInt() and 0xFF
            val op = data[j + 3].toInt() and 0xFF
            val plen = (data[j + 5].toInt() and 0xFF) or ((data[j + 6].toInt() and 0xFF) shl 8)
            val payloadStart = j + 7
            if (payloadStart + plen <= data.size) {
                val p = data.copyOfRange(payloadStart, payloadStart + plen)
                handlePacket(cat, op, p)
            }
            i = j + len
        }
    }

    private fun handlePacket(cat: Int, op: Int, p: ByteArray) {
        // 1. Уведомление батареи (#840, #916): cat=04 op=02 | 01 02 01 64 02 64 (или 01 03 01 LL 02 RR 03 CC)
        if (cat == 0x04 && op == 0x02 && p.size >= 6 && p[0] == 0x01.toByte()) {
            val count = p[1].toInt() and 0xFF
            var l = battery.value.left
            var r = battery.value.right
            var c = battery.value.case
            var idx = 2
            for (k in 0 until count) {
                if (idx + 1 < p.size) {
                    val id = p[idx].toInt() and 0xFF
                    val v = (p[idx + 1].toInt() and 0xFF).coerceIn(0, 100)
                    if (v > 0) {
                        when (id) {
                            1 -> l = v
                            2 -> r = v
                            3 -> c = v
                        }
                    }
                    idx += 2
                }
            }
            battery.value = BudsBattery(l, r, c)
        }

        // 2. Уведомление смены режима с наушников (#943, #996, #1030): cat=04 op=02 | 03 01 01 XX YY
        if (cat == 0x04 && op == 0x02 && p.size >= 5 && p[0] == 0x03.toByte() && p[1] == 0x01.toByte() && p[2] == 0x01.toByte()) {
            val b3 = p[3].toInt() and 0xFF
            val b4 = p[4].toInt() and 0xFF
            when {
                b3 == 0x08 && b4 == 0x00 -> ancMode.value = 1 // Выкл
                b3 == 0x10 && b4 == 0x00 -> ancMode.value = 2 // Прозрачность
                b4 == 0x01 -> ancMode.value = 4               // Шумодав / Адаптив
            }
        }

        // 3. Уведомление эквалайзера (#1795, #1826, #1851): cat=04 op=05 | XX
        if (cat == 0x04 && op == 0x05 && p.isNotEmpty()) {
            eqMode.value = (p[0].toInt() and 0xFF).coerceIn(0, 2)
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

    // 1 = Выкл, 4 = Шумодав, 2 = Прозрачность
    suspend fun setAncMode(mode: Int) {
        ancMode.value = mode
        sendRaw(0x04, 0x04, byteArrayOf(0x01, 0x01, mode.toByte()))
    }

    // 0 = Сбалансированный, 1 = Серенада, 2 = Басы
    suspend fun setEqMode(preset: Int) {
        eqMode.value = preset
        sendRaw(0x06, 0x04, byteArrayOf(preset.toByte()))
    }

    // true = 01 (вкл пищалку), false = 00 (выкл пищалку)
    suspend fun setFindMyBuds(finding: Boolean) {
        findMyBuds.value = finding
        sendRaw(0x00, 0x04, byteArrayOf(if (finding) 0x01 else 0x00))
    }
}
