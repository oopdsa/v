package com.example.pgck2;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageButton;

import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

/*
 * ===================================================================================
 * HƯỚNG DẪN DÀNH CHO TEAMMATE THỰC HIỆN TÍNH NĂNG PICTURE-IN-PICTURE (PiP):
 * ===================================================================================
 * Để tích hợp Picture-in-Picture (PiP) vào PlayerActivity, bạn chỉ cần thực hiện 3 bước sau:
 *
 * BƯỚC 1: Trong AndroidManifest.xml -> khai báo thuộc tính hỗ trợ PiP cho PlayerActivity:
 *   <activity
 *       android:name=".PlayerActivity"
 *       android:supportsPictureInPicture="true"
 *       android:configChanges="screenSize|smallestScreenSize|screenLayout|orientation" />
 *
 * BƯỚC 2: Gọi phương thức enterPictureInPictureMode() khi người dùng bấm phím Home/Rời app:
 *   @Override
 *   protected void onUserLeaveHint() {
 *       super.onUserLeaveHint();
 *       if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
 *           PictureInPictureParams params = new PictureInPictureParams.Builder()
 *                   .setAspectRatio(new Rational(16, 9))
 *                   .build();
 *           enterPictureInPictureMode(params);
 *       }
 *   }
 *
 * BƯỚC 3: Lắng nghe sự kiện chuyển đổi trạng thái PiP để ẩn/hiện nút Thoát (btnExitPlayer):
 *   @Override
 *   public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, Configuration newConfig) {
 *       super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
 *       if (isInPictureInPictureMode) {
 *           btnExitPlayer.setVisibility(View.GONE);
 *           if (playerView != null) playerView.hideController();
 *       } else {
 *           btnExitPlayer.setVisibility(View.VISIBLE);
 *       }
 *   }
 * ===================================================================================
 */

/**
 * Activity phát video trực tuyến hỗ trợ cả ExoPlayer (MP4/HLS) và YouTube Video (WebView IFrame).
 * Có nút Thoát video và ghi chú chi tiết hỗ trợ teammate tự tích hợp PiP về sau.
 */
public class PlayerActivity extends AppCompatActivity {

    public static final String EXTRA_VIDEO_URL = "extra_video_url";
    public static final String EXTRA_VIDEO_TITLE = "extra_video_title";

    private PlayerView playerView;
    private WebView wvYouTubePlayer;
    private ImageButton btnExitPlayer;

    private ExoPlayer player;
    private String videoUrl;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        // Ánh xạ các View từ layout
        playerView = findViewById(R.id.playerView);
        wvYouTubePlayer = findViewById(R.id.wvYouTubePlayer);
        btnExitPlayer = findViewById(R.id.btnExitPlayer);

        // 1. NÚT THOÁT VIDEO: Bấm vào sẽ đóng màn hình PlayerActivity và quay về danh sách
        btnExitPlayer.setOnClickListener(v -> finish());

        // Nhận videoUrl truyền từ Intent
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra(EXTRA_VIDEO_URL)) {
            videoUrl = intent.getStringExtra(EXTRA_VIDEO_URL);
        }

        // Bắt đầu phát phim
        setupPlayer();
    }

    /**
     * Kiểm tra loại link video (YouTube hay MP4 trực tiếp) để chọn trình phát phù hợp.
     */
    private void setupPlayer() {
        if (videoUrl == null || videoUrl.isEmpty()) {
            return;
        }

        Video tempVideo = new Video("Temp", "", videoUrl);
        if (tempVideo.isYouTubeVideo()) {
            // Trường hợp 1: Link YouTube -> Phát qua WebView IFrame Embed
            playYouTubeVideo(tempVideo.getYouTubeVideoId());
        } else {
            // Trường hợp 2: Link MP4/HLS trực tiếp -> Phát qua ExoPlayer
            playDirectMediaVideo();
        }
    }

    /**
     * Phát video YouTube sử dụng WebView nạp IFrame Embed player.
     *
     * @param youtubeId ID của video YouTube
     */
    private void playYouTubeVideo(String youtubeId) {
        // Ẩn ExoPlayer, Hiện WebView
        playerView.setVisibility(View.GONE);
        wvYouTubePlayer.setVisibility(View.VISIBLE);

        WebSettings webSettings = wvYouTubePlayer.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setMediaPlaybackRequiresUserGesture(false);

        wvYouTubePlayer.setWebViewClient(new WebViewClient());
        wvYouTubePlayer.setWebChromeClient(new WebChromeClient());

        // Tạo mã HTML chứa IFrame phát video YouTube tràn màn hình
        String iframeHtml = "<!DOCTYPE html><html><head>"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no\">"
                + "<style>body{margin:0;padding:0;background-color:#000;display:flex;justify-content:center;align-items:center;height:100vh;}"
                + "iframe{width:100%;height:100%;border:none;}</style></head><body>"
                + "<iframe src=\"https://www.youtube.com/embed/" + youtubeId + "?autoplay=1&playsinline=1\" "
                + "allow=\"autoplay; encrypted-media\" allowfullscreen></iframe>"
                + "</body></html>";

        wvYouTubePlayer.loadDataWithBaseURL("https://www.youtube.com", iframeHtml, "text/html", "utf-8", null);
    }

    /**
     * Phát video direct stream (MP4/HLS) sử dụng Media3 ExoPlayer.
     */
    private void playDirectMediaVideo() {
        wvYouTubePlayer.setVisibility(View.GONE);
        playerView.setVisibility(View.VISIBLE);

        if (player == null) {
            player = new ExoPlayer.Builder(this).build();
            playerView.setPlayer(player);

            MediaItem mediaItem = MediaItem.fromUri(Uri.parse(videoUrl));
            player.setMediaItem(mediaItem);
            player.prepare();
            player.setPlayWhenReady(true);
        }
    }

    /**
     * Dừng và giải phóng tài nguyên ExoPlayer và WebView.
     */
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
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (wvYouTubePlayer != null) {
            wvYouTubePlayer.onResume();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (wvYouTubePlayer != null) {
            wvYouTubePlayer.onPause();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        releasePlayer();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        releasePlayer();
    }
}
