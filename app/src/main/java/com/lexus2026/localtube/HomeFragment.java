package com.lexus2026.localtube;

import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.*;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.lang.ref.*;
import java.util.*;

public class HomeFragment {

    private static final int ITEM_TYPE_VIDEO      = 0;
    private static final int ITEM_TYPE_SERIES     = 1;
    private static final int ITEM_TYPE_MOVIE      = 2;
    private static final int ITEM_TYPE_SHORTS_ROW = 3;

    private static final int SHORTS_ROW_EVERY = 8;
    private static final int SHORTS_PER_ROW   = 10;

    private static final int PAGE_SIZE = 30;

    private static final int COLOR_TEXT   = 0xFFFFFFFF;
    private static final int COLOR_TEXT2  = 0xFFA0A0A0;
    private static final int COLOR_TEXT3  = 0xFF5C5C5C;
    private static final int COLOR_THUMB  = 0xFF232323;
    private static final int COLOR_BADGE  = 0xE6000000;
    private static final int COLOR_PRIMARY= 0xFFFF004D;

    private final MainActivity activity;
    private final VideoIndex index;
    private final RecommendationEngine engine;
    private final ListView listView;
    private final HomeAdapter adapter;

    private List<VideoItem> allScored = new ArrayList<VideoItem>();
    private int loadedCount = 0;
    private int shortsOffset = 0;
    private boolean loadingMore = false;

