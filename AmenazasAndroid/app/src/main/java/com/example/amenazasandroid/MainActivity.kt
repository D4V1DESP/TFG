package com.example.amenazasandroid

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
import trafficStats.TrafficStats
import kotlinx.coroutines.*
import virusTotalAPI.VirusTotalHashScanner
import android.content.Context
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
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
            apps = appsManager.getInstalledApps(context) // Obtener aplicaciones y actualizar el estado
            for (app in apps){
                Log.d("APPS", "App Name ${app.packageName}")
                var permissions = appsManager.getAppPermissions(context, app.packageName)
                permissions.forEach { permiso ->
                    Log.d("APPS", "Permiso: $permiso")
                }


                stats = trafficStats.getDataUsageForApp(app.packageName)
                Log.d("TRAFFIC", "${app.packageName}: $stats bytes")
            }

            val foregroundApps = appsManager.getForegroundApps(context)
            val backgroundApps = appsManager.getBackgroundApps(context)
            val startupApps = appsManager.getStartupApps(context)

            Log.d("APPSFOR", "🚀 Apps en Primer Plano: $foregroundApps")
            Log.d("APPSBACK", "📦 Apps en Segundo Plano: $backgroundApps")
            Log.d("APPSSTART", "🔄 Apps de Inicio: $startupApps")

            val dangerousApps = appsManager.getDangerousPermissionApps(context, apps)
            Log.d("APPSDANGER", "Apps peligrosas: $dangerousApps")


            val apiKey = getApiKeyFromConfig(context)
            for (app in dangerousApps){
                val apkFile = ApkFiles().getAppApkFile(context, app.first)
                if (apkFile != null) {
                    VirusTotalHashScanner().scanFileByHash(apkFile.absolutePath, apiKey)
                }
            }

            showApps = true // Mostrar las aplicaciones
        }) {
            Text(("Obtener Aplicaciones"))
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


