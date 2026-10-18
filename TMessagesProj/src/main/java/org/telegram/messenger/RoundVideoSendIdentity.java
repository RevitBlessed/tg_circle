package org.telegram.messenger;

/** Sent-file identity for independently edited clips sharing one recording file. */
public final class RoundVideoSendIdentity {
    private RoundVideoSendIdentity() {}

    public static String appendTo(String sourceIdentity, long startTimeUs, long endTimeUs,
            long durationMs, boolean muted, int width, int height) {
        // A new namespace also prevents reuse of older file-only round-video entries.
        return sourceIdentity + "_round_range_v1_" + startTimeUs + "_" + endTimeUs
                + "_" + durationMs + "_" + (muted ? "m" : "a") + "_" + width + "x" + height;
    }
}
