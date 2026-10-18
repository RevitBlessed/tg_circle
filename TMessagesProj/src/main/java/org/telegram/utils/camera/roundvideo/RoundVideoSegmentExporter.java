package org.telegram.utils.camera.roundvideo;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.IOException;

/** Materializes one range of a completed round-video recording as an independent MP4. */
public final class RoundVideoSegmentExporter {

    public static final class Result {
        private final File file;
        private final long durationMs;

        private Result(@NonNull File file, long durationMs) {
            this.file = file;
            this.durationMs = durationMs;
        }

        public @NonNull File getFile() {
            return file;
        }

        public long getDurationMs() {
            return durationMs;
        }
    }

    private RoundVideoSegmentExporter() {
    }

    /**
     * Copies a final source range into its own MP4 and reports the duration found in that file.
     * Keeping a separate physical file per message prevents later editor/send stages from
     * resolving several messages back to the same continuous recording.
     */
    public static @NonNull Result export(
            @NonNull File source,
            @NonNull File outputDirectory,
            int outputSize,
            long startMs,
            long endMs,
            boolean includeAudio
    ) throws IOException {
        if (!source.isFile() || source.length() <= 0L) {
            throw new IOException("Round-video source is unavailable");
        }
        if (outputDirectory.exists()) {
            if (!outputDirectory.isDirectory()) {
                throw new IOException("Round-video output path is not a directory");
            }
        } else if (!outputDirectory.mkdirs() && !outputDirectory.isDirectory()) {
            throw new IOException("Cannot create round-video output directory");
        }

        File output = File.createTempFile("round_video_segment_", ".mp4", outputDirectory);
        RoundVideoDiagnostics diagnostics = new RoundVideoDiagnostics();
        RoundVideoMp4Writer writer = new RoundVideoMp4Writer(
                output,
                outputSize,
                includeAudio,
                diagnostics,
                ignored -> { }
        );
        try {
            RoundVideoRemuxer.copyRange(source, writer, startMs, endMs, includeAudio);
            writer.finish();
            long durationMs = RoundVideoRemuxer.getDurationMs(output);
            if (durationMs <= 0L || output.length() <= 0L) {
                throw new IOException("Exported round-video segment is empty");
            }
            diagnostics.log("independent segment exported: requested=" + startMs + ".." + endMs
                    + ", durationMs=" + durationMs + ", size=" + output.length());
            return new Result(output, durationMs);
        } catch (Throwable error) {
            RoundVideoRemuxer.erase(output);
            if (error instanceof IOException) throw (IOException) error;
            throw new IOException("Unable to export round-video segment", error);
        }
    }
}
