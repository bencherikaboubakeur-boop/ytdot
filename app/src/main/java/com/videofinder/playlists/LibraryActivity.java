package com.videofinder.playlists;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import java.util.ArrayList;
import java.util.List;

public class LibraryActivity extends Activity {
    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        ListView lv = new ListView(this);
        setContentView(lv);

        List<Library.Item> items = Library.all(this);
        List<String> labels = new ArrayList<>();
        for (Library.Item i : items) labels.add(i.title);
        if (labels.isEmpty()) labels.add("Nothing saved yet — tap ★ on a video");

        lv.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, labels));

        lv.setOnItemClickListener((p,v,pos,ids) -> {
            if (pos >= items.size()) return;
            Intent d = new Intent();
            d.putExtra("url", items.get(pos).url);
            setResult(RESULT_OK, d);
            finish();
        });

        lv.setOnItemLongClickListener((p,v,pos,ids) -> {
            if (pos >= items.size()) return true;
            Library.remove(this, items.get(pos).url);
            recreate();
            return true;
        });
    }
}