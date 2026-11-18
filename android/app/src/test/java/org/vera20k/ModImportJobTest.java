package org.vera20k;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class ModImportJobTest {
    @Test
    public void publishesTheWholeTreeAndKeepsNestedNames() throws Exception {
        Path files = tempDirectory("ih-mod-publish-");
        ModImportJob job = ModImportJob.begin();
        assertNotNull(job);
        try {
            File result = job.run(files.toFile(), "Sample Mod", staging -> {
                Path nested = staging.toPath().resolve("art/nested");
                Files.createDirectories(nested);
                Files.write(nested.resolve("rules.ini"), "[General]".getBytes(StandardCharsets.UTF_8));
            });
            assertEquals("Sample Mod", result.getName());
            assertTrue(Files.isRegularFile(files.resolve("Mods/Sample Mod/art/nested/rules.ini")));
            assertFalse(hasStagingDirectory(files));
        } finally {
            job.close();
        }
    }

    @Test
    public void collisionDoesNotOverwriteInstalledMod() throws Exception {
        Path files = tempDirectory("ih-mod-collision-");
        Path existing = files.resolve("Mods/Sample Mod/mod.ini");
        Files.createDirectories(existing.getParent());
        Files.write(existing, "keep".getBytes(StandardCharsets.UTF_8));
        ModImportJob job = ModImportJob.begin();
        assertNotNull(job);
        try {
            assertThrows(IOException.class, () -> job.run(files.toFile(), "Sample Mod", staging -> {
                Files.write(staging.toPath().resolve("mod.ini"), "replace".getBytes(StandardCharsets.UTF_8));
            }));
            assertEquals("keep", new String(Files.readAllBytes(existing), StandardCharsets.UTF_8));
            assertFalse(hasStagingDirectory(files));
        } finally {
            job.close();
        }
    }

    @Test
    public void failedOrCancelledImportLeavesNoPublishedPartialFolder() throws Exception {
        Path files = tempDirectory("ih-mod-failure-");
        ModImportJob failed = ModImportJob.begin();
        assertNotNull(failed);
        try {
            assertThrows(IOException.class, () -> failed.run(files.toFile(), "Partial", staging -> {
                Files.write(staging.toPath().resolve("partial.bin"), new byte[] {1, 2, 3});
                throw new IOException("provider failed");
            }));
            assertFalse(Files.exists(files.resolve("Mods/Partial")));
            assertFalse(hasStagingDirectory(files));
        } finally {
            failed.close();
        }

        ModImportJob cancelled = ModImportJob.begin();
        assertNotNull(cancelled);
        try {
            assertTrue(cancelled.cancel());
            assertThrows(ModImportJob.Cancelled.class,
                    () -> cancelled.run(files.toFile(), "Cancelled", staging -> fail("copy must not start")));
            assertFalse(Files.exists(files.resolve("Mods/Cancelled")));
            assertFalse(hasStagingDirectory(files));
        } finally {
            cancelled.close();
        }
    }

    private static boolean hasStagingDirectory(Path files) throws IOException {
        try (var children = Files.list(files)) {
            return children.anyMatch(path -> path.getFileName().toString().startsWith(".mod-import-"));
        }
    }

    private static Path tempDirectory(String prefix) throws IOException {
        Path base = Path.of(System.getProperty("user.dir")).resolve("build/test-tmp");
        Files.createDirectories(base);
        return Files.createTempDirectory(base, prefix);
    }
}
