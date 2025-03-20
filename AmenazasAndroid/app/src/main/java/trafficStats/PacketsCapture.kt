package com.example.trafficmonitor

import android.content.Intent
import android.net.VpnService
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.FileInputStream
import java.nio.ByteBuffer

class PacketsCapture : VpnService() {
    private var vpnInterface: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val builder = Builder()
        builder.setSession("PacketMonitor")
            .addAddress("192.168.0.1", 24) // Asignar una IP válida
            .addDnsServer("8.8.8.8")
            .addDnsServer("8.8.4.4")
            .addRoute("0.0.0.0", 0)

        vpnInterface = builder.establish()

        vpnInterface?.let {
            Thread { captureTraffic(it) }.start()
        }

        return START_STICKY
    }

    private fun captureTraffic(tunInterface: ParcelFileDescriptor) {
        val inputStream = FileInputStream(tunInterface.fileDescriptor)
        val buffer = ByteBuffer.allocate(65535)

        while (true) {
            val length = inputStream.read(buffer.array())
            if (length > 0) {
                Log.d("TrafficMonitor", "Captured Packet Length: $length")
                extractIpHeader(buffer.array(), length)
            }
            buffer.clear()
        }
    }

    private fun extractIpHeader(packet: ByteArray, length: Int) {
        if (length < 20) return

        val sourceIp = "${packet[12].toInt() and 0xFF}.${packet[13].toInt() and 0xFF}.${packet[14].toInt() and 0xFF}.${packet[15].toInt() and 0xFF}"
        val destIp = "${packet[16].toInt() and 0xFF}.${packet[17].toInt() and 0xFF}.${packet[18].toInt() and 0xFF}.${packet[19].toInt() and 0xFF}"

        Log.d("TrafficMonitor", "IP Origen: $sourceIp -> IP Destino: $destIp")
    }

    override fun onDestroy() {
        super.onDestroy()
        vpnInterface?.close()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
