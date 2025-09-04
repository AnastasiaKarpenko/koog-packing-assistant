package app

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate
import kotlin.system.exitProcess
import io.github.cdimascio.dotenv.dotenv

fun main() = runBlocking {
    println("== Koog Travel Packing Assistant ==")
    print("City (e.g., Lisbon): "); val city = readln().trim()
    print("Start date (YYYY-MM-DD): "); val start = LocalDate.parse(readln().trim())
    print("End date   (YYYY-MM-DD): "); val end   = LocalDate.parse(readln().trim())
    print("Trip type [BUSINESS|BEACH|CITY|HIKING|SKI|FAMILY|ROMANTIC]: ")
    val tripType = runCatching { TripType.valueOf(readln().trim().uppercase()) }
        .getOrElse { TripType.CITY }

    val dotenv = dotenv()   // loads .env file in project root

    val openWeatherKey = dotenv["OPENWEATHER_API_KEY"] ?: missing("OPENWEATHER_API_KEY")
    val ollamaUrl      = dotenv["OLLAMA_BASE_URL"] ?: "http://localhost:11434"
    val ollamaModel    = dotenv["OLLAMA_MODEL"] ?: "llama3.1:8b"

    val weatherService = WeatherService(openWeatherKey)
    val weatherTool = WeatherTool(weatherService)
    val tripTool = TripContextTool()
    val agent = buildPackingAgent(ollamaUrl, ollamaModel, weatherTool, tripTool)

    // Seed the conversation so the LLM knows what it needs and can call tools
    val days = java.time.Period.between(start.toJavaLocalDate(), end.toJavaLocalDate()).days + 1
    val initialUserMsg = """
        Create a packing list for my trip.
        City="$city"
        Dates=${start}..${end}
        TripType=${tripType.name.lowercase()}

        If you need weather or trip length, call tools:
        - fetch_weather(city="$city", startIso="${start}", endIso="${end}")
        - trip_context(tripType="${tripType.name.lowercase()}", days=$days)
    """.trimIndent()

    val output = agent.run(initialUserMsg)

    println("\n--- PACKING LIST (JSON) ---")
    println(output)
}

private fun missing(name: String): Nothing {
    println("Missing env var: $name")
    exitProcess(2)
}
