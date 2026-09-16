/*
 * This file is part of LocalTube.
 *
 * LocalTube is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LocalTube is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LocalTube. If not, see <https://www.gnu.org/licenses/>.
 */

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

public class SeriesFragment {

    private static final int COLOR_BG      = 0xFF0A0A0A;
    private static final int COLOR_CARD    = 0xFF141414;
    private static final int COLOR_THUMB   = 0xFF232323;
    private static final int COLOR_TEXT    = 0xFFFFFFFF;
    private static final int COLOR_TEXT2   = 0xFFA0A0A0;
    private static final int COLOR_BADGE   = 0xE6000000;

    private final MainActivity activity;
    private final GlobalIndex index;
    private final GridView gridView;

    public SeriesFragment(MainActivity activity, GlobalIndex index) {
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

    private void buildContent() {
        final List<String> seriesNames = index.getSeriesNames();
        if (seriesNames.isEmpty()) {
            TextView empty = new TextView(activity);
            empty.setText(Lang.get("series_empty"));
            empty.setTextColor(COLOR_TEXT2);
            empty.setTextSize(14);
            empty.setGravity(Gravity.CENTER);
            empty.setLineSpacing(dp(6), 1f);
            LinearLayout wrap = new LinearLayout(activity);
            wrap.setGravity(Gravity.CENTER);
            wrap.addView(empty);
            gridView.setEmptyView(wrap);
            return;
        }

        SeriesGridAdapter adapter = new SeriesGridAdapter(activity, seriesNames);
        gridView.setAdapter(adapter);
    }

    private int dp(int v) {
        return (int) (v * activity.getResources().getDisplayMetrics().density + 0.5f);
    }

    private class SeriesGridAdapter extends BaseAdapter {
        private final List<String> names;
        private final Context ctx;

        SeriesGridAdapter(Context ctx, List<String> names) {
            this.ctx   = ctx;
            this.names = names;
        }

        @Override public int getCount() { return names.size(); }
        @Override public Object getItem(int pos) { return names.get(pos); }
        @Override public long getItemId(int pos) { return pos; }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            final String name = names.get(position);

            LinearLayout card = new LinearLayout(ctx);
            card.setOrientation(LinearLayout.VERTICAL);
            GradientDrawable cardBg = new GradientDrawable();
            cardBg.setColor(COLOR_CARD);
            cardBg.setCornerRadius(dp(16));
            card.setBackground(cardBg);
            if (Build.VERSION.SDK_INT >= 21) card.setClipToOutline(true);

            int cardW = (parent.getWidth() - dp(28)) / 3;
            int cardH = (int) (cardW * 1.45f);

            List<VideoItem> eps = index.getEpisodesOf(name);
            VideoItem first = eps.isEmpty() ? null : eps.get(0);

            FrameLayout thumbBox = new FrameLayout(ctx);
            LinearLayout.LayoutParams tbLp = new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, cardH);
            card.addView(thumbBox, tbLp);

            ImageView thumb = new ImageView(ctx);
            thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
            thumb.setBackgroundColor(COLOR_THUMB);
            thumbBox.addView(thumb, new FrameLayout.LayoutParams(
								 ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            if (first != null && first.thumbPath != null) {
                new ThumbLoader(thumb).execute(first.thumbPath);
            }

            TextView badge = new TextView(ctx);
            badge.setText(eps.size() + " " + Lang.get("cap_abbr"));
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

            TextView title = new TextView(ctx);
            title.setText(name);
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
					@Override public void onClick(View v) {
						List<VideoItem> eps2 = index.getEpisodesOf(name);
						if (!eps2.isEmpty()) activity.openSeriesPlayer(eps2.get(0));
					}
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
