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
        val protocol: String,
        val ipVersion: String // Nuevo campo para distinguir IPv4/IPv6
    )

    // Función para parsear IPv4 (original)
    private fun parseIpv4(hexIp: String): String =
        hexIp.chunked(2).reversed().joinToString(".") { it.toInt(16).toString() }

    // Nueva función para parsear IPv6
    private fun parseIpv6(hexIp: String): String {
        // Verificar si es IPv4 mapeada en IPv6 (formato: 0000000000000000FFFF0000BC857D4A)
        if (hexIp.length == 32 && hexIp.startsWith("0000000000000000FFFF0000")) {
            // Extraer los últimos 8 caracteres que representan la IPv4
            val ipv4Hex = hexIp.takeLast(8)
            return parseIpv4(ipv4Hex)
        }

        // IPv6 en /proc/net está en formato little-endian, necesitamos revertir cada grupo de 4 bytes
        val groups = hexIp.chunked(8) // Dividir en grupos de 8 caracteres (4 bytes)
        val reversedGroups = groups.map { group ->
            // Revertir cada grupo de 4 bytes (8 caracteres hex)
            group.chunked(2).reversed().joinToString("")
        }

        // Convertir a formato IPv6 estándar
        val ipv6Parts = reversedGroups.map { group ->
            // Dividir en grupos de 4 caracteres para formato IPv6
            listOf(group.substring(0, 4), group.substring(4, 8))
        }.flatten()

        // Unir con ':' y simplificar (remover ceros leading)
        val fullAddress = ipv6Parts.joinToString(":") {
            it.toInt(16).toString(16)
        }

        return simplifyIpv6(fullAddress)
    }

    // Función para simplificar direcciones IPv6 (remover ceros innecesarios)
    private fun simplifyIpv6(ipv6: String): String {
        val parts = ipv6.split(":")
        val simplified = parts.map { part ->
            part.toInt(16).toString(16)
        }

        // Buscar la secuencia más larga de ceros para reemplazar con ::
        var longestZeroStart = -1
        var longestZeroLength = 0
        var currentZeroStart = -1
        var currentZeroLength = 0

        for (i in simplified.indices) {
            if (simplified[i] == "0") {
                if (currentZeroStart == -1) {
                    currentZeroStart = i
                    currentZeroLength = 1
                } else {
                    currentZeroLength++
                }
            } else {
                if (currentZeroLength > longestZeroLength) {
                    longestZeroStart = currentZeroStart
                    longestZeroLength = currentZeroLength
                }
                currentZeroStart = -1
                currentZeroLength = 0
            }
        }

        // Verificar la última secuencia
        if (currentZeroLength > longestZeroLength) {
            longestZeroStart = currentZeroStart
            longestZeroLength = currentZeroLength
        }

        // Solo comprimir si hay al menos 2 ceros consecutivos
        if (longestZeroLength >= 2) {
            val before = simplified.subList(0, longestZeroStart)
            val after = simplified.subList(longestZeroStart + longestZeroLength, simplified.size)

            return when {
                before.isEmpty() && after.isEmpty() -> "::"
                before.isEmpty() -> "::" + after.joinToString(":")
                after.isEmpty() -> before.joinToString(":") + "::"
                else -> before.joinToString(":") + "::" + after.joinToString(":")
            }
        }

        return simplified.joinToString(":")
    }

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

    // Nueva función para verificar si una IP es privada/loopback/local
    private fun isPrivateOrLocalIp(ip: String, ipVersion: String): Boolean {
        return when (ipVersion) {
            "IPv4" -> {
                when {
                    ip == "0.0.0.0" -> true  // Dirección nula
                    ip.startsWith("127.") -> true  // Loopback
                    ip.startsWith("10.") -> true  // Rango privado Clase A
                    ip.startsWith("192.168.") -> true  // Rango privado Clase C
                    ip.startsWith("172.") -> {
                        val secondOctet = ip.split(".").getOrNull(1)?.toIntOrNull() ?: 0
                        secondOctet in 16..31
                    }
                    ip.startsWith("169.254.") -> true  // Link-local (APIPA)
                    else -> false
                }
            }
            "IPv6" -> {
                when {
                    ip == "::" -> true  // Dirección nula IPv6
                    ip == "::1" -> true  // Loopback IPv6
                    ip.startsWith("fe80:") -> true  // Link-local IPv6
                    ip.startsWith("fc00:") || ip.startsWith("fd00:") -> true  // Unique local IPv6
                    ip.startsWith("ff00:") -> true  // Multicast IPv6
                    else -> false
                }
            }
            else -> false
        }
    }


    // Función mejorada para validar IPv4
fun isValidIPv4(ip: String): Boolean {
    return try {
        val parts = ip.split(".")
        if (parts.size != 4) return false
        parts.all {
            val num = it.toInt()
            num in 0..255
        }
    } catch (e: Exception) {
        false
    }
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
        "/proc/net/tcp" to Pair("TCP", "IPv4"),
        "/proc/net/udp" to Pair("UDP", "IPv4"),
        "/proc/net/tcp6" to Pair("TCP", "IPv6"),  // Nuevo: TCP IPv6
        "/proc/net/udp6" to Pair("UDP", "IPv6")   // Nuevo: UDP IPv6
    )

    for ((file, protocolInfo) in files) {
        val (protocol, ipVersion) = protocolInfo
        val lines = readProcFile(file).drop(1)

        for (line in lines) {
            val parts = line.trim().split(Regex("\\s+"))
            if (parts.size < 10) continue

            val local = parts[1].split(":")
            val remote = parts[2].split(":")
            val stateHex = parts[3]
            val uid = parts[7].toIntOrNull() ?: continue

            // Parsear IP según la versión
            val (localIp, remoteIp, finalIpVersion) = if (ipVersion == "IPv6") {
                val localIpv6 = parseIpv6(local[0])
                val remoteIpv6 = parseIpv6(remote[0])

                // Si el parseo IPv6 devolvió una IPv4 (mapeada), cambiar la versión
                val actualVersion = if (isValidIPv4(remoteIpv6)) "IPv4" else "IPv6"
                Triple(localIpv6, remoteIpv6, actualVersion)
            } else {
                val localIpv4 = parseIpv4(local[0])
                val remoteIpv4 = parseIpv4(remote[0])
                Triple(localIpv4, remoteIpv4, "IPv4")
            }

            // Filtrar IPs privadas/locales
            if (isPrivateOrLocalIp(remoteIp, finalIpVersion)) {
                continue
            }

            val localPort = parsePort(local[1])
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
                    protocol,
                    finalIpVersion  // Usar la versión final determinada
                )
            )
        }
    }

    return connections
}

private fun readProcFile(path: String): List<String> {
    return try {
        val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "cat $path"))
        process.inputStream.bufferedReader().readLines()
    } catch (e: Exception) {
        emptyList() // Retornar lista vacía si hay error (ej: archivo no existe)
    }
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