    public HomeFragment(MainActivity activity, VideoIndex index, RecommendationEngine engine) {
        this.activity = activity;
        this.index    = index;
        this.engine   = engine;

        listView = new ListView(activity);
        listView.setDivider(null);
        listView.setDividerHeight(0);
        listView.setLayoutParams(new ViewGroup.LayoutParams(
                                     ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        listView.setBackgroundColor(0xFF0A0A0A);
        listView.setVerticalScrollBarEnabled(false);
        listView.setPadding(0, dp(6), 0, dp(12));
        listView.setClipToPadding(false);

        adapter = new HomeAdapter(activity);
        listView.setAdapter(adapter);

        listView.setOnScrollListener(new AbsListView.OnScrollListener() {
				@Override public void onScrollStateChanged(AbsListView view, int scrollState) {}

				@Override public void onScroll(AbsListView view, int firstVisible,
											   int visibleCount, int totalCount) {
					if (loadingMore) return;
					if (totalCount == 0) return;
					boolean nearEnd = (firstVisible + visibleCount) >= (totalCount - 3);
					if (nearEnd && loadedCount < allScored.size()) {
						loadMoreItems();
					}
				}
			});

        refresh();
    }

    public View getView() { return listView; }

    public void refresh() {
        List<VideoItem> all = index.getAll();
        allScored = engine.recommend(all, all.size());
        List<VideoItem> filtered = new ArrayList<VideoItem>();
        for (VideoItem v : allScored) {
            if (v.type != VideoItem.TYPE_SHORT) filtered.add(v);
        }
        allScored = filtered;

        loadedCount  = 0;
        shortsOffset = 0;
        adapter.clear();
        loadMoreItems();
    }

    private void loadMoreItems() {
        if (loadedCount >= allScored.size()) return;
        loadingMore = true;

        int end = Math.min(loadedCount + PAGE_SIZE, allScored.size());
        List<VideoItem> page = allScored.subList(loadedCount, end);
        List<Object> newItems = interleaveShorts(page, loadedCount);
        loadedCount = end;

        adapter.appendItems(newItems);
        loadingMore = false;
    }

    private List<Object> interleaveShorts(List<VideoItem> mixed, int offset) {
        List<VideoItem> shorts = index.getShorts();
        List<Object> result = new ArrayList<Object>();

        for (int i = 0; i < mixed.size(); i++) {
            result.add(mixed.get(i));
            int globalIndex = offset + i + 1;
            boolean atBoundary = (globalIndex % SHORTS_ROW_EVERY) == 0;
            if (atBoundary && !shorts.isEmpty()) {
                List<VideoItem> row = new ArrayList<VideoItem>();
                for (int k = 0; k < SHORTS_PER_ROW; k++) {
                    row.add(shorts.get((shortsOffset + k) % shorts.size()));
                }
                shortsOffset = (shortsOffset + SHORTS_PER_ROW) % shorts.size();
                result.add(new ShortsRow(row, shortsOffset));
            }
        }
        return result;
    }

    private static class ShortsRow {
        final List<VideoItem> items;
        final int baseShortsIndex;
        ShortsRow(List<VideoItem> items, int baseShortsIndex) {
            this.items = items;
            this.baseShortsIndex = baseShortsIndex;
        }
    }

    private class HomeAdapter extends BaseAdapter {

        private final List<Object> items = new ArrayList<Object>();
        private final Context ctx;

        HomeAdapter(Context ctx) { this.ctx = ctx; }

        void clear() {
            items.clear();
            notifyDataSetChanged();
        }

        void appendItems(List<Object> newItems) {
            items.addAll(newItems);
            notifyDataSetChanged();
        }

        @Override public int getCount() { return items.size(); }
        @Override public Object getItem(int pos) { return items.get(pos); }
        @Override public long getItemId(int pos) { return pos; }
        @Override public int getViewTypeCount() { return 4; }

        @Override
        public int getItemViewType(int position) {
            Object o = items.get(position);
            if (o instanceof ShortsRow) return ITEM_TYPE_SHORTS_ROW;
            VideoItem v = (VideoItem) o;
            if (v.type == VideoItem.TYPE_SERIES) return ITEM_TYPE_SERIES;
            if (v.type == VideoItem.TYPE_MOVIE)  return ITEM_TYPE_MOVIE;
            return ITEM_TYPE_VIDEO;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            Object o = items.get(position);
            int viewType = getItemViewType(position);

            if (viewType == ITEM_TYPE_SHORTS_ROW) {
                return buildShortsRow((ShortsRow) o);
            }

            RowHolder h;
            if (convertView == null || !(convertView.getTag() instanceof RowHolder)) {
                h = new RowHolder(ctx);
                convertView = h.wrapper;
                convertView.setTag(h);
            } else {
                h = (RowHolder) convertView.getTag();
            }

            final VideoItem item = (VideoItem) o;
            if (viewType == ITEM_TYPE_SERIES) h.bindSeries(item);
            else if (viewType == ITEM_TYPE_MOVIE) h.bindMovie(item);
            else h.bindVideo(item);

            convertView.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        if (item.type == VideoItem.TYPE_SERIES) activity.openSeriesPlayer(item);
                        else activity.openPlayer(item);
                    }
                });

            return convertView;
        }
    }

    // ── ViewHolder ───────────────────────────────────────────────────────────

    private class RowHolder {
        final LinearLayout wrapper;
        final ImageView thumb;
        final TextView durBadge;
        final TextView title;
        final TextView channelName;
        final TextView meta;
        final View progress;

        RowHolder(Context ctx) {
            wrapper = new LinearLayout(ctx);
            wrapper.setOrientation(LinearLayout.VERTICAL);
            wrapper.setPadding(dp(12), 0, dp(12), dp(4));

            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(dp(10), dp(10), dp(10), dp(10));

            GradientDrawable rowBg = new GradientDrawable();
            rowBg.setColor(0xFF141414);
            rowBg.setCornerRadius(dp(16));
            row.setBackground(rowBg);
            wrapper.addView(row, new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            FrameLayout thumbBox = new FrameLayout(ctx);
            int tw = dp(160);
            int th = dp(92);
            LinearLayout.LayoutParams tbLp = new LinearLayout.LayoutParams(tw, th);
            row.addView(thumbBox, tbLp);

            thumb = new ImageView(ctx);
            thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
            GradientDrawable tbg = new GradientDrawable();
            tbg.setColor(COLOR_THUMB);
            tbg.setCornerRadius(dp(12));
            thumb.setBackground(tbg);
            if (Build.VERSION.SDK_INT >= 21) thumb.setClipToOutline(true);
            thumbBox.addView(thumb, new FrameLayout.LayoutParams(
                                 ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            progress = new View(ctx);
            GradientDrawable pbg = new GradientDrawable();
            pbg.setColor(COLOR_PRIMARY);
            progress.setBackground(pbg);
            FrameLayout.LayoutParams pLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(4));
            pLp.gravity = Gravity.BOTTOM;
            thumbBox.addView(progress, pLp);
            progress.setVisibility(View.GONE);

            durBadge = new TextView(ctx);
            durBadge.setTextColor(COLOR_TEXT);
            durBadge.setTextSize(10);
            durBadge.setTypeface(null, Typeface.BOLD);
            durBadge.setPadding(dp(7), dp(3), dp(7), dp(3));
            GradientDrawable dbg = new GradientDrawable();
            dbg.setColor(COLOR_BADGE);
            dbg.setCornerRadius(dp(8));
            durBadge.setBackground(dbg);
            FrameLayout.LayoutParams dLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            dLp.gravity = Gravity.BOTTOM | Gravity.END;
            dLp.setMargins(0, 0, dp(7), dp(7));
            thumbBox.addView(durBadge, dLp);

            LinearLayout textCol = new LinearLayout(ctx);
            textCol.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams tcLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            tcLp.leftMargin = dp(12);
            textCol.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(textCol, tcLp);

            title = new TextView(ctx);
            title.setTextColor(COLOR_TEXT);
            title.setTextSize(14);
            title.setTypeface(null, Typeface.BOLD);
            title.setMaxLines(2);
            title.setEllipsize(TextUtils.TruncateAt.END);
            title.setLineSpacing(dp(2), 1f);
            textCol.addView(title);

            channelName = new TextView(ctx);
            channelName.setTextColor(COLOR_TEXT2);
            channelName.setTextSize(12);
            channelName.setTypeface(null, android.graphics.Typeface.BOLD);
            channelName.setMaxLines(1);
            channelName.setEllipsize(TextUtils.TruncateAt.END);
            channelName.setPadding(0, dp(3), 0, 0);
            channelName.setVisibility(View.GONE);
            // Necesario para que el toque no se lo coma el OnClickListener de la fila.
            channelName.setFocusable(true);
            channelName.setClickable(true);
            textCol.addView(channelName);

            meta = new TextView(ctx);
            meta.setTextColor(COLOR_TEXT2);
            meta.setTextSize(12);
            meta.setMaxLines(1);
            meta.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams mLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            mLp.topMargin = dp(6);
            textCol.addView(meta, mLp);
        }

        void bindVideo(VideoItem item) {
            title.setText(item.title);
            bindChannel(item);
            StringBuilder sb = new StringBuilder();
            sb.append(item.getDurationFormatted());
            String res = item.getResolution();
            if (!res.isEmpty()) sb.append("  \u00B7  ").append(res);
            if (item.fileSize > 0) sb.append("  \u00B7  ").append(item.getFileSizeFormatted());
            meta.setText(sb.toString());
            durBadge.setText(item.getDurationFormatted());
            durBadge.setVisibility(item.duration > 0 ? View.VISIBLE : View.GONE);
            bindProgress(item);
            loadThumb(item);
        }

        void bindSeries(VideoItem item) {
            String sn = item.seriesName != null ? item.seriesName : item.title;
            title.setText(sn);
            // FIX: la serie también muestra el canal al que pertenece.
            bindChannel(item);
            String ep = item.episode >= 0 ? Lang.get("chapter_prefix") + item.episode : Lang.get("chapter_none");
            meta.setText(ep + "  \u00B7  " + item.getDurationFormatted());
            durBadge.setVisibility(View.GONE);
            bindProgress(item);
            loadThumb(item);
        }

        void bindMovie(VideoItem item) {
            title.setText(item.title);
            bindChannel(item);
            StringBuilder sb = new StringBuilder();
            sb.append(item.getDurationFormatted());
            String res = item.getResolution();
            if (!res.isEmpty()) sb.append("  \u00B7  ").append(res);
            meta.setText(sb.toString());
            durBadge.setText(item.getDurationFormatted());
            durBadge.setVisibility(item.duration > 0 ? View.VISIBLE : View.GONE);
            bindProgress(item);
            loadThumb(item);
        }

        /**
         * Muestra el nombre del canal (item.category) y le engancha el click
         * que abre ChannelActivity con el folderId correcto (item.channelId).
         * Funciona tanto para videos sueltos como para episodios de serie.
         */
        private void bindChannel(final VideoItem item) {
            if (item.category == null || item.category.isEmpty()
                || item.channelId == null || item.channelId.isEmpty()) {
                channelName.setVisibility(View.GONE);
                channelName.setOnClickListener(null);
                return;
            }
            channelName.setText(item.category);
            channelName.setVisibility(View.VISIBLE);
            channelName.setOnClickListener(new View.OnClickListener() {
					@Override public void onClick(View v) {
						Intent i = new Intent(activity, ChannelActivity.class);
						i.putExtra(ChannelActivity.EXTRA_CHANNEL_ID, item.channelId);
						activity.startActivity(i);
					}
				});
        }

        private void bindProgress(VideoItem item) {
            if (item.lastPosition > 0 && item.duration > 0) {
                float pct = (float) item.lastPosition / item.duration;
                if (pct > 0.02f && pct < 0.98f) {
                    progress.setScaleX(pct);
                    progress.setVisibility(View.VISIBLE);
                } else {
                    progress.setVisibility(View.GONE);
                }
            } else {
                progress.setVisibility(View.GONE);
            }
        }

        private void loadThumb(VideoItem item) {
            thumb.setImageDrawable(null);
            if (item.thumbPath != null) new ThumbLoader(thumb).execute(item.thumbPath);
        }
    }

    // ── Fila de Shorts ───────────────────────────────────────────────────────

    private View buildShortsRow(final ShortsRow shortsRow) {
        LinearLayout wrapper = new LinearLayout(activity);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setPadding(0, dp(8), 0, dp(12));

        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(18), dp(4), dp(18), dp(10));

        View accent = new View(activity);
        GradientDrawable abg = new GradientDrawable();
        abg.setColor(COLOR_PRIMARY);
        abg.setCornerRadius(dp(3));
        accent.setBackground(abg);
        header.addView(accent, new LinearLayout.LayoutParams(dp(5), dp(20)));

        TextView title = new TextView(activity);
        title.setText("  " + Lang.get("tab_shorts"));
        title.setTextColor(COLOR_TEXT);
        title.setTextSize(17);
        title.setTypeface(null, Typeface.BOLD);
        title.setLetterSpacing(0.01f);
        header.addView(title);

        wrapper.addView(header);

        HorizontalScrollView hscroll = new HorizontalScrollView(activity);
        hscroll.setLayoutParams(new LinearLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        hscroll.setHorizontalScrollBarEnabled(false);
        hscroll.setClipToPadding(false);

        LinearLayout line = new LinearLayout(activity);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setPadding(dp(14), 0, dp(14), 0);

        int thumbW = dp(112);
        int thumbH = dp(198);
        int shortsTotal = Math.max(1, index.getShorts().size());
        int startShortsIndex = shortsRow.baseShortsIndex - shortsRow.items.size();
        if (startShortsIndex < 0) startShortsIndex += shortsTotal;

        for (int i = 0; i < shortsRow.items.size(); i++) {
            final VideoItem item = shortsRow.items.get(i);
            final int absIndex = (startShortsIndex + i) % shortsTotal;

            LinearLayout card = new LinearLayout(activity);
            card.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                thumbW, ViewGroup.LayoutParams.WRAP_CONTENT);
            cardLp.setMargins(dp(5), 0, dp(5), 0);
            card.setLayoutParams(cardLp);

            FrameLayout thumbBox = new FrameLayout(activity);
            LinearLayout.LayoutParams tbLp = new LinearLayout.LayoutParams(thumbW, thumbH);
            card.addView(thumbBox, tbLp);

            ImageView thumb = new ImageView(activity);
            thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
            GradientDrawable tbg = new GradientDrawable();
            tbg.setColor(COLOR_THUMB);
            tbg.setCornerRadius(dp(14));
            thumb.setBackground(tbg);
            if (Build.VERSION.SDK_INT >= 21) thumb.setClipToOutline(true);
            thumbBox.addView(thumb, new FrameLayout.LayoutParams(
                                 ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            if (item.thumbPath != null) new ThumbLoader(thumb).execute(item.thumbPath);

            TextView cTitle = new TextView(activity);
            cTitle.setText(item.title);
            cTitle.setTextColor(COLOR_TEXT);
            cTitle.setTextSize(12);
            cTitle.setMaxLines(2);
            cTitle.setEllipsize(TextUtils.TruncateAt.END);
            cTitle.setLineSpacing(dp(2), 1f);
            cTitle.setPadding(dp(4), dp(8), dp(4), 0);
            cTitle.setLayoutParams(new LinearLayout.LayoutParams(
                                       thumbW, ViewGroup.LayoutParams.WRAP_CONTENT));
            card.addView(cTitle);

            card.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { activity.openShorts(absIndex); }
                });

            line.addView(card);
        }

        hscroll.addView(line);
        wrapper.addView(hscroll);
        return wrapper;
    }

    // ── Utilidades ───────────────────────────────────────────────────────────

    private int dp(int v) {
        return (int) (v * activity.getResources().getDisplayMetrics().density + 0.5f);
    }

    static class ThumbLoader extends AsyncTask<String, Void, Bitmap> {
        private final WeakReference<ImageView> ref;
        ThumbLoader(ImageView iv) { ref = new WeakReference<ImageView>(iv); }

        @Override
        protected Bitmap doInBackground(String... paths) {
            try {
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inSampleSize = 1;
                return BitmapFactory.decodeFile(paths[0], opts);
            } catch (Exception e) { return null; }
        }

        @Override
        protected void onPostExecute(Bitmap bmp) {
            ImageView iv = ref.get();
            if (iv != null && bmp != null) iv.setImageBitmap(bmp);
        }
    }
}
