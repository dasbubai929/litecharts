package com.example.data

import com.example.BuildConfig
import com.example.model.Candle
import com.example.model.Timeframe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException

sealed interface StreamFeedEvent {
    data object Connecting : StreamFeedEvent
    data object Connected : StreamFeedEvent
    data class Reconnecting(val reason: String) : StreamFeedEvent
    data class Tick(
        val instrument: String,
        val bid: Double,
        val ask: Double,
        val timeEpochSec: Long
    ) : StreamFeedEvent
}

class OandaRepository(
    private val apiKey: String = BuildConfig.OANDA_API_KEY
) {
    private val restApi: OandaRestApi by lazy {
        OandaNetworkFactory.createRestApi(apiKey)
    }

    private val streamingClient: OandaStreamingClient by lazy {
        OandaStreamingClient(
            httpClient = OandaNetworkFactory.createStreamingClient(),
            moshi = OandaNetworkFactory.moshi,
            apiKey = apiKey
        )
    }

    @Volatile
    private var cachedAccountId: String? = null

    fun isApiKeyConfigured(): Boolean {
        val trimmed = apiKey.trim()
        return trimmed.isNotEmpty() && trimmed != "MY_OANDA_API_KEY"
    }

    suspend fun fetchCompletedCandles(
        instrument: String,
        timeframe: Timeframe,
        count: Int = 420
    ): Result<List<Candle>> = withContext(Dispatchers.IO) {
        if (!isApiKeyConfigured()) {
            return@withContext Result.failure(IllegalStateException("OANDA_API_KEY is not configured."))
        }
        runCatching {
            val response = restApi.getCandles(
                instrument = instrument,
                price = "M",
                granularity = timeframe.oandaGranularity,
                count = count
            )
            response.candles
                .asSequence()
                .filter { it.complete && it.mid != null }
                .mapNotNull { dto ->
                    val mid = dto.mid ?: return@mapNotNull null
                    val o = mid.o.toDoubleOrNull() ?: return@mapNotNull null
                    val h = mid.h.toDoubleOrNull() ?: return@mapNotNull null
                    val l = mid.l.toDoubleOrNull() ?: return@mapNotNull null
                    val c = mid.c.toDoubleOrNull() ?: return@mapNotNull null
                    Candle(
                        timeEpochSec = parseOandaEpochSec(dto.time),
                        open = o,
                        high = h,
                        low = l,
                        close = c,
                        volume = dto.volume,
                        isComplete = true
                    )
                }
                .sortedBy { it.timeEpochSec }
                .toList()
        }.recoverCatching { err ->
            throw mapReadableError(err)
        }
    }

    /**
     * Fetches multiple higher timeframes concurrently for DCC Master
     * (HTF EMA 1, ADX, ATR SL, TSM HTF1/2/3, and Daily DO/PDH/PDL).
     */
    suspend fun fetchHtfCandlesBundle(
        instrument: String,
        timeframes: Set<Timeframe>,
        count: Int = 180
    ): Map<Timeframe, List<Candle>> = coroutineScope {
        if (!isApiKeyConfigured() || timeframes.isEmpty()) return@coroutineScope emptyMap()
        timeframes.map { tf ->
            tf to async(Dispatchers.IO) {
                fetchCompletedCandles(instrument, tf, count).getOrNull().orEmpty()
            }
        }.associate { (tf, deferred) ->
            tf to deferred.await()
        }
    }

    private suspend fun discoverAccountId(): String = withContext(Dispatchers.IO) {
        cachedAccountId?.let { return@withContext it }
        val response = restApi.getAccounts()
        val id = response.accounts.firstOrNull()?.id
            ?: throw IllegalStateException("No OANDA Practice accounts found for this token.")
        cachedAccountId = id
        id
    }

    fun observeLivePricing(instrument: String): Flow<StreamFeedEvent> = flow {
        if (!isApiKeyConfigured()) {
            return@flow
        }
        var backoffMs = 2_000L
        while (true) {
            emit(StreamFeedEvent.Connecting)
            try {
                val accountId = discoverAccountId()
                streamingClient.streamPricing(accountId, instrument).collect { event ->
                    when (event) {
                        is OandaStreamEvent.Connected -> {
                            backoffMs = 2_000L
                            emit(StreamFeedEvent.Connected)
                        }
                        is OandaStreamEvent.Heartbeat -> {
                            // Keeps stream healthy
                        }
                        is OandaStreamEvent.PriceTick -> {
                            emit(
                                StreamFeedEvent.Tick(
                                    instrument = event.instrument,
                                    bid = event.bid,
                                    ask = event.ask,
                                    timeEpochSec = event.timeEpochSec
                                )
                            )
                        }
                    }
                }
                emit(StreamFeedEvent.Reconnecting("Stream closed"))
            } catch (e: HttpException) {
                val readable = mapReadableError(e).message ?: "HTTP ${e.code()}"
                emit(StreamFeedEvent.Reconnecting(readable))
                if (e.code() == 401 || e.code() == 403) {
                    // Invalid key: stop tight reconnect loop
                    delay(30_000L)
                    continue
                }
            } catch (e: IOException) {
                emit(StreamFeedEvent.Reconnecting(e.message ?: "Network interrupted"))
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                emit(StreamFeedEvent.Reconnecting(e.message ?: "Stream error"))
            }
            delay(backoffMs)
            backoffMs = (backoffMs * 2).coerceAtMost(30_000L)
        }
    }

    private fun mapReadableError(error: Throwable): Throwable {
        return when (error) {
            is HttpException -> when (error.code()) {
                401, 403 -> IllegalStateException("OANDA rejected the API key (HTTP ${error.code()}). Verify OANDA_API_KEY in AI Studio Secrets.")
                400 -> IllegalStateException("Invalid OANDA instrument or granularity request.")
                else -> IllegalStateException("OANDA API error (HTTP ${error.code()}).")
            }
            is IOException -> IllegalStateException("Network error contacting OANDA Practice servers. Check connection and retry.")
            else -> error
        }
    }
}
