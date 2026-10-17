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
 * - Nút "▶ Phát" tự động mở tập mới nhất vừa được thêm gần đây.
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

        // Ngăn chặn sự kiện chạm bấm trực tiếp lên khung Preview (Chỉ cho phép chạy tự động)
        if (framePlayerPreview != null) {
            framePlayerPreview.setOnTouchListener((v, event) -> true);
        }

        // 1. Cập nhật thông tin tiêu đề và năm phát hành
        if (video != null) {
            tvDetailTitle.setText(video.getTitle());
            tvReleaseYear.setText(video.getReleaseYear() + " (" + video.getReleaseMonth() + ")");
            tvDetailDescription.setText(video.getDescription() != null && !video.getDescription().trim().isEmpty() ? video.getDescription() : "Chưa có nội dung mô tả cho phim này.");

            List<Episode> episodes = video.getEpisodes();
            if (episodes != null && !episodes.isEmpty()) {
                tvSeasonsOrEpisodes.setText(episodes.size() + " Tập");
            } else {
                tvSeasonsOrEpisodes.setText("Phim Lẻ / HD");
            }

            // 2. Tạo danh sách Nút Chọn Tập Phim (Tập 1, Tập 2, Tập 3...)
            setupEpisodesList(view.getContext(), layoutEpisodesSection, layoutEpisodesContainer, episodes);

            // 3. Tự động phát Trailer Preview
            setupTrailerPreview(view.getContext());
        }

        // 4. Sự kiện bấm Nút Phát Phim -> Tự động phát Tập Mới Nhất vừa được thêm!
        btnPlayMovie.setOnClickListener(v -> {
            dismiss();
            Episode latestEpisode = null;
            if (video != null && video.getEpisodes() != null && !video.getEpisodes().isEmpty()) {
                // Lấy tập phim mới nhất (Tập cuối cùng trong danh sách)
                latestEpisode = video.getEpisodes().get(video.getEpisodes().size() - 1);
            }
            playEpisodeOrMovie(view.getContext(), latestEpisode);
        });

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

        for (Episode ep : episodes) {
            MaterialButton btnEp = new MaterialButton(context, null, com.google.android.material.R.attr.materialButtonStyle);
            btnEp.setText(ep.getTitle());
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

        String targetUrl = video.getVideoUrl();
        String targetTitle = video.getTitle();

        List<AudioTrackOption> audioList = video.getAudioTracks();
        List<SubtitleOption> subList = video.getSubtitleTracks();

        if (selectedEpisode != null) {
            targetUrl = selectedEpisode.getVideoUrl();
            targetTitle = video.getTitle() + " - " + selectedEpisode.getTitle();
            if (selectedEpisode.getAudioTracks() != null && !selectedEpisode.getAudioTracks().isEmpty()) {
                audioList = selectedEpisode.getAudioTracks();
            }
            if (selectedEpisode.getSubtitleTracks() != null && !selectedEpisode.getSubtitleTracks().isEmpty()) {
                subList = selectedEpisode.getSubtitleTracks();
            }
        }

        ArrayList<String> audioNames = new ArrayList<>();
        ArrayList<String> audioLangs = new ArrayList<>();
        ArrayList<String> audioUrls = new ArrayList<>();

        if (audioList != null) {
            for (AudioTrackOption audio : audioList) {
                audioNames.add(audio.getName());
                audioLangs.add(audio.getLanguage());
                audioUrls.add(audio.getUrl());
            }
        }

        ArrayList<String> subNames = new ArrayList<>();
        ArrayList<String> subLangs = new ArrayList<>();
        ArrayList<String> subUrls = new ArrayList<>();

        if (subList != null) {
            for (SubtitleOption sub : subList) {
                subNames.add(sub.getName());
                subLangs.add(sub.getLanguage());
                subUrls.add(sub.getSubtitleUrl());
            }
        }

        Intent intent = new Intent(context, PlayerActivity.class);
        intent.putExtra(PlayerActivity.EXTRA_VIDEO_URL, targetUrl);
        intent.putExtra(PlayerActivity.EXTRA_VIDEO_TITLE, targetTitle);
        intent.putExtra(PlayerActivity.EXTRA_VIDEO_DESC, video.getDescription());
        intent.putExtra("extra_video_genres", video.getGenresString());
        intent.putStringArrayListExtra("extra_audio_names", audioNames);
        intent.putStringArrayListExtra("extra_audio_langs", audioLangs);
        intent.putStringArrayListExtra("extra_audio_urls", audioUrls);
        intent.putStringArrayListExtra("extra_sub_names", subNames);
        intent.putStringArrayListExtra("extra_sub_langs", subLangs);
        intent.putStringArrayListExtra("extra_sub_urls", subUrls);
        context.startActivity(intent);
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

            WebSettings webSettings = wvYouTubePreview.getSettings();
            webSettings.setJavaScriptEnabled(true);
            webSettings.setDomStorageEnabled(true);
            webSettings.setMediaPlaybackRequiresUserGesture(false);

            wvYouTubePreview.setWebViewClient(new WebViewClient());
            wvYouTubePreview.setWebChromeClient(new WebChromeClient());

            String iframeHtml = "<!DOCTYPE html><html><head>"
                    + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">"
                    + "<style>body{margin:0;padding:0;background-color:#000;display:flex;justify-content:center;align-items:center;height:100vh;}"
                    + "iframe{width:100%;height:100%;border:none;pointer-events:none;}</style></head><body>"
                    + "<iframe src=\"https://www.youtube.com/embed/" + video.getYouTubeVideoId() + "?autoplay=1&mute=1&playsinline=1\" allow=\"autoplay\" allowfullscreen></iframe>"
                    + "</body></html>";

            wvYouTubePreview.loadDataWithBaseURL("https://www.youtube.com", iframeHtml, "text/html", "utf-8", null);
        } else {
            wvYouTubePreview.setVisibility(View.GONE);
            playerViewPreview.setVisibility(View.VISIBLE);

            previewPlayer = new ExoPlayer.Builder(context).build();
            playerViewPreview.setPlayer(previewPlayer);

            MediaItem mediaItem = MediaItem.fromUri(Uri.parse(video.getVideoUrl()));
            previewPlayer.setMediaItem(mediaItem);
            previewPlayer.setRepeatMode(Player.REPEAT_MODE_ONE);
            previewPlayer.setVolume(0.5f);
            previewPlayer.prepare();
            previewPlayer.setPlayWhenReady(true);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (previewPlayer != null) {
            previewPlayer.release();
            previewPlayer = null;
        }
        if (wvYouTubePreview != null) {
            wvYouTubePreview.loadUrl("about:blank");
        }
    }
}
