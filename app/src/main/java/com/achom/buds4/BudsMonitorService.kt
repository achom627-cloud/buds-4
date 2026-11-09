package com.achom.buds4

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder

class BudsMonitorService : Service() {
    private val btReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == BluetoothDevice.ACTION_ACL_CONNECTED) {
                val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                val name = device?.name ?: ""
                if (name.contains("Buds", ignoreCase = true) || name.contains("OnePlus", ignoreCase = true)) {
                    val popup = Intent(context, PopupActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        putExtra("DEVICE_MAC", device?.address)
                    }
                    context.startActivity(popup)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val channelId = "buds_popup_service"
        val channel = NotificationChannel(channelId, "Buds Case Monitor", NotificationManager.IMPORTANCE_MIN)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        val notif = Notification.Builder(this, channelId)
            .setContentTitle("Buds 4 авто-попап активен")
            .setContentText("Жду открытия кейса...")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .build()
        startForeground(1, notif)

        val filter = IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED)
        registerReceiver(btReceiver, filter)
    }

    override fun onDestroy() {
        unregisterReceiver(btReceiver)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
