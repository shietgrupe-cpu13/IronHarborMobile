package org.vera20k;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** Bounded, best-effort capture of Android log entries for this application UID. */
final class AndroidLogcatCapture {
    private static final int MAX_BYTES = 2 * 1024 * 1024;
    private static final int MAX_LINES = 3000;
    private static final long TIMEOUT_SECONDS = 4;

    private AndroidLogcatCapture() {}

    static String capture(int appUid, File cacheDir) {
        File output = null;
        Process process = null;
        try {
            output = File.createTempFile("android-logcat-", ".txt", cacheDir);
            process = new ProcessBuilder(
                    "/system/bin/logcat", "-d", "-v", "threadtime",
                    "--uid=" + appUid, "-t", Integer.toString(MAX_LINES))
                    .redirectErrorStream(true)
                    .redirectOutput(output)
                    .start();
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                process.waitFor(500, TimeUnit.MILLISECONDS);
                return "Android logcat capture timed out after " + TIMEOUT_SECONDS + " seconds.\n";
            }

            byte[] bytes = readBounded(output, MAX_BYTES);
            String result = new String(bytes, StandardCharsets.UTF_8);
            if (output.length() > MAX_BYTES) {
                result += "\n[Capture truncated at " + MAX_BYTES + " bytes.]\n";
            }
            if (process.exitValue() != 0) {
                result += "\n[logcat exited with status " + process.exitValue()
                        + "; output above may contain the reason.]\n";
            }
            if (result.trim().isEmpty()) {
                return "No app-UID logcat entries were available during capture.\n";
            }
            return result;
        } catch (Exception error) {
            return "Android logcat capture unavailable: "
                    + error.getClass().getSimpleName() + ": "
                    + String.valueOf(error.getMessage()) + "\n";
        } finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
            if (output != null) output.delete();
        }
    }

    private static byte[] readBounded(File file, int maxBytes) throws IOException {
        try (FileInputStream input = new FileInputStream(file);
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[16 * 1024];
            int remaining = maxBytes;
            int count;
            while (remaining > 0 && (count = input.read(buffer, 0, Math.min(buffer.length, remaining))) != -1) {
                output.write(buffer, 0, count);
                remaining -= count;
            }
            return output.toByteArray();
        }
    }
}
