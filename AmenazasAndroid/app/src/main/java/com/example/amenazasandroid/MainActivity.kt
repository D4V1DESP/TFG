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
import kotlinx.coroutines.*
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
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
import com.example.amenazasandroid.models.SharedConnectionViewModel

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Android
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.example.amenazasandroid.abuseIPDBAPI.AbuseIPChecker
import org.json.JSONObject
import trafficStats.Connections
import trafficStats.TrafficStats


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
                val sharedConnectionViewModel = viewModel<SharedConnectionViewModel>()

                NavHost(navController = navController, startDestination = Screen.Home.route) {
                    composable(Screen.Home.route) {
                        AppListScreen(navController, sharedReportViewModel, sharedConnectionViewModel)
                    }
                    composable(Screen.AppResults.route) { //Muestra los 4 botones para ver los resultados (Apps, Tráfico, Ubicación, Permisos)
                        AppResultsScreen(navController, sharedReportViewModel)
                    }
                    composable(Screen.Apps.route) {
                        AppsScreen(navController, sharedReportViewModel)
                    }
                    composable(Screen.Traffic.route) {
                        TrafficScreen(navController, sharedConnectionViewModel)
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

fun isValidIPv4(ip: String?): Boolean {
    if (ip.isNullOrBlank()) return false
    if (ip == "0.0.0.0") return false

    val regex = Regex("^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)(\\.|$)){4}\$")
    return regex.matches(ip)
}


@Composable
fun AppListScreen(navController: NavHostController, sharedReportViewModel: SharedReportViewModel, sharedConnectionViewModel: SharedConnectionViewModel) {
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
                    val apps = appsManager.getInstalledApps(context)
                    for (app in apps) {
                        //Log.d("APPS", "App Name ${app.packageName}")
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



                    val connec = Connections()
                    val connections = connec.getAllActiveConnections(context)
                    val abuseIPDBApiKey = getApiKeyFromConfig(context, "ABUSEIPDB_API_KEY")
                    val abuseChecker = AbuseIPChecker(abuseIPDBApiKey)

                    val grouped = connections.groupBy { it.packageName ?: "unknown" }
                    val checkedIps = mutableSetOf<String>() // Declarar fuera del for

                    for ((packageName, conns) in grouped) {
                        val (rxBytes, txBytes) = trafficStats.getDataUsageForApp(packageName)

                        Log.d("APP-CONNECTIONS", "📦 $packageName -> ${conns.size} conexiones")
                        Log.d("APP-CONNECTIONS", "    Total Rx: $rxBytes bytes | Tx: $txBytes bytes")

                        for (conn in conns) {
                            val remoteIp = conn.remoteIp
                            val state = conn.state
                            val proto = conn.protocol

                            // Si la IP NO es válida, saltar esta iteración
                            if (!isValidIPv4(remoteIp)) continue

                            // Solo analizar si no se ha analizado antes
                            if (!checkedIps.contains(remoteIp)) {
                                checkedIps.add(remoteIp)

                                // Usar el nuevo metodo que devuelve JSONObject
                                val jsonResult = abuseChecker.checkIpJson(remoteIp)

                                if (jsonResult != null) {
                                    // Array con todas las conexiones de este paquete para esta IP
                                    val connectionsForIp = conns.filter { it.remoteIp == remoteIp }
                                    val connectionDetails = connectionsForIp.map { connection ->
                                        ConnectionDetail(
                                            protocol = connection.protocol,
                                            state = connection.state,
                                            localPort = connection.localPort,
                                            remotePort = connection.remotePort
                                        )
                                    }

                                    // Crear ConnectionReport
                                    val connectionReport = ConnectionReport(
                                        ipAddress = jsonResult.optString("ipAddress", remoteIp),
                                        packageName = packageName,
                                        totalConnections = conns.size,
                                        rxBytes = rxBytes,
                                        txBytes = txBytes,
                                        abuseConfidenceScore = jsonResult.optInt("abuseConfidenceScore", 0),
                                        countryCode = jsonResult.optString("countryCode", "Unknown"),
                                        usageType = jsonResult.optString("usageType", "Unknown"),
                                        isp = jsonResult.optString("isp", "Unknown"),
                                        domain = jsonResult.optString("domain", "Unknown"),
                                        connections = connectionDetails,
                                        protocols = connectionsForIp.map { it.protocol }.distinct(),
                                        states = connectionsForIp.map { it.state }.distinct(),
                                        isPublic = jsonResult.optBoolean("isPublic", true),
                                        isTor = jsonResult.optBoolean("isTor", false),
                                        totalReports = jsonResult.optInt("totalReports", 0),
                                        lastReportedAt = jsonResult.optString("lastReportedAt", null)
                                    )

                                    // Añadir al ViewModel en el hilo principal
                                    CoroutineScope(Dispatchers.Main).launch {
                                        sharedConnectionViewModel.addConnectionReport(connectionReport)
                                    }

                                    Log.d("CONNECTION-ABUSEAPI", "↳ [$proto] ($state) JSON: $jsonResult")
                                }
                            }
                        }
                    }




                    //val vTotalApiKey = getApiKeyFromConfig(context, "VIRUSTOTAL_API_KEY")
                    val mobSFApiKey = getApiKeyFromConfig(context, "MOBSF_API_KEY")
                    for (app in dangerousApps){
                        val apkFile = ApkFiles().getAppApkFile(context, app.first)
                        if (apkFile != null) {
                            mobSFAPKScanner().analizarAPK(mobSFApiKey, apkFile.absolutePath, context) { jsonReport ->
                                // Parsear JSON recibido y actualizar estado en el hilo principal
                                CoroutineScope(Dispatchers.Main).launch {
                                    try {
                                        val report = Gson().fromJson(jsonReport, AppReport::class.java)
                                        sharedReportViewModel.addReport(report)
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
    val reports = sharedReportViewModel.reports
    val context = LocalContext.current

    Column(modifier = Modifier.padding(16.dp)) {
        Button(onClick = { navController.popBackStack() }) {
            Text("Volver")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("Reportes de Seguridad por App", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn {
            items(reports) { report ->
                val appName = report.app_name

                val categorizedIssues = mapOf(
                    "High" to report.high,
                    "Warning" to report.warning,
                    "Info" to report.info,
                    "Secure" to report.secure,
                    "Hotspot" to report.hotspot
                )

                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                expandedAppNames = if (expandedAppNames.contains(appName)) {
                                    expandedAppNames - appName
                                } else {
                                    expandedAppNames + appName
                                }
                            }
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Icono y nombre juntos a la izquierda
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Obtener icono de la app
                            val pm = context.packageManager
                            val iconDrawable = try {
                                pm.getApplicationIcon(report.file_name.dropLast(4))
                            } catch (e: Exception) {
                                null
                            }

                            if (iconDrawable != null) {
                                Image(
                                    bitmap = iconDrawable.toBitmap().asImageBitmap(),
                                    contentDescription = "Icono de $appName",
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Spacer(modifier = Modifier.size(40.dp))
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column {
                                Text(appName, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Trackers: ${report.trackers}/${report.total_trackers}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        // Security Score dentro de círculo
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        report.security_score >= 80 -> Color(0xFF4CAF50) // verde
                                        report.security_score >= 50 -> Color(0xFFFFC107) // amarillo
                                        else -> Color(0xFFF44336) // rojo
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = report.security_score.toString(),
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 16.sp)
                            )
                        }
                    }

                    if (expandedAppNames.contains(appName)) {
                        categorizedIssues.forEach { (category, issues) ->
                            if (issues.isNotEmpty()) {
                                val categoryKey = "$appName-$category"
                                Column {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                expandedCategories = if (expandedCategories.contains(categoryKey)) {
                                                    expandedCategories - categoryKey
                                                } else {
                                                    expandedCategories + categoryKey
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
                                            imageVector = if (expandedCategories.contains(categoryKey)) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                            contentDescription = null
                                        )
                                    }

                                    if (expandedCategories.contains(categoryKey)) {
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

@Composable
fun TrafficScreen(navController: NavHostController, sharedConnectionViewModel: SharedConnectionViewModel) {
    var expandedApps by remember { mutableStateOf(setOf<String>()) }
    var expandedIPs by remember { mutableStateOf(setOf<String>()) }
    var expandedConnections by remember { mutableStateOf(setOf<String>()) }
    val connectionReports = sharedConnectionViewModel.connectionReports
    val context = LocalContext.current

    // Agrupar conexiones por aplicación
    val groupedByApp = connectionReports.groupBy { it.packageName }

    // Función para obtener el icono de la aplicación
    fun getAppIcon(packageName: String): Drawable? {
        return try {
            context.packageManager.getApplicationIcon(packageName)
        } catch (e: Exception) {
            null
        }
    }

    // Función para obtener el nombre de la aplicación
    fun getAppName(packageName: String): String {
        return try {
            val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName
        }
    }

    @Composable
    fun DrawableImage(drawable: Drawable?, contentDescription: String?, modifier: Modifier = Modifier) {
        val bitmap = remember(drawable) {
            drawable?.let {
                val bitmap = Bitmap.createBitmap(
                    it.intrinsicWidth,
                    it.intrinsicHeight,
                    Bitmap.Config.ARGB_8888
                )
                val canvas = Canvas(bitmap)
                it.setBounds(0, 0, canvas.width, canvas.height)
                it.draw(canvas)
                bitmap.asImageBitmap()
            }
        }

        bitmap?.let {
            Image(
                bitmap = it,
                contentDescription = contentDescription,
                modifier = modifier
            )
        }
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Button(onClick = { navController.popBackStack() }) {
            Text("Volver")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("Análisis de Tráfico de Red", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(8.dp))

        if (connectionReports.isEmpty()) {
            Text(
                "No hay datos de conexión disponibles. Ejecuta el análisis primero.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn {
                items(groupedByApp.toList()) { (packageName, reports) ->
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                // Header de la aplicación
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            expandedApps = if (expandedApps.contains(packageName)) {
                                                expandedApps - packageName
                                            } else {
                                                expandedApps + packageName
                                            }
                                        },
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Icono de la aplicación
                                    val appIcon = remember(packageName) { getAppIcon(packageName) }
                                    val appName = remember(packageName) { getAppName(packageName) }

                                    if (appIcon != null) {
                                        DrawableImage(
                                            drawable = appIcon,
                                            contentDescription = "Icono de $appName",
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                        )
                                    } else {
                                        // Icono por defecto si no se puede cargar
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Android,
                                                contentDescription = "Icono por defecto",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = appName,
                                            style = MaterialTheme.typography.titleLarge,
                                            color = MaterialTheme.colorScheme.primary
                                        )

                                        Text(
                                            text = packageName,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        val totalConnections = reports.sumOf { it.totalConnections }
                                        val totalRx = reports.sumOf { it.rxBytes }
                                        val totalTx = reports.sumOf { it.txBytes }
                                        val uniqueIPs = reports.map { it.ipAddress }.distinct().size

                                        Text(
                                            text = "$uniqueIPs IPs • $totalConnections conexiones",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        Text(
                                            text = "↓ ${formatBytes(totalRx)} • ↑ ${formatBytes(totalTx)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }

                                    // Indicador del score de abuso más alto
                                    val maxAbuseScore = reports.maxOfOrNull { it.abuseConfidenceScore } ?: 0
                                    Box(
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(CircleShape)
                                            .background(
                                                when {
                                                    maxAbuseScore >= 75 -> Color(0xFFF44336) // rojo
                                                    maxAbuseScore >= 25 -> Color(0xFFFFC107) // amarillo
                                                    else -> Color(0xFF4CAF50) // verde
                                                }
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = maxAbuseScore.toString(),
                                                color = Color.White,
                                                style = MaterialTheme.typography.titleMedium
                                            )
                                            Text(
                                                text = "MAX ABUSE",
                                                color = Color.White,
                                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Icon(
                                        imageVector = if (expandedApps.contains(packageName))
                                            Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }

                                // Contenido expandido de la aplicación
                                if (expandedApps.contains(packageName)) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Divider()
                                    Spacer(modifier = Modifier.height(16.dp))

                                    // Lista de IPs para esta aplicación
                                    reports.forEach { report ->
                                        val ipKey = "${report.packageName}-${report.ipAddress}"

                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                                            ),
                                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable {
                                                            expandedIPs = if (expandedIPs.contains(ipKey)) {
                                                                expandedIPs - ipKey
                                                            } else {
                                                                expandedIPs + ipKey
                                                            }
                                                        },
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = "IP: ${report.ipAddress}",
                                                            style = MaterialTheme.typography.titleSmall,
                                                            color = MaterialTheme.colorScheme.primary
                                                        )

                                                        Text(
                                                            text = "${report.countryCode} • ${report.isp}",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )

                                                        Text(
                                                            text = "↓ ${formatBytes(report.rxBytes)} • ↑ ${formatBytes(report.txBytes)}",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.secondary
                                                        )
                                                    }

                                                    // Abuse Score de la IP
                                                    Box(
                                                        modifier = Modifier
                                                            .size(40.dp)
                                                            .clip(CircleShape)
                                                            .background(
                                                                when {
                                                                    report.abuseConfidenceScore >= 75 -> Color(0xFFF44336)
                                                                    report.abuseConfidenceScore >= 25 -> Color(0xFFFFC107)
                                                                    else -> Color(0xFF4CAF50)
                                                                }
                                                            ),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = report.abuseConfidenceScore.toString(),
                                                            color = Color.White,
                                                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp)
                                                        )
                                                    }

                                                    Spacer(modifier = Modifier.width(8.dp))

                                                    Icon(
                                                        imageVector = if (expandedIPs.contains(ipKey))
                                                            Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                                        contentDescription = null
                                                    )
                                                }

                                                // Detalles de la IP
                                                if (expandedIPs.contains(ipKey)) {
                                                    Spacer(modifier = Modifier.height(12.dp))

                                                    Column {
                                                        Text(
                                                            "Detalles de la IP",
                                                            style = MaterialTheme.typography.titleSmall,
                                                            color = MaterialTheme.colorScheme.primary
                                                        )
                                                        Spacer(modifier = Modifier.height(8.dp))

                                                        InfoRow("Tipo de uso", report.usageType)
                                                        InfoRow("Dominio", report.domain)
                                                        InfoRow("Es Tor", if (report.isTor) "Sí" else "No")
                                                        InfoRow("Es pública", if (report.isPublic) "Sí" else "No")
                                                        InfoRow("Total reportes", report.totalReports.toString())
                                                        InfoRow("Conexiones totales", report.totalConnections.toString())
                                                        InfoRow("Protocolos", report.protocols.joinToString(", "))
                                                        InfoRow("Estados", report.states.joinToString(", "))

                                                        if (report.lastReportedAt != null) {
                                                            InfoRow("Último reporte", report.lastReportedAt)
                                                        }

                                                        Spacer(modifier = Modifier.height(12.dp))

                                                        // Conexiones individuales
                                                        val connectionsKey = "$ipKey-connections"
                                                        Row(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .clickable {
                                                                    expandedConnections = if (expandedConnections.contains(connectionsKey)) {
                                                                        expandedConnections - connectionsKey
                                                                    } else {
                                                                        expandedConnections + connectionsKey
                                                                    }
                                                                }
                                                                .padding(vertical = 4.dp),
                                                            horizontalArrangement = Arrangement.SpaceBetween
                                                        ) {
                                                            Text(
                                                                "Conexiones (${report.connections.size})",
                                                                style = MaterialTheme.typography.titleSmall,
                                                                color = MaterialTheme.colorScheme.primary
                                                            )
                                                            Icon(
                                                                imageVector = if (expandedConnections.contains(connectionsKey))
                                                                    Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                                                contentDescription = null
                                                            )
                                                        }

                                                        if (expandedConnections.contains(connectionsKey)) {
                                                            report.connections.forEach { connection ->
                                                                Card(
                                                                    modifier = Modifier
                                                                        .fillMaxWidth()
                                                                        .padding(vertical = 2.dp),
                                                                    colors = CardDefaults.cardColors(
                                                                        containerColor = MaterialTheme.colorScheme.surface
                                                                    )
                                                                ) {
                                                                    Column(modifier = Modifier.padding(12.dp)) {
                                                                        Row(
                                                                            modifier = Modifier.fillMaxWidth(),
                                                                            horizontalArrangement = Arrangement.SpaceBetween
                                                                        ) {
                                                                            Text(
                                                                                "${connection.protocol} - ${connection.state}",
                                                                                style = MaterialTheme.typography.bodyMedium
                                                                            )
                                                                            Text(
                                                                                "Local: ${connection.localPort} → Remote: ${connection.remotePort}",
                                                                                style = MaterialTheme.typography.bodySmall,
                                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                                            )
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
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(2f)
        )
    }
}

// Función auxiliar para formatear bytes
private fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1_073_741_824 -> String.format("%.2f GB", bytes / 1_073_741_824.0)
        bytes >= 1_048_576 -> String.format("%.2f MB", bytes / 1_048_576.0)
        bytes >= 1024 -> String.format("%.2f KB", bytes / 1024.0)
        else -> "$bytes B"
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

data class ConnectionReport(
    val ipAddress: String,
    val packageName: String,
    val totalConnections: Int,
    val rxBytes: Long,
    val txBytes: Long,
    val abuseConfidenceScore: Int,
    val countryCode: String,
    val usageType: String,
    val isp: String,
    val domain: String,
    val connections: List<ConnectionDetail>,
    val protocols: List<String>,
    val states: List<String>,
    val isPublic: Boolean,
    val isTor: Boolean,
    val totalReports: Int,
    val lastReportedAt: String?
)

data class ConnectionDetail(
    val protocol: String,
    val state: String,
    val localPort: Int,
    val remotePort: Int
)





