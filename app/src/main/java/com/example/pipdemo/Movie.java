package com.example.pipdemo;

public class Movie {
    private int id;
    private String title;
    private String description;
    private String thumbnailUrl;
    private String videoUrl;

    public Movie(int id, String title, String description, String thumbnailUrl, String videoUrl) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.thumbnailUrl = thumbnailUrl;
        this.videoUrl = videoUrl;
    }

    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getThumbnailUrl() { return thumbnailUrl; }
    public String getVideoUrl() { return videoUrl; }
}