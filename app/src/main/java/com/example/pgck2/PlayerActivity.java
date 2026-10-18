package com.example.pgck2;

import android.app.DownloadManager;
import android.app.PendingIntent;
import android.app.PictureInPictureParams;
import android.app.RemoteAction;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.content.res.Configuration;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Rational;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
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
import androidx.core.widget.NestedScrollView;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.Tracks;
import androidx.media3.common.VideoSize;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.FilteringMediaSource;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.MergingMediaSource;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.session.MediaSession;
import androidx.media3.ui.PlayerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * PlayerActivity — Màn hình Chi Tiết & trình phát đa năng (ExoPlayer + YouTube WebView).
 * Playback quality depends on the source, selected track and device decoder.
 */
@OptIn(markerClass = UnstableApi.class)
public class PlayerActivity extends AppCompatActivity {

    public static final String EXTRA_VIDEO_URL = "extra_video_url";
    public static final String EXTRA_VIDEO_TITLE = "extra_video_title";
    public static final String EXTRA_VIDEO_DESC = "extra_video_desc";

    // Ánh xạ các View giao diện
    private FrameLayout framePlayerContainer;
    private PlayerView playerView;
    private WebView wvYouTubePlayer;
    private RelativeLayout layoutTopBar;
    private NestedScrollView scrollViewMovieDetail;
    private TextView tvDetailTitle;
    private TextView tvDetailGenres;
    private TextView tvDetailDesc;
    private ProgressBar progressBuffering;
    private TextView tvQualityBadge;

    private ImageButton btnExitPlayer;
    private ImageButton btnDownloadVideo;
    private ImageButton btnPip;
    private ImageButton btnCenterPlayPause;
    private ImageButton btnVolume;
    private SeekBar sbExoVolume;
    private ImageButton btnSettings;
    private ImageButton btnQuality;
    private ImageButton btnSpeed;
    private ImageButton btnMaximize;
    private TextView tvPlayerVideoTitle;

    // Các biến trạng thái logic
    private ExoPlayer player;
    private DefaultTrackSelector trackSelector;
    private MediaSession mediaSession;
    private String videoUrl;
    private String currentMediaUrl;
    private String activeAlternateAudioUrl;
    private String activeAlternateLabel;
    private String selectedAudioKey;
    private String selectedSubtitleKey;
    private String videoTitle;
    private String videoDesc;
    private String videoGenres;
    private boolean isWebViewMode = false;
    private boolean isFullscreen = false;
    private float lastNonZeroVolume = 1.0f;

    // Ghi nhớ vị trí phát để dựng lại player sau onStop
    private long resumePosition = 0;
    private boolean resumePlayWhenReady = true;
    private float resumeVolume = 1f;
    private PlaybackParameters resumePlaybackParameters = PlaybackParameters.DEFAULT;

    // Chất lượng đang chọn: 0 = Auto, còn lại = chiều cao mục tiêu (360/480/720/1080)
    private int qualityHeight = 0;
    // Phụ đề: có bật hay không + ngôn ngữ ưu tiên
    private boolean subtitlesEnabled = true;
    private String preferredSubtitleLang = "vi";

    // Danh sách luồng âm thanh & phụ đề rời nhận từ Admin
    private final List<AudioTrackOption> audioTracksList = new ArrayList<>();
    private final List<SubtitleOption> subtitleTracksList = new ArrayList<>();

