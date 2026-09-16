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

public class ChannelAdapter extends BaseAdapter {

    
    private static final int VIEW_TYPE_VIDEO  = 0;
    private static final int VIEW_TYPE_SERIES = 1;

    
    private static final int COLOR_BG      = 0xFF0A0A0A;
    private static final int COLOR_CARD    = 0xFF141414;
    private static final int COLOR_THUMB   = 0xFF232323;
    private static final int COLOR_TEXT    = 0xFFFFFFFF;
    private static final int COLOR_TEXT2   = 0xFFA0A0A0;
    private static final int COLOR_PRIMARY = 0xFFFF004D;
    private static final int COLOR_BADGE   = 0xE6000000;  

    

    
    public static class RowItem {
        static final int KIND_VIDEO  = 0;
        static final int KIND_SERIES = 1;

        final int     kind;
        final VideoItem  video;      
        final String  seriesName;    
        final int     episodeCount;  

        
        RowItem(VideoItem video) {
            this.kind         = KIND_VIDEO;
            this.video        = video;
            this.seriesName   = null;
            this.episodeCount = 0;
        }

        
        RowItem(String seriesName, VideoItem firstEpisode, int episodeCount) {
            this.kind         = KIND_SERIES;
            this.video        = firstEpisode;  
            this.seriesName   = seriesName;
            this.episodeCount = episodeCount;
        }
    }

    

    public interface OnItemClickListener {
        void onVideoClick(VideoItem item);
        void onSeriesClick(String seriesName);
        
        void onVideoLongClick(VideoItem item);
    }

    

    private final Context          context;
    private final List<RowItem>    rows    = new ArrayList<RowItem>();
    private       OnItemClickListener listener;

    public ChannelAdapter(Context context) {
        this.context = context;
    }

    public void setListener(OnItemClickListener l) { this.listener = l; }

    
    public void setChannelContent(List<VideoItem> looseVideos,
								  java.util.Collection<GlobalIndex.SeriesEntry> series) {
        rows.clear();

        
        if (series != null) {
            for (GlobalIndex.SeriesEntry si : series) {
                if (si.getCount() > 0) {
                    rows.add(new RowItem(si.getSeriesName(),
                                         si.getFirstEpisode(),
                                         si.getCount()));
                }
            }
        }

        
        if (looseVideos != null) {
            for (VideoItem v : looseVideos) {
                rows.add(new RowItem(v));
            }
        }

        notifyDataSetChanged();
    }

    

    @Override public int getCount()                  { return rows.size(); }
    @Override public Object getItem(int pos)         { return rows.get(pos); }
    @Override public long getItemId(int pos)         { return pos; }
    @Override public int getViewTypeCount()          { return 2; }

    @Override public int getItemViewType(int pos) {
        return rows.get(pos).kind == RowItem.KIND_SERIES
			? VIEW_TYPE_SERIES : VIEW_TYPE_VIDEO;
    }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        final RowItem row = rows.get(position);

