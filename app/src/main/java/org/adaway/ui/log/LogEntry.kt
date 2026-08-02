package org.adaway.ui.log

import org.adaway.db.entity.ListType
import java.time.Instant

/**
 * This class represents a DNS request log entry.
 *
 * @property host The requested host name.
 * @property type How the host is listed, or `null` when it is not listed.
 * @property lastSeen When the request was last recorded, or `null` when the recording carries no
 * time. Nothing identifies the application behind a request: the capture reads packets off the
 * network, and the system resolver makes the lookup on every application's behalf.
 */
data class LogEntry(
    val host: String,
    var type: ListType? = null,
    val lastSeen: Instant? = null
) : Comparable<LogEntry> {
    override fun compareTo(other: LogEntry): Int {
        return this.host.compareTo(other.host)
    }
}
