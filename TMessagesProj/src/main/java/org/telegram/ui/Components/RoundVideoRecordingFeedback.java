package org.telegram.ui.Components;

import android.content.Context;
import android.media.AudioAttributes;
import android.os.Build;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.view.HapticFeedbackConstants;
import android.view.View;

import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;

/** Delivers countdown haptics without affecting recording when a device rejects vibration. */
public final class RoundVideoRecordingFeedback {

    private final View view;
    private final RoundVideoRecordingCountdown countdown = new RoundVideoRecordingCountdown();
    private boolean feedbackAccepted;

    public RoundVideoRecordingFeedback(View view) {
        this.view = view;
    }

    public void update(long recordedDurationMs) {
        int remainingSeconds = countdown.update(recordedDurationMs);
        if (remainingSeconds == 0) {
            return;
        }
        boolean delivered = false;
        try {
            if (view != null) {
                delivered = view.performHapticFeedback(
                        HapticFeedbackConstants.KEYBOARD_TAP,
                        HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                );
            }
        } catch (Throwable ignore) {}
        if (!delivered) {
            try {
                Vibrator vibrator = getVibrator();
                if (vibrator != null && vibrator.hasVibrator()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK));
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createOneShot(15, VibrationEffect.DEFAULT_AMPLITUDE));
                    } else {
                        vibrator.vibrate(15);
                    }
                    delivered = true;
                }
            } catch (Throwable ignore) {}
        }
        feedbackAccepted |= delivered;
        if (BuildVars.DEBUG_VERSION) {
            FileLog.d("round video countdown haptic: remainingSeconds=" + remainingSeconds + ", accepted=" + delivered);
        }
    }

    public void onLimitReached(long recordedDurationMs) {
        if (!countdown.finishAtLimit()) {
            return;
        }
        boolean delivered = false;
        try {
            Vibrator vibrator = getVibrator();
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    VibrationEffect effect = VibrationEffect.createPredefined(
                            VibrationEffect.EFFECT_HEAVY_CLICK
                    );
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        vibrator.vibrate(
                                effect,
                                VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH)
                        );
                    } else {
                        vibrator.vibrate(
                                effect,
                                new AudioAttributes.Builder()
                                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                                        .build()
                        );
                    }
                    delivered = true;
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE));
                    delivered = true;
                } else {
                    vibrator.vibrate(80);
                    delivered = true;
                }
            }
        } catch (Throwable error) {
            FileLog.e(error);
        }
        if (!delivered && view != null) {
            try {
                delivered = view.performHapticFeedback(
                        HapticFeedbackConstants.LONG_PRESS,
                        HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                );
            } catch (Throwable error) {
                FileLog.e(error);
            }
        }
        feedbackAccepted |= delivered;
        if (BuildVars.DEBUG_VERSION) {
            FileLog.d("round video limit haptic: durationMs=" + recordedDurationMs
                    + ", effect=heavy, accepted=" + delivered);
        }
    }

    public void reset() {
        countdown.reset();
        feedbackAccepted = false;
    }

    public boolean wasFeedbackAccepted() {
        return feedbackAccepted;
    }

    private Vibrator getVibrator() {
        if (view == null) return null;
        Context context = view.getContext();
        if (context == null) return null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager manager = (VibratorManager) context.getSystemService(
                    Context.VIBRATOR_MANAGER_SERVICE
            );
            return manager == null ? null : manager.getDefaultVibrator();
        }
        return (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
    }
}
