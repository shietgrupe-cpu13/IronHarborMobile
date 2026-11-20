package org.vera20k;

import static org.junit.Assert.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class LauncherModCatalogTest {
    @Test
    public void mentalOmegaFolderNameRequiresMoInsteadOfYurProfiles() {
        assertTrue(LauncherModCatalog.requiresMoProfile("MentalOmega330Mod"));
        assertFalse(LauncherModCatalog.requiresMoProfile("Other Community Mod"));
    }

    @Test
    public void validatedMentalOmegaImportUsesMoProfile() {
        ImportedModSelection selection = ImportedModSelection.afterEngineValidation("MentalOmega330Mod");
        assertEquals("MentalOmega330Mod", selection.getModName());
        assertEquals(LauncherIniProfile.MO, selection.getProfile());
    }

    @Test
    public void validatedOtherModUsesEngineProfileDetection() {
        ImportedModSelection selection = ImportedModSelection.afterEngineValidation("Other Community Mod");
        assertEquals("Other Community Mod", selection.getModName());
        assertNull(selection.getProfile());
    }

    @Test
    public void readsInstalledModsAndPersistedMoChoice() throws Exception {
        Path files = tempDirectory("ih-mod-catalog-");
        Files.createDirectories(files.resolve("Config"));
        Files.createDirectories(files.resolve("Mods/Mental Omega"));
        Files.createDirectories(files.resolve("Mods/VanillaLike"));
        writeUtf8(files.resolve("Config/config.toml"),
                "\uFEFF[paths]\nra2_dir = \"/owned/game\"\nmod = \"Mental Omega\"\n[ini]\nprofile = \"mo\"\n");

        LauncherModCatalog catalog = LauncherModCatalog.read(files.toFile());
        assertNull(catalog.getConfigError());
        assertEquals("Mental Omega", catalog.getSelectedMod());
        assertEquals(LauncherIniProfile.MO, catalog.getProfile());
        assertEquals(2, catalog.getInstalledMods().size());
        assertTrue(catalog.containsSelectedMod());
    }

    @Test
    public void discoversOnlyImmediateModFoldersNotTheirSubfolders() throws Exception {
        Path files = tempDirectory("ih-nested-mod-");
        Path mod = files.resolve("Mods/ExampleFolder Mod1");
        Files.createDirectories(mod.resolve("Nested Content/Another Folder"));
        Files.createFile(mod.resolve("expandmo99.mix"));
        Files.createDirectories(files.resolve("Mods/VanillaLike"));
        LauncherModCatalog catalog = LauncherModCatalog.read(files.toFile());
        assertEquals(java.util.List.of("ExampleFolder Mod1", "VanillaLike"), catalog.getInstalledMods());
    }
    @Test
    public void publisherAndLauncherReaderAgreeAfterAtomicSelection() throws Exception {
        Path files = tempDirectory("ih-mod-publish-");
        Files.createDirectories(files.resolve("Config"));
        writeUtf8(files.resolve("Config/config.toml"),
                "[paths]\nra2_dir = \"/owned/game\"\nmod = \"Old\"\n[ini]\nprofile = \"yr\"\n");
        Files.createDirectories(files.resolve("Mods/New Mod"));

        LauncherConfigPublisher.publish(
                files.toFile(),
                "[paths]\nra2_dir = \"/owned/game\"\nmod = \"New Mod\"\n[ini]\nprofile = \"mo\"\n");

        LauncherModCatalog catalog = LauncherModCatalog.read(files.toFile());
        assertNull(catalog.getConfigError());
        assertEquals("New Mod", catalog.getSelectedMod());
        assertEquals(LauncherIniProfile.MO, catalog.getProfile());
        assertTrue(catalog.containsSelectedMod());
    }

    @Test
    public void reportsMissingSelectedModInsteadOfShowingItAsApplied() throws Exception {
        Path files = tempDirectory("ih-mod-missing-");
        Files.createDirectories(files.resolve("Config"));
        Files.createDirectories(files.resolve("Mods/Other"));
        writeUtf8(files.resolve("Config/config.toml"),
                "[paths]\nra2_dir = \"/owned/game\"\nmod = \"Removed Mod\"\n");

        LauncherModCatalog catalog = LauncherModCatalog.read(files.toFile());
        assertNull(catalog.getConfigError());
        assertFalse(catalog.containsSelectedMod());
    }

    private static Path tempDirectory(String prefix) throws Exception {
        Path base = Path.of(System.getProperty("user.dir")).resolve("build/test-tmp");
        Files.createDirectories(base);
        return Files.createTempDirectory(base, prefix);
    }

    private static void writeUtf8(Path path, String value) throws Exception {
        Files.write(path, value.getBytes(StandardCharsets.UTF_8));
    }
}
