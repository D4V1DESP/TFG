package com.example.amenazasandroid.abuseIPDBAPI

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class AbuseIPChecker(private val apiKey: String) {

    private val client = OkHttpClient()

    fun checkIp(ip: String): String {
        val url = "https://api.abuseipdb.com/api/v2/check?ipAddress=$ip"

        val request = Request.Builder()
            .url(url)
            .addHeader("Key", apiKey)
            .addHeader("Accept", "application/json")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return "❌ Error ${response.code}: ${response.message}"

            val body = response.body?.string() ?: return "❌ Empty response"
            val json = JSONObject(body).getJSONObject("data")

            val score = json.getInt("abuseConfidenceScore")
            val country = json.optString("countryCode", "N/A")
            val usageType = json.optString("usageType", "Unknown")
            val isp = json.optString("isp", "Unknown")
            val domain = json.optString("domain", "Unknown")

            return "IP: $ip\n" +
                    "→ Abuse Score: $score/100\n" +
                    "→ Country: $country | ISP: $isp\n" +
                    "→ Usage: $usageType\n" +
                    "→ Domain: $domain"
        }
    }
}
