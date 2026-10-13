package com.example.pgck2;

/**
 * Model class đại diện cho một Tùy Chọn Phụ Đề Rời (Subtitle CC Track).
 */
public class SubtitleOption {
    private String name;        // Tên hiển thị phụ đề (vd: Tiếng Việt, English, 日本語)
    private String language;    // Mã ngôn ngữ (vd: vi, en, ja)
    private String subtitleUrl; // URL file phụ đề (.vtt hoặc .srt)

    public SubtitleOption() {
    }

    public SubtitleOption(String name, String language, String subtitleUrl) {
        this.name = name != null ? name : "";
        this.language = language != null ? language : "vi";
        this.subtitleUrl = subtitleUrl != null ? subtitleUrl : "";
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getSubtitleUrl() {
        return subtitleUrl;
    }

    public void setSubtitleUrl(String subtitleUrl) {
        this.subtitleUrl = subtitleUrl;
    }
}
