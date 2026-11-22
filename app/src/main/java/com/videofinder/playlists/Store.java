package com.videofinder.playlists;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;
import java.util.UUID;

public final class Store {
    private Store() {}

    public static final class Item {
        public String id = "", title = "", url = "", note = "", tag = "";
        public boolean watched = false;
        public long addedAt = 0L;
    }

    private static final String FILE = "library";
    private static final String KEY = "items_v2";
    private static final String OLD_KEY = "items";

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static List<Item> all(Context c) {
        migrate(c);
        List<Item> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(sp(c).getString(KEY, "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Item it = new Item();
                it.id = o.optString("id", UUID.randomUUID().toString());
                it.title = o.optString("t", "");
                it.url = o.optString("u", "");
                it.note = o.optString("n", "");
                it.tag = o.optString("g", "");
                it.watched = o.optBoolean("w", false);
                it.addedAt = o.optLong("a", System.currentTimeMillis());
                if (SafeLink.clean(it.url) != null) out.add(it);
            }
        } catch (Exception ignored) {}
        return out;
    }

    /** One-time upgrade from the old {"t","u"} format so nobody loses their saved list. */
    private static void migrate(Context c) {
        SharedPreferences p = sp(c);
        if (!p.contains(OLD_KEY)) return;
        try {
            JSONArray old = new JSONArray(p.getString(OLD_KEY, "[]"));
            JSONArray now = new JSONArray(p.getString(KEY, "[]"));
            for (int i = 0; i < old.length(); i++) {
                JSONObject o = old.getJSONObject(i);
                String url = SafeLink.clean(o.optString("u"));
                if (url == null) continue;
                JSONObject n = new JSONObject();
                n.put("id", UUID.randomUUID().toString());
                n.put("t", o.optString("t", url));
                n.put("u", url);
                n.put("n", "");
                n.put("g", "");
                n.put("w", false);
                n.put("a", System.currentTimeMillis());
                now.put(n);
            }
            p.edit().putString(KEY, now.toString()).remove(OLD_KEY).apply();
        } catch (Exception e) {
            p.edit().remove(OLD_KEY).apply();
        }
    }

    private static void save(Context c, List<Item> items) {
        JSONArray a = new JSONArray();
        try {
            for (Item it : items) {
                JSONObject o = new JSONObject();
                o.put("id", it.id);
                o.put("t", it.title);
                o.put("u", it.url);
                o.put("n", it.note);
                o.put("g", it.tag);
                o.put("w", it.watched);
                o.put("a", it.addedAt);
                a.put(o);
            }
        } catch (Exception ignored) {}
        sp(c).edit().putString(KEY, a.toString()).apply();
    }

    public static Item find(Context c, String id) {
        if (id == null) return null;
        for (Item it : all(c)) if (id.equals(it.id)) return it;
        return null;
    }

    /** Insert or update. Returns false if the URL is not allowed or already saved. */
    public static boolean upsert(Context c, Item it) {
        String url = SafeLink.clean(it.url);
        if (url == null) return false;
        it.url = url;
        if (it.title == null || it.title.trim().isEmpty()) {
            String vid = SafeLink.videoId(url);
            it.title = vid != null ? "Video " + vid : url;
        }
        it.title = it.title.trim();
        it.note = it.note == null ? "" : it.note.trim();
        it.tag = it.tag == null ? "" : it.tag.trim();

        List<Item> items = all(c);
        for (Item o : items) {
            if (o.url.equals(url) && !o.id.equals(it.id)) return false;
        }
        if (it.id == null || it.id.isEmpty()) {
            it.id = UUID.randomUUID().toString();
            it.addedAt = System.currentTimeMillis();
            items.add(0, it);
        } else {
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i).id.equals(it.id)) { it.addedAt = items.get(i).addedAt; items.set(i, it); break; }
            }
        }
        save(c, items);
        return true;
    }

    public static void delete(Context c, String id) {
        List<Item> items = all(c);
        items.removeIf(i -> i.id.equals(id));
        save(c, items);
    }

    public static void setWatched(Context c, String id, boolean w) {
        List<Item> items = all(c);
        for (Item i : items) if (i.id.equals(id)) i.watched = w;
        save(c, items);
    }

    public static List<String> tags(Context c) {
        TreeSet<String> s = new TreeSet<>();
        for (Item i : all(c)) if (!i.tag.isEmpty()) s.add(i.tag);
        return new ArrayList<>(s);
    }

    public static String exportJson(Context c) {
        JSONArray a = new JSONArray();
        try {
            for (Item it : all(c)) {
                JSONObject o = new JSONObject();
                o.put("t", it.title); o.put("u", it.url); o.put("n", it.note);
                o.put("g", it.tag); o.put("w", it.watched); o.put("a", it.addedAt);
                a.put(o);
            }
        } catch (Exception ignored) {}
        return a.toString();
    }

    public static int importJson(Context c, String json) {
        int added = 0;
        try {
            JSONArray a = new JSONArray(json);
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Item it = new Item();
                it.title = o.optString("t", "");
                it.url = o.optString("u", "");
                it.note = o.optString("n", "");
                it.tag = o.optString("g", "");
                it.watched = o.optBoolean("w", false);
                if (upsert(c, it)) added++;
            }
        } catch (Exception ignored) {}
        return added;
    }
}
