package com.example.pgck2;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Model class đại diện cho một video/bộ phim trong danh sách.
 * Hỗ trợ nhận diện tự động ID và ảnh thumbnail của video YouTube.
 */
public class Video {
    private String id;            // Mã định danh duy nhất của video
    private String title;         // Tên phim / Tiêu đề video
    private String thumbnailUrl;  // Đường dẫn/URL ảnh bìa (thumbnail)
    private String videoUrl;      // Đường dẫn/URL truyền phát video (YouTube/MP4/HLS)

    /**
     * Constructor không tham số bắt buộc để Firebase Firestore ép kiểu toObject(Video.class).
     */
    public Video() {
    }

    /**
     * Constructor tự sinh ID ngẫu nhiên.
     */
    public Video(String title, String thumbnailUrl, String videoUrl) {
        this(UUID.randomUUID().toString(), title, thumbnailUrl, videoUrl);
    }

    /**
     * Constructor đầy đủ tham số.
     */
    public Video(String id, String title, String thumbnailUrl, String videoUrl) {
        this.id = id != null ? id : UUID.randomUUID().toString();
        this.title = title;
        this.videoUrl = videoUrl;
        // Nếu thumbnailUrl trống và videoUrl là link YouTube -> tự sinh link thumbnail YouTube
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

    public String getThumbnailUrl() {
        // Nếu thumbnail trống và là video YouTube -> tự lấy thumbnail từ YouTube
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

    /**
     * Kiểm tra xem đường dẫn videoUrl có phải là đường dẫn YouTube hay không.
     *
     * @return true nếu là URL YouTube
     */
    public boolean isYouTubeVideo() {
        return getYouTubeVideoId() != null;
    }

    /**
     * Tách ID của video YouTube từ các dạng URL khác nhau (watch?v=, youtu.be/, embed/).
     *
     * @return YouTube Video ID (ví dụ: "L_LUpnjgPso") hoặc null nếu không phải link YouTube
     */
    public String getYouTubeVideoId() {
        if (videoUrl == null || videoUrl.trim().isEmpty()) {
            return null;
        }
        String pattern = "(?<=watch\\?v=|/videos/|embed/|youtu\\.be/|/v/|/e/|watch\\?node=|\\?v=)[^#&?\\n]*";
        Pattern compiledPattern = Pattern.compile(pattern);
        Matcher matcher = compiledPattern.matcher(videoUrl);
        if (matcher.find()) {
            return matcher.group();
        }
        // Nếu truyền vào ID trực tiếp 11 ký tự
        if (videoUrl.matches("^[a-zA-Z0-9_-]{11}$")) {
            return videoUrl;
        }
        return null;
    }
}
