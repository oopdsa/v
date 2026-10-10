package com.example.pgck2;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
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

public class CloudinaryUploader {

    // THAY 2 THÔNG SỐ CỦA BẠN VÀO ĐÂY:
    private static final String CLOUD_NAME = "THAY_CLOUD_NAME_CUA_BAN_VAO_DAY";
    private static final String UPLOAD_PRESET = "THAY_UPLOAD_PRESET_CUA_BAN_VAO_DAY";

    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();

    public interface UploadCallback {
        void onSuccess(String secureUrl);
        void onError(String errorMessage);
    }

    /**
     * Upload file (video MP4 hoặc ảnh) lên Cloudinary
     * @param context Context để đọc Uri
     * @param fileUri Uri được chọn từ thư viện máy
     * @param isVideo true nếu là video, false nếu là ảnh
     * @param callback Nhận kết quả URL trả về
     */
    public static void uploadMedia(Context context, Uri fileUri, boolean isVideo, UploadCallback callback) {
        Handler mainHandler = new Handler(Looper.getMainLooper());

        new Thread(() -> {
            try {
                // 1. Đọc dữ liệu từ Uri
                InputStream inputStream = context.getContentResolver().openInputStream(fileUri);
                if (inputStream == null) {
                    mainHandler.post(() -> callback.onError("Không thể đọc tệp tin!"));
                    return;
                }

                ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int len;
                while ((len = inputStream.read(buffer)) != -1) {
                    byteBuffer.write(buffer, 0, len);
                }
                byte[] fileBytes = byteBuffer.toByteArray();
                inputStream.close();

                // 2. Xác định endpoint (video hoặc image)
                String resourceType = isVideo ? "video" : "image";
                String uploadUrl = "https://api.cloudinary.com/v1_1/" + CLOUD_NAME + "/" + resourceType + "/upload";
                String mimeType = isVideo ? "video/mp4" : "image/jpeg";

                // 3. Đóng gói Multipart Form Body
                RequestBody requestBody = new MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("upload_preset", UPLOAD_PRESET)
                        .addFormDataPart("file", "upload_" + System.currentTimeMillis(),
                                RequestBody.create(fileBytes, MediaType.parse(mimeType)))
                        .build();

                Request request = new Request.Builder()
                        .url(uploadUrl)
                        .post(requestBody)
                        .build();

                // 4. Thực thi gửi request
                client.newCall(request).enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, java.io.IOException e) {
                        mainHandler.post(() -> callback.onError("Lỗi kết nối mạng: " + e.getMessage()));
                    }

                    @Override
                    public void onResponse(Call call, Response response) throws java.io.IOException {
                        String responseData = response.body() != null ? response.body().string() : "";
                        if (response.isSuccessful()) {
                            try {
                                JSONObject jsonObject = new JSONObject(responseData);
                                String secureUrl = jsonObject.getString("secure_url");
                                mainHandler.post(() -> callback.onSuccess(secureUrl));
                            } catch (Exception e) {
                                mainHandler.post(() -> callback.onError("Lỗi phân tích JSON từ server!"));
                            }
                        } else {
                            mainHandler.post(() -> callback.onError("Server trả về lỗi: " + responseData));
                        }
                    }
                });

            } catch (Exception e) {
                mainHandler.post(() -> callback.onError("Lỗi xử lý file: " + e.getMessage()));
            }
        }).start();
    }
}