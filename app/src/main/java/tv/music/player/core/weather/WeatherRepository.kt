package tv.music.player.core.weather

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class WeatherKind { SUN, MOON, CLOUD, RAIN, SNOW, THUNDER, FOG, UNKNOWN }

data class WeatherSnapshot(
    val city: String,
    val tempC: Int,
    val condition: String,
    val kind: WeatherKind,
)

private data class CnCity(
    val id: Int,
    val name: String,
    val lat: Double,
    val lon: Double,
    val population: Int,
)

private data class IpPlace(val lat: Double, val lon: Double, val country: String)

/**
 * 天气链路：IP 定位 → GeoNames 国内城市表就近匹配 → Open-Meteo 当前天气。
 * 城市表随 assets 打包，数据来自 GeoNames（CC BY 4.0）。
 */
class WeatherRepository(context: Context) {

    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("weather", Context.MODE_PRIVATE)
    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(12, TimeUnit.SECONDS)
        .build()
    private val cities: List<CnCity> by lazy { loadCities() }

    @Volatile
    private var cached: WeatherSnapshot? = null

    @Volatile
    private var cachedAt = 0L

    suspend fun current(): WeatherSnapshot? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        cached?.let { if (now - cachedAt < 30 * 60_000L) return@withContext it }
        val city = resolveCity(now) ?: return@withContext cached
        val snapshot = fetchWeather(city) ?: return@withContext cached
        cached = snapshot
        cachedAt = now
        snapshot
    }

    private fun resolveCity(now: Long): CnCity? {
        val storedId = prefs.getInt(KEY_CITY_ID, 0)
        val stored = cities.firstOrNull { it.id == storedId }
        if (stored != null && now - prefs.getLong(KEY_CITY_AT, 0L) < 7 * 24 * 3_600_000L) {
            return stored
        }
        val place = fetchIpPlace() ?: return stored
        if (!place.country.equals("CN", ignoreCase = true)) return stored
        val nearest = nearestCity(place.lat, place.lon) ?: return stored
        prefs.edit().putInt(KEY_CITY_ID, nearest.id).putLong(KEY_CITY_AT, now).apply()
        return nearest
    }

    private fun nearestCity(lat: Double, lon: Double): CnCity? {
        val cities = cities.ifEmpty { return null }
        return cities.minByOrNull { city ->
            val dx = (city.lon - lon) * cos(Math.toRadians(lat))
            val dy = city.lat - lat
            val km = sqrt(dx * dx + dy * dy) * 111.0
            val metroBias = if (city.population > 0) {
                (log10(city.population.toDouble()) - 3.0).coerceAtLeast(0.0) * 6.0
            } else 0.0
            km - metroBias
        }
    }

    private fun fetchIpPlace(): IpPlace? {
        fetchIpinfo()?.let { return it }
        return fetchWttrArea()
    }

    private fun fetchIpinfo(): IpPlace? = runCatching {
        val json = JSONObject(get("https://ipinfo.io/json"))
        val parts = json.optString("loc").split(',')
        if (parts.size != 2) return@runCatching null
        val lat = parts[0].trim().toDoubleOrNull() ?: return@runCatching null
        val lon = parts[1].trim().toDoubleOrNull() ?: return@runCatching null
        IpPlace(lat, lon, json.optString("country"))
    }.getOrNull()

    private fun fetchWttrArea(): IpPlace? = runCatching {
        val json = JSONObject(get("https://wttr.in/?format=j1"))
        val area = json.getJSONArray("nearest_area").getJSONObject(0)
        val lat = area.optString("latitude").toDoubleOrNull() ?: return@runCatching null
        val lon = area.optString("longitude").toDoubleOrNull() ?: return@runCatching null
        val country = area.optJSONArray("country")
            ?.optJSONObject(0)
            ?.optString("value")
            .orEmpty()
        IpPlace(lat, lon, country)
    }.getOrNull()

    private fun fetchWeather(city: CnCity): WeatherSnapshot? = runCatching {
        val url = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=${city.lat}&longitude=${city.lon}" +
            "&current=temperature_2m,weather_code,is_day&timezone=auto"
        val current = JSONObject(get(url)).optJSONObject("current") ?: return@runCatching null
        if (!current.has("temperature_2m")) return@runCatching null
        val code = current.optInt("weather_code", -1)
        if (code !in 0..99) return@runCatching null
        val isDay = current.optInt("is_day", 1) != 0
        val (condition, kind) = describe(code, isDay)
        WeatherSnapshot(
            city = city.name,
            tempC = current.getDouble("temperature_2m").roundToInt(),
            condition = condition,
            kind = kind,
        )
    }.getOrNull()

    private fun get(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "MuYinTV/1.0 (Android TV)")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")
            return response.body.string()
        }
    }

    private fun loadCities(): List<CnCity> = runCatching {
        val text = app.assets.open(CITY_ASSET).bufferedReader(Charsets.UTF_8).use { it.readText() }
        val array = JSONArray(text)
        buildList(array.length()) {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val name = item.optString("name").trim()
                val lat = item.optDouble("lat", Double.NaN)
                val lon = item.optDouble("lon", Double.NaN)
                if (name.isBlank() || lat.isNaN() || lon.isNaN()) continue
                add(
                    CnCity(
                        id = item.optInt("id"),
                        name = name,
                        lat = lat,
                        lon = lon,
                        population = item.optInt("pop"),
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    private fun describe(code: Int, isDay: Boolean): Pair<String, WeatherKind> = when (code) {
        0 -> "晴" to if (isDay) WeatherKind.SUN else WeatherKind.MOON
        1 -> "晴间多云" to if (isDay) WeatherKind.SUN else WeatherKind.MOON
        2 -> "多云" to WeatherKind.CLOUD
        3 -> "阴" to WeatherKind.CLOUD
        45, 48 -> "雾" to WeatherKind.FOG
        51, 53, 55 -> "毛毛雨" to WeatherKind.RAIN
        56, 57, 66, 67 -> "冻雨" to WeatherKind.RAIN
        61 -> "小雨" to WeatherKind.RAIN
        63 -> "中雨" to WeatherKind.RAIN
        65 -> "大雨" to WeatherKind.RAIN
        71 -> "小雪" to WeatherKind.SNOW
        73 -> "中雪" to WeatherKind.SNOW
        75 -> "大雪" to WeatherKind.SNOW
        77 -> "米雪" to WeatherKind.SNOW
        80 -> "阵雨" to WeatherKind.RAIN
        81 -> "强阵雨" to WeatherKind.RAIN
        82 -> "暴雨" to WeatherKind.RAIN
        85, 86 -> "阵雪" to WeatherKind.SNOW
        95 -> "雷阵雨" to WeatherKind.THUNDER
        96, 99 -> "雷暴冰雹" to WeatherKind.THUNDER
        else -> "天气" to WeatherKind.UNKNOWN
    }

    private companion object {
        const val CITY_ASSET = "geonames_cn.json"
        const val KEY_CITY_ID = "city_id"
        const val KEY_CITY_AT = "city_at"
    }
}
