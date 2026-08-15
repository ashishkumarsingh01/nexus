package com.nexus.assistant.online

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.nexus.assistant.security.PermissionLevel
import com.nexus.assistant.tools.Tool
import com.nexus.assistant.tools.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Fully separate from the offline core (per spec: "online functionality
 * must be completely independent from the core offline system"). This tool
 * is only ever registered/reachable when OnlineSettings.onlineModeEnabled
 * is true, and even then it re-checks connectivity before every call and
 * never fabricates a result if the network isn't actually available.
 *
 * Uses Open-Meteo (open-meteo.com) — free, no API key, no account, no
 * usage cost, chosen specifically to keep the "everything free" requirement
 * intact for the optional online layer too.
 */
class OnlineTool(
    private val context: Context,
    private val onlineSettings: OnlineSettings
) : Tool {

    override val name = "online_tool"
    override val description = "Optional: looks up current weather for a city via the internet. Disabled unless online mode is on."
    override val permissionLevel = PermissionLevel.SAFE // no dangerous Android permission; gated by explicit online-mode setting instead

    override suspend fun execute(params: Map<String, String>, confirmed: Boolean): ToolResult {
        if (!onlineSettings.onlineModeEnabled) {
            return ToolResult.Error("Online mode is turned off, so I can't look that up. You can enable it in Settings if you want NEXUS to use the internet for this.")
        }
        if (!isNetworkAvailable()) {
            return ToolResult.Error("I am currently offline, so I cannot perform that operation.")
        }

        val action = params["action"] ?: return ToolResult.Error("online_tool called without an 'action' parameter.")
        return when (action) {
            "weather" -> getWeather(params["city"].orEmpty())
            else -> ToolResult.Error("Unknown online_tool action: \"$action\".")
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private suspend fun getWeather(city: String): ToolResult = withContext(Dispatchers.IO) {
        if (city.isBlank()) return@withContext ToolResult.Error("Which city would you like the weather for?")
        try {
            val encoded = URLEncoder.encode(city, "UTF-8")
            val geoJson = httpGet("https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=1")
            val results = JSONObject(geoJson).optJSONArray("results")
            if (results == null || results.length() == 0) {
                return@withContext ToolResult.Error("I couldn't find a location called \"$city\".")
            }
            val place = results.getJSONObject(0)
            val lat = place.getDouble("latitude")
            val lon = place.getDouble("longitude")
            val resolvedName = place.optString("name", city)

            val forecastJson = httpGet("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current_weather=true")
            val current = JSONObject(forecastJson).getJSONObject("current_weather")
            val tempC = current.getDouble("temperature")
            val windKmh = current.getDouble("windspeed")

            ToolResult.Success("Currently %.0f°C in %s, wind %.0f km/h.".format(tempC, resolvedName, windKmh))
        } catch (e: Exception) {
            ToolResult.Error("Couldn't fetch the weather right now: ${e.message ?: "unknown error"}")
        }
    }

    private fun httpGet(urlString: String): String {
        val connection = URL(urlString).openConnection() as HttpURLConnection
        connection.connectTimeout = 8000
        connection.readTimeout = 8000
        connection.requestMethod = "GET"
        return try {
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
