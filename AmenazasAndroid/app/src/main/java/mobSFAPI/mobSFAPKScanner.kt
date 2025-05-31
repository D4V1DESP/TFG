package mobSFAPI

import android.content.Context
import android.util.Log
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import kotlin.coroutines.resume

class mobSFAPKScanner {

    companion object {
        private const val MAX_RETRIES = 9
        private const val INITIAL_DELAY_MS = 2000L // 2 segundos
        private const val BACKOFF_MULTIPLIER = 1.0
    }

    fun analizarAPK(apiKey: String, apkPath: String, context: Context, onReportReceived: (String) -> Unit) {
        val apkFile = File(apkPath)

        // Paso 1: Generar hash del APK localmente
        val hash = generateMD5Hash(apkFile)
        if (hash == null) {
            Log.e("mobSF", "❌ Error al generar hash del APK")
            return
        }

        Log.d("mobSF", "📋 Hash generado: $hash")

        // Paso 2: Intentar obtener reporte existente con reintentos
        intentarObtenerReporteConReintentos(apiKey, hash, context, onReportReceived) { reporteExiste ->
            if (reporteExiste) {
                Log.d("mobSF", "✅ Reporte ya existe, obtenido desde cache")
            } else {
                Log.d("mobSF", "📤 No existe reporte, procediendo a subir y escanear APK")
                subirYEscanearAPK(apiKey, apkFile, hash, context, onReportReceived)
            }
        }
    }

