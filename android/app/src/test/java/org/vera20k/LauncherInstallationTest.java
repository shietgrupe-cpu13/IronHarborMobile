package org.vera20k;

import static org.junit.Assert.*;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class LauncherInstallationTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void reportsMissingConfigAndRequiredArchives() throws Exception {
        File files = temporary.newFolder("app-files");
        LauncherInstallation missingConfig = LauncherInstallation.Companion.read(files);
        assertFalse(missingConfig.getReady());
        assertNull(missingConfig.getConfiguredDirectory());
        assertEquals(2, missingConfig.getMissingArchives().size());
    }

    @Test public void recognizesConfiguredArchivesWithoutCaseSensitivity() throws Exception {
        File files = temporary.newFolder("app-files");
        File install = temporary.newFolder("retail");
        Files.write(install.toPath().resolve("RA2.MIX"), new byte[] {1});
        Files.write(install.toPath().resolve("Ra2Md.Mix"), new byte[] {2});
        writeConfig(files.toPath().resolve("config.toml"), install.getAbsolutePath());

        LauncherInstallation state = LauncherInstallation.Companion.read(files);
        assertTrue(state.getReady());
        assertEquals(install.getAbsolutePath(), state.getConfiguredDirectory().getAbsolutePath());
        assertTrue(state.getMissingArchives().isEmpty());
    }

    @Test public void doesNotReadARa2DirFromAnotherConfigSection() throws Exception {
        File files = temporary.newFolder("app-files");
        File install = temporary.newFolder("retail");
        Files.write(files.toPath().resolve("config.toml"), (
                "[other]\nra2_dir = \"" + install.getAbsolutePath() + "\"\n")
                .getBytes(StandardCharsets.UTF_8));

        LauncherInstallation state = LauncherInstallation.Companion.read(files);
        assertNull(state.getConfiguredDirectory());
        assertFalse(state.getReady());
    }

    private static void writeConfig(Path path, String paths) throws Exception {
        Files.write(path, ("[paths]\nra2_dir = \"" + paths + "\"\n")
                .getBytes(StandardCharsets.UTF_8));
    }
}
