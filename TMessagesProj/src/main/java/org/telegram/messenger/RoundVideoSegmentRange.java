package org.telegram.messenger;

/** Immutable conversion from editor-local milliseconds to source-file microseconds. */
public final class RoundVideoSegmentRange {
    public final long trimStartMs;
    public final long trimEndMs;
    public final long startTimeUs;
    public final long endTimeUs;
    public final long durationMs;

    private RoundVideoSegmentRange(long sourceStartMs, long trimStartMs, long trimEndMs) {
        this.trimStartMs = trimStartMs;
        this.trimEndMs = trimEndMs;
        this.startTimeUs = (sourceStartMs + trimStartMs) * 1000L;
        this.endTimeUs = (sourceStartMs + trimEndMs) * 1000L;
        this.durationMs = trimEndMs - trimStartMs;
    }

    public static RoundVideoSegmentRange resolve(long sourceStartMs, long segmentDurationMs,
            long startMs, long endMs, long minimumDurationMs) {
        if (sourceStartMs < 0 || segmentDurationMs <= 0 || minimumDurationMs <= 0) {
            throw new IllegalArgumentException("Invalid round-video source range");
        }
        long minimum = Math.min(minimumDurationMs, segmentDurationMs);
        long start = Math.max(0L, Math.min(startMs, segmentDurationMs - minimum));
        long end = endMs < 0 ? segmentDurationMs : Math.min(endMs, segmentDurationMs);
        end = Math.max(start + minimum, end);
        return new RoundVideoSegmentRange(sourceStartMs, start, end);
    }
}
