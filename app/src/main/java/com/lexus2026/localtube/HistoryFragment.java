package com.lexus2026.localtube;

import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.*;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.lang.ref.*;
import java.text.*;
import java.util.*;

public class HistoryFragment {

    private static final int COLOR_BG      = 0xFF0A0A0A;
    private static final int COLOR_CARD    = 0xFF141414;
    private static final int COLOR_THUMB   = 0xFF232323;
    private static final int COLOR_TEXT    = 0xFFFFFFFF;
    private static final int COLOR_TEXT2   = 0xFFA0A0A0;
    private static final int COLOR_TEXT3   = 0xFF5C5C5C;
    private static final int COLOR_PRIMARY = 0xFFFF004D;
    private static final int COLOR_PROGRESS= 0xFFFF004D;
    private static final int COLOR_BADGE   = 0xE6000000;

    private final MainActivity activity;
    private final VideoIndex index;
    private final ListView listView;

    public HistoryFragment(MainActivity activity, VideoIndex index) {
        this.activity = activity;
        this.index    = index;

        listView = new ListView(activity);
        listView.setDivider(null);
        listView.setDividerHeight(0);
        listView.setLayoutParams(new ViewGroup.LayoutParams(
									 ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        listView.setBackgroundColor(COLOR_BG);
        listView.setVerticalScrollBarEnabled(false);
        listView.setPadding(0, dp(6), 0, dp(80));
        listView.setClipToPadding(false);

        buildContent();
    }

    public View getView() { return listView; }

    public void refresh() {
        buildContent();
    }

    private void buildContent() {
        List<VideoItem> history = getHistory();

        if (history.isEmpty()) {
            listView.setAdapter(null);
            // Mostrar mensaje vacío via footer
            TextView empty = new TextView(activity);
            empty.setText(Lang.get("history_empty"));
            empty.setTextColor(COLOR_TEXT2);
            empty.setTextSize(14);
            empty.setGravity(Gravity.CENTER);
            empty.setLineSpacing(dp(6), 1f);
            empty.setPadding(dp(32), dp(80), dp(32), 0);
            listView.addHeaderView(empty, null, false);
            return;
        }

        HistoryAdapter adapter = new HistoryAdapter(activity, history);
        listView.setAdapter(adapter);
    }

    /** Devuelve los videos reproducidos alguna vez, ordenados por lastWatched desc */
    private List<VideoItem> getHistory() {
        List<VideoItem> all = index.getAll();
        List<VideoItem> watched = new ArrayList<VideoItem>();
        for (VideoItem v : all) {
            if (v.lastWatched > 0) watched.add(v);
        }
        Collections.sort(watched, new Comparator<VideoItem>() {
				@Override public int compare(VideoItem a, VideoItem b) {
					return Long.compare(b.lastWatched, a.lastWatched);
				}
			});
        return watched;
    }

    private String formatRelativeTime(long timestampMs) {
        long now = System.currentTimeMillis();
        long diff = now - timestampMs;
        long minutes = diff / 60000;
        long hours   = diff / 3600000;
        long days    = diff / 86400000;
        if (minutes < 1)  return Lang.get("time_now");
        if (minutes < 60) return Lang.get("time_mins_ago") + minutes + Lang.get("time_mins_ago_suffix");
        if (hours < 24)   return Lang.get("time_hours_ago") + hours + Lang.get("time_hours_ago_suffix");
        if (days < 7)     return Lang.get("time_days_ago") + days + Lang.get("time_days_ago_suffix") + (days == 1 ? "" : Lang.get("time_days_ago_plural"));
        SimpleDateFormat sdf = new SimpleDateFormat("d MMM", new Locale(Lang.get("date_locale")));
        return sdf.format(new Date(timestampMs));
    }

    private int dp(int v) {
        return (int) (v * activity.getResources().getDisplayMetrics().density + 0.5f);
    }

    // -------------------------------------------------------------------------
    // Adapter
    // -------------------------------------------------------------------------

    private class HistoryAdapter extends BaseAdapter {

        private final List<VideoItem> items;
        private final Context ctx;

        HistoryAdapter(Context ctx, List<VideoItem> items) {
            this.ctx   = ctx;
            this.items = items;
        }

        @Override public int getCount() { return items.size(); }
        @Override public Object getItem(int pos) { return items.get(pos); }
        @Override public long getItemId(int pos) { return pos; }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            final VideoItem video = items.get(position);

            // --- Fila horizontal: thumbnail izquierda + info derecha ---
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(8), dp(12), dp(8));
            row.setBackgroundColor(COLOR_BG);

            // Thumbnail
            int thumbW = dp(120);
            int thumbH = dp(68);
            FrameLayout thumbBox = new FrameLayout(ctx);
            GradientDrawable thumbBg = new GradientDrawable();
            thumbBg.setColor(COLOR_THUMB);
            thumbBg.setCornerRadius(dp(8));
            thumbBox.setBackground(thumbBg);
            if (Build.VERSION.SDK_INT >= 21) thumbBox.setClipToOutline(true);

            ImageView thumb = new ImageView(ctx);
            thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
            thumbBox.addView(thumb, new FrameLayout.LayoutParams(
								 ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            if (video.thumbPath != null) {
                new ThumbLoader(thumb).execute(video.thumbPath);
            }

            // Barra de progreso de reproducción sobre el thumbnail
            if (video.duration > 0 && video.lastPosition > 0) {
                int progressPct = (int) Math.min(100,
												 (video.lastPosition * 100L / video.duration));
                View progressBg = new View(ctx);
                progressBg.setBackgroundColor(0x55000000);
                FrameLayout.LayoutParams pgBgLp = new FrameLayout.LayoutParams(
					ViewGroup.LayoutParams.MATCH_PARENT, dp(3));
                pgBgLp.gravity = Gravity.BOTTOM;
                thumbBox.addView(progressBg, pgBgLp);

                View progressFill = new View(ctx);
                progressFill.setBackgroundColor(COLOR_PROGRESS);
                FrameLayout.LayoutParams pgFillLp = new FrameLayout.LayoutParams(
					(int) (thumbW * progressPct / 100f), dp(3));
                pgFillLp.gravity = Gravity.BOTTOM | Gravity.START;
                thumbBox.addView(progressFill, pgFillLp);
            }

            // Badge de duración
            TextView duration = new TextView(ctx);
            duration.setText(video.getDurationFormatted());
            duration.setTextColor(COLOR_TEXT);
            duration.setTextSize(9);
            duration.setTypeface(null, Typeface.BOLD);
            duration.setPadding(dp(4), dp(2), dp(4), dp(2));
            GradientDrawable dbg = new GradientDrawable();
            dbg.setColor(COLOR_BADGE);
            dbg.setCornerRadius(dp(4));
            duration.setBackground(dbg);
            FrameLayout.LayoutParams dLp = new FrameLayout.LayoutParams(
				ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            dLp.gravity = Gravity.BOTTOM | Gravity.END;
            dLp.setMargins(0, 0, dp(4), dp(6));
            thumbBox.addView(duration, dLp);

            row.addView(thumbBox, new LinearLayout.LayoutParams(thumbW, thumbH));

            // Info
            LinearLayout info = new LinearLayout(ctx);
            info.setOrientation(LinearLayout.VERTICAL);
            info.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(
				0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            infoLp.leftMargin = dp(12);
            info.setPadding(0, 0, 0, 0);

            TextView title = new TextView(ctx);
            title.setText(video.title);
            title.setTextColor(COLOR_TEXT);
            title.setTextSize(13);
            title.setTypeface(null, Typeface.BOLD);
            title.setMaxLines(2);
            title.setEllipsize(TextUtils.TruncateAt.END);
            title.setLineSpacing(dp(2), 1f);
            info.addView(title);

            // Tiempo relativo
            TextView timeAgo = new TextView(ctx);
            timeAgo.setText(formatRelativeTime(video.lastWatched));
            timeAgo.setTextColor(COLOR_TEXT2);
            timeAgo.setTextSize(11);
            LinearLayout.LayoutParams taLp = new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            taLp.topMargin = dp(4);
            info.addView(timeAgo, taLp);

            // Si tiene posición guardada: mostrar "Retomar en X:XX"
            if (video.lastPosition > 0 && video.duration > 0
				&& video.lastPosition < video.duration - 5000) {
                TextView resume = new TextView(ctx);
                resume.setText(Lang.get("resume_prefix") + formatMs(video.lastPosition));
                resume.setTextColor(COLOR_PRIMARY);
                resume.setTextSize(11);
                LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(
					ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rLp.topMargin = dp(3);
                info.addView(resume, rLp);
            }

            row.addView(info, infoLp);

            row.setOnClickListener(new View.OnClickListener() {
					@Override public void onClick(View v) {
						if (video.type == VideoItem.TYPE_SERIES) {
							activity.openSeriesPlayer(video);
						} else {
							activity.openPlayer(video);
						}
					}
				});

            return row;
        }
    }

    private String formatMs(long ms) {
        long s = ms / 1000, m = s / 60, sec = s % 60;
        return String.format("%d:%02d", m, sec);
    }

    // -------------------------------------------------------------------------
    // Thumb loader
    // -------------------------------------------------------------------------

    static class ThumbLoader extends AsyncTask<String, Void, Bitmap> {
        private final WeakReference<ImageView> ref;
        ThumbLoader(ImageView iv) { ref = new WeakReference<ImageView>(iv); }

        @Override
        protected Bitmap doInBackground(String... paths) {
            try {
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inSampleSize = 2;
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


