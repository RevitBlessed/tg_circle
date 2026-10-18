# Round Video UX Upgrades

## Submission baseline

- Upstream: [DrKLO/Telegram](https://github.com/DrKLO/Telegram).
- Exact base: [`f32ffeec763f0bdbf4bec25b52d654f77bff733d`](https://github.com/DrKLO/Telegram/commit/f32ffeec763f0bdbf4bec25b52d654f77bff733d), Android 13.0.2 / 7175.
- Fork: [RevitBlessed/Telegram](https://github.com/RevitBlessed/Telegram).
- Branch: `contest/round-video-upgrades`; latest release: `round-video-v1.0.1`.

The initial submission commit directly descends from the upstream base; subsequent commits contain documented fixes. The older `RevitBlessed/tg_circle` repository is retained for development history.

### v1.0.1 — edited circles sent as duplicate videos

The real sending path previously excluded round-video trim ranges from its sent-file cache key. Batch circles share one source MP4, so a later range could reuse an earlier uploaded document. `RoundVideoSendIdentity` now includes absolute start/end, duration, audio mode and output dimensions, with a new namespace that does not match old file-only cache entries.

`RoundVideoSegmentRange` clamps per-item editor trims and creates immutable source-microsecond ranges. Send snapshots are created before stack-selection animations; relative editor milliseconds are never converted in place. Regression tests cover two edited circles of equal length, re-editing, retry identity, short tails, deleted-item offsets and resumed ranges.

## 1. One-finger camera flip and recording lock

- Left: existing cancellation gesture.
- Up: lock recording without flipping.
- Diagonally up-left: flip the camera and lock together.

The flip bubble accompanies the lock bubble with synchronized progress for the diagonal gesture. A quick gesture selects the camera before recording starts; UI flip feedback is retained. A later diagonal gesture can switch an already running recording. New sessions start with the front camera; continuations within the same session retain the camera.

Gesture handling lives in `ChatActivityEnterView`. The recorder bridges camera selection into the Camera2 session; controller/GL changes suppress initial unstable frames during initialization and switching.

## 2. Up to five round videos in one session

- Each circle keeps the normal **60-second** limit, not the old 8-second demo setting.
- A continuous camera/encoder session supplies separate message ranges, rather than five simultaneous cameras or players.
- A centered thumbnail stack supports tap and finger-scrub selection, brightness/overlap highlighting and transparent spacing.
- Pause adds the unfinished circle to review; previous circles remain selectable.
- Resume continues the unfinished last circle even after viewing another one. Resume availability is based on session state, not the total file duration.
- Trash deletes only the selected circle when several remain; the last remaining circle uses normal discard behavior.
- Preview and timeline use per-item trim ranges and offsets into the continuous file.
- Sending preserves order and passes ranges through Telegram's existing media sending/conversion path in authenticated chats.

`InstantCameraView2` owns batch state, selection, ranges and send sequencing. `RoundVideoSession` owns recording, paused preview and resume. Chat delegates connect the controls and message sending.

One continuous source does not mean zero-cost export: outgoing clips still require media processing. Long recordings can increase disk use and export time on slower phones. Low-storage, thermal and background behavior require wider physical-device testing.

## 3. Countdown haptics

Short feedback occurs at 10, 5, 4, 3, 2 and 1 seconds remaining, with stronger feedback at the automatic boundary. Manual stop/cancel does not request the automatic-limit signal. Thresholds are one-shot; delayed callbacks do not replay missed ticks in a burst.

`RoundVideoRecordingCountdown` is Android-independent. `RoundVideoRecordingFeedback` handles platform feedback and system settings, including an older-device fallback for the stronger effect. Hardware and OS settings determine actual vibration availability/intensity.

The initial countdown feature was supplied by a collaborating developer and integrated with the session flow. No unverified author identity is assigned here; upstream license notices remain intact.

## Code map

Java paths below are relative to `TMessagesProj/src/main/java/org/telegram/`.

| Area | Files |
| --- | --- |
| Gesture, timer and controls | `ui/Components/ChatActivityEnterView.java` |
| Stack, preview, delete and send | `ui/Components/InstantCameraView2.java` |
| Recorder contract / legacy gesture support | `ui/Components/InstantCameraViewBase.java`, `InstantCameraView.java` |
| Session and initial/switch frames | `utils/camera/roundvideo/RoundVideoSession.java`, `RoundVideoCameraController.java`, `RoundVideoGlProcessor.java` |
| Chat integration and transitions | `ui/ChatActivity.java` |
| Segment timeline | `ui/Components/VideoTimelineView.java` |
| Haptics | `ui/Components/RoundVideoRecordingCountdown.java`, `RoundVideoRecordingFeedback.java` |
| Independent outgoing ranges and sent-file identity | `messenger/RoundVideoSegmentRange.java`, `RoundVideoSendIdentity.java`, `SendMessagesHelper.java` |
| Debug-only local-chat entry | `ui/LoginActivity.java` |

## Integration

```sh
git fetch https://github.com/RevitBlessed/Telegram.git contest/round-video-upgrades
git diff --stat f32ffeec763f0bdbf4bec25b52d654f77bff733d FETCH_HEAD
git log --oneline f32ffeec763f0bdbf4bec25b52d654f77bff733d..FETCH_HEAD
```

The submission commits can be cherry-picked in order onto the pinned base. On a newer revision, review/resolve recorder and chat conflicts before building. Do not copy only the gesture or stack UI: session, timeline, sending-cache and delegate changes are also required.

`ChatActivity.isMultiSegmentRoundVideoEnabled()` enables the feature; its recorder uses `InstantCameraView2`. There are no new third-party runtime dependencies or changes to protocol, native source, submodule pointers, app credentials or signing keys.

### Retained local demo

To preserve the accepted APK, debug builds retain the Russian-language "Открыть локальный чат" entry on the phone-number login page. It creates local messages and is **not** proof of network delivery. Some custom transitions are local-demo-specific. Use an authenticated account and a receiving client to check actual delivery.

An upstream maintainer may omit this debug entry/local-demo support during integration; the real multi-circle feature is not gated on it. It is documented rather than silently removed from the accepted implementation.

See [build instructions](docs/round-video/BUILD.md) and [validation](docs/round-video/VALIDATION.md). Contest eligibility and submission format must be checked against the specific organizer announcement.
