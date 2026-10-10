package com.example.pgck2;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;

import org.json.JSONObject;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSink;

public class CloudinaryUploader {

    private static final String CLOUD_NAME = "eqmwglmm"; // Thay bằng cloud name của bạn
    private static final String UPLOAD_PRESET = "android_video_preset"; // Thay bằng upload preset của bạn

    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();

    public interface UploadCallback {
        void onSuccess(String secureUrl);
        void onError(String errorMessage);
        // Tùy chọn: Thêm callback để hiển thị progress bar
        // void onProgress(int percent);
    }

    public static void uploadMedia(Context context, Uri fileUri, boolean isVideo, UploadCallback callback) {
        Handler mainHandler = new Handler(Looper.getMainLooper());

        new Thread(() -> {
            try {
                // 1. Lấy kích thước file và InputStream mà KHÔNG đọc vào RAM
                ParcelFileDescriptor pfd = context.getContentResolver().openFileDescriptor(fileUri, "r");
                if (pfd == null) {
                    mainHandler.post(() -> callback.onError("Không thể mở tệp tin!"));
                    return;
                }
                long fileSize = pfd.getStatSize();
                InputStream inputStream = new java.io.FileInputStream(pfd.getFileDescriptor());

                // 2. Xác định endpoint và mimeType
                String resourceType = isVideo ? "video" : "image";
                String uploadUrl = "https://api.cloudinary.com/v1_1/" + CLOUD_NAME + "/" + resourceType + "/upload";
                String mimeType = isVideo ? "video/mp4" : "image/jpeg";
                MediaType mediaType = MediaType.parse(mimeType);

                // 3. Tạo RequestBody streaming trực tiếp từ InputStream
                RequestBody fileRequestBody = new RequestBody() {
                    @Override
                    public MediaType contentType() {
                        return mediaType;
                    }

                    @Override
                    public long contentLength() {
                        return fileSize; // Giúp OkHttp tối ưu hóa việc gửi dữ liệu
                    }

                    @Override
                    public void writeTo(BufferedSink sink) throws java.io.IOException {
                        byte[] buffer = new byte[8192]; // Đọc theo từng chunk 8KB
                        int read;
                        while ((read = inputStream.read(buffer)) != -1) {
                            sink.write(buffer, 0, read);
                            // Ở đây bạn có thể tính toán % để gọi callback.onProgress() nếu muốn
                        }
                        inputStream.close();
                        pfd.close();
                    }
                };

                // 4. Đóng gói Multipart Form Body
                RequestBody requestBody = new MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("upload_preset", UPLOAD_PRESET)
                        .addFormDataPart("file", "upload_" + System.currentTimeMillis() + (isVideo ? ".mp4" : ".jpg"), fileRequestBody)
                        .build();

                Request request = new Request.Builder()
                        .url(uploadUrl)
                        .post(requestBody)
                        .build();

                // 5. Thực thi gửi request
                client.newCall(request).enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, java.io.IOException e) {
                        mainHandler.post(() -> callback.onError("Lỗi kết nối mạng: " + e.getMessage()));
                    }

                    @Override
                    public void onResponse(Call call, Response response) {
                        try {
                            String responseData = response.body() != null ? response.body().string() : "";
                            if (response.isSuccessful()) {
                                JSONObject jsonObject = new JSONObject(responseData);
                                String secureUrl = jsonObject.getString("secure_url");
                                mainHandler.post(() -> callback.onSuccess(secureUrl));
                            } else {
                                mainHandler.post(() -> callback.onError("Server trả về lỗi: " + responseData));
                            }
                        } catch (Exception e) {
                            mainHandler.post(() -> callback.onError("Lỗi phân tích JSON: " + e.getMessage()));
                        }
                    }
                });

            } catch (Exception e) {
                mainHandler.post(() -> callback.onError("Lỗi xử lý file: " + e.getMessage()));
            }
        }).start();
    }
}