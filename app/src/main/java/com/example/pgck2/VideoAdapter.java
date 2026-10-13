package com.example.pgck2;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter hiển thị danh sách video/phim trong RecyclerView.
 * Hỗ trợ 3 chế độ hiển thị: Dọc (Chủ đạo), Ngang Top 10 (Recently Added) và Lưới 3 Cột (Grid).
 */
public class VideoAdapter extends RecyclerView.Adapter<VideoAdapter.VideoViewHolder> {

    private static final int VIEW_TYPE_VERTICAL = 0;
    private static final int VIEW_TYPE_HORIZONTAL = 1;
    private static final int VIEW_TYPE_GRID = 2;

    // Interface sự kiện click xem video
    public interface OnVideoClickListener {
        void onVideoClick(Video video);
    }

    // Interface sự kiện sửa video (Admin)
    public interface OnVideoEditListener {
        void onVideoEdit(Video video, int position);
    }

    // Interface sự kiện xóa video (Admin)
    public interface OnVideoDeleteListener {
        void onVideoDelete(Video video, int position);
    }

    private final Context context;
    private List<Video> videoList;
    private boolean isAdminMode = false;
    private boolean isHorizontalMode = false; // Chế độ cuộn ngang Top 10
    private boolean isGridMode = false;       // Chế độ Lưới 3 cột (Grid)

    private final OnVideoClickListener clickListener;
    private final OnVideoEditListener editListener;
    private final OnVideoDeleteListener deleteListener;

    public VideoAdapter(Context context,
                        List<Video> videoList,
                        OnVideoClickListener clickListener,
                        OnVideoEditListener editListener,
                        OnVideoDeleteListener deleteListener) {
        this.context = context;
        this.videoList = videoList != null ? videoList : new ArrayList<>();
        this.clickListener = clickListener;
        this.editListener = editListener;
        this.deleteListener = deleteListener;
    }

    /**
     * Cập nhật trạng thái Chế độ Admin (bật/tắt nút Sửa/Xóa).
     */
    public void setAdminMode(boolean adminMode) {
        this.isAdminMode = adminMode;
        notifyDataSetChanged();
    }

    /**
     * Cập nhật Chế độ hiển thị: Ngang (Recently Added Top 10), Lưới 3 cột hoặc Dọc.
     */
    public void setDisplayMode(boolean isHorizontal, boolean isGrid) {
        this.isHorizontalMode = isHorizontal;
        this.isGridMode = isGrid;
        notifyDataSetChanged();
    }

    public void setHorizontalMode(boolean horizontalMode) {
        setDisplayMode(horizontalMode, false);
    }

    public void setGridMode(boolean gridMode) {
        setDisplayMode(false, gridMode);
    }

    /**
     * Cập nhật danh sách video khi tìm kiếm hoặc thay đổi trang dữ liệu.
     */
    public void updateVideoList(List<Video> newList) {
        this.videoList = newList != null ? newList : new ArrayList<>();
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        if (isHorizontalMode) {
            return VIEW_TYPE_HORIZONTAL;
        } else if (isGridMode) {
            return VIEW_TYPE_GRID;
        } else {
            return VIEW_TYPE_VERTICAL;
        }
    }

