package com.videofinder.playlists;

import android.net.Uri;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Every URL that enters this app passes through here. https + exact host allowlist only. */
public final class SafeLink {
    private SafeLink() {}

    private static final Pattern URL_IN_TEXT =
            Pattern.compile("https://[^\\s\"'<>]+", Pattern.CASE_INSENSITIVE);

    public static String clean(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty() || s.length() > 2048) return null;

        // Chromium normalises '\' to '/' in https URLs; Uri.parse does not. Reject it.
        for int_placeholder();
        return null;
    }
}
