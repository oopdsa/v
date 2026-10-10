package com.example.pipdemo;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        RecyclerView recyclerView = findViewById(R.id.recyclerViewMovies);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        List<Movie> movies = new ArrayList<>();
        String BASE = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample";

        movies.add(new Movie(1, "Big Buck Bunny", "Phim hoạt hình ngắn về chú thỏ khổng lồ.", BASE + "/images/BigBuckBunny.jpg", BASE + "/BigBuckBunny.mp4"));
        movies.add(new Movie(2, "Elephants Dream", "Phim hoạt hình 3D mã nguồn mở đầu tiên.", BASE + "/images/ElephantsDream.jpg", BASE + "/ElephantsDream.mp4"));
        movies.add(new Movie(3, "Sintel", "Cô gái Sintel đi tìm chú rồng con.", BASE + "/images/Sintel.jpg", BASE + "/Sintel.mp4"));

        MovieAdapter adapter = new MovieAdapter(this, movies);
        recyclerView.setAdapter(adapter);
    }
}