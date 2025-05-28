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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.amenazasandroid.models.AppReport
import com.example.amenazasandroid.models.IssueWithCategory
import com.example.trafficmonitor.PacketsCapture

import locationAccess.LocationAccess
import mobSFAPI.mobSFAPKScanner
import processAnalisis.ProcessAnalisis
import java.util.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.amenazasandroid.models.SharedReportViewModel


class MainActivity : ComponentActivity() {

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val vpnServiceIntent = Intent(this, PacketsCapture::class.java)
            startService(vpnServiceIntent) // Iniciar la VPN
        } else {
            Log.e("VPN", "El usuario denegó el permiso para la VPN")
        }
    }

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
                val navController = rememberNavController()
                val sharedReportViewModel = viewModel<SharedReportViewModel>()

                NavHost(navController = navController, startDestination = Screen.Home.route) {
                    composable(Screen.Home.route) {
                        AppListScreen(navController, sharedReportViewModel)
                    }
                    composable(Screen.AppResults.route) {
                        AppResultsScreen(navController, sharedReportViewModel)
                    }
                    composable(Screen.Apps.route) {
                        AppsScreen(navController, sharedReportViewModel)
                    }
                }
            }
        }
    }

    fun startVpnService() {
        val vpnIntent = VpnService.prepare(this)
        if (vpnIntent != null) {
            vpnPermissionLauncher.launch(vpnIntent) // Ahora se usa correctamente
        } else {
            val vpnServiceIntent = Intent(this, PacketsCapture::class.java)
            startService(vpnServiceIntent)
        }
    }
}

fun getApiKeyFromConfig(context: Context, idApiKey: String): String {
    return try {
        val inputStream = context.assets.open("config.properties")
        val properties = Properties().apply { load(inputStream) }
        properties.getProperty(idApiKey)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }.toString()
}


@Composable
fun AppListScreen(navController: NavHostController, sharedReportViewModel: SharedReportViewModel) {
    var showApps by remember { mutableStateOf(false) }  // Estado para mostrar apps
    var apps by remember { mutableStateOf<List<PackageInfo>>(emptyList()) }  // Estado para almacenar las apps obtenidas
    var issues by remember { mutableStateOf<List<IssueWithCategory>>(emptyList()) }
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
                        //Log.d("APPS", "App Name ${app.packageName}")
                        val permissions = appsManager.getAppPermissions(context, app.packageName)
                        permissions.forEach { permiso ->
                            //Log.d("APPS", "Permiso: $permiso")
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


                    val vTotalApiKey = getApiKeyFromConfig(context, "VIRUSTOTAL_API_KEY")
                    val mobSFApiKey = getApiKeyFromConfig(context, "MOBSF_API_KEY")
                    for (app in dangerousApps){
                        val apkFile = ApkFiles().getAppApkFile(context, app.first)
                        if (apkFile != null) {
                            //VirusTotalHashScanner().scanFileByHash(apkFile.absolutePath, vTotalApiKey)
                            mobSFAPKScanner().analizarAPK(mobSFApiKey, apkFile.absolutePath, context) { jsonReport ->
                                // Parsear JSON recibido y actualizar estado en el hilo principal
                                CoroutineScope(Dispatchers.Main).launch {
                                    try {
                                        val report = Gson().fromJson(jsonReport, AppReport::class.java)
                                        sharedReportViewModel.report = report
                                    }catch (e: Exception){
                                        Log.e("mobSF", "❌ Error al parsear JSON: ${e.message}")
                                    }
                                }
                            }
                        }
                    }


                    var locationAccess = LocationAccess()
                    var recentLocationApps = locationAccess.getRecentLocationAccessApps(context)
                    for ((app, accessType) in recentLocationApps){
                        Log.d("Location", "App: $app - $accessType")
                    }

                    var processAnalisis = ProcessAnalisis()
                    processAnalisis.getAllProcesses()//Obtener los procesos que se ejecutan el el dispositivo desde ADB

                    locationAccess.getLocationStats()//Obtener datos sobre la ubicación desde ADB

                }
                navController.navigate(Screen.AppResults.route)
            }

            showApps = true // Mostrar las aplicaciones
        }) {
            Text(("Obtener Aplicaciones"))
        }
        Spacer(modifier = Modifier.height(16.dp))

        Column(modifier = Modifier.padding(16.dp)) {
            Button(onClick = {
                // Parsear JSON y preparar lista de issues
                val gson = Gson()



                showApps = true
            }) {
                Text("Mostrar Issues de Seguridad")
            }

            Spacer(modifier = Modifier.height(16.dp))


        }

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

