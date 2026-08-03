package org.adaway.model.root;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.adaway.model.adblocking.DnsRequest;
import org.junit.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Stream;

/**
 * This class tests the reading of the capture output.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class TcpdumpParsingTest {
    @Test
    public void readsHostAndTimeFromOneLine() {
        List<DnsRequest> requests = TcpdumpUtils.parseRequests(Stream.of(
                "2026-08-03 10:15:42.123456 IP 10.0.0.1.44444 > 8.8.8.8.53: "
                        + "12345+ A? example.com. (32)"
        ));

        assertEquals(1, requests.size());
        assertEquals("example.com", requests.get(0).getHost());
        assertEquals(
                at(2026, 8, 3, 10, 15, 42),
                requests.get(0).getLastSeen()
        );
    }

    /**
     * A verbose capture prints the packet header, which carries the time, and the packet content,
     * which carries the host name, on separate lines. The request still has to come out with its
     * time.
     */
    @Test
    public void readsTimeFromThePrecedingHeaderLine() {
        List<DnsRequest> requests = TcpdumpUtils.parseRequests(Stream.of(
                "2026-08-03 10:15:42.123456 IP (tos 0x0, ttl 64, id 1, offset 0, "
                        + "flags [DF], proto UDP (17), length 60)",
                "    10.0.0.1.44444 > 8.8.8.8.53: 12345+ AAAA? example.com. (32)"
        ));

        assertEquals(1, requests.size());
        assertEquals("example.com", requests.get(0).getHost());
        assertEquals(
                at(2026, 8, 3, 10, 15, 42),
                requests.get(0).getLastSeen()
        );
    }

    @Test
    public void readsALineCarryingOnlyATimeOfDay() {
        List<DnsRequest> requests = TcpdumpUtils.parseRequests(Stream.of(
                "10:15:42.123456 IP 10.0.0.1.44444 > 8.8.8.8.53: 12345+ A? example.com. (32)"
        ));

        assertEquals(1, requests.size());
        assertNotNull(requests.get(0).getLastSeen());
    }

    @Test
    public void keepsALineWithoutATime() {
        List<DnsRequest> requests = TcpdumpUtils.parseRequests(Stream.of(
                "IP 10.0.0.1.44444 > 8.8.8.8.53: 12345+ A? example.com. (32)"
        ));

        assertEquals(1, requests.size());
        assertEquals("example.com", requests.get(0).getHost());
        assertNull(requests.get(0).getLastSeen());
    }

    @Test
    public void reportsAHostOnceWithTheTimeItWasLastRequested() {
        List<DnsRequest> requests = TcpdumpUtils.parseRequests(Stream.of(
                "2026-08-03 10:15:42.123456 IP 10.0.0.1.44444 > 8.8.8.8.53: "
                        + "1+ A? example.com. (32)",
                "2026-08-03 10:16:00.000000 IP 10.0.0.1.44444 > 8.8.8.8.53: "
                        + "2+ A? other.com. (32)",
                "2026-08-03 11:30:07.000000 IP 10.0.0.1.44444 > 8.8.8.8.53: "
                        + "3+ A? example.com. (32)"
        ));

        assertEquals(2, requests.size());
        // The order is the one the hosts first appeared in.
        assertEquals("example.com", requests.get(0).getHost());
        assertEquals("other.com", requests.get(1).getHost());
        assertEquals(at(2026, 8, 3, 11, 30, 7), requests.get(0).getLastSeen());
    }

    @Test
    public void ignoresALineWithoutARequest() {
        List<DnsRequest> requests = TcpdumpUtils.parseRequests(Stream.of(
                "tcpdump: data link type LINUX_SLL2",
                "tcpdump: listening on any, link-type LINUX_SLL2, snapshot length 512 bytes"
        ));

        assertEquals(0, requests.size());
    }

    private static Instant at(int year, int month, int day, int hour, int minute, int second) {
        return ZonedDateTime.of(year, month, day, hour, minute, second, 0, ZoneId.systemDefault())
                .toInstant();
    }
}
