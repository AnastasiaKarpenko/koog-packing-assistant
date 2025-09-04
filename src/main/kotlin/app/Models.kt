package app

import kotlinx.serialization.Serializable
import kotlinx.datetime.LocalDate


enum class TripType { BUSINESS, BEACH, CITY, HIKING, SKI, FAMILY, ROMANTIC }

@Serializable
data class UserTripInput(
    val city: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val tripType: TripType
)

@Serializable
data class DailyForecast(
    val date: String,
    val tempMinC: Double,
    val tempMaxC: Double,
    val precipProb: Double,
    val description: String
)

@Serializable
data class WeatherSummary(
    val city: String,
    val country: String?,
    val days: List<DailyForecast>
)

/** What we want the LLM to return */
@Serializable
data class PackingList(
    val mustHave: List<String>,
    val clothing: List<String>,
    val footwear: List<String>,
    val accessories: List<String>,
    val toiletries: List<String>,
    val gadgets: List<String>,
    val documents: List<String>,
    val optional: List<String>,
    val tips: List<String>,
    val weather: WeatherSummary?
)
