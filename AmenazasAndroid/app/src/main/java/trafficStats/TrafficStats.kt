package trafficStats

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.ConnectivityManager
import android.os.RemoteException
import android.util.Log

class TrafficStats(private val context: Context) {

    private fun getNetworkStats(uid: Int, networkType: Int): Long {
        val networkStatsManager = context.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager

        val bucket = NetworkStats.Bucket()
        val endTime = System.currentTimeMillis()
        val startTime = endTime - (1000 * 60 * 60) // Última hora

        try {
            val stats = networkStatsManager.querySummary(networkType, null, startTime, endTime)
            while (stats.hasNextBucket()) {
                stats.getNextBucket(bucket)
                if (bucket.uid == uid) {
                    return bucket.rxBytes + bucket.txBytes // Bytes recibidos + enviados
                }
            }
        } catch (e: RemoteException) {
            Log.e("NetworkUsage", "Error al obtener estadísticas de red", e)
        }

        return 0L
    }

    fun getDataUsageForApp(packageName: String): Long {
        val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
        val uid = appInfo.uid

        val mobileData = getNetworkStats(uid, ConnectivityManager.TYPE_MOBILE)
        val wifiData = getNetworkStats(uid, ConnectivityManager.TYPE_WIFI)

        return mobileData + wifiData // Total de datos usados
    }
}


