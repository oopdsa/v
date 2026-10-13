package com.example.pgck2;

/**
 * Model class đại diện cho một Luồng Âm Thanh Lồng Tiếng (Audio Track Dubbing).
 */
public class AudioTrackOption {
    private String name;     // Tên hiển thị (vd: Tiếng Việt (Dub), English (Original))
    private String language; // Mã ngôn ngữ (vd: vi, en, ja)
    private String url;      // URL luồng âm thanh hoặc video thay thế (nếu có)

    public AudioTrackOption() {
    }

    public AudioTrackOption(String name, String language, String url) {
        this.name = name != null ? name : "";
        this.language = language != null ? language : "vi";
        this.url = url != null ? url : "";
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

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }
}
