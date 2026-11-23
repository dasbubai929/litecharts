package com.litecharts.app.data

import com.litecharts.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Fetches candlestick data from OANDA's Practice REST API v3.
 *
 * The API key is injected at build time via [BuildConfig.OANDA_API_KEY]
 * (sourced from `local.properties`).
 */
object OandaApiService {

    private const val BASE_URL = "https://api-fxpractice.oanda.com/v3"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val apiKey: String get() = BuildConfig.OANDA_API_KEY

    /**
     * Fetch [count] candles for [instrument] at the given [granularity].
     * Returns a [Result] so callers get clean error messages in the UI.
     */
    suspend fun fetchCandles(
        instrument: String,
        granularity: String,
        count: Int = 300
    ): Result<List<OandaCandle>> = withContext(Dispatchers.IO) {
        try {
            // ── Guard: key not configured ──
            if (apiKey == "YOUR_OANDA_API_KEY_HERE" || apiKey.isBlank()) {
                return@withContext Result.failure(
                    IllegalStateException(
                        "OANDA API key not set.\nAdd OANDA_API_KEY=<key> to local.properties and rebuild."
                    )
                )
            }

            val url = "$BASE_URL/instruments/$instrument/candles" +
                    "?price=M&granularity=$granularity&count=$count"

            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $apiKey")
                .header("Accept-Datetime-Format", "UNIX")
                .get()
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                val body = response.body?.string() ?: "Unknown error"
                return@withContext Result.failure(
                    RuntimeException("OANDA ${response.code}: $body")
                )
            }

            val body = response.body?.string()
                ?: return@withContext Result.failure(RuntimeException("Empty OANDA response"))

            val json = JSONObject(body)
            val arr = json.getJSONArray("candles")
            val candles = mutableListOf<OandaCandle>()

            for (i in 0 until arr.length()) {
                val c = arr.getJSONObject(i)
                val mid = c.getJSONObject("mid")
                candles += OandaCandle(
                    time  = c.getString("time").substringBefore(".").toLong(),
                    open  = mid.getString("o").toDouble(),
                    high  = mid.getString("h").toDouble(),
                    low   = mid.getString("l").toDouble(),
                    close = mid.getString("c").toDouble(),
                    volume = c.optInt("volume", 0)
                )
            }

            Result.success(candles)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