@Composable
fun AppResultsScreen(navController: NavHostController, viewModel: SharedReportViewModel = viewModel()) {
    Column(modifier = Modifier.padding(16.dp)) {
        Button(onClick = { navController.navigate(Screen.Apps.route) }) {
            Text("Apps")
        }
        Spacer(modifier = Modifier.height(8.dp))

        Button(onClick = { navController.navigate(Screen.Traffic.route) }) {
            Text("Tráfico")
        }
        Spacer(modifier = Modifier.height(8.dp))

        Button(onClick = { navController.navigate(Screen.Location.route) }) {
            Text("Ubicación")
        }
        Spacer(modifier = Modifier.height(8.dp))

        Button(onClick = { navController.navigate(Screen.Permissions.route) }) {
            Text("Permisos")
        }
    }
}

@Composable
fun AppsScreen(navController: NavHostController, sharedReportViewModel: SharedReportViewModel) {
    var expandedAppNames by remember { mutableStateOf(setOf<String>()) }
    var expandedCategories by remember { mutableStateOf(setOf<String>()) } // Estado para categorías expandidas
    val report = sharedReportViewModel.report
    if (report == null) {
        Log.d("REPORTES", "$report")
    }

    val categorizedIssues = mapOf(
        "High" to report!!.high,
        "Warning" to report.warning,
        "Info" to report.info,
        "Secure" to report.secure,
        "Hotspot" to report.hotspot
    )

    Column(modifier = Modifier.padding(16.dp)) {
        Button(onClick = { navController.popBackStack() }) {
            Text("⬅ Volver")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("Reportes de Seguridad por App", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn {
            item {
                val appName = report.app_name
                Column {
                    // Fila principal con nombre + datos
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                            .clickable {
                                expandedAppNames = if (expandedAppNames.contains(appName)) {
                                    expandedAppNames - appName
                                } else {
                                    expandedAppNames + appName
                                }
                            },
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(appName, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Security Score: ${report.security_score} | Trackers: ${report.trackers}/${report.total_trackers}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Icon(
                            imageVector = if (expandedAppNames.contains(appName)) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null
                        )
                    }

                    // Detalles desplegables por categoría con plegado individual
                    if (expandedAppNames.contains(appName)) {
                        categorizedIssues.forEach { (category, issues) ->
                            if (issues.isNotEmpty()) {
                                Column {
                                    // Título de categoría clicable para expandir/plegar
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                expandedCategories = if (expandedCategories.contains(category)) {
                                                    expandedCategories - category
                                                } else {
                                                    expandedCategories + category
                                                }
                                            }
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = category,
                                            style = MaterialTheme.typography.titleSmall.copy(color = MaterialTheme.colorScheme.primary)
                                        )
                                        Icon(
                                            imageVector = if (expandedCategories.contains(category)) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                            contentDescription = null
                                        )
                                    }

                                    if (expandedCategories.contains(category)) {
                                        issues.forEach { issue ->
                                            Column(modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)) {
                                                Text(issue.title, style = MaterialTheme.typography.titleSmall)
                                                Text(issue.description, style = MaterialTheme.typography.bodySmall)
                                                Divider(modifier = Modifier.padding(vertical = 4.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
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

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object AppResults : Screen("app_results")
    object Apps : Screen("apps")
    object Traffic : Screen("traffic")
    object Location : Screen("location")
    object Permissions : Screen("permissions")
}

data class AppReport(
    val high: List<Finding> = emptyList(),
    val warning: List<Finding> = emptyList(),
    val info: List<Finding> = emptyList(),
    val secure: List<Finding> = emptyList(),
    val hotspot: List<Finding> = emptyList(),
    val total_trackers: Int = 0,
    val trackers: Int = 0,
    val security_score: Int = 0,
    val app_name: String = "",
    val file_name: String = "",
    val hash: String = "",
    val version_name: String = "",
    val version: String = "",
    val title: String = "",
    val efr01: Boolean = false
)

data class Finding(
    val title: String = "",
    val description: String = "",
    val section: String = ""
)



