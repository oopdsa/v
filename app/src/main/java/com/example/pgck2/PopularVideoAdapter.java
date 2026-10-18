package com.example.pgck2;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter quản lý hiển thị các phim nổi bật trong ViewPager2 Slider.
 */
public class PopularVideoAdapter extends RecyclerView.Adapter<PopularVideoAdapter.PopularViewHolder> {

    private final Context context;
    private List<Video> videoList;

    public PopularVideoAdapter(Context context, List<Video> videoList) {
        this.context = context;
        this.videoList = videoList != null ? videoList : new ArrayList<>();
    }

    public void updateList(List<Video> newList) {
        this.videoList = newList;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public PopularViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_popular_slider, parent, false);
        return new PopularViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PopularViewHolder holder, int position) {
        Video video = videoList.get(position);

        // Hiển thị tiêu đề và mô tả
        holder.tvPopularTitle.setText(video.getTitle());
        if (video.getDescription() != null && !video.getDescription().isEmpty()) {
            holder.tvPopularDesc.setText(video.getDescription());
            holder.tvPopularDesc.setVisibility(View.VISIBLE);
        } else {
            holder.tvPopularDesc.setVisibility(View.GONE);
        }

        // Load ảnh thumbnail banner bằng Glide
        Glide.with(context)
                .load(video.getThumbnailUrl())
                .placeholder(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.ic_dialog_alert)
                .into(holder.imgPopularBanner);

        Episode first = EpisodePlayback.firstEpisode(video.getEpisodes());
        holder.btnWatchNow.setText(first == null ? "Chưa có tập phim" : "Xem tập " + first.getEpisodeNumber());
        holder.btnWatchNow.setEnabled(first != null);
        holder.btnWatchNow.setOnClickListener(v -> {
            if (first != null) context.startActivity(PlaybackIntents.episode(context, video, first));
        });
        holder.itemView.setOnClickListener(v -> {
            if (context instanceof androidx.fragment.app.FragmentActivity) {
                new MovieDetailBottomSheetDialog(video).show(
                        ((androidx.fragment.app.FragmentActivity) context).getSupportFragmentManager(), "movie_detail");
            }
        });
    }
    @Override
    public int getItemCount() {
        return videoList != null ? videoList.size() : 0;
    }

    public static class PopularViewHolder extends RecyclerView.ViewHolder {
        ImageView imgPopularBanner;
        TextView tvPopularTitle;
        TextView tvPopularDesc;
        Button btnWatchNow;

        public PopularViewHolder(@NonNull View itemView) {
            super(itemView);
            imgPopularBanner = itemView.findViewById(R.id.imgPopularBanner);
            tvPopularTitle = itemView.findViewById(R.id.tvPopularTitle);
            tvPopularDesc = itemView.findViewById(R.id.tvPopularDesc);
            btnWatchNow = itemView.findViewById(R.id.btnWatchNow);
        }
    }
}
