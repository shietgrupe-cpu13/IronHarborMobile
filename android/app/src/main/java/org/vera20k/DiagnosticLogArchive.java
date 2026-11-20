package org.vera20k;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
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

    private DiagnosticLogArchive() {}

    static ExportBundle create(File filesDir, File cacheDir, String report) throws IOException {
        return create(filesDir, cacheDir, report, "Android logcat was not captured.");
    }

    static ExportBundle create(File filesDir, File cacheDir, String report, String androidLogcat)
            throws IOException {
        File exportDirectory = new File(cacheDir, EXPORT_DIRECTORY).getCanonicalFile();
        if (!exportDirectory.isDirectory() && !exportDirectory.mkdirs()) {
            throw new IOException("Could not create diagnostic export directory");
        }

        List<File> logs = regularDirectChildren(new File(filesDir, "Logs"));
        File panicReport = regularFile(new File(filesDir, "except.txt"));
        String fileName = String.format(
                Locale.ROOT,
                "full-diagnostics-%d-%s.zip",
                System.currentTimeMillis(),
                Long.toUnsignedString(System.nanoTime()));
        File archive = new File(exportDirectory, fileName);
        File partial = new File(exportDirectory, fileName + ".partial");

        try (ZipOutputStream zip = new ZipOutputStream(
                new BufferedOutputStream(new FileOutputStream(partial)))) {
            putText(zip, "diagnostics.txt", report);
            putText(zip, "contents.txt", contentsReport(logs, panicReport));
            putText(zip, "android-logcat.txt", androidLogcat);
            for (File log : logs) {
                putFile(zip, "logs/" + log.getName(), log);
            }
            if (panicReport != null) {
                putFile(zip, "crash/except.txt", panicReport);
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
                        && canonicalDirectory.equals(canonicalChild.getParentFile())) {
                    result.add(child);
                }
            }
        }
        result.sort(Comparator.comparing(File::getName));
        return result;
    }

    private static File regularFile(File file) throws IOException {
        if (!file.isFile() || Files.isSymbolicLink(file.toPath())) {
            return null;
        }
        File canonical = file.getCanonicalFile();
        return file.getParentFile().getCanonicalFile().equals(canonical.getParentFile())
                ? file : null;
    }

    private static String contentsReport(List<File> logs, File panicReport) {
        StringBuilder contents = new StringBuilder(
                "Included Documents/RA2Harbor diagnostic files and best-effort Android logs:\n"
                        + "android-logcat.txt (filtered to this app UID; capture may be unavailable)\n");
        if (logs.isEmpty() && panicReport == null) {
            contents.append("No saved engine logs or panic report were present.\n");
        }
        for (File log : logs) {
            contents.append("logs/").append(log.getName()).append(" ( ")
                    .append(log.length()).append(" bytes )\n");
        }
        if (panicReport != null) {
            contents.append("crash/except.txt ( ")
                    .append(panicReport.length()).append(" bytes )\n");
        }
        return contents.toString();
    }

    private static void putText(ZipOutputStream zip, String name, String text) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(text.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static void putFile(ZipOutputStream zip, String name, File file) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        try (BufferedInputStream input = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buffer = new byte[32 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) {
                zip.write(buffer, 0, count);
            }
        }
        zip.closeEntry();
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
