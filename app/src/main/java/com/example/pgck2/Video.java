package com.example.pgck2;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Model class đại diện cho một video/bộ phim trong ứng dụng.
 * Chứa thông tin Tiêu đề, Mô tả, Thể loại, Năm/Tháng phát hành (releaseYear/Month),
 * Loại Phim (STANDARD vs TRAILER), Danh sách các Tập Phim (Episodes),
 * Luồng lồng tiếng (Audio Dubs) và Phụ đề rời (Subtitles CC).
 */
public class Video {
    public static final String TYPE_STANDARD = "STANDARD";
    public static final String TYPE_TRAILER = "TRAILER";

    private String id;                           // Mã định danh duy nhất của video
    private String title;                        // Tên phim / Tiêu đề video
    private String description;                  // Mô tả nội dung video / phim
    private String thumbnailUrl;                 // Đường dẫn/URL ảnh bìa (thumbnail)
    private boolean isPopular;                   // Đánh dấu phim Nổi bật (Popular Slider)
    private String videoUrl;                     // Đường dẫn/URL truyền phát video chính (YouTube/MP4/HLS)
    private List<String> genres;                 // Danh sách danh mục/thể loại phim (vd: Hành Động, Hài Hước)
    private String subtitleUrl;                  // URL file phụ đề chính (.vtt hoặc .srt)
    private String subtitleLanguage;             // Ngôn ngữ phụ đề chính (vd: "vi", "en")
    private List<AudioTrackOption> audioTracks;  // Danh sách luồng lồng tiếng lồng ghép (Multi-Audio Dubs)
    private List<SubtitleOption> subtitleTracks; // Danh sách phụ đề rời lồng ghép (Multi-Subtitles CC)

    // Bổ sung các trường mới theo yêu cầu
    private String releaseYear;                  // Năm phát hành (vd: "2024")
    private String releaseMonth;                 // Tháng phát hành (vd: "10")
    private String videoType;                    // Loại video ("STANDARD" hoặc "TRAILER")
    private List<Episode> episodes;              // Danh sách các tập phim (Episodes)

    public Video() {
        this.genres = new ArrayList<>();
        this.subtitleUrl = "";
        this.subtitleLanguage = "vi";
        this.audioTracks = new ArrayList<>();
        this.subtitleTracks = new ArrayList<>();
        this.releaseYear = "2024";
        this.releaseMonth = "10";
        this.videoType = TYPE_STANDARD;
        this.episodes = new ArrayList<>();
    }

    public Video(String title, String thumbnailUrl, String videoUrl) {
        this(UUID.randomUUID().toString(), title, "", thumbnailUrl, videoUrl, false, new ArrayList<>(), "", "vi");
    }

    public Video(String title, String description, String thumbnailUrl, String videoUrl) {
        this(UUID.randomUUID().toString(), title, description, thumbnailUrl, videoUrl, false, new ArrayList<>(), "", "vi");
    }

    public Video(String title, String description, String thumbnailUrl, String videoUrl, boolean isPopular) {
        this(UUID.randomUUID().toString(), title, description, thumbnailUrl, videoUrl, isPopular, new ArrayList<>(), "", "vi");
    }

