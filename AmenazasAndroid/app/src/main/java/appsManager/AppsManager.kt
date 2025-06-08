package appsManager

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build

class AppsManager {

    fun getInstalledApps(context: Context): List<PackageInfo> {
        val packageManager: PackageManager = context.packageManager

        val apps: List<PackageInfo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(0)) //Android 13
        } else {
            packageManager.getInstalledPackages(0) // Versiones anteriores
        }
        return apps
    }

    fun getUserInstalledApps(context: Context): List<PackageInfo>{
        val packageManager: PackageManager = context.packageManager

        val apps: List<PackageInfo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(0)) //Android 13
        } else {
            packageManager.getInstalledPackages(0) // Versiones anteriores
        }
        return apps.filter { pkgInfo ->
            val isSystemApp = (pkgInfo.applicationInfo!!.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val isUpdatedSystemApp = (pkgInfo.applicationInfo!!.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0

            // Incluye si NO es del sistema o si ha sido actualizada por el usuario
            !isSystemApp || isUpdatedSystemApp
        }
    }

    fun getAppPermissions(context: Context, packageName: String): List<String> {
        val packageManager: PackageManager = context.packageManager
        return try {
            val packageInfo = packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
            packageInfo.requestedPermissions?.toList() ?: emptyList()
        } catch (e: PackageManager.NameNotFoundException) {
            emptyList() // Si no encuentra la app, devuelve lista vacía
        }
    }

    fun getForegroundApps(context: Context): List<String> {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

        // Obtén el tiempo actual y el tiempo de hace 1 minuto (puedes ajustar este intervalo)
        val endTime = System.currentTimeMillis()
        val startTime = endTime - 1000 * 60 // 1 minuto

        // Consulta las estadísticas de uso de las apps en el intervalo
        val usageStatsList = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY, startTime, endTime
        )

        // Filtra las apps que están en primer plano (recientemente usadas)
        val foregroundApps = mutableListOf<String>()
        for (usageStats in usageStatsList) {
            if (usageStats.lastTimeUsed > startTime) {
                foregroundApps.add(usageStats.packageName)
            }
        }

        return foregroundApps
    }

    fun getBackgroundApps(context: Context): List<String> {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

        // Obtén el tiempo actual y el tiempo de hace 1 minuto
        val endTime = System.currentTimeMillis()
        val startTime = endTime - 1000 * 60 // 1 minuto

        // Consulta las estadísticas de uso de las apps en el intervalo
        val usageStatsList = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY, startTime, endTime
        )

        // Filtra las apps que están en segundo plano (no usadas recientemente)
        val backgroundApps = mutableListOf<String>()
        for (usageStats in usageStatsList) {
            if (usageStats.lastTimeUsed <= startTime) {
                backgroundApps.add(usageStats.packageName)
            }
        }

        return backgroundApps
    }



    fun getStartupApps(context: Context): List<String> {
        val packageManager = context.packageManager
        val installedApps = packageManager.getInstalledPackages(PackageManager.GET_PERMISSIONS)
        val startupApps = mutableListOf<String>()

        installedApps.forEach { packageInfo ->
            val permissions = packageInfo.requestedPermissions
            if (permissions?.contains("android.permission.RECEIVE_BOOT_COMPLETED") == true) {
                startupApps.add(packageInfo.packageName)
            }
        }

        return startupApps
    }

    fun getDangerousPermissionApps(context : Context, apps: List<PackageInfo>) : List<Pair<String, List<String>>> {
        val dangerousPermissions = listOf(
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.RECORD_AUDIO,
            android.Manifest.permission.READ_CONTACTS,
            android.Manifest.permission.WRITE_CONTACTS,
            android.Manifest.permission.GET_ACCOUNTS,
            android.Manifest.permission.READ_CALL_LOG,
            android.Manifest.permission.WRITE_CALL_LOG,
            android.Manifest.permission.SEND_SMS,
            android.Manifest.permission.READ_SMS,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
            android.Manifest.permission.READ_EXTERNAL_STORAGE,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE

        )

        val dangerousApps = mutableListOf<Pair<String, List<String>>>()

        for (app in apps){
            val appDangerousPermissions = mutableListOf<String>()

            var permissions = getAppPermissions(context, app.packageName)
            permissions.forEach { permiso ->
                if (dangerousPermissions.contains(permiso)){
                    appDangerousPermissions.add(permiso)
                }
            }

            if (appDangerousPermissions.isNotEmpty()){
                dangerousApps.add(Pair(app.packageName, appDangerousPermissions))
            }
        }
        return dangerousApps
    }
}