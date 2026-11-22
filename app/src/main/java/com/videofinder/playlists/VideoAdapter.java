package com.videofinder.playlists;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class VideoAdapter extends RecyclerView.Adapter<VideoAdapter.VH> {

    public interface Callback {
        void open(Store.Item it);
        void edit(Store.Item it);
        void toggleWatched(Store.Item it);
        void delete(Store.Item it);
    }

    private final List<Store.Item> data = new ArrayList<>();
    private final Callback cb;

    public VideoAdapter(Callback cb) { this.cb = cb; }

    public void submit(List<Store.Item> items) {
        data.clear();
        data.addAll(items);
        notifyDataSetChanged();
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_video, parent, false);
        return new VH(v);
    }

    @Override public void onBindViewHolder(@NonNull VH h, int position) {
        Store.Item it = data.get(position);
        String t = it.title == null || it.title.isEmpty() ? "?" : it.title.trim();
        h.tile.setText(t.substring(0, 1).toUpperCase(Locale.ROOT));
        h.title.setText(t);
        h.title.setAlpha(it.watched ? 0.55f : 1f);

        StringBuilder meta = new StringBuilder();
        if (!it.tag.isEmpty()) meta.append(it.tag);
        if (it.watched) { if (meta.length() > 0) meta.append(" · "); meta.append(h.itemView.getContext().getString(R.string.watched)); }
        if (!it.note.isEmpty()) { if (meta.length() > 0) meta.append(" · "); meta.append(it.note); }
        if (meta.length() == 0) meta.append(it.url);
        h.meta.setText(meta.toString());

        h.itemView.setOnClickListener(v -> cb.open(it));
        h.more.setOnClickListener(v -> {
            PopupMenu p = new PopupMenu(v.getContext(), v);
            p.getMenu().add(0, 1, 0, R.string.open_link);
            p.getMenu().add(0, 2, 1, R.string.edit);
            p.getMenu().add(0, 3, 2, it.watched ? R.string.mark_unwatched : R.string.mark_watched);
            p.getMenu().add(0, 4, 3, R.string.delete);
            p.setOnMenuItemClickListener(mi -> {
                switch (mi.getItemId()) {
                    case 1: cb.open(it); return true;
                    case 2: cb.edit(it); return true;
                    case 3: cb.toggleWatched(it); return true;
                    case 4: cb.delete(it); return true;
                }
                return false;
            });
            p.show();
        });
    }

    @Override public int getItemCount() { return data.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final TextView tile, title, meta;
        final ImageButton more;
        VH(@NonNull View v) {
            super(v);
            tile = v.findViewById(R.id.tile);
            title = v.findViewById(R.id.title);
            meta = v.findViewById(R.id.meta);
            more = v.findViewById(R.id.more);
        }
    }
}
