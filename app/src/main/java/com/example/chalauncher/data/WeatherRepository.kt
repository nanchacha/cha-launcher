package com.example.chalauncher.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.io.BufferedReader
import java.io.InputStreamReader

@Serializable
data class OpenMeteoResponse(
    @SerialName("current_weather") val currentWeather: CurrentWeather
)

@Serializable
data class CurrentWeather(
    val temperature: Double,
    val weathercode: Int
)

sealed class WeatherState {
    object Initial : WeatherState()
    object Loading : WeatherState()
    data class Success(val temperature: Double, val weatherCode: Int) : WeatherState()
    data class Error(val message: String) : WeatherState()
}

class WeatherRepository {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchWeather(lat: Double, lon: Double): WeatherState {
        return withContext(Dispatchers.IO) {
            try {
                val urlString = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current_weather=true"
                val url = URL(urlString)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val responseText = reader.use { it.readText() }
                    
                    val response = json.decodeFromString<OpenMeteoResponse>(responseText)
                    WeatherState.Success(
                        temperature = response.currentWeather.temperature,
                        weatherCode = response.currentWeather.weathercode
                    )
                } else {
                    WeatherState.Error("HTTP Error: ${connection.responseCode}")
                }
            } catch (e: Exception) {
                WeatherState.Error(e.message ?: "Unknown error occurred")
            }
        }
    }
}
