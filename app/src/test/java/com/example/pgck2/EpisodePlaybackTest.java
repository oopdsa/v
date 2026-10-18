package com.example.pgck2;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

public class EpisodePlaybackTest {
    @Test public void startsAtLowestNumberRatherThanLastAdded() {
        Episode first = new Episode(1, "Start", "https://example.com/one.mp4");
        assertSame(first, EpisodePlayback.firstEpisode(Arrays.asList(
                new Episode(8, "Later", "https://example.com/eight.mp4"), first,
                new Episode(3, "Middle", "https://example.com/three.mp4"))));
    }

    @Test public void missingEpisodesNeverFallBackToTrailer() {
        assertNull(EpisodePlayback.firstEpisode(null));
        assertNull(EpisodePlayback.firstEpisode(Collections.emptyList()));
        assertNull(EpisodePlayback.firstEpisode(Arrays.asList(null, new Episode(1, "Draft", "  "))));
    }

    @Test public void customOrderControlsFirstEpisodeWithoutChangingNumbers() {
        Episode one = new Episode(1, "One", "https://example.com/one.mp4");
        Episode two = new Episode(2, "Two", "https://example.com/two.mp4");
        java.util.List<Episode> episodes = new java.util.ArrayList<>(Arrays.asList(one, two));
        EpisodePlayback.move(episodes, 1, 0);
        assertSame(two, EpisodePlayback.firstEpisode(episodes));
        assertSame(two, EpisodePlayback.ordered(Arrays.asList(one, two)).get(0));
        assertEquals(1, one.getEpisodeNumber());
        assertEquals(2, two.getEpisodeNumber());
        EpisodePlayback.move(episodes, 0, -1);
        assertSame(two, episodes.get(0));


    }
}
