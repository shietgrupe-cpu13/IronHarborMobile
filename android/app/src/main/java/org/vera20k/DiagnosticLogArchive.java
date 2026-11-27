package org.vera20k;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FilterOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Builds a shareable archive from the user-managed engine logs. */
final class DiagnosticLogArchive {
    private static final String EXPORT_DIRECTORY = "diagnostic-exports";
    static final long MAX_ARCHIVE_BYTES = 32L * 1024 * 1024;
    private static final long MAX_ENGINE_LOG_BYTES = 10L * 1024 * 1024;
    private static final long MAX_CRASH_REPORT_BYTES = 1024L * 1024;
    private static final long MAX_STARTUP_REPORT_BYTES = 2L * 1024 * 1024;
    private static final long MAX_ANDROID_LOGCAT_BYTES = 2L * 1024 * 1024;
    private static final long MAX_DIAGNOSTIC_SUMMARY_BYTES = 512L * 1024;
    private static final long MAX_PREFLIGHT_BYTES = 256L * 1024;

    private DiagnosticLogArchive() {}

    static ExportBundle create(File filesDir, File cacheDir, String report) throws IOException {
        return create(filesDir, cacheDir, report, "Android logcat was not captured.");
    }

    static ExportBundle create(File filesDir, File cacheDir, String report, String androidLogcat)
            throws IOException {
        return create(filesDir, cacheDir, report, androidLogcat, "");
    }

