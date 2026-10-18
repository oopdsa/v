package com.example.pgck2;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.Player;
import androidx.media3.common.Tracks;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** Local media only: verifies the real Media3 sources and overrides without network dependencies. */
@RunWith(AndroidJUnit4.class)
@OptIn(markerClass = UnstableApi.class)
public class PlayerTracksTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();

    @Test public void dubReplacesOriginalAudioAndSameLanguageCaptionsRemainDistinct() throws Exception {
        Context context = instrumentation.getTargetContext();
        File original = wav(context, "track-test-original.mp4", 8000);
        File dub = wav(context, "track-test-dub.wav", 16000);
        File first = caption(context, "track-test-first.vtt", "First caption");
        File second = caption(context, "track-test-second.vtt", "Second caption");
        Intent intent = new Intent(context, PlayerActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(PlayerActivity.EXTRA_VIDEO_URL, Uri.fromFile(original).toString());
        intent.putExtra(PlayerActivity.EXTRA_VIDEO_TITLE, "Local track test");
        intent.putStringArrayListExtra("extra_sub_names", new ArrayList<>(Arrays.asList("First", "Duplicate", "Second")));
        intent.putStringArrayListExtra("extra_sub_langs", new ArrayList<>(Arrays.asList("vi", "vi", "vi")));
        intent.putStringArrayListExtra("extra_sub_urls", new ArrayList<>(Arrays.asList(
                Uri.fromFile(first).toString(), Uri.fromFile(first).toString(), Uri.fromFile(second).toString())));
        PlayerActivity activity = (PlayerActivity) instrumentation.startActivitySync(intent);
        try {
            waitUntilReady(activity);
            onMain(() -> {
                ExoPlayer player = player(activity);
                player.pause();
                assertEquals(2, trackCount(player, C.TRACK_TYPE_TEXT));
                for (Tracks.Group group : player.getCurrentTracks().getGroups()) {
                    if (group.getType() != C.TRACK_TYPE_TEXT) continue;
                    for (int i = 0; i < group.length; i++) {
                        if ("Second".equals(group.getTrackFormat(i).label)) {
                            invoke(activity, "selectEmbeddedSubtitle", new Class<?>[]{Tracks.Group.class,
                                    int.class, String.class, String.class}, group, i, "vi", "Second");
                        }
                    }
                }
            });
            waitForCaption(activity, "Second");
            onMain(() -> invoke(activity, "switchToAlternateAudioUrl",
                    new Class<?>[]{String.class, String.class}, Uri.fromFile(dub).toString(), "Dub"));
            waitUntilReady(activity);
            waitForCaption(activity, "Second");
            onMain(() -> {
                ExoPlayer player = player(activity);
                assertFalse("Switching dub must preserve pause", player.getPlayWhenReady());
                assertEquals(1, trackCount(player, C.TRACK_TYPE_AUDIO));
                assertEquals(16000, player.getAudioFormat().sampleRate);
                assertEquals(2, trackCount(player, C.TRACK_TYPE_TEXT));
                assertTrue("Second caption must survive rebuilding sources", selectedCaption(player, "Second"));
                invoke(activity, "switchToOriginalAudio", new Class<?>[]{});
            });
            waitUntilReady(activity);
            waitForCaption(activity, "Second");
            onMain(() -> {
                assertEquals(8000, player(activity).getAudioFormat().sampleRate);
                assertTrue(selectedCaption(player(activity), "Second"));
            });
        } finally {
            onMain(activity::finish);
            original.delete(); dub.delete(); first.delete(); second.delete();
        }
    }

    private boolean selectedCaption(ExoPlayer player, String label) {
        for (Tracks.Group group : player.getCurrentTracks().getGroups()) {
            if (group.getType() != C.TRACK_TYPE_TEXT) continue;
            for (int i = 0; i < group.length; i++) {
                if (group.isTrackSelected(i) && label.equals(group.getTrackFormat(i).label)) return true;
            }
        }
        return false;
    }

    private void waitForCaption(PlayerActivity activity, String label) throws Exception {
        long deadline = android.os.SystemClock.uptimeMillis() + 3000;
        AtomicReference<Boolean> selected = new AtomicReference<>(false);
        while (android.os.SystemClock.uptimeMillis() < deadline) {
            onMain(() -> selected.set(selectedCaption(player(activity), label)));
            if (selected.get()) return;
            Thread.sleep(50);
        }
        AtomicReference<String> details = new AtomicReference<>("");
        onMain(() -> {
            StringBuilder message = new StringBuilder();
            for (Tracks.Group group : player(activity).getCurrentTracks().getGroups()) {
                if (group.getType() != C.TRACK_TYPE_TEXT) continue;
                for (int i = 0; i < group.length; i++) message.append(group.getTrackFormat(i).id)
                        .append(" / ").append(group.getTrackFormat(i).label).append(" selected=")
                        .append(group.isTrackSelected(i)).append("; ");
            }
            try {
                Field key = PlayerActivity.class.getDeclaredField("selectedSubtitleKey");
                key.setAccessible(true);
                message.append("requested=").append(key.get(activity));
            } catch (Exception error) { throw new AssertionError(error); }
            details.set(message.toString());
        });
        fail("Caption selection did not settle: " + details.get());
    }

    private int trackCount(ExoPlayer player, int type) {
        int count = 0;
        for (Tracks.Group group : player.getCurrentTracks().getGroups()) if (group.getType() == type) count += group.length;
        return count;
    }

    private ExoPlayer player(PlayerActivity activity) {
        try {
            Field field = PlayerActivity.class.getDeclaredField("player");
            field.setAccessible(true);
            return (ExoPlayer) field.get(activity);
        } catch (Exception error) { throw new AssertionError(error); }
    }

    private void invoke(Object target, String name, Class<?>[] types, Object... arguments) {
        try {
            Method method = target.getClass().getDeclaredMethod(name, types);
            method.setAccessible(true);
            method.invoke(target, arguments);
        } catch (Exception error) { throw new AssertionError(error); }
    }

    private void waitUntilReady(PlayerActivity activity) throws Exception {
        long deadline = android.os.SystemClock.uptimeMillis() + 10000;
        while (android.os.SystemClock.uptimeMillis() < deadline) {
            AtomicReference<Boolean> ready = new AtomicReference<>(false);
            onMain(() -> {
                ExoPlayer player = player(activity);
                assertNull("Playback error", player.getPlayerError());
                ready.set(player.getPlaybackState() == Player.STATE_READY);
            });
            if (ready.get()) return;
            Thread.sleep(50);
        }
        fail("Player did not become ready");
    }

    private void onMain(Runnable action) { instrumentation.runOnMainSync(action); }

    private File caption(Context context, String name, String text) throws Exception {
        File file = new File(context.getCacheDir(), name);
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(("WEBVTT\n\n00:00.000 --> 00:59.000\n" + text + "\n").getBytes(StandardCharsets.UTF_8));
        }
        return file;
    }

    private File wav(Context context, String name, int sampleRate) throws Exception {
        int bytes = sampleRate * 2 * 60;
        ByteBuffer header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        header.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36 + bytes);
        header.put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16).putShort((short) 1);
        header.putShort((short) 1).putInt(sampleRate).putInt(sampleRate * 2).putShort((short) 2).putShort((short) 16);
        header.put("data".getBytes(StandardCharsets.US_ASCII)).putInt(bytes);
        File file = new File(context.getCacheDir(), name);
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(header.array());
            byte[] silence = new byte[4096];
            while (bytes > 0) { int length = Math.min(bytes, silence.length); out.write(silence, 0, length); bytes -= length; }
        }
        return file;
    }
}
