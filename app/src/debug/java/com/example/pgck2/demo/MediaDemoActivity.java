package com.example.pgck2.demo;

import android.app.PendingIntent;
import android.app.PictureInPictureParams;
import android.app.RemoteAction;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Rational;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import com.example.pgck2.R;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.ChecksSdkIntAtLeast;
import androidx.annotation.RequiresApi;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.Tracks;
import androidx.media3.common.VideoSize;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo;
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.FilteringMediaSource;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.MergingMediaSource;
import androidx.media3.ui.PlayerView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;

/** Isolated, debug-only recording screen. No Firebase, admin or product navigation. */
@OptIn(markerClass = UnstableApi.class)
public class MediaDemoActivity extends AppCompatActivity {
    // EDIT ONLY THESE FIVE URLS. Use direct media URLs, NOT YouTube watch links.
    // For HD use a multi-variant HLS master playlist. For dub use matching timelines.
    public static final String VIDEO_URL = "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8";
    public static final String SUBTITLE_DEMO_1_URL = "https://res.cloudinary.com/nlkw26dt/raw/upload/v1792307150/33.vtt";
    public static final String SUBTITLE_DEMO_2_URL = "https://res.cloudinary.com/nlkw26dt/raw/upload/v1792151939/first.vtt";
    public static final String DUB_DEMO_1_URL = "https://res.cloudinary.com/nlkw26dt/video/upload/v1792248441/Liminal-Spaces-SnapYT.App.mp3";
    public static final String DUB_DEMO_2_URL = "https://res.cloudinary.com/nlkw26dt/video/upload/v1792306998/YTSave_YouTube_Media_OSyomAwLzYk_CKG-CH%C6%A0I-VALORANT-TO%C3%80N-TH%E1%BA%AENG_009_128k.mp3";