    static ExportBundle create(
            File filesDir,
            File cacheDir,
            String report,
            String androidLogcat,
            String startupDiagnostics) throws IOException {
        File exportDirectory = new File(cacheDir, EXPORT_DIRECTORY).getCanonicalFile();
        if (!exportDirectory.isDirectory() && !exportDirectory.mkdirs()) {
            throw new IOException("Could not create diagnostic export directory");
        }

        List<File> children = regularDirectChildren(new File(filesDir, "Logs"));
        List<File> logs = new ArrayList<>();
        children.stream().filter(file -> isCampaignEngineLog(file.getName()))
                .sorted(Comparator.comparingInt(DiagnosticLogArchive::engineLogOrder))
                .limit(2).forEach(logs::add);
        children.stream().filter(file -> file.getName().equals("launcher-preflight-latest.txt"))
                .forEach(logs::add);
        List<File> crashReports = new ArrayList<>();
        children.stream().filter(DiagnosticLogArchive::isCrashReport)
                .limit(2).forEach(crashReports::add);
        File panicReport = regularFile(new File(filesDir, "except.txt"));
        String fileName = String.format(
                Locale.ROOT,
                "full-diagnostics-%d-%s.zip",
                System.currentTimeMillis(),
                Long.toUnsignedString(System.nanoTime()));
        File archive = new File(exportDirectory, fileName);
        File partial = new File(exportDirectory, fileName + ".partial");

        try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(
                new SizeLimitedOutputStream(new FileOutputStream(partial), MAX_ARCHIVE_BYTES)))) {
            putText(zip, "diagnostics.txt", report, MAX_DIAGNOSTIC_SUMMARY_BYTES);
            putText(zip, "android-logcat.txt", androidLogcat, MAX_ANDROID_LOGCAT_BYTES);
            if (hasText(startupDiagnostics)) {
                putText(zip, "startup-diagnostics.txt", startupDiagnostics, MAX_STARTUP_REPORT_BYTES);
            }
            putText(zip, "contents.txt",
                    contentsReport(logs, crashReports, panicReport, startupDiagnostics),
                    MAX_DIAGNOSTIC_SUMMARY_BYTES);
            for (File log : logs) {
                long limit = log.getName().equals("launcher-preflight-latest.txt")
                        ? MAX_PREFLIGHT_BYTES : MAX_ENGINE_LOG_BYTES;
                putBoundedFile(zip, "logs/" + log.getName(), log, limit);
            }
            if (panicReport != null) {
                putBoundedFile(zip, "crash/except.txt", panicReport, MAX_CRASH_REPORT_BYTES);
            }
            for (File crashReport : crashReports) {
                putBoundedFile(zip, "crash/" + crashReport.getName(), crashReport, MAX_CRASH_REPORT_BYTES);
            }
        } catch (IOException error) {
            // Do not leave a partial archive that could be shared as complete.
            partial.delete();
            throw error;
        }

        if (!partial.renameTo(archive)) {
            partial.delete();
            throw new IOException("Could not finalize diagnostic ZIP");
        }
        return new ExportBundle(archive, logs.size(), panicReport != null);
    }

    static File resolveShareableArchive(File cacheDir, String name) throws FileNotFoundException {
        if (name == null || !name.matches("full-diagnostics-[0-9]+-[0-9]+\\.zip")) {
            throw new FileNotFoundException("Invalid diagnostic archive name");
        }
        try {
            File exportDirectory = new File(cacheDir, EXPORT_DIRECTORY).getCanonicalFile();
            File archive = new File(exportDirectory, name).getCanonicalFile();
            if (!exportDirectory.equals(archive.getParentFile())
                    || !archive.isFile()
                    || Files.isSymbolicLink(archive.toPath())) {
                throw new FileNotFoundException("Diagnostic archive is not available");
            }
            return archive;
        } catch (IOException error) {
            FileNotFoundException missing = new FileNotFoundException("Diagnostic archive is not available");
            missing.initCause(error);
            throw missing;
        }
    }

    private static List<File> regularDirectChildren(File directory) throws IOException {
        List<File> result = new ArrayList<>();
        File canonicalDirectory = directory.getCanonicalFile();
        File[] children = directory.listFiles();
        if (children != null) {
            for (File child : children) {
                File canonicalChild = child.getCanonicalFile();
                if (child.isFile()
                        && !Files.isSymbolicLink(child.toPath())
                        && canonicalDirectory.equals(canonicalChild.getParentFile())
                        && !isArchiveOrPartial(child.getName())) {
                    result.add(child);
                }
            }
        }
        result.sort(Comparator.comparing(File::getName));
        return result;
    }

    private static boolean isArchiveOrPartial(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".zip") || lower.endsWith(".partial")
                || lower.contains("full-diagnostics-");
    }

    private static boolean isCampaignEngineLog(String name) {
        if (isArchiveOrPartial(name)) return false;
        if (name.equals("ra2.log")) return true;
        String prefix = "ra2.log.";
        if (!name.startsWith(prefix)) return false;
        String rotation = name.substring(prefix.length());
        return !rotation.isEmpty() && rotation.chars().allMatch(Character::isDigit);
    }

    private static int engineLogOrder(File file) {
        String name = file.getName();
        if (name.equals("ra2.log")) return 0;
        if (name.startsWith("ra2.log.")) {
            try {
                return Integer.parseInt(name.substring("ra2.log.".length()));
            } catch (NumberFormatException ignored) {
                return Integer.MAX_VALUE - 1;
            }
        }
        return Integer.MAX_VALUE;
    }

    private static boolean isCrashReport(File file) {
        String name = file.getName().toLowerCase(Locale.ROOT);
        if (isArchiveOrPartial(name)) return false;
        return name.equals("crash-report.txt") || name.equals("anr.txt")
                || name.equals("tombstone.txt") || name.matches("hs_err_pid[0-9]+\\.log");
    }

    private static File regularFile(File file) throws IOException {
        if (!file.isFile() || Files.isSymbolicLink(file.toPath())) {
            return null;
        }
        File canonical = file.getCanonicalFile();
        return file.getParentFile().getCanonicalFile().equals(canonical.getParentFile())
                ? file : null;
    }

    private static String contentsReport(
            List<File> logs, List<File> crashReports, File panicReport, String startupDiagnostics) {
        StringBuilder contents = new StringBuilder(
                "Included recognized diagnostic files and bounded best-effort Android logs:\n"
                        + "android-logcat.txt (filtered to this app UID; capture may be unavailable)\n");
        if (hasText(startupDiagnostics)) {
            contents.append("startup-diagnostics.txt (recent app exits and available crash/ANR traces)\n");
        }
        if (logs.isEmpty() && crashReports.isEmpty() && panicReport == null) {
            contents.append("No saved engine logs or panic report were present.\n");
        }
        for (File log : logs) {
            appendBoundedFile(contents, "logs/" + log.getName(), log,
                    log.getName().equals("launcher-preflight-latest.txt")
                            ? MAX_PREFLIGHT_BYTES : MAX_ENGINE_LOG_BYTES);
        }
        if (panicReport != null) {
            appendBoundedFile(contents, "crash/except.txt", panicReport, MAX_CRASH_REPORT_BYTES);
        }
        for (File crashReport : crashReports) {
            appendBoundedFile(contents, "crash/" + crashReport.getName(), crashReport,
                    MAX_CRASH_REPORT_BYTES);
        }
        return contents.toString();
    }

    private static void appendBoundedFile(StringBuilder report, String name, File file, long maxBytes) {
        report.append(name).append(" ( ").append(Math.min(file.length(), maxBytes)).append(" of ")
                .append(file.length()).append(" bytes");
        if (file.length() > maxBytes) report.append("; older bytes omitted, tail preserved");
        report.append(" )\n");
    }

    private static boolean hasText(String text) {
        return text != null && !text.trim().isEmpty();
    }

    private static void putText(ZipOutputStream zip, String name, String text, long maxBytes)
            throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > maxBytes) {
            zip.write(bytes, 0, (int) maxBytes);
            zip.write(("\n[Remaining text omitted at the " + maxBytes + " byte safety limit.]\n")
                    .getBytes(StandardCharsets.UTF_8));
        } else {
            zip.write(bytes);
        }
        zip.closeEntry();
    }

    private static void putBoundedFile(ZipOutputStream zip, String name, File file, long maxBytes)
            throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        try (BufferedInputStream input = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buffer = new byte[32 * 1024];
            long omitted = Math.max(0, file.length() - maxBytes);
            if (omitted > 0) {
                zip.write(("[Earlier bytes omitted; final " + maxBytes + " of " + file.length()
                        + " bytes follow.]\n").getBytes(StandardCharsets.UTF_8));
                skipFully(input, omitted);
            }
            long remaining = Math.min(file.length(), maxBytes);
            int count;
            while (remaining > 0 && (count = input.read(buffer, 0, (int) Math.min(buffer.length, remaining))) != -1) {
                zip.write(buffer, 0, count);
                remaining -= count;
            }
        }
        zip.closeEntry();
    }

    private static void skipFully(InputStream input, long bytes) throws IOException {
        long remaining = bytes;
        while (remaining > 0) {
            long skipped = input.skip(remaining);
            if (skipped > 0) {
                remaining -= skipped;
            } else if (input.read() == -1) {
                throw new IOException("Unexpected end of diagnostic file while seeking its tail");
            } else {
                remaining--;
            }
        }
    }

    private static final class SizeLimitedOutputStream extends FilterOutputStream {
        private final long limit;
        private long written;

        SizeLimitedOutputStream(OutputStream output, long limit) {
            super(output);
            this.limit = limit;
        }

        @Override
        public void write(int value) throws IOException {
            requireCapacity(1);
            out.write(value);
            written++;
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            requireCapacity(length);
            out.write(bytes, offset, length);
            written += length;
        }

        private void requireCapacity(int length) throws IOException {
            if (length < 0 || written + length > limit) {
                throw new IOException("Diagnostic archive exceeds the " + limit + " byte safety limit");
            }
        }
    }

    static final class ExportBundle {
        final File file;
        final int logFileCount;
        final boolean crashReportIncluded;

        ExportBundle(File file, int logFileCount, boolean crashReportIncluded) {
            this.file = file;
            this.logFileCount = logFileCount;
            this.crashReportIncluded = crashReportIncluded;
        }
    }
}
