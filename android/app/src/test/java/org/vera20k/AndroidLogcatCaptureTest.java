package org.vera20k;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class AndroidLogcatCaptureTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void boundedCaptureKeepsTheNewestFatalLogcatLines() throws Exception {
        File logcat = temporary.newFile("logcat.txt");
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 40; i++) text.append("I/old: earlier line ").append(i).append('\n');
        text.append("E/AndroidRuntime: FATAL EXCEPTION: main\n")
                .append("E/Vulkan: vkQueueSubmit failed\n");
        Files.write(logcat.toPath(), text.toString().getBytes(StandardCharsets.UTF_8));

        String tail = new String(AndroidLogcatCapture.readTailBounded(logcat, 96), StandardCharsets.UTF_8);
        assertTrue(tail.contains("FATAL EXCEPTION: main"));
        assertTrue(tail.contains("vkQueueSubmit failed"));
        assertFalse(tail.contains("earlier line 0"));
    }
}
