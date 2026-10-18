package com.example.pgck2;

import android.content.Context;
import android.content.Intent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class PlaybackIntents {
    private PlaybackIntents() {}

    static Intent episode(Context context, Video video, Episode episode) {
        return create(context, video, episode.getVideoUrl(), video.getTitle() + " — " + episode.getTitle(),
                episode.getAudioTracks(), episode.getSubtitleTracks());
    }

    static Intent trailer(Context context, Video video) {
        return create(context, video, video.getVideoUrl(), video.getTitle() + " — Trailer",
                Collections.emptyList(), Collections.emptyList());
    }

    private static Intent create(Context context, Video video, String url, String title,
                                 List<AudioTrackOption> audio, List<SubtitleOption> subtitles) {
        Intent intent = new Intent(context, PlayerActivity.class);
        intent.putExtra(PlayerActivity.EXTRA_VIDEO_URL, url);
        intent.putExtra(PlayerActivity.EXTRA_VIDEO_TITLE, title);
        intent.putExtra(PlayerActivity.EXTRA_VIDEO_DESC, video.getDescription());
        intent.putExtra("extra_video_genres", video.getGenresString());
        ArrayList<String> names = new ArrayList<>(), langs = new ArrayList<>(), urls = new ArrayList<>();
        for (AudioTrackOption track : audio) {
            names.add(track.getName()); langs.add(track.getLanguage()); urls.add(track.getUrl());
        }
        intent.putStringArrayListExtra("extra_audio_names", names);
        intent.putStringArrayListExtra("extra_audio_langs", langs);
        intent.putStringArrayListExtra("extra_audio_urls", urls);
        names = new ArrayList<>(); langs = new ArrayList<>(); urls = new ArrayList<>();
        for (SubtitleOption track : subtitles) {
            names.add(track.getName()); langs.add(track.getLanguage()); urls.add(track.getSubtitleUrl());
        }
        intent.putStringArrayListExtra("extra_sub_names", names);
        intent.putStringArrayListExtra("extra_sub_langs", langs);
        intent.putStringArrayListExtra("extra_sub_urls", urls);
        return intent;
    }
}
