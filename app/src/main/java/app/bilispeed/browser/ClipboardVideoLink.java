package app.bilispeed.browser;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Finds a video URL in copied text without following redirects or inspecting other clipboard data. */
final class ClipboardVideoLink {
    private static final int MAX_TEXT_LENGTH = 16384;
    private static final Pattern LINK = Pattern.compile(
            "(?<![a-z0-9_.@/%:-])(?:https?://|(?=(?:(?:www\\.|m\\.)?bilibili\\.com|b23\\.tv)/))"
                    + "[\\x21-\\x7e&&[^<>\"']]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern VIDEO_PATH = Pattern.compile(
            "/(?:video/(?:BV[0-9A-Za-z]{10}|av[0-9]+)|bangumi/play/(?:ep|ss)[0-9]+)/?");
    private static final Pattern SHORT_PATH = Pattern.compile("/[A-Za-z0-9_-]{1,32}/?");

    final String url;
    final String label;
    final boolean shortLink;

    private ClipboardVideoLink(String url, String label, boolean shortLink) {
        this.url = url;
        this.label = label;
        this.shortLink = shortLink;
    }

    static ClipboardVideoLink find(CharSequence text) {
        if (text == null || text.length() > MAX_TEXT_LENGTH) return null;
        Matcher links = LINK.matcher(text);
        while (links.find()) {
            String token = links.group();
            int end = token.length();
            while (end > 0 && ")]}.,;!?".indexOf(token.charAt(end - 1)) >= 0) end--;
            ClipboardVideoLink result = parse(token.substring(0, end));
            if (result != null) return result;
        }
        return null;
    }

    private static ClipboardVideoLink parse(String token) {
        try {
            boolean hasScheme = token.regionMatches(true, 0, "https://", 0, 8)
                    || token.regionMatches(true, 0, "http://", 0, 7);
            URI uri = new URI(hasScheme ? token : "https://" + token);
            if (uri.getHost() == null || uri.getUserInfo() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443)) return null;
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            String path = uri.getRawPath();
            boolean shortLink = host.equals("b23.tv");
            if (shortLink) {
                if (!SHORT_PATH.matcher(path).matches()) return null;
            } else {
                if (!(host.equals("www.bilibili.com") || host.equals("m.bilibili.com") || host.equals("bilibili.com"))
                        || !VIDEO_PATH.matcher(path).matches()) return null;
                host = "www.bilibili.com";
            }
            String label = host + path;
            String url = "https://" + label;
            if (uri.getRawQuery() != null) url += "?" + uri.getRawQuery();
            if (uri.getRawFragment() != null) url += "#" + uri.getRawFragment();
            return new ClipboardVideoLink(url, label, shortLink);
        } catch (URISyntaxException exception) {
            return null;
        }
    }
}
