package top.e404.statimg.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import top.e404.statimg.platform.buildWakatimeActivity
import top.e404.statimg.platform.levelForWakatimeTotal
import java.time.LocalDate

class TestWakatimeActivity {
    @Test
    fun mapsPublicTotalsToSixLevelsAtExactBoundaries() {
        assertEquals(
            listOf(0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5),
            listOf(0, 3_599, 3_600, 10_800, 10_801, 28_800, 28_801, 36_000, 36_001, 43_200, 43_201)
                .map(::levelForWakatimeTotal)
        )
    }

    @Test
    fun groupsMondayFirstWeeksAcrossYearAndPadsEdges() {
        val activity = buildWakatimeActivity(
            "last year",
            listOf(LocalDate.parse("2025-12-31") to 3_600, LocalDate.parse("2026-01-05") to 0)
        )!!
        assertEquals(listOf(2025 to 12, 2026 to 1), activity.byMonth.keys.toList())
        val decemberWeek = activity.byMonth.getValue(2025 to 12).single()
        assertEquals((1..7).map { LocalDate.parse("2025-12-29").plusDays((it - 1).toLong()) }, decemberWeek.map { it.first })
        assertEquals(listOf(null, null, 1, null, null, null, null), decemberWeek.map { it.second })
        val januaryWeek = activity.byMonth.getValue(2026 to 1).single()
        assertEquals(listOf(0, null, null, null, null, null, null), januaryWeek.map { it.second })
    }

    @Test
    fun distinguishesMissingDatesFromExplicitZeroAndReturnsNullForEmptyData() {
        val activity = buildWakatimeActivity(
            "range",
            listOf(LocalDate.parse("2026-04-06") to 0, LocalDate.parse("2026-04-08") to 3_600)
        )!!
        val week = activity.byMonth.getValue(2026 to 4).single()
        assertEquals(listOf(0, null, 1, null, null, null, null), week.map { it.second })
        assertNull(buildWakatimeActivity("range", emptyList()))
    }
}
