package org.adaway.db

import org.adaway.db.entity.ListType

/**
 * The cached counts of blocked, allowed and redirected hosts.
 *
 * Counting the distinct hosts of a type scans millions of rows, which is far too slow to do every
 * time the home screen is shown. The counts are computed when the host lists change and stored, so
 * the screen reads the last computed value immediately.
 *
 * A stored count only ever lags: it is recomputed whenever the lists are rebuilt and again when the
 * home screen opens, so a value that missed a change is corrected shortly after it is displayed.
 */
object HostCounts {
    private val TYPES = listOf(ListType.BLOCKED, ListType.ALLOWED, ListType.REDIRECTED)

    /**
     * Recompute and store every host count. Blocking: run it off the main thread.
     */
    @JvmStatic
    fun refresh(database: AppDatabase) {
        val hostListItemDao = database.hostsListItemDao()
        val metadataDao = database.metadataDao()
        for (type in TYPES) {
            metadataDao.setHostCount(type, hostListItemDao.countHosts(type.value))
        }
    }
}
