package org.adaway.model.root

import org.junit.Assert.assertEquals
import org.junit.Test

class ProgressReporterTest {
    @Test
    fun percentagesAreBounded() {
        assertEquals(0, ProgressReporter.percentOf(0, 100))
        assertEquals(50, ProgressReporter.percentOf(50, 100))
        assertEquals(100, ProgressReporter.percentOf(100, 100))
        // More items than expected must not report beyond completion.
        assertEquals(100, ProgressReporter.percentOf(150, 100))
        // Nothing to do is complete, not a division by zero.
        assertEquals(100, ProgressReporter.percentOf(0, 0))
        assertEquals(100, ProgressReporter.percentOf(5, -1))
    }

    /**
     * Large totals must not overflow the multiplication before the division.
     */
    @Test
    fun largeTotalsDoNotOverflow() {
        assertEquals(50, ProgressReporter.percentOf(1_150_000, 2_300_000))
        assertEquals(99, ProgressReporter.percentOf(2_299_999, 2_300_000))
        assertEquals(100, ProgressReporter.percentOf(2_300_000, 2_300_000))
    }

    @Test
    fun everyPercentageIsReportedOnceInOrder() {
        val reported = mutableListOf<Int>()
        val reporter = ProgressReporter(2_300_000) { reported.add(it) }
        repeat(2_300_000) { reporter.increment() }
        // One report per distinct whole percentage, from 0 to 100 inclusive.
        assertEquals((0..100).toList(), reported)
    }

    @Test
    fun smallTotalsStillReachCompletion() {
        val reported = mutableListOf<Int>()
        val reporter = ProgressReporter(3) { reported.add(it) }
        repeat(3) { reporter.increment() }
        assertEquals(listOf(33, 66, 100), reported)
    }

    @Test
    fun anEmptyRunReportsNothing() {
        val reported = mutableListOf<Int>()
        ProgressReporter(0) { reported.add(it) }
        assertEquals(emptyList<Int>(), reported)
    }
}
