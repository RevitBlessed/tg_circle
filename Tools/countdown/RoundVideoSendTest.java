package org.telegram.messenger;

import java.util.HashMap;
import java.util.Map;

/** Regression tests for edited circles sharing the same continuous source file. */
public final class RoundVideoSendTest {
    public static void main(String[] args) {
        RoundVideoSegmentRange first = range(0, 60_000, 5_000, 20_000);
        RoundVideoSegmentRange second = range(60_000, 60_000, 5_000, 20_000);
        equal(5_000_000, first.startTimeUs);
        equal(20_000_000, first.endTimeUs);
        equal(65_000_000, second.startTimeUs);
        equal(80_000_000, second.endTimeUs);
        equal(15_000, first.durationMs);
        equal(15_000, second.durationMs);

        // Simulate first upload completing before the second send consults sent-file storage.
        Map<String, String> sent = new HashMap<>();
        sent.put(key(first), "first-uploaded-document");
        if (sent.containsKey(key(second))) throw new AssertionError("Second clip reused first upload");
        if (!sent.containsKey(key(range(0, 60_000, 5_000, 20_000)))) {
            throw new AssertionError("Identical retry should retain its identity");
        }
        if (sent.containsKey(key(range(0, 60_000, 6_000, 21_000)))) {
            throw new AssertionError("Different trim with equal duration reused the upload");
        }
        // Defaults, both handles, short tail, clamping and a deleted middle circle.
        equal(60_000_000, range(0, 60_000, -1, -1).endTimeUs);
        equal(120_000_000, range(60_000, 60_000, -1, -1).endTimeUs);
        RoundVideoSegmentRange tail = range(120_000, 4_200, 1_000, -1);
        equal(121_000_000, tail.startTimeUs);
        equal(124_200_000, tail.endTimeUs);
        equal(3_200, tail.durationMs);
        equal(800, range(0, 60_000, 90_000, 2_000).durationMs);
        equal(200, range(60_000, 200, 0, -1).durationMs);
        equal(180_000_000, range(180_000, 60_000, 0, -1).startTimeUs);
        // Resume extends only the current source range, without rebasing earlier entries.
        equal(75_000_000, range(60_000, 15_000, 0, -1).endTimeUs);
        equal(90_000_000, range(60_000, 30_000, 0, -1).endTimeUs);
        equal(5_000_000, first.startTimeUs);
        if (key(first).equals("session.mp4_12345_98765")) {
            throw new AssertionError("Must not reuse legacy file-only cache entry");
        }
        String muted = RoundVideoSendIdentity.appendTo("session.mp4_12345_98765",
                first.startTimeUs, first.endTimeUs, first.durationMs, true, 480, 480);
        if (muted.equals(key(first))) throw new AssertionError("Audio edit not in identity");
        System.out.println("Round-video range and send-identity tests: PASS");
    }

    private static RoundVideoSegmentRange range(long source, long duration, long start, long end) {
        return RoundVideoSegmentRange.resolve(source, duration, start, end, 800);
    }

    private static String key(RoundVideoSegmentRange range) {
        return RoundVideoSendIdentity.appendTo("session.mp4_12345_98765", range.startTimeUs,
                range.endTimeUs, range.durationMs, false, 480, 480);
    }

    private static void equal(long expected, long actual) {
        if (expected != actual) throw new AssertionError("Expected " + expected + ", got " + actual);
    }
}
