package com.example.pgck2;

import android.app.ProgressDialog;
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
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MainActivity hiển thị danh sách phim/video đẳng cấp Cinema.
 * Hỗ trợ ViewPager2 Slider phim nổi bật (Popular), Tìm kiếm thời gian thực,
 * Danh sách Top 10 Phim Trong Tháng (Tiếng Việt 100%, cho phép bạn điền URL video),
 * Chuyển đổi 2 chế độ View (Mặc Định Cuộn Ngang Top 10 vs Xem Tất Cả Lưới 3 Cột 18 Phim/Trang Phân Trang),
 * Màn hình Chi Tiết Phim Netflix Style (Trailer Preview + Quản lý Tập Phim Episodes + Năm/Tháng phát hành),
 * Bộ Lọc Thể Loại 3 Trạng Thái Netflix Style (Bình thường / ✓ Bắt buộc / ✕ Blacklist),
 * Quản lý Thể Loại Phim & Luồng Âm Thanh (Dub) / Phụ Đề (Sub CC) Admin,
 * Chế độ Admin đồng bộ trực tiếp với FIREBASE CLOUD FIRESTORE.
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "FIREBASE_TEST";

    private RecyclerView rvVideos;
    private VideoAdapter videoAdapter;
    private List<Video> fullVideoList;
    private List<Video> currentFilteredList;

    // Danh sách Top 10 Phim Trong Tháng Demo Tiếng Việt
    private RecyclerView rvTop10Month;
    private VideoAdapter top10MonthAdapter;
    private List<Video> top10MonthList;
    private View layoutTop10MonthHeader;

    private ViewPager2 viewPagerPopular;
    private PopularVideoAdapter popularVideoAdapter;
    private List<Video> popularVideoList;
    private View layoutPopularSlider;

    private MaterialButton btnToggleViewMode;
    private TextView btnSwitchViewMode;
    private TextView tvSectionTitle;
    private ImageButton btnToggleAdmin;
    private ImageButton btnToggleSearch;
    private CardView cardSearch;
    private EditText etSearchQuery;
    private ImageButton btnClearSearch;
    private TextView btnResetFilter;
    private ChipGroup chipGroupGenresFilter;
    private FloatingActionButton fabAddVideo;
    private NestedScrollView nestedScrollView;

    // Thanh phân trang Lưới 3 cột (18 phim / trang)
    private LinearLayout layoutPagination;
    private MaterialButton btnPrevPage;
    private MaterialButton btnNextPage;
    private TextView tvPageIndicator;
    private int currentPage = 1;
    private static final int PAGE_SIZE = 18;

    private boolean isAdminMode = false;
    private boolean isAllViewMode = false;
    private FirebaseFirestore db;

    // Quản lý Thể Loại Phim (Genres)
    private List<Genre> globalGenreList;
    private Map<String, Integer> genreFilterStates;

    // Launchers chọn Video và Ảnh
    private ActivityResultLauncher<String> videoPickerLauncher;
    private ActivityResultLauncher<String> imagePickerLauncher;
    private ActivityResultLauncher<String> audioPickerLauncher;
    private ProgressDialog progressDialog;

    private EditText currentEtVideoUrl;
    private EditText currentEtThumbnailUrl;
    private EditText currentEtAudioUrl;

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
            sliderHandler.postDelayed(this, 12000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();

        progressDialog = new ProgressDialog(this);
        progressDialog.setCancelable(false);

        audioPickerLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri == null || currentEtAudioUrl == null) return;
            EditText target = currentEtAudioUrl;
            progressDialog.setMessage("Đang tải âm thanh lên Cloudinary...");
            progressDialog.show();
            CloudinaryUploader.uploadAudio(this, uri, new CloudinaryUploader.UploadCallback() {
                @Override public void onSuccess(String url) {
                    if (isFinishing() || isDestroyed()) return;
                    progressDialog.dismiss();
                    target.setText(url);
                }
                @Override public void onError(String message) {
                    if (isFinishing() || isDestroyed()) return;
                    progressDialog.dismiss();
                    Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                }
            });
        });

        videoPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        EditText target = currentEtVideoUrl;
                        progressDialog.setMessage("Đang tải video lên đám mây, vui lòng chờ...");
                        progressDialog.show();
                        CloudinaryUploader.uploadMedia(this, uri, true, new CloudinaryUploader.UploadCallback() {
                            @Override
                            public void onSuccess(String secureUrl) {
                                progressDialog.dismiss();
                                Toast.makeText(MainActivity.this, "Tải video thành công!", Toast.LENGTH_SHORT).show();
                                if (target != null) {
                                    target.setText(secureUrl);
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

        fullVideoList = new ArrayList<>();
        currentFilteredList = new ArrayList<>();
        popularVideoList = new ArrayList<>();
        top10MonthList = new ArrayList<>();
        globalGenreList = new ArrayList<>();
        genreFilterStates = new HashMap<>();

        seedLocalSampleData();
        setupAdapter();
        applyViewMode();

        try {
            db = FirebaseFirestore.getInstance();
            loadGenresFromFirebase();
            loadVideosFromFirebase();
        } catch (Exception e) {
            Log.e(TAG, "Lỗi kết nối Firebase Firestore", e);
        }

        setupSearchAndAdminEvents();
    }

    private void initViews() {
        rvVideos = findViewById(R.id.rvVideos);
        rvTop10Month = findViewById(R.id.rvTop10Month);
        layoutTop10MonthHeader = findViewById(R.id.layoutTop10MonthHeader);

        btnToggleAdmin = findViewById(R.id.btnToggleAdmin);
        btnToggleSearch = findViewById(R.id.btnToggleSearch);
        btnToggleViewMode = findViewById(R.id.btnToggleViewMode);
        btnSwitchViewMode = findViewById(R.id.btnSwitchViewMode);
        tvSectionTitle = findViewById(R.id.tvSectionTitle);
        btnResetFilter = findViewById(R.id.btnResetFilter);
        cardSearch = findViewById(R.id.cardSearch);
        etSearchQuery = findViewById(R.id.etSearchQuery);
        btnClearSearch = findViewById(R.id.btnClearSearch);
        chipGroupGenresFilter = findViewById(R.id.chipGroupGenresFilter);
        fabAddVideo = findViewById(R.id.fabAddVideo);
        nestedScrollView = findViewById(R.id.nestedScrollView);

        viewPagerPopular = findViewById(R.id.viewPagerPopular);
        layoutPopularSlider = findViewById(R.id.layoutPopularSlider);

        layoutPagination = findViewById(R.id.layoutPagination);
        btnPrevPage = findViewById(R.id.btnPrevPage);
        btnNextPage = findViewById(R.id.btnNextPage);
        tvPageIndicator = findViewById(R.id.tvPageIndicator);
    }

    private void seedLocalSampleData() {
        String[] defaults = {"Hành Động", "Hài Hước", "Viễn Tưởng", "Tình Cảm", "Kinh Dị", "Hoạt Hình", "Phiêu Lưu"};
        globalGenreList.clear();
        for (String name : defaults) {
            globalGenreList.add(new Genre(name));
            genreFilterStates.put(name, 0);
        }

        fullVideoList.clear();
        Video v1 = new Video("Thỏ Bunny Khổng Lồ 4K", "Bộ phim hoạt hình 3D nổi tiếng kể về chú thỏ khổng lồ Bunny trong rừng xanh.", "https://img.youtube.com/vi/aqz-KE-bpKQ/hqdefault.jpg", "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8", true);
        v1.setGenres(new ArrayList<>(Arrays.asList("Hoạt Hình", "Hài Hước", "Phiêu Lưu")));
        v1.setReleaseYear("2024");
        v1.setReleaseMonth("10");
        v1.getAudioTracks().add(new AudioTrackOption("Tiếng Việt (Lồng tiếng / Dub)", "vi", ""));
        v1.getAudioTracks().add(new AudioTrackOption("Tiếng Anh (Gốc)", "en", ""));
        v1.getSubtitleTracks().add(new SubtitleOption("Tiếng Việt (VTT)", "vi", "https://storage.googleapis.com/exoplayer-test-media-1/webvtt/numeric-subtitles-en.vtt"));
        v1.getSubtitleTracks().add(new SubtitleOption("Tiếng Anh (VTT)", "en", "https://storage.googleapis.com/exoplayer-test-media-1/webvtt/numeric-subtitles-en.vtt"));
        v1.getEpisodes().add(new Episode(1, "Tập 1: Rừng Xanh Khoáng Đạt", "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8"));
        v1.getEpisodes().add(new Episode(2, "Tập 2: Chú Thỏ Bunny Dũng Cảm", "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8"));
        v1.getEpisodes().add(new Episode(3, "Tập 3: Trận Chiến Bảo Vệ Rừng", "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8"));

        Video v2 = new Video("Nước Mắt Thép: Viễn Tưởng 2024", "Phim viễn tưởng kỹ xảo đỉnh cao về cuộc chiến bảo vệ Trái Đất khỏi thảm họa robot.", "https://img.youtube.com/vi/e1Ebe1sB70M/hqdefault.jpg", "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8", true);
        v2.setGenres(new ArrayList<>(Arrays.asList("Viễn Tưởng", "Hành Động")));
        v2.setReleaseYear("2024");

        Video v3 = new Video("Sintel: Cuộc Phiêu Lưu Kì Thú", "Hành trình tìm kiếm chú rồng con kỳ thú của cô gái trẻ dũng cảm Sintel.", "https://img.youtube.com/vi/e1Ebe1sB70M/hqdefault.jpg", "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8", true);
        v3.setGenres(new ArrayList<>(Arrays.asList("Phiêu Lưu", "Hoạt Hình")));
        v3.setReleaseYear("2023");

        Video v4 = new Video("Phim Chó Cún Thông Minh", "Những khoảnh khắc đáng yêu và thông minh hài hước của chú chó cún xinh xắn.", "https://img.youtube.com/vi/aqz-KE-bpKQ/hqdefault.jpg", "https://www.youtube.com/watch?v=aqz-KE-bpKQ", false);
        v4.setGenres(new ArrayList<>(Arrays.asList("Hài Hước", "Hành Động")));

        Video v5 = new Video("Thế Giới Động Vật: Đàn Bò Ăn Cỏ", "Thước phim tài liệu thiên nhiên hoang dã sinh động về đàn bò trên đồng cỏ.", "https://img.youtube.com/vi/aqz-KE-bpKQ/hqdefault.jpg", "https://www.youtube.com/watch?v=aqz-KE-bpKQ", false);
        v5.setGenres(new ArrayList<>(Arrays.asList("Hoạt Hình", "Phiêu Lưu")));

        Video v6 = new Video("Chúa Tể Rừng Xanh: Voi Khổng Lồ", "Cuộc phiêu lưu viễn tưởng kỳ thú trên vùng đất hoang sơ kỳ vĩ.", "https://img.youtube.com/vi/aqz-KE-bpKQ/hqdefault.jpg", "https://www.youtube.com/watch?v=aqz-KE-bpKQ", false);
        v6.setGenres(new ArrayList<>(Arrays.asList("Phiêu Lưu", "Viễn Tưởng")));

        fullVideoList.add(v1);
        fullVideoList.add(v2);
        fullVideoList.add(v3);
        fullVideoList.add(v4);
        fullVideoList.add(v5);
        fullVideoList.add(v6);

        currentFilteredList.clear();
        currentFilteredList.addAll(fullVideoList);

        // Nạp 10 video mẫu cứng Tiếng Việt cho TOP 10 PHIM TRONG THÁNG (Cho phép bạn tự thay thế URL Video)
        top10MonthList.clear();

        // 1. Phim Top 1
        Video t1 = new Video(
                "Paddleton",
                "Demo",
                "https://image.tmdb.org/t/p/w600_and_h900_bestv2/gtssTSvNgY8132hPRtp1DbciYJ6.jpg",
                "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8" // Thay URL video Top 1 tại đây
        );
        t1.setReleaseYear("2024");
        t1.setReleaseMonth("10");
        t1.setGenres(new ArrayList<>(Arrays.asList("Hành Động", "Viễn Tưởng", "Phiêu Lưu")));
        top10MonthList.add(t1);

        // 2. Phim Top 2
        Video t2 = new Video(
                "Orange",
                "Demo",
                "https://thumbs.dreamstime.com/b/cute-cat-sleeping-street-car-random-58655731.jpg",
                "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8" // Thay URL video Top 2 tại đây
        );
        t2.setReleaseYear("2024");
        t2.setReleaseMonth("09");
        t2.setGenres(new ArrayList<>(Arrays.asList("Hành Động", "Tình Cảm")));
        top10MonthList.add(t2);

        // 3. Phim Top 3
        Video t3 = new Video(
                "Flyboys",
                "Demo",
                "https://image.tmdb.org/t/p/w600_and_h900_bestv2/ArKIGwJohxskEIjVOqEkzsEXkpc.jpg",
                "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8" // Thay URL video Top 3 tại đây
        );
        t3.setReleaseYear("2024");
        t3.setReleaseMonth("08");
        t3.setGenres(new ArrayList<>(Arrays.asList("Hoạt Hình", "Hài Hước", "Phiêu Lưu")));
        top10MonthList.add(t3);

        // 4. Phim Top 4
        Video t4 = new Video(
                "The Stranger",
                "Những hiện tượng kỳ bí liên tục xảy ra khiến nhóm thám tử trẻ phải dấn thân vào ngôi biệt thự ma quái.",
                "https://m.media-amazon.com/images/S/compressed.photo.goodreads.com/books/1738704267i/49552.jpg",
                "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8" // Thay URL video Top 4 tại đây
        );
        t4.setReleaseYear("2024");
        t4.setReleaseMonth("07");
        t4.setGenres(new ArrayList<>(Arrays.asList("Kinh Dị", "Phiêu Lưu")));
        top10MonthList.add(t4);

        // 5. Phim Top 5
        Video t5 = new Video(
                "Cuộc Phiêu Lưu Của Sintel Vùng Đất Rồng",
                "Hành trình vượt qua muôn vàn trắc trở của cô gái trẻ Sintel trên đường tìm lại chú rồng con đi lạc.",
                "https://img.youtube.com/vi/e1Ebe1sB70M/hqdefault.jpg",
                "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8" // Thay URL video Top 5 tại đây
        );
        t5.setReleaseYear("2023");
        t5.setReleaseMonth("12");
        t5.setGenres(new ArrayList<>(Arrays.asList("Hoạt Hình", "Phiêu Lưu", "Viễn Tưởng")));
        top10MonthList.add(t5);

        // 6. Phim Top 6
        Video t6 = new Video(
                "Tiếng Gọi Nơi Hoang Dã: Thung Lũng Tuyết",
                "Khám phá thiên nhiên hoang dã hùng vĩ cùng lòng dũng cảm phi thường của muôn loài động vật.",
                "https://img.youtube.com/vi/aqz-KE-bpKQ/hqdefault.jpg",
                "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8" // Thay URL video Top 6 tại đây
        );
        t6.setReleaseYear("2024");
        t6.setReleaseMonth("06");
        t6.setGenres(new ArrayList<>(Arrays.asList("Phiêu Lưu", "Hoạt Hình")));
        top10MonthList.add(t6);

        // 7. Phim Top 7
        Video t7 = new Video(
                "Hài Hước Phố Cổ: Chuyện Tình Bún Đậu",
                "Những tình huống dở khóc dở cười giữa hai gia đình hàng xóm truyền thống tại Hà Nội.",
                "https://img.youtube.com/vi/aqz-KE-bpKQ/hqdefault.jpg",
                "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8" // Thay URL video Top 7 tại đây
        );
        t7.setReleaseYear("2024");
        t7.setReleaseMonth("05");
        t7.setGenres(new ArrayList<>(Arrays.asList("Hài Hước", "Tình Cảm")));
        top10MonthList.add(t7);

        // 8. Phim Top 8
        Video t8 = new Video(
                "Vệ Binh Vũ Trụ: Kỷ Nguyên Mới",
                "Hành trình khám phá các hành tinh xa xôi và chiến đấu bảo vệ nền hòa bình ngân hà.",
                "https://img.youtube.com/vi/e1Ebe1sB70M/hqdefault.jpg",
                "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8" // Thay URL video Top 8 tại đây
        );
        t8.setReleaseYear("2024");
        t8.setReleaseMonth("04");
        t8.setGenres(new ArrayList<>(Arrays.asList("Viễn Tưởng", "Hành Động")));
        top10MonthList.add(t8);

        // 9. Phim Top 9
        Video t9 = new Video(
                "Bóng Đêm Kinh Hoàng: Lời Nguyền Cổ Xưa",
                "Cuộc truy tìm sự thật kinh hoàng đằng sau những lời nguyền đã bị lãng quên hàng thế kỷ.",
                "https://img.youtube.com/vi/e1Ebe1sB70M/hqdefault.jpg",
                "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8" // Thay URL video Top 9 tại đây
        );
        t9.setReleaseYear("2024");
        t9.setReleaseMonth("03");
        t9.setGenres(new ArrayList<>(Arrays.asList("Kinh Dị", "Hành Động")));
        top10MonthList.add(t9);

        // 10. Phim Top 10
        Video t10 = new Video(
                "Thế Giới Động Vật: Vương Quốc Voi Khổng Lồ",
                "Thước phim tài liệu sống động và chân thực về đàn voi trong rừng nguyên sinh Châu Phi.",
                "https://img.youtube.com/vi/aqz-KE-bpKQ/hqdefault.jpg",
                "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8" // Thay URL video Top 10 tại đây
        );
        t10.setReleaseYear("2024");
        t10.setReleaseMonth("02");
        t10.setGenres(new ArrayList<>(Arrays.asList("Phiêu Lưu", "Hoạt Hình")));
        top10MonthList.add(t10);

        updatePopularSliderList();
    }

    private List<Video> getPaginatedList() {
        if (currentFilteredList == null || currentFilteredList.isEmpty()) {
            return new ArrayList<>();
        }
        int total = currentFilteredList.size();
        int maxPages = (int) Math.ceil((double) total / PAGE_SIZE);
        if (maxPages == 0) maxPages = 1;

        if (currentPage < 1) currentPage = 1;
        if (currentPage > maxPages) currentPage = maxPages;

        int startIndex = (currentPage - 1) * PAGE_SIZE;
        int endIndex = Math.min(startIndex + PAGE_SIZE, total);

        if (startIndex >= total) {
            return new ArrayList<>();
        }

        return new ArrayList<>(currentFilteredList.subList(startIndex, endIndex));
    }

    private void updatePaginationUI() {
        if (!isAllViewMode || currentFilteredList == null || currentFilteredList.isEmpty()) {
            if (layoutPagination != null) layoutPagination.setVisibility(View.GONE);
            return;
        }

        if (layoutPagination != null) layoutPagination.setVisibility(View.VISIBLE);

        int total = currentFilteredList.size();
        int maxPages = (int) Math.ceil((double) total / PAGE_SIZE);
        if (maxPages == 0) maxPages = 1;

        int startItem = (currentPage - 1) * PAGE_SIZE + 1;
        int endItem = Math.min(currentPage * PAGE_SIZE, total);

        if (tvPageIndicator != null) {
            tvPageIndicator.setText("Trang " + currentPage + " / " + maxPages + " (" + startItem + "-" + endItem + " / " + total + ")");
        }

        if (btnPrevPage != null) {
            btnPrevPage.setEnabled(currentPage > 1);
            btnPrevPage.setAlpha(currentPage > 1 ? 1.0f : 0.4f);
        }

        if (btnNextPage != null) {
            btnNextPage.setEnabled(currentPage < maxPages);
            btnNextPage.setAlpha(currentPage < maxPages ? 1.0f : 0.4f);
        }
    }

    private void applyViewMode() {
        if (isAllViewMode) {
            if (layoutPopularSlider != null) layoutPopularSlider.setVisibility(View.GONE);
            if (layoutTop10MonthHeader != null) layoutTop10MonthHeader.setVisibility(View.GONE);
            if (rvTop10Month != null) rvTop10Month.setVisibility(View.GONE);

            if (tvSectionTitle != null) tvSectionTitle.setText("Tất Cả Phim (" + currentFilteredList.size() + ")");
            if (btnToggleViewMode != null) btnToggleViewMode.setText("Tất Cả");
            if (btnSwitchViewMode != null) btnSwitchViewMode.setText("Chế độ Mặc định ➔");

            rvVideos.getRecycledViewPool().clear();
            rvVideos.setLayoutManager(new GridLayoutManager(this, 3));
            videoAdapter.setGridMode(true);
            videoAdapter.updateVideoList(getPaginatedList());
            updatePaginationUI();
        } else {
            if (layoutPopularSlider != null) layoutPopularSlider.setVisibility(View.VISIBLE);
            if (layoutTop10MonthHeader != null) layoutTop10MonthHeader.setVisibility(View.VISIBLE);
            if (rvTop10Month != null) rvTop10Month.setVisibility(View.VISIBLE);

            if (tvSectionTitle != null) tvSectionTitle.setText("Phim Mới Cập Nhật");
            if (btnToggleViewMode != null) btnToggleViewMode.setText("Mặc Định");
            if (btnSwitchViewMode != null) btnSwitchViewMode.setText("Xem tất cả ➔");

            if (layoutPagination != null) layoutPagination.setVisibility(View.GONE);

            rvVideos.getRecycledViewPool().clear();
            rvVideos.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
            videoAdapter.setHorizontalMode(true);
            videoAdapter.updateVideoList(currentFilteredList);
        }
    }

    private void loadGenresFromFirebase() {
        if (db == null) return;
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
                setupGenreFilterChips();
            }
        });
    }

    private void setupGenreFilterChips() {
        if (chipGroupGenresFilter == null) return;
        chipGroupGenresFilter.removeAllViews();

        for (Genre genre : globalGenreList) {
            String name = genre.getName();
            if (!genreFilterStates.containsKey(name)) {
                genreFilterStates.put(name, 0);
            }

            Integer stateObj = genreFilterStates.get(name);
            int state = stateObj != null ? stateObj : 0;

            Chip chip = new Chip(this);
            chip.setText(getChipText(name, state));
            updateChipStyle(chip, state);

            chip.setOnClickListener(v -> {
                Integer currObj = genreFilterStates.get(name);
                int currentState = currObj != null ? currObj : 0;
                int nextState = (currentState + 1) % 3;
                genreFilterStates.put(name, nextState);

                chip.setText(getChipText(name, nextState));
                updateChipStyle(chip, nextState);

                currentPage = 1;
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
        if (state == 1) {
            chip.setChipBackgroundColor(ColorStateList.valueOf(Color.parseColor("#4CAF50")));
            chip.setTextColor(Color.WHITE);
        } else if (state == 2) {
            chip.setChipBackgroundColor(ColorStateList.valueOf(Color.parseColor("#E50914")));
            chip.setTextColor(Color.WHITE);
        } else {
            chip.setChipBackgroundColor(ColorStateList.valueOf(Color.parseColor("#2A2A38")));
            chip.setTextColor(Color.parseColor("#AAAAAA"));
        }
    }

    private void loadVideosFromFirebase() {
        if (db == null) return;
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

                Toast.makeText(MainActivity.this, "Đã đồng bộ " + fullVideoList.size() + " video từ Firebase Firestore", Toast.LENGTH_SHORT).show();
            }
        });
    }

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

    private void openPlayerForVideo(Video video) {
        if (video == null) return;
        MovieDetailBottomSheetDialog detailDialog = new MovieDetailBottomSheetDialog(video);
        detailDialog.show(getSupportFragmentManager(), "MovieDetailBottomSheetDialog");
    }

    private void setupAdapter() {
        videoAdapter = new VideoAdapter(
                this, currentFilteredList,
                this::openPlayerForVideo,
                (video, position) -> showAddEditVideoDialog(video),
                (video, position) -> confirmDeleteVideo(video)
        );
        rvVideos.setAdapter(videoAdapter);

        // Khởi tạo Adapter cho Top 10 Phim Trong Tháng Demo
        top10MonthAdapter = new VideoAdapter(
                this, top10MonthList,
                this::openPlayerForVideo,
                (video, position) -> showAddEditVideoDialog(video),
                (video, position) -> confirmDeleteVideo(video)
        );
        rvTop10Month.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        top10MonthAdapter.setHorizontalMode(true);
        rvTop10Month.setAdapter(top10MonthAdapter);

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

        View.OnClickListener switchViewListener = v -> {
            isAllViewMode = !isAllViewMode;
            currentPage = 1;
            applyViewMode();
            Toast.makeText(this, isAllViewMode ? "Chuyển sang Xem Tất Cả Phim (Lưới 3 Cột, 18 Phim/Trang)" : "Chuyển sang Chế Độ Mặc Định (Top 10)", Toast.LENGTH_SHORT).show();
        };

        if (btnToggleViewMode != null) btnToggleViewMode.setOnClickListener(switchViewListener);
        if (btnSwitchViewMode != null) btnSwitchViewMode.setOnClickListener(switchViewListener);

        if (btnPrevPage != null) {
            btnPrevPage.setOnClickListener(v -> {
                if (currentPage > 1) {
                    currentPage--;
                    applyViewMode();
                    if (nestedScrollView != null) nestedScrollView.smoothScrollTo(0, 0);
                }
            });
        }

        if (btnNextPage != null) {
            btnNextPage.setOnClickListener(v -> {
                int maxPages = (int) Math.ceil((double) currentFilteredList.size() / PAGE_SIZE);
                if (currentPage < maxPages) {
                    currentPage++;
                    applyViewMode();
                    if (nestedScrollView != null) nestedScrollView.smoothScrollTo(0, 0);
                }
            });
        }

        if (btnResetFilter != null) {
            btnResetFilter.setOnClickListener(v -> {
                etSearchQuery.setText("");
                for (String key : genreFilterStates.keySet()) {
                    genreFilterStates.put(key, 0);
                }
                currentPage = 1;
                setupGenreFilterChips();
                filterVideoList("");
                Toast.makeText(this, "Đã đặt lại bộ lọc!", Toast.LENGTH_SHORT).show();
            });
        }

        etSearchQuery.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentPage = 1;
                filterVideoList(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        btnClearSearch.setOnClickListener(v -> etSearchQuery.setText(""));

        btnToggleAdmin.setOnClickListener(v -> {
            isAdminMode = !isAdminMode;
            videoAdapter.setAdminMode(isAdminMode);
            if (top10MonthAdapter != null) top10MonthAdapter.setAdminMode(isAdminMode);

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

    private void filterVideoList(String query) {
        currentFilteredList.clear();
        String lowerCaseQuery = (query != null) ? query.toLowerCase().trim() : "";

        for (Video video : fullVideoList) {
            boolean matchText = lowerCaseQuery.isEmpty()
                    || (video.getTitle() != null && video.getTitle().toLowerCase().contains(lowerCaseQuery))
                    || (video.getDescription() != null && video.getDescription().toLowerCase().contains(lowerCaseQuery));

            if (!matchText) continue;

            boolean matchGenres = true;
            List<String> videoGenres = video.getGenres();

            for (Map.Entry<String, Integer> entry : genreFilterStates.entrySet()) {
                String genreName = entry.getKey();
                int state = entry.getValue();

                if (state == 1) {
                    if (videoGenres == null || !videoGenres.contains(genreName)) {
                        matchGenres = false;
                        break;
                    }
                } else if (state == 2) {
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

        applyViewMode();
    }

    private void showAddEditVideoDialog(Video videoToEdit) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_edit_video, null);
        builder.setView(dialogView);

        TextView tvDialogTitle = dialogView.findViewById(R.id.tvDialogTitle);
        EditText etTitle = dialogView.findViewById(R.id.etDialogTitle);
        EditText etDescription = dialogView.findViewById(R.id.etDialogDescription);
        EditText etYear = dialogView.findViewById(R.id.etDialogYear);
        EditText etMonth = dialogView.findViewById(R.id.etDialogMonth);
        RadioButton rbTypeStandard = dialogView.findViewById(R.id.rbTypeStandard);
        RadioButton rbTypeTrailer = dialogView.findViewById(R.id.rbTypeTrailer);
        CheckBox cbIsPopular = dialogView.findViewById(R.id.cbIsPopular);

        TextView tvSelectedGenres = dialogView.findViewById(R.id.tvSelectedGenres);
        Button btnSelectGenres = dialogView.findViewById(R.id.btnSelectGenres);
        Button btnManageGenres = dialogView.findViewById(R.id.btnManageGenres);

        Button btnManageEpisodes = dialogView.findViewById(R.id.btnManageEpisodes);
        TextView tvEpisodesSummary = dialogView.findViewById(R.id.tvEpisodesSummary);

        EditText etVideoUrl = dialogView.findViewById(R.id.etDialogVideoUrl);
        EditText etThumbnailUrl = dialogView.findViewById(R.id.etDialogThumbnailUrl);
        Button btnPickVideo = dialogView.findViewById(R.id.btnPickVideo);
        Button btnPickImage = dialogView.findViewById(R.id.btnPickImage);






        List<String> tempSelectedGenres = new ArrayList<>();
        List<AudioTrackOption> tempAudioTracks = new ArrayList<>();
        List<SubtitleOption> tempSubtitleTracks = new ArrayList<>();
        List<Episode> tempEpisodes = new ArrayList<>();

        boolean isEditMode = (videoToEdit != null);
        if (isEditMode) {
            tvDialogTitle.setText("Sửa thông tin phim");
            etTitle.setText(videoToEdit.getTitle());
            etDescription.setText(videoToEdit.getDescription());
            if (etYear != null) etYear.setText(videoToEdit.getReleaseYear());
            if (etMonth != null) etMonth.setText(videoToEdit.getReleaseMonth());
            if (rbTypeTrailer != null && videoToEdit.isTrailer()) {
                rbTypeTrailer.setChecked(true);
            } else if (rbTypeStandard != null) {
                rbTypeStandard.setChecked(true);
            }

            cbIsPopular.setChecked(videoToEdit.isPopular());
            etVideoUrl.setText(videoToEdit.getVideoUrl());
            etThumbnailUrl.setText(videoToEdit.getThumbnailUrl());

            if (videoToEdit.getGenres() != null) tempSelectedGenres.addAll(videoToEdit.getGenres());
            if (videoToEdit.getAudioTracks() != null) tempAudioTracks.addAll(videoToEdit.getAudioTracks());
            if (videoToEdit.getSubtitleTracks() != null) tempSubtitleTracks.addAll(videoToEdit.getSubtitleTracks());
            if (videoToEdit.getEpisodes() != null) for (Episode original : videoToEdit.getEpisodes()) {
                Episode copy = new Episode(original.getEpisodeNumber(), original.getTitle(), original.getVideoUrl());
                copy.setSortOrder(original.getSortOrder());
                copy.setAudioTracks(new ArrayList<>(original.getAudioTracks()));
                copy.setSubtitleTracks(new ArrayList<>(original.getSubtitleTracks()));
                tempEpisodes.add(copy);
            }
        } else {
            tvDialogTitle.setText("Thêm phim");
            cbIsPopular.setChecked(false);
        }

        updateSelectedGenresText(tvSelectedGenres, tempSelectedGenres);


        updateEpisodesSummaryText(tvEpisodesSummary, tempEpisodes);

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

        btnManageGenres.setOnClickListener(v -> showAdminManageGenresDialog(tvSelectedGenres, tempSelectedGenres));

        if (btnManageEpisodes != null) {
            btnManageEpisodes.setOnClickListener(v -> showAdminManageEpisodesDialog(tvEpisodesSummary, tempEpisodes));
        }

        btnPickVideo.setOnClickListener(v -> {
            currentEtVideoUrl = etVideoUrl;
            videoPickerLauncher.launch("video/*");
        });

        btnPickImage.setOnClickListener(v -> {
            currentEtThumbnailUrl = etThumbnailUrl;
            imagePickerLauncher.launch("image/*");
        });

        builder.setPositiveButton(isEditMode ? "Lưu thay đổi" : "Thêm phim", null);
        builder.setNegativeButton("Hủy", null);
        AlertDialog editDialog = builder.create();
        editDialog.show();
        editDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String title = etTitle.getText().toString().trim();
            String description = etDescription.getText().toString().trim();
            String year = etYear != null ? etYear.getText().toString().trim() : "2024";
            String month = etMonth != null ? etMonth.getText().toString().trim() : "10";
            String type = (rbTypeTrailer != null && rbTypeTrailer.isChecked()) ? Video.TYPE_TRAILER : Video.TYPE_STANDARD;
            boolean isPopular = cbIsPopular.isChecked();
            String videoUrl = etVideoUrl.getText().toString().trim();
            String thumbnailUrl = etThumbnailUrl.getText().toString().trim();

            if (title.isEmpty()) {
                etTitle.setError("Nhập tên phim");
                etTitle.requestFocus();
                return;
            }

            Video videoTarget;
            if (isEditMode) {
                videoToEdit.setTitle(title);
                videoToEdit.setDescription(description);
                videoToEdit.setReleaseYear(year);
                videoToEdit.setReleaseMonth(month);
                videoToEdit.setVideoType(type);
                videoToEdit.setPopular(isPopular);
                videoToEdit.setVideoUrl(videoUrl);
                videoToEdit.setThumbnailUrl(thumbnailUrl);
                videoToEdit.setGenres(new ArrayList<>(tempSelectedGenres));
                videoToEdit.setAudioTracks(new ArrayList<>(tempAudioTracks));
                videoToEdit.setSubtitleTracks(new ArrayList<>(tempSubtitleTracks));
                videoToEdit.setEpisodes(new ArrayList<>(tempEpisodes));
                videoTarget = videoToEdit;
            } else {
                videoTarget = new Video(title, description, thumbnailUrl, videoUrl, isPopular);
                videoTarget.setReleaseYear(year);
                videoTarget.setReleaseMonth(month);
                videoTarget.setVideoType(type);
                videoTarget.setGenres(new ArrayList<>(tempSelectedGenres));
                videoTarget.setAudioTracks(new ArrayList<>(tempAudioTracks));
                videoTarget.setSubtitleTracks(new ArrayList<>(tempSubtitleTracks));
                videoTarget.setEpisodes(new ArrayList<>(tempEpisodes));
                fullVideoList.add(0, videoTarget);
            }

            filterVideoList(etSearchQuery.getText().toString());
            updatePopularSliderList();

            if (db != null) {
                db.collection("videos").document(videoTarget.getId()).set(videoTarget)
                        .addOnSuccessListener(aVoid -> Toast.makeText(this, "Đã lưu lên Firebase thành công!", Toast.LENGTH_SHORT).show())
                        .addOnFailureListener(e -> Log.e(TAG, "Lỗi đồng bộ Firebase", e));
            }
            editDialog.dismiss();
        });


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

    private void updateAudioTracksSummaryText(TextView tv, List<AudioTrackOption> list) {
        if (tv == null) return;
        if (list == null || list.isEmpty()) {
            tv.setText("🎙️ Luồng lồng tiếng (Dub): Mặc định");
        } else {
            StringBuilder sb = new StringBuilder("🎙️ Luồng lồng tiếng (Dub): ");
            for (int i = 0; i < list.size(); i++) {
                sb.append(list.get(i).getName());
                if (i < list.size() - 1) sb.append(", ");
            }
            tv.setText(sb.toString());
        }
    }

    private void updateSubtitlesSummaryText(TextView tv, List<SubtitleOption> list) {
        if (tv == null) return;
        if (list == null || list.isEmpty()) {
            tv.setText("💬 Phụ đề rời (Sub CC): Chưa có");
        } else {
            StringBuilder sb = new StringBuilder("💬 Phụ đề rời (Sub CC): ");
            for (int i = 0; i < list.size(); i++) {
                sb.append(list.get(i).getName());
                if (i < list.size() - 1) sb.append(", ");
            }
            tv.setText(sb.toString());
        }
    }

    private void updateEpisodesSummaryText(TextView tv, List<Episode> list) {
        if (tv == null) return;
        if (list == null || list.isEmpty()) {
            tv.setText("📺 Danh sách Tập Phim: Chưa có tập nào");
        } else {
            StringBuilder sb = new StringBuilder("📺 Danh sách Tập Phim (" + list.size() + " Tập): ");
            for (int i = 0; i < list.size(); i++) {
                sb.append(list.get(i).getTitle());
                if (i < list.size() - 1) sb.append(", ");
            }
            tv.setText(sb.toString());
        }
    }

    private void showAdminManageEpisodesDialog(TextView tvSummary, List<Episode> tempEpisodes) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Tập phim");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (20 * getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding / 2, padding, padding / 2);

        Button btnAddNew = new Button(this);
        btnAddNew.setText("Thêm tập phim");
        layout.addView(btnAddNew);

        TextView tvHeader = new TextView(this);
        tvHeader.setText(tempEpisodes.isEmpty() ? "Chưa có tập phim. Thêm tập để bật nút Xem."
                : "Tập đầu danh sách sẽ mở khi bấm Xem. Bấm Thứ tự để sắp xếp; lưu phim để áp dụng.");
        tvHeader.setTypeface(null, Typeface.BOLD);
        layout.addView(tvHeader);

        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.addView(layout);
        builder.setView(scroll);
        AlertDialog dialog = builder.setNegativeButton("Đóng", null).create();

        btnAddNew.setOnClickListener(v -> {
            dialog.dismiss();
            showAddEditEpisodeDialog(null, tempEpisodes, () -> {
                updateEpisodesSummaryText(tvSummary, tempEpisodes);
                showAdminManageEpisodesDialog(tvSummary, tempEpisodes);
            });
        });

        List<Episode> ordered = EpisodePlayback.ordered(tempEpisodes);
        tempEpisodes.clear();
        tempEpisodes.addAll(ordered);
        for (Episode ep : tempEpisodes) {
            LinearLayout itemRow = new LinearLayout(this);
            itemRow.setOrientation(LinearLayout.VERTICAL);
            itemRow.setPadding(0, 8, 0, 8);

            TextView tvEpName = new TextView(this);
            tvEpName.setText("Tập " + ep.getEpisodeNumber() + " · " + ep.getTitle()
                    + "\n" + ep.getSubtitleTracks().size() + " phụ đề · " + ep.getAudioTracks().size() + " lồng tiếng");
            tvEpName.setTextSize(14);
            tvEpName.setTypeface(null, Typeface.BOLD);
            LinearLayout.LayoutParams lpText = new LinearLayout.LayoutParams(-1, -2);
            tvEpName.setLayoutParams(lpText);
            itemRow.addView(tvEpName);
            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            itemRow.addView(actions);
            Button btnOrder = new Button(this);
            btnOrder.setText("Thứ tự");
            btnOrder.setOnClickListener(v -> {
                int index = tempEpisodes.indexOf(ep);
                new AlertDialog.Builder(this).setTitle(ep.getTitle())
                        .setItems(new String[]{"Đưa lên đầu", "Di chuyển lên", "Di chuyển xuống"}, (orderDialog, which) -> {
                            int to = which == 0 ? 0 : which == 1 ? index - 1 : index + 1;
                            EpisodePlayback.move(tempEpisodes, index, to);
                            updateEpisodesSummaryText(tvSummary, tempEpisodes);
                            dialog.dismiss();
                            showAdminManageEpisodesDialog(tvSummary, tempEpisodes);
                        }).show();
            });
            actions.addView(btnOrder, new LinearLayout.LayoutParams(0, -2, 1));

            Button btnEdit = new Button(this);
            btnEdit.setText("Sửa");
            btnEdit.setOnClickListener(v -> {
                dialog.dismiss();
                showAddEditEpisodeDialog(ep, tempEpisodes, () -> {
                    updateEpisodesSummaryText(tvSummary, tempEpisodes);
                    showAdminManageEpisodesDialog(tvSummary, tempEpisodes);
                });
            });
            actions.addView(btnEdit, new LinearLayout.LayoutParams(0, -2, 1));

            Button btnDelete = new Button(this);
            btnDelete.setText("Xóa");
            btnDelete.setTextColor(Color.RED);
            btnDelete.setOnClickListener(v -> {
                tempEpisodes.remove(ep);
                updateEpisodesSummaryText(tvSummary, tempEpisodes);
                Toast.makeText(this, "Đã xóa tập phim!", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
                showAdminManageEpisodesDialog(tvSummary, tempEpisodes);
            });
            actions.addView(btnDelete, new LinearLayout.LayoutParams(0, -2, 1));

            layout.addView(itemRow);
        }

        dialog.show();
    }

    private void addEpisodeField(LinearLayout layout, EditText input, String label) {
        com.google.android.material.textfield.TextInputLayout field =
                new com.google.android.material.textfield.TextInputLayout(this);
        field.setHint(label);
        field.setBoxBackgroundMode(com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_OUTLINE);
        input.setHint(null);
        field.addView(input, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = (int) (12 * getResources().getDisplayMetrics().density);
        layout.addView(field, params);
    }
    private void showAddEditEpisodeDialog(Episode epToEdit, List<Episode> tempEpisodes, Runnable onUpdated) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(epToEdit != null ? "Sửa tập phim" : "Thêm tập phim");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (20 * getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding / 2, padding, padding / 2);

        EditText etNumber = new EditText(this);
        etNumber.setHint("Số tập (vd: 1, 2, 3)");
        etNumber.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        addEpisodeField(layout, etNumber, "Số tập");

        EditText etTitle = new EditText(this);
        etTitle.setHint("Tên tập phim (vd: Tập 1: Khởi đầu)");
        addEpisodeField(layout, etTitle, "Tên tập");

        EditText etUrl = new EditText(this);
        etUrl.setHint("URL Video tập này (.mp4 / .m3u8 / YouTube)");
        etUrl.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        addEpisodeField(layout, etUrl, "URL video của tập");

        Button btnPickVideo = new Button(this);
        btnPickVideo.setText("Tải video từ thiết bị");
        btnPickVideo.setOnClickListener(v -> {
            currentEtVideoUrl = etUrl;
            videoPickerLauncher.launch("video/*");
        });
        layout.addView(btnPickVideo);

        TextView tvEpAudioSummary = new TextView(this);
        tvEpAudioSummary.setPadding(0, 12, 0, 4);
        layout.addView(tvEpAudioSummary);

        Button btnManageEpAudio = new Button(this);
        btnManageEpAudio.setText("Lồng tiếng của tập");
        layout.addView(btnManageEpAudio);

        TextView tvEpSubSummary = new TextView(this);
        tvEpSubSummary.setPadding(0, 12, 0, 4);
        layout.addView(tvEpSubSummary);

        Button btnManageEpSub = new Button(this);
        btnManageEpSub.setText("Phụ đề của tập");
        layout.addView(btnManageEpSub);

        List<AudioTrackOption> epAudioTracks = new ArrayList<>();
        List<SubtitleOption> epSubtitleTracks = new ArrayList<>();

        if (epToEdit != null) {
            etNumber.setText(String.valueOf(epToEdit.getEpisodeNumber()));
            etTitle.setText(epToEdit.getTitle());
            etUrl.setText(epToEdit.getVideoUrl());
            if (epToEdit.getAudioTracks() != null) epAudioTracks.addAll(epToEdit.getAudioTracks());
            if (epToEdit.getSubtitleTracks() != null) epSubtitleTracks.addAll(epToEdit.getSubtitleTracks());
        } else {
            int nextNumber = 1;
            for (Episode existing : tempEpisodes) nextNumber = Math.max(nextNumber, existing.getEpisodeNumber() + 1);
            etNumber.setText(String.valueOf(nextNumber));
        }

        updateAudioTracksSummaryText(tvEpAudioSummary, epAudioTracks);
        updateSubtitlesSummaryText(tvEpSubSummary, epSubtitleTracks);

        btnManageEpAudio.setOnClickListener(v -> showAdminManageAudioTracksDialog(tvEpAudioSummary, epAudioTracks));
        btnManageEpSub.setOnClickListener(v -> showAdminManageSubtitlesDialog(tvEpSubSummary, epSubtitleTracks));

        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.addView(layout);
        builder.setView(scroll);
        builder.setPositiveButton("Lưu tập", null);
        builder.setNegativeButton("Hủy", null);
        AlertDialog episodeDialog = builder.create();
        episodeDialog.show();
        episodeDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String numStr = etNumber.getText().toString().trim();
            String title = etTitle.getText().toString().trim();
            String url = etUrl.getText().toString().trim();

            int epNum;
            try {
                epNum = Integer.parseInt(numStr);
            } catch (NumberFormatException error) {
                etNumber.setError("Nhập số tập hợp lệ"); return;
            }
            if (epNum < 1) { etNumber.setError("Số tập phải lớn hơn 0"); return; }
            for (Episode existing : tempEpisodes) {
                if (existing != epToEdit && existing.getEpisodeNumber() == epNum) {
                    etNumber.setError("Số tập đã tồn tại"); return;
                }
            }

            if (!url.matches("(?i)^https?://[^\\s]+$")) {
                etUrl.setError("Nhập URL video http hoặc https hợp lệ");
                return;
            }

            if (epToEdit != null) {
                epToEdit.setEpisodeNumber(epNum);
                epToEdit.setTitle(title.isEmpty() ? "Tập " + epNum : title);
                epToEdit.setVideoUrl(url);
                epToEdit.setAudioTracks(new ArrayList<>(epAudioTracks));
                epToEdit.setSubtitleTracks(new ArrayList<>(epSubtitleTracks));
            } else {
                Episode newEp = new Episode(epNum, title.isEmpty() ? "Tập " + epNum : title, url);
                newEp.setAudioTracks(new ArrayList<>(epAudioTracks));
                newEp.setSubtitleTracks(new ArrayList<>(epSubtitleTracks));
                int nextOrder = 0;
                for (Episode existing : tempEpisodes) nextOrder = Math.max(nextOrder,
                        (existing.getSortOrder() != null ? existing.getSortOrder() : existing.getEpisodeNumber()) + 1);
                newEp.setSortOrder(nextOrder);
                tempEpisodes.add(newEp);
            }

            episodeDialog.dismiss();
            if (onUpdated != null) onUpdated.run();
        });


    }

    private void showAdminManageAudioTracksDialog(TextView tvSummary, List<AudioTrackOption> tempAudioTracks) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("🎙️ Quản Lý Luồng Lồng Tiếng (Dub)");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 16, 32, 16);

        EditText etName = new EditText(this);
        etName.setHint("Tên luồng (vd: English Dub, Tiếng Nhật Original)");
        layout.addView(etName);

        EditText etLang = new EditText(this);
        etLang.setHint("Mã ngôn ngữ (vd: en, ja, vi)");
        etLang.setText("en");
        layout.addView(etLang);

        EditText etUrl = new EditText(this);
        etUrl.setHint("URL luồng video/audio lồng tiếng thay thế (nếu có)");
        etUrl.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        layout.addView(etUrl);

        Button btnUploadAudio = new Button(this);
        btnUploadAudio.setText("Chọn file âm thanh từ thiết bị");
        btnUploadAudio.setOnClickListener(v -> {
            currentEtAudioUrl = etUrl;
            audioPickerLauncher.launch("audio/*");
        });
        layout.addView(btnUploadAudio);
        TextView uploadHint = new TextView(this);
        uploadHint.setText("Nhập link trực tiếp hoặc chọn file. File sẽ được tải lên Cloudinary và điền URL vào ô trên.");
        layout.addView(uploadHint);

        Button btnAdd = new Button(this);
        btnAdd.setText("➕ Thêm Luồng Lồng Tiếng");
        layout.addView(btnAdd);

        TextView tvHeader = new TextView(this);
        tvHeader.setText("\nCác luồng lồng tiếng hiện có (Bấm để Xóa):");
        tvHeader.setTypeface(null, Typeface.BOLD);
        layout.addView(tvHeader);

        android.widget.ScrollView audioScroll = new android.widget.ScrollView(this);
        audioScroll.addView(layout);
        builder.setView(audioScroll);
        AlertDialog dialog = builder.setNegativeButton("Đóng", null).create();

        btnAdd.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            String lang = etLang.getText().toString().trim();
            String url = etUrl.getText().toString().trim();

            if (name.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập tên luồng lồng tiếng!", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!url.matches("(?i)^https?://[^\\s]+$")) {
                etUrl.setError("Nhập link âm thanh hoặc chọn file từ thiết bị");
                return;
            }
            for (AudioTrackOption track : tempAudioTracks) {
                if (url.equals(MediaTracks.clean(track.getUrl()))) {
                    etUrl.setError("Luồng âm thanh này đã có trong tập");
                    return;
                }
            }
            tempAudioTracks.add(new AudioTrackOption(name, lang.isEmpty() ? "en" : lang, url));
            updateAudioTracksSummaryText(tvSummary, tempAudioTracks);
            Toast.makeText(this, "Đã thêm luồng: " + name, Toast.LENGTH_SHORT).show();
            dialog.dismiss();
            showAdminManageAudioTracksDialog(tvSummary, tempAudioTracks);
        });

        for (AudioTrackOption audio : tempAudioTracks) {
            Button btnItem = new Button(this);
            btnItem.setText("🗑️ Xóa: " + audio.getName() + " (" + audio.getLanguage() + ")");
            btnItem.setTextColor(Color.RED);

            btnItem.setOnClickListener(v -> {
                tempAudioTracks.remove(audio);
                updateAudioTracksSummaryText(tvSummary, tempAudioTracks);
                Toast.makeText(this, "Đã xóa luồng âm thanh!", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
                showAdminManageAudioTracksDialog(tvSummary, tempAudioTracks);
            });

            layout.addView(btnItem);
        }

        dialog.show();
    }

    private void showAdminManageSubtitlesDialog(TextView tvSummary, List<SubtitleOption> tempSubtitleTracks) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("💬 Quản Lý Phụ Đề Rời (Subtitles CC)");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 16, 32, 16);

        EditText etName = new EditText(this);
        etName.setHint("Tên phụ đề (vd: Tiếng Việt, English, 日本語)");
        layout.addView(etName);

        EditText etLang = new EditText(this);
        etLang.setHint("Mã ngôn ngữ (vd: vi, en, ja)");
        etLang.setText("vi");
        layout.addView(etLang);

        EditText etUrl = new EditText(this);
        etUrl.setHint("URL file phụ đề (.vtt hoặc .srt)");
        layout.addView(etUrl);

        Button btnAdd = new Button(this);
        btnAdd.setText("➕ Thêm File Phụ Đề Mới");
        layout.addView(btnAdd);

        TextView tvHeader = new TextView(this);
        tvHeader.setText("\nCác file phụ đề hiện có (Bấm để Xóa):");
        tvHeader.setTypeface(null, Typeface.BOLD);
        layout.addView(tvHeader);

        builder.setView(layout);
        AlertDialog dialog = builder.setNegativeButton("Đóng", null).create();

        btnAdd.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            String lang = etLang.getText().toString().trim();
            String url = etUrl.getText().toString().trim();

            if (name.isEmpty() || url.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập đầy đủ tên và URL file phụ đề!", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!url.matches("(?i)^https?://[^\\s]+$")) {
                etUrl.setError("Nhập URL phụ đề hợp lệ");
                return;
            }
            for (SubtitleOption track : tempSubtitleTracks) {
                if (url.equals(MediaTracks.clean(track.getSubtitleUrl()))) {
                    etUrl.setError("File phụ đề này đã có trong tập");
                    return;
                }
            }
            tempSubtitleTracks.add(new SubtitleOption(name, lang.isEmpty() ? "vi" : lang, url));
            updateSubtitlesSummaryText(tvSummary, tempSubtitleTracks);
            Toast.makeText(this, "Đã thêm phụ đề: " + name, Toast.LENGTH_SHORT).show();
            dialog.dismiss();
            showAdminManageSubtitlesDialog(tvSummary, tempSubtitleTracks);
        });

        for (SubtitleOption sub : tempSubtitleTracks) {
            Button btnItem = new Button(this);
            btnItem.setText("🗑️ Xóa: " + sub.getName() + " (" + sub.getLanguage() + ")");
            btnItem.setTextColor(Color.RED);

            btnItem.setOnClickListener(v -> {
                tempSubtitleTracks.remove(sub);
                updateSubtitlesSummaryText(tvSummary, tempSubtitleTracks);
                Toast.makeText(this, "Đã xóa phụ đề!", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
                showAdminManageSubtitlesDialog(tvSummary, tempSubtitleTracks);
            });

            layout.addView(btnItem);
        }

        dialog.show();
    }

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
            if (db != null) db.collection("genres").document(newGenre.getId()).set(newGenre);

            setupGenreFilterChips();
            etNewGenre.setText("");
            Toast.makeText(this, "Đã tạo thể loại: " + newName, Toast.LENGTH_SHORT).show();
            dialog.dismiss();
            showAdminManageGenresDialog(tvSelectedGenres, tempSelectedGenres);
        });

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
                        if (db != null) db.collection("genres").document(g.getId()).delete();

                        for (Video video : fullVideoList) {
                            if (video.getGenres() != null && video.getGenres().contains(deletedName)) {
                                video.getGenres().remove(deletedName);
                                if (db != null) db.collection("videos").document(video.getId()).set(video);
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

    private void confirmDeleteVideo(Video video) {
        new AlertDialog.Builder(this)
                .setTitle("Xác Nhận Xóa")
                .setMessage("Bạn có chắc chắn muốn xóa video \"" + video.getTitle() + "\" không?")
                .setPositiveButton("Xóa", (dialog, which) -> {
                    fullVideoList.remove(video);
                    top10MonthList.remove(video);
                    filterVideoList(etSearchQuery.getText().toString());
                    updatePopularSliderList();
                    if (top10MonthAdapter != null) top10MonthAdapter.notifyDataSetChanged();

                    if (db != null) {
                        db.collection("videos").document(video.getId()).delete()
                                .addOnSuccessListener(aVoid -> Toast.makeText(this, "Đã xóa khỏi Firebase!", Toast.LENGTH_SHORT).show())
                                .addOnFailureListener(e -> Log.e(TAG, "Lỗi xóa trên Firebase", e));
                    }
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