        if (row.kind == RowItem.KIND_SERIES) {
            return getSeriesView(position, convertView, parent, row);
        } else {
            return getVideoView(position, convertView, parent, row);
        }
    }

    

    private View getVideoView(final int pos, View convertView,
							  ViewGroup parent, final RowItem row) {
        VideoHolder h;
        if (convertView == null || !(convertView.getTag() instanceof VideoHolder)) {
            h = new VideoHolder(context);
            convertView = h.wrapper;
            convertView.setTag(h);
        } else {
            h = (VideoHolder) convertView.getTag();
        }
        h.bind(row.video);
        convertView.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) {
					if (listener != null) listener.onVideoClick(row.video);
				}
			});
        convertView.setOnLongClickListener(new View.OnLongClickListener() {
				@Override public boolean onLongClick(View v) {
					if (listener != null) listener.onVideoLongClick(row.video);
					return true;
				}
			});
        return convertView;
    }

    

    private View getSeriesView(final int pos, View convertView,
							   ViewGroup parent, final RowItem row) {
        SeriesHolder h;
        if (convertView == null || !(convertView.getTag() instanceof SeriesHolder)) {
            h = new SeriesHolder(context);
            convertView = h.wrapper;
            convertView.setTag(h);
        } else {
            h = (SeriesHolder) convertView.getTag();
        }
        h.bind(row);
        convertView.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) {
					if (listener != null) listener.onSeriesClick(row.seriesName);
				}
			});
        return convertView;
    }

    
    
    

    private static class VideoHolder {
        final Context      ctx;
        final LinearLayout wrapper;
        final ImageView    thumb;
        final TextView     duration;
        final TextView     title;
        final TextView     meta;
        final View         progressBar;

        VideoHolder(Context ctx) {
            this.ctx = ctx;
            int D = 0; 

            wrapper = new LinearLayout(ctx);
            wrapper.setOrientation(LinearLayout.VERTICAL);
            wrapper.setPadding(dp(ctx,12), dp(ctx,5), dp(ctx,12), dp(ctx,5));
            wrapper.setBackgroundColor(COLOR_BG);

            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(dp(ctx,10), dp(ctx,10), dp(ctx,10), dp(ctx,10));
            GradientDrawable rowBg = new GradientDrawable();
            rowBg.setColor(COLOR_CARD);
            rowBg.setCornerRadius(dp(ctx,16));
            row.setBackground(rowBg);
            wrapper.addView(row, new LinearLayout.LayoutParams(
								ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            
            FrameLayout thumbBox = new FrameLayout(ctx);
            int tw = dp(ctx,152), th = dp(ctx,86);
            row.addView(thumbBox, new LinearLayout.LayoutParams(tw, th));

            thumb = new ImageView(ctx);
            thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
            GradientDrawable tbg = new GradientDrawable();
            tbg.setColor(COLOR_THUMB);
            tbg.setCornerRadius(dp(ctx,12));
            thumb.setBackground(tbg);
            if (Build.VERSION.SDK_INT >= 21) thumb.setClipToOutline(true);
            thumbBox.addView(thumb, new FrameLayout.LayoutParams(
								 ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            progressBar = new View(ctx);
            progressBar.setBackgroundColor(COLOR_PRIMARY);
            FrameLayout.LayoutParams pLp = new FrameLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx,4));
            pLp.gravity = Gravity.BOTTOM;
            thumbBox.addView(progressBar, pLp);
            progressBar.setVisibility(View.GONE);

            duration = new TextView(ctx);
            duration.setTextColor(COLOR_TEXT);
            duration.setTextSize(10);
            duration.setTypeface(null, Typeface.BOLD);
            duration.setPadding(dp(ctx,7), dp(ctx,3), dp(ctx,7), dp(ctx,3));
            GradientDrawable dbg = new GradientDrawable();
            dbg.setColor(COLOR_BADGE);
            dbg.setCornerRadius(dp(ctx,8));
            duration.setBackground(dbg);
            FrameLayout.LayoutParams dLp = new FrameLayout.LayoutParams(
				ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            dLp.gravity = Gravity.BOTTOM | Gravity.END;
            dLp.setMargins(0, 0, dp(ctx,7), dp(ctx,7));
            thumbBox.addView(duration, dLp);

            
            LinearLayout textCol = new LinearLayout(ctx);
            textCol.setOrientation(LinearLayout.VERTICAL);
            textCol.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams tcLp = new LinearLayout.LayoutParams(
				0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            tcLp.leftMargin = dp(ctx,12);
            row.addView(textCol, tcLp);

            title = new TextView(ctx);
            title.setTextColor(COLOR_TEXT);
            title.setTextSize(14);
            title.setTypeface(null, Typeface.BOLD);
            title.setMaxLines(2);
            title.setEllipsize(TextUtils.TruncateAt.END);
            title.setLineSpacing(dp(ctx,2), 1f);
            textCol.addView(title);

            meta = new TextView(ctx);
            meta.setTextColor(COLOR_TEXT2);
            meta.setTextSize(12);
            meta.setMaxLines(1);
            meta.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams mLp = new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            mLp.topMargin = dp(ctx,5);
            textCol.addView(meta, mLp);
        }

        void bind(VideoItem item) {
            if (item == null) return;
            title.setText(item.title);
            duration.setText(item.getDurationFormatted());
            duration.setVisibility(item.duration > 0 ? View.VISIBLE : View.GONE);

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
                    progressBar.setScaleX(pct);
                    progressBar.setVisibility(View.VISIBLE);
                } else { progressBar.setVisibility(View.GONE); }
            } else { progressBar.setVisibility(View.GONE); }

            thumb.setImageDrawable(null);
            if (item.thumbPath != null) new ThumbLoader(thumb).execute(item.thumbPath);
        }
    }

    
    
    

    private static class SeriesHolder {
        final Context      ctx;
        final LinearLayout wrapper;
        final ImageView    thumb;
        final TextView     seriesTitle;
        final TextView     episodeBadge; 

        SeriesHolder(Context ctx) {
            this.ctx = ctx;

            wrapper = new LinearLayout(ctx);
            wrapper.setOrientation(LinearLayout.VERTICAL);
            wrapper.setPadding(dp(ctx,12), dp(ctx,5), dp(ctx,12), dp(ctx,5));
            wrapper.setBackgroundColor(COLOR_BG);

            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(dp(ctx,10), dp(ctx,10), dp(ctx,10), dp(ctx,10));
            GradientDrawable rowBg = new GradientDrawable();
            rowBg.setColor(COLOR_CARD);
            rowBg.setCornerRadius(dp(ctx,16));
            row.setBackground(rowBg);
            wrapper.addView(row, new LinearLayout.LayoutParams(
								ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            
            FrameLayout thumbBox = new FrameLayout(ctx);
            int tw = dp(ctx,152), th = dp(ctx,86);
            row.addView(thumbBox, new LinearLayout.LayoutParams(tw, th));

            thumb = new ImageView(ctx);
            thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
            GradientDrawable tbg = new GradientDrawable();
            tbg.setColor(COLOR_THUMB);
            tbg.setCornerRadius(dp(ctx,12));
            thumb.setBackground(tbg);
            if (Build.VERSION.SDK_INT >= 21) thumb.setClipToOutline(true);
            thumbBox.addView(thumb, new FrameLayout.LayoutParams(
								 ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            
            episodeBadge = new TextView(ctx);
            episodeBadge.setTextColor(COLOR_TEXT);
            episodeBadge.setTextSize(10);
            episodeBadge.setTypeface(null, Typeface.BOLD);
            episodeBadge.setPadding(dp(ctx,7), dp(ctx,3), dp(ctx,7), dp(ctx,3));
            GradientDrawable ebg = new GradientDrawable();
            ebg.setColor(COLOR_BADGE);
            ebg.setCornerRadius(dp(ctx,8));
            episodeBadge.setBackground(ebg);
            FrameLayout.LayoutParams eLp = new FrameLayout.LayoutParams(
				ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            eLp.gravity = Gravity.BOTTOM | Gravity.END;
            eLp.setMargins(0, 0, dp(ctx,7), dp(ctx,7));
            thumbBox.addView(episodeBadge, eLp);

            
            LinearLayout textCol = new LinearLayout(ctx);
            textCol.setOrientation(LinearLayout.VERTICAL);
            textCol.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams tcLp = new LinearLayout.LayoutParams(
				0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            tcLp.leftMargin = dp(ctx,12);
            row.addView(textCol, tcLp);

            
            TextView seriesLabel = new TextView(ctx);
            seriesLabel.setText("SERIE");
            seriesLabel.setTextColor(COLOR_PRIMARY);
            seriesLabel.setTextSize(9);
            seriesLabel.setTypeface(null, Typeface.BOLD);
            seriesLabel.setPadding(dp(ctx,6), dp(ctx,2), dp(ctx,6), dp(ctx,2));
            GradientDrawable slBg = new GradientDrawable();
            slBg.setColor(0x22FF004D);  
            slBg.setCornerRadius(dp(ctx,6));
            seriesLabel.setBackground(slBg);
            textCol.addView(seriesLabel);

            seriesTitle = new TextView(ctx);
            seriesTitle.setTextColor(COLOR_TEXT);
            seriesTitle.setTextSize(14);
            seriesTitle.setTypeface(null, Typeface.BOLD);
            seriesTitle.setMaxLines(2);
            seriesTitle.setEllipsize(TextUtils.TruncateAt.END);
            seriesTitle.setLineSpacing(dp(ctx,2), 1f);
            LinearLayout.LayoutParams stLp = new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            stLp.topMargin = dp(ctx,4);
            textCol.addView(seriesTitle, stLp);
        }

        void bind(RowItem row) {
            seriesTitle.setText(row.seriesName);

            int n = row.episodeCount;
            episodeBadge.setText(n + (n == 1 ? " cap." : " caps."));

            thumb.setImageDrawable(null);
            if (row.video != null && row.video.thumbPath != null) {
                new ThumbLoader(thumb).execute(row.video.thumbPath);
            }
        }
    }

    
    
    

    static class ThumbLoader extends AsyncTask<String, Void, Bitmap> {
        private final WeakReference<ImageView> ref;
        ThumbLoader(ImageView iv) { ref = new WeakReference<ImageView>(iv); }

        @Override
        protected Bitmap doInBackground(String... paths) {
            try { return BitmapFactory.decodeFile(paths[0]); }
            catch (Exception e) { return null; }
        }

        @Override
        protected void onPostExecute(Bitmap bmp) {
            ImageView iv = ref.get();
            if (iv != null && bmp != null) iv.setImageBitmap(bmp);
        }
    }

    private static int dp(Context ctx, int v) {
        return (int) (v * ctx.getResources().getDisplayMetrics().density + 0.5f);
    }
}
