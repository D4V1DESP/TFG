package appsManager

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.util.Log
import java.io.*

class ApkFiles {

    fun getAppApkFile(context: Context, packageName: String): File? {
        return try {
            val packageManager: PackageManager = context.packageManager
            val packageInfo: PackageInfo = packageManager.getPackageInfo(packageName, 0)

            val appSourceDir = packageInfo.applicationInfo?.sourceDir
                ?: return null // Si el sourceDir es null, salimos

            val appDestinationFile = File(context.cacheDir, "$packageName.apk")

            FileInputStream(appSourceDir).use { inputStream ->
                FileOutputStream(appDestinationFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            Log.d("APK_FILES", "APK copiado a: ${appDestinationFile.absolutePath}")
            appDestinationFile

        } catch (e: PackageManager.NameNotFoundException) {
            Log.e("APK_FILES", "Aplicación no encontrada: $packageName", e)
            null
        } catch (e: IOException) {
            Log.e("APK_FILES", "Error al copiar APK de $packageName", e)
            null
        }
    }
}
