package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Region;
import android.net.Uri;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.utils.settings.SharedSettings;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.VideoEditedInfo;
import org.telegram.messenger.utils.Choreographer60FpsContent;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundColorProvider;
import org.telegram.ui.Components.voip.CellFlickerDrawable;
import org.telegram.ui.Stories.recorder.FlashViews;
import org.telegram.utils.camera.roundvideo.RoundVideoSession;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;

/** Telegram UI adapter for the standalone Camera2 round-video session. */
public final class InstantCameraView2 extends InstantCameraViewBase {

    private static final long MINIMUM_DURATION_MS = 800L;
    private static final float ZOOM_GESTURE_EFFORT = 1.5f;
    private static final int SWITCH_PREVIEW_BITMAP_SIZE = 180;
    private static final long UI_FRAME_FALLBACK_DELAY_NS = 70_000_000L;
    private static final long CAMERA_FLIP_MINIMUM_DURATION_MS = 580L;
    private static final long BATCH_DEMO_SEGMENT_DURATION_MS = 8_000L;
    private static final int MAX_BATCH_SEGMENTS = 5;
    private static final float BATCH_STACK_IDLE_ALPHA = 0.55f;
    private static final int BATCH_STACK_THUMBNAIL_SIZE_DP = 72;
    private static final int BATCH_STACK_THUMBNAIL_STEP_DP = 41;
    private static final long BATCH_STACK_SEND_STAGGER_MS = 125L;
    private static final long BATCH_STACK_SHIFT_DURATION_MS = 115L;
    private static final long BATCH_STACK_FLIGHT_DURATION_MS = 340L;
    private static final long BATCH_FINAL_SEND_GAP_MS = 20L;
    private static final long BATCH_MAXIMUM_DURATION_MS =
            BATCH_DEMO_SEGMENT_DURATION_MS * MAX_BATCH_SEGMENTS;
    private static final long BATCH_SESSION_FINISH_MARGIN_MS = 1_500L;
    private static final int BATCH_ACTION_NONE = 0;
    private static final int BATCH_ACTION_REVIEW = 2;
    private static final int BATCH_ACTION_SEND = 3;

    private final InstantCameraView.Delegate delegate;
    private final Theme.ResourcesProvider resourcesProvider;
    private final int currentAccount = UserConfig.selectedAccount;
    private final int recordingGuid;
    private final boolean secretChat;
    private final View parentView;
    private final CameraContainer cameraContainer;
    private final RoundVideoProgressView progressView;
    private final FrameLayout previewContainer;
    private final TextureView textureView;
    private final BackupImageView textureOverlayView;
    private final LinearLayout buttonsLayout;
    private final FlashViews.ImageViewInvertable switchCameraButton;
    private final FlashViews.ImageViewInvertable flashButton;
    private final ImageView muteImageView;
    private final FlashViews flashViews;
    private final int[] screenPosition = new int[2];
    private final Matrix previewBitmapMatrix = new Matrix();
    private final float[] previewBitmapMatrixValues = new float[9];
    private final Paint previewBitmapPaint = new Paint(
            Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG
    );
    private final int buttonIconSize;
    private final boolean multiSegmentEnabled;
    private final FrameLayout batchStackLayout;
    private final ArrayList<BatchSegment> batchSegments = new ArrayList<>();
    private final RoundVideoSession.OutputListener batchOutputListener =
            new RoundVideoSession.OutputListener() {};

    private RoundVideoSession session;
    private RoundVideoSession.OutputResolution activeOutputResolution;
    private RoundVideoSession.StateInfo stateInfo;
    private RoundVideoSession.CameraCapabilities capabilities;
    private TelegramRoundVideoUpload upload;
    private VideoEditedInfo videoEditedInfo;
    private SendOptions pendingSend;
    private AnimatorSet visibilityAnimator;
    private ValueAnimator zoomResetAnimator;
    private ValueAnimator cameraFlipAnimator;
    private RLottieDrawable switchCameraDrawable;
    private RLottieDrawable flashOnDrawable;
    private RLottieDrawable flashOffDrawable;
    private boolean startedNotificationSent;
    private boolean resumeNotificationPending;
    private boolean stopNotificationSent;
    private boolean muted;
    private boolean sent;
    private boolean opened;
    private boolean setVisibilityFromPause;
    private boolean drawInitialPlaceholder;
    private boolean switchPlaceholderVisible;
    private boolean cameraFlipInProgress;
    private boolean cameraFlipSessionReady;
    private boolean cameraFlipFirstFrameReady;
    private boolean cameraFlipAnimationCompleted;
    private long cameraFlipStartedRealtimeMs;
    private int cameraFlipGeneration;
    private Bitmap lastBitmap;
    private long lastVisibleDurationMs;
    private float panTranslationY;
    private boolean rearCameraStartOverride;
    private RoundVideoSession.CameraFacing cameraBeforeRearGesture;
    private float animationTranslationY;
    private float pinchStartDistance;
    private float zoomProgress;
    private int zoomPointerId1 = -1;
    private int zoomPointerId2 = -1;

    private boolean pinchZoomActive;
    private boolean heavyOperationsStopped;
    private float previousWindowBrightness = Float.NaN;
    private int textureViewSize;
    private long lastPreviewUiFrameRealtimeNs;
    private boolean recordingUiFallbackRegistered;
    private boolean handlingPreviewTextureInvalidate;
    private int pendingBatchAction;
    private SendOptions pendingBatchSend;
    private Bitmap pendingBatchThumbnail;
    private View pendingBatchHolder;
    private boolean batchReviewVisible;
    private boolean batchSending;
    private boolean batchSendPreviewFrozen;
    private boolean batchStackCenterFrozen;
    private int batchStackTouchIndex = -1;
    private int pendingBatchScrubIndex = -1;
    private final Runnable batchScrubPreviewRunnable = () -> {
        int index = pendingBatchScrubIndex;
        pendingBatchScrubIndex = -1;
        if (canScrubBatchStack() && index >= 0 && index != this.selectedBatchSegment) {
            showBatchPreview(index);
        }
    };
    private int selectedBatchSegment = -1;
    private VideoPlayer batchVideoPlayer;
    private final Runnable batchPreviewProgressRunnable = new Runnable() {
        @Override
        public void run() {
            if (!batchReviewVisible || batchVideoPlayer == null
                    || selectedBatchSegment < 0
                    || selectedBatchSegment >= batchSegments.size()) {
                return;
            }
            BatchSegment segment = batchSegments.get(selectedBatchSegment);
            long end = segment.sourceStartMs
                    + (segment.trimEndMs > 0 ? segment.trimEndMs : segment.durationMs);
            if (batchVideoPlayer.getCurrentPosition() >= end) {
                batchVideoPlayer.seekTo(segment.sourceStartMs
                        + Math.max(0L, segment.trimStartMs));
            }
            AndroidUtilities.runOnUIThread(this, 33L);
        }
    };

    private static final class BatchSegment {
        File file;
        final long durationMs;
        final long sourceStartMs;
        final long sourceEndMs;
        boolean hasAudio;
        RoundVideoSession.FrameRate frameRate;
        final Bitmap thumbnail;
        long trimStartMs;
        long trimEndMs;

        BatchSegment(
                File file,
                long durationMs,
                long sourceStartMs,
                long sourceEndMs,
                boolean hasAudio,
                RoundVideoSession.FrameRate frameRate,
                Bitmap thumbnail
        ) {
            this.file = file;
            this.durationMs = durationMs;
            this.sourceStartMs = sourceStartMs;
            this.sourceEndMs = sourceEndMs;
            this.hasAudio = hasAudio;
            this.frameRate = frameRate;
            this.thumbnail = thumbnail;
            trimStartMs = 0L;
            trimEndMs = durationMs;
        }
    }

    private final Runnable recordingUiFallback = () -> {
        if (!isRecordingState()) return;
        long nowNs = SystemClock.elapsedRealtimeNanos();
        if (lastPreviewUiFrameRealtimeNs == 0L
                || nowNs - lastPreviewUiFrameRealtimeNs >= UI_FRAME_FALLBACK_DELAY_NS) {
            updateRecordingUiFrame();
        }
    };

    public InstantCameraView2(
            Context context,
            InstantCameraView.Delegate delegate,
            Theme.ResourcesProvider resourcesProvider,
            boolean isNewDesign
    ) {
        super(context);
        this.delegate = delegate;
        this.resourcesProvider = resourcesProvider;
        multiSegmentEnabled = delegate.isMultiSegmentRoundVideoEnabled();
        recordingGuid = delegate.getClassGuid();
        secretChat = delegate.isSecretChat();
        parentView = delegate.getFragmentView();
        setWillNotDraw(false);
        setClipChildren(false);
        setClipToPadding(false);

        flashViews = new FlashViews(context, null, this, null);
        flashViews.setWarmth(0.5f);
        addView(flashViews.backgroundView, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.MATCH_PARENT,
                Gravity.FILL
        ));

