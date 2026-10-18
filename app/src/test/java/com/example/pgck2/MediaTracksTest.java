package com.example.pgck2;

import org.junit.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class MediaTracksTest {
    @Test public void removesDuplicateSourcesButKeepsDifferentTracksInSameLanguage() {
        List<SubtitleOption> subtitles = MediaTracks.uniqueSubtitles(Arrays.asList(
                new SubtitleOption("First", "vi", "https://example.com/a.vtt"),
                new SubtitleOption("Duplicate", "vi", " https://example.com/a.vtt "),
                new SubtitleOption("Second", "vi", "https://example.com/b.vtt"), null));
        assertEquals(2, subtitles.size());
        assertEquals("First", subtitles.get(0).getName());
        assertEquals("Second", subtitles.get(1).getName());
        assertEquals(2, MediaTracks.uniqueAudio(Arrays.asList(
                new AudioTrackOption("A", "en", "https://example.com/a.m4a"),
                new AudioTrackOption("Duplicate", "en", " https://example.com/a.m4a "),
                new AudioTrackOption("B", "en", "https://example.com/b.m4a"))).size());
    }

    @Test public void detectsSignedAndUppercaseSrtPaths() {
        assertTrue(MediaTracks.isSrt("https://example.com/captions.SRT?token=abc#part"));
        assertFalse(MediaTracks.isSrt("https://example.com/captions.vtt?file=a.srt"));
    }

    @Test public void sourceMergingDoesNotChangeCaptionIdentity() {
        String id = "external:https://example.com/captions.vtt";
        assertEquals(id, MediaTracks.stableTrackId("2:" + id));
        assertEquals(id, MediaTracks.stableTrackId("0:2:" + id));
        assertEquals("0", MediaTracks.stableTrackId("1:0"));
    }
}
