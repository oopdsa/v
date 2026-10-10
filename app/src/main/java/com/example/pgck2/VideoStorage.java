package com.example.pgck2;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Lớp quản lý lưu trữ và đọc danh sách video từ SharedPreferences dạng JSON.
 * Giúp dữ liệu Thêm / Sửa / Xóa trong Admin Mode được lưu cố định.
 */
public class VideoStorage {

    private static final String PREF_NAME = "video_app_prefs_v2";
    private static final String KEY_VIDEO_LIST = "key_video_list_v2";

    /**
     * Tải danh sách video đã lưu. Nếu chưa có, trả về danh sách video mặc định.
     *
     * @param context Context của ứng dụng
     * @return Danh sách các đối tượng Video
     */
    public static List<Video> loadVideos(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String jsonString = prefs.getString(KEY_VIDEO_LIST, null);

        if (jsonString == null || jsonString.isEmpty()) {
            List<Video> defaultList = getDefaultVideos();
            saveVideos(context, defaultList);
            return defaultList;
        }

        List<Video> videos = new ArrayList<>();
        try {
            JSONArray jsonArray = new JSONArray(jsonString);
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject obj = jsonArray.getJSONObject(i);
                String id = obj.optString("id");
                String title = obj.optString("title");
                String thumbnailUrl = obj.optString("thumbnailUrl");
                String videoUrl = obj.optString("videoUrl");

                videos.add(new Video(id, title, thumbnailUrl, videoUrl));
            }
        } catch (JSONException e) {
            e.printStackTrace();
            return getDefaultVideos();
        }

        return videos;
    }

    /**
     * Lưu danh sách video vào SharedPreferences dưới dạng chuỗi JSON.
     *
     * @param context Context của ứng dụng
     * @param videos  Danh sách video cần lưu
     */
    public static void saveVideos(Context context, List<Video> videos) {
        JSONArray jsonArray = new JSONArray();
        for (Video video : videos) {
            JSONObject obj = new JSONObject();
            try {
                obj.put("id", video.getId());
                obj.put("title", video.getTitle());
                obj.put("thumbnailUrl", video.getThumbnailUrl());
                obj.put("videoUrl", video.getVideoUrl());
                jsonArray.put(obj);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_VIDEO_LIST, jsonArray.toString()).apply();
    }

    /**
     * Khôi phục danh sách video về danh sách mặc định.
     */
    public static List<Video> resetToDefault(Context context) {
        List<Video> defaultList = getDefaultVideos();
        saveVideos(context, defaultList);
        return defaultList;
    }

    /**
     * Tạo danh sách video mặc định hoạt động 100% trên cả ExoPlayer (Direct MP4) và YouTube.
     */
    private static List<Video> getDefaultVideos() {
        List<Video> list = new ArrayList<>();

        list.add(new Video(
                "Big Buck Bunny (Direct MP4 Stream)",
                "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/BigBuckBunny.jpg",
                "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
        ));

        list.add(new Video(
                "Sintel - Open Movie (Direct MP4 Stream)",
                "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/Sintel.jpg",
                "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4"
        ));

        list.add(new Video(
                "Big Buck Bunny (YouTube 60fps)",
                "https://img.youtube.com/vi/YE7VzlLtp-4/hqdefault.jpg",
                "https://www.youtube.com/watch?v=YE7VzlLtp-4"
        ));

        list.add(new Video(
                "Tears of Steel - Sci-Fi Movie (Direct MP4)",
                "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/TearsOfSteel.jpg",
                "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4"
        ));

        list.add(new Video(
                "Elephants Dream (Direct MP4 Stream)",
                "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/ElephantsDream.jpg",
                "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4"
        ));

        list.add(new Video(
                "For Bigger Blazes (Direct MP4 Stream)",
                "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/ForBiggerBlazes.jpg",
                "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4"
        ));

        list.add(new Video(
                "Google I/O Keynote (YouTube)",
                "https://img.youtube.com/vi/L_LUpnjgPso/hqdefault.jpg",
                "https://www.youtube.com/watch?v=L_LUpnjgPso"
        ));

        return list;
    }
}
