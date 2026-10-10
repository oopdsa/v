package com.example.pgck2;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.TrackSelectionParameters;
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
 * BƯỚC 3: Lắng nghe sự kiện chuyển đổi trạng thái PiP để ẩn/hiện các nút điều khiển:
 *   @Override
 *   public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, Configuration newConfig) {
 *       super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
 *       if (isInPictureInPictureMode) {
 *           findViewById(R.id.layoutTopBar).setVisibility(View.GONE);
 *           findViewById(R.id.layoutSeekControls).setVisibility(View.GONE);
 *           if (playerView != null) playerView.hideController();
 *       } else {
 *           findViewById(R.id.layoutTopBar).setVisibility(View.VISIBLE);
 *           findViewById(R.id.layoutSeekControls).setVisibility(View.VISIBLE);
 *       }
 *   }
 * ===================================================================================
 */

/**
 * Activity phát video trực tuyến hỗ trợ:
 * 1. Nút Thoát video (Close button)
 * 2. Nút Tua Lùi 10 Giây (-10s)
 * 3. Nút Tua Tới 10 Giây (+10s)
 * 4. Nút Chỉnh Độ Phân Giải (Resolution Selector: 1080p, 720p, 480p, 360p, Auto)
 * 5. Trình phát kép: ExoPlayer (MP4/HLS) & WebView (YouTube Video)
 */
public class PlayerActivity extends AppCompatActivity {

    public static final String EXTRA_VIDEO_URL = "extra_video_url";
    public static final String EXTRA_VIDEO_TITLE = "extra_video_title";

    private PlayerView playerView;
    private WebView wvYouTubePlayer;
    private ImageButton btnExitPlayer;
    private ImageButton btnQuality;
    private ImageButton btnRewind10;
    private ImageButton btnForward10;
    private TextView tvPlayerVideoTitle;

    private ExoPlayer player;
    private String videoUrl;
    private String videoTitle;
    private boolean isYouTube = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        // 1. Ánh xạ các thành phần View từ layout activity_player.xml
        initViews();

        // 2. Nhận dữ liệu truyền từ Intent (videoUrl & videoTitle)
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

        // 3. Đăng ký các sự kiện nút bấm điều khiển
        setupControlListeners();

