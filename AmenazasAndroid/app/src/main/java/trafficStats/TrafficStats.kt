package trafficStats

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.ConnectivityManager
import android.os.RemoteException
import android.util.Log

class TrafficStats(private val context: Context) {

    private fun getNetworkStats(uid: Int, networkType: Int): Pair<Long, Long> {
        val networkStatsManager = context.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager

        val bucket = NetworkStats.Bucket()
        val endTime = System.currentTimeMillis()
        val startTime = endTime - (1000 * 60 * 60 * 24) // Última hora
        var rxBytes = 0L
        var txBytes = 0L

        try {
            val stats = networkStatsManager.querySummary(networkType, null, startTime, endTime)
            while (stats.hasNextBucket()) {
                stats.getNextBucket(bucket)
                if (bucket.uid == uid) {
                    rxBytes += bucket.rxBytes //Bytes recibidos
                    txBytes += bucket.txBytes //Bytes enviados
                }
            }
        } catch (e: RemoteException) {
            Log.e("NetworkUsage", "Error al obtener estadísticas de red", e)
        }

        return Pair(rxBytes, txBytes)
    }

    fun getDataUsageForApp(packageName: String): Pair<Long, Long> {
        return try {
            val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
            val uid = appInfo.uid

            val (mobileRx, mobileTx) = getNetworkStats(uid, ConnectivityManager.TYPE_MOBILE)
            val (wifiRx, wifiTx) = getNetworkStats(uid, ConnectivityManager.TYPE_WIFI)

            val totalRx = mobileRx + wifiRx // Total de datos recibidos
            val totalTx = mobileTx + wifiTx // Total de datos enviados

            Pair(totalRx, totalTx)
        } catch (e: Exception) {
            Log.e("NetworkUsage", "Error al obtener el uso de datos para la app", e)
            Pair(0L, 0L)
        }
    }


}


