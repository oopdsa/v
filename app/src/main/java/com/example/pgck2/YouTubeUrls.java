package com.example.pgck2;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

final class YouTubeUrls {
    private YouTubeUrls() {}

    static String videoId(String value) {
        if (value == null) return null;
        value = value.trim();
        if (value.matches("[A-Za-z0-9_-]{11}")) return value;
        try {
            URI uri = new URI(value.contains("://") ? value : "https://" + value);
            String host = uri.getHost();
            if (host == null) return null;
            host = host.toLowerCase(Locale.ROOT);
            String path = uri.getPath();
            String id = null;
            if (host.equals("youtu.be") || host.equals("www.youtu.be")) {
                id = path.substring(1).split("/")[0];
            } else if (host.equals("youtube.com") || host.endsWith(".youtube.com")
                    || host.equals("youtube-nocookie.com") || host.endsWith(".youtube-nocookie.com")) {
                String[] parts = path.split("/");
                if (parts.length > 2 && (parts[1].equals("embed") || parts[1].equals("shorts")
                        || parts[1].equals("live") || parts[1].equals("v"))) id = parts[2];
                if (id == null && uri.getRawQuery() != null) {
                    for (String parameter : uri.getRawQuery().split("&")) {
                        if (parameter.startsWith("v=")) {
                            id = URLDecoder.decode(parameter.substring(2), StandardCharsets.UTF_8.name());
                            break;
                        }
                    }
                }
            }
            return id != null && id.matches("[A-Za-z0-9_-]{11}") ? id : null;
        } catch (Exception ignored) {
            return null;
        }
    }
}
