package com.videofinder.playlists;

import android.net.Uri;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SafeLink {
    private SafeLink() {}

    private static final Pattern URL_IN_TEXT =
            Pattern.compile("https://[^\\s\"'<>]+", Pattern.CASE_INSENSITIVE);

    /** Returns a rebuilt https URL on an allowed host, or null. */
    public static String clean(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty() || s.length() > 2048) return null;

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' || c <= 0x20 || c == 0x7F) return null;
        }

        Uri u;
        try { u = Uri.parse(s); } catch (Exception e) { return null; }

        if (u.getScheme() == null) return null;
        if (!u.getScheme().toLowerCase(Locale.ROOT).equals("https")) return null;
        if (u.getUserInfo() != null) return null;
        if (u.getPort() != -1 && u.getPort() != 443) return null;

        String h = u.getHost();
        if (h == null) return null;
        h = h.toLowerCase(Locale.ROOT);
        if (!h.matches("[a-z0-9.\\-]+")) return null;
        if (!allowed(h)) return null;

        StringBuilder sb = new StringBuilder("https://").append(h);
        String p = u.getEncodedPath();
        sb.append(p == null || p.isEmpty() ? "/" : p);
        if (u.getEncodedQuery() != null) sb.append('?').append(u.getEncodedQuery());
        return sb.toString();
    }

    private static boolean allowed(String h) {
        return h.equals("youtube.com") || h.equals("youtu.be")
                || h.endsWith(".youtube.com") || h.endsWith(".youtu.be");
    }

    /** Pull the first valid link out of a share-sheet blob of text. */
    public static String fromText(String text) {
        if (text == null) return null;
        Matcher m = URL_IN_TEXT.matcher(text);
        while (m.find()) {
            String c = clean(m.group());
            if (c != null) return c;
        }
        return null;
    }

    /** YouTube video id (11 chars) or null. Used only for a local label, never for playback. */
    public static String videoId(String url) {
        String c = clean(url);
        if (c == null) return null;
        Uri u = Uri.parse(c);
        String h = u.getHost();
        String id = null;
        if (h != null && (h.equals("youtu.be") || h.endsWith(".youtu.be"))) {
            String p = u.getPath();
            if (p != null && p.length() > 1) id = p.substring(1);
        } else {
            id = u.getQueryParameter("v");
            String p = u.getPath();
            if (id == null && p != null) {
                if (p.startsWith("/shorts/")) id = p.substring(8);
                else if (p.startsWith("/embed/")) id = p.substring(7);
                else if (p.startsWith("/live/")) id = p.substring(6);
            }
        }
        if (id == null) return null;
        int cut = id.indexOf('/');
        if (cut > 0) id = id.substring(0, cut);
        return id.matches("[A-Za-z0-9_-]{11}") ? id : null;
    }
}
