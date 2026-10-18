package com.example.pgck2;

import java.util.ArrayList;
import java.util.List;

/**
 * Model class đại diện cho một Tập Phim (Episode) trong một bộ phim Series.
 */
public class Episode {
    private int episodeNumber;                   // Số tập (vd: 1, 2, 3)
    private String title;                        // Tên tập phim (vd: Tập 1: Khởi đầu)
    private String videoUrl;                     // Đường dẫn URL video của tập này
    private Integer sortOrder;                   // null for older records; fall back to episode number
    private List<AudioTrackOption> audioTracks;  // Danh sách luồng âm thanh lồng tiếng riêng cho tập
    private List<SubtitleOption> subtitleTracks; // Danh sách phụ đề rời CC riêng cho tập

    public Episode() {
        this.audioTracks = new ArrayList<>();
        this.subtitleTracks = new ArrayList<>();
    }

    public Episode(int episodeNumber, String title, String videoUrl) {
        this.episodeNumber = episodeNumber;
        this.title = title != null ? title : "Tập " + episodeNumber;
        this.videoUrl = videoUrl != null ? videoUrl : "";
        this.audioTracks = new ArrayList<>();
        this.subtitleTracks = new ArrayList<>();
    }

    public int getEpisodeNumber() {
        return episodeNumber;
    }

    public void setEpisodeNumber(int episodeNumber) {
        this.episodeNumber = episodeNumber;
    }

    public String getTitle() {
        return title != null && !title.isEmpty() ? title : "Tập " + episodeNumber;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getVideoUrl() {
        return videoUrl;
    }

    public Integer getSortOrder() { return sortOrder; }

    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public void setVideoUrl(String videoUrl) {
        this.videoUrl = videoUrl;
    }

    public List<AudioTrackOption> getAudioTracks() {
        if (audioTracks == null) {
            audioTracks = new ArrayList<>();
        }
        return audioTracks;
    }

    public void setAudioTracks(List<AudioTrackOption> audioTracks) {
        this.audioTracks = audioTracks != null ? audioTracks : new ArrayList<>();
    }

    public List<SubtitleOption> getSubtitleTracks() {
        if (subtitleTracks == null) {
            subtitleTracks = new ArrayList<>();
        }
        return subtitleTracks;
    }

    public void setSubtitleTracks(List<SubtitleOption> subtitleTracks) {
        this.subtitleTracks = subtitleTracks != null ? subtitleTracks : new ArrayList<>();
    }
}
