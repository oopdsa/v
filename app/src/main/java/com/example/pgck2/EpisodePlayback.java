package com.example.pgck2;

import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;

/** Shared selection policy for the featured banner and movie details. */
final class EpisodePlayback {
    private EpisodePlayback() {}

    static Episode firstEpisode(List<Episode> episodes) {
        if (episodes == null) return null;
        for (Episode episode : ordered(episodes)) {
            if (episode == null || episode.getVideoUrl() == null
                    || episode.getVideoUrl().trim().isEmpty()) continue;
            return episode;
        }
        return null;
    }

    static List<Episode> ordered(List<Episode> episodes) {
        List<Episode> result = new ArrayList<>();
        if (episodes != null) for (Episode episode : episodes) if (episode != null) result.add(episode);
        result.sort(Comparator.comparingInt((Episode ep) -> ep.getSortOrder() != null
                ? ep.getSortOrder() : ep.getEpisodeNumber()).thenComparingInt(Episode::getEpisodeNumber));
        return result;
    }

    static void move(List<Episode> episodes, int from, int to) {
        if (from < 0 || to < 0 || from >= episodes.size() || to >= episodes.size()) return;
        Episode moved = episodes.remove(from);
        episodes.add(to, moved);
        for (int i = 0; i < episodes.size(); i++) episodes.get(i).setSortOrder(i);
    }

}
