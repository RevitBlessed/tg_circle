package org.telegram.ui.Components;

/** Plain-JVM regression checks for the round-video haptic countdown. */
public final class RoundVideoRecordingCountdownTest {

    public static void main(String[] args) {
        fullCountdown();
        boundaryAndDuplicateCalls();
        delayedFramesDoNotCatchUp();
        pauseAndResumeKeepState();
        backwardsCorrectionDoesNotRepeat();
        resetStartsNewRecording();
        alternativeLimit();
        invalidDurationIsIgnored();
        finishIsOneShot();
        resetAllowsFinishAgain();
    }

    private static void fullCountdown() {
        RoundVideoRecordingCountdown countdown = new RoundVideoRecordingCountdown();
        expect(10, countdown.update(50_000));
        expect(5, countdown.update(55_000));
        expect(4, countdown.update(56_000));
        expect(3, countdown.update(57_000));
        expect(2, countdown.update(58_000));
        expect(1, countdown.update(59_000));
    }

    private static void boundaryAndDuplicateCalls() {
        RoundVideoRecordingCountdown countdown = new RoundVideoRecordingCountdown();
        expect(0, countdown.update(49_999));
        expect(10, countdown.update(50_000));
        expect(0, countdown.update(50_000));
        expect(0, countdown.update(50_999));
    }

    private static void delayedFramesDoNotCatchUp() {
        RoundVideoRecordingCountdown countdown = new RoundVideoRecordingCountdown();
        expect(0, countdown.update(54_500));
        expect(5, countdown.update(55_100));
        expect(3, countdown.update(57_500));
    }

    private static void pauseAndResumeKeepState() {
        RoundVideoRecordingCountdown countdown = new RoundVideoRecordingCountdown();
        expect(10, countdown.update(50_250));
        expect(0, countdown.update(50_250));
        expect(5, countdown.update(55_250));
    }

    private static void backwardsCorrectionDoesNotRepeat() {
        RoundVideoRecordingCountdown countdown = new RoundVideoRecordingCountdown();
        expect(10, countdown.update(50_100));
        expect(0, countdown.update(49_900));
        expect(0, countdown.update(50_200));
    }

    private static void resetStartsNewRecording() {
        RoundVideoRecordingCountdown countdown = new RoundVideoRecordingCountdown();
        expect(10, countdown.update(50_000));
        countdown.reset();
        expect(10, countdown.update(50_000));
    }

    private static void alternativeLimit() {
        RoundVideoRecordingCountdown countdown = new RoundVideoRecordingCountdown(30_000);
        expect(10, countdown.update(20_000));
        expect(5, countdown.update(25_000));
    }

    private static void invalidDurationIsIgnored() {
        RoundVideoRecordingCountdown countdown = new RoundVideoRecordingCountdown();
        expect(0, countdown.update(-1));
        expect(0, countdown.update(60_000));
    }

    private static void finishIsOneShot() {
        RoundVideoRecordingCountdown countdown = new RoundVideoRecordingCountdown();
        expect(true, countdown.finishAtLimit());
        expect(false, countdown.finishAtLimit());
        expect(0, countdown.update(59_000));
    }

    private static void resetAllowsFinishAgain() {
        RoundVideoRecordingCountdown countdown = new RoundVideoRecordingCountdown();
        expect(true, countdown.finishAtLimit());
        countdown.reset();
        expect(true, countdown.finishAtLimit());
    }

    private static void expect(int expected, int actual) {
        if (expected != actual) {
            throw new AssertionError("expected=" + expected + ", actual=" + actual);
        }
    }

    private static void expect(boolean expected, boolean actual) {
        if (expected != actual) {
            throw new AssertionError("expected=" + expected + ", actual=" + actual);
        }
    }
}
