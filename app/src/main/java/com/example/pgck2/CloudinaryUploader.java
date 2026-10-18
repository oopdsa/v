package com.example.pgck2;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import org.json.JSONObject;
import java.io.IOException;
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
    private static final String CLOUD_NAME = "eqmwglmm";
    private static final String UPLOAD_PRESET = "android_video_preset";
    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS).writeTimeout(120, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS).build();

    public interface UploadCallback {
        void onSuccess(String secureUrl);
        void onError(String errorMessage);
    }

    public static void uploadMedia(Context context, Uri uri, boolean isVideo, UploadCallback callback) {
        upload(context, uri, isVideo ? "video" : "image", callback);
    }

    public static void uploadAudio(Context context, Uri uri, UploadCallback callback) {
        // Cloudinary stores audio under the video resource type as well.
        upload(context, uri, "video", callback);
    }

    private static void upload(Context context, Uri uri, String resourceType, UploadCallback callback) {
        Context app = context.getApplicationContext();
        Handler main = new Handler(Looper.getMainLooper());
        try {
            String name = "upload_" + System.currentTimeMillis();
            long size = -1;
            try (Cursor cursor = app.getContentResolver().query(uri,
                    new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    if (!cursor.isNull(0)) name = cursor.getString(0);
                    if (!cursor.isNull(1)) size = cursor.getLong(1);
                }
            }
            String mime = app.getContentResolver().getType(uri);
            MediaType type = MediaType.parse(mime != null ? mime : "application/octet-stream");
            final long contentSize = size;
            RequestBody fileBody = new RequestBody() {
                @Override public MediaType contentType() { return type; }
                @Override public long contentLength() { return contentSize; }
                @Override public void writeTo(BufferedSink sink) throws IOException {
                    try (InputStream stream = app.getContentResolver().openInputStream(uri)) {
                        if (stream == null) throw new IOException("Không thể mở tệp đã chọn.");
                        byte[] buffer = new byte[8192];
                        int read;
                        while ((read = stream.read(buffer)) != -1) sink.write(buffer, 0, read);
                    }
                }
            };
            RequestBody body = new MultipartBody.Builder().setType(MultipartBody.FORM)
                    .addFormDataPart("upload_preset", UPLOAD_PRESET)
                    .addFormDataPart("file", name, fileBody).build();
            Request request = new Request.Builder().url("https://api.cloudinary.com/v1_1/"
                    + CLOUD_NAME + "/" + resourceType + "/upload").post(body).build();
            client.newCall(request).enqueue(new Callback() {
                @Override public void onFailure(Call call, IOException error) {
                    main.post(() -> callback.onError("Lỗi tải tệp: " + error.getMessage()));
                }
                @Override public void onResponse(Call call, Response response) {
                    try (Response result = response) {
                        String payload = result.body() != null ? result.body().string() : "";
                        JSONObject json = new JSONObject(payload);
                        if (!result.isSuccessful()) {
                            JSONObject error = json.optJSONObject("error");
                            throw new IOException(error != null ? error.optString("message")
                                    : "Cloudinary HTTP " + result.code());
                        }
                        String url = json.getString("secure_url");
                        main.post(() -> callback.onSuccess(url));
                    } catch (Exception error) {
                        main.post(() -> callback.onError(error.getMessage()));
                    }
                }
            });
        } catch (Exception error) {
            main.post(() -> callback.onError("Không đọc được tệp: " + error.getMessage()));
        }
    }
}
