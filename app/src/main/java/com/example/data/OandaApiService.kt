package com.example.data

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.io.IOException
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class OandaAccountsResponse(
    val accounts: List<OandaAccountSummary> = emptyList()
)

@JsonClass(generateAdapter = true)
data class OandaAccountSummary(
    val id: String
)

@JsonClass(generateAdapter = true)
data class OandaCandlesResponse(
    val instrument: String = "",
    val granularity: String = "",
    val candles: List<OandaCandleDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class OandaCandleDto(
    val complete: Boolean = false,
    val volume: Long = 0L,
    val time: String = "",
    val mid: OandaMidDto? = null
)

@JsonClass(generateAdapter = true)
data class OandaMidDto(
    val o: String = "0",
    val h: String = "0",
    val l: String = "0",
    val c: String = "0"
)

@JsonClass(generateAdapter = true)
data class OandaStreamBucket(
    val price: String = "0",
    val liquidity: Long = 0L
)

@JsonClass(generateAdapter = true)
data class OandaPriceStreamMessage(
    val type: String = "",
    val time: String = "",
    val instrument: String = "",
    val bids: List<OandaStreamBucket>? = null,
    val asks: List<OandaStreamBucket>? = null,
    val closeoutBid: String? = null,
    val closeoutAsk: String? = null,
    val tradeable: Boolean? = null
)

interface OandaRestApi {
    @GET("v3/accounts")
    suspend fun getAccounts(): OandaAccountsResponse

    @GET("v3/instruments/{instrument}/candles")
    suspend fun getCandles(
        @Path("instrument") instrument: String,
        @Query("price") price: String = "M",
        @Query("granularity") granularity: String,
        @Query("count") count: Int = 250,
        @Query("dailyAlignment") dailyAlignment: Int = 17,
        @Query("alignmentTimezone") alignmentTimezone: String = "America/New_York"
    ): OandaCandlesResponse
}

sealed interface OandaStreamEvent {
    data object Connected : OandaStreamEvent
    data class Heartbeat(val timeEpochSec: Long) : OandaStreamEvent
    data class PriceTick(
        val instrument: String,
        val bid: Double,
        val ask: Double,
        val timeEpochSec: Long
    ) : OandaStreamEvent
}

object OandaNetworkFactory {
    const val PRACTICE_REST_BASE_URL = "https://api-fxpractice.oanda.com/"
    const val PRACTICE_STREAM_BASE_URL = "https://stream-fxpractice.oanda.com/"

    val moshi: Moshi by lazy {
        Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }

    fun createRestApi(apiKey: String): OandaRestApi {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("Authorization", "Bearer $apiKey")
                    .header("Accept-Datetime-Format", "UNIX")
                    .header("Accept", "application/json")
                    .build()
                chain.proceed(req)
            }
            .build()

        return Retrofit.Builder()
            .baseUrl(PRACTICE_REST_BASE_URL)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(OandaRestApi::class.java)
    }

    fun createStreamingClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS) // Long-lived streaming response
            .retryOnConnectionFailure(true)
            .build()
    }
}

class OandaStreamingClient(
    private val httpClient: OkHttpClient,
    private val moshi: Moshi,
    private val apiKey: String
) {
    private val messageAdapter = moshi.adapter(OandaPriceStreamMessage::class.java)

    fun streamPricing(accountId: String, instrument: String): Flow<OandaStreamEvent> = flow {
        val url = "${OandaNetworkFactory.PRACTICE_STREAM_BASE_URL}v3/accounts/$accountId/pricing/stream?instruments=$instrument"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .header("Accept-Datetime-Format", "UNIX")
            .header("Accept", "application/json")
            .get()
            .build()

        val call = httpClient.newCall(request)
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("OANDA stream HTTP ${response.code}: ${response.message}")
                }
                emit(OandaStreamEvent.Connected)
                val body = response.body ?: throw IOException("Empty OANDA stream body")
                val reader = body.charStream().buffered()
                while (currentCoroutineContext().isActive) {
                    val line = reader.readLine() ?: break
                    val trimmed = line.trim()
                    if (trimmed.isEmpty()) continue
                    val msg = runCatching { messageAdapter.fromJson(trimmed) }.getOrNull() ?: continue
                    val epochSec = parseOandaEpochSec(msg.time)
                    when (msg.type) {
                        "HEARTBEAT" -> emit(OandaStreamEvent.Heartbeat(epochSec))
                        "PRICE" -> {
                            val bid = msg.bids?.firstOrNull()?.price?.toDoubleOrNull()
                                ?: msg.closeoutBid?.toDoubleOrNull()
                            val ask = msg.asks?.firstOrNull()?.price?.toDoubleOrNull()
                                ?: msg.closeoutAsk?.toDoubleOrNull()
                            if (bid != null && ask != null && bid > 0.0 && ask > 0.0) {
                                emit(
                                    OandaStreamEvent.PriceTick(
                                        instrument = msg.instrument.ifBlank { instrument },
                                        bid = bid,
                                        ask = ask,
                                        timeEpochSec = epochSec
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } finally {
            call.cancel()
        }
    }.flowOn(Dispatchers.IO)
}

internal fun parseOandaEpochSec(rawTime: String): Long {
    val value = rawTime.trim()

    // Retain support for numeric Unix timestamps if supplied.
    value.toLongOrNull()?.let { return it }

    // OANDA returns RFC3339/ISO-8601 UTC timestamps, usually with
    // fractional seconds such as .000000000Z. Candle precision here
    // is one second, so the fractional part can safely be ignored.
    val isoPrefix = Regex(
        """^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?Z$"""
    )
    require(isoPrefix.matches(value)) {
        "Invalid OANDA timestamp: $rawTime"
    }

    val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
        isLenient = false
        timeZone = TimeZone.getTimeZone("UTC")
    }
    val position = ParsePosition(0)
    val parsed = formatter.parse(value.substring(0, 19), position)
    require(parsed != null && position.index == 19) {
        "Unable to parse OANDA timestamp: $rawTime"
    }
    return parsed.time / 1000L
}
