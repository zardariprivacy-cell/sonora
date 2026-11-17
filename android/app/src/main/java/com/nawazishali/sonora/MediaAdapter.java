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
        v.setBackground(Ui.cardBg());
        v.setElevation(Ui.dp(3));
        ImageLoader.load(ctx, it, thumb);
        // v3.3 music vibe: mini EQ bars on the currently-playing song's row.
        View eq = v.findViewById(R.id.eqbars);
        if (eq != null) {
            MusicService svc = MusicService.instance;
            boolean current = !grid && !it.video && svc != null && svc.current() != null
                    && it.key().equals(svc.current().key());
            eq.setVisibility(current ? View.VISIBLE : View.GONE);
            if (current && eq instanceof AnimatedBarsView)
                ((AnimatedBarsView) eq).setAnimating(svc.isPlaying());
        }
        boolean isFav = Db.get(ctx).isFavorite(it.key());
        fav.setVisibility(isFav ? View.VISIBLE : View.GONE);
        if (isFav) fav.setColorFilter(Theme.ACCENT);
        ImageView badge = v.findViewById(R.id.badge);
        if (badge != null) {
            badge.setVisibility(it.video ? View.VISIBLE : View.GONE);
            if (it.video) badge.setColorFilter(Theme.TEXT);
        }
        // Overflow button (v3.1): opens the 3D MediaSheet, same as long-press.
        ImageView more = v.findViewById(R.id.more);
        if (more != null) {
            more.setColorFilter(grid ? 0xFFFFFFFF : Theme.TEXT2);
            more.setOnClickListener(vw -> {
                if (ctx instanceof android.app.Activity)
                    ItemMenu.show((android.app.Activity) ctx, it, null);
            });
        }
        return v;
    }
}
