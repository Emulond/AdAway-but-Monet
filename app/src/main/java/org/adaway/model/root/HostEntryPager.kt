package org.adaway.model.root

/**
 * Walks the host entries in pages, so generating the hosts file never holds more than one page in
 * memory.
 *
 * Paging is done by host name rather than by offset: the host column is unique and the pages are
 * ordered by it, so each page starts exactly where the previous one ended. No entry can be visited
 * twice or skipped, and the database seeks straight to the next page through the index instead of
 * counting rows it then discards.
 */
object HostEntryPager {
    /**
     * The default number of entries read at a time.
     */
    const val DEFAULT_PAGE_SIZE = 5_000

    /**
     * Visit every entry, in host order.
     *
     * @param pageSize The number of entries read at a time. Must be strictly positive.
     * @param fetch Reads the entries whose host sorts strictly after the given one, in host order,
     * limited to the given count.
     * @param action Called once per entry.
     * @return The number of entries visited.
     */
    @JvmStatic
    fun <T> forEachEntry(
        pageSize: Int = DEFAULT_PAGE_SIZE,
        fetch: (afterHost: String, limit: Int) -> List<T>,
        hostOf: (T) -> String,
        action: (T) -> Unit
    ): Int {
        require(pageSize > 0) { "Page size must be strictly positive but was $pageSize" }
        var visited = 0
        // The empty string sorts before every host name, so the first page starts at the beginning.
        var afterHost = ""
        while (true) {
            val page = fetch(afterHost, pageSize)
            if (page.isEmpty()) {
                return visited
            }
            val lastHost = hostOf(page.last())
            // Guard against a fetch that does not advance. Checked before the page is visited, so
            // a page that repeats the previous one is discarded rather than emitted twice.
            if (lastHost <= afterHost) {
                return visited
            }
            for (entry in page) {
                action(entry)
                visited++
            }
            afterHost = lastHost
            // A short page means the end of the table was reached.
            if (page.size < pageSize) {
                return visited
            }
        }
    }
}
