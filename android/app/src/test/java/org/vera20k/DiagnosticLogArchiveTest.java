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
        File logs = new File(files, "logs");
        assertTrue(logs.mkdir());
        byte[] current = new byte[2 * 1024 * 1024 + 37];
        for (int i = 0; i < current.length; i++) current[i] = (byte) (i * 31);
        Files.write(new File(logs, "ra2.log").toPath(), current);
        Files.write(new File(logs, "ra2.log.1").toPath(), "previous-session".getBytes());
        Files.write(new File(files, "except.txt").toPath(), "panic backtrace".getBytes());
        Files.write(new File(files, "config.toml").toPath(), "private config".getBytes());

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
}
