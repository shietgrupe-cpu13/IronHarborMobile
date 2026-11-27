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
        Files.createDirectories(files.toPath().resolve("Config"));
        writeConfig(files.toPath().resolve("Config/config.toml"), install.getAbsolutePath());

        LauncherInstallation state = LauncherInstallation.Companion.read(files);
        assertTrue(state.getReady());
        assertEquals(install.getAbsolutePath(), state.getConfiguredDirectory().getAbsolutePath());
        assertTrue(state.getMissingArchives().isEmpty());
    }

    @Test public void yuriFolderValidationRequiresBothBaseArchivesAtFolderRoot() throws Exception {
        File install = temporary.newFolder("picked-game");
        Files.write(install.toPath().resolve("RA2.MIX"), new byte[] {1});
        assertEquals(java.util.Collections.singletonList("ra2md.mix"),
                LauncherInstallation.Companion.missingRequiredArchives(install));
        Files.write(install.toPath().resolve("Ra2Md.Mix"), new byte[] {2});
        assertTrue(LauncherInstallation.Companion.missingRequiredArchives(install).isEmpty());
        File nestedOnly = new File(install, "mods/example");
        assertTrue(nestedOnly.mkdirs());
        Files.write(nestedOnly.toPath().resolve("ra2.mix"), new byte[] {3});
        assertEquals(java.util.Collections.singletonList("ra2md.mix"),
                LauncherInstallation.Companion.missingRequiredArchives(nestedOnly));
    }

    @Test public void baseRa2ProfileRequiresOnlyTheBaseGameMasterArchive() throws Exception {
        File install = temporary.newFolder("base-ra2");
        Files.write(install.toPath().resolve("RA2.MIX"), new byte[] {1});
        assertTrue(LauncherInstallation.Companion.missingRequiredArchives(install, "ra2").isEmpty());
        assertTrue(LauncherInstallation.Companion.missingRequiredArchives(install, "yr", true).isEmpty());
        assertEquals(java.util.Collections.singletonList("ra2md.mix"),
                LauncherInstallation.Companion.missingRequiredArchives(install, "yr"));
    }

    @Test public void doesNotReadARa2DirFromAnotherConfigSection() throws Exception {
        File files = temporary.newFolder("app-files");
        File install = temporary.newFolder("retail");
        Files.createDirectories(files.toPath().resolve("Config"));
        Files.write(files.toPath().resolve("Config/config.toml"), (
                "[other]\nra2_dir = \"" + install.getAbsolutePath() + "\"\n")
                .getBytes(StandardCharsets.UTF_8));

        LauncherInstallation state = LauncherInstallation.Companion.read(files);
        assertNull(state.getConfiguredDirectory());
        assertFalse(state.getReady());
    }

    @Test public void readsLegacyRootConfigWithoutMovingOrOverwritingIt() throws Exception {
        File files = temporary.newFolder("legacy-root-config");
        File install = temporary.newFolder("legacy-retail");
        Files.write(install.toPath().resolve("ra2.mix"), new byte[] {1});
        Files.write(install.toPath().resolve("ra2md.mix"), new byte[] {2});
        Path legacy = files.toPath().resolve("config.toml");
        writeConfig(legacy, install.getAbsolutePath());
        byte[] original = Files.readAllBytes(legacy);

        LauncherInstallation state = LauncherInstallation.Companion.read(files);

        assertTrue(state.getReady());
        assertEquals(install.getAbsolutePath(), state.getConfiguredDirectory().getAbsolutePath());
        assertArrayEquals(original, Files.readAllBytes(legacy));
    }

    private static void writeConfig(Path path, String paths) throws Exception {
        Files.write(path, ("[paths]\nra2_dir = \"" + paths + "\"\n")
                .getBytes(StandardCharsets.UTF_8));
    }
}
