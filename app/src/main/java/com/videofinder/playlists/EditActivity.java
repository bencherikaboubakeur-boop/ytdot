package com.videofinder.playlists;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class EditActivity extends AppCompatActivity {

    private EditText title, url, tag, note;
    private String id = null;
    private boolean existing = false;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_edit);

        title = findViewById(R.id.fTitle);
        url = findViewById(R.id.fUrl);
        tag = findViewById(R.id.fTag);
        note = findViewById(R.id.fNote);

        id = getIntent() != null ? getIntent().getStringExtra("id") : null;
        Store.Item it = Store.find(this, id);
        if (it != null) {
            existing = true;
            id = it.id;
            title.setText(it.title);
            url.setText(it.url);
            tag.setText(it.tag);
            note.setText(it.note);
        } else {
            id = null;
            String shared = getIntent() != null ? getIntent().getStringExtra("shared_url") : null;
            String sharedTitle = getIntent() != null ? getIntent().getStringExtra("shared_title") : null;
            String safe = SafeLink.clean(shared);
            if (safe != null) url.setText(safe);
            if (sharedTitle != null) title.setText(sharedTitle);
        }

        Button save = findViewById(R.id.btnSave);
        Button open = findViewById(R.id.btnOpen);
        Button delete = findViewById(R.id.btnDelete);
        delete.setVisibility(existing ? android.view.View.VISIBLE : android.view.View.GONE);

        save.setOnClickListener(v -> save());
        open.setOnClickListener(v -> openExternally(url.getText().toString()));
        delete.setOnClickListener(v -> {
            if (id != null) Store.delete(this, id);
            finish();
        });
    }

    private void save() {
        Store.Item it = new Store.Item();
        it.id = id == null ? "" : id;
        it.title = title.getText().toString();
        it.url = url.getText().toString();
        it.tag = tag.getText().toString();
        it.note = note.getText().toString();
        if (existing) it.watched = false;

        if (SafeLink.clean(it.url) == null) {
            toast(getString(R.string.invalid_link));
            return;
        }
        if (!Store.upsert(this, it)) {
            toast(getString(R.string.already_saved));
            return;
        }
        toast(getString(R.string.saved));
        finish();
    }

    private void openExternally(String raw) {
        String safe = SafeLink.clean(raw);
        if (safe == null) { toast(getString(R.string.invalid_link)); return; }
        Uri u = Uri.parse(safe);
        try {
            Intent yt = new Intent(Intent.ACTION_VIEW, u);
            yt.setPackage("com.google.android.youtube");
            startActivity(yt);
            return;
        } catch (ActivityNotFoundException ignored) { }
        try {
            Intent web = new Intent(Intent.ACTION_VIEW, u);
            web.addCategory(Intent.CATEGORY_BROWSABLE);
            startActivity(web);
        } catch (ActivityNotFoundException e) {
            toast(getString(R.string.no_browser));
        }
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
}
