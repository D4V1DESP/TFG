import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import java.io.File
import java.io.IOException


class VirusTotalUploader {

    suspend fun uploadFileToVirusTotal(file: File, apiKey: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient()

                val mediaType = "application/octet-stream".toMediaTypeOrNull()
                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file", file.name, RequestBody.create(mediaType, file))
                    .build()

                val request = Request.Builder()
                    .url("https://www.virustotal.com/api/v3/files")
                    .post(requestBody)
                    .addHeader("x-apikey", apiKey)
                    .build()

                val response: Response = client.newCall(request).execute()
                if (!response.isSuccessful) throw IOException("Unexpected code $response")

                response.body?.string()
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
}
