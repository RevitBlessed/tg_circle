package org.telegram.ui.Components;

/** Plain-JVM regression checks for the round-video camera diagonal gesture. */
public final class RoundVideoCameraGestureTest {

    public static void main(String[] args) {
        regularHoldWithoutMovement();
        smallJitterDoesNotTrigger();
        fastDiagonalSelectsRearCamera();
        extendedWindowAllowsCompletionBefore300();
        deadlineExceededFails();
        lateMovementAfter150Fails();
        wrongDirectionExcludesGesture();
        angleToleranceCheck();
        resetClearsState();
        invalidCoordinatesIgnored();
        System.out.println("RoundVideoCameraGestureTest: all checks passed.");
    }

    private static void regularHoldWithoutMovement() {
        RoundVideoCameraGesture gesture = new RoundVideoCameraGesture();
        gesture.begin(100f, 100f, 1000L);
        expect(false, gesture.isRearSelected());
        expect(150L, gesture.delayBeforeStart(1000L));
        expect(50L, gesture.delayBeforeStart(1100L));
        expect(0L, gesture.delayBeforeStart(1150L));
    }

    private static void smallJitterDoesNotTrigger() {
        RoundVideoCameraGesture gesture = new RoundVideoCameraGesture();
        gesture.begin(100f, 100f, 1000L);
        // movement of 4dp left, 4dp up -> distance squared = 32 < 64 (8dp)
        expect(false, gesture.move(96f, 96f, 1050L));
        expect(false, gesture.isRearSelected());
        expect(false, gesture.hasMoved());
    }

    private static void fastDiagonalSelectsRearCamera() {
        RoundVideoCameraGesture gesture = new RoundVideoCameraGesture();
        gesture.begin(100f, 100f, 1000L);
        // Intent threshold
        expect(false, gesture.move(90f, 90f, 1050L));
        expect(true, gesture.hasMoved());
        // Select threshold: left = 20, up = 20 -> distSq = 800 >= 784 (28dp^2)
        expect(true, gesture.move(80f, 80f, 1080L));
        expect(true, gesture.isRearSelected());
    }

    private static void extendedWindowAllowsCompletionBefore300() {
        RoundVideoCameraGesture gesture = new RoundVideoCameraGesture();
        gesture.begin(100f, 100f, 1000L);
        // Start diagonal before 150ms: left = 8, up = 8 (distSq = 128 > 64)
        expect(false, gesture.move(92f, 92f, 1100L));
        expect(true, gesture.hasMoved());
        // At 1150ms, delayBeforeStart gives extended deadline (up to 300ms from start)
        expect(150L, gesture.delayBeforeStart(1150L));
        // Complete diagonal at 220ms (1220L)
        expect(true, gesture.move(75f, 75f, 1220L));
        expect(true, gesture.isRearSelected());
    }

    private static void deadlineExceededFails() {
        RoundVideoCameraGesture gesture = new RoundVideoCameraGesture();
        gesture.begin(100f, 100f, 1000L);
        gesture.move(92f, 92f, 1100L);
        // At 301ms (1301L), deadline exceeded
        expect(false, gesture.move(70f, 70f, 1301L));
        expect(false, gesture.isRearSelected());
    }

    private static void lateMovementAfter150Fails() {
        RoundVideoCameraGesture gesture = new RoundVideoCameraGesture();
        gesture.begin(100f, 100f, 1000L);
        // No movement before 150ms. At 1160L, first significant movement
        expect(false, gesture.move(70f, 70f, 1160L));
        expect(false, gesture.isRearSelected());
    }

    private static void wrongDirectionExcludesGesture() {
        RoundVideoCameraGesture gesture = new RoundVideoCameraGesture();
        gesture.begin(100f, 100f, 1000L);
        // Move right and down (downX - x < 0)
        expect(false, gesture.move(120f, 120f, 1050L));
        expect(false, gesture.isRearSelected());
    }

    private static void angleToleranceCheck() {
        RoundVideoCameraGesture gesture = new RoundVideoCameraGesture();
        gesture.begin(100f, 100f, 1000L);
        // Too flat: left = 20, up = 5 (up < left * 0.5)
        expect(false, gesture.move(80f, 95f, 1050L));
        expect(false, gesture.isRearSelected());
    }

    private static void resetClearsState() {
        RoundVideoCameraGesture gesture = new RoundVideoCameraGesture();
        gesture.begin(100f, 100f, 1000L);
        gesture.move(70f, 70f, 1080L);
        expect(true, gesture.isRearSelected());
        gesture.reset();
        expect(false, gesture.isRearSelected());
        expect(false, gesture.hasMoved());
    }

    private static void invalidCoordinatesIgnored() {
        RoundVideoCameraGesture gesture = new RoundVideoCameraGesture();
        gesture.begin(Float.NaN, 100f, 1000L);
        expect(false, gesture.move(50f, 50f, 1050L));
        expect(0L, gesture.delayBeforeStart(1000L));
    }

    private static void expect(boolean expected, boolean actual) {
        if (expected != actual) {
            throw new AssertionError("Expected " + expected + ", got " + actual);
        }
    }

    private static void expect(long expected, long actual) {
        if (expected != actual) {
            throw new AssertionError("Expected " + expected + ", got " + actual);
        }
    }
}
