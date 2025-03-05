package virusTotalAPI

import android.util.Log
import okhttp3.*
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.math.BigInteger
import java.security.MessageDigest

class VirusTotalHashScanner {

    private val client = OkHttpClient()

    fun scanFileByHash(filePath: String, apiKey: String) {
        val fileHash = calculateSHA256(File(filePath)) ?: return
        val url = "https://www.virustotal.com/api/v3/files/$fileHash"

        val request = Request.Builder()
            .url(url)
            .get()
            .addHeader("accept", "application/json")
            .addHeader("x-apikey", apiKey)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.d("VIRUSTOTAL","Error en la petición: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                Log.d("VIRUSTOTAL","Respuesta de VirusTotal: ${response.body?.string()}")
            }
        })
    }

    private fun calculateSHA256(file: File): String? {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val inputStream = FileInputStream(file)
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
            inputStream.close()
            val hash = BigInteger(1, digest.digest()).toString(16).padStart(64, '0')
            Log.d("VIRUSTOTAL","SHA-256: $hash")
            hash
        } catch (e: Exception) {
            Log.d("VIRUSTOTAL","Error calculando SHA-256: ${e.message}")
            null
        }
    }
}