    private static final String ACTION_MEDIA_CONTROL = "media_control_action";
    private static final int REQUEST_CODE = 100;

    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || !ACTION_MEDIA_CONTROL.equals(intent.getAction())) return;
            if (player != null) {
                if (player.isPlaying()) {
                    player.pause();
                } else {
                    player.play();
                }
                updatePipParams();
                updatePlayPauseIcon();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        initViews();
        qualityHeight = getSharedPreferences("player_preferences", MODE_PRIVATE)
                .getInt("preferred_video_height", 0);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.playerContainer), (view, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            boolean pip = Build.VERSION.SDK_INT >= 26 && isInPictureInPictureMode();
            view.setPadding(pip ? 0 : bars.left, pip ? 0 : bars.top,
                    pip ? 0 : bars.right, pip ? 0 : bars.bottom);
            return insets;
        });

        Intent intent = getIntent();
        if (intent != null) {
            if (intent.hasExtra(EXTRA_VIDEO_URL)) {
                videoUrl = intent.getStringExtra(EXTRA_VIDEO_URL);
            }
            if (intent.hasExtra(EXTRA_VIDEO_TITLE)) {
                videoTitle = intent.getStringExtra(EXTRA_VIDEO_TITLE);
                tvPlayerVideoTitle.setText(videoTitle);
                tvDetailTitle.setText(videoTitle);
            }
            if (intent.hasExtra(EXTRA_VIDEO_DESC)) {
                videoDesc = intent.getStringExtra(EXTRA_VIDEO_DESC);
                tvDetailDesc.setText(videoDesc != null && !videoDesc.trim().isEmpty() ? videoDesc : "Chưa có nội dung mô tả cho phim này.");
            }
            if (intent.hasExtra("extra_video_genres")) {
                videoGenres = intent.getStringExtra("extra_video_genres");
                tvDetailGenres.setText(getString(R.string.the_loai, videoGenres != null && !videoGenres.isEmpty() ? videoGenres : "Chưa phân loại"));
            } else {
                tvDetailGenres.setText(getString(R.string.the_loai, "Chưa phân loại"));
            }

            // Đọc danh sách Luồng Âm Thanh (Dub) Admin đã thêm
            if (intent.hasExtra("extra_audio_names")) {
                ArrayList<String> audioNames = intent.getStringArrayListExtra("extra_audio_names");
                ArrayList<String> audioLangs = intent.getStringArrayListExtra("extra_audio_langs");
                ArrayList<String> audioUrls = intent.getStringArrayListExtra("extra_audio_urls");

                if (audioNames != null && audioLangs != null && audioUrls != null) {
                    int n = Math.min(audioNames.size(), Math.min(audioLangs.size(), audioUrls.size()));
                    for (int i = 0; i < n; i++) {
                        audioTracksList.add(new AudioTrackOption(audioNames.get(i), audioLangs.get(i), audioUrls.get(i)));
                    }
                }
            }

            // Đọc danh sách Phụ Đề Rời (Sub CC) Admin đã thêm
            if (intent.hasExtra("extra_sub_names")) {
                ArrayList<String> subNames = intent.getStringArrayListExtra("extra_sub_names");
                ArrayList<String> subLangs = intent.getStringArrayListExtra("extra_sub_langs");
                ArrayList<String> subUrls = intent.getStringArrayListExtra("extra_sub_urls");

                if (subNames != null && subLangs != null && subUrls != null) {
                    int n = Math.min(subNames.size(), Math.min(subLangs.size(), subUrls.size()));
                    for (int i = 0; i < subNames.size() && i < n; i++) {
                        subtitleTracksList.add(new SubtitleOption(subNames.get(i), subLangs.get(i), subUrls.get(i)));
                    }
                }
            }
        }
        List<AudioTrackOption> audio = MediaTracks.uniqueAudio(audioTracksList);
        audioTracksList.clear();
        audioTracksList.addAll(audio);
        List<SubtitleOption> subtitles = MediaTracks.uniqueSubtitles(subtitleTracksList);
        subtitleTracksList.clear();
        subtitleTracksList.addAll(subtitles);
        currentMediaUrl = videoUrl;

        setupControlListeners();
        setupPlayer();

        IntentFilter filter = new IntentFilter(ACTION_MEDIA_CONTROL);
        ContextCompat.registerReceiver(this, broadcastReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    private void initViews() {
        framePlayerContainer = findViewById(R.id.framePlayerContainer);
        playerView = findViewById(R.id.playerView);
        wvYouTubePlayer = findViewById(R.id.wvYouTubePlayer);
        layoutTopBar = findViewById(R.id.layoutTopBar);
        scrollViewMovieDetail = findViewById(R.id.scrollViewMovieDetail);
        tvDetailTitle = findViewById(R.id.tvDetailTitle);
        tvDetailGenres = findViewById(R.id.tvDetailGenres);
        tvDetailDesc = findViewById(R.id.tvDetailDesc);
        progressBuffering = findViewById(R.id.progressBuffering);
        tvQualityBadge = findViewById(R.id.tvQualityBadge);

        btnExitPlayer = findViewById(R.id.btnExitPlayer);
        btnDownloadVideo = findViewById(R.id.btnDownloadVideo);
        btnPip = findViewById(R.id.btnPip);
        tvPlayerVideoTitle = findViewById(R.id.tvPlayerVideoTitle);

        if (playerView != null) {
            playerView.setControllerShowTimeoutMs(3000);
            playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER);

            btnCenterPlayPause = playerView.findViewById(R.id.btnExoCenterPlayPause);
            btnVolume = playerView.findViewById(R.id.btnExoVolume);
            sbExoVolume = playerView.findViewById(R.id.sbExoVolume);
            btnSettings = playerView.findViewById(R.id.btnExoSettings);
            btnQuality = playerView.findViewById(R.id.btnExoQuality);
            btnSpeed = playerView.findViewById(R.id.btnExoSpeed);
            btnMaximize = playerView.findViewById(R.id.btnExoFullscreen);
        }

        if (layoutTopBar != null) layoutTopBar.bringToFront();
        btnPip.setVisibility(supportsPip() ? View.VISIBLE : View.GONE);
        updateVolumeIcon();
    }

    private void setupControlListeners() {
        btnExitPlayer.setOnClickListener(v -> finish());
        findViewById(R.id.btnWebBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnWebPip).setVisibility(supportsPip() ? View.VISIBLE : View.GONE);
        findViewById(R.id.btnWebPip).setOnClickListener(v -> enterPipMode());
        findViewById(R.id.btnWebFullscreen).setOnClickListener(v -> toggleFullscreenMode());

        if (btnDownloadVideo != null) {
            btnDownloadVideo.setOnClickListener(v -> downloadVideo());
        }

        // Nút Play / Pause Trung Tâm Duy Nhất
        if (btnCenterPlayPause != null) {
            btnCenterPlayPause.setOnClickListener(v -> {
                if (player == null) return;
                if (player.isPlaying()) {
                    player.pause();
                } else {
                    player.play();
                }
                updatePlayPauseIcon();
            });
        }

        // Thanh trượt âm lượng trực tiếp trên thanh điều khiển Player (YouTube Style)
        if (sbExoVolume != null) {
            sbExoVolume.setMax(100);
            float curVol = (player != null) ? player.getVolume() * 100f : 100f;
            sbExoVolume.setProgress(Math.round(curVol));

            sbExoVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser && player != null) {
                        float v = progress / 100f;
                        player.setVolume(v);
                        if (v > 0f) {
                            lastNonZeroVolume = v;
                        }
                        updateVolumeIcon();
                    }
                }

                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }

        // Nút bấm Mute / Unmute âm lượng nhanh trên Player Bar
        if (btnVolume != null) {
            btnVolume.setOnClickListener(v -> {
                if (player == null) return;
                if (player.getVolume() > 0f) {
                    lastNonZeroVolume = player.getVolume();
                    player.setVolume(0f);
                    if (sbExoVolume != null) sbExoVolume.setProgress(0);
                    Toast.makeText(this, "Tắt tiếng phim (Mute)", Toast.LENGTH_SHORT).show();
                } else {
                    float restoreVol = lastNonZeroVolume > 0f ? lastNonZeroVolume : 1.0f;
                    player.setVolume(restoreVol);
                    if (sbExoVolume != null) sbExoVolume.setProgress(Math.round(restoreVol * 100f));
                    Toast.makeText(this, "Bật tiếng phim", Toast.LENGTH_SHORT).show();
                }
                updateVolumeIcon();
            });
        }

        if (btnSettings != null) {
            btnSettings.setOnClickListener(v -> showSettingsDialog());
        }

        if (btnQuality != null) {
            btnQuality.setOnClickListener(v -> showResolutionDialog());
        }

        if (btnSpeed != null) {
            btnSpeed.setOnClickListener(v -> showSpeedDialog());
        }

        if (btnMaximize != null) {
            btnMaximize.setOnClickListener(v -> toggleFullscreenMode());
        }

        if (btnPip != null) {
            btnPip.setOnClickListener(v -> enterPipMode());
        }

        if (playerView != null) {
            playerView.setControllerVisibilityListener((PlayerView.ControllerVisibilityListener) visibility -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode()) {
                    return;
                }
                if (layoutTopBar != null) {
                    layoutTopBar.setVisibility(visibility);
                    if (visibility == View.VISIBLE) layoutTopBar.bringToFront();
                }
            });
        }
    }

    /** Cập nhật Icon Play / Pause trung tâm khớp với trạng thái thực tế của ExoPlayer. */
    private void updatePlayPauseIcon() {
        if (btnCenterPlayPause == null) return;
        boolean isPlaying = (player != null && player.isPlaying());
        btnCenterPlayPause.setImageResource(isPlaying ? R.drawable.ic_pause : R.drawable.ic_play);
    }

    private void toggleFullscreenMode() {
        isFullscreen = !isFullscreen;
        setPlayerExpanded(isFullscreen);

        if (isFullscreen) {
            scrollViewMovieDetail.setVisibility(View.GONE);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
            if (getSupportActionBar() != null) getSupportActionBar().hide();
            Toast.makeText(this, "Toàn Màn Hình", Toast.LENGTH_SHORT).show();
        } else {
            scrollViewMovieDetail.setVisibility(View.VISIBLE);
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
            if (getSupportActionBar() != null) getSupportActionBar().show();
            Toast.makeText(this, "Khung Chi Tiết", Toast.LENGTH_SHORT).show();
        }

        if (layoutTopBar != null) layoutTopBar.bringToFront();
    }

    private void setPlayerExpanded(boolean expanded) {
        androidx.constraintlayout.widget.ConstraintLayout.LayoutParams params =
                (androidx.constraintlayout.widget.ConstraintLayout.LayoutParams) framePlayerContainer.getLayoutParams();
        params.height = expanded ? 0 : (int) (250 * getResources().getDisplayMetrics().density);
        params.bottomToBottom = expanded ? androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
                : androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET;
        framePlayerContainer.setLayoutParams(params);
    }

    private void showSettingsDialog() {
        String[] options = {
                "💬 Phụ Đề Rời (Subtitles CC)",
                "🎙️ Ngôn Ngữ Lồng Tiếng (Multi-Audio Dub)",
                "🎬 Chất Lượng Video (Quality HD)",
                "⚡ Tốc Độ Phát Video (Playback Speed)"};
        new AlertDialog.Builder(this)
                .setTitle("Cài Đặt Trình Phát Video")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        showSubtitleDialog();
                    } else if (which == 1) {
                        showAudioTrackDialog();
                    } else if (which == 2) {
                        showResolutionDialog();
                    } else {
                        showSpeedDialog();
                    }
                })
                .setNegativeButton("Đóng", null)
                .show();
    }

    private void updateVolumeIcon() {
        if (btnVolume == null) return;
        boolean muted = (player != null && player.getVolume() == 0f);
        btnVolume.setAlpha(muted ? 0.4f : 1.0f);
    }

    private void downloadVideo() {
        if (videoUrl == null || videoUrl.isEmpty()) {
            Toast.makeText(this, "Không có URL video để tải!", Toast.LENGTH_SHORT).show();
            return;
        }

        if (isWebViewMode) {
            Toast.makeText(this, "Link trang web không hỗ trợ tải trực tiếp. Vui lòng xem online!", Toast.LENGTH_LONG).show();
            return;
        }

        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(currentMediaUrl != null ? currentMediaUrl : videoUrl));
            String fileName = (videoTitle != null && !videoTitle.trim().isEmpty() ? videoTitle : "phim_" + System.currentTimeMillis()) + ".mp4";
            fileName = fileName.replaceAll("[^a-zA-Z0-9._-]", "_");

            request.setTitle("Đang tải: " + (videoTitle != null ? videoTitle : "Phim"));
            request.setDescription("Đang tải file video về máy...");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_MOVIES, fileName);
            request.setAllowedOverMetered(true);
            request.setAllowedOverRoaming(true);

            DownloadManager downloadManager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            if (downloadManager != null) {
                downloadManager.enqueue(request);
                Toast.makeText(this, "Bắt đầu tải video: " + fileName, Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Lỗi khi tải video: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void setupPlayer() {
        if (videoUrl == null || videoUrl.isEmpty()) {
            videoUrl = "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8";
        }
        if (currentMediaUrl == null || currentMediaUrl.isEmpty()) {
            currentMediaUrl = videoUrl;
        }

        Video tempVideo = new Video();
        tempVideo.setVideoUrl(videoUrl);

        if (tempVideo.isYouTubeVideo()) {
            isWebViewMode = true;
            playYouTubeVideo(tempVideo.getYouTubeVideoId());
        } else if (videoUrl.matches("(?i).*\\.(mp4|m3u8|mpd|webm|mkv)([?#].*)?$")) {
            isWebViewMode = false;
            playDirectMediaVideo();
        } else {
            isWebViewMode = true;
            playWebVideo(videoUrl);
        }
    }

    private void showResolutionDialog() {
        if (isWebViewMode) {
            showResolutionDialogWeb();
            return;
        }
        if (player == null) {
            Toast.makeText(this, "Trình phát chưa sẵn sàng, thử lại sau giây lát!", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<Integer, Long> heightToBitrate = new LinkedHashMap<>();
        for (Tracks.Group group : player.getCurrentTracks().getGroups()) {
            if (group.getType() != C.TRACK_TYPE_VIDEO) continue;
            for (int i = 0; i < group.length; i++) {
                int h = group.getTrackFormat(i).height;
                int bitrate = group.getTrackFormat(i).bitrate;
                if (h > 0 && group.isTrackSupported(i)) {
                    Long old = heightToBitrate.get(h);
                    if (old == null || (bitrate > 0 && bitrate > old)) {
                        heightToBitrate.put(h, (long) Math.max(bitrate, 0));
                    }
                }
            }
        }

        List<Integer> heights = new ArrayList<>(heightToBitrate.keySet());
        Collections.sort(heights);

        if (heights.size() <= 1) {
            new AlertDialog.Builder(this)
                    .setTitle("Chất lượng video")
                    .setMessage("Video này chỉ có 1 luồng phân giải"
                            + (heights.size() == 1 ? " (" + heights.get(0) + "p)" : "")
                            + " (file MP4 trực tiếp), nên không có mức khác để chuyển.\n\n"
                            + "Muốn đổi 360/480/720/1080 được thì nguồn phải là HLS (.m3u8) "
                            + "hoặc DASH (.mpd) đa variant — hãy thêm link dạng đó trong Admin.")
                    .setPositiveButton("Đã hiểu", null)
                    .show();
            return;
        }

        List<String> labels = new ArrayList<>();
        labels.add(qualityHeight == 0 ? "✓ Tự động (Auto Adaptive)" : "Tự động (Auto Adaptive)");
        List<Integer> values = new ArrayList<>();
        values.add(0);

        for (int i = heights.size() - 1; i >= 0; i--) {
            int h = heights.get(i);
            long br = heightToBitrate.get(h);
            String bitrateStr = br > 0 ? " • " + String.format("%.1f Mbps", br / 1000000f) : "";
            labels.add((qualityHeight == h ? "✓ " : "") + h + "p" + bitrateStr);
            values.add(h);
        }

        String[] arr = labels.toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle("Chọn độ phân giải (" + heights.size() + " mức có thật)")
                .setItems(arr, (dialog, which) -> changeVideoResolutionByHeight(values.get(which), labels.get(which)))
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void showResolutionDialogWeb() {
        new AlertDialog.Builder(this)
                .setTitle("Chất lượng video")
                .setMessage("Chọn Chất lượng trong nút cài đặt của video đang phát.")
                .setPositiveButton("Đã hiểu", null)
                .show();
    }

    private void changeVideoResolutionByHeight(int height, String label) {
        if (player == null) return;
        qualityHeight = height;
        getSharedPreferences("player_preferences", MODE_PRIVATE).edit()
                .putInt("preferred_video_height", height).apply();

        TrackSelectionParameters.Builder builder = player.getTrackSelectionParameters().buildUpon();
        reapplyQualitySelection(builder);
        player.setTrackSelectionParameters(builder.build());
        updateQualityBadge();
        Toast.makeText(this, "Đã chuyển độ phân giải: " + label.replace("✓ ", ""), Toast.LENGTH_SHORT).show();
    }

    private TrackSelectionOverride findBestVideoOverride(int height) {
        Tracks.Group bestGroup = null;
        int bestIndex = -1;
        int bestHeight = -1;
        int bestBitrate = -1;
        for (Tracks.Group group : player.getCurrentTracks().getGroups()) {
            if (group.getType() != C.TRACK_TYPE_VIDEO) continue;
            for (int i = 0; i < group.length; i++) {
                int h = group.getTrackFormat(i).height;
                int bitrate = group.getTrackFormat(i).bitrate;
                if (group.isTrackSupported(i) && h > 0 && h <= height
                        && (h > bestHeight || (h == bestHeight && bitrate > bestBitrate))) {
                    bestHeight = h;
                    bestBitrate = bitrate;
                    bestGroup = group;
                    bestIndex = i;
                }
            }
        }
        if (bestGroup == null) {
            for (Tracks.Group group : player.getCurrentTracks().getGroups()) {
                if (group.getType() != C.TRACK_TYPE_VIDEO) continue;
                for (int i = 0; i < group.length; i++) {
                    int h = group.getTrackFormat(i).height;
                    if (group.isTrackSupported(i) && h > 0 && (bestHeight == -1 || h < bestHeight)) {
                        bestHeight = h;
                        bestGroup = group;
                        bestIndex = i;
                    }
                }
            }
        }
        if (bestGroup != null && bestIndex >= 0) {
            return new TrackSelectionOverride(bestGroup.getMediaTrackGroup(), bestIndex);
        }
        return null;
    }

    private void reapplyQualitySelection(TrackSelectionParameters.Builder builder) {
        if (qualityHeight == 0) {
            builder.clearVideoSizeConstraints();
            builder.setMaxVideoBitrate(Integer.MAX_VALUE);
            builder.clearOverridesOfType(C.TRACK_TYPE_VIDEO);
        } else {
            builder.clearVideoSizeConstraints();
            builder.setMaxVideoBitrate(Integer.MAX_VALUE);
            TrackSelectionOverride override = findBestVideoOverride(qualityHeight);
            builder.clearOverridesOfType(C.TRACK_TYPE_VIDEO);
            if (override != null) {
                builder.setOverrideForType(override);
            }
        }
    }

    private void updateQualityBadge() {
        if (tvQualityBadge == null) return;
        if (isWebViewMode) {
            tvQualityBadge.setVisibility(View.GONE);
            return;
        }
        tvQualityBadge.setVisibility(View.VISIBLE);
        if (player != null && player.getVideoSize().height > 0) {
            int h = player.getVideoSize().height;
            tvQualityBadge.setText(h + "p" + (qualityHeight == 0 ? " • AUTO" : ""));
        } else if (qualityHeight == 0) {
            tvQualityBadge.setText("AUTO");
        } else {
            tvQualityBadge.setText("…");
        }
    }

    private void showAudioTrackDialog() {
        if (isWebViewMode) {
            Toast.makeText(this, "Trang web không hỗ trợ chuyển luồng âm thanh rời!", Toast.LENGTH_LONG).show();
            return;
        }
        if (player == null) {
            Toast.makeText(this, "Trình phát chưa sẵn sàng!", Toast.LENGTH_SHORT).show();
            return;
        }

        List<String> labels = new ArrayList<>();
        final List<Runnable> actions = new ArrayList<>();

        if (activeAlternateAudioUrl != null) {
            labels.add("Âm thanh gốc");
            actions.add(this::switchToOriginalAudio);
        } else {
            // Adaptive bitrate variants of the same audio rendition are one menu item.
            java.util.Set<String> shownTracks = new java.util.HashSet<>();
            for (Tracks.Group group : player.getCurrentTracks().getGroups()) {
                if (group.getType() != C.TRACK_TYPE_AUDIO) continue;
                for (int i = 0; i < group.length; i++) {
                    if (!group.isTrackSupported(i)) continue;
                    androidx.media3.common.Format format = group.getTrackFormat(i);
                    String key = MediaTracks.clean(format.language) + "|" + MediaTracks.clean(format.label)
                            + "|" + format.roleFlags + "|" + format.channelCount;
                    if (!shownTracks.add(key)) continue;
                    String name = trackLabel(format, "Âm thanh gốc");
                    final int index = i;
                    labels.add((group.isTrackSelected(i) ? "✓ " : "") + name);
                    actions.add(() -> selectEmbeddedAudio(group, index, name));
                }
            }
        }

        if (audioTracksList != null && !audioTracksList.isEmpty()) {
            for (AudioTrackOption audio : audioTracksList) {
                final AudioTrackOption a = audio;
                // Language-only legacy records already appear as embedded tracks above.
                if (MediaTracks.clean(a.getUrl()).isEmpty()) continue;
                labels.add((a.getUrl().equals(activeAlternateAudioUrl) ? "✓ " : "") + a.getName());
                actions.add(() -> {
                    if (a.getUrl() != null && !a.getUrl().trim().isEmpty()) {
                        switchToAlternateAudioUrl(a.getUrl(), a.getName());
                    } else if (a.getLanguage() != null && !a.getLanguage().isEmpty()) {
                        changeAudioTrack(a.getName(), a.getLanguage());
                    } else {
                        Toast.makeText(this, "Luồng này chưa có URL!", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }

        String[] names = labels.toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle("Chọn luồng âm thanh (Multi-Audio Dub)")
                .setItems(names, (dialog, which) -> actions.get(which).run())
                .setNegativeButton("Hủy", null)
                .show();
    }

    @OptIn(markerClass = UnstableApi.class)
    private void selectEmbeddedAudio(Tracks.Group group, int trackIndex, String label) {
        if (player == null) return;
        selectedAudioKey = trackKey(group.getTrackFormat(trackIndex));
        TrackSelectionParameters params = player.getTrackSelectionParameters()
                .buildUpon()
                .setPreferredAudioLanguage(null)
                .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                .setOverrideForType(new TrackSelectionOverride(group.getMediaTrackGroup(), trackIndex))
                .build();
        player.setTrackSelectionParameters(params);
        Toast.makeText(this, "Đã chuyển âm thanh: " + label, Toast.LENGTH_SHORT).show();
    }

    private void switchToOriginalAudio() {
        if (player != null && videoUrl != null && !videoUrl.trim().isEmpty()) {
            activeAlternateAudioUrl = null;
            activeAlternateLabel = null;
            selectedAudioKey = null;
            currentMediaUrl = videoUrl;

            TrackSelectionParameters parameters = player.getTrackSelectionParameters()
                    .buildUpon()
                    .setPreferredAudioLanguage(null)
                    .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                    .build();
            player.setTrackSelectionParameters(parameters);

            long currentPos = player.getCurrentPosition();
            playDirectMediaSourceUrl(videoUrl, currentPos, player.getPlayWhenReady());
            Toast.makeText(this, "Đã chuyển về Âm thanh gốc", Toast.LENGTH_SHORT).show();
        }
    }

    @OptIn(markerClass = UnstableApi.class)
    private void switchToAlternateAudioUrl(String alternateUrl, String label) {
        if (player == null || alternateUrl == null || alternateUrl.trim().isEmpty()) return;

        long currentPos = player.getCurrentPosition();
        boolean playWhenReady = player.getPlayWhenReady();

        activeAlternateAudioUrl = alternateUrl;
        activeAlternateLabel = label;
        selectedAudioKey = null;
        currentMediaUrl = videoUrl;

        DefaultMediaSourceFactory mediaSourceFactory = new DefaultMediaSourceFactory(this);

        // Keep the picture and subtitles from this episode. Take only audio from the dub,
        // including when the admin supplies a video file or a URL with no extension.
        MediaSource videoSource = new FilteringMediaSource(
                buildVideoSource(mediaSourceFactory, videoUrl),
                new java.util.HashSet<>(java.util.Arrays.asList(C.TRACK_TYPE_VIDEO, C.TRACK_TYPE_TEXT)));
        MediaSource audioSource = new FilteringMediaSource(mediaSourceFactory.createMediaSource(
                MediaItem.fromUri(alternateUrl)), C.TRACK_TYPE_AUDIO);
        MediaSource finalSource = new MergingMediaSource(true, false, videoSource, audioSource);

        TrackSelectionParameters.Builder builder = player.getTrackSelectionParameters().buildUpon();
        builder.clearOverridesOfType(C.TRACK_TYPE_AUDIO).clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .clearOverridesOfType(C.TRACK_TYPE_VIDEO).setPreferredAudioLanguage(null);
        applySubtitlePreference(builder);
        player.setTrackSelectionParameters(builder.build());

        player.setMediaSource(finalSource);
        player.prepare();
        player.seekTo(currentPos);
        player.setPlayWhenReady(playWhenReady);

        Toast.makeText(this, "Đã chuyển luồng âm thanh: " + label, Toast.LENGTH_SHORT).show();
    }

    private MediaItem buildVideoItem(String targetUrl) {
        List<MediaItem.SubtitleConfiguration> subtitleConfigs = new ArrayList<>();
        if (subtitleTracksList != null && !subtitleTracksList.isEmpty()) {
            for (SubtitleOption sub : subtitleTracksList) {
                if (sub.getSubtitleUrl() != null && !sub.getSubtitleUrl().trim().isEmpty()) {
                    String subUrl = sub.getSubtitleUrl().trim();
                    String mimeType = MediaTracks.isSrt(subUrl)
                            ? MimeTypes.APPLICATION_SUBRIP : MimeTypes.TEXT_VTT;

                    MediaItem.SubtitleConfiguration config = new MediaItem.SubtitleConfiguration.Builder(Uri.parse(subUrl))
                            .setMimeType(mimeType)
                            .setId("external:" + subUrl)
                            .setLabel(sub.getName())
                            .setLanguage(sub.getLanguage())
                            .build();
                    subtitleConfigs.add(config);
                }
            }
        }

        return new MediaItem.Builder()
                .setUri(Uri.parse(targetUrl))
                .setSubtitleConfigurations(subtitleConfigs)
                .build();
    }

    @OptIn(markerClass = UnstableApi.class)
    private MediaSource buildVideoSource(DefaultMediaSourceFactory factory, String targetUrl) {
        return factory.createMediaSource(buildVideoItem(targetUrl));
    }

    private void applySubtitlePreference(TrackSelectionParameters.Builder builder) {
        if (subtitlesEnabled) {
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false);
            if (preferredSubtitleLang != null && !preferredSubtitleLang.isEmpty()) {
                builder.setPreferredTextLanguage(preferredSubtitleLang);
            }
        } else {
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true);
        }
    }

    @OptIn(markerClass = UnstableApi.class)
    private void playDirectMediaSourceUrl(String targetUrl, long seekPos, boolean playWhenReady) {
        if (player == null) return;

        DefaultMediaSourceFactory mediaSourceFactory = new DefaultMediaSourceFactory(this);
        MediaSource videoSource = buildVideoSource(mediaSourceFactory, targetUrl);

        TrackSelectionParameters.Builder builder = player.getTrackSelectionParameters().buildUpon();
        builder.clearOverridesOfType(C.TRACK_TYPE_AUDIO).clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .clearOverridesOfType(C.TRACK_TYPE_VIDEO);
        applySubtitlePreference(builder);
        player.setTrackSelectionParameters(builder.build());

        player.setMediaSource(videoSource);
        player.prepare();
        if (seekPos > 0) {
            player.seekTo(seekPos);
        }
        player.setPlayWhenReady(playWhenReady);
    }

    private void changeAudioTrack(String trackLabel, String langCode) {
        if (player != null) {
            TrackSelectionParameters parameters = player.getTrackSelectionParameters()
                    .buildUpon()
                    .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                    .setPreferredAudioLanguage(langCode)
                    .build();
            player.setTrackSelectionParameters(parameters);
            Toast.makeText(this, "Luồng âm thanh: " + trackLabel, Toast.LENGTH_SHORT).show();
        }
    }

    private void showSubtitleDialog() {
        if (isWebViewMode) {
            Toast.makeText(this, "Trang web xử lý phụ đề CC trực tiếp trong trình phát!", Toast.LENGTH_SHORT).show();
            return;
        }

        List<String> namesList = new ArrayList<>();
        final List<Runnable> actionsList = new ArrayList<>();

        namesList.add(!subtitlesEnabled ? "✓ Tắt phụ đề" : "Tắt phụ đề");
        actionsList.add(this::disableSubtitles);

        if (player != null) {
            for (Tracks.Group group : player.getCurrentTracks().getGroups()) {
                if (group.getType() != C.TRACK_TYPE_TEXT) continue;
                for (int i = 0; i < group.length; i++) {
                    if (!group.isTrackSupported(i)) continue;
                    String lang = group.getTrackFormat(i).language;
                    String label = group.getTrackFormat(i).label;
                    String name = label != null && !label.isEmpty() ? label
                            : (lang != null && !lang.isEmpty() ? "Phụ đề " + lang : "Phụ đề nhúng");
                    final Tracks.Group g = group;
                    final int idx = i;
                    final String lCode = lang;
                    final String shownName = name;

                    boolean active = subtitlesEnabled && group.isTrackSelected(i);
                    namesList.add((active ? "✓ " : "") + shownName);
                    actionsList.add(() -> selectEmbeddedSubtitle(g, idx, lCode, shownName));
                }
            }
        }

        String[] names = namesList.toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle("Chọn phụ đề rời đồng bộ (CC)")
                .setItems(names, (dialog, which) -> actionsList.get(which).run())
                .setNegativeButton("Hủy", null)
                .show();
    }

    @OptIn(markerClass = UnstableApi.class)
    private void selectEmbeddedSubtitle(Tracks.Group group, int trackIndex, String langCode, String label) {
        subtitlesEnabled = true;
        if (langCode != null) preferredSubtitleLang = langCode;

        if (player == null) return;

        selectedSubtitleKey = trackKey(group.getTrackFormat(trackIndex));

        TrackSelectionParameters params = player.getTrackSelectionParameters()
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .setOverrideForType(new TrackSelectionOverride(group.getMediaTrackGroup(), trackIndex))
                .build();
        player.setTrackSelectionParameters(params);
        Toast.makeText(this, "Đã bật phụ đề: " + label, Toast.LENGTH_SHORT).show();
    }

    private void disableSubtitles() {
        subtitlesEnabled = false;
        selectedSubtitleKey = null;
        if (player != null) {
            TrackSelectionParameters parameters = player.getTrackSelectionParameters()
                    .buildUpon()
                    .setPreferredTextLanguage(null)
                    .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    .build();
            player.setTrackSelectionParameters(parameters);
            Toast.makeText(this, "Đã tắt phụ đề CC", Toast.LENGTH_SHORT).show();
        }
    }

    private String trackLabel(androidx.media3.common.Format format, String fallback) {
        if (!MediaTracks.clean(format.label).isEmpty()) return format.label;
        return MediaTracks.clean(format.language).isEmpty() ? fallback : format.language;
    }

    private String trackKey(androidx.media3.common.Format format) {
        return MediaTracks.stableTrackId(format.id) + "|" + MediaTracks.clean(format.language)
                + "|" + MediaTracks.clean(format.label);
    }

    private void restoreTrackChoices(Tracks tracks) {
        if (player == null) return;
        TrackSelectionParameters.Builder builder = player.getTrackSelectionParameters().buildUpon();
        for (Tracks.Group group : tracks.getGroups()) {
            String selected = group.getType() == C.TRACK_TYPE_TEXT && subtitlesEnabled
                    ? selectedSubtitleKey : group.getType() == C.TRACK_TYPE_AUDIO ? selectedAudioKey : null;
            if (selected == null) continue;
            for (int i = 0; i < group.length; i++) {
                if (group.isTrackSupported(i) && selected.equals(trackKey(group.getTrackFormat(i)))) {
                    builder.setOverrideForType(new TrackSelectionOverride(group.getMediaTrackGroup(), i));
                    break;
                }
            }
        }
        TrackSelectionParameters updated = builder.build();
        if (!updated.equals(player.getTrackSelectionParameters())) player.setTrackSelectionParameters(updated);
    }

    private void showSpeedDialog() {
        String[] speedOptions = {"0.5x (Chậm)", "0.75x (Chậm vừa)", "1.0x (Mặc định)", "1.5x (Nhanh)", "2.0x (Nhanh 2x)"};
        float[] speedValues = {0.5f, 0.75f, 1.0f, 1.5f, 2.0f};

        new AlertDialog.Builder(this)
                .setTitle("Chọn tốc độ phát video")
                .setItems(speedOptions, (dialog, which) -> changePlaybackSpeed(which, speedOptions[which], speedValues[which]))
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void changePlaybackSpeed(int optionIndex, String speedLabel, float speedValue) {
        if (isWebViewMode) {
            if (wvYouTubePlayer != null) {
                wvYouTubePlayer.evaluateJavascript("if(player && player.setPlaybackRate){ player.setPlaybackRate(" + speedValue + "); } else { document.querySelector('video').playbackRate = " + speedValue + "; }", null);
            }
        } else {
            if (player != null) {
                PlaybackParameters param = new PlaybackParameters(speedValue);
                player.setPlaybackParameters(param);
            }
        }
        Toast.makeText(this, "Tốc độ phát: " + speedLabel, Toast.LENGTH_SHORT).show();
    }

    private void showBuffering(boolean show) {
        if (progressBuffering != null) {
            progressBuffering.setVisibility(show && !isWebViewMode ? View.VISIBLE : View.GONE);
        }
    }

    private void playYouTubeVideo(String youtubeId) {
        playerView.setVisibility(View.GONE);
        wvYouTubePlayer.setVisibility(View.VISIBLE);
        if (tvQualityBadge != null) tvQualityBadge.setVisibility(View.GONE);
        showBuffering(true);
        layoutTopBar.setVisibility(View.GONE);
        findViewById(R.id.webPlayerActions).setVisibility(View.VISIBLE);
        YouTubeEmbed.load(wvYouTubePlayer, youtubeId, false, new YouTubeEmbed.Listener() {
            @Override public void onLoading(boolean loading) { showBuffering(loading); }
            @Override public void onError(String message) {
                if (isFinishing() || isDestroyed()) return;
                new AlertDialog.Builder(PlayerActivity.this).setTitle("Không phát được YouTube")
                        .setMessage(message)
                        .setPositiveButton("Thử lại", (dialog, which) -> playYouTubeVideo(youtubeId))
                        .setNegativeButton("Đóng", null).show();
            }
        });
    }

    private void playWebVideo(String url) {
        playerView.setVisibility(View.GONE);
        wvYouTubePlayer.setVisibility(View.VISIBLE);
        if (tvQualityBadge != null) tvQualityBadge.setVisibility(View.GONE);
        showBuffering(true);

        WebSettings webSettings = wvYouTubePlayer.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setMediaPlaybackRequiresUserGesture(false);
        webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);
        webSettings.setUserAgentString("Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");

        if (Build.VERSION.SDK_INT >= 21) {
            webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }

        wvYouTubePlayer.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                showBuffering(false);
            }
        });
        wvYouTubePlayer.setWebChromeClient(new WebChromeClient());
        wvYouTubePlayer.loadUrl(url);

        if (layoutTopBar != null) {
            layoutTopBar.setVisibility(View.VISIBLE);
            layoutTopBar.bringToFront();
        }
    }

    @OptIn(markerClass = UnstableApi.class)
    private void playDirectMediaVideo() {
        wvYouTubePlayer.setVisibility(View.GONE);
        playerView.setVisibility(View.VISIBLE);

        if (player == null) {
            trackSelector = new DefaultTrackSelector(this);

            // Cấu hình LoadControl chuẩn ổn định bộ đệm không làm nén/méo tiếng
            DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                            15000, // Min buffer: 15s (nhẹ RAM, không gây nghẽn mạng như mức 50s mặc định)
                            30000, // Max buffer: 30s
                            1500,  // Buffer để bắt đầu phát: 1.5s (bấm là chạy nhanh)
                            3000   // Buffer sau khi mạng lag: 3.0s (đủ dày để không bị khựng liên tục)
                    )
                    .setPrioritizeTimeOverSizeThresholds(true)
                    .build();

            DefaultRenderersFactory renderersFactory = new DefaultRenderersFactory(this)
                    .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
                    .setEnableDecoderFallback(true)
                    .setEnableAudioFloatOutput(false)
                    .setEnableAudioTrackPlaybackParams(false);

            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build();

            player = new ExoPlayer.Builder(this)
                    .setTrackSelector(trackSelector)
                    .setLoadControl(loadControl)
                    .setRenderersFactory(renderersFactory)
                    .setSeekBackIncrementMs(10000)
                    .setSeekForwardIncrementMs(10000)
                    .setAudioAttributes(audioAttributes, true)
                    .setHandleAudioBecomingNoisy(true)
                    .build();
            player.setVolume(resumeVolume);
            player.setPlaybackParameters(resumePlaybackParameters);
            if (sbExoVolume != null) sbExoVolume.setProgress(Math.round(resumeVolume * 100f));
            playerView.setPlayer(player);

            mediaSession = new MediaSession.Builder(this, player)
                    .setId(java.util.UUID.randomUUID().toString()).build();

            player.addListener(new Player.Listener() {
                @Override
                public void onPlayerError(@NonNull PlaybackException error) {
                    showBuffering(false);
                    String msg = "Không thể phát video!";
                    Throwable cause = error.getCause();
                    if (cause != null && cause.getMessage() != null) {
                        msg += " (" + cause.getMessage() + ")";
                    } else if (error.getMessage() != null) {
                        msg += " (" + error.getMessage() + ")";
                    }
                    Toast.makeText(PlayerActivity.this, msg, Toast.LENGTH_LONG).show();
                    if (layoutTopBar != null) {
                        layoutTopBar.setVisibility(View.VISIBLE);
                        layoutTopBar.bringToFront();
                    }
                }

                @Override
                public void onPlaybackStateChanged(int state) {
                    if (state == Player.STATE_BUFFERING) {
                        showBuffering(true);
                    } else {
                        showBuffering(false);
                    }
                }

                @Override
                public void onVideoSizeChanged(@NonNull VideoSize videoSize) {
                    updateQualityBadge();
                    updatePipParams();
                }

                @Override
                public void onTracksChanged(@NonNull Tracks tracks) {
                    restoreTrackChoices(tracks);
                    if (qualityHeight != 0 && player != null) {
                        TrackSelectionOverride override = findBestVideoOverride(qualityHeight);
                        if (override != null) {
                            TrackSelectionParameters params = player.getTrackSelectionParameters()
                                    .buildUpon()
                                    .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                                    .setOverrideForType(override)
                                    .build();
                            player.setTrackSelectionParameters(params);
                        }
                    }
                    updateQualityBadge();
                }

                @Override
                public void onIsPlayingChanged(boolean isPlaying) {
                    playerView.setKeepScreenOn(isPlaying);
                    updatePipParams();
                    updatePlayPauseIcon();
                }
            });

            updateVolumeIcon();

            if (activeAlternateAudioUrl != null) {
                switchToAlternateAudioUrl(activeAlternateAudioUrl,
                        activeAlternateLabel != null ? activeAlternateLabel : "Dub");
                if (resumePosition > 0) player.seekTo(resumePosition);
                player.setPlayWhenReady(resumePlayWhenReady);
            } else {
                playDirectMediaSourceUrl(videoUrl, resumePosition, resumePlayWhenReady);
            }
        }
    }

    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.O)
    private boolean supportsPip() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && getPackageManager().hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE);
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private PictureInPictureParams buildPipParams() {
        float ratio = 16f / 9f;
        if (player != null) {
            VideoSize size = player.getVideoSize();
            if (size.width > 0 && size.height > 0) {
                ratio = size.width * size.pixelWidthHeightRatio / size.height;
            }
        }
        ratio = Math.max(1f / 2.39f, Math.min(2.39f, ratio));
        PictureInPictureParams.Builder builder = new PictureInPictureParams.Builder()
                .setAspectRatio(new Rational((int) Math.ceil(ratio * 10000), 10000));
        Rect bounds = new Rect();
        if (framePlayerContainer.getGlobalVisibleRect(bounds)) builder.setSourceRectHint(bounds);
        if (!isWebViewMode) {
            RemoteAction action = createPlayPauseAction();
            if (action != null) builder.setActions(Collections.singletonList(action));
        }
        return builder.build();
    }

    private void enterPipMode() {
        if (!supportsPip() || isInPictureInPictureMode()) return;
        try {
            if (!enterPictureInPictureMode(buildPipParams())) {
                Toast.makeText(this, "Không thể mở PiP. Kiểm tra quyền PiP của ứng dụng.", Toast.LENGTH_SHORT).show();
            }
        } catch (IllegalStateException | IllegalArgumentException error) {
            Toast.makeText(this, "PiP hiện không khả dụng trên thiết bị này.", Toast.LENGTH_SHORT).show();
        }
    }

    private void updatePipParams() {
        if (supportsPip() && isInPictureInPictureMode()) {
            setPictureInPictureParams(buildPipParams());
        }
    }

    private RemoteAction createPlayPauseAction() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            boolean isPlaying = player != null && player.isPlaying();
            int iconResId = isPlaying ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play;
            String titleText = isPlaying ? "Pause" : "Play";

            Intent intent = new Intent(ACTION_MEDIA_CONTROL).setPackage(getPackageName());
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    this, REQUEST_CODE, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            Icon icon = Icon.createWithResource(this, iconResId);
            return new RemoteAction(icon, titleText, titleText, pendingIntent);
        }
        return null;
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if ((player != null && player.isPlaying()) || isWebViewMode) {
                enterPipMode();
            }
        }
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        if (isInPictureInPictureMode) {
            findViewById(R.id.webPlayerActions).setVisibility(View.GONE);
            setPlayerExpanded(true);
            layoutTopBar.setVisibility(View.GONE);
            scrollViewMovieDetail.setVisibility(View.GONE);
            playerView.setUseController(false);
        } else {
            setPlayerExpanded(isFullscreen);
            layoutTopBar.setVisibility(isWebViewMode ? View.GONE : View.VISIBLE);
            findViewById(R.id.webPlayerActions).setVisibility(isWebViewMode ? View.VISIBLE : View.GONE);
            if (!isFullscreen) {
                scrollViewMovieDetail.setVisibility(View.VISIBLE);
            }
            playerView.setUseController(true);

            if (layoutTopBar != null) layoutTopBar.bringToFront();
        }
        ViewCompat.requestApplyInsets(findViewById(R.id.playerContainer));
    }

    private void releasePlayer() {
        if (mediaSession != null) {
            mediaSession.release();
            mediaSession = null;
        }
        if (player != null) {
            try {
                resumePosition = player.getCurrentPosition();
                resumePlayWhenReady = player.getPlayWhenReady();
                resumeVolume = player.getVolume();
                resumePlaybackParameters = player.getPlaybackParameters();
            } catch (Exception ignored) {}
            player.release();
            player = null;
            trackSelector = null;
            if (playerView != null) playerView.setPlayer(null);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!isWebViewMode && player == null && videoUrl != null && !videoUrl.isEmpty()) {
            playDirectMediaVideo();
        }
        if (isWebViewMode && wvYouTubePlayer != null) {
            wvYouTubePlayer.onResume();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        // PiP remains started while visible; onStop means playback is no longer visible.
        if (isWebViewMode && wvYouTubePlayer != null) {
            wvYouTubePlayer.evaluateJavascript("if(window.player&&player.pauseVideo)player.pauseVideo();", null);
            wvYouTubePlayer.onPause();
        } else {
            releasePlayer();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(broadcastReceiver);
        } catch (Exception e) {
            e.printStackTrace();
        }
        releasePlayer();
        if (wvYouTubePlayer != null) {
            YouTubeEmbed.release(wvYouTubePlayer);
            wvYouTubePlayer = null;
        }
    }
}
