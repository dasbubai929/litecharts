package com.example.indicators

import com.example.model.Candle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyVwapTest {

    @Test
    fun dailyVwapResetsAtNativeDailyCandleBoundary() {
        // Synthetic daily boundaries deliberately do not coincide with UTC
        // midnight, matching the need for native OANDA D1 alignment.
        val dayOneStart = 1_800_000_000L
        val dayTwoStart = dayOneStart + 86_400L

        val candles = listOf(
            candle(dayOneStart + 60L, 10.0),
            candle(dayOneStart + 120L, 20.0),
            candle(dayTwoStart + 60L, 30.0)
        )

        val dailyCandles = listOf(
            candle(dayOneStart, 10.0),
            candle(dayTwoStart, 30.0)
        )

        val (values, breaks) = PineTa.dailyVwap(
            candles,
            doubleArrayOf(10.0, 20.0, 30.0),
            dailyCandles
        )

        assertEquals(10.0, values[0], 0.000001)
        assertEquals(15.0, values[1], 0.000001)
        assertEquals(30.0, values[2], 0.000001)

        assertFalse(breaks[0])
        assertFalse(breaks[1])
        assertTrue(breaks[2])
    }

    @Test
    fun legacyDailyVwapOverloadRemainsAvailable() {
        val candles = listOf(
            candle(1_800_000_000L, 10.0),
            candle(1_800_000_060L, 20.0)
        )

        val (values, breaks) = PineTa.dailyVwap(
            candles,
            doubleArrayOf(10.0, 20.0)
        )

        assertEquals(10.0, values[0], 0.000001)
        assertEquals(15.0, values[1], 0.000001)
        assertFalse(breaks[0])
        assertFalse(breaks[1])
    }

    private fun candle(time: Long, price: Double) = Candle(
        timeEpochSec = time,
        open = price,
        high = price,
        low = price,
        close = price,
        volume = 1L
    )
}