        // 4. Bắt đầu cấu hình trình phát video
        setupPlayer();
    }

    private void initViews() {
        playerView = findViewById(R.id.playerView);
        wvYouTubePlayer = findViewById(R.id.wvYouTubePlayer);
        btnExitPlayer = findViewById(R.id.btnExitPlayer);
        btnQuality = findViewById(R.id.btnQuality);
        btnRewind10 = findViewById(R.id.btnRewind10);
        btnForward10 = findViewById(R.id.btnForward10);
        tvPlayerVideoTitle = findViewById(R.id.tvPlayerVideoTitle);
    }

    /**
     * Thiết lập các sự kiện bấm nút: Thoát, Tua lùi 10s, Tua tới 10s, Chỉnh độ phân giải.
     */
    private void setupControlListeners() {
        // NÚT THOÁT VIDEO -> Đóng Activity quay về danh sách
        btnExitPlayer.setOnClickListener(v -> finish());

        // NÚT TUA LÙI 10 GIÂY (-10s)
        btnRewind10.setOnClickListener(v -> seekVideoRelative(-10));

        // NÚT TUA TỚI 10 GIÂY (+10s)
        btnForward10.setOnClickListener(v -> seekVideoRelative(10));

        // NÚT CHỈNH ĐỘ PHÂN GIẢI -> Hiển thị Dialog chọn chất lượng
        btnQuality.setOnClickListener(v -> showResolutionDialog());
    }

    /**
     * Kiểm tra loại video và kích hoạt trình phát tương ứng (ExoPlayer hay YouTube WebView).
     */
    private void setupPlayer() {
        if (videoUrl == null || videoUrl.isEmpty()) {
            Toast.makeText(this, "Đường dẫn video không hợp lệ!", Toast.LENGTH_SHORT).show();
            return;
        }

        Video tempVideo = new Video("Temp", "", videoUrl);
        if (tempVideo.isYouTubeVideo()) {
            isYouTube = true;
            playYouTubeVideo(tempVideo.getYouTubeVideoId());
        } else {
            isYouTube = false;
            playDirectMediaVideo();
        }
    }

    /**
     * Tua video tới hoặc lùi một khoảng thời gian (tính bằng giây).
     *
     * @param secondsDelta Số giây cần tua (+10 cho tới, -10 cho lùi)
     */
    private void seekVideoRelative(int secondsDelta) {
        if (isYouTube) {
            // Đối với video YouTube: Gọi hàm JavaScript seekRelative trong WebView
            wvYouTubePlayer.evaluateJavascript("seekRelative(" + secondsDelta + ");", null);
        } else {
            // Đối với ExoPlayer: Tính toán lại vị trí phát
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

    /**
     * Hiển thị Dialog cho phép người dùng chọn độ phân giải (1080p, 720p, 480p, 360p, Auto).
     */
    private void showResolutionDialog() {
        String[] qualities = {"Tự động (Auto)", "1080p (Full HD)", "720p (HD)", "480p (SD)", "360p (Thấp)"};

        new AlertDialog.Builder(this)
                .setTitle("Chọn độ phân giải video")
                .setItems(qualities, (dialog, which) -> changeVideoResolution(which, qualities[which]))
                .setNegativeButton("Hủy", null)
                .show();
    }

    /**
     * Thay đổi chất lượng/độ phân giải của video.
     *
     * @param optionIndex Vị trí lựa chọn (0: Auto, 1: 1080p, 2: 720p, 3: 480p, 4: 360p)
     * @param optionLabel Nhãn hiển thị độ phân giải
     */
    private void changeVideoResolution(int optionIndex, String optionLabel) {
        if (isYouTube) {
            // Đặt chất lượng cho YouTube WebView IFrame Player
            String ytQuality = "auto";
            switch (optionIndex) {
                case 1: ytQuality = "hd1080"; break;
                case 2: ytQuality = "hd720"; break;
                case 3: ytQuality = "large"; break;
                case 4: ytQuality = "medium"; break;
            }
            wvYouTubePlayer.evaluateJavascript("setPlaybackQuality('" + ytQuality + "');", null);
        } else {
            // Đặt chất lượng cho ExoPlayer thông qua TrackSelectionParameters
            if (player != null) {
                TrackSelectionParameters.Builder builder = player.getTrackSelectionParameters().buildUpon();
                switch (optionIndex) {
                    case 0:
                        builder.clearVideoSizeConstraints();
                        break;
                    case 1:
                        builder.setMaxVideoSize(1920, 1080);
                        break;
                    case 2:
                        builder.setMaxVideoSize(1280, 720);
                        break;
                    case 3:
                        builder.setMaxVideoSize(854, 480);
                        break;
                    case 4:
                        builder.setMaxVideoSize(640, 360);
                        break;
                }
                player.setTrackSelectionParameters(builder.build());
            }
        }

        Toast.makeText(this, "Đã chọn độ phân giải: " + optionLabel, Toast.LENGTH_SHORT).show();
    }

    /**
     * Phát video YouTube sử dụng WebView nạp IFrame Embed player hỗ trợ JavaScript API.
     */
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

        // Mã HTML nhúng IFrame YouTube với đầy đủ thuộc tính origin và JavaScript API
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

    /**
     * Phát video direct stream (MP4/HLS) sử dụng Media3 ExoPlayer.
     */
    private void playDirectMediaVideo() {
        wvYouTubePlayer.setVisibility(View.GONE);
        playerView.setVisibility(View.VISIBLE);

        if (player == null) {
            player = new ExoPlayer.Builder(this).build();
            playerView.setPlayer(player);

            // Bắt sự kiện lỗi khi nguồn video không hỗ trợ hoặc link lỗi
            player.addListener(new Player.Listener() {
                @Override
                public void onPlayerError(@NonNull PlaybackException error) {
                    Toast.makeText(PlayerActivity.this, "Không thể phát video! Vui lòng kiểm tra lại đường dẫn.", Toast.LENGTH_LONG).show();
                }
            });

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
