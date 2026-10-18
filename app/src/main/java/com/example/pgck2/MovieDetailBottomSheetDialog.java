package com.example.pgck2;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

/**
 * BottomSheetDialogFragment hiển thị Chi Tiết Phim chuẩn Netflix Style:
 * - Cho phép vuốt kéo lên/xuống để xem toàn bộ thông tin phim (Draggable Sheet).
 * - Tự động phát Trailer Preview (chỉ tự chạy lặp lại, không cho người dùng bấm can thiệp).
 * - Nút "▶ Phát" mở tập đầu tiên theo số tập.
 * - Danh sách nút bấm Chọn Tập Phim (Tập 1, Tập 2, Tập 3...).
 */
public class MovieDetailBottomSheetDialog extends BottomSheetDialogFragment {

    private final Video video;
    private ExoPlayer previewPlayer;
    private PlayerView playerViewPreview;
    private WebView wvYouTubePreview;

    public MovieDetailBottomSheetDialog(Video video) {
        this.video = video;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_movie_detail, container, false);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (wvYouTubePreview != null) wvYouTubePreview.onResume();
        BottomSheetDialog dialog = (BottomSheetDialog) getDialog();
        if (dialog != null) {
            View bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
                // Cho phép kéo vuốt mở rộng toàn màn hình hoặc thu nhỏ nửa màn hình
                behavior.setState(BottomSheetBehavior.STATE_HALF_EXPANDED);
                behavior.setSkipCollapsed(false);
            }
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ImageButton btnCloseDetail = view.findViewById(R.id.btnCloseDetail);
        playerViewPreview = view.findViewById(R.id.playerViewPreview);
        wvYouTubePreview = view.findViewById(R.id.wvYouTubePreview);
        FrameLayout framePlayerPreview = view.findViewById(R.id.framePlayerPreview);

        TextView tvDetailTitle = view.findViewById(R.id.tvDetailTitle);
        TextView tvReleaseYear = view.findViewById(R.id.tvReleaseYear);
        TextView tvSeasonsOrEpisodes = view.findViewById(R.id.tvSeasonsOrEpisodes);
        TextView tvDetailDescription = view.findViewById(R.id.tvDetailDescription);

        MaterialButton btnPlayMovie = view.findViewById(R.id.btnPlayMovie);
        LinearLayout layoutEpisodesSection = view.findViewById(R.id.layoutEpisodesSection);
        LinearLayout layoutEpisodesContainer = view.findViewById(R.id.layoutEpisodesContainer);

        View btnMyList = view.findViewById(R.id.btnMyList);
        View btnRate = view.findViewById(R.id.btnRate);
        View btnShare = view.findViewById(R.id.btnShare);

        // 1. Cập nhật thông tin tiêu đề và năm phát hành
        if (video != null) {
            tvDetailTitle.setText(video.getTitle());
            tvReleaseYear.setText(video.getReleaseYear() + " (" + video.getReleaseMonth() + ")");
            tvDetailDescription.setText(video.getDescription() != null && !video.getDescription().trim().isEmpty() ? video.getDescription() : "Chưa có nội dung mô tả cho phim này.");

            List<Episode> episodes = video.getEpisodes();
            if (episodes != null && !episodes.isEmpty()) {
                tvSeasonsOrEpisodes.setText(episodes.size() + " Tập");
            } else {
                tvSeasonsOrEpisodes.setText("Chưa có tập phim");
            }

            // 2. Tạo danh sách Nút Chọn Tập Phim (Tập 1, Tập 2, Tập 3...)
            setupEpisodesList(view.getContext(), layoutEpisodesSection, layoutEpisodesContainer, episodes);

            // 3. Tự động phát Trailer Preview
            setupTrailerPreview(view.getContext());
        }

        Episode first = EpisodePlayback.firstEpisode(video.getEpisodes());
        btnPlayMovie.setText(first == null ? "Chưa có tập phim" : "Xem tập " + first.getEpisodeNumber());
        btnPlayMovie.setEnabled(first != null);
        btnPlayMovie.setOnClickListener(v -> {
            if (first != null) playEpisodeOrMovie(view.getContext(), first);
        });
        MaterialButton btnTrailer = view.findViewById(R.id.btnWatchTrailer);
        btnTrailer.setVisibility(video.getVideoUrl() == null || video.getVideoUrl().trim().isEmpty()
                ? View.GONE : View.VISIBLE);
        btnTrailer.setOnClickListener(v -> playEpisodeOrMovie(view.getContext(), null));
        // Nút Đóng modal
        btnCloseDetail.setOnClickListener(v -> dismiss());

