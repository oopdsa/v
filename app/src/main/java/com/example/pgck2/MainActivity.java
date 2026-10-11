package com.example.pgck2;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * MainActivity hiển thị danh sách phim/video đẳng cấp Cinema.
 * Hỗ trợ ViewPager2 Slider phim nổi bật (Popular), Tìm kiếm thời gian thực,
 * Chế độ Admin (Thêm/Sửa/Xóa video & Đánh dấu Popular Slider) đồng bộ trực tiếp với FIREBASE CLOUD FIRESTORE
 * và tích hợp Upload Video/Ảnh Bìa lên Đám mây qua CloudinaryUploader.
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "FIREBASE_TEST";

    private RecyclerView rvVideos;
    private VideoAdapter videoAdapter;
    private List<Video> fullVideoList;
    private List<Video> currentFilteredList;

    private ViewPager2 viewPagerPopular;
    private ImageButton btnPrevPopular;
    private ImageButton btnNextPopular;
    private PopularVideoAdapter popularVideoAdapter;
    private List<Video> popularVideoList;

    private ImageButton btnToggleAdmin;
    private ImageButton btnToggleSearch;
    private CardView cardSearch;
    private EditText etSearchQuery;
    private ImageButton btnClearSearch;
    private FloatingActionButton fabAddVideo;

    private boolean isAdminMode = false;
    private FirebaseFirestore db; // Đối tượng Firestore

    // Launchers chọn Video và Ảnh
    private ActivityResultLauncher<String> videoPickerLauncher;
    private ActivityResultLauncher<String> imagePickerLauncher;
    private ProgressDialog progressDialog; // Tiến trình hiển thị khi đang upload

    private EditText currentEtVideoUrl;    // Giữ tham chiếu ô nhập URL Video
    private EditText currentEtThumbnailUrl;// Giữ tham chiếu ô nhập URL Ảnh Bìa

    // Handler tự động cuộn Slider Popular mỗi 12 giây
    private final Handler sliderHandler = new Handler(Looper.getMainLooper());
    private final Runnable sliderRunnable = new Runnable() {
        @Override
        public void run() {
            if (viewPagerPopular != null && popularVideoAdapter != null) {
                int currentItem = viewPagerPopular.getCurrentItem();
                int totalItems = popularVideoAdapter.getItemCount();
                if (totalItems > 0) {
                    int nextItem = (currentItem + 1) % totalItems;
                    viewPagerPopular.setCurrentItem(nextItem, true);
                }
            }
            sliderHandler.postDelayed(this, 12000); // 12 giây
        }
    };

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

        // 1. Khởi tạo ProgressDialog
        progressDialog = new ProgressDialog(this);
        progressDialog.setCancelable(false);

        // 2. Đăng ký Launcher chọn & upload VIDEO từ thiết bị
        videoPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        progressDialog.setMessage("Đang tải video lên đám mây, vui lòng chờ...");
                        progressDialog.show();
                        CloudinaryUploader.uploadMedia(this, uri, true, new CloudinaryUploader.UploadCallback() {
                            @Override
                            public void onSuccess(String secureUrl) {
                                progressDialog.dismiss();
                                Toast.makeText(MainActivity.this, "Tải video thành công!", Toast.LENGTH_SHORT).show();
                                if (currentEtVideoUrl != null) {
                                    currentEtVideoUrl.setText(secureUrl);
                                }
                            }

                            @Override
                            public void onError(String errorMessage) {
                                progressDialog.dismiss();
                                Toast.makeText(MainActivity.this, "Lỗi upload video: " + errorMessage, Toast.LENGTH_LONG).show();
                            }
                        });
                    }
                }
        );

        // 3. Đăng ký Launcher chọn & upload ẢNH BÌA (Thumbnail) từ thiết bị
        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        progressDialog.setMessage("Đang tải ảnh bìa lên đám mây...");
                        progressDialog.show();
                        CloudinaryUploader.uploadMedia(this, uri, false, new CloudinaryUploader.UploadCallback() {
                            @Override
                            public void onSuccess(String secureUrl) {
                                progressDialog.dismiss();
                                Toast.makeText(MainActivity.this, "Tải ảnh thành công!", Toast.LENGTH_SHORT).show();
                                if (currentEtThumbnailUrl != null) {
                                    currentEtThumbnailUrl.setText(secureUrl);
                                }
                            }

                            @Override
                            public void onError(String errorMessage) {
                                progressDialog.dismiss();
                                Toast.makeText(MainActivity.this, "Lỗi upload ảnh: " + errorMessage, Toast.LENGTH_LONG).show();
                            }
                        });
                    }
                }
        );

        // Khởi tạo danh sách rỗng ban đầu
        fullVideoList = new ArrayList<>();
        currentFilteredList = new ArrayList<>();
        popularVideoList = new ArrayList<>();

        // Cài đặt Adapters
        setupAdapter();

        // Khởi tạo Firestore và nạp dữ liệu
        db = FirebaseFirestore.getInstance();
        loadVideosFromFirebase();

        // Cài đặt các sự kiện
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

        viewPagerPopular = findViewById(R.id.viewPagerPopular);
        btnPrevPopular = findViewById(R.id.btnPrevPopular);
        btnNextPopular = findViewById(R.id.btnNextPopular);

        rvVideos.setLayoutManager(new LinearLayoutManager(this));
    }

    /**
     * Tải danh sách phim trực tiếp từ bộ sưu tập "videos" trên Firebase Firestore.
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

                // Nạp danh sách phim Nổi bật (Popular) dựa trên cờ isPopular()
                updatePopularSliderList();

                Toast.makeText(MainActivity.this, "Đã tải " + fullVideoList.size() + " video từ Firebase Firestore", Toast.LENGTH_SHORT).show();

            } else {
                Log.w(TAG, "Firebase Firestore chưa có dữ liệu hoặc kết nối thất bại.");
                Toast.makeText(MainActivity.this, "Chưa có dữ liệu video trên Firebase. Hãy bật Chế độ Admin để thêm!", Toast.LENGTH_LONG).show();
            }
        });
    }

    /**
     * Cập nhật danh sách các phim nổi bật (isPopular = true) lên ViewPager2 Slider.
     */
    private void updatePopularSliderList() {
        popularVideoList.clear();
        for (Video video : fullVideoList) {
            if (video.isPopular()) {
                popularVideoList.add(video);
            }
        }

        // Nếu Admin chưa tích chọn phim popular nào, mặc định nạp tối đa 5 phim đầu tiên làm slider
        if (popularVideoList.isEmpty()) {
            int sliderCount = Math.min(fullVideoList.size(), 5);
            for (int i = 0; i < sliderCount; i++) {
                popularVideoList.add(fullVideoList.get(i));
            }
        }

        if (popularVideoAdapter != null) {
            popularVideoAdapter.updateList(popularVideoList);
        }
    }

    /**
     * Khởi tạo Adapters cho RecyclerView và ViewPager2 Popular Slider.
     */
    private void setupAdapter() {
        // Adapter cho danh sách chính
        videoAdapter = new VideoAdapter(
                this, currentFilteredList,
                video -> {
                    Intent intent = new Intent(MainActivity.this, PlayerActivity.class);
                    intent.putExtra(PlayerActivity.EXTRA_VIDEO_URL, video.getVideoUrl());
                    intent.putExtra(PlayerActivity.EXTRA_VIDEO_TITLE, video.getTitle());
                    intent.putExtra(PlayerActivity.EXTRA_VIDEO_DESC, video.getDescription());
                    startActivity(intent);
                },
                (video, position) -> showAddEditVideoDialog(video),
                (video, position) -> confirmDeleteVideo(video)
        );
        rvVideos.setAdapter(videoAdapter);

        // Adapter cho ViewPager2 Popular Slider
        popularVideoAdapter = new PopularVideoAdapter(this, popularVideoList);
        viewPagerPopular.setAdapter(popularVideoAdapter);

        // Nút chuyển phim trên Slider Popular
        btnPrevPopular.setOnClickListener(v -> {
            int current = viewPagerPopular.getCurrentItem();
            if (current > 0) {
                viewPagerPopular.setCurrentItem(current - 1, true);
            }
        });

        btnNextPopular.setOnClickListener(v -> {
            int current = viewPagerPopular.getCurrentItem();
            int total = popularVideoAdapter.getItemCount();
            if (total > 0) {
                viewPagerPopular.setCurrentItem((current + 1) % total, true);
            }
        });
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
                Toast.makeText(this, "Đã BẬT Chế độ Admin (Lưu trực tiếp Firebase)", Toast.LENGTH_SHORT).show();
            } else {
                fabAddVideo.setVisibility(View.GONE);
                Toast.makeText(this, "Đã TẮT Chế độ Admin", Toast.LENGTH_SHORT).show();
            }
        });

        fabAddVideo.setOnClickListener(v -> showAddEditVideoDialog(null));
    }

    /**
     * Lọc danh sách video thời gian thực theo cả tiêu đề lẫn nội dung mô tả.
     */
    private void filterVideoList(String query) {
        currentFilteredList.clear();
        if (query == null || query.trim().isEmpty()) {
            currentFilteredList.addAll(fullVideoList);
        } else {
            String lowerCaseQuery = query.toLowerCase().trim();
            for (Video video : fullVideoList) {
                boolean matchTitle = video.getTitle() != null && video.getTitle().toLowerCase().contains(lowerCaseQuery);
                boolean matchDesc = video.getDescription() != null && video.getDescription().toLowerCase().contains(lowerCaseQuery);
                if (matchTitle || matchDesc) {
                    currentFilteredList.add(video);
                }
            }
        }
        videoAdapter.updateVideoList(currentFilteredList);
    }

    /**
     * Hiển thị Dialog Thêm/Sửa video và tích hợp chọn quyền Phim Nổi Bật (Popular) + Upload Video/Ảnh.
     */
    private void showAddEditVideoDialog(Video videoToEdit) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_edit_video, null);
        builder.setView(dialogView);

        TextView tvDialogTitle = dialogView.findViewById(R.id.tvDialogTitle);
        EditText etTitle = dialogView.findViewById(R.id.etDialogTitle);
        EditText etDescription = dialogView.findViewById(R.id.etDialogDescription);
        CheckBox cbIsPopular = dialogView.findViewById(R.id.cbIsPopular);
        EditText etVideoUrl = dialogView.findViewById(R.id.etDialogVideoUrl);
        EditText etThumbnailUrl = dialogView.findViewById(R.id.etDialogThumbnailUrl);
        Button btnPickVideo = dialogView.findViewById(R.id.btnPickVideo);
        Button btnPickImage = dialogView.findViewById(R.id.btnPickImage);

        // Sự kiện bấm chọn video từ máy
        btnPickVideo.setOnClickListener(v -> {
            currentEtVideoUrl = etVideoUrl;
            videoPickerLauncher.launch("video/*");
        });

        // Sự kiện bấm chọn ảnh bìa từ máy
        btnPickImage.setOnClickListener(v -> {
            currentEtThumbnailUrl = etThumbnailUrl;
            imagePickerLauncher.launch("image/*");
        });

        boolean isEditMode = (videoToEdit != null);
        if (isEditMode) {
            tvDialogTitle.setText("Sửa Thông Tin Video");
            etTitle.setText(videoToEdit.getTitle());
            etDescription.setText(videoToEdit.getDescription());
            cbIsPopular.setChecked(videoToEdit.isPopular());
            etVideoUrl.setText(videoToEdit.getVideoUrl());
            etThumbnailUrl.setText(videoToEdit.getThumbnailUrl());
        } else {
            tvDialogTitle.setText("Thêm Video Mới (Firebase)");
            cbIsPopular.setChecked(false);
        }

        builder.setPositiveButton(isEditMode ? "Cập Nhật" : "Thêm Mới", (dialog, which) -> {
            String title = etTitle.getText().toString().trim();
            String description = etDescription.getText().toString().trim();
            boolean isPopular = cbIsPopular.isChecked();
            String videoUrl = etVideoUrl.getText().toString().trim();
            String thumbnailUrl = etThumbnailUrl.getText().toString().trim();

            if (title.isEmpty() || videoUrl.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập đầy đủ Tiêu đề và URL Video!", Toast.LENGTH_SHORT).show();
                return;
            }

            Video videoTarget;
            if (isEditMode) {
                videoToEdit.setTitle(title);
                videoToEdit.setDescription(description);
                videoToEdit.setPopular(isPopular);
                videoToEdit.setVideoUrl(videoUrl);
                videoToEdit.setThumbnailUrl(thumbnailUrl);
                videoTarget = videoToEdit;
            } else {
                videoTarget = new Video(title, description, thumbnailUrl, videoUrl, isPopular);
                fullVideoList.add(0, videoTarget);
            }

            filterVideoList(etSearchQuery.getText().toString());
            updatePopularSliderList();

            // Đồng bộ trực tiếp lên Firebase Cloud Firestore
            db.collection("videos").document(videoTarget.getId()).set(videoTarget)
                    .addOnSuccessListener(aVoid -> Toast.makeText(this, "Đã lưu lên Firebase thành công!", Toast.LENGTH_SHORT).show())
                    .addOnFailureListener(e -> Log.e(TAG, "Lỗi đồng bộ Firebase", e));
        });

        builder.setNegativeButton("Hủy", (dialog, which) -> dialog.dismiss());
        builder.create().show();
    }

    /**
     * Xác nhận Xóa video khỏi Firebase Cloud Firestore.
     */
    private void confirmDeleteVideo(Video video) {
        new AlertDialog.Builder(this)
                .setTitle("Xác Nhận Xóa")
                .setMessage("Bạn có chắc chắn muốn xóa video \"" + video.getTitle() + "\" không?")
                .setPositiveButton("Xóa", (dialog, which) -> {
                    fullVideoList.remove(video);
                    filterVideoList(etSearchQuery.getText().toString());
                    updatePopularSliderList();

                    // Xóa dữ liệu trực tiếp khỏi Firebase Firestore
                    db.collection("videos").document(video.getId()).delete()
                            .addOnSuccessListener(aVoid -> Toast.makeText(this, "Đã xóa khỏi Firebase!", Toast.LENGTH_SHORT).show())
                            .addOnFailureListener(e -> Log.e(TAG, "Lỗi xóa trên Firebase", e));
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        sliderHandler.postDelayed(sliderRunnable, 12000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        sliderHandler.removeCallbacks(sliderRunnable);
    }
}
