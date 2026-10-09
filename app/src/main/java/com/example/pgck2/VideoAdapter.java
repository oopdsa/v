package com.example.pgck2;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
 * Adapter quản lý hiển thị danh sách video trong RecyclerView.
 * Hỗ trợ hiển thị nút Sửa / Xóa khi ở Chế độ Admin (Admin Mode).
 */
public class VideoAdapter extends RecyclerView.Adapter<VideoAdapter.VideoViewHolder> {

    // Interface sự kiện click phát video
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
     * Cập nhật danh sách video khi tìm kiếm hoặc thay đổi dữ liệu.
     */
    public void updateVideoList(List<Video> newList) {
        this.videoList = newList;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VideoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_video, parent, false);
        return new VideoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VideoViewHolder holder, int position) {
        Video video = videoList.get(position);

        // Hiển thị tiêu đề
        holder.tvTitle.setText(video.getTitle());

        // Sử dụng Glide nạp ảnh thumbnail
        Glide.with(context)
                .load(video.getThumbnailUrl())
                .placeholder(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.ic_dialog_alert)
                .into(holder.imgThumbnail);

        // Nút Admin Sửa / Xóa ẩn/hiện tùy theo isAdminMode
        if (isAdminMode) {
            holder.layoutAdminActions.setVisibility(View.VISIBLE);
        } else {
            holder.layoutAdminActions.setVisibility(View.GONE);
        }

        // Bắt sự kiện click xem video
        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onVideoClick(video);
            }
        });

        // Bắt sự kiện click nút Sửa (Admin)
        holder.btnEdit.setOnClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (editListener != null && pos != RecyclerView.NO_POSITION) {
                editListener.onVideoEdit(video, pos);
            }
        });

        // Bắt sự kiện click nút Xóa (Admin)
        holder.btnDelete.setOnClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (deleteListener != null && pos != RecyclerView.NO_POSITION) {
                deleteListener.onVideoDelete(video, pos);
            }
        });
    }

    @Override
    public int getItemCount() {
        return videoList != null ? videoList.size() : 0;
    }

    public static class VideoViewHolder extends RecyclerView.ViewHolder {
        ImageView imgThumbnail;
        TextView tvTitle;
        LinearLayout layoutAdminActions;
        ImageButton btnEdit;
        ImageButton btnDelete;

        public VideoViewHolder(@NonNull View itemView) {
            super(itemView);
            imgThumbnail = itemView.findViewById(R.id.imgThumbnail);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            layoutAdminActions = itemView.findViewById(R.id.layoutAdminActions);
            btnEdit = itemView.findViewById(R.id.btnEditVideo);
            btnDelete = itemView.findViewById(R.id.btnDeleteVideo);
        }
    }
}
