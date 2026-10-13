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

        // Sự kiện click nút Watch Now hoặc toàn bộ slider
        View.OnClickListener clickListener = v -> {
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
        };

        holder.btnWatchNow.setOnClickListener(clickListener);
        holder.itemView.setOnClickListener(clickListener);
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
