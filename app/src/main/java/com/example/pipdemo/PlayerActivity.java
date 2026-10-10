package com.example.pipdemo;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.app.PictureInPictureParams;
import android.app.RemoteAction;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.util.Rational;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import java.util.Collections;

public class PlayerActivity extends AppCompatActivity {

    private PlayerView playerView;
    private ExoPlayer player;
    private LinearLayout layoutDetails;
    private TextView tvTitle, tvDesc;
    private Button btnPip;

    private String videoUrl, title, desc;
    private static final String ACTION_MEDIA_CONTROL = "media_control_action";
    private static final int REQUEST_CODE = 100;

    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || !ACTION_MEDIA_CONTROL.equals(intent.getAction())) {
                return;
            }
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

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        videoUrl = getIntent().getStringExtra("video_url");
        title = getIntent().getStringExtra("title");
        desc = getIntent().getStringExtra("desc");

        playerView = findViewById(R.id.playerView);
        layoutDetails = findViewById(R.id.layoutDetails);
        tvTitle = findViewById(R.id.tvPlayerTitle);
        tvDesc = findViewById(R.id.tvPlayerDesc);
        btnPip = findViewById(R.id.btnPip);

        tvTitle.setText(title);
        tvDesc.setText(desc);

        setupPlayer();

        btnPip.setOnClickListener(v -> enterPipMode());

        // Đăng ký BroadcastReceiver an toàn cho mọi phiên bản Android
        IntentFilter filter = new IntentFilter(ACTION_MEDIA_CONTROL);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(broadcastReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(broadcastReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        }
    }

    private void setupPlayer() {
        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);

        MediaItem mediaItem = MediaItem.fromUri(videoUrl);
        player.setMediaItem(mediaItem);
        player.prepare();
        player.play();

        player.addListener(new Player.Listener() {
            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                updatePipParams();
            }
        });
    }

    private void enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Rational aspectRatio = new Rational(16, 9);
            PictureInPictureParams.Builder builder = new PictureInPictureParams.Builder()
                    .setAspectRatio(aspectRatio);

            builder.setActions(Collections.singletonList(createPlayPauseAction()));

            enterPictureInPictureMode(builder.build());
        }
    }

    private void updatePipParams() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isInPictureInPictureMode()) {
            Rational aspectRatio = new Rational(16, 9);
            PictureInPictureParams.Builder builder = new PictureInPictureParams.Builder()
                    .setAspectRatio(aspectRatio);

            builder.setActions(Collections.singletonList(createPlayPauseAction()));

            setPictureInPictureParams(builder.build());
        }
    }

    private RemoteAction createPlayPauseAction() {
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

    @Override
    @androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        if (isInPictureInPictureMode) {
            layoutDetails.setVisibility(View.GONE);
            playerView.hideController();
        } else {
            layoutDetails.setVisibility(View.VISIBLE);
            playerView.showController();
        }
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (player != null && player.isPlaying()) {
                enterPipMode();
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
        if (player != null) {
            player.release();
            player = null;
        }
    }
}