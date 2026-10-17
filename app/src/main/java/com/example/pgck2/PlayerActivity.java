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
import androidx.annotation.OptIn;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
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
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
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
 * Nâng cấp giao diện điều khiển chuẩn YouTube:
 * 1. NÚT SPEED ĐỘC LẬP: Nút đổi tốc độ phát (btnExoSpeed) nằm bên ngoài Player Bar để thao tác cực nhanh.
 * 2. CÀI ĐẶT RỌN GÀNG: Nút Settings chỉ còn chứa 2 mục chính: Phụ đề CC và Ngôn ngữ Lồng Tiếng Dub.
 * 3. THANH ÂM LƯỢNG YOUTUBE: Chỉnh âm lượng phim trực tiếp trong App không che video.
 */
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
    private String videoTitle;
    private String videoDesc;
    private String videoGenres;
    private boolean isWebViewMode = false;
    private boolean isFullscreen = false;
    private float lastNonZeroVolume = 1.0f;

    // Ghi nhớ vị trí phát để dựng lại player sau onStop
    private long resumePosition = 0;
    private boolean resumePlayWhenReady = true;

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
            playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING);

            btnCenterPlayPause = playerView.findViewById(R.id.btnExoCenterPlayPause);
            btnVolume = playerView.findViewById(R.id.btnExoVolume);
            sbExoVolume = playerView.findViewById(R.id.sbExoVolume);
            btnSettings = playerView.findViewById(R.id.btnExoSettings);
            btnSpeed = playerView.findViewById(R.id.btnExoSpeed);
            btnMaximize = playerView.findViewById(R.id.btnExoFullscreen);
        }

        if (layoutTopBar != null) layoutTopBar.bringToFront();
        updateVolumeIcon();
    }

    private void setupControlListeners() {
        btnExitPlayer.setOnClickListener(v -> finish());

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
        ViewGroup.LayoutParams params = framePlayerContainer.getLayoutParams();

        if (isFullscreen) {
            params.height = ViewGroup.LayoutParams.MATCH_PARENT;
            framePlayerContainer.setLayoutParams(params);
            scrollViewMovieDetail.setVisibility(View.GONE);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
            if (getSupportActionBar() != null) getSupportActionBar().hide();
            Toast.makeText(this, "Toàn Màn Hình", Toast.LENGTH_SHORT).show();
        } else {
            int defaultHeightPx = (int) (250 * getResources().getDisplayMetrics().density);
            params.height = defaultHeightPx;
            framePlayerContainer.setLayoutParams(params);
            scrollViewMovieDetail.setVisibility(View.VISIBLE);
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
            if (getSupportActionBar() != null) getSupportActionBar().show();
            Toast.makeText(this, "Khung Chi Tiết", Toast.LENGTH_SHORT).show();
        }

        if (layoutTopBar != null) layoutTopBar.bringToFront();
    }

    private void showSettingsDialog() {
        String[] options = {
                "💬 Phụ Đề Rời (Subtitles CC)",
                "🎙️ Ngôn Ngữ Lồng Tiếng (Multi-Audio Dub)"};
        new AlertDialog.Builder(this)
                .setTitle("Cài Đặt Cấu Hình Phim")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        showSubtitleDialog();
                    } else {
                        showAudioTrackDialog();
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
        } else if (videoUrl.matches(".*\\.(mp4|m3u8|mpd|webm|mkv)(\\?.*)?$")) {
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
                if (h > 0) {
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
        String[] qualities = {"Tự động", "1080p", "720p", "480p", "360p"};
        String[] ytValues = {"auto", "hd1080", "hd720", "large", "medium"};
        new AlertDialog.Builder(this)
                .setTitle("Chọn độ phân giải (Web)")
                .setMessage("Lưu ý: Trình phát của trang web sẽ quyết định chất lượng thực tế.")
                .setItems(qualities, (dialog, which) -> {
                    wvYouTubePlayer.evaluateJavascript("setPlaybackQuality('" + ytValues[which] + "');", null);
                    Toast.makeText(this, "Đã gửi lệnh chuyển: " + qualities[which], Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void changeVideoResolutionByHeight(int height, String label) {
        if (player == null) return;
        qualityHeight = height;

        TrackSelectionParameters.Builder builder = player.getTrackSelectionParameters().buildUpon();
        reapplyQualitySelection(builder);
        player.setTrackSelectionParameters(builder.build());
        updateQualityBadge();
        Toast.makeText(this, "Đã chuyển độ phân giải: " + label.replace("✓ ", ""), Toast.LENGTH_SHORT).show();
    }

    private void applyHeightConstraints(TrackSelectionParameters.Builder builder, int height) {
        switch (height) {
            case 1080:
                builder.setMaxVideoSize(1920, 1080);
                builder.setMaxVideoBitrate(6000000);
                break;
            case 720:
                builder.setMaxVideoSize(1280, 720);
                builder.setMaxVideoBitrate(3000000);
                break;
            case 480:
                builder.setMaxVideoSize(854, 480);
                builder.setMaxVideoBitrate(1200000);
                break;
            case 360:
                builder.setMaxVideoSize(640, 360);
                builder.setMaxVideoBitrate(700000);
                break;
            default:
                builder.setMaxVideoSize(1920, height);
                builder.setMaxVideoBitrate(Integer.MAX_VALUE);
                break;
        }
    }

    private TrackSelectionOverride findBestVideoOverride(int height) {
        Tracks.Group bestGroup = null;
        int bestIndex = -1;
        int bestHeight = -1;
        for (Tracks.Group group : player.getCurrentTracks().getGroups()) {
            if (group.getType() != C.TRACK_TYPE_VIDEO) continue;
            for (int i = 0; i < group.length; i++) {
                int h = group.getTrackFormat(i).height;
                if (h > 0 && h <= height && h > bestHeight) {
                    bestHeight = h;
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
                    if (h > 0 && (bestHeight == -1 || h < bestHeight)) {
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
            applyHeightConstraints(builder, qualityHeight);
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
        if (player != null && player.getVideoSize().height > 0 && qualityHeight == 0) {
            int h = player.getVideoSize().height;
            tvQualityBadge.setText(h + "p • AUTO");
        } else if (qualityHeight == 0) {
            tvQualityBadge.setText("AUTO");
        } else {
            tvQualityBadge.setText(qualityHeight + "p");
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

        labels.add("🔊 Âm thanh gốc (Original Video)");
        actions.add(this::switchToOriginalAudio);

        int embeddedCount = 0;
        List<Tracks.Group> audioGroups = new ArrayList<>();
        for (Tracks.Group group : player.getCurrentTracks().getGroups()) {
            if (group.getType() == C.TRACK_TYPE_AUDIO) {
                audioGroups.add(group);
                embeddedCount += group.length;
            }
        }

        if (embeddedCount > 1) {
            int trackIdx = 1;
            for (Tracks.Group group : audioGroups) {
                for (int i = 0; i < group.length; i++) {
                    String lang = group.getTrackFormat(i).language;
                    String trackLabel = group.getTrackFormat(i).label;
                    String shown = trackLabel != null && !trackLabel.isEmpty() ? trackLabel
                            : (lang != null && !lang.isEmpty() ? lang : "Track #" + trackIdx);
                    final Tracks.Group g = group;
                    final int idx = i;
                    final String name = shown;
                    labels.add("🎧 " + shown + " (Track nhúng #" + trackIdx + ")");
                    actions.add(() -> selectEmbeddedAudio(g, idx, name));
                    trackIdx++;
                }
            }
        }

        if (audioTracksList != null && !audioTracksList.isEmpty()) {
            for (AudioTrackOption audio : audioTracksList) {
                final AudioTrackOption a = audio;
                labels.add("🎙️ " + a.getName());
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
            currentMediaUrl = videoUrl;

            TrackSelectionParameters parameters = player.getTrackSelectionParameters()
                    .buildUpon()
                    .setPreferredAudioLanguage(null)
                    .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                    .build();
            player.setTrackSelectionParameters(parameters);

            long currentPos = player.getCurrentPosition();
            playDirectMediaSourceUrl(videoUrl, currentPos, true);
            Toast.makeText(this, "Đã chuyển về Âm thanh gốc", Toast.LENGTH_SHORT).show();
        }
    }

    @OptIn(markerClass = UnstableApi.class)
    private void switchToAlternateAudioUrl(String alternateUrl, String label) {
        if (player == null || alternateUrl == null || alternateUrl.trim().isEmpty()) return;

        long currentPos = player.getCurrentPosition();

        boolean isAudioOnlyFile = alternateUrl.matches(".*\\.(mp3|m4a|aac|wav|ogg)(\\?.*)?$");
        activeAlternateAudioUrl = alternateUrl;
        activeAlternateLabel = label;
        if (!isAudioOnlyFile) {
            currentMediaUrl = alternateUrl;
        }

        DefaultMediaSourceFactory mediaSourceFactory = new DefaultMediaSourceFactory(this);

        MediaSource finalSource;
        if (isAudioOnlyFile) {
            MediaSource videoSource = buildVideoSource(mediaSourceFactory, videoUrl);
            MediaItem audioItem = new MediaItem.Builder().setUri(Uri.parse(alternateUrl)).build();
            MediaSource audioSource = mediaSourceFactory.createMediaSource(audioItem);

            finalSource = new MergingMediaSource(videoSource, audioSource);
        } else {
            finalSource = buildVideoSource(mediaSourceFactory, alternateUrl);
        }

        TrackSelectionParameters.Builder builder = player.getTrackSelectionParameters().buildUpon();
        reapplyQualitySelection(builder);
        applySubtitlePreference(builder);
        player.setTrackSelectionParameters(builder.build());

        player.setMediaSource(finalSource);
        player.prepare();
        player.seekTo(currentPos);
        player.setPlayWhenReady(true);

        Toast.makeText(this, "Đã chuyển luồng âm thanh: " + label, Toast.LENGTH_SHORT).show();
    }

    private MediaItem buildVideoItem(String targetUrl) {
        List<MediaItem.SubtitleConfiguration> subtitleConfigs = new ArrayList<>();
        if (subtitleTracksList != null && !subtitleTracksList.isEmpty()) {
            for (SubtitleOption sub : subtitleTracksList) {
                if (sub.getSubtitleUrl() != null && !sub.getSubtitleUrl().trim().isEmpty()) {
                    String subUrl = sub.getSubtitleUrl().trim();
                    String mimeType = subUrl.toLowerCase().endsWith(".srt")
                            ? MimeTypes.APPLICATION_SUBRIP : MimeTypes.TEXT_VTT;

                    MediaItem.SubtitleConfiguration config = new MediaItem.SubtitleConfiguration.Builder(Uri.parse(subUrl))
                            .setMimeType(mimeType)
                            .setLanguage(sub.getLanguage())
                            .setSelectionFlags(sub.getLanguage().equalsIgnoreCase("vi") ? C.SELECTION_FLAG_DEFAULT : 0)
                            .build();
                    subtitleConfigs.add(config);
                }
            }
        } else {
            MediaItem.SubtitleConfiguration defaultVi = new MediaItem.SubtitleConfiguration.Builder(
                    Uri.parse("https://storage.googleapis.com/exoplayer-test-media-1/webvtt/numeric-subtitles-en.vtt"))
                    .setMimeType(MimeTypes.TEXT_VTT)
                    .setLanguage("vi")
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    .build();
            subtitleConfigs.add(defaultVi);
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
        reapplyQualitySelection(builder);
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

        namesList.add(subtitlesEnabled ? "✓ Đang bật — Bấm để Tắt phụ đề (CC Off)" : "Tắt phụ đề (CC Off)");
        actionsList.add(this::disableSubtitles);

        if (subtitleTracksList != null && !subtitleTracksList.isEmpty()) {
            for (SubtitleOption sub : subtitleTracksList) {
                final SubtitleOption s = sub;
                boolean active = subtitlesEnabled && s.getLanguage().equalsIgnoreCase(preferredSubtitleLang);
                namesList.add((active ? "✓ " : "") + s.getName() + " (CC)");
                actionsList.add(() -> selectSubtitleOption(s));
            }
        }

        if (player != null) {
            for (Tracks.Group group : player.getCurrentTracks().getGroups()) {
                if (group.getType() != C.TRACK_TYPE_TEXT) continue;
                for (int i = 0; i < group.length; i++) {
                    String lang = group.getTrackFormat(i).language;
                    String label = group.getTrackFormat(i).label;
                    String name = label != null && !label.isEmpty() ? label
                            : (lang != null && !lang.isEmpty() ? "Phụ đề " + lang : "Phụ đề nhúng");
                    final Tracks.Group g = group;
                    final int idx = i;
                    final String lCode = lang;
                    final String shownName = name;

                    boolean active = subtitlesEnabled && group.isTrackSelected(i);
                    namesList.add((active ? "✓ " : "") + shownName + " (trong video)");
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
    private void selectSubtitleOption(SubtitleOption selected) {
        subtitlesEnabled = true;
        preferredSubtitleLang = selected.getLanguage();

        if (player == null) return;

        TrackSelectionParameters.Builder builder = player.getTrackSelectionParameters().buildUpon();
        builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false);
        builder.setPreferredTextLanguage(selected.getLanguage());

        Tracks.Group textGroup = null;
        int trackIndex = -1;

        for (Tracks.Group group : player.getCurrentTracks().getGroups()) {
            if (group.getType() != C.TRACK_TYPE_TEXT) continue;
            for (int i = 0; i < group.length; i++) {
                String lang = group.getTrackFormat(i).language;
                if (lang != null && lang.equalsIgnoreCase(selected.getLanguage())) {
                    textGroup = group;
                    trackIndex = i;
                    break;
                }
            }
        }

        if (textGroup != null && trackIndex >= 0) {
            builder.clearOverridesOfType(C.TRACK_TYPE_TEXT);
            builder.setOverrideForType(new TrackSelectionOverride(textGroup.getMediaTrackGroup(), trackIndex));
        }

        player.setTrackSelectionParameters(builder.build());
        Toast.makeText(this, "Đã bật phụ đề: " + selected.getName(), Toast.LENGTH_SHORT).show();
    }

    @OptIn(markerClass = UnstableApi.class)
    private void selectEmbeddedSubtitle(Tracks.Group group, int trackIndex, String langCode, String label) {
        subtitlesEnabled = true;
        if (langCode != null) preferredSubtitleLang = langCode;

        if (player == null) return;

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
            progressBuffering.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    private void playYouTubeVideo(String youtubeId) {
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

        String iframeHtml = "<!DOCTYPE html><html><head>"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no\">"
                + "<style>body{margin:0;padding:0;background-color:#000;display:flex;justify-content:center;align-items:center;height:100vh;}"
                + "iframe{width:100%;height:100%;border:none;}</style>"
                + "<script src=\"https://www.youtube.com/iframe_api\"></script>"
                + "<script>"
                + "var player;"
                + "function onYouTubeIframeAPIReady(){ player = new YT.Player('ytplayer'); }"
                + "function seekRelative(sec){ if(player && player.getCurrentTime){ player.seekTo(player.getCurrentTime() + sec, true); } }"
                + "function setPlaybackQuality(q){ if(player && player.setPlaybackQuality){ player.setPlaybackQuality(q); } }"
                + "</script>"
                + "</head><body>"
                + "<iframe id=\"ytplayer\" src=\"https://www.youtube.com/embed/" + youtubeId + "?enablejsapi=1&autoplay=1&playsinline=1&origin=https://www.youtube.com&widget_referrer=https://www.youtube.com\" "
                + "allow=\"autoplay; encrypted-media; picture-in-picture\" allowfullscreen></iframe>"
                + "</body></html>";

        wvYouTubePlayer.loadDataWithBaseURL("https://www.youtube.com", iframeHtml, "text/html", "utf-8", null);
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

            DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                    .setBufferDurationsMs(10000, 30000, 1000, 2000)
                    .setPrioritizeTimeOverSizeThresholds(true)
                    .build();

            DefaultRenderersFactory renderersFactory = new DefaultRenderersFactory(this)
                    .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
                    .setEnableDecoderFallback(true);

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
                    .setWakeMode(C.WAKE_MODE_NETWORK)
                    .build();
            playerView.setPlayer(player);

            mediaSession = new MediaSession.Builder(this, player).build();

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
                }

                @Override
                public void onTracksChanged(@NonNull Tracks tracks) {
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
                    updatePipParams();
                    updatePlayPauseIcon();
                }
            });

            updateVolumeIcon();

            String target = (activeAlternateAudioUrl != null && !isAudioOnlyUrl(activeAlternateAudioUrl))
                    ? activeAlternateAudioUrl
                    : videoUrl;
            if (activeAlternateAudioUrl != null && isAudioOnlyUrl(activeAlternateAudioUrl)) {
                switchToAlternateAudioUrl(activeAlternateAudioUrl,
                        activeAlternateLabel != null ? activeAlternateLabel : "Dub");
                if (resumePosition > 0) player.seekTo(resumePosition);
                player.setPlayWhenReady(resumePlayWhenReady);
            } else {
                playDirectMediaSourceUrl(target, resumePosition, resumePlayWhenReady);
            }
        }
    }

    private boolean isAudioOnlyUrl(String url) {
        return url != null && url.matches(".*\\.(mp3|m4a|aac|wav|ogg)(\\?.*)?$");
    }

    private void enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Rational aspectRatio = new Rational(16, 9);
            PictureInPictureParams.Builder builder = new PictureInPictureParams.Builder()
                    .setAspectRatio(aspectRatio);

            if (!isWebViewMode) {
                RemoteAction action = createPlayPauseAction();
                if (action != null) {
                    builder.setActions(Collections.singletonList(action));
                }
            }
            enterPictureInPictureMode(builder.build());
        }
    }

    private void updatePipParams() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isInPictureInPictureMode()) {
            Rational aspectRatio = new Rational(16, 9);
            PictureInPictureParams.Builder builder = new PictureInPictureParams.Builder()
                    .setAspectRatio(aspectRatio);

            if (!isWebViewMode) {
                RemoteAction action = createPlayPauseAction();
                if (action != null) {
                    builder.setActions(Collections.singletonList(action));
                }
            }
            setPictureInPictureParams(builder.build());
        }
    }

    private RemoteAction createPlayPauseAction() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            boolean isPlaying = player != null && player.isPlaying();
            int iconResId = isPlaying ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play;
            String titleText = isPlaying ? "Pause" : "Play";

            Intent intent = new Intent(ACTION_MEDIA_CONTROL);
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
            layoutTopBar.setVisibility(View.GONE);
            scrollViewMovieDetail.setVisibility(View.GONE);
            playerView.setUseController(false);
        } else {
            layoutTopBar.setVisibility(View.VISIBLE);
            if (!isFullscreen) {
                scrollViewMovieDetail.setVisibility(View.VISIBLE);
            }
            playerView.setUseController(true);

            if (layoutTopBar != null) layoutTopBar.bringToFront();
        }
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !isInPictureInPictureMode()) {
            if (isWebViewMode && wvYouTubePlayer != null) {
                wvYouTubePlayer.onPause();
            } else {
                releasePlayer();
            }
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
            wvYouTubePlayer.loadUrl("about:blank");
            wvYouTubePlayer.destroy();
        }
    }
}
