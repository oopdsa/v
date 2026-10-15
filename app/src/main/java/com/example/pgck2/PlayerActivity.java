package com.example.pgck2;

import android.app.DownloadManager;
import android.app.PendingIntent;
import android.app.PictureInPictureParams;
import android.app.RemoteAction;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Rational;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.MergingMediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.session.MediaSession;
import androidx.media3.ui.PlayerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * PlayerActivity hiển thị Màn hình Chi Tiết & Trình phát video đa năng (ExoPlayer + YouTube WebView).
 * Hỗ trợ hiển thị động danh sách luồng lồng tiếng (Multi-Audio Dubs) và phụ đề rời (Subtitles CC)
 * do Admin khởi tạo trực tiếp cho từng bộ phim.
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
    private View layoutBottomControls;
    private NestedScrollView scrollViewMovieDetail;
    private TextView tvDetailTitle;
    private TextView tvDetailGenres;
    private TextView tvDetailDesc;

    private ImageButton btnExitPlayer;
    private ImageButton btnDownloadVideo;
    private ImageButton btnPip;
    private ImageButton btnRewind10;
    private ImageButton btnForward10;
    private ImageButton btnSettings;
    private ImageButton btnQuality;
    private ImageButton btnMaximize;
    private TextView tvPlayerVideoTitle;

    // Các biến trạng thái logic
    private ExoPlayer player;
    private MediaSession mediaSession;
    private String videoUrl;
    private String videoTitle;
    private String videoDesc;
    private String videoGenres;
    private boolean isWebViewMode = false;
    private boolean isFullscreen = false;

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
                    for (int i = 0; i < audioNames.size(); i++) {
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
                    for (int i = 0; i < subNames.size(); i++) {
                        subtitleTracksList.add(new SubtitleOption(subNames.get(i), subLangs.get(i), subUrls.get(i)));
                    }
                }
            }
        }

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
        layoutBottomControls = findViewById(R.id.layoutBottomControls);
        scrollViewMovieDetail = findViewById(R.id.scrollViewMovieDetail);
        tvDetailTitle = findViewById(R.id.tvDetailTitle);
        tvDetailGenres = findViewById(R.id.tvDetailGenres);
        tvDetailDesc = findViewById(R.id.tvDetailDesc);

        btnExitPlayer = findViewById(R.id.btnExitPlayer);
        btnDownloadVideo = findViewById(R.id.btnDownloadVideo);
        btnPip = findViewById(R.id.btnPip);
        btnRewind10 = findViewById(R.id.btnRewind10);
        btnForward10 = findViewById(R.id.btnForward10);
        btnSettings = findViewById(R.id.btnSettings);
        btnQuality = findViewById(R.id.btnQuality);
        btnMaximize = findViewById(R.id.btnMaximize);
        tvPlayerVideoTitle = findViewById(R.id.tvPlayerVideoTitle);

        if (layoutTopBar != null) layoutTopBar.bringToFront();
        if (layoutBottomControls != null) layoutBottomControls.bringToFront();
    }

    private void setupControlListeners() {
        btnExitPlayer.setOnClickListener(v -> finish());

        if (btnDownloadVideo != null) {
            btnDownloadVideo.setOnClickListener(v -> downloadVideo());
        }

        if (btnRewind10 != null) {
            btnRewind10.setOnClickListener(v -> seekVideoRelative(-10));
        }

        if (btnForward10 != null) {
            btnForward10.setOnClickListener(v -> seekVideoRelative(10));
        }

        if (btnSettings != null) {
            btnSettings.setOnClickListener(v -> showSettingsDialog());
        }

        if (btnQuality != null) {
            btnQuality.setOnClickListener(v -> showResolutionDialog());
        }

        if (btnMaximize != null) {
            btnMaximize.setOnClickListener(v -> toggleFullscreenMode());
        }

        if (btnPip != null) {
            btnPip.setOnClickListener(v -> enterPipMode());
        }

        playerView.setControllerVisibilityListener((PlayerView.ControllerVisibilityListener) visibility -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode()) {
                return;
            }
            if (layoutTopBar != null) {
                layoutTopBar.setVisibility(visibility);
                if (visibility == View.VISIBLE) layoutTopBar.bringToFront();
            }
            if (layoutBottomControls != null) {
                layoutBottomControls.setVisibility(visibility);
                if (visibility == View.VISIBLE) layoutBottomControls.bringToFront();
            }
        });
    }

    private void toggleFullscreenMode() {
        isFullscreen = !isFullscreen;
        ViewGroup.LayoutParams params = framePlayerContainer.getLayoutParams();

        if (isFullscreen) {
            params.height = ViewGroup.LayoutParams.MATCH_PARENT;
            framePlayerContainer.setLayoutParams(params);
            scrollViewMovieDetail.setVisibility(View.GONE);
            Toast.makeText(this, "Toàn Màn Hình", Toast.LENGTH_SHORT).show();
        } else {
            int defaultHeightPx = (int) (230 * getResources().getDisplayMetrics().density);
            params.height = defaultHeightPx;
            framePlayerContainer.setLayoutParams(params);
            scrollViewMovieDetail.setVisibility(View.VISIBLE);
            Toast.makeText(this, "Khung Chi Tiết", Toast.LENGTH_SHORT).show();
        }

        if (layoutTopBar != null) layoutTopBar.bringToFront();
        if (layoutBottomControls != null) layoutBottomControls.bringToFront();
    }

    private void showSettingsDialog() {
        String[] options = {"💬 Phụ Đề Rời (Subtitles CC)", "🎙️ Ngôn Ngữ Lồng Tiếng (Multi-Audio Dub)", "⚡ Tốc Độ Phát Video (Playback Speed)"};
        new AlertDialog.Builder(this)
                .setTitle("Cài Đặt Trình Phát Video")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        showSubtitleDialog();
                    } else if (which == 1) {
                        showAudioTrackDialog();
                    } else {
                        showSpeedDialog();
                    }
                })
                .setNegativeButton("Đóng", null)
                .show();
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
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(videoUrl));
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

        Video tempVideo = new Video();
        tempVideo.setVideoUrl(videoUrl);

        if (tempVideo.isYouTubeVideo()) {
            isWebViewMode = true;
            playYouTubeVideo(tempVideo.getYouTubeVideoId());
        } else if (videoUrl.matches(".*\\.(mp4|m3u8|webm|mkv)(\\?.*)?$")) {
            isWebViewMode = false;
            playDirectMediaVideo();
        } else {
            isWebViewMode = true;
            playWebVideo(videoUrl);
        }
    }

    private void seekVideoRelative(int secondsDelta) {
        if (isWebViewMode) {
            wvYouTubePlayer.evaluateJavascript("seekRelative(" + secondsDelta + ");", null);
        } else {
            if (player != null) {
                long currentPos = player.getCurrentPosition();
                long targetPos = currentPos + (secondsDelta * 1000L);
                if (targetPos < 0) targetPos = 0;
                long duration = player.getDuration();
                if (duration > 0 && targetPos > duration) targetPos = duration;

                player.seekTo(targetPos);
            }
        }
        String message = secondsDelta > 0 ? "Tua tới " + secondsDelta + "s" : "Tua lùi " + Math.abs(secondsDelta) + "s";
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void showResolutionDialog() {
        String[] qualities = {"Tự động (Auto Adaptive ABR)", "1080p (Full HD)", "720p (HD 4G Tối ưu)", "480p (SD)", "360p (Thấp)"};
        new AlertDialog.Builder(this)
                .setTitle("Chọn độ phân giải HD (Adaptive Bitrate)")
                .setItems(qualities, (dialog, which) -> changeVideoResolution(which, qualities[which]))
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void changeVideoResolution(int optionIndex, String optionLabel) {
        if (isWebViewMode) {
            String ytQuality = "auto";
            switch (optionIndex) {
                case 1: ytQuality = "hd1080"; break;
                case 2: ytQuality = "hd720"; break;
                case 3: ytQuality = "large"; break;
                case 4: ytQuality = "medium"; break;
            }
            wvYouTubePlayer.evaluateJavascript("setPlaybackQuality('" + ytQuality + "');", null);
            Toast.makeText(this, "WebView: Đã gửi lệnh chuyển phân giải", Toast.LENGTH_SHORT).show();
        } else {
            if (player != null) {
                TrackSelectionParameters.Builder builder = player.getTrackSelectionParameters().buildUpon();
                switch (optionIndex) {
                    case 0:
                        builder.clearVideoSizeConstraints();
                        builder.setMaxVideoBitrate(Integer.MAX_VALUE);
                        break;
                    case 1:
                        builder.setMaxVideoSize(1920, 1080);
                        builder.setMaxVideoBitrate(5000000);
                        break;
                    case 2:
                        builder.setMaxVideoSize(1280, 720);
                        builder.setMaxVideoBitrate(2500000);
                        break;
                    case 3:
                        builder.setMaxVideoSize(854, 480);
                        builder.setMaxVideoBitrate(1000000);
                        break;
                    case 4:
                        builder.setMaxVideoSize(640, 360);
                        builder.setMaxVideoBitrate(500000);
                        break;
                }
                player.setTrackSelectionParameters(builder.build());
            }
            Toast.makeText(this, "Đã chuyển độ phân giải: " + optionLabel, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Hiển thị danh sách Luồng Lồng Tiếng (Dubbing) đăng ký bởi Admin.
     * Tùy chọn 0 BẮT BUỘC LUÔN LÀ: "🔊 Âm thanh gốc (Original Audio)" để quay về luồng gốc!
     */
    private void showAudioTrackDialog() {
        if (isWebViewMode) {
            Toast.makeText(this, "Trang web không hỗ trợ chuyển luồng âm thanh rời!", Toast.LENGTH_LONG).show();
            return;
        }

        List<String> namesList = new ArrayList<>();
        namesList.add("🔊 Âm thanh gốc (Original / Default)"); // Tùy chọn 0: Âm thanh gốc!

        if (audioTracksList != null && !audioTracksList.isEmpty()) {
            for (AudioTrackOption audio : audioTracksList) {
                namesList.add(audio.getName());
            }
        }

        String[] names = namesList.toArray(new String[0]);

        new AlertDialog.Builder(this)
                .setTitle("Chọn luồng âm thanh lồng tiếng (Multi-Audio)")
                .setItems(names, (dialog, which) -> {
                    if (which == 0) {
                        // Bấm chọn Âm thanh gốc -> Chuyển về video MP4/HLS gốc
                        switchToOriginalAudio();
                    } else if (audioTracksList != null && !audioTracksList.isEmpty()) {
                        AudioTrackOption selected = audioTracksList.get(which - 1);
                        if (selected.getUrl() != null && !selected.getUrl().trim().isEmpty()) {
                            switchToAlternateAudioUrl(selected.getUrl(), selected.getName());
                        } else {
                            changeAudioTrack(selected.getName(), selected.getLanguage());
                        }
                    }
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    /**
     * Quay trở về nguồn Video & Âm Thanh Gốc ban đầu mà không bị đen màn hình.
     */
    private void switchToOriginalAudio() {
        if (player != null && videoUrl != null && !videoUrl.trim().isEmpty()) {
            long currentPos = player.getCurrentPosition();
            boolean isPlaying = player.isPlaying();

            TrackSelectionParameters parameters = player.getTrackSelectionParameters()
                    .buildUpon()
                    .setPreferredAudioLanguage(null)
                    .build();
            player.setTrackSelectionParameters(parameters);

            playDirectMediaSourceUrl(videoUrl, currentPos, isPlaying);
            Toast.makeText(this, "Đã chuyển về Âm thanh gốc", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Chuyển sang luồng lồng tiếng thay thế (Xử lý thông minh không làm đen màn hình).
     */
    @androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
    private void switchToAlternateAudioUrl(String alternateUrl, String label) {
        if (player == null || alternateUrl == null || alternateUrl.trim().isEmpty()) return;

        long currentPos = player.getCurrentPosition();
        boolean isPlaying = player.isPlaying();

        // Kiểm tra xem alternateUrl là file âm thanh rời (.mp3, .m4a, .aac) hay là video lồng tiếng đầy đủ (.mp4, .m3u8)
        boolean isAudioOnlyFile = alternateUrl.matches(".*\\.(mp3|m4a|aac|wav|ogg)(\\?.*)?$");

        DefaultDataSource.Factory dataSourceFactory = new DefaultDataSource.Factory(this);

        MediaSource finalSource;
        if (isAudioOnlyFile) {
            // Trường hợp 1: Admin đăng ký file nhạc MP3/AAC lồng tiếng rời -> Ép GỘP Khung Hình Video Gốc + Âm Thanh MP3
            MediaItem videoItem = new MediaItem.Builder().setUri(Uri.parse(videoUrl)).build();
            MediaItem audioItem = new MediaItem.Builder().setUri(Uri.parse(alternateUrl)).build();

            MediaSource videoSource = new ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(videoItem);
            MediaSource audioSource = new ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(audioItem);

            finalSource = new MergingMediaSource(videoSource, audioSource);
        } else {
            // Trường hợp 2: Admin đăng ký một file Video MP4/HLS lồng tiếng đầy đủ -> Tải luồng video lồng tiếng
            MediaItem videoItem = new MediaItem.Builder().setUri(Uri.parse(alternateUrl)).build();
            finalSource = new ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(videoItem);
        }

        player.setMediaSource(finalSource);
        player.prepare();
        player.seekTo(currentPos);
        player.setPlayWhenReady(isPlaying);

        Toast.makeText(this, "Đã chuyển luồng âm thanh: " + label, Toast.LENGTH_SHORT).show();
    }

    @androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
    private void playDirectMediaSourceUrl(String targetUrl, long seekPos, boolean playWhenReady) {
        if (player == null) return;

        DefaultDataSource.Factory dataSourceFactory = new DefaultDataSource.Factory(this);

        // Đóng gói Subtitle configurations
        List<MediaItem.SubtitleConfiguration> subtitleConfigs = new ArrayList<>();
        if (!subtitleTracksList.isEmpty()) {
            for (SubtitleOption sub : subtitleTracksList) {
                if (sub.getSubtitleUrl() != null && !sub.getSubtitleUrl().trim().isEmpty()) {
                    MediaItem.SubtitleConfiguration config = new MediaItem.SubtitleConfiguration.Builder(Uri.parse(sub.getSubtitleUrl()))
                            .setMimeType(MimeTypes.TEXT_VTT)
                            .setLanguage(sub.getLanguage())
                            .setSelectionFlags(sub.getLanguage().equalsIgnoreCase("vi") ? C.SELECTION_FLAG_DEFAULT : 0)
                            .build();
                    subtitleConfigs.add(config);
                }
            }
        }

        MediaItem videoItem = new MediaItem.Builder()
                .setUri(Uri.parse(targetUrl))
                .setSubtitleConfigurations(subtitleConfigs)
                .build();

        MediaSource videoSource = new ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(videoItem);

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
                    .setPreferredAudioLanguage(langCode)
                    .build();
            player.setTrackSelectionParameters(parameters);
            Toast.makeText(this, "Luồng âm thanh: " + trackLabel, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Hiển thị danh sách Phụ Đề Rời CC do Admin đăng ký cho phim này.
     */
    private void showSubtitleDialog() {
        if (isWebViewMode) {
            Toast.makeText(this, "Trang web xử lý phụ đề CC trực tiếp trong trình phát!", Toast.LENGTH_SHORT).show();
            return;
        }

        if (subtitleTracksList != null && !subtitleTracksList.isEmpty()) {
            String[] names = new String[subtitleTracksList.size() + 1];
            names[0] = "Tắt phụ đề (CC Off)";
            for (int i = 0; i < subtitleTracksList.size(); i++) {
                names[i + 1] = subtitleTracksList.get(i).getName();
            }
            new AlertDialog.Builder(this)
                    .setTitle("Chọn phụ đề rời đồng bộ (CC)")
                    .setItems(names, (dialog, which) -> {
                        if (which == 0) {
                            disableSubtitles();
                        } else {
                            SubtitleOption selected = subtitleTracksList.get(which - 1);
                            enableSubtitles(selected.getLanguage(), selected.getName());
                        }
                    })
                    .setNegativeButton("Hủy", null)
                    .show();
        } else {
            String[] subOptions = {"Tắt phụ đề (CC Off)"};

            new AlertDialog.Builder(this)
                    .setTitle("Chọn phụ đề rời đồng bộ (CC)")
                    .setItems(subOptions, (dialog, which) -> disableSubtitles())
                    .setNegativeButton("Hủy", null)
                    .show();
        }
    }

    private void disableSubtitles() {
        if (player != null) {
            TrackSelectionParameters parameters = player.getTrackSelectionParameters()
                    .buildUpon()
                    .setPreferredTextLanguage(null)
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    .build();
            player.setTrackSelectionParameters(parameters);
            Toast.makeText(this, "Đã tắt phụ đề CC", Toast.LENGTH_SHORT).show();
        }
    }

    private void enableSubtitles(String langCode, String langLabel) {
        if (player != null) {
            TrackSelectionParameters parameters = player.getTrackSelectionParameters()
                    .buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .setPreferredTextLanguage(langCode)
                    .build();
            player.setTrackSelectionParameters(parameters);
            Toast.makeText(this, "Đã bật phụ đề: " + langLabel, Toast.LENGTH_SHORT).show();
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

    private void playYouTubeVideo(String youtubeId) {
        playerView.setVisibility(View.GONE);
        wvYouTubePlayer.setVisibility(View.VISIBLE);

        WebSettings webSettings = wvYouTubePlayer.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setMediaPlaybackRequiresUserGesture(false);
        webSettings.setUserAgentString("Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
        if (Build.VERSION.SDK_INT >= 21) {
            webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }

        wvYouTubePlayer.setWebViewClient(new WebViewClient());
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

        WebSettings webSettings = wvYouTubePlayer.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setMediaPlaybackRequiresUserGesture(false);
        webSettings.setUserAgentString("Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");

        if (Build.VERSION.SDK_INT >= 21) {
            webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }

        wvYouTubePlayer.setWebViewClient(new WebViewClient());
        wvYouTubePlayer.setWebChromeClient(new WebChromeClient());
        wvYouTubePlayer.loadUrl(url);

        if (layoutTopBar != null) {
            layoutTopBar.setVisibility(View.VISIBLE);
            layoutTopBar.bringToFront();
        }
    }

    @androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
    private void playDirectMediaVideo() {
        wvYouTubePlayer.setVisibility(View.GONE);
        playerView.setVisibility(View.VISIBLE);

        if (player == null) {
            player = new ExoPlayer.Builder(this).build();
            playerView.setPlayer(player);

            mediaSession = new MediaSession.Builder(this, player).build();

            player.addListener(new Player.Listener() {
                @Override
                public void onPlayerError(@NonNull PlaybackException error) {
                    Toast.makeText(PlayerActivity.this, "Không thể phát video!", Toast.LENGTH_LONG).show();
                    if (layoutTopBar != null) {
                        layoutTopBar.setVisibility(View.VISIBLE);
                        layoutTopBar.bringToFront();
                    }
                }

                @Override
                public void onIsPlayingChanged(boolean isPlaying) {
                    updatePipParams();
                }
            });

            playDirectMediaSourceUrl(videoUrl, 0, true);
        }
    }

    // =========================================================================
    // XỬ LÝ CHẾ ĐỘ PICTURE-IN-PICTURE (PiP)
    // =========================================================================

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
            if (layoutBottomControls != null) layoutBottomControls.setVisibility(View.GONE);
            scrollViewMovieDetail.setVisibility(View.GONE);
            playerView.setUseController(false);
        } else {
            layoutTopBar.setVisibility(View.VISIBLE);
            if (layoutBottomControls != null) layoutBottomControls.setVisibility(View.VISIBLE);
            if (!isFullscreen) {
                scrollViewMovieDetail.setVisibility(View.VISIBLE);
            }
            playerView.setUseController(true);

            if (layoutTopBar != null) layoutTopBar.bringToFront();
            if (layoutBottomControls != null) layoutBottomControls.bringToFront();
        }
    }

    private void releasePlayer() {
        if (mediaSession != null) {
            mediaSession.release();
            mediaSession = null;
        }
        if (player != null) {
            player.release();
            player = null;
            playerView.setPlayer(null);
        }
        if (wvYouTubePlayer != null) {
            wvYouTubePlayer.loadUrl("about:blank");
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !isInPictureInPictureMode()) {
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
    }
}
