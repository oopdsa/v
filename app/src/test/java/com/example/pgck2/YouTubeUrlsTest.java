package com.example.pgck2;

import org.junit.Test;
import static org.junit.Assert.*;

public class YouTubeUrlsTest {
    @Test public void recognisesSharedShortLiveAndWatchLinks() {
        String id = "aqz-KE-bpKQ";
        assertEquals(id, YouTubeUrls.videoId("https://youtu.be/" + id + "?si=abc"));
        assertEquals(id, YouTubeUrls.videoId("https://www.youtube.com/watch?feature=share&v=" + id + "&t=30"));
        assertEquals(id, YouTubeUrls.videoId("https://www.youtube.com/shorts/" + id));
        assertEquals(id, YouTubeUrls.videoId("https://m.youtube.com/live/" + id));
        assertEquals(id, YouTubeUrls.videoId("https://www.youtube-nocookie.com/embed/" + id));
    }

    @Test public void rejectsUnrelatedHostsAndMalformedIds() {
        assertNull(YouTubeUrls.videoId("https://example.com/watch?v=aqz-KE-bpKQ"));
        assertNull(YouTubeUrls.videoId("https://youtube.com.evil.test/watch?v=aqz-KE-bpKQ"));
        assertNull(YouTubeUrls.videoId("https://youtube.com/watch?v=too_short"));
        assertNull(YouTubeUrls.videoId(null));
    }
}
