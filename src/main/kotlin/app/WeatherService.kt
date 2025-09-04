package app

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

class WeatherService(
    private val apiKey: String,
    private val http: HttpClient = HttpClient(CIO) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }
) {
    @Serializable private data class GeoItem(val name: String, val country: String? = null, val lat: Double, val lon: Double)
    @Serializable private data class ForecastResponse(val list: List<Item>, val city: City) {
        @Serializable data class City(val name: String, val country: String? = null)
        @Serializable data class Item(
            val dt_txt: String,
            val main: Main,
            val weather: List<W>,
            val pop: Double = 0.0
        ) {
            @Serializable data class Main(val temp_min: Double, val temp_max: Double)
            @Serializable data class W(val description: String)
        }
    }

    suspend fun fetch(city: String, startIso: String, endIso: String): WeatherSummary {
        val geo = http.get("https://api.openweathermap.org/geo/1.0/direct") {
            parameter("q", city)
            parameter("limit", 1)
            parameter("appid", apiKey)
        }.body<List<GeoItem>>().firstOrNull()
            ?: error("City not found: $city")

        val forecast = http.get("https://api.openweathermap.org/data/2.5/forecast") {
            parameter("lat", geo.lat)
            parameter("lon", geo.lon)
            parameter("appid", apiKey)
            parameter("units", "metric")
        }.body<ForecastResponse>()

        // group 3-hour entries into daily aggregates
        val grouped = forecast.list.groupBy { it.dt_txt.substring(0, 10) } // YYYY-MM-DD
        val days = grouped.entries.map { (date, items) ->
            val min = items.minOf { it.main.temp_min }
            val max = items.maxOf { it.main.temp_max }
            val avgPop = items.map { it.pop }.average()
            val commonDesc = items.flatMap { it.weather }.groupBy { it.description }
                .maxByOrNull { it.value.size }?.key ?: "—"
            DailyForecast(
                date = date,
                tempMinC = min,
                tempMaxC = max,
                precipProb = (avgPop * 100).roundToInt() / 100.0,
                description = commonDesc
            )
        }.sortedBy { it.date }

        return WeatherSummary(
            city = forecast.city.name,
            country = forecast.city.country,
            days = days
        )
    }
}
