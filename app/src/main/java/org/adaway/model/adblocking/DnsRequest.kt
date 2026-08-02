package org.adaway.model.adblocking

import java.time.Instant

/**
 * A DNS request seen while recording.
 *
 * The request carries no information about which application made it: the capture reads packets
 * off the network, where nothing identifies the process behind them. On top of that, the system
 * resolver makes the lookup on behalf of the application, so the packet belongs to the resolver
 * rather than to whichever application asked for the host name.
 *
 * @property host The requested host name.
 * @property lastSeen When the request was last seen, or `null` when the recording carries no time.
 */
data class DnsRequest(
    val host: String,
    val lastSeen: Instant? = null
)
