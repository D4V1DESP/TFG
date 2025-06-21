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
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Android
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.example.amenazasandroid.abuseIPDBAPI.AbuseIPChecker
import com.example.amenazasandroid.models.SharedLocationViewModel
import trafficStats.Connections
import trafficStats.TrafficStats
import androidx.compose.material.icons.filled.Warning


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
                val sharedLocationViewModel = viewModel<SharedLocationViewModel>()

                NavHost(navController = navController, startDestination = Screen.Home.route) {
                    composable(Screen.Home.route) {
                        AppListScreen(navController, sharedReportViewModel, sharedConnectionViewModel, sharedLocationViewModel)
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
                    composable(Screen.Location.route) {
                        LocationScreen(navController, sharedLocationViewModel)
                    }
                    composable(Screen.Permissions.route) {
                        PermissionsScreen(navController)
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
fun AppListScreen(navController: NavHostController, sharedReportViewModel: SharedReportViewModel, sharedConnectionViewModel: SharedConnectionViewModel, sharedLocationViewModel: SharedLocationViewModel) {
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
                            val ipVersion = conn.ipVersion // Nuevo campo

                            // Las IPs ya están filtradas (sin privadas/locales) y validadas en getAllActiveConnections()
                            // Solo analizar si no se ha analizado antes
                            if (!checkedIps.contains(remoteIp)) {
                                checkedIps.add(remoteIp)

                                Log.d("CONNECTION-CHECK", "🔍 Analizando IP $packageName: $remoteIp")

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
                                            remotePort = connection.remotePort,
                                            ipVersion = connection.ipVersion // Añadir versión IP al detalle
                                        )
                                    }

                                    val reportsArray = jsonResult.optJSONArray("reports")
                                    val abuseReports = mutableListOf<AbuseReport>()

                                    if (reportsArray != null) {
                                        for (i in 0 until reportsArray.length()) {
                                            val reportObj = reportsArray.getJSONObject(i)
                                            val abuseReport = AbuseReport(
                                                reportedAt = reportObj.optString("reportedAt", null),
                                                comment = reportObj.optString("comment", ""),
                                                categories = reportObj.optJSONArray("categories")?.let { categoriesArray ->
                                                    (0 until categoriesArray.length()).map { categoriesArray.getInt(it) }
                                                } ?: emptyList(),
                                                reporterId = reportObj.optInt("reporterId", 0),
                                                reporterCountryCode = reportObj.optString("reporterCountryCode", "Unknown"),
                                                reporterCountryName = reportObj.optString("reporterCountryName", "Unknown")
                                            )
                                            abuseReports.add(abuseReport)
                                        }
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
                                        countryName = jsonResult.optString("countryName", "Unknown"),
                                        usageType = jsonResult.optString("usageType", "Unknown"),
                                        isp = jsonResult.optString("isp", "Unknown"),
                                        domain = jsonResult.optString("domain", "Unknown"),
                                        connections = connectionDetails,
                                        protocols = connectionsForIp.map { it.protocol }.distinct(),
                                        states = connectionsForIp.map { it.state }.distinct(),
                                        isPublic = jsonResult.optBoolean("isPublic", true),
                                        isTor = jsonResult.optBoolean("isTor", false),
                                        totalReports = jsonResult.optInt("totalReports", 0),
                                        lastReportedAt = jsonResult.optString("lastReportedAt", null),
                                        ipVersion = ipVersion, // Nuevo campo para la versión IP
                                        reports = abuseReports
                                    )

                                    // Añadir al ViewModel en el hilo principal
                                    CoroutineScope(Dispatchers.Main).launch {
                                        sharedConnectionViewModel.addConnectionReport(connectionReport)
                                    }

                                    Log.d("CONNECTION-ABUSEAPI", "↳ [$proto] ($state) [$ipVersion] JSON: $jsonResult")
                                } else {
                                    Log.w("CONNECTION-ABUSEAPI", "⚠️ No se pudo obtener info para IP $ipVersion: $remoteIp")
                                }
                            }
                        }
                    }


                    val userInstalledApps = appsManager.getUserInstalledApps(context)
                    val mobSFApiKey = getApiKeyFromConfig(context, "MOBSF_API_KEY")
                    for (app in userInstalledApps){
                        val apkFile = ApkFiles().getAppApkFile(context, app.packageName)
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

                    val locationStats = locationAccess.getLocationStats(context)//Obtener datos sobre la ubicación desde ADB
                    sharedLocationViewModel.updateLocationStats(locationStats)
                }
                navController.navigate(Screen.AppResults.route)
            }

            showApps = true // Mostrar las aplicaciones
        }) {
            Text(("Obtener Aplicaciones"))
        }
        Spacer(modifier = Modifier.height(16.dp))

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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
            }
            Text(
                "Reportes de Seguridad por App",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

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
    var expandedReports by remember { mutableStateOf(setOf<String>()) } // Nuevo estado para reportes
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

    fun getCategoryDescription(categories: List<Int>): String {
        val categoryMap = mapOf(
            3 to "Fraude",
            4 to "Actividad DDoS",
            5 to "Spam",
            6 to "Exploit",
            7 to "Botnet",
            8 to "Malware",
            9 to "Phishing",
            10 to "Hacking",
            11 to "Spam",
            12 to "Suplantación",
            13 to "Brute Force",
            14 to "Badware",
            15 to "Exploit",
            16 to "Botnet",
            17 to "Comprometido",
            18 to "SSH",
            19 to "IoT",
            20 to "Abuso de base de datos",
            21 to "Abuso de webmail",
            22 to "SSH"
        )

        return categories.mapNotNull { categoryMap[it] }.distinct().joinToString(", ")
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
            }
            Text(
                "Análisis de Tráfico de Red",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

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

                                    // Lista de IPs para esta aplicación ordenadas por abuse score (descendente)
                                    reports.sortedByDescending { it.abuseConfidenceScore }.forEach { report ->
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
                                                        InfoRow("Proveedor de servicios", report.isp)
                                                        InfoRow("País", report.countryName)
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

                                                        if (report.reports.isNotEmpty()) {
                                                            val reportsKey = "$ipKey-reports"
                                                            Row(
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .clickable {
                                                                        expandedReports = if (expandedReports.contains(reportsKey)) {
                                                                            expandedReports - reportsKey
                                                                        } else {
                                                                            expandedReports + reportsKey
                                                                        }
                                                                    }
                                                                    .padding(vertical = 4.dp),
                                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                                    Icon(
                                                                        imageVector = Icons.Default.Warning,
                                                                        contentDescription = null,
                                                                        tint = Color(0xFFF44336),
                                                                        modifier = Modifier.size(16.dp)
                                                                    )
                                                                    Spacer(modifier = Modifier.width(4.dp))
                                                                    Text(
                                                                        "Reportes de Abuso (${report.reports.size})",
                                                                        style = MaterialTheme.typography.titleSmall,
                                                                        color = MaterialTheme.colorScheme.primary
                                                                    )
                                                                }
                                                                Icon(
                                                                    imageVector = if (expandedReports.contains(reportsKey))
                                                                        Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                                                    contentDescription = null
                                                                )
                                                            }

                                                            if (expandedReports.contains(reportsKey)) {
                                                                Spacer(modifier = Modifier.height(8.dp))

                                                                report.reports.forEach { abuseReport ->
                                                                    Card(
                                                                        modifier = Modifier
                                                                            .fillMaxWidth()
                                                                            .padding(vertical = 2.dp),
                                                                        colors = CardDefaults.cardColors(
                                                                            containerColor = Color(0xFFFFF3E0) // Fondo naranja claro
                                                                        ),
                                                                        border = BorderStroke(1.dp, Color(0xFFFF9800))
                                                                    ) {
                                                                        Column(modifier = Modifier.padding(12.dp)) {
                                                                            // Fecha y país del reportero
                                                                            Row(
                                                                                modifier = Modifier.fillMaxWidth(),
                                                                                horizontalArrangement = Arrangement.SpaceBetween
                                                                            ) {
                                                                                Text(
                                                                                    "📅 ${abuseReport.reportedAt}",
                                                                                    style = MaterialTheme.typography.bodySmall,
                                                                                    color = Color(0xFF795548)
                                                                                )
                                                                                Text(
                                                                                    "🌍 ${abuseReport.reporterCountryName}",
                                                                                    style = MaterialTheme.typography.bodySmall,
                                                                                    color = Color(0xFF795548)
                                                                                )
                                                                            }

                                                                            Spacer(modifier = Modifier.height(4.dp))

                                                                            // Categorías de abuso
                                                                            if (abuseReport.categories.isNotEmpty()) {
                                                                                Text(
                                                                                    "🏷️ ${getCategoryDescription(abuseReport.categories)}",
                                                                                    style = MaterialTheme.typography.bodySmall,
                                                                                    color = Color(0xFFD84315),
                                                                                    fontWeight = FontWeight.Medium
                                                                                )
                                                                                Spacer(modifier = Modifier.height(4.dp))
                                                                            }

                                                                            // Comentario del reporte
                                                                            if (abuseReport.comment.isNotEmpty()) {
                                                                                Text(
                                                                                    "💬 ${abuseReport.comment}",
                                                                                    style = MaterialTheme.typography.bodySmall,
                                                                                    color = Color(0xFF424242),
                                                                                    modifier = Modifier
                                                                                        .background(
                                                                                            Color(0xFFF5F5F5),
                                                                                            RoundedCornerShape(4.dp)
                                                                                        )
                                                                                        .padding(8.dp)
                                                                                )
                                                                            }
                                                                        }
                                                                    }
                                                                }
                                                            }
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
fun LocationScreen(navController: NavHostController, sharedLocationViewModel: SharedLocationViewModel) {
    val locationStats = sharedLocationViewModel.locationStats

    var expandedProviders by remember { mutableStateOf(setOf<String>()) }
    var expandedEntries by remember { mutableStateOf(setOf<String>()) }

    Column(modifier = Modifier.padding(16.dp)) {
        // Header simple
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
            }
            Text(
                "Estadísticas de Ubicación",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val grouped = locationStats.groupBy { it.provider }

            grouped.forEach { (provider, entries) ->
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column {
                            // Header del proveedor
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        expandedProviders = if (expandedProviders.contains(provider)) {
                                            expandedProviders - provider
                                        } else {
                                            expandedProviders + provider
                                        }
                                    }
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        provider.uppercase(),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        "${entries.size} aplicaciones",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                }
                                Icon(
                                    imageVector = if (expandedProviders.contains(provider))
                                        Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null
                                )
                            }

                            // Lista de aplicaciones
                            if (expandedProviders.contains(provider)) {
                                entries.forEach { entry ->
                                    val isExpanded = expandedEntries.contains(entry.uid)
                                    val hasThreats = entry.threats.isNotEmpty()

                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                expandedEntries = if (isExpanded) {
                                                    expandedEntries - entry.uid
                                                } else {
                                                    expandedEntries + entry.uid
                                                }
                                            }
                                            .padding(horizontal = 16.dp, vertical = 8.dp)
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    entry.packageName,
                                                    style = MaterialTheme.typography.titleSmall
                                                )
                                                if (hasThreats) {
                                                    Text(
                                                        "⚠️ ${entry.threats.size} amenaza${if (entry.threats.size > 1) "s" else ""}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.error
                                                    )
                                                }
                                            }

                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    "${entry.locations}",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Icon(
                                                    imageVector = if (isExpanded)
                                                        Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }

                                        if (isExpanded) {
                                            Spacer(modifier = Modifier.height(8.dp))

                                            Card(
                                                colors = CardDefaults.cardColors(
                                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                                )
                                            ) {
                                                Column(modifier = Modifier.padding(12.dp)) {
                                                    Text("UID: ${entry.uid}", style = MaterialTheme.typography.bodySmall)
                                                    Text("Intervalo Mín: ${entry.minInterval}", style = MaterialTheme.typography.bodySmall)
                                                    Text("Intervalo Máx: ${entry.maxInterval}", style = MaterialTheme.typography.bodySmall)
                                                    Text("Duración Total: ${entry.totalDuration}", style = MaterialTheme.typography.bodySmall)
                                                    Text("Duración Activa: ${entry.activeDuration}", style = MaterialTheme.typography.bodySmall)
                                                    Text("Duración Primer Plano: ${entry.foregroundDuration}", style = MaterialTheme.typography.bodySmall)
                                                    Text("Ubicaciones: ${entry.locations}", style = MaterialTheme.typography.bodySmall)

                                                    if (entry.threats.isNotEmpty()) {
                                                        Spacer(modifier = Modifier.height(8.dp))
                                                        Text(
                                                            "Amenazas:",
                                                            style = MaterialTheme.typography.labelMedium,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = MaterialTheme.colorScheme.error
                                                        )
                                                        entry.threats.forEach { threat ->
                                                            Text(
                                                                "• $threat",
                                                                style = MaterialTheme.typography.bodySmall,
                                                                modifier = Modifier.padding(start = 8.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    if (entry != entries.last()) {
                                        Divider(
                                            modifier = Modifier.padding(horizontal = 16.dp),
                                            thickness = 0.5.dp,
                                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
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

@Composable
fun PermissionsScreen(navController: NavHostController) {
    var expandedApps by remember { mutableStateOf(setOf<String>()) }
    var dangerousApps by remember { mutableStateOf<List<Pair<String, List<String>>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val context = LocalContext.current

    // Cargar datos al iniciar
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val appsManager = AppsManager()
                val apps = appsManager.getUserInstalledApps(context)
                val dangerous = appsManager.getDangerousPermissionApps(context, apps)
                dangerousApps = dangerous.sortedByDescending { it.second.size }
                isLoading = false
            } catch (e: Exception) {
                isLoading = false
            }
        }
    }

    // Funciones auxiliares (iguales que antes)
    fun getAppIcon(packageName: String): Drawable? {
        return try {
            context.packageManager.getApplicationIcon(packageName)
        } catch (e: Exception) {
            null
        }
    }

    fun getAppName(packageName: String): String {
        return try {
            val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName
        }
    }

    fun getPermissionDisplayName(permission: String): String {
        return when (permission) {
            android.Manifest.permission.CAMERA -> "📷 Cámara"
            android.Manifest.permission.RECORD_AUDIO -> "🎤 Micrófono"
            android.Manifest.permission.READ_CONTACTS -> "👥 Leer contactos"
            android.Manifest.permission.WRITE_CONTACTS -> "✏️ Modificar o borrar contactos"
            android.Manifest.permission.GET_ACCOUNTS -> "👤 Obtener las cuentas del usuario"
            android.Manifest.permission.READ_CALL_LOG -> "📞 Leer el historial de llamadas"
            android.Manifest.permission.WRITE_CALL_LOG -> "📝 Modificar el historial de llamadas"
            android.Manifest.permission.SEND_SMS -> "📤 Enviar SMS"
            android.Manifest.permission.READ_SMS -> "📨 Leer SMS"
            android.Manifest.permission.ACCESS_FINE_LOCATION -> "📍 Ubicación GPS"
            android.Manifest.permission.ACCESS_COARSE_LOCATION -> "🗺️ Ubicación por red"
            android.Manifest.permission.READ_EXTERNAL_STORAGE -> "📁 Leer archivos"
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE -> "💾 Modificar archivos"
            else -> permission.substringAfterLast(".")
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
            }
            Text(
                "Apps Sospechosas",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (dangerousApps.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E8))
            ) {
                Text(
                    "✅ ¡Genial! No hay aplicaciones con permisos peligrosos.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF2E7D32),
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            LazyColumn {
                items(dangerousApps) { (packageName, permissions) ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Header clickeable
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
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Icono de la app
                                val appIcon = remember(packageName) { getAppIcon(packageName) }
                                val appName = remember(packageName) { getAppName(packageName) }

                                if (appIcon != null) {
                                    DrawableImage(
                                        drawable = appIcon,
                                        contentDescription = "Icono de $appName",
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Android,
                                            contentDescription = "Icono por defecto",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                // Información de la app
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = appName,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = packageName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                // Contador de permisos
                                Text(
                                    text = "${permissions.size}",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                Icon(
                                    imageVector = if (expandedApps.contains(packageName))
                                        Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }

                            // Lista de permisos expandida
                            if (expandedApps.contains(packageName)) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Divider()
                                Spacer(modifier = Modifier.height(12.dp))

                                permissions.forEach { permission ->
                                    Text(
                                        text = getPermissionDisplayName(permission),
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(vertical = 2.dp)
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
    val countryName: String,
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
    val lastReportedAt: String?,
    val ipVersion: String,
    val reports: List<AbuseReport>
)

data class AbuseReport(
    val reportedAt: String?,
    val comment: String,
    val categories: List<Int>,
    val reporterId: Int,
    val reporterCountryCode: String,
    val reporterCountryName: String
)

data class ConnectionDetail(
    val protocol: String,
    val state: String,
    val localPort: Int,
    val remotePort: Int,
    val ipVersion: String
)





