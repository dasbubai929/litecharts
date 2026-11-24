package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OandaTimestampTest {

    @Test
    fun parsesOandaIsoTimestampWithNanosecondFraction() {
        val withFraction = parseOandaEpochSec(
            "2026-11-24T03:15:00.123456789Z"
        )
        val withoutFraction = parseOandaEpochSec(
            "2026-11-24T03:15:00Z"
        )

        assertEquals(withoutFraction, withFraction)
        assertTrue(withFraction > 0L)
    }

    @Test
    fun preservesNumericUnixTimestampSupport() {
        assertEquals(1_800_000_000L, parseOandaEpochSec("1800000000"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidTimestampInsteadOfUsingCurrentTime() {
        parseOandaEpochSec("not-a-timestamp")
    }
}
