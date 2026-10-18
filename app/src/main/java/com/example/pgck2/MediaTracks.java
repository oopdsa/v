package com.example.pgck2;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Identity uses the source URL, never just the display name or language. */
final class MediaTracks {
    private MediaTracks() {}
    static String clean(String value) { return value == null ? "" : value.trim(); }

    static List<AudioTrackOption> uniqueAudio(List<AudioTrackOption> input) {
        Map<String, AudioTrackOption> result = new LinkedHashMap<>();
        for (AudioTrackOption track : input) {
            if (track == null) continue;
            String url = clean(track.getUrl());
            String language = clean(track.getLanguage()).toLowerCase(Locale.ROOT);
            String key = url.isEmpty() ? "language:" + language : "url:" + url;
            if (url.isEmpty() && language.isEmpty()) continue;
            result.putIfAbsent(key, new AudioTrackOption(clean(track.getName()), language, url));
        }
        return new ArrayList<>(result.values());
    }

    static List<SubtitleOption> uniqueSubtitles(List<SubtitleOption> input) {
        Map<String, SubtitleOption> result = new LinkedHashMap<>();
        for (SubtitleOption track : input) {
            if (track == null || clean(track.getSubtitleUrl()).isEmpty()) continue;
            String url = clean(track.getSubtitleUrl());
            result.putIfAbsent(url, new SubtitleOption(clean(track.getName()), clean(track.getLanguage()), url));
        }
        return new ArrayList<>(result.values());
    }

    static boolean isSrt(String url) {
        return clean(url).split("[?#]", 2)[0].toLowerCase(Locale.ROOT).endsWith(".srt");
    }

    static String stableTrackId(String id) {
        // MergingMediaSource prepends child indices (e.g. 2:external:... -> 0:2:external:...).
        return clean(id).replaceFirst("^(?:[0-9]+:)+", "");
    }
}
