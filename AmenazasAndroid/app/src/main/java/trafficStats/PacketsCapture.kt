package trafficStats

import android.net.VpnService
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

class PacketsCapture : VpnService() {
    private var vpnInterface: ParcelFileDescriptor? = null
    private val packetCaptureThread = HandlerThread("PacketCaptureThread").apply { start() }
    private val captureHandler = Handler(packetCaptureThread.looper)

    override fun onCreate() {
        super.onCreate()
        startVpn()
    }

    private fun startVpn() {
        val builder = Builder()

        // Asignamos una dirección IP para la interfaz de la VPN
        builder.addAddress("10.0.0.2", 32)

        // Permite que todo el tráfico pase a través de la VPN (captura todo)
        builder.addRoute("0.0.0.0", 0) // Ruta por defecto para todo el tráfico

        // Usamos servidores DNS públicos para la resolución de nombres
        builder.addDnsServer("8.8.8.8")
        builder.addDnsServer("8.8.4.4")

        // Establecer la interfaz de la VPN
        vpnInterface = builder.establish()

        // Si la VPN se establece correctamente, comenzamos a capturar los paquetes
        vpnInterface?.let { fd ->
            val fileInputStream = FileInputStream(fd.fileDescriptor)
            val channel = fileInputStream.channel
            captureHandler.post { processPackets(channel) }
        }
    }

    private fun processPackets(channel: FileChannel) {
        val buffer = ByteBuffer.allocate(32767)  // Tamaño del buffer para capturar paquetes
        while (!Thread.currentThread().isInterrupted) {
            val bytesRead = channel.read(buffer)
            if (bytesRead > 0) {
                buffer.flip() // Establecer el buffer para leer
                logPacket(buffer) // Loggear el paquete
                buffer.clear() // Limpiar el buffer para el siguiente paquete
            }
        }
    }

    private fun logPacket(buffer: ByteBuffer) {
        val timestamp = System.currentTimeMillis()
        Log.d("TrafficCapture", "Packet intercepted at $timestamp: ${buffer.remaining()} bytes")
    }

    override fun onDestroy() {
        super.onDestroy()
        captureHandler.removeCallbacksAndMessages(null)
        packetCaptureThread.quitSafely()
        vpnInterface?.close()  // Cerrar la interfaz de VPN cuando el servicio se detiene
    }
}