    private static final String ACTION = "com.example.pgck2.demo.PLAY_PAUSE";
    private static final String EXTRA_COMMAND = "demo_command";
    private static final int COMMAND_REWIND = 1;
    private static final int COMMAND_PLAY_PAUSE = 2;
    private static final int COMMAND_FORWARD = 3;
    private ExoPlayer player;
    private PlayerView playerView;
    private LinearLayout root, header, controls, extra;
    private TextView status, description;
    private ImageButton centerPlay, pipButton;
    private SeekBar volumeSlider;
    private boolean fullscreen;
    // 0 = source aspect ratio, 1 = landscape, 2 = portrait.
    private int pipAspectMode;
    private final List<Button> levelButtons = new ArrayList<>();
    private int level = 1, dub = 0, subtitle = -1, quality = 0;
    private long position;
    private boolean play = true;
    private float volume = 1f, speed = 1f, previousVolume = 1f;
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (ACTION.equals(intent.getAction()) && player != null) {
                switch (intent.getIntExtra(EXTRA_COMMAND, 0)) {
                    case COMMAND_REWIND:
                        if (player.isCurrentMediaItemSeekable()) player.seekBack();
                        break;
                    case COMMAND_PLAY_PAUSE:
                        // A buffering player can still have an active play request.
                        if (player.getPlayWhenReady()) player.pause(); else player.play();
                        break;
                    case COMMAND_FORWARD:
                        if (player.isCurrentMediaItemSeekable()) player.seekForward();
                        break;
                    default:
                        return;
                }
                updatePip();
            }
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (getSupportActionBar() != null) getSupportActionBar().hide();
        if (state != null) {
            level = state.getInt("level", 1); dub = state.getInt("dub");
            subtitle = state.getInt("subtitle", -1); quality = state.getInt("quality");
            position = state.getLong("position"); play = state.getBoolean("play", true);
            volume = state.getFloat("volume", 1f); speed = state.getFloat("speed", 1f);
            pipAspectMode = state.getInt("pipAspectMode", 0);
        }
        buildUi();
        ContextCompat.registerReceiver(this, receiver, new IntentFilter(ACTION), ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    private void buildUi() {
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        setContentView(root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            boolean pip = Build.VERSION.SDK_INT >= 26 && isInPictureInPictureMode();
            v.setPadding(pip ? 0 : bars.left, pip ? 0 : bars.top, pip ? 0 : bars.right, pip ? 0 : bars.bottom);
            return insets;
        });
        header = row(); root.addView(header);
        ImageButton close = icon(header, android.R.drawable.ic_menu_close_clear_cancel, "Đóng", this::finish);
        TextView title = label("Media3 Demo", 14); header.addView(title, new LinearLayout.LayoutParams(0, dp(40), 1));
        pipButton = icon(header, R.drawable.ic_pip, "Tùy chọn PiP", this::showPipOptions);
        for (int i = 1; i <= 3; i++) {
            final int selected = i;
            Button button = button(header, String.valueOf(i), () -> selectLevel(selected));
            button.setContentDescription("Mức demo " + i);
            button.setLayoutParams(new LinearLayout.LayoutParams(dp(36), dp(40)));
            levelButtons.add(button);
        }
        playerView = (PlayerView) getLayoutInflater().inflate(R.layout.demo_player, root, false);
        playerView.setControllerShowTimeoutMs(3000);
        bindController();
        playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING);
        root.addView(playerView, new LinearLayout.LayoutParams(-1, 0, 1));
        controls = new LinearLayout(this); controls.setOrientation(LinearLayout.VERTICAL); root.addView(controls);
        description = label("", 14); controls.addView(description);
        extra = new LinearLayout(this); extra.setOrientation(LinearLayout.VERTICAL); controls.addView(extra);
        status = label("", 13); controls.addView(status);
        refreshControls();
    }

    private void selectLevel(int next) {
        if (next == level) return;
        release(); level = next;
        // Keep the timestamp, but reset options so each level demonstrates its own scope.
        dub = 0; subtitle = -1; quality = 0; speed = 1f; volume = 1f;
        refreshControls(); startPlayer();
    }

    private void refreshControls() {
        for (int i = 0; i < levelButtons.size(); i++) levelButtons.get(i).setAlpha(i + 1 == level ? 1f : .45f);
        description.setText(level == 1 ? "1 · Cơ bản — phát và tua trên timeline" : level == 2
                ? "2 · Cấu hình — tua 10s, tốc độ, volume, buffer" : "3 · Mở rộng — HD / Auto, CC, dub, PiP");
        // Reuse the product controller, exposing only the current level's features.
        int configured = level >= 2 ? View.VISIBLE : View.GONE;
        for (int id : new int[]{R.id.btnExoVolume, R.id.sbExoVolume, R.id.btnExoSpeed,
                androidx.media3.ui.R.id.exo_rew, androidx.media3.ui.R.id.exo_ffwd})
            playerView.findViewById(id).setVisibility(configured);
        playerView.findViewById(R.id.btnExoQuality).setVisibility(level == 3 ? View.VISIBLE : View.GONE);
        playerView.findViewById(R.id.btnExoSettings).setVisibility(level == 3 ? View.VISIBLE : View.GONE);
        pipButton.setVisibility(level == 3 && supportsPip() ? View.VISIBLE : View.GONE);
        volumeSlider.setProgress(Math.round(volume * 100));
        updateStatus();
    }

    private void bindController() {
        centerPlay = playerView.findViewById(R.id.btnExoCenterPlayPause);
        centerPlay.setOnClickListener(v -> { if (player != null) {
            if (player.isPlaying()) player.pause(); else player.play();
        }});
        // Set our explicit 10-second icons after inflating the existing controller.
        ((ImageButton) playerView.findViewById(androidx.media3.ui.R.id.exo_rew)).setImageResource(R.drawable.demo_replay_10);
        ((ImageButton) playerView.findViewById(androidx.media3.ui.R.id.exo_ffwd)).setImageResource(R.drawable.demo_forward_10);
        playerView.findViewById(R.id.btnExoSpeed).setOnClickListener(v -> showSpeed());
        playerView.findViewById(R.id.btnExoQuality).setOnClickListener(v -> showQuality());
        playerView.findViewById(R.id.btnExoSettings).setOnClickListener(v ->
                new AlertDialog.Builder(this).setTitle("Cài đặt phát")
                    .setItems(new String[]{"Phụ đề CC", "Luồng âm thanh / Dub"}, (d, which) -> {
                        if (which == 0) showSubtitles(); else showDub();
                    }).show());
        playerView.findViewById(R.id.btnExoFullscreen).setOnClickListener(v -> {
            fullscreen = !fullscreen;
            header.setVisibility(fullscreen ? View.GONE : View.VISIBLE);
            controls.setVisibility(fullscreen ? View.GONE : View.VISIBLE);
            androidx.core.view.WindowInsetsControllerCompat bars =
                    new androidx.core.view.WindowInsetsControllerCompat(getWindow(), root);
            bars.setSystemBarsBehavior(androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            if (fullscreen) bars.hide(WindowInsetsCompat.Type.systemBars()); else bars.show(WindowInsetsCompat.Type.systemBars());
        });
        volumeSlider = playerView.findViewById(R.id.sbExoVolume);
        volumeSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                if (!fromUser || player == null) return;
                volume = value / 100f; player.setVolume(volume);
                if (volume > 0) previousVolume = volume;
                updateStatus();
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });
        playerView.findViewById(R.id.btnExoVolume).setOnClickListener(v -> {
            if (player == null) return;
            if (player.getVolume() > 0) { previousVolume = player.getVolume(); volume = 0; }
            else volume = previousVolume > 0 ? previousVolume : 1;
            player.setVolume(volume); volumeSlider.setProgress(Math.round(volume * 100)); updateStatus();
        });
    }

    private void startPlayer() {
        if (player != null) return;
        ExoPlayer.Builder builder = new ExoPlayer.Builder(this)
                .setRenderersFactory(demoRenderers());
        if (level >= 2) builder.setLoadControl(new DefaultLoadControl.Builder()
                .setBufferDurationsMs(15000, 30000, 1500, 3000)
                .setPrioritizeTimeOverSizeThresholds(true).build())
                .setSeekBackIncrementMs(10000).setSeekForwardIncrementMs(10000);
        player = builder.setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
                .setHandleAudioBecomingNoisy(true).build();
        playerView.setPlayer(player);
        playerView.setShowRewindButton(level >= 2); playerView.setShowFastForwardButton(level >= 2);
        playerView.setShowSubtitleButton(false);
        playerView.setShowNextButton(false); playerView.setShowPreviousButton(false);
        player.setVolume(volume); player.setPlaybackSpeed(speed);
        player.addListener(new Player.Listener() {
            @Override public void onTracksChanged(@NonNull Tracks tracks) { applyTrackChoices(); updateStatus(); }
            @Override public void onVideoSizeChanged(@NonNull VideoSize size) { updateStatus(); updatePip(); }
            @Override public void onIsPlayingChanged(boolean playing) { playerView.setKeepScreenOn(playing); updatePlayIcon(); updatePip(); }
            @Override public void onPlayWhenReadyChanged(boolean ready, int reason) { updatePlayIcon(); updatePip(); }
            @Override public void onTimelineChanged(@NonNull androidx.media3.common.Timeline timeline, int reason) { updatePip(); }
            @Override public void onPlaybackStateChanged(int state) { updateStatus(); }
            @Override public void onPlayerError(@NonNull PlaybackException error) {
                status.setText("Không phát được: " + error.getErrorCodeName());
                new AlertDialog.Builder(MediaDemoActivity.this).setTitle("Lỗi nguồn demo")
                        .setMessage(error.getErrorCodeName() + "\nKiểm tra URL trực tiếp, định dạng và timeline dub.")
                        .setPositiveButton("Thử lại", (d, w) -> loadSource()).setNegativeButton("Đóng", null).show();
            }
        });
        updatePlayIcon();
        loadSource();
    }

    private DefaultRenderersFactory demoRenderers() {
        DefaultRenderersFactory factory = new DefaultRenderersFactory(this)
                .setEnableDecoderFallback(true);
        // Goldfish's host H.264 decoder can output stale/striped frames after an
        // adaptive resolution change. Prefer Android's software video decoder
        // only on the emulator, without affecting real devices or product playback.
        boolean emulator = Build.HARDWARE.contains("ranchu") || Build.HARDWARE.contains("goldfish")
                || Build.MODEL.startsWith("sdk_gphone");
        if (emulator) factory.setMediaCodecSelector((mime, secure, tunneling) -> {
            List<MediaCodecInfo> available = MediaCodecSelector.DEFAULT
                    .getDecoderInfos(mime, secure, tunneling);
            if (mime.startsWith("video/")) {
                List<MediaCodecInfo> software = new ArrayList<>();
                for (MediaCodecInfo codec : available) if (codec.softwareOnly
                        && !codec.name.toLowerCase(Locale.ROOT).contains("goldfish")) software.add(codec);
                if (!software.isEmpty()) return software;
            }
            return available;
        });
        return factory;
    }

    private void loadSource() {
        if (player == null) return;
        MediaItem.Builder item = new MediaItem.Builder().setUri(VIDEO_URL);
        List<MediaItem.SubtitleConfiguration> subs = new ArrayList<>();
        String[] urls = {SUBTITLE_DEMO_1_URL, SUBTITLE_DEMO_2_URL};
        if (level == 3) for (int i = 0; i < urls.length; i++) {
            if (urls[i].trim().isEmpty()) continue;
            String path = Uri.parse(urls[i]).getPath();
            boolean srt = path != null && path.toLowerCase(Locale.ROOT).endsWith(".srt");
            subs.add(new MediaItem.SubtitleConfiguration.Builder(Uri.parse(urls[i]))
                    .setId("demo-sub-" + i).setLabel("Demo " + (i + 1)).setLanguage("vi")
                    .setMimeType(srt ? MimeTypes.APPLICATION_SUBRIP : MimeTypes.TEXT_VTT).build());
        }
        item.setSubtitleConfigurations(subs);
        DefaultMediaSourceFactory factory = new DefaultMediaSourceFactory(this);
        MediaSource source = factory.createMediaSource(item.build());
        if (level == 3 && dub > 0) {
            MediaSource picture = new FilteringMediaSource(source, new HashSet<>(Arrays.asList(C.TRACK_TYPE_VIDEO, C.TRACK_TYPE_TEXT)));
            String url = dub == 1 ? DUB_DEMO_1_URL : DUB_DEMO_2_URL;
            MediaSource audio = new FilteringMediaSource(factory.createMediaSource(MediaItem.fromUri(url)), C.TRACK_TYPE_AUDIO);
            source = new MergingMediaSource(true, false, picture, audio);
        }
        player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().clearOverrides()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, subtitle < 0).build());
        player.setMediaSource(source); player.prepare(); player.seekTo(position); player.setPlayWhenReady(play);
    }

    private void showSpeed() {
        float[] values = {.5f, .75f, 1f, 1.5f, 2f};
        new AlertDialog.Builder(this).setTitle("Tốc độ phát")
                .setItems(new String[]{"0.5x", "0.75x", "1x", "1.5x", "2x"}, (d, which) -> {
                    speed = values[which]; if (player != null) player.setPlaybackSpeed(speed); updateStatus();
                }).show();
    }

    private void showQuality() {
        if (player == null) return;
        TreeMap<Integer, Boolean> heights = new TreeMap<>(Collections.reverseOrder());
        for (Tracks.Group g : player.getCurrentTracks().getGroups()) if (g.getType() == C.TRACK_TYPE_VIDEO)
            for (int i = 0; i < g.length; i++) if (g.isTrackSupported(i) && g.getTrackFormat(i).height > 0)
                heights.put(g.getTrackFormat(i).height, true);
        if (heights.size() < 2) { message("Nguồn này chưa có nhiều mức chất lượng. Dùng HLS master playlist có nhiều variant."); return; }
        List<Integer> values = new ArrayList<>(); values.add(0); values.addAll(heights.keySet());
        String[] labels = new String[values.size()]; labels[0] = "Auto";
        for (int i = 1; i < labels.length; i++) labels[i] = values.get(i) + "p";
        new AlertDialog.Builder(this).setTitle("Chất lượng").setItems(labels, (d, which) -> {
            quality = values.get(which); applyTrackChoices(); updateStatus();
        }).show();
    }

    private void showSubtitles() {
        new AlertDialog.Builder(this).setTitle("Phụ đề")
                .setItems(new String[]{"Tắt", "Demo 1", "Demo 2"}, (d, which) -> {
                    String url = which == 1 ? SUBTITLE_DEMO_1_URL : SUBTITLE_DEMO_2_URL;
                    if (which > 0 && url.trim().isEmpty()) { message("Điền SUBTITLE_DEMO_" + which + "_URL trong MediaDemoActivity.java."); return; }
                    subtitle = which - 1; applyTrackChoices(); updateStatus();
                }).show();
    }

    private void showDub() {
        new AlertDialog.Builder(this).setTitle("Luồng âm thanh")
                .setItems(new String[]{"Âm thanh gốc", "Dub demo 1", "Dub demo 2"}, (d, which) -> {
                    String url = which == 1 ? DUB_DEMO_1_URL : DUB_DEMO_2_URL;
                    if (which > 0 && url.trim().isEmpty()) { message("Điền DUB_DEMO_" + which + "_URL trong MediaDemoActivity.java."); return; }
                    if (which == dub || player == null) return;
                    position = player.getCurrentPosition(); play = player.getPlayWhenReady(); dub = which; loadSource();
                }).show();
    }

    private void applyTrackChoices() {
        if (player == null) return;
        TrackSelectionParameters.Builder b = player.getTrackSelectionParameters().buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_TEXT).clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                .clearVideoSizeConstraints().setMaxVideoBitrate(Integer.MAX_VALUE)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, subtitle < 0);
        Tracks.Group best = null; int index = -1, bitrate = -1;
        for (Tracks.Group g : player.getCurrentTracks().getGroups()) for (int i = 0; i < g.length; i++) {
            if (!g.isTrackSupported(i)) continue;
            if (g.getType() == C.TRACK_TYPE_TEXT && subtitle >= 0
                    && ("Demo " + (subtitle + 1)).equals(g.getTrackFormat(i).label))
                b.setOverrideForType(new TrackSelectionOverride(g.getMediaTrackGroup(), i));
            if (g.getType() == C.TRACK_TYPE_VIDEO && quality > 0 && g.getTrackFormat(i).height == quality
                    && (index < 0 || g.getTrackFormat(i).bitrate > bitrate)) {
                best = g; index = i; bitrate = g.getTrackFormat(i).bitrate;
            }
        }
        if (best != null) b.setOverrideForType(new TrackSelectionOverride(best.getMediaTrackGroup(), index));
        TrackSelectionParameters params = b.build();
        if (!params.equals(player.getTrackSelectionParameters())) player.setTrackSelectionParameters(params);
    }

    private void updateStatus() {
        if (status == null) return;
        if (player == null) { status.setText("Đang chuẩn bị…"); return; }
        int height = player.getVideoSize().height;
        String state = player.getPlaybackState() == Player.STATE_BUFFERING ? " · Buffering" : "";
        status.setText((height > 0 ? height + "p" : "…") + (quality == 0 ? " AUTO" : "")
                + (level >= 2 ? " · " + speed + "x · Vol " + Math.round(volume * 100) + "%" : "")
                + (level == 3 ? " · CC " + (subtitle < 0 ? "Off" : subtitle + 1) + " · " + (dub == 0 ? "Audio gốc" : "Dub " + dub) : "") + state);
    }

    @ChecksSdkIntAtLeast(api = 26)
    private boolean supportsPip() { return Build.VERSION.SDK_INT >= 26 && getPackageManager().hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE); }
    @RequiresApi(26)
    private PictureInPictureParams pipParams() {
        VideoSize size = player == null ? VideoSize.UNKNOWN : player.getVideoSize();
        float ratio = size.height > 0 ? size.width * size.pixelWidthHeightRatio / size.height : 16f / 9f;
        if (pipAspectMode == 1) ratio = 16f / 9f;
        else if (pipAspectMode == 2) ratio = 9f / 16f;
        ratio = Math.max(1f / 2.39f, Math.min(2.39f, ratio));
        PictureInPictureParams.Builder b = new PictureInPictureParams.Builder().setAspectRatio(new Rational(Math.round(ratio * 10000), 10000));
        Rect bounds = new Rect(); if (playerView.getGlobalVisibleRect(bounds)) b.setSourceRectHint(bounds);
        boolean playing = player != null && player.getPlayWhenReady();
        boolean seekable = player != null && player.isCurrentMediaItemSeekable();
        RemoteAction back = pipAction(COMMAND_REWIND, R.drawable.demo_replay_10, "Lùi 10 giây");
        RemoteAction toggle = pipAction(COMMAND_PLAY_PAUSE,
                playing ? R.drawable.ic_pause : R.drawable.ic_play, playing ? "Tạm dừng" : "Phát");
        RemoteAction forward = pipAction(COMMAND_FORWARD, R.drawable.demo_forward_10, "Tiến 10 giây");
        back.setEnabled(seekable); forward.setEnabled(seekable);
        // Android controls how many actions fit. Always retain Play/Pause.
        b.setActions(getMaxNumPictureInPictureActions() >= 3
                ? Arrays.asList(back, toggle, forward) : Collections.singletonList(toggle));
        return b.build();
    }
    @RequiresApi(26)
    private RemoteAction pipAction(int command, int icon, String title) {
        Intent intent = new Intent(ACTION).setPackage(getPackageName()).putExtra(EXTRA_COMMAND, command);
        // Different request codes keep the three PendingIntents distinct.
        PendingIntent pending = PendingIntent.getBroadcast(this, 9400 + command, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new RemoteAction(Icon.createWithResource(this, icon), title, title, pending);
    }
    private void showPipOptions() {
        new AlertDialog.Builder(this).setTitle("Picture-in-Picture")
                .setItems(new String[]{"PiP theo tỷ lệ video", "PiP ngang (16:9)", "PiP dọc (9:16)",
                        "Cách đổi cỡ nhỏ/lớn (~25%)"}, (dialog, which) -> {
                    if (which == 3) showPipSizeHelp();
                    else { pipAspectMode = which; enterPip(); }
                }).show();
    }
    private void showPipSizeHelp() {
        new AlertDialog.Builder(this).setTitle("Kích thước PiP")
                .setMessage("Android quyết định kích thước cửa sổ PiP; app không đặt được chính xác 25% màn hình.\n\n"
                        + "Trên Android 12 trở lên: chạm hai lần để đổi giữa cỡ nhỏ và lớn, hoặc dùng hai ngón để co/giãn.\n\n"
                        + "Muốn cỡ khoảng 25%, co/giãn cửa sổ đến kích thước mong muốn; tỷ lệ chính xác phụ thuộc thiết bị. "
                        + "Chọn ngang/dọc trong menu PiP trước khi mở. Video giữ nguyên tỷ lệ, có thể có viền đen.\n\n"
                        + "Chạm một lần vào PiP để hiện nút lùi 10 giây, Phát/Tạm dừng và tiến 10 giây.")
                .setPositiveButton("Mở PiP", (dialog, which) -> enterPip())
                .setNegativeButton("Đóng", null).show();
    }
    private void enterPip() {
        if (!supportsPip() || isInPictureInPictureMode()) return;
        try { if (!enterPictureInPictureMode(pipParams())) message("PiP chưa được phép trên thiết bị này."); }
        catch (IllegalStateException | IllegalArgumentException error) { message("Không mở được PiP. Kiểm tra cài đặt ứng dụng."); }
    }
    private void updatePip() { if (supportsPip() && isInPictureInPictureMode()) setPictureInPictureParams(pipParams()); }
    @Override protected void onUserLeaveHint() { super.onUserLeaveHint(); if (level == 3 && player != null && player.isPlaying()) enterPip(); }
    @Override public void onPictureInPictureModeChanged(boolean pip, Configuration config) {
        super.onPictureInPictureModeChanged(pip, config);
        header.setVisibility(pip || fullscreen ? View.GONE : View.VISIBLE); controls.setVisibility(pip || fullscreen ? View.GONE : View.VISIBLE);
        playerView.setUseController(!pip); ViewCompat.requestApplyInsets(root);
    }

    private void updatePlayIcon() {
        if (centerPlay != null) centerPlay.setImageResource(player != null && player.isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play);
    }
    private ImageButton icon(LinearLayout parent, int drawable, String title, Runnable action) {
        ImageButton b = new ImageButton(this); b.setImageResource(drawable); b.setColorFilter(Color.WHITE);
        b.setBackgroundColor(Color.TRANSPARENT); b.setContentDescription(title); b.setPadding(dp(9), dp(9), dp(9), dp(9));
        parent.addView(b, new LinearLayout.LayoutParams(dp(40), dp(40))); b.setOnClickListener(v -> action.run()); return b;
    }

    private void release() {
        if (player == null) return;
        position = player.getCurrentPosition(); play = player.getPlayWhenReady();
        volume = player.getVolume(); speed = player.getPlaybackParameters().speed;
        playerView.setPlayer(null); player.release(); player = null;
    }
    @Override protected void onStart() { super.onStart(); startPlayer(); }
    @Override protected void onStop() { super.onStop(); release(); }
    @Override protected void onDestroy() { unregisterReceiver(receiver); release(); super.onDestroy(); }
    @Override protected void onSaveInstanceState(@NonNull Bundle out) {
        out.putInt("level", level); out.putInt("dub", dub); out.putInt("subtitle", subtitle); out.putInt("quality", quality);
        out.putInt("pipAspectMode", pipAspectMode);
        out.putLong("position", player == null ? position : player.getCurrentPosition());
        out.putBoolean("play", player == null ? play : player.getPlayWhenReady());
        out.putFloat("volume", volume); out.putFloat("speed", speed); super.onSaveInstanceState(out);
    }
    private LinearLayout row() { LinearLayout row = new LinearLayout(this); row.setGravity(android.view.Gravity.CENTER_VERTICAL); return row; }
    private TextView label(String value, int size) { TextView v = new TextView(this); v.setText(value); v.setTextSize(size); v.setTextColor(Color.WHITE); v.setPadding(dp(10), dp(6), dp(6), dp(6)); v.setGravity(android.view.Gravity.CENTER_VERTICAL); return v; }
    private Button button(LinearLayout parent, String title, Runnable action) {
        Button v = new Button(this); v.setText(title); v.setTextSize(12); v.setAllCaps(false);
        v.setTextColor(Color.WHITE); v.setBackgroundColor(Color.TRANSPARENT);
        v.setMinWidth(0); v.setMinimumWidth(0); v.setPadding(dp(8), 0, dp(8), 0);
        parent.addView(v, new LinearLayout.LayoutParams(0, dp(48), 1)); v.setOnClickListener(w -> action.run()); return v;
    }
    private void message(String text) { Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
