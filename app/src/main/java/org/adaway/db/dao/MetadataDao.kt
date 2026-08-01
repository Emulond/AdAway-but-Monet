package org.adaway.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.adaway.db.entity.Metadata
import java.util.UUID

@Dao
interface MetadataDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun put(metadata: Metadata)

    @Query("SELECT `value` FROM `metadata` WHERE `key` = :key LIMIT 1")
    fun get(key: String): String?

    /**
     * Record that the host entries were rebuilt.
     * Must run in the same transaction as the rebuild, so the recorded revision and the entries it
     * describes can never disagree.
     */
    fun markHostEntriesRebuilt() {
        put(Metadata().apply {
            key = HOST_ENTRIES_REVISION
            value = UUID.randomUUID().toString()
        })
    }

    /**
     * Get the revision of the host entries.
     *
     * @return The revision, or an empty string when the entries were never rebuilt.
     */
    fun getHostEntriesRevision(): String = get(HOST_ENTRIES_REVISION).orEmpty()

    companion object {
        private const val HOST_ENTRIES_REVISION = "hostEntriesRevision"
    }
}
