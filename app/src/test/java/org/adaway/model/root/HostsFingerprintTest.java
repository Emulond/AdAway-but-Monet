package org.adaway.model.root;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.List;

/**
 * Tests of {@link HostsFingerprint}.
 *
 * The generated hosts file is only reused when its embedded fingerprint matches the current one, so
 * these tests cover the property the reuse relies on: any change to any input must change the
 * fingerprint.
 */
public class HostsFingerprintTest {
    private static final String REVISION = "0b6d1a4e-1f2b-4c3d-8e9f-0a1b2c3d4e5f";
    private static final List<String> SOURCES = asList("1:AdAway:https://adaway.org/hosts.txt",
            "2:StevenBlack:https://example.org/hosts");

    private static String reference() {
        return HostsFingerprint.compute(REVISION, 2_300_000, "127.0.0.1", "::1", true, SOURCES);
    }

    @Test
    public void sameInputsGiveSameFingerprint() {
        assertEquals(reference(), reference());
    }

    @Test
    public void revisionChangeIsDetected() {
        String other = HostsFingerprint.compute(
                "11111111-2222-3333-4444-555555555555", 2_300_000, "127.0.0.1", "::1", true, SOURCES);
        assertNotEquals(reference(), other);
    }

    @Test
    public void entryCountChangeIsDetected() {
        String other = HostsFingerprint.compute(REVISION, 2_300_001, "127.0.0.1", "::1", true, SOURCES);
        assertNotEquals(reference(), other);
    }

    @Test
    public void redirectionIpv4ChangeIsDetected() {
        String other = HostsFingerprint.compute(REVISION, 2_300_000, "0.0.0.0", "::1", true, SOURCES);
        assertNotEquals(reference(), other);
    }

    @Test
    public void redirectionIpv6ChangeIsDetected() {
        String other = HostsFingerprint.compute(REVISION, 2_300_000, "127.0.0.1", "::", true, SOURCES);
        assertNotEquals(reference(), other);
    }

    @Test
    public void ipv6ToggleIsDetected() {
        String other = HostsFingerprint.compute(REVISION, 2_300_000, "127.0.0.1", "::1", false, SOURCES);
        assertNotEquals(reference(), other);
    }

    @Test
    public void sourceChangeIsDetected() {
        String removed = HostsFingerprint.compute(
                REVISION, 2_300_000, "127.0.0.1", "::1", true, singletonList(SOURCES.get(0)));
        String reordered = HostsFingerprint.compute(
                REVISION, 2_300_000, "127.0.0.1", "::1", true, asList(SOURCES.get(1), SOURCES.get(0)));
        assertNotEquals(reference(), removed);
        assertNotEquals(reference(), reordered);
    }

    /**
     * Inputs are length prefixed, so content cannot be shifted across their boundaries without
     * changing the fingerprint.
     */
    @Test
    public void inputBoundariesAreNotAmbiguous() {
        String left = HostsFingerprint.compute(REVISION, 1, "127.0.0.1", "::1", true, asList("ab", "c"));
        String right = HostsFingerprint.compute(REVISION, 1, "127.0.0.1", "::1", true, asList("a", "bc"));
        assertNotEquals(left, right);
    }

    @Test
    public void headerLineRoundTrips() {
        String fingerprint = reference();
        String header = HostsFingerprint.toHeaderLine(fingerprint);
        assertEquals(fingerprint, HostsFingerprint.fromHeaderLines(singletonList(header)));
    }

    @Test
    public void headerIsFoundAmongOtherLines() {
        String fingerprint = reference();
        List<String> lines = asList(
                "# AdAway: Generated 2026-07-31 00:00:00",
                HostsFingerprint.toHeaderLine(fingerprint),
                "# Sources:");
        assertEquals(fingerprint, HostsFingerprint.fromHeaderLines(lines));
    }

    /**
     * An unreadable header must yield no fingerprint, so the file is regenerated rather than reused.
     */
    @Test
    public void unreadableHeadersYieldNoFingerprint() {
        assertNull(HostsFingerprint.fromHeaderLines(emptyList()));
        assertNull(HostsFingerprint.fromHeaderLines(singletonList("# AdAway: Generated")));
        assertNull(HostsFingerprint.fromHeaderLines(singletonList("# AdAway fingerprint: ")));
        assertNull(HostsFingerprint.fromHeaderLines(singletonList("# AdAway fingerprint: not-hex!")));
    }
}
