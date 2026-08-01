package org.adaway.model.root

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.TreeSet

/**
 * Tests of [HostEntryPager].
 *
 * Generating the hosts file walks every entry exactly once. A paging bug would either drop hosts,
 * which silently stops blocking them, or repeat them, so these tests check the walk against a known
 * set rather than only checking that it terminates.
 */
class HostEntryPagerTest {
    /**
     * A stand in for the database: returns the hosts sorting strictly after the given one, in
     * order, capped to the requested count.
     */
    private fun fetcherOf(hosts: List<String>): (String, Int) -> List<String> {
        val sorted = TreeSet(hosts)
        return { afterHost, limit -> sorted.tailSet(afterHost, false).take(limit) }
    }

    private fun walk(hosts: List<String>, pageSize: Int): List<String> {
        val visited = mutableListOf<String>()
        val count = HostEntryPager.forEachEntry(
            pageSize = pageSize,
            fetch = fetcherOf(hosts),
            hostOf = { it },
            action = { visited.add(it) }
        )
        assertEquals(count, visited.size)
        return visited
    }

    private fun hosts(count: Int): List<String> =
        (0 until count).map { "host%06d.example".format(it) }

    @Test
    fun emptyTableVisitsNothing() {
        assertEquals(emptyList<String>(), walk(emptyList(), 10))
    }

    @Test
    fun everyEntryIsVisitedOnceInOrder() {
        val all = hosts(250)
        assertEquals(all, walk(all, 10))
    }

    /**
     * The boundaries are where an off by one would show: a page size that divides the total exactly
     * requires one extra fetch to notice the end.
     */
    @Test
    fun pageSizeBoundariesAreHandled() {
        assertEquals(hosts(100), walk(hosts(100), 100))
        assertEquals(hosts(100), walk(hosts(100), 50))
        assertEquals(hosts(100), walk(hosts(100), 99))
        assertEquals(hosts(100), walk(hosts(100), 101))
        assertEquals(hosts(1), walk(hosts(1), 1))
    }

    @Test
    fun singleEntryPagesWalkEverything() {
        assertEquals(hosts(25), walk(hosts(25), 1))
    }

    /**
     * A fetch that never advances must end the walk rather than loop forever.
     */
    @Test
    fun nonAdvancingFetchTerminates() {
        val visited = mutableListOf<String>()
        val count = HostEntryPager.forEachEntry(
            pageSize = 2,
            fetch = { _, _ -> listOf("stuck.example", "stuck.example") },
            hostOf = { it },
            action = { visited.add(it) }
        )
        assertEquals(2, count)
        assertEquals(2, visited.size)
    }

    @Test
    fun invalidPageSizeIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { walk(hosts(5), 0) }
    }
}
