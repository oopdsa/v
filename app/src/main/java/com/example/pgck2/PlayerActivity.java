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
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import java.util.Collections;

/**
 * PlayerActivity hiển thị trình phát video đa năng (ExoPlayer + YouTube WebView)
 * tích hợp nút Tua ±10s đặt ngang hàng với nút Play/Pause, Chọn Độ phân giải, Tải video và Chế độ PiP.
 */
public class PlayerActivity extends AppCompatActivity {

    public static final String EXTRA_VIDEO_URL = "extra_video_url";
    public static final String EXTRA_VIDEO_TITLE = "extra_video_title";
    public static final String EXTRA_VIDEO_DESC = "extra_video_desc";

    private PlayerView playerView;
    private WebView wvYouTubePlayer;
    private RelativeLayout layoutTopBar;
    private View layoutSeekControls;
    private ImageButton btnExitPlayer;
    private ImageButton btnDownloadVideo;
    private ImageButton btnQuality;
    private ImageButton btnPip;
    private ImageButton btnRewind10;
    private ImageButton btnForward10;
    private TextView tvPlayerVideoTitle;

    private ExoPlayer player;
    private String videoUrl;
    private String videoTitle;
    private boolean isYouTube = false;

    // Hằng số điều khiển PiP
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
            }
        }

        setupControlListeners();
        setupPlayer();

        // Đăng ký BroadcastReceiver cho nút Play/Pause trong PiP
        IntentFilter filter = new IntentFilter(ACTION_MEDIA_CONTROL);
        ContextCompat.registerReceiver(this, broadcastReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    private void initViews() {
        playerView = findViewById(R.id.playerView);
        wvYouTubePlayer = findViewById(R.id.wvYouTubePlayer);
        layoutTopBar = findViewById(R.id.layoutTopBar);
        layoutSeekControls = findViewById(R.id.layoutSeekControls);
        btnExitPlayer = findViewById(R.id.btnExitPlayer);
        btnDownloadVideo = findViewById(R.id.btnDownloadVideo);
        btnQuality = findViewById(R.id.btnQuality);
        btnPip = findViewById(R.id.btnPip);
        btnRewind10 = findViewById(R.id.btnRewind10);
        btnForward10 = findViewById(R.id.btnForward10);
        tvPlayerVideoTitle = findViewById(R.id.tvPlayerVideoTitle);

        // Mặc định ẩn nút tua cho tới khi chạm màn hình
        if (layoutSeekControls != null) {
            layoutSeekControls.setVisibility(View.GONE);
        }
    }

    private void setupControlListeners() {
        // Nút thoát
        btnExitPlayer.setOnClickListener(v -> finish());

        // Nút Tải video phim về máy
        if (btnDownloadVideo != null) {
            btnDownloadVideo.setOnClickListener(v -> downloadVideo());
        }

        // Nút tua 10 giây (-10s và +10s nằm ngang hàng với nút Play/Pause chính giữa)
        if (btnRewind10 != null) {
            btnRewind10.setOnClickListener(v -> seekVideoRelative(-10));
        }
        if (btnForward10 != null) {
            btnForward10.setOnClickListener(v -> seekVideoRelative(10));
        }

        // Nút chỉnh độ phân giải
        btnQuality.setOnClickListener(v -> showResolutionDialog());

        // Nút bấm PiP thủ công
        btnPip.setOnClickListener(v -> enterPipMode());

        // Đồng bộ ẩn/hiện điều khiển với PlayerView
        playerView.setControllerVisibilityListener((PlayerView.ControllerVisibilityListener) visibility -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode()) {
                return;
            }
            if (layoutSeekControls != null) {
                layoutSeekControls.setVisibility(visibility);
            }
            if (layoutTopBar != null) {
                layoutTopBar.setVisibility(visibility);
            }
        });
    }

    /**
     * Tải video phim về máy bằng Android DownloadManager.
     */
    private void downloadVideo() {
        if (videoUrl == null || videoUrl.isEmpty()) {
            Toast.makeText(this, "Không có URL video để tải!", Toast.LENGTH_SHORT).show();
            return;
        }

        if (isYouTube) {
            Toast.makeText(this, "Video YouTube không hỗ trợ tải trực tiếp. Vui lòng xem online!", Toast.LENGTH_LONG).show();
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
            Toast.makeText(this, "Đường dẫn video không hợp lệ!", Toast.LENGTH_SHORT).show();
            return;
        }

        Video tempVideo = new Video();
        tempVideo.setVideoUrl(videoUrl);
        if (tempVideo.isYouTubeVideo()) {
            isYouTube = true;
            playYouTubeVideo(tempVideo.getYouTubeVideoId());
        } else {
            isYouTube = false;
            playDirectMediaVideo();
        }
    }

    private void seekVideoRelative(int secondsDelta) {
        if (isYouTube) {
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
        String[] qualities = {"Tự động (Auto)", "1080p (Full HD)", "720p (HD)", "480p (SD)", "360p (Thấp)"};
        new AlertDialog.Builder(this)
                .setTitle("Chọn độ phân giải video")
                .setItems(qualities, (dialog, which) -> changeVideoResolution(which, qualities[which]))
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void changeVideoResolution(int optionIndex, String optionLabel) {
        if (isYouTube) {
            String ytQuality = "auto";
            switch (optionIndex) {
                case 1: ytQuality = "hd1080"; break;
                case 2: ytQuality = "hd720"; break;
                case 3: ytQuality = "large"; break;
                case 4: ytQuality = "medium"; break;
            }
            wvYouTubePlayer.evaluateJavascript("setPlaybackQuality('" + ytQuality + "');", null);
        } else {
            if (player != null) {
                TrackSelectionParameters.Builder builder = player.getTrackSelectionParameters().buildUpon();
                switch (optionIndex) {
                    case 0: builder.clearVideoSizeConstraints(); break;
                    case 1: builder.setMaxVideoSize(1920, 1080); break;
                    case 2: builder.setMaxVideoSize(1280, 720); break;
                    case 3: builder.setMaxVideoSize(854, 480); break;
                    case 4: builder.setMaxVideoSize(640, 360); break;
                }
                player.setTrackSelectionParameters(builder.build());
            }
        }
        Toast.makeText(this, "Đã chọn: " + optionLabel, Toast.LENGTH_SHORT).show();
    }

    private void playYouTubeVideo(String youtubeId) {
        playerView.setVisibility(View.GONE);
        wvYouTubePlayer.setVisibility(View.VISIBLE);

        WebSettings webSettings = wvYouTubePlayer.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setMediaPlaybackRequiresUserGesture(false);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);
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

    private void playDirectMediaVideo() {
        wvYouTubePlayer.setVisibility(View.GONE);
        playerView.setVisibility(View.VISIBLE);

        if (player == null) {
            player = new ExoPlayer.Builder(this).build();
            playerView.setPlayer(player);

            player.addListener(new Player.Listener() {
                @Override
                public void onPlayerError(@NonNull PlaybackException error) {
                    Toast.makeText(PlayerActivity.this, "Không thể phát video!", Toast.LENGTH_LONG).show();
                }

                @Override
                public void onIsPlayingChanged(boolean isPlaying) {
                    updatePipParams();
                }
            });

            MediaItem mediaItem = MediaItem.fromUri(Uri.parse(videoUrl));
            player.setMediaItem(mediaItem);
            player.prepare();
            player.setPlayWhenReady(true);
        }
    }

    // =========================================================================
    // XỬ LÝ PiP
    // =========================================================================

    private void enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Rational aspectRatio = new Rational(16, 9);
            PictureInPictureParams.Builder builder = new PictureInPictureParams.Builder()
                    .setAspectRatio(aspectRatio);

            if (!isYouTube) {
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

            if (!isYouTube) {
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
            if ((player != null && player.isPlaying()) || isYouTube) {
                enterPipMode();
            }
        }
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        if (isInPictureInPictureMode) {
            layoutTopBar.setVisibility(View.GONE);
            if (layoutSeekControls != null) layoutSeekControls.setVisibility(View.GONE);
            playerView.setUseController(false);
        } else {
            layoutTopBar.setVisibility(View.VISIBLE);
            playerView.setUseController(true);
        }
    }

    private void releasePlayer() {
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
