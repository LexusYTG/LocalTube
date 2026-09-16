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

public class VideoAdapter extends BaseAdapter {

    private static final int COLOR_BG     = 0xFF0A0A0A;
    private static final int COLOR_CARD   = 0xFF141414;
    private static final int COLOR_THUMB  = 0xFF232323;
    private static final int COLOR_TEXT   = 0xFFFFFFFF;
    private static final int COLOR_TEXT2  = 0xFFA0A0A0;
    private static final int COLOR_PRIMARY= 0xFFFF004D;
    private static final int COLOR_BADGE  = 0xE6000000;

    private List<VideoItem> items = new ArrayList<VideoItem>();
    private final Context context;
    private OnVideoClickListener listener;

    public interface OnVideoClickListener {
        void onVideoClick(VideoItem item, int position);
    }

    public VideoAdapter(Context context) { this.context = context; }

    public void setListener(OnVideoClickListener l) { this.listener = l; }

    public void setItems(List<VideoItem> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    @Override public int getCount()          { return items.size(); }
    @Override public Object getItem(int pos) { return items.get(pos); }
    @Override public long getItemId(int pos) { return pos; }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        Holder h;
        if (convertView == null || !(convertView.getTag() instanceof Holder)) {
            h = new Holder(context);
            convertView = h.wrapper;
            convertView.setTag(h);
        } else {
            h = (Holder) convertView.getTag();
        }
        final VideoItem item = items.get(position);
        h.bind(item);
        convertView.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) {
					if (listener != null) listener.onVideoClick(item, position);
				}
			});
        return convertView;
    }

    private static class Holder {
        final Context ctx;
        final LinearLayout wrapper;
        final ImageView thumb;
        final TextView duration;
        final TextView title;
        final TextView channelName;
        final TextView meta;
        final View progress;

        Holder(Context ctx) {
            this.ctx = ctx;
            wrapper = new LinearLayout(ctx);
            wrapper.setOrientation(LinearLayout.VERTICAL);
            wrapper.setPadding(dp(ctx, 12), dp(ctx, 5), dp(ctx, 12), dp(ctx, 5));
            wrapper.setBackgroundColor(COLOR_BG);

            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(dp(ctx, 10), dp(ctx, 10), dp(ctx, 10), dp(ctx, 10));
            GradientDrawable rowBg = new GradientDrawable();
            rowBg.setColor(COLOR_CARD);
            rowBg.setCornerRadius(dp(ctx, 16));
            row.setBackground(rowBg);
            wrapper.addView(row, new LinearLayout.LayoutParams(
								ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            FrameLayout thumbBox = new FrameLayout(ctx);
            int tw = dp(ctx, 152);
            int th = dp(ctx, 86);
            LinearLayout.LayoutParams tbLp = new LinearLayout.LayoutParams(tw, th);
            row.addView(thumbBox, tbLp);

            thumb = new ImageView(ctx);
            thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
            GradientDrawable tbg = new GradientDrawable();
            tbg.setColor(COLOR_THUMB);
            tbg.setCornerRadius(dp(ctx, 12));
            thumb.setBackground(tbg);
            if (Build.VERSION.SDK_INT >= 21) thumb.setClipToOutline(true);
            thumbBox.addView(thumb, new FrameLayout.LayoutParams(
								 ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            progress = new View(ctx);
            progress.setBackgroundColor(COLOR_PRIMARY);
            FrameLayout.LayoutParams pLp = new FrameLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, 4));
            pLp.gravity = Gravity.BOTTOM;
            thumbBox.addView(progress, pLp);
            progress.setVisibility(View.GONE);

            duration = new TextView(ctx);
            duration.setTextColor(COLOR_TEXT);
            duration.setTextSize(10);
            duration.setTypeface(null, Typeface.BOLD);
            duration.setPadding(dp(ctx, 7), dp(ctx, 3), dp(ctx, 7), dp(ctx, 3));
            GradientDrawable dbg = new GradientDrawable();
            dbg.setColor(COLOR_BADGE);
            dbg.setCornerRadius(dp(ctx, 8));
            duration.setBackground(dbg);
            FrameLayout.LayoutParams dLp = new FrameLayout.LayoutParams(
				ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            dLp.gravity = Gravity.BOTTOM | Gravity.END;
            dLp.setMargins(0, 0, dp(ctx, 7), dp(ctx, 7));
            thumbBox.addView(duration, dLp);

            LinearLayout textCol = new LinearLayout(ctx);
            textCol.setOrientation(LinearLayout.VERTICAL);
            textCol.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams tcLp = new LinearLayout.LayoutParams(
				0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            tcLp.leftMargin = dp(ctx, 12);
            row.addView(textCol, tcLp);

            title = new TextView(ctx);
            title.setTextColor(COLOR_TEXT);
            title.setTextSize(14);
            title.setTypeface(null, Typeface.BOLD);
            title.setMaxLines(2);
            title.setEllipsize(TextUtils.TruncateAt.END);
            title.setLineSpacing(dp(ctx, 2), 1f);
            textCol.addView(title);

            channelName = new TextView(ctx);
            channelName.setTextColor(COLOR_TEXT2);
            channelName.setTextSize(12);
            channelName.setTypeface(null, Typeface.BOLD);
            channelName.setMaxLines(1);
            channelName.setEllipsize(TextUtils.TruncateAt.END);
            channelName.setPadding(0, dp(ctx, 3), 0, 0);
            textCol.addView(channelName);

            meta = new TextView(ctx);
            meta.setTextColor(COLOR_TEXT2);
            meta.setTextSize(12);
            meta.setMaxLines(1);
            meta.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams mLp = new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            mLp.topMargin = dp(ctx, 5);
            textCol.addView(meta, mLp);
        }

        void bind(final VideoItem item) {
            title.setText(item.title);
            duration.setText(item.getDurationFormatted());
            duration.setVisibility(item.duration > 0 ? View.VISIBLE : View.GONE);

            if (item.category != null && !item.category.isEmpty()) {
                channelName.setText(item.category);
                channelName.setVisibility(View.VISIBLE);
                channelName.setOnClickListener(new View.OnClickListener() {
						@Override public void onClick(View v) {
							if (item.channelId == null || item.channelId.isEmpty()) return;
							Intent i = new Intent(ctx, ChannelActivity.class);
							i.putExtra(ChannelActivity.EXTRA_CHANNEL_ID, item.channelId);
							if (!(ctx instanceof android.app.Activity)) {
								i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
							}
							ctx.startActivity(i);
						}
					});
            } else {
                channelName.setVisibility(View.GONE);
                channelName.setOnClickListener(null);
            }

            StringBuilder sb = new StringBuilder();
            String res = item.getResolution();
            if (!res.isEmpty()) sb.append(res);
            if (item.fileSize > 0) {
                if (sb.length() > 0) sb.append("  \u00B7  ");
                sb.append(item.getFileSizeFormatted());
            }
            meta.setText(sb.toString());

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

            thumb.setImageDrawable(null);
            if (item.thumbPath != null) new ThumbLoader(thumb).execute(item.thumbPath);
        }

        private static int dp(Context ctx, int v) {
            return (int) (v * ctx.getResources().getDisplayMetrics().density + 0.5f);
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
