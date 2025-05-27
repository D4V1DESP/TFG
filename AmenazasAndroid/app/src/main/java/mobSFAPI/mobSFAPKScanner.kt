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
import java.util.concurrent.TimeUnit


class mobSFAPKScanner {

    fun analizarAPK(apiKey: String, apkPath: String) {
        val client = OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.MINUTES)
            .readTimeout(5, TimeUnit.MINUTES)
            .writeTimeout(5, TimeUnit.MINUTES)
            .build()

        val apkFile = File(apkPath)

        // Paso 1: Subir APK
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
                Log.d("mobSF","❌ Error al subir el APK: ${uploadResponse.code}")
                uploadResponse.body?.string()?.let { Log.d("mobSF", it) }
                return
            }

            val uploadJson = JSONObject(uploadResponse.body!!.string())
            val hash = uploadJson.getString("hash")
            Log.d("mobSF","✅ APK subido correctamente. Hash: $hash")

            // Paso 2: Escanear APK
            val scanBody = FormBody.Builder()
                .add("hash", hash)
                .add("re_scan", "0")
                .build()

            val scanRequest = Request.Builder()
                .url("http://10.0.2.2:8000/api/v1/scan")
                .addHeader("Authorization", apiKey)
                .post(scanBody)
                .build()

            client.newCall(scanRequest).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    // Si quieres registrar fallos, pero sin romper nada
                    Log.e("mobSF", "Error al iniciar análisis: ${e.message}")
                }

                override fun onResponse(call: Call, response: Response) {
                    // No leemos el body, solo cerramos la respuesta
                    response.close()
                    Log.i("mobSF", "Análisis enviado correctamente")

                    generarReportJSON(apiKey, hash)
                }
            })
        }
    }

    fun generarReportJSON(apiKey: String, hash: String){
        val client = OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.MINUTES)
            .readTimeout(10, TimeUnit.MINUTES)
            .writeTimeout(5, TimeUnit.MINUTES)
            .build()



        val reportBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("hash", hash)
            .build()

        val reportRequest = Request.Builder()
            .url("http://10.0.2.2:8000/api/v1/scorecard")
            .addHeader("Authorization", apiKey)
            .addHeader("Accept", "application/json")
            .post(reportBody)
            .build()

        client.newCall(reportRequest).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("mobSF", "❌ Error al obtener el reporte JSON: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val json = it.body?.string()
                    if (!it.isSuccessful) {
                        Log.e("mobSF", "❌ Código HTTP ${it.code} - Respuesta: $json")
                    } else {
                        Log.i("mobSF", "✅ Reporte JSON recibido:\n$json")
                    }
                }
            }
        })
    }
}