        if (btnMyList != null) {
            btnMyList.setOnClickListener(v -> Toast.makeText(getContext(), "Đã thêm vào Danh Sách của bạn!", Toast.LENGTH_SHORT).show());
        }
        if (btnRate != null) {
            btnRate.setOnClickListener(v -> Toast.makeText(getContext(), "Cảm ơn bạn đã đánh giá 👍!", Toast.LENGTH_SHORT).show());
        }
        if (btnShare != null) {
            btnShare.setOnClickListener(v -> Toast.makeText(getContext(), "Chia sẻ liên kết phim...", Toast.LENGTH_SHORT).show());
        }
    }

    /**
     * Dựng hàng nút bấm Tập Phim ("Tập 1", "Tập 2", "Tập 3"...).
     */
    private void setupEpisodesList(Context context, LinearLayout layoutSection, LinearLayout container, List<Episode> episodes) {
        if (container == null) return;
        container.removeAllViews();

        if (episodes == null || episodes.isEmpty()) {
            if (layoutSection != null) layoutSection.setVisibility(View.GONE);
            return;
        }

        if (layoutSection != null) layoutSection.setVisibility(View.VISIBLE);

        List<Episode> sorted = EpisodePlayback.ordered(episodes);
        for (Episode ep : sorted) {
            MaterialButton btnEp = new MaterialButton(context, null, com.google.android.material.R.attr.materialButtonStyle);
            btnEp.setText("Tập " + ep.getEpisodeNumber() + " · " + ep.getTitle());
            btnEp.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#2A2A38")));
            btnEp.setTextColor(android.graphics.Color.WHITE);
            btnEp.setTextSize(13);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            lp.setMargins(0, 0, 16, 0);
            btnEp.setLayoutParams(lp);

            btnEp.setOnClickListener(v -> {
                dismiss();
                playEpisodeOrMovie(context, ep);
            });

            container.addView(btnEp);
        }
    }

    /**
     * Mở PlayerActivity phát tập phim hoặc video gốc được chọn.
     * Nạp đúng luồng Dub & Sub CC riêng biệt của tập đó!
     */
    private void playEpisodeOrMovie(Context context, Episode selectedEpisode) {
        // DỪNG NGAY LẬP TỨC PREVIEW PLAYER TRƯỚC KHI MỞ MÀN HÌNH PHÁT CHÍNH
        if (previewPlayer != null) {
            previewPlayer.stop();
            previewPlayer.release();
            previewPlayer = null;
        }
        if (wvYouTubePreview != null) {
            wvYouTubePreview.loadUrl("about:blank");
        }
        if (video == null) return;

        context.startActivity(selectedEpisode == null
                ? PlaybackIntents.trailer(context, video)
                : PlaybackIntents.episode(context, video, selectedEpisode));
        dismiss();
    }
    /**
     * Tự động phát Trailer Preview ở ô xem trước.
     */
    @OptIn(markerClass = UnstableApi.class)
    private void setupTrailerPreview(Context context) {
        if (video == null || video.getVideoUrl() == null || video.getVideoUrl().isEmpty()) return;

        if (video.isYouTubeVideo()) {
            playerViewPreview.setVisibility(View.GONE);
            wvYouTubePreview.setVisibility(View.VISIBLE);

            YouTubeEmbed.load(wvYouTubePreview, video.getYouTubeVideoId(), true, new YouTubeEmbed.Listener() {
                @Override public void onLoading(boolean loading) {}
                @Override public void onError(String message) {
                    if (isAdded()) Toast.makeText(context, message, Toast.LENGTH_LONG).show();
                }
            });
        } else {
            wvYouTubePreview.setVisibility(View.GONE);
            playerViewPreview.setVisibility(View.VISIBLE);

            previewPlayer = new ExoPlayer.Builder(context).build();
            playerViewPreview.setPlayer(previewPlayer);

            MediaItem mediaItem = MediaItem.fromUri(Uri.parse(video.getVideoUrl()));
            previewPlayer.setMediaItem(mediaItem);
            previewPlayer.setRepeatMode(Player.REPEAT_MODE_ONE);
            previewPlayer.setVolume(0f);
            previewPlayer.prepare();
            previewPlayer.setPlayWhenReady(true);
        }
    }

    @Override
    public void onStop() {
        if (previewPlayer != null) previewPlayer.pause();
        if (wvYouTubePreview != null) {
            wvYouTubePreview.evaluateJavascript("if(window.player&&player.pauseVideo)player.pauseVideo();", null);
            wvYouTubePreview.onPause();
        }
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (previewPlayer != null) {
            previewPlayer.release();
            previewPlayer = null;
        }
        if (wvYouTubePreview != null) {
            YouTubeEmbed.release(wvYouTubePreview);
            wvYouTubePreview = null;
        }
    }
}
