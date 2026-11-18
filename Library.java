package com.videofinder.playlists;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class Library {
    public static class Item {
        public String title, url;
        public Item(String t, String u){ title=t; url=u; }
    }

    private static SharedPreferences sp(Context c){
        return c.getSharedPreferences("library", Context.MODE_PRIVATE);
    }

    public static List<Item> all(Context c){
        List<Item> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(sp(c).getString("items","[]"));
            for (int i=0;i<a.length();i++){
                JSONObject o=a.getJSONObject(i);
                out.add(new Item(o.optString("t"), o.optString("u")));
            }
        } catch (Exception ignored){}
        return out;
    }

    public static void add(Context c, String title, String url){
        if (url == null || url.isEmpty()) return;
        List<Item> items = all(c);
        for (Item i : items) if (i.url.equals(url)) return;
        items.add(0, new Item(title == null ? url : title, url));
        save(c, items);
    }

    public static void remove(Context c, String url){
        List<Item> items = all(c);
        items.removeIf(i -> i.url.equals(url));
        save(c, items);
    }

    private static void save(Context c, List<Item> items){
        JSONArray a = new JSONArray();
        try {
            for (Item i : items){
                JSONObject o = new JSONObject();
                o.put("t", i.title); o.put("u", i.url);
                a.put(o);
            }
        } catch (Exception ignored){}
        sp(c).edit().putString("items", a.toString()).apply();
    }
}
