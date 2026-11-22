package org.vera20k;

import static org.junit.Assert.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class DiagnosticLogArchiveTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void exportsCompleteCurrentRolledAndPanicLogsWithoutOtherPrivateFiles() throws Exception {
        File files = temporary.newFolder("files");
        File logs = new File(files, "Logs");
        assertTrue(logs.mkdir());
        byte[] current = new byte[2 * 1024 * 1024 + 37];
        for (int i = 0; i < current.length; i++) current[i] = (byte) (i * 31);
        Files.write(new File(logs, "ra2.log").toPath(), current);
        Files.write(new File(logs, "ra2.log.1").toPath(), "previous-session".getBytes());
        Files.write(new File(files, "except.txt").toPath(), "panic backtrace".getBytes());
        File config = new File(files, "Config");
        assertTrue(config.mkdir());
        Files.write(new File(config, "config.toml").toPath(), "private config".getBytes());

        DiagnosticLogArchive.ExportBundle bundle = DiagnosticLogArchive.create(
                files, temporary.newFolder("cache"), "device summary", "app-uid logcat");
        assertEquals(2, bundle.logFileCount);
        assertTrue(bundle.crashReportIncluded);

        try (ZipFile zip = new ZipFile(bundle.file)) {
            assertArrayEquals(current, readEntry(zip, "logs/ra2.log"));
            assertEquals("previous-session", new String(readEntry(zip, "logs/ra2.log.1")));
            assertEquals("panic backtrace", new String(readEntry(zip, "crash/except.txt")));
            assertEquals("device summary", new String(readEntry(zip, "diagnostics.txt")));
            assertEquals("app-uid logcat", new String(readEntry(zip, "android-logcat.txt")));
            assertNotNull(zip.getEntry("contents.txt"));
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                assertFalse("unrelated app-private files must not be exported",
                        entries.nextElement().getName().contains("config.toml"));
            }
        }
    }

    private static byte[] readEntry(ZipFile zip, String name) throws Exception {
        try (InputStream input = zip.getInputStream(zip.getEntry(name))) {
            return input.readAllBytes();
        }
    }

    @Test public void shareResolverAllowsOnlyAnExistingGeneratedZipInTheCacheExportFolder() throws Exception {
        File cache = temporary.newFolder("cache");
        File exports = new File(cache, "diagnostic-exports");
        assertTrue(exports.mkdir());
        File archive = new File(exports, "full-diagnostics-123-456.zip");
        Files.write(archive.toPath(), new byte[] {1, 2, 3});

        assertEquals(archive.getCanonicalFile(), DiagnosticLogArchive.resolveShareableArchive(
                cache, archive.getName()));
        assertThrows(FileNotFoundException.class,
                () -> DiagnosticLogArchive.resolveShareableArchive(cache, "../secret.zip"));
        assertThrows(FileNotFoundException.class,
                () -> DiagnosticLogArchive.resolveShareableArchive(cache, "full-diagnostics-1-2.zip"));
        assertThrows(FileNotFoundException.class,
                () -> DiagnosticLogArchive.resolveShareableArchive(cache, "full-diagnostics-1-2.txt"));
    }

    @Test public void repeatedExportsIgnorePreviousArchivesAndUnrecognizedFiles() throws Exception {
        File files = temporary.newFolder("repeat-files");
        File logs = new File(files, "Logs");
        assertTrue(logs.mkdir());
        StringBuilder engineText = new StringBuilder(2_816_076);
        while (engineText.length() < 2_816_076) engineText.append("[INFO] game initialization completed\n");
        Files.write(new File(logs, "ra2.log").toPath(), engineText.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Files.write(new File(logs, "ra2.log.1").toPath(), "older engine session\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Files.write(new File(logs, "launcher-preflight-latest.txt").toPath(), "mod load 41 ms\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Files.write(new File(logs, "config.toml").toPath(), "private config".getBytes());
        Files.write(new File(logs, "unknown.txt").toPath(), "not a recognized diagnostic".getBytes());
        Files.write(new File(logs, "export.zip").toPath(), new byte[1024]);
        Files.write(new File(logs, "export.zip.partial").toPath(), new byte[1024]);
        Files.write(new File(logs, "full-diagnostics-1-2.txt").toPath(), new byte[1024]);
        File nested = new File(logs, "old");
        assertTrue(nested.mkdir());
        Files.write(new File(nested, "old-diagnostic.zip").toPath(), new byte[1024]);

        File cache = temporary.newFolder("repeat-cache");
        String summary = "device summary";
        String logcat = "Vulkan initialization completed";
        String startup = "reason=ANR\nFATAL EXCEPTION\nnative abort stack";
        DiagnosticLogArchive.ExportBundle first = DiagnosticLogArchive.create(
                files, cache, summary, logcat, startup);
        File priorExportInLogs = new File(logs, first.file.getName());
        Files.copy(first.file.toPath(), priorExportInLogs.toPath());

        DiagnosticLogArchive.ExportBundle second = DiagnosticLogArchive.create(
                files, cache, summary, logcat, startup);
        assertEquals(first.file.length(), second.file.length());
        assertTrue("existing user-accessible export must not be deleted", priorExportInLogs.isFile());
        assertTrue(second.file.length() <= DiagnosticLogArchive.MAX_ARCHIVE_BYTES);
        try (ZipFile zip = new ZipFile(second.file)) {
            assertNotNull(zip.getEntry("logs/ra2.log"));
            assertNotNull(zip.getEntry("logs/ra2.log.1"));
            assertNotNull(zip.getEntry("logs/launcher-preflight-latest.txt"));
            assertNotNull(zip.getEntry("startup-diagnostics.txt"));
            assertNull(zip.getEntry("logs/export.zip"));
            assertNull(zip.getEntry("logs/export.zip.partial"));
            assertNull(zip.getEntry("logs/full-diagnostics-1-2.txt"));
            assertNull(zip.getEntry("logs/unknown.txt"));
            assertNull(zip.getEntry("logs/config.toml"));
            assertNull(zip.getEntry("logs/" + first.file.getName()));
            assertNull(zip.getEntry("logs/old/old-diagnostic.zip"));
            assertTrue(new String(readEntry(zip, "startup-diagnostics.txt"))
                    .contains("native abort stack"));
        }
    }

    @Test public void oversizedEngineLogKeepsTheLatestFatalDetails() throws Exception {
        File files = temporary.newFolder("bounded-files");
        File logs = new File(files, "Logs");
        assertTrue(logs.mkdir());
        File engineLog = new File(logs, "ra2.log");
        byte[] oldPrefix = new byte[10 * 1024 * 1024 + 256];
        java.util.Arrays.fill(oldPrefix, (byte) 'x');
        Files.write(engineLog.toPath(), oldPrefix);
        Files.write(engineLog.toPath(), "\nFATAL EXCEPTION: Vulkan device lost\n".getBytes(java.nio.charset.StandardCharsets.UTF_8), java.nio.file.StandardOpenOption.APPEND);

        DiagnosticLogArchive.ExportBundle bundle = DiagnosticLogArchive.create(
                files, temporary.newFolder("bounded-cache"), "summary", "logcat");
        try (ZipFile zip = new ZipFile(bundle.file)) {
            String contents = new String(readEntry(zip, "logs/ra2.log"));
            assertTrue(contents.contains("FATAL EXCEPTION: Vulkan device lost"));
            assertTrue(contents.startsWith("[Earlier bytes omitted;"));
            assertTrue(readEntry(zip, "logs/ra2.log").length <= 10 * 1024 * 1024 + 256);
        }
        assertTrue(bundle.file.length() <= DiagnosticLogArchive.MAX_ARCHIVE_BYTES);
    }
}
