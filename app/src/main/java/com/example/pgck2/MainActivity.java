package com.example.pgck2;

import android.app.ProgressDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
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
import android.widget.LinearLayout;
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

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MainActivity hiển thị danh sách phim/video đẳng cấp Cinema.
 * Hỗ trợ ViewPager2 Slider phim nổi bật (Popular), Tìm kiếm thời gian thực,
 * Lọc thể loại 3 Trạng Thái (Bình thường / ✓ Bắt buộc có / ✕ Blacklist),
 * Quản lý Thể Loại Phim Admin (Tạo/Xóa Thể Loại & Tự động đồng bộ gỡ bỏ khỏi Video),
 * Chế độ Admin đồng bộ trực tiếp với FIREBASE CLOUD FIRESTORE
 * và tích hợp Upload Video/Ảnh Bìa lên Đám mây qua CloudinaryUploader.
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "FIREBASE_TEST";

    private RecyclerView rvVideos;
    private VideoAdapter videoAdapter;
    private List<Video> fullVideoList;
    private List<Video> currentFilteredList;

    private ViewPager2 viewPagerPopular;
    private PopularVideoAdapter popularVideoAdapter;
    private List<Video> popularVideoList;

    private ImageButton btnToggleAdmin;
    private ImageButton btnToggleSearch;
    private CardView cardSearch;
    private EditText etSearchQuery;
    private ImageButton btnClearSearch;
    private ChipGroup chipGroupGenresFilter;
    private FloatingActionButton fabAddVideo;

    private boolean isAdminMode = false;
    private FirebaseFirestore db; // Đối tượng Firestore

    // Quản lý Thể Loại Phim (Genres)
    private List<Genre> globalGenreList;
    // Lọc Thể Loại 3 Trạng Thái: 0 = Normal/Ignore, 1 = YES/Include, 2 = BLACKLIST/Exclude
    private Map<String, Integer> genreFilterStates;

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
        globalGenreList = new ArrayList<>();
        genreFilterStates = new HashMap<>();

        // Cài đặt Adapters
        setupAdapter();

        // Khởi tạo Firestore và nạp dữ liệu
        db = FirebaseFirestore.getInstance();
        loadGenresFromFirebase();
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
        chipGroupGenresFilter = findViewById(R.id.chipGroupGenresFilter);
        fabAddVideo = findViewById(R.id.fabAddVideo);

        viewPagerPopular = findViewById(R.id.viewPagerPopular);

        rvVideos.setLayoutManager(new LinearLayoutManager(this));
    }

    /**
     * Tải danh sách Thể Loại Phim từ bộ sưu tập "genres" trên Firebase Firestore.
     * Nếu trống sẽ khởi tạo danh sách thể loại mặc định.
     */
    private void loadGenresFromFirebase() {
        db.collection("genres").get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null && !task.getResult().isEmpty()) {
                globalGenreList.clear();
                for (QueryDocumentSnapshot document : task.getResult()) {
                    Genre genre = document.toObject(Genre.class);
                    if (genre.getId() == null || genre.getId().isEmpty()) {
                        genre.setId(document.getId());
                    }
                    globalGenreList.add(genre);
                }
            } else {
                // Khởi tạo các thể loại mặc định nếu Firebase chưa có
                seedDefaultGenres();
            }
            setupGenreFilterChips();
        });
    }

    private void seedDefaultGenres() {
        String[] defaults = {"Hành Động", "Hài Hước", "Viễn Tưởng", "Tình Cảm", "Kinh Dị", "Hoạt Hình", "Phiêu Lưu"};
        globalGenreList.clear();
        for (String name : defaults) {
            Genre genre = new Genre(name);
            globalGenreList.add(genre);
            db.collection("genres").document(genre.getId()).set(genre);
        }
    }

    /**
     * Khởi tạo cụm Chips Thể Loại 3 Trạng Thái (0: Bình thường, 1: ✓ Bắt buộc có, 2: ✕ Blacklist).
     */
    private void setupGenreFilterChips() {
        if (chipGroupGenresFilter == null) return;
        chipGroupGenresFilter.removeAllViews();

        for (Genre genre : globalGenreList) {
            String name = genre.getName();
            if (!genreFilterStates.containsKey(name)) {
                genreFilterStates.put(name, 0); // Trạng thái mặc định: 0 (Normal)
            }

            Integer stateObj = genreFilterStates.get(name);
            int state = stateObj != null ? stateObj : 0;

            Chip chip = new Chip(this);
            chip.setText(getChipText(name, state));
            updateChipStyle(chip, state);

            chip.setOnClickListener(v -> {
                Integer currObj = genreFilterStates.get(name);
                int currentState = currObj != null ? currObj : 0;
                int nextState = (currentState + 1) % 3; // Lặp 0 -> 1 -> 2 -> 0
                genreFilterStates.put(name, nextState);

                chip.setText(getChipText(name, nextState));
                updateChipStyle(chip, nextState);

                // Lọc lại danh sách video ngay lập tức
                filterVideoList(etSearchQuery.getText().toString());
            });

            chipGroupGenresFilter.addView(chip);
        }
    }

    private String getChipText(String genreName, int state) {
        if (state == 1) return "✓ " + genreName;
        if (state == 2) return "✕ " + genreName;
        return genreName;
    }

    private void updateChipStyle(Chip chip, int state) {
        if (state == 1) { // YES / Included
            chip.setChipBackgroundColor(ColorStateList.valueOf(Color.parseColor("#4CAF50"))); // Green
            chip.setTextColor(Color.WHITE);
        } else if (state == 2) { // BLACKLIST / Excluded
            chip.setChipBackgroundColor(ColorStateList.valueOf(Color.parseColor("#E50914"))); // Red Accent
            chip.setTextColor(Color.WHITE);
        } else { // Normal / Ignored
            chip.setChipBackgroundColor(ColorStateList.valueOf(Color.parseColor("#2A2A38"))); // Dark Gray
            chip.setTextColor(Color.parseColor("#AAAAAA"));
        }
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
                }

                filterVideoList(etSearchQuery.getText().toString());
                updatePopularSliderList();

                Toast.makeText(MainActivity.this, "Đã tải " + fullVideoList.size() + " video từ Firebase Firestore", Toast.LENGTH_SHORT).show();

            } else {
                Log.w(TAG, "Firebase Firestore chưa có dữ liệu hoặc kết nối thất bại.");
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

    private void setupAdapter() {
        videoAdapter = new VideoAdapter(
                this, currentFilteredList,
                video -> {
                    Intent intent = new Intent(MainActivity.this, PlayerActivity.class);
                    intent.putExtra(PlayerActivity.EXTRA_VIDEO_URL, video.getVideoUrl());
                    intent.putExtra(PlayerActivity.EXTRA_VIDEO_TITLE, video.getTitle());
                    intent.putExtra(PlayerActivity.EXTRA_VIDEO_DESC, video.getDescription());
                    intent.putExtra("extra_video_genres", video.getGenresString());
                    startActivity(intent);
                },
                (video, position) -> showAddEditVideoDialog(video),
                (video, position) -> confirmDeleteVideo(video)
        );
        rvVideos.setAdapter(videoAdapter);

        popularVideoAdapter = new PopularVideoAdapter(this, popularVideoList);
        viewPagerPopular.setAdapter(popularVideoAdapter);
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
                Toast.makeText(this, "Đã BẬT Chế độ Admin", Toast.LENGTH_SHORT).show();
            } else {
                fabAddVideo.setVisibility(View.GONE);
                Toast.makeText(this, "Đã TẮT Chế độ Admin", Toast.LENGTH_SHORT).show();
            }
        });

        fabAddVideo.setOnClickListener(v -> showAddEditVideoDialog(null));
    }

    /**
     * Lọc danh sách video kết hợp Tìm kiếm Từ Khóa và Bộ Lọc Thể Loại 3 Trạng Thái (Yes/Blacklist/No).
     */
    private void filterVideoList(String query) {
        currentFilteredList.clear();
        String lowerCaseQuery = (query != null) ? query.toLowerCase().trim() : "";

        for (Video video : fullVideoList) {
            // 1. Lọc theo từ khóa tìm kiếm
            boolean matchText = lowerCaseQuery.isEmpty()
                    || (video.getTitle() != null && video.getTitle().toLowerCase().contains(lowerCaseQuery))
                    || (video.getDescription() != null && video.getDescription().toLowerCase().contains(lowerCaseQuery));

            if (!matchText) continue;

            // 2. Lọc theo Thể Loại 3 Trạng Thái
            boolean matchGenres = true;
            List<String> videoGenres = video.getGenres();

            for (Map.Entry<String, Integer> entry : genreFilterStates.entrySet()) {
                String genreName = entry.getKey();
                int state = entry.getValue();

                if (state == 1) { // YES -> Video BẮT BUỘC phải có thể loại này
                    if (videoGenres == null || !videoGenres.contains(genreName)) {
                        matchGenres = false;
                        break;
                    }
                } else if (state == 2) { // BLACKLIST -> Video CẤM/LOẠI TRỪ thể loại này
                    if (videoGenres != null && videoGenres.contains(genreName)) {
                        matchGenres = false;
                        break;
                    }
                }
            }

            if (matchGenres) {
                currentFilteredList.add(video);
            }
        }
        videoAdapter.updateVideoList(currentFilteredList);
    }

    /**
     * Dialog Thêm/Sửa Video hỗ trợ gán Thể Loại và Quản lý Thể Loại Admin.
     */
    private void showAddEditVideoDialog(Video videoToEdit) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_edit_video, null);
        builder.setView(dialogView);

        TextView tvDialogTitle = dialogView.findViewById(R.id.tvDialogTitle);
        EditText etTitle = dialogView.findViewById(R.id.etDialogTitle);
        EditText etDescription = dialogView.findViewById(R.id.etDialogDescription);
        TextView tvSelectedGenres = dialogView.findViewById(R.id.tvSelectedGenres);
        Button btnSelectGenres = dialogView.findViewById(R.id.btnSelectGenres);
        Button btnManageGenres = dialogView.findViewById(R.id.btnManageGenres);
        CheckBox cbIsPopular = dialogView.findViewById(R.id.cbIsPopular);
        EditText etVideoUrl = dialogView.findViewById(R.id.etDialogVideoUrl);
        EditText etThumbnailUrl = dialogView.findViewById(R.id.etDialogThumbnailUrl);
        Button btnPickVideo = dialogView.findViewById(R.id.btnPickVideo);
        Button btnPickImage = dialogView.findViewById(R.id.btnPickImage);

        List<String> tempSelectedGenres = new ArrayList<>();

        boolean isEditMode = (videoToEdit != null);
        if (isEditMode) {
            tvDialogTitle.setText("Sửa Thông Tin Video");
            etTitle.setText(videoToEdit.getTitle());
            etDescription.setText(videoToEdit.getDescription());
            cbIsPopular.setChecked(videoToEdit.isPopular());
            etVideoUrl.setText(videoToEdit.getVideoUrl());
            etThumbnailUrl.setText(videoToEdit.getThumbnailUrl());
            if (videoToEdit.getGenres() != null) {
                tempSelectedGenres.addAll(videoToEdit.getGenres());
            }
        } else {
            tvDialogTitle.setText("Thêm Video Mới (Firebase)");
            cbIsPopular.setChecked(false);
        }

        updateSelectedGenresText(tvSelectedGenres, tempSelectedGenres);

        // Bấm chọn thể loại cho video
        btnSelectGenres.setOnClickListener(v -> {
            String[] genreNames = new String[globalGenreList.size()];
            boolean[] checkedItems = new boolean[globalGenreList.size()];

            for (int i = 0; i < globalGenreList.size(); i++) {
                String name = globalGenreList.get(i).getName();
                genreNames[i] = name;
                checkedItems[i] = tempSelectedGenres.contains(name);
            }

            new AlertDialog.Builder(this)
                    .setTitle("Chọn Thể Loại Phim")
                    .setMultiChoiceItems(genreNames, checkedItems, (dialog1, which, isChecked) -> {
                        String name = genreNames[which];
                        if (isChecked) {
                            if (!tempSelectedGenres.contains(name)) tempSelectedGenres.add(name);
                        } else {
                            tempSelectedGenres.remove(name);
                        }
                    })
                    .setPositiveButton("Xác Nhận", (dialog1, which) -> updateSelectedGenresText(tvSelectedGenres, tempSelectedGenres))
                    .setNegativeButton("Hủy", null)
                    .show();
        });

        // Bấm Quản lý Thể Loại Admin (Tạo/Xóa thể loại toàn hệ thống)
        btnManageGenres.setOnClickListener(v -> showAdminManageGenresDialog(tvSelectedGenres, tempSelectedGenres));

        btnPickVideo.setOnClickListener(v -> {
            currentEtVideoUrl = etVideoUrl;
            videoPickerLauncher.launch("video/*");
        });

        btnPickImage.setOnClickListener(v -> {
            currentEtThumbnailUrl = etThumbnailUrl;
            imagePickerLauncher.launch("image/*");
        });

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
                videoToEdit.setGenres(new ArrayList<>(tempSelectedGenres));
                videoTarget = videoToEdit;
            } else {
                videoTarget = new Video(title, description, thumbnailUrl, videoUrl, isPopular);
                videoTarget.setGenres(new ArrayList<>(tempSelectedGenres));
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

    private void updateSelectedGenresText(TextView tv, List<String> list) {
        if (list == null || list.isEmpty()) {
            tv.setText("Thể loại đã chọn: Chưa chọn thể loại nào");
        } else {
            StringBuilder sb = new StringBuilder("Thể loại đã chọn: ");
            for (int i = 0; i < list.size(); i++) {
                sb.append(list.get(i));
                if (i < list.size() - 1) sb.append(", ");
            }
            tv.setText(sb.toString());
        }
    }

    /**
     * Dialog Admin Quản Lý Thể Loại (Tạo mới hoặc Xóa Thể Loại toàn hệ thống).
     * Khi xóa thể loại -> tự động gỡ bỏ thể loại đó khỏi tất cả video trong Firestore.
     */
    private void showAdminManageGenresDialog(TextView tvSelectedGenres, List<String> tempSelectedGenres) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("⚙️ Admin Quản Lý Thể Loại Phim");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 16, 32, 16);

        EditText etNewGenre = new EditText(this);
        etNewGenre.setHint("Nhập tên thể loại mới...");
        layout.addView(etNewGenre);

        Button btnAddNew = new Button(this);
        btnAddNew.setText("➕ Tạo Thể Loại Mới");
        layout.addView(btnAddNew);

        TextView tvListHeader = new TextView(this);
        tvListHeader.setText("\nDanh sách thể loại hiện có (Bấm để Xóa):");
        tvListHeader.setTypeface(null, Typeface.BOLD);
        layout.addView(tvListHeader);

        builder.setView(layout);
        AlertDialog dialog = builder.setNegativeButton("Đóng", null).create();

        btnAddNew.setOnClickListener(v -> {
            String newName = etNewGenre.getText().toString().trim();
            if (newName.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập tên thể loại!", Toast.LENGTH_SHORT).show();
                return;
            }

            Genre newGenre = new Genre(newName);
            globalGenreList.add(newGenre);
            db.collection("genres").document(newGenre.getId()).set(newGenre);

            setupGenreFilterChips();
            etNewGenre.setText("");
            Toast.makeText(this, "Đã tạo thể loại: " + newName, Toast.LENGTH_SHORT).show();
            dialog.dismiss();
            showAdminManageGenresDialog(tvSelectedGenres, tempSelectedGenres);
        });

        // Bổ sung các dòng thể loại hiện có để xóa
        for (Genre g : globalGenreList) {
            Button btnGenreItem = new Button(this);
            btnGenreItem.setText("🗑️ Xóa: " + g.getName());
            btnGenreItem.setTextColor(Color.RED);

            btnGenreItem.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setTitle("Xóa Thể Loại")
                    .setMessage("Xóa thể loại \"" + g.getName() + "\" sẽ gỡ bỏ thẻ này khỏi tất cả video. Xác nhận xóa?")
                    .setPositiveButton("Xóa", (dialog1, which) -> {
                        String deletedName = g.getName();
                        globalGenreList.remove(g);
                        db.collection("genres").document(g.getId()).delete();

                        // Tự động gỡ bỏ thể loại này khỏi toàn bộ video trên Firestore
                        for (Video video : fullVideoList) {
                            if (video.getGenres() != null && video.getGenres().contains(deletedName)) {
                                video.getGenres().remove(deletedName);
                                db.collection("videos").document(video.getId()).set(video);
                            }
                        }

                        tempSelectedGenres.remove(deletedName);
                        updateSelectedGenresText(tvSelectedGenres, tempSelectedGenres);

                        setupGenreFilterChips();
                        filterVideoList(etSearchQuery.getText().toString());

                        Toast.makeText(this, "Đã xóa thể loại \"" + deletedName + "\" khỏi toàn hệ thống!", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    })
                    .setNegativeButton("Hủy", null)
                    .show());

            layout.addView(btnGenreItem);
        }

        dialog.show();
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
