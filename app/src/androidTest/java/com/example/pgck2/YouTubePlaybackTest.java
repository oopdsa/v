package com.example.pgck2;

import android.app.Instrumentation;
import android.content.Intent;
import android.webkit.WebView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** Network smoke test: player-ready alone is insufficient; require actual PLAYING state. */
@RunWith(AndroidJUnit4.class)
public class YouTubePlaybackTest {
    @Test public void embeddedVideoActuallyPlays() throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        String url = InstrumentationRegistry.getArguments().getString("youtubeVideoUrl",
                "https://www.youtube.com/watch?v=aqz-KE-bpKQ");
        Intent intent = new Intent(instrumentation.getTargetContext(), PlayerActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(PlayerActivity.EXTRA_VIDEO_URL, url)
                .putExtra(PlayerActivity.EXTRA_VIDEO_TITLE, "YouTube playback check");
        PlayerActivity activity = (PlayerActivity) instrumentation.startActivitySync(intent);
        String lastTitle = "";
        try {
            long deadline = android.os.SystemClock.uptimeMillis() + 30000;
            while (android.os.SystemClock.uptimeMillis() < deadline) {
                AtomicReference<String> title = new AtomicReference<>();
                instrumentation.runOnMainSync(() -> title.set(((WebView)
                        activity.findViewById(R.id.wvYouTubePlayer)).getTitle()));
                lastTitle = title.get();
                if ("yt:state:1".equals(lastTitle)) return;
                assertFalse("YouTube returned " + lastTitle, lastTitle != null && lastTitle.startsWith("yt:error:"));
                Thread.sleep(100);
            }
            fail("YouTube did not start playing; last event: " + lastTitle);
        } finally {
            instrumentation.runOnMainSync(activity::finish);
        }
    }
}
