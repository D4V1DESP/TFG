package locationAccess

import android.app.AppOpsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log

class LocationThreatDetector(val context: Context) {

    // Convierte duraciones tipo "+4h37m1s639ms" a milisegundos
    fun parseDurationToMillis(duration: String): Long {
        var totalMillis = 0L
        val regex = Regex("""(?:(\d+)h)?(?:(\d+)m)?(?:(\d+)s)?(?:(\d+)ms)?""")
        val match = regex.matchEntire(duration.removePrefix("+"))

        if (match != null) {
            val (hours, minutes, seconds, millis) = match.destructured
            totalMillis += hours.toLongOrNull()?.times(3600_000) ?: 0
            totalMillis += minutes.toLongOrNull()?.times(60_000) ?: 0
            totalMillis += seconds.toLongOrNull()?.times(1000) ?: 0
            totalMillis += millis.toLongOrNull() ?: 0
        }

        return totalMillis
    }


    // Obtiene apps con acceso real a ubicación (usando AppOps)
    fun getAppsWithLocationAccess(): Set<String> {
        val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val packageManager = context.packageManager
        val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        val allowedApps = mutableSetOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            for (app in installedApps) {
                if ((app.flags and ApplicationInfo.FLAG_SYSTEM) != 0) continue

                val packageName = app.packageName
                val fineLocationMode = appOpsManager.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_FINE_LOCATION,
                    android.os.Process.myUid(),
                    packageName
                )
                val coarseLocationMode = appOpsManager.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_COARSE_LOCATION,
                    android.os.Process.myUid(),
                    packageName
                )
                if (fineLocationMode == AppOpsManager.MODE_ALLOWED || fineLocationMode == AppOpsManager.MODE_FOREGROUND ||
                    coarseLocationMode == AppOpsManager.MODE_ALLOWED || coarseLocationMode == AppOpsManager.MODE_FOREGROUND
                ) {
                    allowedApps.add(packageName)
                }
            }
        }
        return allowedApps
    }

    // Detecta amenazas analizando LocationStatEntries
    fun analyzeLocationStats(entries: List<LocationAccess.LocationStatEntry>): List<String> {
        val alerts = mutableListOf<String>()
        val allowedApps = getAppsWithLocationAccess()

        for (entry in entries) {
            val minIntervalMs = parseDurationToMillis(entry.minInterval)
            val totalDurationMs = parseDurationToMillis(entry.totalDuration)

            // Frecuencia alta: menos de 1 segundo entre updates
            if (minIntervalMs < 1000) {
                alerts.add("App ${entry.packageName} solicita ubicación con frecuencia alta (intervalo: ${entry.minInterval})")
            }

            // Uso excesivo: más de 1 hora en total
            if (totalDurationMs > 3600000) {
                alerts.add("App ${entry.packageName} mantiene acceso activo durante mucho tiempo (total: ${entry.totalDuration})")
            }

            // App sin permiso aparente pero con solicitudes
            if (entry.packageName !in allowedApps) {
                alerts.add("App ${entry.packageName} solicita ubicación pero no tiene permiso aparente.")
            }
        }
        return alerts
    }
}
