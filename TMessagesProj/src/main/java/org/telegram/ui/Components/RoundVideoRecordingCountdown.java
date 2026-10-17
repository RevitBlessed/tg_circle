package org.telegram.ui.Components;

/** Calculates one-shot haptic warning events near the round-video duration limit. */
public final class RoundVideoRecordingCountdown {

    public static final long MAX_DURATION_MS = 60_000L;
    private static final int[] WARNING_SECONDS = {10, 5, 4, 3, 2, 1};

    private final long maximumDurationMs;
    private int handledWarnings;
    private boolean limitHandled;

    public RoundVideoRecordingCountdown() {
        this(MAX_DURATION_MS);
    }

    public RoundVideoRecordingCountdown(long maximumDurationMs) {
        if (maximumDurationMs <= 0L) {
            throw new IllegalArgumentException("maximumDurationMs must be positive");
        }
        this.maximumDurationMs = maximumDurationMs;
    }

    /** Returns the warning's remaining-second value, or zero when no signal is due. */
    public int update(long recordedDurationMs) {
        if (recordedDurationMs < 0L || recordedDurationMs >= maximumDurationMs
                || limitHandled) {
            return 0;
        }
        for (int i = 0; i < WARNING_SECONDS.length; i++) {
            int mask = 1 << i;
            if ((handledWarnings & mask) != 0) {
                continue;
            }
            long warningStartMs = maximumDurationMs - WARNING_SECONDS[i] * 1_000L;
            if (recordedDurationMs < warningStartMs) {
                continue;
            }
            handledWarnings |= mask;
            if (recordedDurationMs < warningStartMs + 1_000L) {
                return WARNING_SECONDS[i];
            }
        }
        return 0;
    }

    /** Marks the automatic duration-limit event and returns true exactly once. */
    public boolean finishAtLimit() {
        if (limitHandled) {
            return false;
        }
        limitHandled = true;
        handledWarnings = (1 << WARNING_SECONDS.length) - 1;
        return true;
    }

    public void reset() {
        handledWarnings = 0;
        limitHandled = false;
    }
}