    public Video(String id, String title, String description, String thumbnailUrl, String videoUrl, boolean isPopular, List<String> genres, String subtitleUrl, String subtitleLanguage) {
        this.id = id != null && !id.isEmpty() ? id : UUID.randomUUID().toString();
        this.title = title != null ? title : "";
        this.description = description != null ? description : "";
        this.videoUrl = videoUrl != null ? videoUrl : "";
        this.isPopular = isPopular;
        this.genres = genres != null ? genres : new ArrayList<>();
        this.subtitleUrl = subtitleUrl != null ? subtitleUrl : "";
        this.subtitleLanguage = subtitleLanguage != null && !subtitleLanguage.isEmpty() ? subtitleLanguage : "vi";
        this.audioTracks = new ArrayList<>();
        this.subtitleTracks = new ArrayList<>();
        this.releaseYear = "2024";
        this.releaseMonth = "10";
        this.videoType = TYPE_STANDARD;
        this.episodes = new ArrayList<>();

        if ((thumbnailUrl == null || thumbnailUrl.trim().isEmpty()) && isYouTubeVideo()) {
            String youtubeId = getYouTubeVideoId();
            if (youtubeId != null) {
                this.thumbnailUrl = "https://img.youtube.com/vi/" + youtubeId + "/hqdefault.jpg";
            } else {
                this.thumbnailUrl = thumbnailUrl;
            }
        } else {
            this.thumbnailUrl = thumbnailUrl;
        }
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isPopular() {
        return isPopular;
    }

    public boolean getIsPopular() {
        return isPopular;
    }

    public void setPopular(boolean popular) {
        isPopular = popular;
    }

    public void setIsPopular(boolean popular) {
        isPopular = popular;
    }

    public List<String> getGenres() {
        if (genres == null) {
            genres = new ArrayList<>();
        }
        return genres;
    }

    public void setGenres(List<String> genres) {
        this.genres = genres != null ? genres : new ArrayList<>();
    }

    public String getSubtitleUrl() {
        return subtitleUrl != null ? subtitleUrl : "";
    }

    public void setSubtitleUrl(String subtitleUrl) {
        this.subtitleUrl = subtitleUrl;
    }

    public String getSubtitleLanguage() {
        return subtitleLanguage != null && !subtitleLanguage.isEmpty() ? subtitleLanguage : "vi";
    }

    public void setSubtitleLanguage(String subtitleLanguage) {
        this.subtitleLanguage = subtitleLanguage;
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

    public String getReleaseYear() {
        return releaseYear != null && !releaseYear.trim().isEmpty() ? releaseYear : "2024";
    }

    public void setReleaseYear(String releaseYear) {
        this.releaseYear = releaseYear;
    }

    public String getReleaseMonth() {
        return releaseMonth != null && !releaseMonth.trim().isEmpty() ? releaseMonth : "10";
    }

    public void setReleaseMonth(String releaseMonth) {
        this.releaseMonth = releaseMonth;
    }

    public String getVideoType() {
        return videoType != null && !videoType.trim().isEmpty() ? videoType : TYPE_STANDARD;
    }

    public void setVideoType(String videoType) {
        this.videoType = videoType;
    }

    public boolean isTrailer() {
        return TYPE_TRAILER.equalsIgnoreCase(videoType);
    }

    public List<Episode> getEpisodes() {
        if (episodes == null) {
            episodes = new ArrayList<>();
        }
        return episodes;
    }

    public void setEpisodes(List<Episode> episodes) {
        this.episodes = episodes != null ? episodes : new ArrayList<>();
    }

    public String getGenresString() {
        if (genres == null || genres.isEmpty()) {
            return "Chưa phân loại";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < genres.size(); i++) {
            sb.append(genres.get(i));
            if (i < genres.size() - 1) {
                sb.append(", ");
            }
        }
        return sb.toString();
    }

    public String getThumbnailUrl() {
        if ((thumbnailUrl == null || thumbnailUrl.trim().isEmpty()) && isYouTubeVideo()) {
            String youtubeId = getYouTubeVideoId();
            if (youtubeId != null) {
                return "https://img.youtube.com/vi/" + youtubeId + "/hqdefault.jpg";
            }
        }
        return thumbnailUrl;
    }

    public void setThumbnailUrl(String thumbnailUrl) {
        this.thumbnailUrl = thumbnailUrl;
    }

    public String getVideoUrl() {
        return videoUrl;
    }

    public void setVideoUrl(String videoUrl) {
        this.videoUrl = videoUrl;
    }

    public boolean isYouTubeVideo() {
        return getYouTubeVideoId() != null;
    }

    public String getYouTubeVideoId() {
        return YouTubeUrls.videoId(videoUrl);
    }
}
