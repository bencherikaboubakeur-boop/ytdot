package com.videofinder.playlists;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.UserMessagingPlatform;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.messaging.FirebaseMessaging;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements VideoAdapter.Callback {

    private static final String TAG = "VideoFinder";

    private VideoAdapter adapter;
    private TextView empty;
    private FrameLayout adSlot;
    private Ads ads;
    private ConsentInformation consent;

    private String query = "";
    private String filterTag = null;
    private boolean hideWatched = false;
    private int sortMode = 0; // 0 newest, 1 title, 2 unwatched first

    private final ActivityResultLauncher<String> exportLauncher =
            registerForActivityResult(new ActivityResultContracts.CreateDocument("application/json"), uri -> {
                if (uri == null) return;
                try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                    if (os == null) throw new IllegalStateException("null stream");
                    os.write(Store.exportJson(this).getBytes(StandardCharsets.UTF_8));
                    toast(getString(R.string.exported));
                } catch (Exception e) {
                    Log.e(TAG, "export", e);
                    toast(getString(R.string.export_failed));
                }
            });

    private final ActivityResultLauncher<String[]> importLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri == null) return;
                StringBuilder sb = new StringBuilder();
                try (InputStream is = getContentResolver().openInputStream(uri)) {
                    if (is == null) throw new IllegalStateException("null stream");
                    BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                    String line;
                    while ((line = r.readLine()) != null) sb.append(line);
                    int n = Store.importJson(this, sb.toString());
                    toast(getString(R.string.imported, n));
                    refresh();
                } catch (Exception e) {
                    Log.e(TAG, "import", e);
                    toast(getString(R.string.import_failed));
                }
            });

    private final ActivityResultLauncher<String> notifLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {});

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);

        empty = findViewById(R.id.empty);
        adSlot = findViewById(R.id.adSlot);
        ads = new Ads(this, adSlot);

        RecyclerView list = findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new VideoAdapter(this);
        list.setAdapter(adapter);

        EditText search = findViewById(R.id.search);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b2, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b2, int c) {}
            @Override public void afterTextChanged(Editable s) { query = s.toString(); refresh(); }
        });

        Button filter = findViewById(R.id.btnFilter);
        Button sort = findViewById(R.id.btnSort);
        Button tools = findViewById(R.id.btnTools);
        filter.setOnClickListener(this::showFilterMenu);
        sort.setOnClickListener(this::showSortMenu);
        tools.setOnClickListener(this::showToolsMenu);

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(v -> startActivity(new Intent(this, EditActivity.class)));

        setupConsentThenAds();
        setupNotifications();
    }

    @Override protected void onResume() {
        super.onResume();
        refresh();
        ads.resume();
        if (consent != null && consent.canRequestAds()) ads.load();
    }

    @Override protected void onPause() { ads.pause(); super.onPause(); }

    @Override protected void onDestroy() { ads.destroy(); super.onDestroy(); }

    private void refresh() {
        List<Store.Item> out = new ArrayList<>();
        String q = query.trim().toLowerCase(Locale.ROOT);
        for (Store.Item it : Store.all(this)) {
            if (filterTag != null && !filterTag.equals(it.tag)) continue;
            if (hideWatched && it.watched) continue;
            if (!q.isEmpty()) {
                String hay = (it.title + " " + it.note + " " + it.tag + " " + it.url).toLowerCase(Locale.ROOT);
                if (!hay.contains(q)) continue;
            }
            out.add(it);
        }
        if (sortMode == 1) Collections.sort(out, (x, y) -> x.title.compareToIgnoreCase(y.title));
        else if (sortMode == 2) Collections.sort(out, (x, y) -> Boolean.compare(x.watched, y.watched));
        else Collections.sort(out, (x, y) -> Long.compare(y.addedAt, x.addedAt));

        adapter.submit(out);
        empty.setVisibility(out.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void showFilterMenu(View anchor) {
        PopupMenu p = new PopupMenu(this, anchor);
        p.getMenu().add(0, 0, 0, R.string.all_tags);
        List<String> tags = Store.tags(this);
        for (int i = 0; i < tags.size(); i++) p.getMenu().add(0, i + 1, i + 1, tags.get(i));
        int toggleId = 9000;
        p.getMenu().add(0, toggleId, 9000,
                getString(hideWatched ? R.string.show_watched : R.string.hide_watched));
        p.setOnMenuItemClickListener(mi -> {
            int id = mi.getItemId();
            if (id == 0) filterTag = null;
            else if (id == toggleId) hideWatched = !hideWatched;
            else if (id - 1 < tags.size()) filterTag = tags.get(id - 1);
            refresh();
            return true;
        });
        p.show();
    }

    private void showSortMenu(View anchor) {
        PopupMenu p = new PopupMenu(this, anchor);
        p.getMenu().add(0, 0, 0, R.string.sort_newest);
        p.getMenu().add(0, 1, 1, R.string.sort_title);
        p.getMenu().add(0, 2, 2, R.string.sort_unwatched);
        p.setOnMenuItemClickListener(mi -> { sortMode = mi.getItemId(); refresh(); return true; });
        p.show();
    }

    private void showToolsMenu(View anchor) {
        PopupMenu p = new PopupMenu(this, anchor);
        p.getMenu().add(0, 1, 0, R.string.export_json);
        p.getMenu().add(0, 2, 1, R.string.import_json);
        p.setOnMenuItemClickListener(mi -> {
            if (mi.getItemId() == 1) exportLauncher.launch("video-finder-library.json");
            else importLauncher.launch(new String[]{"application/json", "text/plain"});
            return true;
        });
        p.show();
    }

    // ---- Callbacks ----
    @Override public void open(Store.Item it) { openExternally(it.url); }

    @Override public void edit(Store.Item it) {
        Intent i = new Intent(this, EditActivity.class);
        i.putExtra("id", it.id);
        startActivity(i);
    }

    @Override public void toggleWatched(Store.Item it) {
        Store.setWatched(this, it.id, !it.watched);
        refresh();
    }

    @Override public void delete(Store.Item it) {
        Store.delete(this, it.id);
        refresh();
        toast(getString(R.string.deleted));
    }

    /** Playback always happens in the official YouTube app, or the browser if it is absent. */
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

    private void setupConsentThenAds() {
        try {
            consent = UserMessagingPlatform.getConsentInformation(this);
            ConsentRequestParameters params = new ConsentRequestParameters.Builder().build();
            consent.requestConsentInfoUpdate(this, params,
                    () -> UserMessagingPlatform.loadAndShowConsentFormIfRequired(this, err -> {
                        if (consent.canRequestAds()) startAds();
                    }),
                    err -> { if (consent.canRequestAds()) startAds(); });
            if (consent.canRequestAds()) startAds();
        } catch (Exception e) {
            Log.e(TAG, "consent", e);
        }
    }

    private void startAds() { Ads.init(this, () -> ads.load()); }

    private void setupNotifications() {
        try {
            if (Build.VERSION.SDK_INT >= 33
                    && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
            FirebaseMessaging.getInstance().subscribeToTopic("all");
            FirebaseAnalytics.getInstance(this);
        } catch (Exception e) {
            Log.w(TAG, "notifications", e);
        }
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
}
