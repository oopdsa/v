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
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import java.util.Collections;

/**
 * PlayerActivity là Activity phát video trực tuyến đa năng trong ứng dụng PGCK2.
 * Hỗ trợ các tính năng chính:
 * 1. Trình phát kép: ExoPlayer (phát stream MP4/HLS) và WebView (phát nhúng IFrame YouTube).
 * 2. Nút Tua ±10 giây (-10s / +10s) tự động ẩn/hiện đồng bộ khi chạm màn hình.
 * 3. Nút Chỉnh Độ Phân Giải (Resolution Selector: 1080p, 720p, 480p, 360p, Tự động).
 * 4. Nút Chỉnh Tốc Độ Phát Video (PlaybackParameters: 0.5x, 0.75x, 1.0x mặc định, 1.5x, 2.0x) ở góc phải dưới.
 * 5. Nút Tải Video Phim về máy sử dụng Android DownloadManager.
 * 6. Tính năng Picture-in-Picture (PiP) tự động thu nhỏ video khi bấm Home hoặc chuyển ứng dụng.
 */
public class PlayerActivity extends AppCompatActivity {

    // Các hằng số Extra dùng để gửi/nhận dữ liệu qua Intent
    public static final String EXTRA_VIDEO_URL = "extra_video_url";       // Đường dẫn URL video
    public static final String EXTRA_VIDEO_TITLE = "extra_video_title";   // Tiêu đề phim
    public static final String EXTRA_VIDEO_DESC = "extra_video_desc";     // Nội dung mô tả phim

    // Ánh xạ các View thành phần giao diện
    private PlayerView playerView;             // Trình phát ExoPlayer cho video MP4
    private WebView wvYouTubePlayer;           // Webview phát nhúng IFrame cho video YouTube
    private RelativeLayout layoutTopBar;       // Thanh điều khiển phía trên (Header Top Bar)
    private View layoutSeekControls;           // Khung chứa 2 nút tua lùi -10s và tua tới +10s
    private ImageButton btnExitPlayer;         // Nút thoát / đóng màn hình xem phim
    private ImageButton btnDownloadVideo;      // Nút tải video về máy
    private ImageButton btnQuality;            // Nút mở Dialog chọn độ phân giải
    private ImageButton btnSpeed;              // Nút mở Dialog chọn tốc độ phát (Playback Speed)
    private ImageButton btnPip;                // Nút thu nhỏ video thành cửa sổ nổi PiP
    private ImageButton btnRewind10;           // Nút tua lùi 10 giây (-10s)
    private ImageButton btnForward10;          // Nút tua tới 10 giây (+10s)
    private TextView tvPlayerVideoTitle;       // TextView hiển thị tiêu đề phim

    // Các biến trạng thái logic
    private ExoPlayer player;                  // Đối tượng ExoPlayer Media3
    private String videoUrl;                   // URL video nhận từ Intent
    private String videoTitle;                 // Tiêu đề phim nhận từ Intent
    private boolean isYouTube = false;         // Cờ đánh dấu video hiện tại có phải là YouTube hay không

    // Hằng số đăng ký Receiver điều khiển PiP
    private static final String ACTION_MEDIA_CONTROL = "media_control_action"; // Hành động Broadcast PiP
    private static final int REQUEST_CODE = 100;                               // Mã Yêu cầu PendingIntent

