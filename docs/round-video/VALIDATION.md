# Verification and reviewer checklist

## v1.0.2 fix verification

The v1.0.1 sent-file identity fix did not eliminate the physical-file aliasing reported on a device: every batch item still pointed at one continuous recording and depended on downstream time ranges. v1.0.2 resolves the ambiguity before sending. Each final range is remuxed serially into its own MP4, each message receives a unique path with no remaining trim range, and message duration is read back from the resulting MP4 rather than copied from editor state.

The Android build and APK signature verification pass locally. The range/identity and countdown JVM tests continue to cover their production calculation helpers, but do not exercise Android media extraction. The v1.0.2 APK therefore still needs confirmation on the originally reported two-edited-video physical-device scenario; earlier acceptance is not claimed as acceptance of this fix.

## Evidence

- The project owner explicitly accepted the latest APK after the pause/resume fix.
- `:TMessagesProj_App:assembleAfatDebug` succeeded using the local native-library reuse route described in [BUILD.md](BUILD.md).
- APK signature verification and update installation on the emulator succeeded.
- The Android-independent countdown regression test is included and exercised separately.
- v1.0.2 retains the send-cache/range identity fix and removes shared-file ranges from the final send inputs entirely.

This is not exhaustive QA. Clean full-native builds, all variants, low-end performance, physical haptics and all network delivery cases are not certified here. The last automated emulator pass stopped at login, so authenticated recording was not exercised in that pass. The owner's subsequent acceptance is separate from automated testing.

## Manual acceptance checklist

Use an authenticated account and a suitable chat (e.g. Saved Messages). Check delivery on a receiving client; local-demo messages are not a delivery test.

1. Up locks without flip; left cancels; quick/slow diagonal gestures flip and lock together. Check initial camera/frame and UI feedback.
2. New sessions start on the front camera; pause/resume within a session retains the camera.
3. Cross the 60-second boundary and continue through five circles. Send and verify five ordered messages, each at most 60 seconds.
4. Pause on circle two: both thumbnails remain. Switch previews, then resume and pause again without lost/duplicate ranges.
5. Repeat on circles three through five, including previewing an earlier item before resuming.
6. Delete selected first/middle/last items; retain the others. Delete the only remaining item to discard.
7. Trim individual items, change selection and send; verify ranges/audio/order. Resume after trimming the unfinished last circle.
8. Scrub the stack and send with a middle item selected. Check movement, central-circle occlusion and no duplicate arrivals.
9. Check haptics at warning thresholds and automatic boundaries, no automatic-limit effect on manual cancel, and behavior with system haptics disabled.
10. On low-end physical hardware, test five-minute recording, heat, storage pressure, export duration, repeated pauses, camera switches, interruptions/backgrounding and permissions.

Video presentation and contest submission are provided separately by the owner. An unchecked item must not be called passed merely because an APK installs or the countdown test succeeds.
