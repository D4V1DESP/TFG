package com.example.amenazasandroid

import trafficStats.TrafficStats
import android.content.Intent
import android.content.pm.PackageInfo
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.amenazasandroid.ui.theme.AmenazasAndroidTheme
import appsManager.AppsManager
import android.provider.Settings
import appsManager.ApkFiles
import kotlinx.coroutines.*
import android.content.Context
import android.net.Uri
import android.net.VpnService
import locationAccess.LocationAccess
import trafficStats.PacketsCapture
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        var intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        startActivity(intent)

        intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)

        setContent {
            AmenazasAndroidTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppListScreen()  // Muestra la lista de apps
                }
            }
        }
    }

    fun startVpnService() {
        val vpnIntent = VpnService.prepare(this)

        if (vpnIntent != null) {
            // If permission not granted, ask the user for permission
            startActivityForResult(vpnIntent, 0)
        } else {
            // If permission already granted, start the VPN service directly
            val vpnServiceIntent = Intent(this, PacketsCapture::class.java)
            startService(vpnServiceIntent)
        }
    }
}

fun getApiKeyFromConfig(context: Context): String {
    return try {
        val inputStream = context.assets.open("config.properties")
        val properties = Properties().apply { load(inputStream) }
        properties.getProperty("VIRUSTOTAL_API_KEY")
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }.toString()
}


@Composable
fun AppListScreen() {
    var showApps by remember { mutableStateOf(false) }  // Estado para mostrar apps
    var apps by remember { mutableStateOf<List<PackageInfo>>(emptyList()) }  // Estado para almacenar las apps obtenidas
    var stats : Long
    var appsManager = AppsManager()

    var apkFiles = ApkFiles()
    val scope = CoroutineScope(Dispatchers.IO)

    var context = LocalContext.current
    var trafficStats = TrafficStats(context)
    Column(modifier = Modifier.padding(16.dp)) {
        Button(onClick =  {
            CoroutineScope(Dispatchers.Main).launch {
                // Iniciar corutina en IO thread
                withContext(Dispatchers.IO) {
                    val apps = appsManager.getInstalledApps(context) // Obtener aplicaciones en segundo plano
                    for (app in apps) {
                        Log.d("APPS", "App Name ${app.packageName}")
                        val permissions = appsManager.getAppPermissions(context, app.packageName)
                        permissions.forEach { permiso ->
                            Log.d("APPS", "Permiso: $permiso")
                        }

                        val (received, sent) = trafficStats.getDataUsageForApp(app.packageName)
                        if (received != 0L || sent != 0L) {
                            Log.d("TRAFFIC", "UID for ${app.packageName}: ${context.packageManager.getApplicationInfo(app.packageName, 0).uid}")
                            Log.d("TRAFFIC", "${app.packageName}: Received $received bytes, Sent $sent bytes")
                        }
                    }

                    // Otros procesos
                    val foregroundApps = appsManager.getForegroundApps(context)
                    val backgroundApps = appsManager.getBackgroundApps(context)
                    val startupApps = appsManager.getStartupApps(context)

                    Log.d("APPSFOR", "🚀 Apps en Primer Plano: $foregroundApps")
                    Log.d("APPSBACK", "📦 Apps en Segundo Plano: $backgroundApps")
                    Log.d("APPSSTART", "🔄 Apps de Inicio: $startupApps")


                    val dangerousApps = appsManager.getDangerousPermissionApps(context, apps)
                    Log.d("APPSDANGER", "Apps peligrosas: $dangerousApps")


                    val apiKey = getApiKeyFromConfig(context)
                    /*for (app in dangerousApps){
                        val apkFile = ApkFiles().getAppApkFile(context, app.first)
                        if (apkFile != null) {
                            VirusTotalHashScanner().scanFileByHash(apkFile.absolutePath, apiKey)
                        }
                    }*/

                    var locationAccess = LocationAccess()
                    var locationApps = locationAccess.getAppsAndLocationType(context)
                    for ((app, locationType) in locationApps){
                        Log.d("LOCATION", "La app $app accedió a la ubicación usando $locationType")
                    }
                }
            }


            showApps = true // Mostrar las aplicaciones
        }) {
            Text(("Obtener Aplicaciones"))
        }
        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            // Call to start the VPN service
            (context as? MainActivity)?.startVpnService() // Make sure to start the service
        }) {
            Text("Iniciar Captura de Paquetes")
        }

        /*Spacer(modifier = Modifier.height(16.dp))
        if (showApps) {
            LazyColumn {
                items(apps) { app ->
                    // Aquí extraemos el nombre del paquete y su nombre de la aplicación
                    val appName = app.applicationInfo?.loadLabel(LocalContext.current.packageManager).toString()
                    val packageName = app.packageName

                    // Mostramos el nombre y el paquete de la aplicación
                    Text(
                        text = "$appName - $packageName",
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }*/
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewAppListScreen() {
    AmenazasAndroidTheme {
        var showApps by remember { mutableStateOf(true) } // Forzar la lista visible en preview

        Column(modifier = Modifier.padding(16.dp)) {
            Button(onClick = { /* No hace nada en preview */ }) {
                Text("Obtener Aplicaciones")
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (showApps) { // La lista siempre se muestra en la preview
                LazyColumn {
                    items(listOf("com.android.bluetooth", "Instagram", "Facebook", "Telegram")) { app ->
                        Text(text = app, modifier = Modifier.padding(8.dp))
                    }
                }
            }
        }
    }
}