        cameraContainer = new CameraContainer(context);
        progressView = new RoundVideoProgressView(context);
        previewContainer = new FrameLayout(context);
        textureView = createTextureView(context);
        previewContainer.addView(textureView, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.MATCH_PARENT,
                Gravity.FILL
        ));
        Paint placeholderDimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        placeholderDimPaint.setColor(Color.argb(40, 0, 0, 0));
        textureOverlayView = new BackupImageView(context) {
            private final CellFlickerDrawable flickerDrawable = new CellFlickerDrawable();

            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                if (!drawInitialPlaceholder) return;
                float radius = Math.min(getWidth(), getHeight()) * 0.5f;
                AndroidUtilities.rectTmp.set(0, 0, getWidth(), getHeight());
                canvas.drawRoundRect(AndroidUtilities.rectTmp, radius, radius, placeholderDimPaint);
                AndroidUtilities.rectTmp.inset(dp(1), dp(1));
                flickerDrawable.setParentWidth(getWidth());
                flickerDrawable.draw(canvas, AndroidUtilities.rectTmp, radius, null);
                invalidate();
            }
        };
        textureOverlayView.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View target, Outline outline) {
                outline.setOval(0, 0, target.getWidth(), target.getHeight());
            }
        });
        textureOverlayView.setClipToOutline(true);
        previewContainer.addView(textureOverlayView, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.MATCH_PARENT,
                Gravity.FILL
        ));
        progressView.addView(previewContainer, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.MATCH_PARENT,
                Gravity.FILL,
                14,
                14,
                14,
                14
        ));
        cameraContainer.addView(progressView, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.MATCH_PARENT,
                Gravity.FILL
        ));
        addView(cameraContainer, new LayoutParams(
                AndroidUtilities.roundPlayingMessageSize,
                AndroidUtilities.roundPlayingMessageSize,
                Gravity.CENTER
        ));
        addView(flashViews.foregroundView, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.MATCH_PARENT,
                Gravity.FILL
        ));

        batchStackLayout = new FrameLayout(context) {
            private final Path occlusion = new Path();

            @Override
            protected void dispatchDraw(Canvas canvas) {
                super.dispatchDraw(canvas);
                // Sibling transforms also change the holes in each other sibling.
                boolean moving = batchSending;
                for (int i = 0; i < getChildCount(); i++) {
                    moving |= getChildAt(i).hasTransientState();
                }
                if (moving) postInvalidateOnAnimation();
            }

            @Override
            protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
                int save = canvas.save();
                // Cut out every foreground thumbnail plus a transparent gutter.
                // Alpha dims a thumbnail, but must never reveal its rear neighbours.
                for (int i = indexOfChild(child) + 1; i < getChildCount(); i++) {
                    View front = getChildAt(i);
                    if (front.getVisibility() != VISIBLE || front.getAlpha() <= 0f) continue;
                    float sx = front.getScaleX();
                    float sy = front.getScaleY();
                    float left = front.getX() + front.getPivotX() * (1f - sx);
                    float top = front.getY() + front.getPivotY() * (1f - sy);
                    occlusion.reset();
                    // The outer footprint stays fixed; the image is inset by 4dp.
                    occlusion.addOval(left, top,
                            left + front.getWidth() * sx,
                            top + front.getHeight() * sy, Path.Direction.CW);
                    canvas.clipPath(occlusion, Region.Op.DIFFERENCE);
                }
                boolean drawn = super.drawChild(canvas, child, drawingTime);
                canvas.restoreToCount(save);
                return drawn;
            }

            @Override
            public boolean onInterceptTouchEvent(MotionEvent event) {
                return canScrubBatchStack() || super.onInterceptTouchEvent(event);
            }

            @Override
            public boolean onTouchEvent(MotionEvent event) {
                return handleBatchStackTouch(event) || super.onTouchEvent(event);
            }
        };
        batchStackLayout.setClipChildren(false);
        batchStackLayout.setClipToPadding(false);
        batchStackLayout.setVisibility(GONE);
        addView(batchStackLayout, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT,
                84,
                Gravity.LEFT | Gravity.TOP,
                12,
                58,
                12,
                0
        ));

        buttonsLayout = new LinearLayout(context);
        buttonsLayout.setOrientation(LinearLayout.HORIZONTAL);
        buttonsLayout.setPadding(dp(6), dp(6), dp(6), dp(6));
        addView(buttonsLayout, LayoutHelper.createFrame(
                LayoutHelper.WRAP_CONTENT,
                56,
                Gravity.LEFT | Gravity.BOTTOM,
                1,
                0,
                0,
                0
        ));

        switchCameraButton = new FlashViews.ImageViewInvertable(context);
        switchCameraButton.setScaleType(ImageView.ScaleType.CENTER);
        switchCameraButton.setContentDescription(
                LocaleController.getString(R.string.AccDescrSwitchCamera)
        );
        buttonsLayout.addView(switchCameraButton, LayoutHelper.createLinear(44, 44));
        switchCameraButton.setOnClickListener(view -> switchCamera());

        flashButton = new FlashViews.ImageViewInvertable(context);
        flashButton.setScaleType(ImageView.ScaleType.CENTER);
        buttonsLayout.addView(flashButton, LayoutHelper.createLinear(44, 44));
        flashButton.setOnClickListener(view -> toggleFlash());

        buttonIconSize = dp(isNewDesign ? 24 : 28);
        switchCameraDrawable = new RLottieDrawable(
                R.raw.roundcamera_flip,
                buttonIconSize,
                buttonIconSize
        );
        switchCameraDrawable.setCallback(switchCameraButton);
        switchCameraDrawable.setCurrentFrame(switchCameraDrawable.getFramesCount() - 1);
        switchCameraButton.setImageDrawable(switchCameraDrawable);
        updateFlashIcon();

        if (!isNewDesign) {
            flashViews.add(switchCameraButton);
            flashViews.add(flashButton);
        } else if (resourcesProvider != null && !resourcesProvider.isDark()) {
            switchCameraButton.setInvert(0.6f);
            flashButton.setInvert(0.6f);
        }

        muteImageView = new ImageView(context);
        muteImageView.setScaleType(ImageView.ScaleType.CENTER);
        muteImageView.setImageResource(R.drawable.video_mute);
        muteImageView.setAlpha(0f);
        addView(muteImageView, LayoutHelper.createFrame(48, 48, Gravity.CENTER));

        textureView.setOnTouchListener(this::handleZoomTouch);
        super.setVisibility(INVISIBLE);
    }

    @Override
    public void setButtonsBackground(
            BlurredBackgroundDrawableViewFactory factory,
            BlurredBackgroundColorProvider colorProvider
    ) {
        BlurredBackgroundDrawable drawable = factory.create(buttonsLayout, colorProvider);
        drawable.setPadding(dp(6));
        drawable.setRadius(dp(21));
        buttonsLayout.setBackground(drawable);
    }

    @Override
    public void setInternalPadding(int padding) {
        setPadding(0, 0, 0, padding);
    }

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);
        buttonsLayout.setAlpha(0f);
        cameraContainer.setAlpha(0f);
        cameraContainer.setScaleX(setVisibilityFromPause ? 1f : 0.1f);
        cameraContainer.setScaleY(setVisibilityFromPause ? 1f : 0.1f);
        cameraContainer.setTranslationX(0f);
        muteImageView.setAlpha(0f);
        muteImageView.setScaleX(1f);
        muteImageView.setScaleY(1f);
        progressView.getPaint().setAlpha(0);
        try {
            Activity activity = (Activity) getContext();
            if (visibility == VISIBLE) {
                activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            } else {
                activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    @Override
    public void destroy(boolean async) {
        rearCameraStartOverride = false;
        setRecordingUiFrameClockActive(false);
        cancelCameraFlipAnimation();
        if (zoomResetAnimator != null) zoomResetAnimator.cancel();
        releaseBatchPlayer();
        if (session != null) {
            session.release();
            session = null;
        }
        if (upload != null) {
            upload.release(!sent);
            upload = null;
        }
        setScreenFlashEnabled(false);
        MediaController.getInstance().requestRecordAudioFocus(false);
        startHeavyOperations();
    }

    @Override
    public void togglePause() {
        if (batchReviewVisible) return;
        if (session == null || stateInfo == null) return;
        if (stateInfo.getState() == RoundVideoSession.State.RECORDING) {
            if (multiSegmentEnabled && !batchSegments.isEmpty()) {
                finishContinuousBatch(BATCH_ACTION_REVIEW, null);
            } else {
                pauseForPreview();
            }
        } else if (stateInfo.getState() == RoundVideoSession.State.PREVIEWING) {
            videoEditedInfo = null;
            resumeNotificationPending = true;
            stopNotificationSent = false;
            session.resumeRecording();
        }
    }

    @Override
    public boolean isPaused() {
        return batchReviewVisible || stateInfo != null && (
                stateInfo.getState() == RoundVideoSession.State.PAUSING
                        || stateInfo.getState() == RoundVideoSession.State.PREVIEWING
                        || stateInfo.getState() == RoundVideoSession.State.RESUMING
        );
    }

    @Override
    public void showCamera(boolean fromPaused) {
        if (session != null) return;
        setVisibilityFromPause = fromPaused;
        setVisibility(VISIBLE);
        if (!fromPaused) {
            clearBatchSegments(true);
        }
        progressView.getPaint().setAlpha(255);
        sent = false;
        muted = false;
        startedNotificationSent = false;
        stopNotificationSent = false;
        lastVisibleDurationMs = 0L;
        progressView.setProgress(0f);
        showInitialPlaceholder();
        startNewRecordingSession();
        MediaController.getInstance().requestRecordAudioFocus(true);
        startAnimation(true, fromPaused);
    }

    private void startNewRecordingSession() {
        stateInfo = null;
        videoEditedInfo = null;
        pendingSend = null;
        muted = false;
        lastVisibleDurationMs = 0L;
        progressView.setProgress(0f);
        progressView.getPaint().setAlpha(255);
        if (multiSegmentEnabled) {
            upload = null;
        } else {
            upload = new TelegramRoundVideoUpload(currentAccount, secretChat);
        }
        activeOutputResolution = SharedSettings.roundVideoOutputResolution.get();
        boolean rearCameraStart = consumeRearCameraStart();
        if (rearCameraStart) {
            rearCameraStartOverride = true;
            cameraBeforeRearGesture = SharedSettings.roundVideoLastCamera.get();
        }
        RoundVideoSession.CameraFacing initialFacing = rearCameraStart
                ? RoundVideoSession.CameraFacing.BACK
                : SharedSettings.roundVideoLastCamera.get();
        session = new RoundVideoSession.Builder(getContext(), textureView)
                .setOutputDirectory(new File(ApplicationLoader.getFilesDirFixed(), "cache"))
                .setInitialFacing(initialFacing)
                .setOutputResolution(activeOutputResolution)
                .setVideoBitrate(SharedSettings.roundVideoVideoBitrate.get())
                .setCameraResolution(SharedSettings.roundVideoCameraResolution.get())
                .setFrameRate(SharedSettings.roundVideoFrameRate.get())
                .setMaximumDurationMs(
                        multiSegmentEnabled
                                ? BATCH_MAXIMUM_DURATION_MS + BATCH_SESSION_FINISH_MARGIN_MS
                                : 60_000L
                )
                .setCompositionEnabled(SharedSettings.roundVideoComposition.get())
                .setListener(sessionListener)
                .setOutputListener(multiSegmentEnabled ? batchOutputListener : upload)
                .setScreenFlashController(this::setScreenFlashEnabled)
                .build();
        session.start();
    }

    @Override
    public void startAnimation(boolean open, boolean fromPaused) {
        dispatchAnimationState(open, fromPaused);
        if (visibilityAnimator != null) {
            visibilityAnimator.removeAllListeners();
            visibilityAnimator.cancel();
        }
        PipRoundVideoView pipRoundVideoView = PipRoundVideoView.getInstance();
        if (pipRoundVideoView != null) pipRoundVideoView.showTemporary(!open);
        if (open && !opened) {
            cameraContainer.setTranslationX(0f);
            animationTranslationY = fromPaused ? 0f : getMeasuredHeight() * 0.5f;
            updateTranslationY();
        }
        opened = open;
        if (parentView != null) parentView.invalidate();
        float targetTranslationX = !open
                && Math.max(getCurrentDurationMs(), lastVisibleDurationMs) > 300L
                ? dp(24) - getMeasuredWidth() * 0.5f
                : 0f;
        ValueAnimator translationYAnimator = ValueAnimator.ofFloat(
                open ? 1f : 0f,
                open ? 0f : 1f
        );
        translationYAnimator.addUpdateListener(animation -> {
            animationTranslationY = fromPaused
                    ? 0f
                    : getMeasuredHeight() * 0.5f * (float) animation.getAnimatedValue();
            updateTranslationY();
        });
        visibilityAnimator = new AnimatorSet();
        visibilityAnimator.playTogether(
                ObjectAnimator.ofFloat(buttonsLayout, View.ALPHA, open ? 1f : 0f),
                ObjectAnimator.ofFloat(cameraContainer, View.ALPHA, open ? 1f : 0f),
                ObjectAnimator.ofFloat(cameraContainer, View.SCALE_X, open ? 1f : 0.1f),
                ObjectAnimator.ofFloat(cameraContainer, View.SCALE_Y, open ? 1f : 0.1f),
                ObjectAnimator.ofFloat(cameraContainer, View.TRANSLATION_X, targetTranslationX),
                ObjectAnimator.ofFloat(muteImageView, View.ALPHA, muted && open ? 1f : 0f),
                translationYAnimator
        );
        visibilityAnimator.setDuration(180L);
        visibilityAnimator.setInterpolator(new DecelerateInterpolator());
        if (!open) {
            visibilityAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (animation == visibilityAnimator) {
                        hideCamera(true);
                        setVisibilityFromPause = false;
                        setVisibility(INVISIBLE);
                    }
                }
            });
        } else {
            setTranslationX(0f);
        }
        visibilityAnimator.start();
    }

    @Override
    public RectF getCameraRect() {
        textureView.getLocationOnScreen(screenPosition);
        float scaleX = 1f;
        float scaleY = 1f;
        View ancestor = textureView;
        while (ancestor != null) {
            scaleX *= ancestor.getScaleX();
            scaleY *= ancestor.getScaleY();
            ancestor = ancestor.getParent() instanceof View ? (View) ancestor.getParent() : null;
        }
        return new RectF(
                screenPosition[0],
                screenPosition[1],
                screenPosition[0] + textureView.getWidth() * scaleX,
                screenPosition[1] + textureView.getHeight() * scaleY
        );
    }

    @Override
    public void changeVideoPreviewState(int state, float progress) {
        if (batchSending) return;
        if (batchReviewVisible && batchVideoPlayer != null) {
            persistSelectedBatchTrim();
            if (state == 0) {
                batchVideoPlayer.play();
            } else if (state == 1) {
                batchVideoPlayer.pause();
            } else if (state == 2) {
                BatchSegment segment = batchSegments.get(selectedBatchSegment);
                batchVideoPlayer.seekTo(segment.sourceStartMs
                        + (long) (progress * segment.durationMs));
            }
            return;
        }
        if (session == null || stateInfo == null
                || stateInfo.getState() != RoundVideoSession.State.PREVIEWING) {
            return;
        }
        applyExternalTrim();
        if (state == 0) {
            session.playPreview();
        } else if (state == 1) {
            session.pausePreview();
        } else if (state == 2) {
            session.seekPreview((long) (progress * stateInfo.getRecordedDurationMs()));
        }
    }

    @Override
    public void send(
            int state,
            boolean notify,
            int scheduleDate,
            int scheduleRepeatPeriod,
            int ttl,
            long effectId,
            long stars
    ) {
        if (batchSending) return;
        SendOptions options = new SendOptions(
                notify,
                scheduleDate,
                scheduleRepeatPeriod,
                ttl,
                effectId,
                stars
        );
        if (multiSegmentEnabled && batchReviewVisible) {
            sendBatch(options);
            return;
        }
        if (session == null || stateInfo == null) return;
        if (state == 3) {
            if (multiSegmentEnabled && !batchSegments.isEmpty()) {
                finishContinuousBatch(BATCH_ACTION_REVIEW, null);
            } else {
                pauseForPreview();
            }
            return;
        }
        if (state != 1 && state != 4) return;
        long duration = getCurrentDurationMs();
        if (duration < MINIMUM_DURATION_MS) {
            if (multiSegmentEnabled && !batchSegments.isEmpty()) {
                session.cancel();
                session = null;
                stateInfo = null;
                sendBatch(options);
                return;
            }
            NotificationCenter.getInstance(currentAccount).postNotificationName(
                    NotificationCenter.audioRecordTooShort,
                    recordingGuid,
                    true,
                    (int) duration
            );
            cancel(false);
            return;
        }
        if (stateInfo.getState() == RoundVideoSession.State.RECORDING) {
            postRecordStopped(5);
        } else {
            applyExternalTrim();
        }
        if (multiSegmentEnabled && !batchSegments.isEmpty()) {
            finishContinuousBatch(BATCH_ACTION_SEND, options);
        } else {
            pendingSend = options;
            session.finish(!muted);
        }
    }

    @Override
    public void cancel(boolean byGesture) {
        if (batchSending) return;
        if (multiSegmentEnabled && batchReviewVisible) {
            postRecordStopped(byGesture ? 0 : 6);
            clearBatchSegments(true);
            MediaController.getInstance().requestRecordAudioFocus(false);
            startAnimation(false, false);
            return;
        }
        if (session == null) return;
        postRecordStopped(byGesture ? 0 : 6);
        session.cancel();
        if (upload != null) upload.release(true);
        upload = null;
        MediaController.getInstance().requestRecordAudioFocus(false);
        startAnimation(false, false);
    }

    @Override
    public View getButtonsLayout() {
        return buttonsLayout;
    }

    @Override
    public View getMuteImageView() {
        return muteImageView;
    }

    @Override
    public Paint getPaint() {
        return progressView.getPaint();
    }

    @Override
    public void hideCamera(boolean async) {
        destroy(async);
        cameraContainer.setTranslationX(0f);
        animationTranslationY = 0f;
        updateTranslationY();
        cameraContainer.setImageReceiver(null);
        MediaController.getInstance().resumeByRewind();
    }

    @Override
    public TextureView getTextureView() {
        return textureView;
    }

    @Override
    public InstantCameraViewBase.InstantViewCameraContainer getCameraContainer() {
        return cameraContainer;
    }

    @Override
    public void setIsMessageTransition(boolean messageTransition) {
        cameraContainer.messageTransition = messageTransition;
    }

    @Override
    public void resetCameraFile() {
    }

    @Override
    public void onPanTranslationUpdate(float translationY) {
        panTranslationY = translationY * 0.5f;
        updateTranslationY();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN
                && event.getY() > getMeasuredHeight() - getPaddingBottom()) {
            return false;
        }
        return true;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
        return super.onInterceptTouchEvent(event);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int newSize = MeasureSpec.getSize(heightMeasureSpec) - getPaddingBottom()
                > MeasureSpec.getSize(widthMeasureSpec) * 1.3f
                ? AndroidUtilities.roundPlayingMessageSize
                : AndroidUtilities.roundMessageSize;
        if (textureViewSize != newSize) {
            textureViewSize = newSize;
            cameraContainer.getLayoutParams().width = newSize + dp(28);
            cameraContainer.getLayoutParams().height = newSize + dp(28);
            ((LayoutParams) muteImageView.getLayoutParams()).topMargin = newSize / 2 - dp(24);
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int width = MeasureSpec.makeMeasureSpec(getMeasuredWidth(), MeasureSpec.EXACTLY);
        int height = MeasureSpec.makeMeasureSpec(getMeasuredHeight(), MeasureSpec.EXACTLY);
        flashViews.backgroundView.measure(width, height);
        flashViews.foregroundView.measure(width, height);
    }

    private boolean isRecordingState() {
        return stateInfo != null
                && stateInfo.getState() == RoundVideoSession.State.RECORDING;
    }

    private void setRecordingUiFrameClockActive(boolean active) {
        if (active == recordingUiFallbackRegistered) return;
        recordingUiFallbackRegistered = active;
        lastPreviewUiFrameRealtimeNs = 0L;
        if (active) {
            Choreographer60FpsContent.getInstance().addFrameCallback(
                    recordingUiFallback,
                    30
            );
        } else {
            Choreographer60FpsContent.getInstance().removeFrameCallback(recordingUiFallback);
        }
        dispatchRecordingUiFrameClockActive(active);
    }

    private void updateRecordingUiFrame() {
        RoundVideoSession.StateInfo info = stateInfo;
        if (info == null || info.getState() != RoundVideoSession.State.RECORDING) return;
        long duration = Math.min(
                info.getMaximumDurationMs(),
                info.getRecordedDurationMs()
                        + SystemClock.elapsedRealtime()
                        - info.getRecordingStartedAtRealtimeMs()
        );
        lastVisibleDurationMs = duration;
        if (multiSegmentEnabled) {
            while (batchSegments.size() < MAX_BATCH_SEGMENTS - 1) {
                long nextBoundaryMs = (batchSegments.size() + 1L)
                        * BATCH_DEMO_SEGMENT_DURATION_MS;
                if (duration < nextBoundaryMs) break;
                captureContinuousBoundary(nextBoundaryMs);
            }
            long currentSegmentDurationMs = duration
                    - batchSegments.size() * BATCH_DEMO_SEGMENT_DURATION_MS;
            currentSegmentDurationMs = Math.max(0L, Math.min(
                    BATCH_DEMO_SEGMENT_DURATION_MS,
                    currentSegmentDurationMs
            ));
            progressView.setProgress(
                    currentSegmentDurationMs / (float) BATCH_DEMO_SEGMENT_DURATION_MS
            );
            dispatchRecordingUiFrame(currentSegmentDurationMs);
            // Finish directly from RECORDING at the fifth boundary. Letting the
            // session's own limit pause first creates a full preview snapshot and
            // then finalizes the same 40-second file again before our editor opens.
            if (duration >= BATCH_MAXIMUM_DURATION_MS
                    && pendingBatchAction == BATCH_ACTION_NONE) {
                finishContinuousBatch(BATCH_ACTION_REVIEW, null);
            }
        } else {
            progressView.setProgress(duration / (float) info.getMaximumDurationMs());
            dispatchRecordingUiFrame(duration);
        }
    }

    private void captureContinuousBoundary(long endMs) {
        int index = batchSegments.size();
        long startMs = index * BATCH_DEMO_SEGMENT_DURATION_MS;
        Bitmap thumbnail = prepareBatchThumbnail(textureView.getBitmap());
        BatchSegment segment = new BatchSegment(
                null,
                endMs - startMs,
                startMs,
                endMs,
                true,
                SharedSettings.roundVideoFrameRate.get(),
                thumbnail
        );
        batchSegments.add(segment);
        updateBatchSelectionVisual(index, true);
        addBatchThumbnail(thumbnail, index, true);
    }

    private final RoundVideoSession.Listener sessionListener = new RoundVideoSession.Listener() {
        @Override
        public void onStateChanged(@NonNull RoundVideoSession.StateInfo info) {
            RoundVideoSession.State previous = stateInfo == null ? null : stateInfo.getState();
            stateInfo = info;
            if (previous == RoundVideoSession.State.RECORDING
                    && info.getState() != RoundVideoSession.State.RECORDING) {
                saveLastCameraBitmap(true);
            }
            lastVisibleDurationMs = Math.max(lastVisibleDurationMs, info.getRecordedDurationMs());
            if (info.getState() == RoundVideoSession.State.RECORDING) {
                progressView.getPaint().setAlpha(255);
                stopHeavyOperations();
                hideInitialPlaceholder();
                setRecordingUiFrameClockActive(true);
                updateRecordingUiFrame();
                if (!startedNotificationSent) {
                    startedNotificationSent = true;
                    NotificationCenter.getInstance(currentAccount).postNotificationName(
                            NotificationCenter.recordStarted,
                            recordingGuid,
                            false
                    );
                } else if (resumeNotificationPending) {
                    resumeNotificationPending = false;
                    NotificationCenter.getInstance(currentAccount).postNotificationName(
                            NotificationCenter.recordResumed
                    );
                }
            } else {
                setRecordingUiFrameClockActive(false);
                long visibleDuration = multiSegmentEnabled
                        ? info.getRecordedDurationMs() % BATCH_DEMO_SEGMENT_DURATION_MS
                        : info.getRecordedDurationMs();
                float visibleLimit = multiSegmentEnabled
                        ? BATCH_DEMO_SEGMENT_DURATION_MS
                        : info.getMaximumDurationMs();
                progressView.setProgress(visibleDuration / visibleLimit);
                if (info.getState() == RoundVideoSession.State.PREVIEWING) {
                    progressView.getPaint().setAlpha(0);
                    progressView.invalidate();
                }
            }
            if (info.getState() == RoundVideoSession.State.COMPLETED
                    || info.getState() == RoundVideoSession.State.ERROR
                    || info.getState() == RoundVideoSession.State.RELEASED) {
                cancelCameraFlipAnimation();
                startHeavyOperations();
            }
            if (previous == RoundVideoSession.State.RECORDING
                    && info.getState() == RoundVideoSession.State.PAUSING) {
                boolean automaticBatchSplit = multiSegmentEnabled
                        && info.getRecordedDurationMs() >= info.getMaximumDurationMs() - 50L;
                if (!automaticBatchSplit) {
                    postRecordStopped(2);
                }
            }
            updateButtons();
        }

        @Override
        public void onCameraCapabilitiesChanged(
                @NonNull RoundVideoSession.CameraCapabilities cameraCapabilities
        ) {
            capabilities = cameraCapabilities;
            if (cameraCapabilities.getActiveFacing() != null && !rearCameraStartOverride) {
                SharedSettings.roundVideoLastCamera.set(cameraCapabilities.getActiveFacing());
            }
            updateButtons();
        }

        @Override
        public void onCameraSwitchStarted(@NonNull RoundVideoSession.CameraFacing facing) {
            startCameraFlipAnimation(facing);
        }

        @Override
        public void onCameraSwitchCompleted(@NonNull RoundVideoSession.CameraFacing facing) {
            cameraFlipSessionReady = true;
            FileLog.d("RoundVideo camera flip session ready: facing=" + facing
                    + ", elapsedMs=" + getCameraFlipElapsedMs());
            maybeFinishCameraFlip();
        }

        @Override
        public void onCameraSwitchFirstFrame() {
            cameraFlipFirstFrameReady = true;
            FileLog.d("RoundVideo camera flip first frame: elapsedMs="
                    + getCameraFlipElapsedMs());
            hideCameraSwitchPlaceholder(true);
            maybeFinishCameraFlip();
        }

        @Override
        public void onPreviewReady(long durationMs, long trimStartMs, long trimEndMs) {
            if (multiSegmentEnabled && pendingBatchAction != BATCH_ACTION_NONE) {
                session.finish(!muted);
                return;
            }
            if (multiSegmentEnabled
                    && pendingBatchAction == BATCH_ACTION_NONE
                    && !batchSegments.isEmpty()
                    && stateInfo != null
                    && durationMs >= stateInfo.getMaximumDurationMs() - 50L) {
                finishContinuousBatch(BATCH_ACTION_REVIEW, null);
                return;
            }
            progressView.setProgress(durationMs / (float) getSegmentDurationLimitMs());
            muted = false;
            muteImageView.setAlpha(0f);
            File previewFile = session == null ? null : session.getPreviewFile();
            if (previewFile == null) return;
            videoEditedInfo = createVideoEditedInfo(previewFile, durationMs, null);
            videoEditedInfo.startTime = trimStartMs > 0 ? trimStartMs : -1;
            videoEditedInfo.endTime = trimEndMs < durationMs ? trimEndMs : -1;
            NotificationCenter.getInstance(currentAccount).postNotificationName(
                    NotificationCenter.audioDidSent,
                    recordingGuid,
                    videoEditedInfo,
                    previewFile.getAbsolutePath(),
                    new ArrayList<>()
            );
            float duration = Math.max(1L, durationMs);
            dispatchTrimRange(trimStartMs / duration, trimEndMs / duration);
        }

        @Override
        public void onPreviewFirstFrame() {
            hideInitialPlaceholder();
        }

        @Override
        public void onCompleted(@NonNull RoundVideoSession.Result result) {
            if (multiSegmentEnabled && pendingBatchAction != BATCH_ACTION_NONE) {
                completeContinuousBatch(result);
            } else {
                completeSend(result);
            }
        }

        @Override
        public void onError(@NonNull Exception error) {
            startHeavyOperations();
            FileLog.e(error);
            if (upload != null) upload.release(true);
            upload = null;
            MediaController.getInstance().requestRecordAudioFocus(false);
            if (startedNotificationSent) {
                postRecordStopped(6);
            } else {
                NotificationCenter.getInstance(currentAccount).postNotificationName(
                    NotificationCenter.recordStartError,
                    recordingGuid
                );
            }
            startAnimation(false, false);
        }
    };

    private void stopHeavyOperations() {
        if (heavyOperationsStopped) return;
        heavyOperationsStopped = true;
        NotificationCenter.getGlobalInstance().postNotificationName(
                NotificationCenter.stopAllHeavyOperations,
                512
        );
    }

    private void startHeavyOperations() {
        if (!heavyOperationsStopped) return;
        heavyOperationsStopped = false;
        NotificationCenter.getGlobalInstance().postNotificationName(
                NotificationCenter.startAllHeavyOperations,
                512
        );
    }

    private void pauseForPreview() {
        if (session == null || stateInfo == null
                || stateInfo.getState() != RoundVideoSession.State.RECORDING) {
            return;
        }
        postRecordStopped(2);
        session.pauseRecording();
    }

    private void postRecordStopped(int reason) {
        if (stopNotificationSent && reason == 2) return;
        stopNotificationSent = true;
        NotificationCenter.getInstance(currentAccount).postNotificationName(
                NotificationCenter.recordStopped,
                recordingGuid,
                reason
        );
    }

    private long getCurrentDurationMs() {
        if (session == null || stateInfo == null) return 0L;
        if (stateInfo.getState() == RoundVideoSession.State.RECORDING) {
            return session.getRecordedDurationMs();
        }
        return stateInfo.getRecordedDurationMs();
    }

    private void applyExternalTrim() {
        if (session == null || stateInfo == null || videoEditedInfo == null) return;
        long duration = stateInfo.getRecordedDurationMs();
        long start = Math.max(0L, videoEditedInfo.startTime);
        long end = videoEditedInfo.endTime < 0
                ? duration
                : Math.min(duration, videoEditedInfo.endTime);
        session.setTrimRange(start, end);
    }

    private void finishContinuousBatch(int action, SendOptions options) {
        if (!multiSegmentEnabled || session == null || stateInfo == null
                || pendingBatchAction != BATCH_ACTION_NONE) {
            return;
        }
        long duration = getCurrentDurationMs();
        if (duration < MINIMUM_DURATION_MS) {
            if (action == BATCH_ACTION_SEND && !batchSegments.isEmpty()) {
                session.cancel();
                session = null;
                stateInfo = null;
                sendBatch(options);
            } else {
                NotificationCenter.getInstance(currentAccount).postNotificationName(
                        NotificationCenter.audioRecordTooShort,
                        recordingGuid,
                        true,
                        (int) duration
                );
                cancel(false);
            }
            return;
        }
        if (action == BATCH_ACTION_REVIEW
                && stateInfo.getState() == RoundVideoSession.State.RECORDING) {
            postRecordStopped(2);
        }
        pendingBatchThumbnail = prepareBatchThumbnail(textureView.getBitmap());
        pendingBatchAction = action;
        pendingBatchSend = options;
        progressView.getPaint().setAlpha(0);
        progressView.invalidate();
        long finalStartMs = batchSegments.size() * BATCH_DEMO_SEGMENT_DURATION_MS;
        if (duration - finalStartMs >= MINIMUM_DURATION_MS) {
            updateBatchSelectionVisual(batchSegments.size(), true);
            pendingBatchHolder = addBatchThumbnail(
                    pendingBatchThumbnail,
                    batchSegments.size(),
                    true
            );
        }
        session.finish(!muted);
    }

    private Bitmap prepareBatchThumbnail(Bitmap thumbnail) {
        if (thumbnail != null && (thumbnail.getWidth() > dp(160)
                || thumbnail.getHeight() > dp(160))) {
            Bitmap scaled = Bitmap.createScaledBitmap(thumbnail, dp(160), dp(160), true);
            if (scaled != thumbnail) thumbnail.recycle();
            return scaled;
        }
        return thumbnail;
    }

    private void completeContinuousBatch(@NonNull RoundVideoSession.Result result) {
        int action = pendingBatchAction;
        SendOptions options = pendingBatchSend;
        pendingBatchAction = BATCH_ACTION_NONE;
        pendingBatchSend = null;

        Bitmap thumbnail = pendingBatchThumbnail;
        pendingBatchThumbnail = null;
        long finalStartMs = batchSegments.size() * BATCH_DEMO_SEGMENT_DURATION_MS;
        long finalEndMs = Math.min(BATCH_MAXIMUM_DURATION_MS, result.getDurationMs());
        long finalDurationMs = Math.max(0L, finalEndMs - finalStartMs);
        if (finalDurationMs >= MINIMUM_DURATION_MS) {
            BatchSegment segment = new BatchSegment(
                    result.getFile(),
                    finalDurationMs,
                    finalStartMs,
                    finalEndMs,
                    result.hasAudio(),
                    result.getFrameRate(),
                    thumbnail
            );
            batchSegments.add(segment);
            int segmentIndex = batchSegments.size() - 1;
            if (pendingBatchHolder != null) {
                bindBatchThumbnail(pendingBatchHolder, segmentIndex);
                pendingBatchHolder = null;
            } else if (batchSegments.size() > 1) {
                View holder = addBatchThumbnail(segment.thumbnail, segmentIndex, true);
                bindBatchThumbnail(holder, segmentIndex);
            }
        } else {
            if (pendingBatchHolder != null) {
                batchStackLayout.removeView(pendingBatchHolder);
                pendingBatchHolder = null;
            }
            if (thumbnail != null && !thumbnail.isRecycled()) thumbnail.recycle();
        }

        for (int i = 0; i < batchSegments.size(); i++) {
            BatchSegment segment = batchSegments.get(i);
            segment.file = result.getFile();
            segment.hasAudio = result.hasAudio();
            segment.frameRate = result.getFrameRate();
        }
        for (int i = 0; i < batchStackLayout.getChildCount(); i++) {
            View holder = batchStackLayout.getChildAt(i);
            if (holder.getTag() instanceof Integer) {
                bindBatchThumbnail(holder, (Integer) holder.getTag());
            }
        }

        if (session != null) {
            session.release();
            session = null;
        }
        stateInfo = null;
        videoEditedInfo = null;
        progressView.setProgress(0f);

        if (batchSegments.size() <= 1) {
            batchStackLayout.setVisibility(GONE);
        }
        if (action == BATCH_ACTION_SEND) {
            sendBatch(options);
        } else {
            postRecordStopped(2);
            showBatchPreview(batchSegments.size() - 1);
        }
    }

    private View addBatchThumbnail(Bitmap thumbnail, int index, boolean animate) {
        batchStackLayout.setVisibility(VISIBLE);
        // Selection uses overlap order and brightness, with every thumbnail the same size.
        boolean selectedInReview = batchReviewVisible
                && !isBatchRecordingInProgress()
                && selectedBatchSegment == index;
        float restingScale = 1f;
        float restingAlpha = selectedInReview ? 1f : BATCH_STACK_IDLE_ALPHA;
        FrameLayout holder = new FrameLayout(getContext());
        ImageView image = new ImageView(getContext()) {
            private final Path thumbnailClip = new Path();

            @Override
            protected void onSizeChanged(int w, int h, int oldw, int oldh) {
                super.onSizeChanged(w, h, oldw, oldh);
                thumbnailClip.reset();
                thumbnailClip.addOval(0, 0, w, h, Path.Direction.CW);
            }

            @Override
            protected void onDraw(Canvas canvas) {
                // Outline clipping is hardware-only. Keep the same round image
                // when the holder is captured into the flight's bitmap canvas,
                // so the flight keeps the exact same circular silhouette.
                int save = canvas.save();
                canvas.clipPath(thumbnailClip);
                super.onDraw(canvas);
                canvas.restoreToCount(save);
            }
        };
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        });
        image.setClipToOutline(true);
        if (thumbnail != null) {
            image.setImageBitmap(thumbnail);
        } else {
            image.setImageResource(R.drawable.icplaceholder);
        }
        holder.addView(image, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.MATCH_PARENT,
                Gravity.FILL,
                4,
                4,
                4,
                4
        ));
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                dp(BATCH_STACK_THUMBNAIL_SIZE_DP),
                dp(BATCH_STACK_THUMBNAIL_SIZE_DP)
        );
        params.leftMargin = index * dp(BATCH_STACK_THUMBNAIL_STEP_DP);
        params.topMargin = dp(4);
        batchStackLayout.addView(holder, params);
        holder.setTag(index);
        updateBatchStackCenter(true);

        if (animate) {
            float startX = getWidth() * 0.5f - dp(BATCH_STACK_THUMBNAIL_SIZE_DP / 2f)
                    - dp(12) - batchStackLayout.getTranslationX() - params.leftMargin;
            float startY = getHeight() * 0.5f - dp(BATCH_STACK_THUMBNAIL_SIZE_DP / 2f)
                    - dp(58) - params.topMargin;
            holder.setTranslationX(startX);
            holder.setTranslationY(startY);
            holder.setScaleX(4.5f);
            holder.setScaleY(4.5f);
            holder.setAlpha(1f);
            holder.animate()
                    .translationX(0f)
                    .translationY(0f)
                    .scaleX(restingScale)
                    .scaleY(restingScale)
                    .alpha(restingAlpha)
                    .setDuration(680L)
                    .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                    .start();
        }
        return holder;
    }

    private void updateBatchStackCenter(boolean animate) {
        if (batchStackCenterFrozen) return;
        int count = batchStackLayout.getChildCount();
        if (count <= 0) return;
        if (batchStackLayout.getWidth() <= 0) {
            batchStackLayout.post(() -> updateBatchStackCenter(animate));
            return;
        }
        float contentWidth = dp(BATCH_STACK_THUMBNAIL_SIZE_DP
                + (count - 1) * BATCH_STACK_THUMBNAIL_STEP_DP);
        float target = (batchStackLayout.getWidth() - contentWidth) * 0.5f;
        batchStackLayout.animate().cancel();
        if (animate) {
            batchStackLayout.animate()
                    .translationX(target)
                    .setDuration(220L)
                    .setInterpolator(CubicBezierInterpolator.DEFAULT)
                    .start();
        } else {
            batchStackLayout.setTranslationX(target);
        }
    }

    private boolean canScrubBatchStack() {
        return batchReviewVisible && !batchSending && !isBatchRecordingInProgress()
                && !batchSegments.isEmpty();
    }

    private boolean handleBatchStackTouch(MotionEvent event) {
        if (!canScrubBatchStack()) return false;
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP) {
            AndroidUtilities.cancelRunOnUIThread(batchScrubPreviewRunnable);
            if (action == MotionEvent.ACTION_UP) batchScrubPreviewRunnable.run();
            else {
                pendingBatchScrubIndex = -1;
                updateBatchSelectionVisual(selectedBatchSegment, true);
            }
            batchStackTouchIndex = -1;
            return true;
        }
        if (action != MotionEvent.ACTION_DOWN && action != MotionEvent.ACTION_MOVE) return true;
        View nearest = null;
        float nearestDistance = Float.MAX_VALUE;
        for (int i = 0; i < batchStackLayout.getChildCount(); i++) {
            View child = batchStackLayout.getChildAt(i);
            if (!(child.getTag() instanceof Integer) || child.getVisibility() != VISIBLE) continue;
            float center = child.getX() + child.getWidth() * 0.5f;
            float distance = Math.abs(event.getX() - center);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = child;
            }
        }
        if (nearest == null) return true;
        int index = (Integer) nearest.getTag();
        if (action == MotionEvent.ACTION_MOVE && batchStackTouchIndex >= 0
                && index != batchStackTouchIndex) {
            View current = findBatchThumbnail(batchStackTouchIndex);
            if (current != null && nearestDistance + dp(4) >= Math.abs(event.getX()
                    - (current.getX() + current.getWidth() * 0.5f))) return true;
        }
        if (index != batchStackTouchIndex) {
            batchStackTouchIndex = index;
            updateBatchSelectionVisual(index, true);
            pendingBatchScrubIndex = index;
            AndroidUtilities.cancelRunOnUIThread(batchScrubPreviewRunnable);
            if (action == MotionEvent.ACTION_DOWN) batchScrubPreviewRunnable.run();
            else AndroidUtilities.runOnUIThread(batchScrubPreviewRunnable, 65L);
        }
        return true;
    }

    private void bindBatchThumbnail(View holder, int index) {
        holder.setTag(index);
        holder.setContentDescription("Video message " + (index + 1));
        holder.setOnClickListener(view -> {
            if (!batchSending && !isBatchRecordingInProgress()) {
                showBatchPreview(index);
            }
        });
    }

    private void showBatchPreview(int index) {
        if (isBatchRecordingInProgress()) return;
        if (index < 0 || index >= batchSegments.size()) return;
        if (batchSending && batchVideoPlayer != null) {
            // Sending advances through ranges of the same continuous recording.
            // Reopening the editor here restarts its UI animation and decoder on
            // every row shift, interrupting the concurrently departing thumbnails.
            selectedBatchSegment = index;
            BatchSegment next = batchSegments.get(index);
            updateBatchSelectionVisual(index, true);
            videoEditedInfo = createBatchVideoEditedInfo(next, false);
            batchVideoPlayer.setMute(!next.hasAudio || muted);
            batchVideoPlayer.seekTo(next.sourceStartMs + Math.max(0L, next.trimStartMs));
            batchVideoPlayer.play();
            return;
        }
        boolean reusePlayer = batchVideoPlayer != null && selectedBatchSegment >= 0
                && selectedBatchSegment < batchSegments.size()
                && batchSegments.get(selectedBatchSegment).file.equals(batchSegments.get(index).file);
        persistSelectedBatchTrim();
        batchReviewVisible = true;
        selectedBatchSegment = index;
        if (!reusePlayer) releaseBatchPlayer();
        BatchSegment segment = batchSegments.get(index);
        updateBatchSelectionVisual(index, true);
        if (!reusePlayer) {
        batchVideoPlayer = new VideoPlayer();
        batchVideoPlayer.setDelegate(new VideoPlayer.VideoPlayerDelegate() {
            @Override
            public void onStateChanged(boolean playWhenReady, int playbackState) {
                if (batchVideoPlayer != null
                        && playbackState == androidx.media3.exoplayer.ExoPlayer.STATE_ENDED) {
                    BatchSegment playing = selectedBatchSegment >= 0
                            && selectedBatchSegment < batchSegments.size()
                            ? batchSegments.get(selectedBatchSegment) : segment;
                    batchVideoPlayer.seekTo(playing.sourceStartMs
                            + Math.max(0L, playing.trimStartMs));
                    batchVideoPlayer.play();
                }
            }

            @Override
            public void onError(VideoPlayer player, Exception error) {
                FileLog.e(error);
            }

            @Override
            public void onVideoSizeChanged(
                    int width,
                    int height,
                    int unappliedRotationDegrees,
                    float pixelWidthHeightRatio
            ) {
            }

            @Override
            public void onRenderedFirstFrame() {
                hideInitialPlaceholder();
            }
        });
        batchVideoPlayer.setTextureView(textureView);
        batchVideoPlayer.setLooping(true);
        batchVideoPlayer.preparePlayer(Uri.fromFile(segment.file), "other");
        }
        batchVideoPlayer.setMute(!segment.hasAudio || muted);
        batchVideoPlayer.seekTo(segment.sourceStartMs
                + Math.max(0L, segment.trimStartMs));
        batchVideoPlayer.play();
        progressView.setProgress(0f);
        progressView.getPaint().setAlpha(0);
        progressView.invalidate();
        videoEditedInfo = createBatchVideoEditedInfo(segment, false);
        NotificationCenter.getInstance(currentAccount).postNotificationName(
                NotificationCenter.audioDidSent,
                recordingGuid,
                videoEditedInfo,
                segment.file.getAbsolutePath(),
                new ArrayList<>()
        );
        float duration = Math.max(1L, segment.durationMs);
        dispatchTrimRange(
                segment.trimStartMs / duration,
                segment.trimEndMs / duration
        );
        AndroidUtilities.cancelRunOnUIThread(batchPreviewProgressRunnable);
        AndroidUtilities.runOnUIThread(batchPreviewProgressRunnable, 33L);
    }

    private void updateBatchSelectionVisual(int index, boolean animate) {
        boolean canShowSelection = batchReviewVisible && !isBatchRecordingInProgress();
        View selectedView = null;
        for (int i = 0; i < batchStackLayout.getChildCount(); i++) {
            View child = batchStackLayout.getChildAt(i);
            boolean selected = canShowSelection
                    && child.getTag() instanceof Integer
                    && (Integer) child.getTag() == index;
            if (animate) {
                child.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .alpha(selected ? 1f : BATCH_STACK_IDLE_ALPHA)
                        .setDuration(190L)
                        .setInterpolator(CubicBezierInterpolator.DEFAULT)
                        .start();
            } else {
                child.animate().cancel();
                child.setScaleX(1f);
                child.setScaleY(1f);
                child.setAlpha(selected ? 1f : BATCH_STACK_IDLE_ALPHA);
            }
            if (selected) selectedView = child;
        }
        if (selectedView != null) {
            restoreBatchThumbnailOrder(index);
            batchStackLayout.invalidate();
        }
    }

    private void restoreBatchThumbnailOrder(int selected) {
        for (int i = 0; i < selected; i++) {
            View thumbnail = findBatchThumbnail(i);
            if (thumbnail != null) thumbnail.bringToFront();
        }
        for (int i = batchSegments.size() - 1; i >= selected; i--) {
            View thumbnail = findBatchThumbnail(i);
            if (thumbnail != null) thumbnail.bringToFront();
        }
        batchStackLayout.invalidate();
    }

    private View batchThumbnailFlightSource(View holder) {
        if (holder == null) return null;
        // Capture only the image, not its transparent gutter. This keeps the
        // flight's visible bounds aligned with the camera and message bubble.
        View image = ((FrameLayout) holder).getChildAt(0);
        image.setAlpha(holder.getAlpha());
        return image;
    }

    private boolean isBatchRecordingInProgress() {
        return stateInfo != null && stateInfo.getState() == RoundVideoSession.State.RECORDING;
    }

    private void persistSelectedBatchTrim() {
        if (selectedBatchSegment < 0
                || selectedBatchSegment >= batchSegments.size()
                || videoEditedInfo == null) {
            return;
        }
        BatchSegment segment = batchSegments.get(selectedBatchSegment);
        long start = Math.max(0L, videoEditedInfo.startTime);
        long end = videoEditedInfo.endTime < 0
                ? segment.durationMs
                : Math.min(segment.durationMs, videoEditedInfo.endTime);
        if (end - start < MINIMUM_DURATION_MS) {
            end = Math.min(segment.durationMs, start + MINIMUM_DURATION_MS);
        }
        segment.trimStartMs = start;
        segment.trimEndMs = Math.max(start, end);
    }

    private VideoEditedInfo createBatchVideoEditedInfo(
            BatchSegment segment,
            boolean absoluteSourceRange
    ) {
        VideoEditedInfo info = new VideoEditedInfo();
        info.startTime = -1;
        info.endTime = -1;
        info.estimatedDuration = segment.durationMs;
        long sourceDurationMs = batchSegments.isEmpty()
                ? BATCH_MAXIMUM_DURATION_MS
                : Math.max(1L, batchSegments.get(batchSegments.size() - 1).sourceEndMs);
        info.estimatedSize = segment.file == null
                ? 1L
                : Math.max(1L, (long) (segment.file.length()
                * (segment.durationMs / (double) sourceDurationMs)));
        info.roundVideo = true;
        info.framerate = segment.frameRate.getValue();
        int outputSize = activeOutputResolution == null
                ? RoundVideoSession.OutputResolution.P480.getSize()
                : activeOutputResolution.getSize();
        info.resultWidth = info.originalWidth = outputSize;
        info.resultHeight = info.originalHeight = outputSize;
        info.originalPath = segment.file == null ? null : segment.file.getAbsolutePath();
        info.muted = !segment.hasAudio;
        if (absoluteSourceRange) {
            info.startTime = segment.sourceStartMs + segment.trimStartMs;
            info.endTime = segment.sourceStartMs + segment.trimEndMs;
        } else {
            if (segment.trimStartMs > 0L) {
                info.startTime = segment.trimStartMs;
            }
            if (segment.trimEndMs < segment.durationMs) {
                info.endTime = segment.trimEndMs;
            }
        }
        return info;
    }

    private long getSegmentDurationLimitMs() {
        return multiSegmentEnabled ? BATCH_DEMO_SEGMENT_DURATION_MS : 60_000L;
    }

    private void sendBatch(SendOptions options) {
        if (options == null || batchSegments.isEmpty() || batchSending) return;
        batchStackLayout.animate().cancel();
        batchStackCenterFrozen = true;
        batchSending = true;
        persistSelectedBatchTrim();
        int finalSegmentIndex = batchSegments.size() - 1;
        final int sendSelectionSlot = selectedBatchSegment >= 0
                ? Math.min(selectedBatchSegment, finalSegmentIndex) : finalSegmentIndex;
        updateBatchSelectionVisual(sendSelectionSlot, false);
        sent = true;
        ArrayList<BatchSegment> segments = new ArrayList<>(batchSegments);
        ArrayList<MediaController.PhotoEntry> entries = new ArrayList<>(segments.size());
        ArrayList<VideoEditedInfo> editedInfos = new ArrayList<>(segments.size());
        for (int i = 0; i < segments.size(); i++) {
            BatchSegment segment = segments.get(i);
            VideoEditedInfo info = createBatchVideoEditedInfo(segment, true);
            long trimStart = Math.max(0L, segment.trimStartMs);
            long trimEnd = Math.min(segment.durationMs, segment.trimEndMs);
            long fullDuration = Math.max(1L, segment.durationMs);
            info.estimatedDuration = Math.max(1L, trimEnd - trimStart);
            info.estimatedSize = Math.max(
                    1L,
                    (long) (info.estimatedSize
                            * (info.estimatedDuration / (double) fullDuration))
            );
            if (info.startTime >= 0L) info.startTime *= 1000L;
            if (info.endTime >= 0L) info.endTime *= 1000L;
            MediaController.PhotoEntry entry = new MediaController.PhotoEntry(
                    0,
                    0,
                    0,
                    segment.file.getAbsolutePath(),
                    0,
                    true,
                    0,
                    0,
                    0
            );
            entry.ttl = options.ttl;
            entry.effectId = options.effectId;
            entries.add(entry);
            editedInfos.add(info);

        }

        if (segments.size() == 1) {
            delegate.sendMedia(
                    entries.get(0),
                    editedInfos.get(0),
                    options.notify,
                    options.scheduleDate,
                    options.scheduleRepeatPeriod,
                    false,
                    options.stars
            );
            AndroidUtilities.runOnUIThread(() -> {
                clearBatchSegments(false);
                MediaController.getInstance().requestRecordAudioFocus(false);
            }, 500L);
            return;
        }

        Runnable sendFinal = () -> {
            View finalThumbnail = findBatchThumbnail(finalSegmentIndex);
            delegate.animateRoundVideoThumbnailIntoCamera(batchThumbnailFlightSource(finalThumbnail), () -> {
                batchStackLayout.animate().cancel();
                batchStackLayout.setAlpha(0f);
                batchStackLayout.setVisibility(GONE);
                batchReviewVisible = false;
                // Once the final stack thumbnail has merged under the central preview,
                // use Telegram's normal single-round transition into the dialog.
                delegate.sendMedia(
                        entries.get(finalSegmentIndex),
                        editedInfos.get(finalSegmentIndex),
                        options.notify,
                        options.scheduleDate,
                        options.scheduleRepeatPeriod,
                        false,
                        options.stars
                );
                AndroidUtilities.runOnUIThread(() -> {
                    clearBatchSegments(false);
                    MediaController.getInstance().requestRecordAudioFocus(false);
                }, 500L);
            });
        };
        if (sendSelectionSlot < finalSegmentIndex) {
            sendBatchWithMovingSelection(entries, editedInfos, options, sendSelectionSlot, sendFinal);
            return;
        }
        int stackSize = entries.size() - 1;
        for (int i = 0; i < stackSize; i++) {
            final int index = i;
            Runnable sendSegment = () -> sendBatchStackSegment(
                    index, finalSegmentIndex, entries.get(index), editedInfos.get(index), options);
            if (i == 0) sendSegment.run();
            else AndroidUtilities.runOnUIThread(sendSegment, i * BATCH_STACK_SEND_STAGGER_MS);
        }
        AndroidUtilities.runOnUIThread(sendFinal, (stackSize - 1L) * BATCH_STACK_SEND_STAGGER_MS
                + BATCH_STACK_FLIGHT_DURATION_MS + BATCH_FINAL_SEND_GAP_MS);
    }

    private void showBatchSendFrame(int index) {
        selectedBatchSegment = index;
        BatchSegment segment = batchSegments.get(index);
        videoEditedInfo = createBatchVideoEditedInfo(segment, false);
        // During the short send sequence, use the same immutable frame as the stack.
        // Decoder seeks cannot race selection changes or reveal a previous segment.
        batchSendPreviewFrozen = true;
        textureOverlayView.animate().cancel();
        drawInitialPlaceholder = false;
        if (segment.thumbnail != null) textureOverlayView.setImageBitmap(segment.thumbnail);
        else textureOverlayView.setImageResource(R.drawable.icplaceholder);
        textureOverlayView.setAlpha(1f);
        textureOverlayView.invalidate();
    }

    private void sendBatchWithMovingSelection(
            ArrayList<MediaController.PhotoEntry> entries, ArrayList<VideoEditedInfo> infos,
            SendOptions options, int slot, Runnable sendFinal) {
        AndroidUtilities.cancelRunOnUIThread(batchPreviewProgressRunnable);
        if (batchVideoPlayer != null) batchVideoPlayer.pause();
        showBatchSendFrame(slot);
        final int last = entries.size() - 1;
        class SendSequence implements Runnable {
            int next;
            int flights;
            boolean rowsDone;
            boolean finalStarted;

            void finishIfReady() {
                if (!batchSending || finalStarted || !rowsDone) return;
                finalStarted = true;
                // Keep the same cadence as every preceding departure. Earlier
                // thumbnails may finish their flight concurrently with this handoff.
                sendFinal.run();
            }

            @Override
            public void run() {
                if (!batchSending) return;
                if (next == last) {
                    rowsDone = true;
                    finishIfReady();
                    return;
                }
                final int index = next++;
                final int selection = Math.min(slot + index + 1, last);
                View source = findBatchThumbnail(index);
                if (source != null) {
                    source.animate().cancel();
                    source.setScaleX(1f);
                    source.setScaleY(1f);
                    flights++;
                    delegate.prepareRoundVideoTransitionSourceWithCompletion(batchThumbnailFlightSource(source), () -> {
                        flights--;
                        finishIfReady();
                    });
                    batchStackLayout.removeView(source);
                }
                ArrayList<Animator> movements = new ArrayList<>();
                for (int i = 0; i < batchStackLayout.getChildCount(); i++) {
                    View child = batchStackLayout.getChildAt(i);
                    child.animate().cancel();
                    boolean selected = child.getTag() instanceof Integer
                            && (Integer) child.getTag() == selection;
                    movements.add(ObjectAnimator.ofFloat(child, View.TRANSLATION_X,
                            -(index + 1) * dp(BATCH_STACK_THUMBNAIL_STEP_DP)));
                    movements.add(ObjectAnimator.ofFloat(child, View.SCALE_X, 1f));
                    movements.add(ObjectAnimator.ofFloat(child, View.SCALE_Y, 1f));
                    movements.add(ObjectAnimator.ofFloat(child, View.ALPHA, selected ? 1f : BATCH_STACK_IDLE_ALPHA));
                }
                // Transfer overlap order when the incoming selection becomes
                // brighter than the outgoing one, on the same animation clock.
                boolean[] selectionTransferred = {false};
                ValueAnimator selectionSync = ValueAnimator.ofFloat(0f, 1f);
                selectionSync.addUpdateListener(animation -> {
                    if (!selectionTransferred[0] && (float) animation.getAnimatedValue() >= 0.5f) {
                        selectionTransferred[0] = true;
                        restoreBatchThumbnailOrder(selection);
                        showBatchSendFrame(selection);
                    }
                    batchStackLayout.invalidate();
                });
                movements.add(selectionSync);
                AnimatorSet shift = new AnimatorSet();
                shift.playTogether(movements);
                shift.setDuration(BATCH_STACK_SHIFT_DURATION_MS);
                shift.setInterpolator(CubicBezierInterpolator.DEFAULT);
                shift.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (!batchSending) return;
                        AndroidUtilities.runOnUIThread(SendSequence.this,
                                BATCH_STACK_SEND_STAGGER_MS - BATCH_STACK_SHIFT_DURATION_MS);
                    }
                });
                delegate.sendMediaKeepingCamera(entries.get(index), infos.get(index), options.notify,
                        options.scheduleDate, options.scheduleRepeatPeriod, false, options.stars);
                shift.start();
            }
        }
        new SendSequence().run();
    }

    private void sendBatchStackSegment(
            int index,
            int nextSelectedIndex,
            MediaController.PhotoEntry entry,
            VideoEditedInfo editedInfo,
            SendOptions options
    ) {
        View source = findBatchThumbnail(index);
        if (source != null) {
            source.animate().cancel();
            source.setScaleX(1f);
            source.setScaleY(1f);
            delegate.prepareRoundVideoTransitionSource(batchThumbnailFlightSource(source));
            batchStackLayout.removeView(source);
        }

        // The selection advances through the original segment indices as the row
        // shifts beneath its slot. Once it reaches the last segment, it follows it.
        if (selectedBatchSegment != nextSelectedIndex) {
            showBatchPreview(nextSelectedIndex);
        }
        float targetTranslation = -(index + 1) * dp(BATCH_STACK_THUMBNAIL_STEP_DP);
        for (int i = 0; i < batchStackLayout.getChildCount(); i++) {
            View child = batchStackLayout.getChildAt(i);
            child.animate()
                    .translationX(targetTranslation)
                    .setDuration(BATCH_STACK_SHIFT_DURATION_MS)
                    .setInterpolator(CubicBezierInterpolator.DEFAULT)
                    .start();
        }

        delegate.sendMediaKeepingCamera(
                entry,
                editedInfo,
                options.notify,
                options.scheduleDate,
                options.scheduleRepeatPeriod,
                false,
                options.stars
        );
    }

    private View findBatchThumbnail(int index) {
        for (int i = 0; i < batchStackLayout.getChildCount(); i++) {
            View child = batchStackLayout.getChildAt(i);
            if (child.getTag() instanceof Integer && (Integer) child.getTag() == index) {
                return child;
            }
        }
        return null;
    }

    private void releaseBatchPlayer() {
        AndroidUtilities.cancelRunOnUIThread(batchPreviewProgressRunnable);
        if (batchVideoPlayer != null) {
            batchVideoPlayer.releasePlayer(true);
            batchVideoPlayer = null;
        }
    }

    private void clearBatchSegments(boolean deleteFiles) {
        AndroidUtilities.cancelRunOnUIThread(batchScrubPreviewRunnable);
        pendingBatchScrubIndex = -1;
        releaseBatchPlayer();
        if (batchSendPreviewFrozen) {
            batchSendPreviewFrozen = false;
            textureOverlayView.setImageBitmap((Bitmap) null);
            textureOverlayView.setAlpha(0f);
        }
        if (deleteFiles) {
            for (BatchSegment segment : batchSegments) {
                try {
                    if (segment.file != null) segment.file.delete();
                } catch (Throwable ignore) {
                }
            }
        }
        for (BatchSegment segment : batchSegments) {
            if (segment.thumbnail != null && !segment.thumbnail.isRecycled()) {
                segment.thumbnail.recycle();
            }
        }
        batchSegments.clear();
        batchStackLayout.removeAllViews();
        batchStackLayout.animate().cancel();
        batchStackLayout.setTranslationX(0f);
        batchStackLayout.setAlpha(1f);
        batchStackLayout.setVisibility(GONE);
        pendingBatchHolder = null;
        batchReviewVisible = false;
        batchSending = false;
        batchStackCenterFrozen = false;
        batchStackTouchIndex = -1;
        selectedBatchSegment = -1;
        pendingBatchAction = BATCH_ACTION_NONE;
        pendingBatchSend = null;
        if (pendingBatchThumbnail != null && !pendingBatchThumbnail.isRecycled()) {
            pendingBatchThumbnail.recycle();
        }
        pendingBatchThumbnail = null;
    }

    private void completeSend(@NonNull RoundVideoSession.Result result) {
        SendOptions options = pendingSend;
        if (options == null) return;
        pendingSend = null;
        sent = true;
        TelegramRoundVideoUpload.UploadInfo uploadInfo = upload == null
                ? null
                : upload.getUploadInfo(result.getOutputId(), result.getFile());
        VideoEditedInfo info = createVideoEditedInfo(
                result.getFile(),
                result.getDurationMs(),
                uploadInfo
        );
        info.muted = !result.hasAudio();
        MediaController.PhotoEntry entry = new MediaController.PhotoEntry(
                0,
                0,
                0,
                result.getFile().getAbsolutePath(),
                0,
                true,
                0,
                0,
                0
        );
        entry.ttl = options.ttl;
        entry.effectId = options.effectId;
        delegate.sendMedia(
                entry,
                info,
                options.notify,
                options.scheduleDate,
                options.scheduleRepeatPeriod,
                false,
                options.stars
        );
        if (upload != null) upload.release(false);
        upload = null;
        MediaController.getInstance().requestRecordAudioFocus(false);
    }

    private VideoEditedInfo createVideoEditedInfo(
            @NonNull File file,
            long durationMs,
            TelegramRoundVideoUpload.UploadInfo uploadInfo
    ) {
        VideoEditedInfo info = new VideoEditedInfo();
        info.startTime = -1;
        info.endTime = -1;
        info.estimatedDuration = durationMs;
        info.estimatedSize = Math.max(1L, uploadInfo == null ? file.length() : uploadInfo.size);
        info.roundVideo = true;
        info.framerate = session == null || session.getActiveFrameRate() == null
                ? RoundVideoSession.FrameRate.FPS_30.getValue()
                : session.getActiveFrameRate().getValue();
        int outputSize = activeOutputResolution == null
                ? RoundVideoSession.OutputResolution.P480.getSize()
                : activeOutputResolution.getSize();
        info.resultWidth = info.originalWidth = outputSize;
        info.resultHeight = info.originalHeight = outputSize;
        info.originalPath = file.getAbsolutePath();
        if (uploadInfo != null) {
            info.file = uploadInfo.inputFile;
            info.encryptedFile = uploadInfo.encryptedFile;
            info.key = uploadInfo.key;
            info.iv = uploadInfo.iv;
        }
        return info;
    }

    private void switchCamera() {
        if (session == null || stateInfo == null || capabilities == null
                || !stateInfo.canControlCamera() || cameraFlipInProgress) {
            return;
        }
        RoundVideoSession.CameraFacing facing =
                capabilities.getRequestedFacing() == RoundVideoSession.CameraFacing.FRONT
                        ? RoundVideoSession.CameraFacing.BACK
                        : RoundVideoSession.CameraFacing.FRONT;
        boolean overrideBefore = rearCameraStartOverride;
        rearCameraStartOverride = false;
        if (session.setCameraFacing(facing)) {
            switchCameraDrawable.setCurrentFrame(0);
            switchCameraDrawable.start();
        } else {
            rearCameraStartOverride = overrideBefore;
        }
    }

    private void toggleFlash() {
        if (session == null || stateInfo == null || capabilities == null) return;
        if (session.setFlashEnabled(!stateInfo.isFlashEnabled())) {
            updateFlashIcon();
        }
    }

    private void updateFlashIcon() {
        boolean enabled = stateInfo != null && stateInfo.isFlashEnabled();
        if (enabled) {
            if (flashOffDrawable == null) {
                flashOffDrawable = new RLottieDrawable(
                        R.raw.roundcamera_flash_off,
                        buttonIconSize,
                        buttonIconSize
                );
                flashOffDrawable.setCallback(flashButton);
            }
            flashButton.setImageDrawable(flashOffDrawable);
        } else {
            if (flashOnDrawable == null) {
                flashOnDrawable = new RLottieDrawable(
                        R.raw.roundcamera_flash_on,
                        buttonIconSize,
                        buttonIconSize
                );
                flashOnDrawable.setCallback(flashButton);
            }
            flashButton.setImageDrawable(flashOnDrawable);
        }
    }

    private void updateButtons() {
        boolean cameraControls = stateInfo != null
                && stateInfo.canControlCamera()
                && !cameraFlipInProgress;
        switchCameraButton.setEnabled(cameraControls);
        flashButton.setEnabled(cameraControls
                && capabilities != null
                && capabilities.getFlashType() != RoundVideoSession.FlashType.NONE);
        updateFlashIcon();
    }

    private void togglePreviewMuted() {
        if (session == null || stateInfo == null
                || stateInfo.getState() != RoundVideoSession.State.PREVIEWING) {
            return;
        }
        muted = !muted;
        session.setPreviewMuted(muted);
        if (videoEditedInfo != null) videoEditedInfo.muted = muted;
        muteImageView.animate().cancel();
        muteImageView.animate().alpha(muted ? 1f : 0f).setDuration(180L).start();
    }

    private boolean handleZoomTouch(View view, MotionEvent event) {
        if (session == null || stateInfo == null) {
            return false;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            if (zoomResetAnimator != null) zoomResetAnimator.cancel();
            pinchZoomActive = false;
            zoomPointerId1 = event.getPointerId(0);
            zoomPointerId2 = -1;
            if (stateInfo.getState() == RoundVideoSession.State.PREVIEWING) {
                togglePreviewMuted();
            }
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN
                && event.getPointerCount() == 2
                && stateInfo.canControlCamera()) {
            zoomPointerId1 = event.getPointerId(0);
            zoomPointerId2 = event.getPointerId(1);
            pinchStartDistance = pointerDistance(event, 0, 1);
            pinchZoomActive = pinchStartDistance > 0f;
            zoomProgress = 0f;
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_MOVE && pinchZoomActive) {
            int index1 = event.findPointerIndex(zoomPointerId1);
            int index2 = event.findPointerIndex(zoomPointerId2);
            if (index1 < 0 || index2 < 0) {
                finishZoomGesture();
                return true;
            }
            float scale = pointerDistance(event, index1, index2) / pinchStartDistance;
            float maximumZoomRatio = capabilities == null
                    ? 1f
                    : capabilities.getMaximumZoomRatio();
            float requestedZoomRatio = 1f
                    + Math.max(0f, scale - 1f) / ZOOM_GESTURE_EFFORT;
            zoomProgress = maximumZoomRatio <= 1f
                    ? 0f
                    : clamp(
                            (requestedZoomRatio - 1f) / (maximumZoomRatio - 1f),
                            0f,
                            1f
                    );
            session.setZoom(zoomProgress);
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_POINTER_UP && pinchZoomActive) {
            int pointerId = event.getPointerId(event.getActionIndex());
            if (pointerId == zoomPointerId1 || pointerId == zoomPointerId2) {
                finishZoomGesture();
            }
            return true;
        }
        if ((event.getActionMasked() == MotionEvent.ACTION_UP
                || event.getActionMasked() == MotionEvent.ACTION_CANCEL) && pinchZoomActive) {
            finishZoomGesture();
            return true;
        }
        return true;
    }

    private static float pointerDistance(MotionEvent event, int index1, int index2) {
        return (float) Math.hypot(
                event.getX(index2) - event.getX(index1),
                event.getY(index2) - event.getY(index1)
        );
    }

    private void finishZoomGesture() {
        if (!pinchZoomActive) return;
        pinchZoomActive = false;
        zoomPointerId1 = -1;
        zoomPointerId2 = -1;
        animateZoomReset();
    }

    private void animateZoomReset() {
        if (session == null) return;
        if (zoomResetAnimator != null) zoomResetAnimator.cancel();
        zoomResetAnimator = ValueAnimator.ofFloat(zoomProgress, 0f);
        zoomResetAnimator.setDuration(350L);
        zoomResetAnimator.addUpdateListener(animation -> {
            zoomProgress = (float) animation.getAnimatedValue();
            if (session != null) session.setZoom(zoomProgress);
        });
        zoomResetAnimator.start();
    }

    private void setScreenFlashEnabled(boolean enabled) {
        Activity activity = delegate.getParentActivity();
        if (activity == null) return;
        WindowManager.LayoutParams attributes = activity.getWindow().getAttributes();
        if (enabled) {
            if (Float.isNaN(previousWindowBrightness)) {
                previousWindowBrightness = attributes.screenBrightness;
            }
            attributes.screenBrightness = 1f;
            flashViews.flashIn(null);
        } else {
            if (!Float.isNaN(previousWindowBrightness)) {
                attributes.screenBrightness = previousWindowBrightness;
                previousWindowBrightness = Float.NaN;
            }
            flashViews.flashOut();
        }
        activity.getWindow().setAttributes(attributes);
    }

    private void updateTranslationY() {
        cameraContainer.setTranslationY(animationTranslationY + panTranslationY);
    }

    private void showInitialPlaceholder() {
        if (drawInitialPlaceholder) return;
        if (lastBitmap == null && !rearCameraStartOverride) {
            try {
                File file = new File(ApplicationLoader.getFilesDirFixed(), "icthumb.jpg");
                lastBitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
            } catch (Throwable ignore) {
            }
        }
        if (lastBitmap != null && !rearCameraStartOverride) {
            textureOverlayView.setImageBitmap(lastBitmap);
        } else {
            textureOverlayView.setImageResource(R.drawable.icplaceholder);
        }
        drawInitialPlaceholder = true;
        textureOverlayView.animate().cancel();
        textureOverlayView.setAlpha(1f);
        textureOverlayView.invalidate();
    }

    private void hideInitialPlaceholder() {
        if (batchSendPreviewFrozen) return;
        if (!drawInitialPlaceholder) return;
        drawInitialPlaceholder = false;
        textureOverlayView.invalidate();
        textureOverlayView.animate().cancel();
        textureOverlayView.animate()
                .alpha(0f)
                .setDuration(120L)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    private void showCameraSwitchPlaceholder() {
        saveLastCameraBitmap(false);
        if (lastBitmap == null) return;
        textureOverlayView.setImageBitmap(lastBitmap);
        switchPlaceholderVisible = true;
        textureOverlayView.animate().cancel();
        textureOverlayView.setAlpha(1f);
    }

    private void startCameraFlipAnimation(
            @NonNull RoundVideoSession.CameraFacing facing
    ) {
        cameraFlipGeneration++;
        if (cameraFlipAnimator != null) {
            cameraFlipAnimator.removeAllListeners();
            cameraFlipAnimator.cancel();
            cameraFlipAnimator = null;
        }
        cameraFlipInProgress = true;
        cameraFlipSessionReady = false;
        cameraFlipFirstFrameReady = false;
        cameraFlipAnimationCompleted = false;
        cameraFlipStartedRealtimeMs = SystemClock.elapsedRealtime();
        showCameraSwitchPlaceholder();
        previewContainer.animate().cancel();
        previewContainer.setRotationY(0f);
        previewContainer.setCameraDistance(previewContainer.getMeasuredHeight() * 8f);
        int generation = cameraFlipGeneration;
        cameraFlipAnimator = ValueAnimator.ofFloat(0f, 1f);
        cameraFlipAnimator.setDuration(CAMERA_FLIP_MINIMUM_DURATION_MS);
        cameraFlipAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        cameraFlipAnimator.addUpdateListener(animation -> {
            if (generation != cameraFlipGeneration) return;
            float progress = (float) animation.getAnimatedValue();
            float rotation = progress < 0.5f ? progress : progress - 1f;
            previewContainer.setRotationY(rotation * 180f);
        });
        cameraFlipAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (generation != cameraFlipGeneration) return;
                previewContainer.setRotationY(0f);
                cameraFlipAnimationCompleted = true;
                cameraFlipAnimator = null;
                FileLog.d("RoundVideo camera flip visual animation completed: elapsedMs="
                        + getCameraFlipElapsedMs());
                maybeFinishCameraFlip();
            }
        });
        cameraFlipAnimator.start();
        FileLog.d("RoundVideo camera flip started: facing=" + facing
                + ", minimumDurationMs=" + CAMERA_FLIP_MINIMUM_DURATION_MS);
        updateButtons();
    }

    private void maybeFinishCameraFlip() {
        if (!cameraFlipInProgress
                || !cameraFlipAnimationCompleted
                || !cameraFlipSessionReady
                || !cameraFlipFirstFrameReady) {
            return;
        }
        cameraFlipInProgress = false;
        FileLog.d("RoundVideo camera flip completed: elapsedMs="
                + getCameraFlipElapsedMs());
        updateButtons();
    }

    private void cancelCameraFlipAnimation() {
        if (!cameraFlipInProgress && cameraFlipAnimator == null) return;
        cameraFlipGeneration++;
        if (cameraFlipAnimator != null) {
            cameraFlipAnimator.removeAllListeners();
            cameraFlipAnimator.cancel();
            cameraFlipAnimator = null;
        }
        previewContainer.animate().cancel();
        previewContainer.animate().setListener(null);
        previewContainer.setRotationY(0f);
        cameraFlipInProgress = false;
        cameraFlipSessionReady = false;
        cameraFlipFirstFrameReady = false;
        cameraFlipAnimationCompleted = false;
        hideCameraSwitchPlaceholder(false);
    }

    private long getCameraFlipElapsedMs() {
        return cameraFlipStartedRealtimeMs == 0L
                ? 0L
                : Math.max(0L, SystemClock.elapsedRealtime() - cameraFlipStartedRealtimeMs);
    }

    private void hideCameraSwitchPlaceholder(boolean animated) {
        if (!switchPlaceholderVisible) return;
        switchPlaceholderVisible = false;
        textureOverlayView.animate().cancel();
        if (animated) {
            textureOverlayView.animate()
                    .alpha(0f)
                    .setDuration(120L)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        } else {
            textureOverlayView.setAlpha(0f);
        }
    }

    private void saveLastCameraBitmap(boolean persist) {
        if (!textureView.isAvailable()) return;
        Bitmap bitmap = textureView.getBitmap(
                SWITCH_PREVIEW_BITMAP_SIZE,
                SWITCH_PREVIEW_BITMAP_SIZE
        );
        if (bitmap == null) return;
        try {
            if (bitmap.getWidth() == 0
                    || bitmap.getHeight() == 0
                    || bitmap.getPixel(bitmap.getWidth() / 2, bitmap.getHeight() / 2) == 0) {
                return;
            }
            bitmap = applyPreviewBitmapTransform(bitmap);
            Utilities.stackBlurBitmap(bitmap, 15);
            Bitmap previous = lastBitmap;
            lastBitmap = bitmap;
            if (previous != null && previous != bitmap && !previous.isRecycled()) {
                previous.recycle();
            }
            if (persist) {
                try (FileOutputStream stream = new FileOutputStream(
                        new File(ApplicationLoader.getFilesDirFixed(), "icthumb.jpg")
                )) {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 87, stream);
                } catch (Throwable ignore) {
                }
            }
        } finally {
            if (bitmap != lastBitmap) bitmap.recycle();
        }
    }

    private Bitmap applyPreviewBitmapTransform(@NonNull Bitmap source) {
        if (textureView.getWidth() <= 0 || textureView.getHeight() <= 0) return source;
        textureView.getTransform(previewBitmapMatrix);
        if (previewBitmapMatrix.isIdentity()) return source;

        previewBitmapMatrix.getValues(previewBitmapMatrixValues);
        float scaleX = source.getWidth() / (float) textureView.getWidth();
        float scaleY = source.getHeight() / (float) textureView.getHeight();
        previewBitmapMatrixValues[Matrix.MSKEW_X] *= scaleX / scaleY;
        previewBitmapMatrixValues[Matrix.MTRANS_X] *= scaleX;
        previewBitmapMatrixValues[Matrix.MSKEW_Y] *= scaleY / scaleX;
        previewBitmapMatrixValues[Matrix.MTRANS_Y] *= scaleY;
        previewBitmapMatrixValues[Matrix.MPERSP_0] /= scaleX;
        previewBitmapMatrixValues[Matrix.MPERSP_1] /= scaleY;
        previewBitmapMatrix.setValues(previewBitmapMatrixValues);

        Bitmap transformed = Bitmap.createBitmap(
                source.getWidth(),
                source.getHeight(),
                Bitmap.Config.ARGB_8888
        );
        Canvas canvas = new Canvas(transformed);
        canvas.drawBitmap(source, previewBitmapMatrix, previewBitmapPaint);
        source.recycle();
        return transformed;
    }

    private TextureView createTextureView(Context context) {
        TextureView view = new TextureView(context) {
            @Override
            public void invalidate() {
                onPreviewTextureInvalidated();
                super.invalidate();
            }
        };
        view.setOpaque(true);
        view.setClickable(true);
        view.setCameraDistance(dp(8000));
        view.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View target, Outline outline) {
                outline.setOval(0, 0, target.getWidth(), target.getHeight());
            }
        });
        view.setClipToOutline(true);
        return view;
    }

    private void onPreviewTextureInvalidated() {
        if (handlingPreviewTextureInvalidate || !isRecordingState()) return;
        handlingPreviewTextureInvalidate = true;
        try {
            lastPreviewUiFrameRealtimeNs = SystemClock.elapsedRealtimeNanos();
            updateRecordingUiFrame();
        } finally {
            handlingPreviewTextureInvalidate = false;
        }
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private final class CameraContainer extends InstantCameraViewBase.InstantViewCameraContainer {
        private ImageReceiver imageReceiver;
        private float imageProgress;
        private boolean messageTransition;

        CameraContainer(Context context) {
            super(context);
            setWillNotDraw(false);
        }

        @Override
        public void setImageReceiver(ImageReceiver imageReceiver) {
            if (this.imageReceiver == null) imageProgress = 0f;
            this.imageReceiver = imageReceiver;
            invalidate();
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            super.dispatchDraw(canvas);
            if (imageReceiver == null) return;
            if (imageProgress < 1f) {
                imageProgress = Math.min(1f, imageProgress + 16f / 250f);
                invalidate();
            }
            canvas.save();
            canvas.translate(
                    progressView.getLeft() + previewContainer.getLeft() + textureView.getLeft(),
                    progressView.getTop() + previewContainer.getTop() + textureView.getTop()
            );
            if (imageReceiver.getImageWidth() != textureView.getWidth()) {
                float scale = textureView.getWidth() / imageReceiver.getImageWidth();
                canvas.scale(scale, scale);
            }
            canvas.translate(-imageReceiver.getImageX(), -imageReceiver.getImageY());
            float oldAlpha = imageReceiver.getAlpha();
            imageReceiver.setAlpha(imageProgress);
            imageReceiver.draw(canvas);
            imageReceiver.setAlpha(oldAlpha);
            canvas.restore();
        }
    }

    private static final class SendOptions {
        final boolean notify;
        final int scheduleDate;
        final int scheduleRepeatPeriod;
        final int ttl;
        final long effectId;
        final long stars;

        SendOptions(
                boolean notify,
                int scheduleDate,
                int scheduleRepeatPeriod,
                int ttl,
                long effectId,
                long stars
        ) {
            this.notify = notify;
            this.scheduleDate = scheduleDate;
            this.scheduleRepeatPeriod = scheduleRepeatPeriod;
            this.ttl = ttl;
            this.effectId = effectId;
            this.stars = stars;
        }
    }
}
