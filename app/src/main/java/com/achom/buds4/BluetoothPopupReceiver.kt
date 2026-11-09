package com.achom.buds4

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BluetoothPopupReceiver : BroadcastReceiver() {
    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == BluetoothDevice.ACTION_ACL_CONNECTED) {
            val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            val name = device?.name ?: ""
            if (name.contains("Buds", ignoreCase = true) || name.contains("OnePlus", ignoreCase = true)) {
                val popupIntent = Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra("POPUP_MODE", true)
                    putExtra("DEVICE_MAC", device?.address)
                }
                context.startActivity(popupIntent)
            }
        }
    }
}
