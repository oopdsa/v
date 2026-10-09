package com.example.pgck2;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
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

import java.util.ArrayList;
import java.util.List;

/**
 * MainActivity hiển thị danh sách video, hỗ trợ Tìm Kiếm, Chế độ Admin (Thêm/Sửa/Xóa video)
 * và lưu trữ dữ liệu bền vững qua SharedPreferences.
 */
public class MainActivity extends AppCompatActivity {

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

        // Ánh xạ các thành phần View
        initViews();

        // Nạp danh sách video từ Storage
        fullVideoList = VideoStorage.loadVideos(this);
        currentFilteredList = new ArrayList<>(fullVideoList);

        // Khởi tạo VideoAdapter với 3 listener (Click xem, Admin Sửa, Admin Xóa)
        setupAdapter();

        // Cài đặt các sự kiện Tìm Kiếm và Chế độ Admin
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
     * Khởi tạo Adapter và xử lý 3 hành động: Phát phim, Sửa video (Admin), Xóa video (Admin).
     */
    private void setupAdapter() {
        videoAdapter = new VideoAdapter(
                this, currentFilteredList,
                video -> {
                    // 1. Hành động CLICK XEM VIDEO -> Mở PlayerActivity
                    Intent intent = new Intent(MainActivity.this, PlayerActivity.class);
                    intent.putExtra(PlayerActivity.EXTRA_VIDEO_URL, video.getVideoUrl());
                    intent.putExtra(PlayerActivity.EXTRA_VIDEO_TITLE, video.getTitle());
                    startActivity(intent);
                },
                (video, position) -> {
                    // 2. Hành động SỬA VIDEO (Admin Mode)
                    showAddEditVideoDialog(video);
                },
                (video, position) -> {
                    // 3. Hành động XÓA VIDEO (Admin Mode)
                    confirmDeleteVideo(video);
                }
        );

        rvVideos.setAdapter(videoAdapter);
    }

    /**
     * Thiết lập sự kiện Bật/Tắt tìm kiếm, Tìm kiếm thời gian thực và Bật/Tắt Chế độ Admin.
     */
    private void setupSearchAndAdminEvents() {
        // Nút Bật/Tắt Thanh Tìm Kiếm
        btnToggleSearch.setOnClickListener(v -> {
            if (cardSearch.getVisibility() == View.VISIBLE) {
                cardSearch.setVisibility(View.GONE);
                etSearchQuery.setText("");
            } else {
                cardSearch.setVisibility(View.VISIBLE);
                etSearchQuery.requestFocus();
            }
        });

        // Lắng nghe sự kiện gõ phím tìm kiếm thời gian thực
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

        // Nút Xóa từ khóa tìm kiếm
        btnClearSearch.setOnClickListener(v -> etSearchQuery.setText(""));

        // Nút Bật/Tắt Chế độ Admin
        btnToggleAdmin.setOnClickListener(v -> {
            isAdminMode = !isAdminMode;
            videoAdapter.setAdminMode(isAdminMode);

            if (isAdminMode) {
                fabAddVideo.setVisibility(View.VISIBLE);
                Toast.makeText(this, "Đã BẬT Chế độ Admin (Cho phép Thêm / Sửa / Xóa)", Toast.LENGTH_SHORT).show();
            } else {
                fabAddVideo.setVisibility(View.GONE);
                Toast.makeText(this, "Đã TẮT Chế độ Admin", Toast.LENGTH_SHORT).show();
            }
        });

        // Nút Thêm Video Mới (FAB)
        fabAddVideo.setOnClickListener(v -> showAddEditVideoDialog(null));
    }

    /**
     * Lọc danh sách video theo từ khóa tìm kiếm.
     */
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
     * Hiển thị Dialog cho phép Admin Thêm video mới hoặc Sửa video có sẵn.
     *
     * @param videoToEdit null nếu là THÊM MỚI, có đối tượng nếu là SỬA
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
            tvDialogTitle.setText("Thêm Video Mới (Admin)");
        }

        builder.setPositiveButton(isEditMode ? "Cập Nhật" : "Thêm Mới", (dialog, which) -> {
            String title = etTitle.getText().toString().trim();
            String videoUrl = etVideoUrl.getText().toString().trim();
            String thumbnailUrl = etThumbnailUrl.getText().toString().trim();

            if (title.isEmpty() || videoUrl.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập đầy đủ Tiêu đề và URL Video!", Toast.LENGTH_SHORT).show();
                return;
            }

            if (isEditMode) {
                videoToEdit.setTitle(title);
                videoToEdit.setVideoUrl(videoUrl);
                videoToEdit.setThumbnailUrl(thumbnailUrl);
                Toast.makeText(this, "Đã cập nhật video!", Toast.LENGTH_SHORT).show();
            } else {
                Video newVideo = new Video(title, thumbnailUrl, videoUrl);
                fullVideoList.add(0, newVideo);
                Toast.makeText(this, "Đã thêm video mới!", Toast.LENGTH_SHORT).show();
            }

            // Lưu dữ liệu vĩnh viễn và cập nhật giao diện
            VideoStorage.saveVideos(this, fullVideoList);
            filterVideoList(etSearchQuery.getText().toString());
        });

        builder.setNegativeButton("Hủy", (dialog, which) -> dialog.dismiss());
        builder.create().show();
    }

    /**
     * Hiển thị Dialog xác nhận xóa video.
     */
    private void confirmDeleteVideo(Video video) {
        new AlertDialog.Builder(this)
                .setTitle("Xác Nhận Xóa")
                .setMessage("Bạn có chắc chắn muốn xóa video \"" + video.getTitle() + "\" không?")
                .setPositiveButton("Xóa", (dialog, which) -> {
                    fullVideoList.remove(video);
                    VideoStorage.saveVideos(this, fullVideoList);
                    filterVideoList(etSearchQuery.getText().toString());
                    Toast.makeText(this, "Đã xóa video thành công!", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Hủy", null)
                .show();
    }
}
