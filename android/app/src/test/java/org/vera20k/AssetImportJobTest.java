package org.vera20k;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class AssetImportJobTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void rejectsTraversalAndSymlinksOutsideTheImportFolder() throws Exception {
        File directory = temporary.newFolder("import");
        Path outside = temporary.newFile("config.toml").toPath();
        assertThrows(IOException.class, () -> AssetImportJob.child(directory, "../config.toml"));
        assertThrows(IOException.class, () -> AssetImportJob.child(directory, "..\\config.toml"));
        assertEquals(new File(directory, "ra2md.mix").getCanonicalFile(),
                AssetImportJob.child(directory, "ra2md.mix"));
        boolean symlinkCreated;
        try {
            Files.createSymbolicLink(directory.toPath().resolve("rules.ini"), outside);
            symlinkCreated = true;
        } catch (IOException | UnsupportedOperationException | SecurityException unavailable) {
            // Windows may require a privilege that the contributor does not have.
            symlinkCreated = false;
        }
        if (symlinkCreated) {
            assertThrows(IOException.class, () -> AssetImportJob.child(directory, "rules.ini"));
        }
    }

    @Test public void failedCopyLeavesTheOldInstallAndRetryHasNoStaleRules() throws Exception {
        File files = temporary.newFolder("files");
        Path oldRules = Files.createDirectory(files.toPath().resolve("old")).resolve("RULESMD.INI");
        writeUtf8(oldRules, "old rules");
        Path config = files.toPath().resolve("config.toml");
        writeUtf8(config, "old config");
        File[] failedDirectory = new File[1];
        try (AssetImportJob job = begin()) {
            assertThrows(IOException.class, () -> job.run(files, directory -> {
                failedDirectory[0] = directory;
                writeUtf8(AssetImportJob.child(directory, "RULESMD.INI").toPath(), "stale");
                throw new IOException("copy failed");
            }, directory -> {
                throw new AssertionError("a failed copy must not publish config");
            }));
            assertFalse(failedDirectory[0].exists());
            assertEquals("old config", readUtf8(config));
            assertEquals("old rules", readUtf8(oldRules));
        }
        try (AssetImportJob retry = begin()) {
            File installed = retry.run(files, directory -> {
                assertFalse(AssetImportJob.child(directory, "RULESMD.INI").exists());
                writeUtf8(AssetImportJob.child(directory, "ra2md.mix").toPath(), "new assets");
                assertEquals("old config", readUtf8(config));
            }, directory -> {
                assertEquals("old config", readUtf8(config));
                return "new config";
            });
            assertNotEquals(failedDirectory[0], installed);
            assertFalse(AssetImportJob.child(installed, "RULESMD.INI").exists());
            assertEquals("new assets", readUtf8(installed.toPath().resolve("ra2md.mix")));
            assertEquals("old config", readUtf8(config));
            assertEquals("new config", readUtf8(files.toPath().resolve("Config/config.toml")));
            assertEquals("old rules", readUtf8(oldRules));
        }
    }

    @Test public void cancellationKeepsAdmissionUntilCleanupAndCloseWithoutPublishing() throws Exception {
        File files = temporary.newFolder("files");
        Path config = files.toPath().resolve("config.toml");
        writeUtf8(config, "old config");
        File[] cancelledDirectory = new File[1];
        try (AssetImportJob job = begin()) {
            assertThrows(AssetImportJob.Cancelled.class, () -> job.run(files, directory -> {
                cancelledDirectory[0] = directory;
                writeUtf8(AssetImportJob.child(directory, "ra2md.mix").toPath(), "partial");
                assertTrue(AssetImportJob.cancelActive());
                assertBusy();
                job.checkCancelled();
            }, directory -> {
                throw new AssertionError("a cancelled copy must not publish config");
            }));
            assertFalse(cancelledDirectory[0].exists());
            assertEquals("old config", readUtf8(config));
            assertBusy();
        }
        try (AssetImportJob retry = begin()) {
            retry.checkCancelled();
        }
    }

    @Test public void directSelectionAtomicallyUpdatesConfigWithoutCopyingGameFiles() throws Exception {
        File files = temporary.newFolder("files");
        Path install = temporary.newFolder("original-game").toPath();
        Files.write(install.resolve("ra2.mix"), new byte[] {1});
        Files.write(install.resolve("ra2md.mix"), new byte[] {2});
        Path legacyConfig = files.toPath().resolve("config.toml");
        writeUtf8(legacyConfig, "[paths]\nra2_dir = \"old\"\n");
        Path config = files.toPath().resolve("Config/config.toml");

        AssetImportJob.publishConfig(files,
                "[paths]\nra2_dir = \"" + install.toString().replace("\\", "\\\\") + "\"\n");

        assertTrue(readUtf8(config).contains(install.toString()));
        assertTrue(readUtf8(legacyConfig).contains("ra2_dir = \"old\""));
        assertTrue(Files.exists(install.resolve("ra2.mix")));
        assertEquals(2, install.toFile().listFiles().length);
        assertEquals(2, files.listFiles().length);
    }

    @Test public void publishesConfigUnderDocumentsCompatibilityDirectoryWhenNoRootConfigExists() throws Exception {
        File dataRoot = temporary.newFolder("data-root");
        File config = new File(dataRoot, "Config/config.toml");

        AssetImportJob.publishConfig(dataRoot, "[paths]\nra2_dir = 'game'\n");

        assertTrue(config.isFile());
        assertFalse(new File(dataRoot, "config.toml").exists());
        assertTrue(readUtf8(config.toPath()).contains("ra2_dir = 'game'"));
    }

    @Test public void dataRootPointerIsAtomicallyCreatedAndCanReturnToPrivateStorage() throws Exception {
        File files = temporary.newFolder("files");
        File shared = temporary.newFolder("shared");
        File pointer = new File(files, "launcher-data-root.txt");

        AssetImportJob.publishDataRoot(files, shared);
        assertEquals(shared.getCanonicalPath(), readUtf8(pointer.toPath()));
        AssetImportJob.clearDataRoot(files);
        assertFalse(pointer.exists());
    }

    private static AssetImportJob begin() {
        AssetImportJob job = AssetImportJob.begin();
        assertNotNull("a completed job must release admission", job);
        return job;
    }

    private static void writeUtf8(Path path, String value) throws IOException {
        Files.write(path, value.getBytes(StandardCharsets.UTF_8));
    }

    private static String readUtf8(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static void assertBusy() {
        AssetImportJob unexpected = AssetImportJob.begin();
        if (unexpected != null) unexpected.close();
        assertNull("an active job must keep admission until close", unexpected);
    }
}
