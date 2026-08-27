package de.venomenon.cscxtool.song;

import de.venomenon.cscxtool.shared.ApiBadRequestException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Normalizes the supported YouTube video URLs without contacting YouTube. */
@Component
public class YoutubeUrlNormalizer {

    private static final Pattern VIDEO_ID = Pattern.compile("[A-Za-z0-9_-]{11}");
    private static final Pattern START_TIME = Pattern.compile("(?:[0-9]+|[0-9]+h(?:[0-9]+m)?(?:[0-9]+s)?|[0-9]+m(?:[0-9]+s)?|[0-9]+s)");

    public String normalize(String value) {
        URI uri;
        try {
            uri = new URI(value.trim());
        } catch (URISyntaxException exception) {
            throw invalid();
        }
        if (uri.getScheme() == null || !(uri.getScheme().equalsIgnoreCase("https") || uri.getScheme().equalsIgnoreCase("http"))) {
            throw invalid();
        }

        String host = uri.getHost();
        if (host == null) {
            throw invalid();
        }
        host = host.toLowerCase(Locale.ROOT);
        Map<String, String> query = queryParameters(uri.getRawQuery());
        String videoId = videoId(host, uri.getPath(), query);
        if (videoId == null || !VIDEO_ID.matcher(videoId).matches()) {
            throw invalid();
        }

        String start = usefulStartTime(query);
        return "https://www.youtube.com/watch?v=" + videoId + (start == null ? "" : "&t=" + start);
    }

    private String videoId(String host, String path, Map<String, String> query) {
        if (host.equals("youtu.be") || host.equals("www.youtu.be")) {
            return pathSegment(path, 0);
        }
        if (!(host.equals("youtube.com") || host.equals("www.youtube.com")
                || host.equals("m.youtube.com") || host.equals("music.youtube.com"))) {
            throw invalid();
        }
        if ("/watch".equalsIgnoreCase(path)) {
            return query.get("v");
        }
        String firstSegment = pathSegment(path, 0).toLowerCase(Locale.ROOT);
        if (firstSegment.equals("shorts") || firstSegment.equals("live") || firstSegment.equals("embed")) {
            return pathSegment(path, 1);
        }
        throw invalid();
    }

    private static String pathSegment(String path, int index) {
        if (path == null) {
            throw invalid();
        }
        String[] segments = path.split("/");
        int found = 0;
        for (String segment : segments) {
            if (!segment.isEmpty()) {
                if (found++ == index) {
                    return segment;
                }
            }
        }
        throw invalid();
    }

    private static String usefulStartTime(Map<String, String> query) {
        String value = query.containsKey("t") ? query.get("t") : query.get("start");
        if (value == null || !START_TIME.matcher(value).matches()) {
            return null;
        }
        return value;
    }

    private static Map<String, String> queryParameters(String rawQuery) {
        Map<String, String> parameters = new HashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) {
            return parameters;
        }
        for (String pair : rawQuery.split("&")) {
            String[] pieces = pair.split("=", 2);
            String key = decode(pieces[0]);
            if (!parameters.containsKey(key)) {
                parameters.put(key, pieces.length == 2 ? decode(pieces[1]) : "");
            }
        }
        return parameters;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static ApiBadRequestException invalid() {
        return new ApiBadRequestException(
                "INVALID_YOUTUBE_URL",
                "Bitte gib einen gültigen YouTube-Video-Link an."
        );
    }
}
