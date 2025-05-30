package trafficStats

import android.content.Context
import android.content.pm.PackageManager
import android.net.TrafficStats
import kotlinx.coroutines.delay
import java.net.InetAddress

class Connections {

    data class ConnectionInfo(
        val localIp: String,
        val localPort: Int,
        val remoteIp: String,
        val remotePort: Int,
        val remoteHost: String?,
        val state: String,
        val uid: Int,
        val packageName: String?,
        val rxBytes: Long?,
        val txBytes: Long?,
        val protocol: String
    )

    private fun parseIp(hexIp: String): String =
        hexIp.chunked(2).reversed().joinToString(".") { it.toInt(16).toString() }

    private fun parsePort(hexPort: String): Int = hexPort.toInt(16)

    private fun parseState(hexState: String): String = when (hexState) {
        "01" -> "ESTABLISHED"; "02" -> "SYN_SENT"; "03" -> "SYN_RECV"; "04" -> "FIN_WAIT1"
        "05" -> "FIN_WAIT2"; "06" -> "TIME_WAIT"; "07" -> "CLOSE"; "08" -> "CLOSE_WAIT"
        "09" -> "LAST_ACK"; "0A" -> "LISTEN"; "0B" -> "CLOSING"; else -> "UNKNOWN"
    }

    private fun resolveHost(ip: String): String? = try {
        val addr = InetAddress.getByName(ip)
        val host = addr.hostName
        if (host == ip) null else host
    } catch (e: Exception) {
        null
    }

    fun getPackageNameFromUid(context: Context, uid: Int): String? {
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        for (app in apps) {
            if (app.uid == uid) return app.packageName
        }
        return null
    }

    fun getAllActiveConnections(context: Context): List<ConnectionInfo> {
        val connections = mutableListOf<ConnectionInfo>()
        val files = listOf(
            "/proc/net/tcp" to "TCP",
            "/proc/net/udp" to "UDP",
        )

        for ((file, protocol) in files) {
            val lines = readProcFile(file).drop(1)

            for (line in lines) {
                val parts = line.trim().split(Regex("\\s+"))
                if (parts.size < 10) continue

                val local = parts[1].split(":")
                val remote = parts[2].split(":")
                val stateHex = parts[3]
                val uid = parts[7].toIntOrNull() ?: continue

                val localIp = parseIp(local[0])
                val localPort = parsePort(local[1])
                val remoteIp = parseIp(remote[0])
                val remotePort = parsePort(remote[1])
                val remoteHost = resolveHost(remoteIp)

                val rxBytes = TrafficStats.getUidRxBytes(uid).takeIf { it >= 0 }
                val txBytes = TrafficStats.getUidTxBytes(uid).takeIf { it >= 0 }

                val packageName = getPackageNameFromUid(context, uid)
                val state = if (protocol == "TCP") parseState(stateHex) else "UNSPECIFIED"

                connections.add(
                    ConnectionInfo(
                        localIp, localPort,
                        remoteIp, remotePort,
                        remoteHost, state,
                        uid, packageName,
                        rxBytes, txBytes,
                        protocol
                    )
                )
            }
        }

        return connections
    }

    private fun readProcFile(path: String): List<String> {
        val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "cat $path"))
        return process.inputStream.bufferedReader().readLines()
    }

    suspend fun logTrafficDeltas(context: Context, intervalMs: Long = 5000L) {
        val initialStats = snapshotUidStats(context)
        delay(intervalMs)
        val updatedStats = snapshotUidStats(context)

        for ((uid, oldStats) in initialStats) {
            val newStats = updatedStats[uid] ?: continue
            val deltaRx = newStats.first - oldStats.first
            val deltaTx = newStats.second - oldStats.second

            if (deltaRx > 0 || deltaTx > 0) {
                val name = getPackageNameFromUid(context, uid) ?: "unknown"
                println("📦 $name → ΔRx: $deltaRx B, ΔTx: $deltaTx B")
            }
        }
    }

    private fun snapshotUidStats(context: Context): Map<Int, Pair<Long, Long>> {
        val conns = getAllActiveConnections(context)
        return conns.map { it.uid }.distinct().associateWith { uid ->
            val rx = TrafficStats.getUidRxBytes(uid).takeIf { it >= 0 } ?: 0L
            val tx = TrafficStats.getUidTxBytes(uid).takeIf { it >= 0 } ?: 0L
            Pair(rx, tx)
        }
    }
}
