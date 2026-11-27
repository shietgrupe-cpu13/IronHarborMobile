package org.vera20k;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class LauncherCampaignProgressTest {
    @Test
    public void parsesCompletedCampaignMissionsAndFinishedState() {
        var progress = LauncherCampaignProgress.parse(
            "version=1\nra2.all1.completed=3\nra2.all1.finished=0\nyr.sov1.completed=7\nyr.sov1.finished=1\n"
        );
        assertEquals(3, progress.get("ra2.all1").getCompletedMissions());
        assertFalse(progress.get("ra2.all1").getFinished());
        assertEquals("3/7 missions completed", LauncherCampaignProgress.label(progress.get("ra2.all1")));
        assertTrue(progress.get("yr.sov1").getFinished());
        assertEquals("Campaign complete · 7/7 missions", LauncherCampaignProgress.label(progress.get("yr.sov1")));
    }

    @Test
    public void ignoresMalformedAndClampsProgress() {
        var progress = LauncherCampaignProgress.parse("ra2.sov1.completed=99\nra2.sov1.finished=wat\ninvalid\n");
        assertEquals(7, progress.get("ra2.sov1").getCompletedMissions());
        assertFalse(progress.get("ra2.sov1").getFinished());
        assertEquals("No completed missions recorded", LauncherCampaignProgress.label(null));
    }
}