    private fun generateMD5Hash(file: File): String? {
        return try {
            val digest = MessageDigest.getInstance("MD5")
            file.inputStream().use { inputStream ->
                val buffer = ByteArray(8192)
                var read: Int
                while (inputStream.read(buffer).also { read = it } != -1) {
                    digest.update(buffer, 0, read)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.e("mobSF", "Error generando hash MD5: ${e.message}")
            null
        }
    }

    private fun intentarObtenerReporteConReintentos(
        apiKey: String,
        hash: String,
        context: Context,
        onReportReceived: (String) -> Unit,
        onResult: (Boolean) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            var attempt = 0
            var delay = INITIAL_DELAY_MS

            while (attempt <= MAX_RETRIES) {
                try {
                    val success = suspendCancellableCoroutine<Boolean> { continuation ->
                        intentarObtenerReporte(apiKey, hash, context, onReportReceived) { reporteExiste ->
                            continuation.resume(reporteExiste)
                        }
                    }

                    if (success) {
                        onResult(true)
                        return@launch
                    } else if (attempt == MAX_RETRIES) {
                        Log.w("mobSF", "⚠️ No se pudo obtener el reporte después de ${MAX_RETRIES + 1} intentos")
                        onResult(false)
                        return@launch
                    }

                } catch (e: Exception) {
                    Log.w("mobSF", "⚠️ Intento ${attempt + 1} fallido al obtener reporte: ${e.message}")

                    if (attempt == MAX_RETRIES) {
                        Log.e("mobSF", "❌ Error final al obtener reporte después de ${MAX_RETRIES + 1} intentos")
                        onResult(false)
                        return@launch
                    }
                }

                attempt++
                if (attempt <= MAX_RETRIES) {
                    Log.i("mobSF", "🔄 Reintentando obtener reporte en ${delay}ms (intento ${attempt + 1}/${MAX_RETRIES + 1})")
                    delay(delay)
                    delay = (delay * BACKOFF_MULTIPLIER).toLong()
                }
            }
        }
    }

    private fun intentarObtenerReporte(apiKey: String, hash: String, context: Context, onReportReceived: (String) -> Unit, onResult: (Boolean) -> Unit) {
        val client = OkHttpClient.Builder()
            .connectTimeout(1, TimeUnit.MINUTES)
            .readTimeout(3, TimeUnit.MINUTES)
            .writeTimeout(2, TimeUnit.MINUTES)
            .retryOnConnectionFailure(true)
            .build()

        val reportBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("hash", hash)
            .build()

        val reportRequest = Request.Builder()
            .url("http://10.0.2.2:8000/api/v1/scorecard")
            .addHeader("Authorization", apiKey)
            .addHeader("Accept", "application/json")
            .addHeader("Connection", "close") // Evitar problemas de keep-alive
            .post(reportBody)
            .build()

        client.newCall(reportRequest).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.w("mobSF", "⚠️ Error al verificar reporte existente: ${e.javaClass.simpleName} - ${e.message}")
                onResult(false) // Asumimos que no existe y procedemos con upload
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    response.use {
                        if (it.isSuccessful) {
                            val json = safeReadResponseBody(it)
                            if (json != null && json.isNotEmpty()) {
                                Log.i("mobSF", "✅ Reporte existente encontrado")
                                // Llamar al callback con el reporte existente
                                onReportReceived(json)
                                onResult(true)
                            } else {
                                Log.d("mobSF", "📭 Reporte vacío, procediendo con upload")
                                onResult(false)
                            }
                        } else {
                            Log.d("mobSF", "📭 Reporte no existe (HTTP ${it.code}), procediendo con upload")
                            onResult(false)
                        }
                    }
                } catch (e: Exception) {
                    Log.w("mobSF", "⚠️ Error procesando respuesta del reporte: ${e.javaClass.simpleName} - ${e.message}")
                    onResult(false)
                }
            }
        })
    }

    private fun subirYEscanearAPK(apiKey: String, apkFile: File, expectedHash: String, context: Context, onReportReceived: (String) -> Unit) {
        val client = OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.MINUTES)
            .readTimeout(5, TimeUnit.MINUTES)
            .writeTimeout(5, TimeUnit.MINUTES)
            .build()

        // Subir APK
        val uploadRequestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", apkFile.name, RequestBody.create("application/octet-stream".toMediaTypeOrNull(), apkFile))
            .build()

        val uploadRequest = Request.Builder()
            .url("http://10.0.2.2:8000/api/v1/upload")
            .addHeader("Authorization", apiKey)
            .post(uploadRequestBody)
            .build()

        client.newCall(uploadRequest).execute().use { uploadResponse ->
            if (!uploadResponse.isSuccessful) {
                Log.e("mobSF", "❌ Error al subir el APK: ${uploadResponse.code}")
                uploadResponse.body?.string()?.let { Log.e("mobSF", it) }
                return
            }

            val uploadJson = JSONObject(uploadResponse.body!!.string())
            val serverHash = uploadJson.getString("hash")

            // Verificar que el hash coincida con el que calculamos
            if (serverHash != expectedHash) {
                Log.w("mobSF", "⚠️ Hash del servidor ($serverHash) no coincide con el calculado ($expectedHash)")
            }

            Log.d("mobSF", "✅ APK subido correctamente. Hash: $serverHash")

            // Escanear APK
            val scanBody = FormBody.Builder()
                .add("hash", serverHash)
                .add("re_scan", "0")
                .build()

            val scanRequest = Request.Builder()
                .url("http://10.0.2.2:8000/api/v1/scan")
                .addHeader("Authorization", apiKey)
                .post(scanBody)
                .build()

            client.newCall(scanRequest).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e("mobSF", "❌ Error al iniciar análisis: ${e.message}")
                }

                override fun onResponse(call: Call, response: Response) {
                    response.close()
                    Log.i("mobSF", "🔍 Análisis iniciado correctamente")

                    // Generar reporte después del escaneo con reintentos
                    generarReportJSONConReintentos(apiKey, serverHash, context, onReportReceived)
                }
            })
        }
    }

    private fun generarReportJSONConReintentos(apiKey: String, hash: String, context: Context, onReportReceived: (String) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            var attempt = 0
            var delay = INITIAL_DELAY_MS

            while (attempt <= MAX_RETRIES) {
                try {
                    val success = suspendCancellableCoroutine<Boolean> { continuation ->
                        generarReportJSON(apiKey, hash, context) { jsonReport ->
                            if (jsonReport.isNotEmpty()) {
                                onReportReceived(jsonReport)
                                continuation.resume(true)
                            } else {
                                continuation.resume(false)
                            }
                        }
                    }

                    if (success) {
                        Log.i("mobSF", "✅ Reporte obtenido exitosamente")
                        return@launch
                    }

                } catch (e: Exception) {
                    Log.w("mobSF", "⚠️ Intento ${attempt + 1} fallido al generar reporte: ${e.message}")
                }

                attempt++
                if (attempt <= MAX_RETRIES) {
                    Log.i("mobSF", "🔄 Reintentando generar reporte en ${delay}ms (intento ${attempt + 1}/${MAX_RETRIES + 1})")
                    delay(delay)
                    delay = (delay * BACKOFF_MULTIPLIER).toLong()
                } else {
                    Log.e("mobSF", "❌ Error final al generar reporte después de ${MAX_RETRIES + 1} intentos")
                }
            }
        }
    }

    private fun generarReportJSON(apiKey: String, hash: String, context: Context, onReportReceived: (String) -> Unit) {
        val client = OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.MINUTES)
            .readTimeout(10, TimeUnit.MINUTES)
            .writeTimeout(5, TimeUnit.MINUTES)
            .retryOnConnectionFailure(true)
            .build()

        val reportBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("hash", hash)
            .build()

        val reportRequest = Request.Builder()
            .url("http://10.0.2.2:8000/api/v1/scorecard")
            .addHeader("Authorization", apiKey)
            .addHeader("Accept", "application/json")
            .addHeader("Connection", "close") // Evitar problemas de keep-alive
            .post(reportBody)
            .build()

        client.newCall(reportRequest).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("mobSF", "❌ Error al obtener el reporte JSON: ${e.javaClass.simpleName} - ${e.message}")
                onReportReceived("") // Retornar string vacío para indicar fallo
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    response.use {
                        if (!it.isSuccessful) {
                            val errorBody = safeReadResponseBody(it)
                            Log.e("mobSF", "❌ Código HTTP ${it.code} - Respuesta: $errorBody")
                            onReportReceived("") // Retornar string vacío para indicar fallo
                        } else {
                            Log.i("mobSF", "✅ Reporte JSON recibido")
                            val json = safeReadResponseBody(it)
                            if (json != null && json.isNotEmpty()) {
                                onReportReceived(json)
                            } else {
                                Log.w("mobSF", "⚠️ Reporte JSON vacío")
                                onReportReceived("") // Retornar string vacío para indicar fallo
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("mobSF", "❌ Error procesando respuesta del reporte JSON: ${e.javaClass.simpleName} - ${e.message}")
                    onReportReceived("") // Retornar string vacío para indicar fallo
                }
            }
        })
    }


    /**
     * Método para leer de forma segura el cuerpo de la respuesta HTTP
     * Maneja errores de protocolo como chunked transfer encoding malformado
     */
    private fun safeReadResponseBody(response: Response): String? {
        return try {
            response.body?.string()
        } catch (e: java.net.ProtocolException) {
            Log.w("mobSF", "⚠️ Error de protocolo al leer respuesta: ${e.message}")
            // Intentar leer byte a byte si hay problemas con chunked encoding
            try {
                response.body?.byteStream()?.bufferedReader()?.use { reader ->
                    reader.readText()
                }
            } catch (fallbackException: Exception) {
                Log.e("mobSF", "❌ Error en lectura de respaldo: ${fallbackException.message}")
                null
            }
        } catch (e: IOException) {
            Log.w("mobSF", "⚠️ Error de IO al leer respuesta: ${e.message}")
            null
        } catch (e: Exception) {
            Log.e("mobSF", "❌ Error inesperado al leer respuesta: ${e.javaClass.simpleName} - ${e.message}")
            null
        }
    }
}