    @NonNull
    @Override
    public VideoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layoutId;
        if (viewType == VIEW_TYPE_HORIZONTAL) {
            layoutId = R.layout.item_video_horizontal;
        } else if (viewType == VIEW_TYPE_GRID) {
            layoutId = R.layout.item_video_grid;
        } else {
            layoutId = R.layout.item_video;
        }
        View view = LayoutInflater.from(context).inflate(layoutId, parent, false);
        return new VideoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VideoViewHolder holder, int position) {
        if (position >= videoList.size()) return;
        Video video = videoList.get(position);

        // Hiển thị tiêu đề
        holder.tvTitle.setText(video.getTitle());

        // Hiển thị danh sách thể loại phim
        String genresText = video.getGenresString();
        if (genresText != null && !genresText.trim().isEmpty() && !genresText.equals("Chưa phân loại")) {
            holder.tvGenres.setText("🏷️ " + genresText);
            holder.tvGenres.setVisibility(View.VISIBLE);
        } else {
            holder.tvGenres.setVisibility(View.GONE);
        }

        // Hiển thị mô tả ngắn (Chỉ dành cho layout dọc)
        if (holder.tvDescription != null) {
            if (video.getDescription() != null && !video.getDescription().trim().isEmpty()) {
                holder.tvDescription.setText(video.getDescription());
                holder.tvDescription.setVisibility(View.VISIBLE);
            } else {
                holder.tvDescription.setVisibility(View.GONE);
            }
        }

        // Sử dụng Glide nạp ảnh thumbnail
        Glide.with(context)
                .load(video.getThumbnailUrl())
                .placeholder(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.ic_dialog_alert)
                .into(holder.imgThumbnail);

        // Nút Admin Sửa / Xóa ẩn/hiện tùy theo isAdminMode
        if (holder.layoutAdminActions != null) {
            if (isAdminMode) {
                holder.layoutAdminActions.setVisibility(View.VISIBLE);
            } else {
                holder.layoutAdminActions.setVisibility(View.GONE);
            }
        }

        // Bắt sự kiện click xem video
        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onVideoClick(video);
            } else {
                ArrayList<String> audioNames = new ArrayList<>();
                ArrayList<String> audioLangs = new ArrayList<>();
                ArrayList<String> audioUrls = new ArrayList<>();

                if (video.getAudioTracks() != null) {
                    for (AudioTrackOption audio : video.getAudioTracks()) {
                        audioNames.add(audio.getName());
                        audioLangs.add(audio.getLanguage());
                        audioUrls.add(audio.getUrl());
                    }
                }

                ArrayList<String> subNames = new ArrayList<>();
                ArrayList<String> subLangs = new ArrayList<>();
                ArrayList<String> subUrls = new ArrayList<>();

                if (video.getSubtitleTracks() != null) {
                    for (SubtitleOption sub : video.getSubtitleTracks()) {
                        subNames.add(sub.getName());
                        subLangs.add(sub.getLanguage());
                        subUrls.add(sub.getSubtitleUrl());
                    }
                }

                Intent intent = new Intent(context, PlayerActivity.class);
                intent.putExtra(PlayerActivity.EXTRA_VIDEO_URL, video.getVideoUrl());
                intent.putExtra(PlayerActivity.EXTRA_VIDEO_TITLE, video.getTitle());
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
        });

        // Bắt sự kiện click nút Sửa (Admin)
        if (holder.btnEdit != null) {
            holder.btnEdit.setOnClickListener(v -> {
                int pos = holder.getBindingAdapterPosition();
                if (editListener != null && pos != RecyclerView.NO_POSITION && pos < videoList.size()) {
                    editListener.onVideoEdit(videoList.get(pos), pos);
                }
            });
        }

        // Bắt sự kiện click nút Xóa (Admin)
        if (holder.btnDelete != null) {
            holder.btnDelete.setOnClickListener(v -> {
                int pos = holder.getBindingAdapterPosition();
                if (deleteListener != null && pos != RecyclerView.NO_POSITION && pos < videoList.size()) {
                    deleteListener.onVideoDelete(videoList.get(pos), pos);
                }
            });
        }
    }

    @Override
    public int getItemCount() {
        return videoList.size();
    }

    public static class VideoViewHolder extends RecyclerView.ViewHolder {
        ImageView imgThumbnail;
        TextView tvTitle;
        TextView tvGenres;
        TextView tvDescription;
        LinearLayout layoutAdminActions;
        View btnEdit;
        View btnDelete;

        public VideoViewHolder(@NonNull View itemView) {
            super(itemView);
            imgThumbnail = itemView.findViewById(R.id.imgThumbnail);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvGenres = itemView.findViewById(R.id.tvGenres);
            tvDescription = itemView.findViewById(R.id.tvDescription);
            layoutAdminActions = itemView.findViewById(R.id.layoutAdminActions);
            btnEdit = itemView.findViewById(R.id.btnEditVideo);
            btnDelete = itemView.findViewById(R.id.btnDeleteVideo);
        }
    }
}
