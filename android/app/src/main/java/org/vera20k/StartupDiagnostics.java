package org.vera20k;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Application;
import android.app.ApplicationExitInfo;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Process;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Writes one bounded, app-UID-only diagnostic snapshot to Downloads and overwrites it on later captures. */
final class StartupDiagnostics {
    private static final String TAG = "IronHarborDiagnostics";
    private static final String FILE_NAME = "IronHarborMobile-startup-diagnostics.txt";
    private static final String RELATIVE_PATH = Environment.DIRECTORY_DOWNLOADS + "/";
    private static final int MAX_TRACE_BYTES = 256 * 1024;
    private static final ScheduledExecutorService WORKER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "iron-harbor-startup-diagnostics");
        thread.setDaemon(true);
        return thread;
    });
    private static final AtomicReference<String> LATEST_REPORT = new AtomicReference<>("");
    private static volatile boolean handlerInstalled;

    private StartupDiagnostics() {}

    static synchronized void install(Application application) {
        Context context = application.getApplicationContext();
        if (!handlerInstalled) {
            Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
            Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
                try {
                    String report = LATEST_REPORT.get();
                    if (report.isEmpty()) report = baseReport(context, "Uncaught application exception");
                    StringWriter stack = new StringWriter();
                    error.printStackTrace(new PrintWriter(stack));
                    writeReport(context, report + "\n--- UNCAUGHT JAVA EXCEPTION ---\nThread: "
                            + thread.getName() + " (" + thread.getId() + ")\n" + stack);
                } catch (Throwable writeFailure) {
                    Log.e(TAG, "Could not save crash report", writeFailure);
                }
                if (previous != null) previous.uncaughtException(thread, error);
                else {
                    Process.killProcess(Process.myPid());
                    System.exit(10);
                }
            });
            handlerInstalled = true;
        }
        if (application.getPackageName().equals(processName(context))) {
            capture(context, "Application launch", 2, TimeUnit.SECONDS);
        }
    }

    static void captureAfterGame(Context context) {
        capture(context.getApplicationContext(), "Returned from game", 0, TimeUnit.SECONDS);
    }

    static boolean shareLatest(Activity activity) {
        Uri uri = findLatestUri(activity.getContentResolver());
        if (uri == null) {
            Toast.makeText(activity, "No startup log is available yet. Reopen the launcher and try again.", Toast.LENGTH_LONG).show();
            return false;
        }
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT, "Iron Harbor Mobile startup diagnostics");
        send.putExtra(Intent.EXTRA_TEXT, "Latest automatic startup log: Downloads/" + FILE_NAME);
        send.putExtra(Intent.EXTRA_STREAM, uri);
        send.setClipData(android.content.ClipData.newUri(activity.getContentResolver(), FILE_NAME, uri));
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        activity.startActivity(Intent.createChooser(send, "Share startup diagnostics"));
        return true;
    }

    static String locationDescription(Context context) {
        if (Build.VERSION.SDK_INT >= 29) return "Downloads/" + FILE_NAME;
        File directory = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        return (directory == null ? new File(context.getFilesDir(), "Downloads") : directory)
                .getAbsolutePath() + "/" + FILE_NAME;
    }

    private static void capture(Context context, String reason, long delay, TimeUnit unit) {
        WORKER.schedule(() -> {
            try {
                String report = baseReport(context, reason)
                        + "\n--- APP LOGCAT (UID FILTERED, BEST EFFORT) ---\n"
                        + AndroidLogcatCapture.capture(Process.myUid(), context.getCacheDir());
                LATEST_REPORT.set(report);
                writeReport(context, report);
            } catch (Throwable error) {
                Log.e(TAG, "Automatic diagnostic capture failed", error);
            }
        }, delay, unit);
    }

    private static String baseReport(Context context, String reason) {
        StringBuilder report = new StringBuilder(4096);
        PackageInfo info;
        try {
            info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
        } catch (Exception error) {
            info = null;
        }
        report.append("Iron Harbor Mobile automatic diagnostics\n")
                .append("Generated: ").append(System.currentTimeMillis()).append(" (UTC epoch ms)\n")
                .append("Capture reason: ").append(reason).append('\n')
                .append("Package: ").append(context.getPackageName()).append('\n')
                .append("Version: ").append(info == null ? "unknown" : info.versionName)
                .append(" (code ").append(info == null ? -1 : (Build.VERSION.SDK_INT >= 28
                        ? info.getLongVersionCode() : info.versionCode)).append(")\n")
                .append("Android: ").append(Build.VERSION.RELEASE).append(" / API ").append(Build.VERSION.SDK_INT).append('\n')
                .append("Device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n')
                .append("ABI: ").append(String.join(", ", Build.SUPPORTED_ABIS)).append('\n')
                .append("Process: ").append(processName(context)).append(" / pid ").append(Process.myPid()).append('\n')
                .append("App UID: ").append(Process.myUid()).append('\n')
                .append("This file is overwritten on the next automatic capture. Logs are limited to this app UID.\n");
        appendPreviousExits(context, report);
        return report.toString();
    }

    private static void appendPreviousExits(Context context, StringBuilder report) {
        if (Build.VERSION.SDK_INT < 30) {
            report.append("Previous process exit details: unavailable before Android 11\n");
            return;
        }
        try {
            ActivityManager manager = context.getSystemService(ActivityManager.class);
            List<ApplicationExitInfo> exits = manager == null ? null
                    : manager.getHistoricalProcessExitReasons(context.getPackageName(), 0, 5);
            report.append("\n--- RECENT PROCESS EXIT DETAILS ---\n");
            if (exits == null || exits.isEmpty()) {
                report.append("No prior process exit records returned by Android.\n");
                return;
            }
            for (ApplicationExitInfo exit : exits) {
                report.append("time=").append(exit.getTimestamp())
                        .append(" process=").append(exit.getProcessName())
                        .append(" pid=").append(exit.getPid())
                        .append(" reason=").append(exitReason(exit.getReason()))
                        .append(" status=").append(exit.getStatus())
                        .append(" description=").append(exit.getDescription()).append('\n');
                try (InputStream trace = exit.getTraceInputStream()) {
                    if (trace != null) {
                        byte[] bytes = readBounded(trace, MAX_TRACE_BYTES);
                        if (bytes.length != 0) {
                            report.append("trace_base64=")
                                    .append(java.util.Base64.getEncoder().encodeToString(bytes)).append('\n');
                        }
                    }
                } catch (Exception error) {
                    report.append("trace unavailable: ").append(error.getClass().getSimpleName()).append('\n');
                }
            }
        } catch (Exception error) {
            report.append("Previous process exit details unavailable: ")
                    .append(error.getClass().getSimpleName()).append(": ").append(error.getMessage()).append('\n');
        }
    }

    private static String exitReason(int reason) {
        switch (reason) {
            case ApplicationExitInfo.REASON_CRASH: return "JAVA_CRASH";
            case ApplicationExitInfo.REASON_CRASH_NATIVE: return "NATIVE_CRASH";
            case ApplicationExitInfo.REASON_ANR: return "ANR";
            case ApplicationExitInfo.REASON_LOW_MEMORY: return "LOW_MEMORY";
            case ApplicationExitInfo.REASON_SIGNALED: return "SIGNAL";
            case ApplicationExitInfo.REASON_INITIALIZATION_FAILURE: return "INITIALIZATION_FAILURE";
            case ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE: return "EXCESSIVE_RESOURCE_USAGE";
            case ApplicationExitInfo.REASON_USER_REQUESTED: return "USER_REQUESTED";
            case ApplicationExitInfo.REASON_USER_STOPPED: return "USER_STOPPED";
            case ApplicationExitInfo.REASON_DEPENDENCY_DIED: return "DEPENDENCY_DIED";
            case ApplicationExitInfo.REASON_EXIT_SELF: return "EXIT_SELF";
            case ApplicationExitInfo.REASON_OTHER: return "OTHER";
            default: return "UNKNOWN(" + reason + ")";
        }
    }

    private static synchronized void writeReport(Context context, String report) throws Exception {
        File lockPath = new File(context.getFilesDir(), "startup-diagnostics.lock");
        try (FileOutputStream lockOutput = new FileOutputStream(lockPath, true);
                FileChannel channel = lockOutput.getChannel();
                FileLock ignored = channel.lock()) {
            if (Build.VERSION.SDK_INT >= 29) writeMediaStore(context, report);
            else writeAppSpecificFallback(context, report);
        }
    }

    private static void writeMediaStore(Context context, String report) throws Exception {
        ContentResolver resolver = context.getContentResolver();
        Uri collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        Uri uri = findLatestUri(resolver);
        boolean inserted = false;
        if (uri == null) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, FILE_NAME);
            values.put(MediaStore.Downloads.MIME_TYPE, "text/plain");
            values.put(MediaStore.Downloads.RELATIVE_PATH, RELATIVE_PATH);
            values.put(MediaStore.Downloads.IS_PENDING, 1);
            uri = resolver.insert(collection, values);
            if (uri == null) throw new IllegalStateException("MediaStore did not create the Downloads log");
            inserted = true;
        }
        try {
            try (OutputStream output = resolver.openOutputStream(uri, inserted ? "w" : "wt")) {
                if (output == null) throw new IllegalStateException("Could not open the Downloads log");
                output.write(report.getBytes(StandardCharsets.UTF_8));
                output.flush();
            }
            if (inserted) {
                ContentValues ready = new ContentValues();
                ready.put(MediaStore.Downloads.IS_PENDING, 0);
                resolver.update(uri, ready, null, null);
            }
        } catch (Exception error) {
            if (inserted) resolver.delete(uri, null, null);
            throw error;
        }
    }

    private static void writeAppSpecificFallback(Context context, String report) throws Exception {
        File directory = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (directory == null) directory = new File(context.getFilesDir(), "Downloads");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IllegalStateException("Cannot create diagnostic folder");
        try (FileOutputStream output = new FileOutputStream(new File(directory, FILE_NAME), false)) {
            output.write(report.getBytes(StandardCharsets.UTF_8));
            output.getFD().sync();
        }
    }

    private static Uri findLatestUri(ContentResolver resolver) {
        if (Build.VERSION.SDK_INT < 29) return null;
        Uri collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        String[] columns = { MediaStore.Downloads._ID };
        String selection = MediaStore.Downloads.DISPLAY_NAME + "=? AND "
                + MediaStore.Downloads.RELATIVE_PATH + "=?";
        try (Cursor cursor = resolver.query(collection, columns, selection,
                new String[] { FILE_NAME, RELATIVE_PATH }, MediaStore.Downloads.DATE_MODIFIED + " DESC")) {
            if (cursor == null || !cursor.moveToFirst()) return null;
            return Uri.withAppendedPath(collection, Long.toString(cursor.getLong(0)));
        } catch (Exception error) {
            Log.w(TAG, "Could not find the previous Downloads log", error);
            return null;
        }
    }

    private static byte[] readBounded(InputStream input, int maxBytes) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while (output.size() < maxBytes
                && (count = input.read(buffer, 0, Math.min(buffer.length, maxBytes - output.size()))) != -1) {
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private static String processName(Context context) {
        if (Build.VERSION.SDK_INT >= 28) return Application.getProcessName();
        try (FileInputStream input = new FileInputStream("/proc/self/cmdline")) {
            byte[] bytes = new byte[256];
            int count = input.read(bytes);
            int end = 0;
            while (end < count && bytes[end] != 0) end++;
            return new String(bytes, 0, end, StandardCharsets.UTF_8);
        } catch (Exception error) {
            return context.getPackageName();
        }
    }
}
