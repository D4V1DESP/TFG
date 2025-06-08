package locationAccess

import android.app.AppOpsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

class LocationAccess {

    fun runCommand(command: String): String {
        try {
            val process = Runtime.getRuntime().exec(command)
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            process.waitFor()
            return output.toString()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return ""
    }

    fun getLocationStats(context: Context): List<LocationStatEntry> {
        val isRooted = checkRoot()
        var stats = emptyList<LocationStatEntry>()
        if (isRooted) {
            val command = "su -c dumpsys location"  // Obtiene datos sobre la ubicación
            val result = runCommand(command)
            Log.d("LOCATION_STATS", "$result")

            stats = parseLocationStats(result, context)


            /*for (entry in stats) {
                Log.d("LOCATION_STATS","Provider: ${entry.provider}")
                Log.d("LOCATION_STATS","App: ${entry.packageName}")
                Log.d("LOCATION_STATS","UID: ${entry.uid}")
                Log.d("LOCATION_STATS","Min Interval: ${entry.minInterval}")
                Log.d("LOCATION_STATS","Max Interval: ${entry.maxInterval}")
                Log.d("LOCATION_STATS","Total Duration: ${entry.totalDuration}")
                Log.d("LOCATION_STATS","Active Duration: ${entry.activeDuration}")
                Log.d("LOCATION_STATS","Foreground Duration: ${entry.foregroundDuration}")
                Log.d("LOCATION_STATS","Locations: ${entry.locations}")
                entry.threats.forEach { threat ->
                    Log.d("LOCATION_STATS", "- $threat")
                }
                Log.d("LOCATION_STATS","-----")
            }*/

        } else {
            Log.d("LOCATION_STATS", "El dispositivo no tiene acceso root.")
        }
        return stats
    }

    fun checkRoot(): Boolean {
        val paths = arrayOf("/system/bin/su", "/system/xbin/su", "/data/local/bin/su", "/data/local/xbin/su")
        for (path in paths) {
            if (File(path).exists()) {
                return true
            }
        }
        return false
    }


    fun getActiveLocationProvider(context: Context): String {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return when {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> "GPS"
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> "Triangulación (WiFi/Red móvil)"
            else -> "Ubicación desactivada"
        }
    }

    fun getRecentLocationAccessApps(context: Context): Map<String, String> {
        val result = mutableMapOf<String, String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val packageManager = context.packageManager
            val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)

            for (app in installedApps) {
                if ((app.flags and ApplicationInfo.FLAG_SYSTEM) != 0) {
                    continue
                }

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

                if (fineLocationMode == AppOpsManager.MODE_ALLOWED || fineLocationMode == AppOpsManager.MODE_FOREGROUND) {
                    result[packageName] = "Accedió a ubicación precisa"
                } else if (coarseLocationMode == AppOpsManager.MODE_ALLOWED || coarseLocationMode == AppOpsManager.MODE_FOREGROUND) {
                    result[packageName] = "Accedió a ubicación aproximada"
                }
            }
        }

        return result
    }

    data class LocationStatEntry(
        val provider: String,
        val uid: String,
        val packageName: String,
        val minInterval: String,
        val maxInterval: String,
        val totalDuration: String,
        val activeDuration: String,
        val foregroundDuration: String,
        val locations: Int,
        val threats: List<String> = emptyList()
    )

    fun parseLocationStats(log: String, context: Context): List<LocationStatEntry> {
        val entries = mutableListOf<LocationStatEntry>()

        val providerRegex = Regex("""(gps|network|passive):""")
        val appDataRegex = Regex(
            """(\d+)/([\w\.\[\]]+):\s+min/max interval = (\S+)/(\S+),\s+total/active/foreground duration = \+([\dhms]+[\dms]*?)/\+([\dhms]+[\dms]*?)/\+([\dhms]+[\dms]*?),\s+locations = (\d+)"""
        )

        val providerBlocks = providerRegex.findAll(log).map { it.range.first }.toList() + log.length
        for (i in 0 until providerBlocks.size - 1) {
            val start = providerBlocks[i]
            val end = providerBlocks[i + 1]
            val providerBlock = log.substring(start, end)

            val provider = providerRegex.find(providerBlock)?.groupValues?.get(1) ?: continue

            for (match in appDataRegex.findAll(providerBlock)) {
                val (uid, pkg, minInt, maxInt, total, active, foreground, locs) = match.destructured

                val tempEntry = mutableListOf<LocationStatEntry>()
                tempEntry.add(
                    LocationStatEntry(
                        provider,
                        uid,
                        pkg,
                        minInt,
                        maxInt,
                        total,
                        active,
                        foreground,
                        locs.toInt()
                    )
                )
                val detector = LocationThreatDetector(context)
                val threats = detector.analyzeLocationStats(tempEntry)
                entries.add(
                    LocationStatEntry(
                        provider,
                        uid,
                        pkg,
                        minInt,
                        maxInt,
                        total,
                        active,
                        foreground,
                        locs.toInt(),
                        threats = threats
                    )
                )
            }
        }

        return entries
    }



}