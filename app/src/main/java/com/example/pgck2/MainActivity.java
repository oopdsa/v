package com.example.pgck2;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * MainActivity hiển thị danh sách video, hỗ trợ Tìm Kiếm, Chế độ Admin (Thêm/Sửa/Xóa video)
 * Dữ liệu đồng bộ trực tiếp với FIREBASE CLOUD FIRESTORE và tự động fallback về Local Storage khi ngoại tuyến.
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "FIREBASE_TEST";

    private RecyclerView rvVideos;
    private VideoAdapter videoAdapter;
    private List<Video> fullVideoList;
    private List<Video> currentFilteredList;

    private ImageButton btnToggleAdmin;
    private ImageButton btnToggleSearch;
    private CardView cardSearch;
    private EditText etSearchQuery;
    private ImageButton btnClearSearch;
    private FloatingActionButton fabAddVideo;

    private boolean isAdminMode = false;
    private FirebaseFirestore db; // Đối tượng Firestore

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        // Cấu hình EdgeToEdge UI Insets
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        initViews();

        // 1. Khởi tạo danh sách rỗng ban đầu để Adapter không bị lỗi null
        fullVideoList = new ArrayList<>();
        currentFilteredList = new ArrayList<>();

        // 2. Cài đặt Adapter trước
        setupAdapter();

        // 3. Khởi tạo Firestore và nạp dữ liệu
        db = FirebaseFirestore.getInstance();
        loadVideosFromFirebase();

        // 4. Cài đặt các sự kiện nút bấm
        setupSearchAndAdminEvents();
    }

    private void initViews() {
        rvVideos = findViewById(R.id.rvVideos);
        btnToggleAdmin = findViewById(R.id.btnToggleAdmin);
        btnToggleSearch = findViewById(R.id.btnToggleSearch);
        cardSearch = findViewById(R.id.cardSearch);
        etSearchQuery = findViewById(R.id.etSearchQuery);
        btnClearSearch = findViewById(R.id.btnClearSearch);
        fabAddVideo = findViewById(R.id.fabAddVideo);

        rvVideos.setLayoutManager(new LinearLayoutManager(this));
    }

    /**
     * Tải danh sách phim trực tiếp từ bộ sưu tập "videos" trên Firebase Firestore.
     * Tự động fallback về dữ liệu Local nếu Firestore trống hoặc lỗi mạng.
     */
    private void loadVideosFromFirebase() {
        db.collection("videos").get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null && !task.getResult().isEmpty()) {
                fullVideoList.clear();

                for (QueryDocumentSnapshot document : task.getResult()) {
                    Video video = document.toObject(Video.class);

                    if (video.getId() == null || video.getId().isEmpty()) {
                        video.setId(document.getId());
                    }

                    fullVideoList.add(video);
                    Log.d(TAG, "Đã nạp thành công từ Firebase: " + video.getTitle());
                }

                currentFilteredList.clear();
                currentFilteredList.addAll(fullVideoList);
                videoAdapter.updateVideoList(currentFilteredList);

                Toast.makeText(MainActivity.this, "Đã tải " + fullVideoList.size() + " video từ Firebase Firestore", Toast.LENGTH_SHORT).show();

            } else {
                Log.w(TAG, "Firebase Firestore chưa có dữ liệu hoặc lỗi mạng. Đang chuyển sang dữ liệu dự phòng...");
                loadLocalFallbackVideos();
            }
        });
    }

    /**
     * Tải dữ liệu dự phòng từ Local Storage (SharedPreferences).
     */
    private void loadLocalFallbackVideos() {
        fullVideoList = VideoStorage.loadVideos(this);
        currentFilteredList.clear();
        currentFilteredList.addAll(fullVideoList);
        videoAdapter.updateVideoList(currentFilteredList);
    }

    /**
     * Khởi tạo Adapter và xử lý 3 hành động: Phát phim, Sửa video (Admin), Xóa video (Admin).
     */
    private void setupAdapter() {
        videoAdapter = new VideoAdapter(
                this, currentFilteredList,
                video -> {
                    // 1. CLICK XEM VIDEO -> Mở PlayerActivity
                    Intent intent = new Intent(MainActivity.this, PlayerActivity.class);
                    intent.putExtra(PlayerActivity.EXTRA_VIDEO_URL, video.getVideoUrl());
                    intent.putExtra(PlayerActivity.EXTRA_VIDEO_TITLE, video.getTitle());
                    startActivity(intent);
                },
                (video, position) -> showAddEditVideoDialog(video),
                (video, position) -> confirmDeleteVideo(video)
        );

        rvVideos.setAdapter(videoAdapter);
    }

    private void setupSearchAndAdminEvents() {
        btnToggleSearch.setOnClickListener(v -> {
            if (cardSearch.getVisibility() == View.VISIBLE) {
                cardSearch.setVisibility(View.GONE);
                etSearchQuery.setText("");
            } else {
                cardSearch.setVisibility(View.VISIBLE);
                etSearchQuery.requestFocus();
            }
        });

        etSearchQuery.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterVideoList(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        btnClearSearch.setOnClickListener(v -> etSearchQuery.setText(""));

        btnToggleAdmin.setOnClickListener(v -> {
            isAdminMode = !isAdminMode;
            videoAdapter.setAdminMode(isAdminMode);

            if (isAdminMode) {
                fabAddVideo.setVisibility(View.VISIBLE);
                Toast.makeText(this, "Đã BẬT Chế độ Admin (Đồng bộ Firebase & Local)", Toast.LENGTH_SHORT).show();
            } else {
                fabAddVideo.setVisibility(View.GONE);
                Toast.makeText(this, "Đã TẮT Chế độ Admin", Toast.LENGTH_SHORT).show();
            }
        });

        fabAddVideo.setOnClickListener(v -> showAddEditVideoDialog(null));
    }

    private void filterVideoList(String query) {
        currentFilteredList.clear();
        if (query == null || query.trim().isEmpty()) {
            currentFilteredList.addAll(fullVideoList);
        } else {
            String lowerCaseQuery = query.toLowerCase().trim();
            for (Video video : fullVideoList) {
                if (video.getTitle() != null && video.getTitle().toLowerCase().contains(lowerCaseQuery)) {
                    currentFilteredList.add(video);
                }
            }
        }
        videoAdapter.updateVideoList(currentFilteredList);
    }

    /**
     * Hiển thị Dialog Thêm/Sửa video và đồng bộ lên Firebase Cloud Firestore + Local Storage.
     */
    private void showAddEditVideoDialog(Video videoToEdit) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_edit_video, null);
        builder.setView(dialogView);

        TextView tvDialogTitle = dialogView.findViewById(R.id.tvDialogTitle);
        EditText etTitle = dialogView.findViewById(R.id.etDialogTitle);
        EditText etVideoUrl = dialogView.findViewById(R.id.etDialogVideoUrl);
        EditText etThumbnailUrl = dialogView.findViewById(R.id.etDialogThumbnailUrl);

        boolean isEditMode = (videoToEdit != null);
        if (isEditMode) {
            tvDialogTitle.setText("Sửa Thông Tin Video");
            etTitle.setText(videoToEdit.getTitle());
            etVideoUrl.setText(videoToEdit.getVideoUrl());
            etThumbnailUrl.setText(videoToEdit.getThumbnailUrl());
        } else {
            tvDialogTitle.setText("Thêm Video Mới (Firebase & Local)");
        }

        builder.setPositiveButton(isEditMode ? "Cập Nhật" : "Thêm Mới", (dialog, which) -> {
            String title = etTitle.getText().toString().trim();
            String videoUrl = etVideoUrl.getText().toString().trim();
            String thumbnailUrl = etThumbnailUrl.getText().toString().trim();

            if (title.isEmpty() || videoUrl.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập đầy đủ Tiêu đề và URL Video!", Toast.LENGTH_SHORT).show();
                return;
            }

            Video videoTarget;
            if (isEditMode) {
                videoToEdit.setTitle(title);
                videoToEdit.setVideoUrl(videoUrl);
                videoToEdit.setThumbnailUrl(thumbnailUrl);
                videoTarget = videoToEdit;
            } else {
                videoTarget = new Video(title, thumbnailUrl, videoUrl);
                fullVideoList.add(0, videoTarget);
            }

            // 1. Lưu vĩnh viễn vào Local Storage
            VideoStorage.saveVideos(this, fullVideoList);
            filterVideoList(etSearchQuery.getText().toString());

            // 2. Đồng bộ trực tiếp lên Firebase Firestore
            db.collection("videos").document(videoTarget.getId()).set(videoTarget)
                    .addOnSuccessListener(aVoid -> Toast.makeText(this, "Đã đồng bộ lên Firebase!", Toast.LENGTH_SHORT).show())
                    .addOnFailureListener(e -> Log.e(TAG, "Lỗi đồng bộ Firebase", e));
        });

        builder.setNegativeButton("Hủy", (dialog, which) -> dialog.dismiss());
        builder.create().show();
    }

    /**
     * Xác nhận Xóa video khỏi Firebase Cloud Firestore và Local Storage.
     */
    private void confirmDeleteVideo(Video video) {
        new AlertDialog.Builder(this)
                .setTitle("Xác Nhận Xóa")
                .setMessage("Bạn có chắc chắn muốn xóa video \"" + video.getTitle() + "\" không?")
                .setPositiveButton("Xóa", (dialog, which) -> {
                    fullVideoList.remove(video);
                    VideoStorage.saveVideos(this, fullVideoList);
                    filterVideoList(etSearchQuery.getText().toString());

                    // Xóa dữ liệu khỏi Firebase Firestore
                    db.collection("videos").document(video.getId()).delete()
                            .addOnSuccessListener(aVoid -> Toast.makeText(this, "Đã xóa khỏi Firebase!", Toast.LENGTH_SHORT).show())
                            .addOnFailureListener(e -> Log.e(TAG, "Lỗi xóa trên Firebase", e));
                })
                .setNegativeButton("Hủy", null)
                .show();
    }
}