    /**
     * BroadcastReceiver lắng nghe sự kiện khi người dùng bấm nút Play/Pause trên cửa sổ PiP thu nhỏ.
     */
    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || !ACTION_MEDIA_CONTROL.equals(intent.getAction())) return;
            if (player != null) {
                if (player.isPlaying()) {
                    player.pause(); // Nếu đang phát thì tạm dừng
                } else {
                    player.play();  // Nếu đang dừng thì phát tiếp
                }
                updatePipParams();  // Cập nhật lại icon Play/Pause trên nút PiP
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        // 1. Ánh xạ toàn bộ các View từ layout activity_player.xml
        initViews();

        // 2. Nhận dữ liệu truyền sang từ Intent (videoUrl, videoTitle)
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

        // 3. Đăng ký sự kiện click cho các nút bấm điều khiển
        setupControlListeners();

        // 4. Bắt đầu cấu hình trình phát video (ExoPlayer hoặc YouTube)
        setupPlayer();

        // 5. Đăng ký BroadcastReceiver hỗ trợ nút Play/Pause trên cửa sổ PiP
        IntentFilter filter = new IntentFilter(ACTION_MEDIA_CONTROL);
        ContextCompat.registerReceiver(this, broadcastReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    /**
     * Ánh xạ tất cả các View thành phần từ layout XML.
     */
    private void initViews() {
        playerView = findViewById(R.id.playerView);
        wvYouTubePlayer = findViewById(R.id.wvYouTubePlayer);
        layoutTopBar = findViewById(R.id.layoutTopBar);
        layoutSeekControls = findViewById(R.id.layoutSeekControls);
        btnExitPlayer = findViewById(R.id.btnExitPlayer);
        btnDownloadVideo = findViewById(R.id.btnDownloadVideo);
        btnQuality = findViewById(R.id.btnQuality);
        btnSpeed = findViewById(R.id.btnSpeed);
        btnPip = findViewById(R.id.btnPip);
        btnRewind10 = findViewById(R.id.btnRewind10);
        btnForward10 = findViewById(R.id.btnForward10);
        tvPlayerVideoTitle = findViewById(R.id.tvPlayerVideoTitle);

        // Mặc định ẩn nút tua và nút tốc độ phát cho tới khi người dùng chạm vào màn hình
        if (layoutSeekControls != null) {
            layoutSeekControls.setVisibility(View.GONE);
        }
        if (btnSpeed != null) {
            btnSpeed.setVisibility(View.GONE);
        }
    }

    /**
     * Đăng ký các bộ lắng nghe sự kiện bấm nút trên giao diện.
     */
    private void setupControlListeners() {
        // Nút thoát -> Đóng Activity và quay lại danh sách
        btnExitPlayer.setOnClickListener(v -> finish());

        // Nút Tải video phim về máy
        if (btnDownloadVideo != null) {
            btnDownloadVideo.setOnClickListener(v -> downloadVideo());
        }

        // Nút tua lùi 10 giây (-10s)
        if (btnRewind10 != null) {
            btnRewind10.setOnClickListener(v -> seekVideoRelative(-10));
        }

        // Nút tua tới 10 giây (+10s)
        if (btnForward10 != null) {
            btnForward10.setOnClickListener(v -> seekVideoRelative(10));
        }

        // Nút chọn độ phân giải
        btnQuality.setOnClickListener(v -> showResolutionDialog());

        // Nút chỉnh tốc độ phát ở góc phải dưới
        if (btnSpeed != null) {
            btnSpeed.setOnClickListener(v -> showSpeedDialog());
        }

        // Nút bấm kích hoạt PiP thủ công
        btnPip.setOnClickListener(v -> enterPipMode());

        // Đồng bộ ẩn/hiện cụm nút tua 10s, thanh Header và Nút Tốc độ phát theo thao tác chạm màn hình
        playerView.setControllerVisibilityListener((PlayerView.ControllerVisibilityListener) visibility -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode()) {
                return; // Nếu đang ở chế độ PiP thu nhỏ thì giữ nguyên giao diện ẩn
            }
            if (layoutSeekControls != null) {
                layoutSeekControls.setVisibility(visibility);
            }
            if (layoutTopBar != null) {
                layoutTopBar.setVisibility(visibility);
            }
            if (btnSpeed != null) {
                btnSpeed.setVisibility(visibility);
            }
        });
    }

    /**
     * Tải video phim về máy sử dụng dịch vụ hệ thống Android DownloadManager.
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
            fileName = fileName.replaceAll("[^a-zA-Z0-9._-]", "_"); // Làm sạch tên file

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

    /**
     * Kiểm tra loại video và kích hoạt trình phát tương ứng (ExoPlayer hay YouTube WebView).
     */
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
     * Hiển thị Dialog cho phép người dùng chọn độ phân giải video (1080p, 720p, 480p, 360p, Auto).
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
     * Thay đổi chất lượng/độ phân giải của video phát.
     *
     * @param optionIndex Chỉ số lựa chọn (0: Auto, 1: 1080p, 2: 720p, 3: 480p, 4: 360p)
     * @param optionLabel Nhãn chất lượng hiển thị
     */
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
                    case 0:
                        builder.clearVideoSizeConstraints(); // Bỏ giới hạn (Tự động)
                        break;
                    case 1:
                        builder.setMaxVideoSize(1920, 1080); // Giới hạn Full HD 1080p
                        break;
                    case 2:
                        builder.setMaxVideoSize(1280, 720);  // Giới hạn HD 720p
                        break;
                    case 3:
                        builder.setMaxVideoSize(854, 480);   // Giới hạn SD 480p
                        break;
                    case 4:
                        builder.setMaxVideoSize(640, 360);   // Giới hạn Thấp 360p
                        break;
                }
                player.setTrackSelectionParameters(builder.build());
            }
        }
        Toast.makeText(this, "Đã chọn chất lượng: " + optionLabel, Toast.LENGTH_SHORT).show();
    }

    /**
     * Hiển thị Dialog chọn Tốc độ phát video (0.5x, 0.75x, 1.0x mặc định, 1.5x, 2.0x).
     */
    private void showSpeedDialog() {
        String[] speedOptions = {"0.5x (Chậm)", "0.75x (Chậm vừa)", "1.0x (Mặc định)", "1.5x (Nhanh)", "2.0x (Nhanh 2x)"};
        float[] speedValues = {0.5f, 0.75f, 1.0f, 1.5f, 2.0f}; // <<<

        new AlertDialog.Builder(this)
                .setTitle("Chọn tốc độ phát video")
                .setItems(speedOptions, (dialog, which) -> changePlaybackSpeed(which, speedOptions[which], speedValues[which]))
                .setNegativeButton("Hủy", null)
                .show();
    }

    /**
     * Thay đổi tốc độ phát video sử dụng PlaybackParameters (cho ExoPlayer) hoặc JavaScript setPlaybackRate (cho YouTube).
     *
     * @param optionIndex Chỉ số lựa chọn tốc độ
     * @param speedLabel  Nhãn hiển thị tốc độ (vd: "1.5x (Nhanh)")
     * @param speedValue  Giá trị tốc độ float (vd: 0.5f, 1.0f, 1.5f, 2.0f)
     */
    private void changePlaybackSpeed(int optionIndex, String speedLabel, float speedValue) {
        if (isYouTube) {
            // Đặt tốc độ phát trên YouTube IFrame Player thông qua JavaScript API
            if (wvYouTubePlayer != null) {
                wvYouTubePlayer.evaluateJavascript("if(player && player.setPlaybackRate){ player.setPlaybackRate(" + speedValue + "); }", null);
            }
        } else {
            // Đặt tốc độ phát trên ExoPlayer bằng PlaybackParameters
            if (player != null) {
                PlaybackParameters param = new PlaybackParameters(speedValue);
                player.setPlaybackParameters(param);
            }
        }
        Toast.makeText(this, "Tốc độ phát: " + speedLabel, Toast.LENGTH_SHORT).show();
    }

    /**
     * Cấu hình và phát video YouTube trong WebView sử dụng HTML5 IFrame API.
     *
     * @param youtubeId Mã định danh video YouTube
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
     * Khởi tạo và phát video MP4/HLS bằng AndroidX Media3 ExoPlayer.
     */
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
    // XỬ LÝ CHẾ ĐỘ PICTURE-IN-PICTURE (PiP)
    // =========================================================================

    /**
     * Kích hoạt chế độ cửa sổ thu nhỏ PiP với tỷ lệ khung hình 16:9.
     */
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

    /**
     * Cập nhật các thông số và icon Play/Pause cho cửa sổ PiP thu nhỏ.
     */
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

    /**
     * Tạo nút RemoteAction Play/Pause hiển thị trên cửa sổ PiP thu nhỏ.
     */
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

    /**
     * Tự động chuyển sang chế độ PiP khi người dùng bấm phím Home hoặc thoát app.
     */
    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if ((player != null && player.isPlaying()) || isYouTube) {
                enterPipMode();
            }
        }
    }

    /**
     * Lắng nghe sự kiện khi ứng dụng chuyển đổi trạng thái vào/ra chế độ PiP.
     */
    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        if (isInPictureInPictureMode) {
            layoutTopBar.setVisibility(View.GONE);
            if (layoutSeekControls != null) layoutSeekControls.setVisibility(View.GONE);
            if (btnSpeed != null) btnSpeed.setVisibility(View.GONE);
            playerView.setUseController(false);
        } else {
            layoutTopBar.setVisibility(View.VISIBLE);
            playerView.setUseController(true);
        }
    }

    /**
     * Dừng và giải phóng tài nguyên phần cứng của ExoPlayer và WebView.
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
