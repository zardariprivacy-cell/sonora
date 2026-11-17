package com.nawazishali.sonora;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

/** List/grid adapter for media items: thumbnail, title, subtitle, favorite marker. */
public class MediaAdapter extends BaseAdapter {
    private final Context ctx;
    private List<MediaItem> items;
    private final boolean grid;

    public MediaAdapter(Context ctx, List<MediaItem> items, boolean grid) {
        this.ctx = ctx; this.items = items; this.grid = grid;
    }

    public void setItems(List<MediaItem> items) { this.items = items; notifyDataSetChanged(); }

    @Override public int getCount() { return items == null ? 0 : items.size(); }
    @Override public MediaItem getItem(int i) { return items.get(i); }
    @Override public long getItemId(int i) { return i; }

    @Override public View getView(int pos, View convert, ViewGroup parent) {
        View v = convert;
        if (v == null) v = LayoutInflater.from(ctx).inflate(
                grid ? R.layout.item_media_grid : R.layout.item_media_list, parent, false);
        MediaItem it = items.get(pos);
        ImageView thumb = v.findViewById(R.id.thumb);
        TextView title = v.findViewById(R.id.title);
        TextView sub = v.findViewById(R.id.subtitle);
        ImageView fav = v.findViewById(R.id.fav);
        title.setText(it.title);
        title.setTextColor(Theme.TEXT);
        sub.setText(it.subtitle());
        sub.setTextColor(Theme.TEXT2);
        v.setBackgroundColor(Theme.CARD);
        ImageLoader.load(ctx, it, thumb);
        boolean isFav = Db.get(ctx).isFavorite(it.key());
        fav.setVisibility(isFav ? View.VISIBLE : View.GONE);
        if (isFav) fav.setColorFilter(Theme.ACCENT);
        ImageView badge = v.findViewById(R.id.badge);
        if (badge != null) {
            badge.setVisibility(it.video ? View.VISIBLE : View.GONE);
            if (it.video) badge.setColorFilter(Theme.TEXT);
        }
        return v;
    }
}
