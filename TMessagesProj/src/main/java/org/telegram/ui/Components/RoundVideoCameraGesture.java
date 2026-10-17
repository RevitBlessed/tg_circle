package org.telegram.ui.Components;

final class RoundVideoCameraGesture {
    static final long HOLD_DELAY_MS = 150;
    static final long GESTURE_DEADLINE_MS = 300;
    static final float INTENT_DISTANCE_DP = 8;
    static final float SELECT_DISTANCE_DP = 28;

    private float downX, downY;
    private long downTime;
    private boolean tracking, diagonal, rearSelected, moved;

    void begin(float x, float y, long time) {
        reset();
        downX = x;
        downY = y;
        downTime = time;
        tracking = finite(x) && finite(y);
    }

    boolean move(float x, float y, long time) {
        if (!tracking || !finite(x) || !finite(y) || time < downTime) return false;

        float left = downX - x;
        float up = downY - y;
        float distanceSquared = left * left + up * up;
        if (distanceSquared < INTENT_DISTANCE_DP * INTENT_DISTANCE_DP) return false;

        moved = true;
        long elapsed = time - downTime;
        if (elapsed >= GESTURE_DEADLINE_MS || elapsed >= HOLD_DELAY_MS && !diagonal) {
            tracking = false;
            return false;
        }
        if (left <= 0 || up <= 0 || up < left * 0.5f || up > left * 2f) {
            tracking = false;
            diagonal = false;
            return false;
        }

        diagonal = true;
        if (distanceSquared >= SELECT_DISTANCE_DP * SELECT_DISTANCE_DP) {
            rearSelected = true;
            tracking = false;
            return true;
        }
        return false;
    }

    long delayBeforeStart(long time) {
        if (!tracking) return 0;
        long elapsed = Math.max(0, time - downTime);
        if (elapsed < HOLD_DELAY_MS) return HOLD_DELAY_MS - elapsed;
        return diagonal ? Math.max(0, GESTURE_DEADLINE_MS - elapsed) : 0;
    }

    boolean isRearSelected() { return rearSelected; }
    boolean hasMoved() { return moved; }
    void recordingRequested() { tracking = false; }
    void reset() { tracking = diagonal = rearSelected = moved = false; }

    private static boolean finite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }
}
