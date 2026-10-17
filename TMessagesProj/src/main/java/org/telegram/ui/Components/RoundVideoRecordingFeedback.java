package org.telegram.ui.Components;

import android.content.Context;
import android.media.AudioAttributes;
import android.os.Build;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.provider.Settings;
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
        if (remainingSeconds == 0 || !canUseView()) {
            return;
        }
        feedbackAccepted |= view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        if (BuildVars.DEBUG_VERSION) {
            FileLog.d("round video countdown haptic: remainingSeconds=" + remainingSeconds);
        }
    }

    public void onLimitReached(long recordedDurationMs) {
        if (!countdown.finishAtLimit() || !canUseSystemHaptics()) {
            return;
        }
        boolean delivered = false;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Vibrator vibrator = getVibrator();
                if (vibrator != null && vibrator.hasVibrator()) {
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
                }
            }
        } catch (Throwable error) {
            FileLog.e(error);
        }
        if (!delivered) {
            try {
                delivered = view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
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

    private boolean canUseView() {
        return view.getVisibility() == View.VISIBLE && view.hasWindowFocus();
    }

    private boolean canUseSystemHaptics() {
        if (!canUseView() || !view.isHapticFeedbackEnabled()) {
            return false;
        }
        try {
            return Settings.System.getInt(
                    view.getContext().getContentResolver(),
                    Settings.System.HAPTIC_FEEDBACK_ENABLED,
                    0
            ) != 0;
        } catch (Throwable error) {
            FileLog.e(error);
            return false;
        }
    }

    private Vibrator getVibrator() {
        Context context = view.getContext();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager manager = (VibratorManager) context.getSystemService(
                    Context.VIBRATOR_MANAGER_SERVICE
            );
            return manager == null ? null : manager.getDefaultVibrator();
        }
        return (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
    }
}
