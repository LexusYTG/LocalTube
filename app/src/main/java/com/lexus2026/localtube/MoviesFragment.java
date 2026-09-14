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

public class MoviesFragment {

    private static final int COLOR_BG      = 0xFF0A0A0A;
    private static final int COLOR_CARD    = 0xFF141414;
    private static final int COLOR_THUMB   = 0xFF232323;
    private static final int COLOR_TEXT    = 0xFFFFFFFF;
    private static final int COLOR_BADGE   = 0xE6000000;

    private final MainActivity activity;
    private final VideoIndex index;
    private final GridView gridView;

    public MoviesFragment(MainActivity activity, VideoIndex index) {
        this.activity = activity;
        this.index    = index;

        gridView = new GridView(activity);
        gridView.setLayoutParams(new ViewGroup.LayoutParams(
									 ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        gridView.setBackgroundColor(COLOR_BG);
        gridView.setNumColumns(3);
        gridView.setHorizontalSpacing(dp(6));
        gridView.setVerticalSpacing(dp(6));
        gridView.setPadding(dp(8), dp(8), dp(8), dp(8));
        gridView.setVerticalScrollBarEnabled(false);

        buildContent();
    }

    public View getView() { return gridView; }

    // Umbral: 1 hora en milisegundos
    private static final long MIN_DURATION_MS = 60L * 60L * 1000L;

    private void buildContent() {
        List<VideoItem> all = index.getAll();
        List<VideoItem> longVideos = new java.util.ArrayList<VideoItem>();
        for (VideoItem v : all) {
            if (v.duration >= MIN_DURATION_MS) longVideos.add(v);
        }
        MoviesGridAdapter adapter = new MoviesGridAdapter(activity, longVideos);
        gridView.setAdapter(adapter);
    }

    private int dp(int v) {
        return (int) (v * activity.getResources().getDisplayMetrics().density + 0.5f);
    }

    private class MoviesGridAdapter extends BaseAdapter {
        private final List<VideoItem> items;
        private final Context ctx;

        MoviesGridAdapter(Context ctx, List<VideoItem> items) {
            this.ctx   = ctx;
            this.items = items;
        }

        @Override public int getCount() { return items.size(); }
        @Override public Object getItem(int pos) { return items.get(pos); }
        @Override public long getItemId(int pos) { return pos; }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            final VideoItem item = items.get(position);

            LinearLayout card = new LinearLayout(ctx);
            card.setOrientation(LinearLayout.VERTICAL);
            GradientDrawable cardBg = new GradientDrawable();
            cardBg.setColor(COLOR_CARD);
            cardBg.setCornerRadius(dp(16));
            card.setBackground(cardBg);
            if (Build.VERSION.SDK_INT >= 21) card.setClipToOutline(true);

            int cardW = (parent.getWidth() - dp(28)) / 3;
            int cardH = (int) (cardW * 1.45f);

            FrameLayout thumbBox = new FrameLayout(ctx);
            LinearLayout.LayoutParams tbLp = new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, cardH);
            card.addView(thumbBox, tbLp);

            ImageView thumb = new ImageView(ctx);
            thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
            thumb.setBackgroundColor(COLOR_THUMB);
            thumbBox.addView(thumb, new FrameLayout.LayoutParams(
								 ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            if (item.thumbPath != null) new ThumbLoader(thumb).execute(item.thumbPath);

            if (item.duration > 0) {
                TextView badge = new TextView(ctx);
                badge.setText(item.getDurationFormatted());
                badge.setTextColor(COLOR_TEXT);
                badge.setTextSize(9);
                badge.setTypeface(null, Typeface.BOLD);
                badge.setPadding(dp(6), dp(3), dp(6), dp(3));
                GradientDrawable bbg = new GradientDrawable();
                bbg.setColor(COLOR_BADGE);
                bbg.setCornerRadius(dp(6));
                badge.setBackground(bbg);
                FrameLayout.LayoutParams bLp = new FrameLayout.LayoutParams(
					ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                bLp.gravity = Gravity.BOTTOM | Gravity.END;
                bLp.setMargins(0, 0, dp(6), dp(6));
                thumbBox.addView(badge, bLp);
            }

            TextView title = new TextView(ctx);
            title.setText(item.title);
            title.setTextColor(COLOR_TEXT);
            title.setTextSize(11);
            title.setTypeface(null, Typeface.BOLD);
            title.setMaxLines(2);
            title.setEllipsize(TextUtils.TruncateAt.END);
            title.setLineSpacing(dp(2), 1f);
            title.setPadding(dp(8), dp(8), dp(8), dp(10));
            title.setBackgroundColor(COLOR_CARD);
            card.addView(title);

            card.setOnClickListener(new View.OnClickListener() {
					@Override public void onClick(View v) { activity.openPlayer(item); }
				});

            return card;
        }
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
