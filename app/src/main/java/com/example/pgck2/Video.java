package com.example.pgck2;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Model class đại diện cho một video/bộ phim trong ứng dụng.
 * Chứa tiêu đề, mô tả, danh sách thể loại (genres), đường dẫn hình ảnh, URL video và trạng thái Nổi bật (isPopular).
 */
public class Video {
    private String id;                 // Mã định danh duy nhất của video
    private String title;              // Tên phim / Tiêu đề video
    private String description;        // Mô tả nội dung video / phim
    private String thumbnailUrl;       // Đường dẫn/URL ảnh bìa (thumbnail)
    private boolean isPopular;         // Đánh dấu phim Nổi bật (Popular Slider)
    private String videoUrl;           // Đường dẫn/URL truyền phát video (YouTube/MP4/HLS)
    private List<String> genres;       // Danh sách danh mục/thể loại phim (vd: Hành Động, Hài Hước)

    /**
     * Constructor không tham số bắt buộc để Firebase Firestore ép kiểu toObject(Video.class).
     */
    public Video() {
        this.genres = new ArrayList<>();
    }

    /**
     * Constructor ngắn gọn.
     */
    public Video(String title, String thumbnailUrl, String videoUrl) {
        this(UUID.randomUUID().toString(), title, "", thumbnailUrl, videoUrl, false, new ArrayList<>());
    }

    /**
     * Constructor gồm tiêu đề và mô tả.
     */
    public Video(String title, String description, String thumbnailUrl, String videoUrl) {
        this(UUID.randomUUID().toString(), title, description, thumbnailUrl, videoUrl, false, new ArrayList<>());
    }

    /**
     * Constructor gồm 5 tham số có trạng thái Popular.
     */
    public Video(String title, String description, String thumbnailUrl, String videoUrl, boolean isPopular) {
        this(UUID.randomUUID().toString(), title, description, thumbnailUrl, videoUrl, isPopular, new ArrayList<>());
    }

    /**
     * Constructor đầy đủ tham số.
     */
    public Video(String id, String title, String description, String thumbnailUrl, String videoUrl, boolean isPopular, List<String> genres) {
        this.id = id != null && !id.isEmpty() ? id : UUID.randomUUID().toString();
        this.title = title != null ? title : "";
        this.description = description != null ? description : "";
        this.videoUrl = videoUrl != null ? videoUrl : "";
        this.isPopular = isPopular;
        this.genres = genres != null ? genres : new ArrayList<>();

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

    /**
     * Trả về chuỗi danh sách các thể loại nối nhau bằng dấu phẩy (vd: "Hành Động, Kinh Dị").
     */
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

    /**
     * Kiểm tra xem đường dẫn videoUrl có phải là đường dẫn YouTube hay không.
     */
    public boolean isYouTubeVideo() {
        return getYouTubeVideoId() != null;
    }

    /**
     * Tách ID của video YouTube từ các dạng URL khác nhau (watch?v=, youtu.be/, embed/).
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
        if (videoUrl.matches("^[a-zA-Z0-9_-]{11}$")) {
            return videoUrl;
        }
        return null;
    }
}
