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

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.*;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.lang.ref.*;
import java.util.*;

public class ChannelActivity extends Activity {

    public static final String EXTRA_CHANNEL_ID = "extra_channel_id";

    private static final int TAB_VIDEOS = 0;
    private static final int TAB_SHORTS = 1;
    private static final int TAB_SERIES = 2;

    private static final int COLOR_BG      = 0xFF0A0A0A;
    private static final int COLOR_TEXT    = 0xFFFFFFFF;
    private static final int COLOR_TEXT2   = 0xFFA0A0A0;
    private static final int COLOR_TEXT3   = 0xFF5C5C5C;
    private static final int COLOR_AVBG    = 0xFF232323;
    private static final int COLOR_PRIMARY = 0xFFFF004D;
    private static final int COLOR_BORDER  = 0xFF262626;

    
    private GlobalIndex index;
    private GlobalIndex.ChannelEntry channelIndex;
    private String channelId;
    private String channelDisplayName;

    
    private String expandedSeriesName = null;
    private int currentTab = TAB_VIDEOS;

    
    private ImageView  avatar;
    private TextView   nameLabel;
    private TextView   countLabel;
    private TextView   topBarTitle;
    private ListView   listView;
    private View       tabBarWrapper;

    private FrameLayout tabVideosBox, tabShortsBox, tabSeriesBox;
    private TextView    tabVideosLbl, tabShortsLbl, tabSeriesLbl;
    private View        tabVideosInd, tabShortsInd, tabSeriesInd;

    
    private ChannelAdapter channelAdapter;
    private EpisodeAdapter episodeAdapter;

    

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        channelId = getIntent().getStringExtra(EXTRA_CHANNEL_ID);

        index = new GlobalIndex(this);
        String root = AppPrefs.get(this).getRootPath();
        if (root != null) { index.setRootPath(root); index.loadFromDisk(); }

        channelIndex = channelId != null ? index.getChannel(channelId) : null;

        ChannelData cd = channelIndex != null ? channelIndex.getChannelData() : null;
        channelDisplayName = (cd != null && cd.displayName != null && !cd.displayName.isEmpty())
            ? cd.displayName : channelId;
        String photoPath = cd != null ? cd.photoPath : null;

        int itemCount = countChannelItems();

        buildUi(channelDisplayName, photoPath, itemCount);
        initChannelMode();
    }

    @Override
    public void onBackPressed() {
        if (expandedSeriesName != null) {
            exitSeriesMode();
        } else {
            super.onBackPressed();
        }
    }

    

    private void initChannelMode() {
        expandedSeriesName = null;
        topBarTitle.setText("Canal");
        if (tabBarWrapper != null) tabBarWrapper.setVisibility(View.VISIBLE);

        if (channelAdapter == null) {
            channelAdapter = new ChannelAdapter(this);
            channelAdapter.setListener(new ChannelAdapter.OnItemClickListener() {
					@Override public void onVideoClick(VideoItem item) {
						openPlayer(item);
					}
					@Override public void onSeriesClick(String seriesName) {
						enterSeriesMode(seriesName);
					}
					@Override public void onVideoLongClick(VideoItem item) {
						showVideoOptionsMenu(item, false);
					}
				});
        }

        listView.setAdapter(channelAdapter);
        refreshTabContent();
        updateTabStyles();
    }

    
    private void refreshTabContent() {
        if (channelAdapter == null) return;

        List<VideoItem> videos = new ArrayList<VideoItem>();
        Collection<GlobalIndex.SeriesEntry> series = new ArrayList<GlobalIndex.SeriesEntry>();

        if (channelIndex != null) {
            if (currentTab == TAB_VIDEOS) {
                videos = channelIndex.getVideos();
            } else if (currentTab == TAB_SHORTS) {
                videos = channelIndex.getShorts();
            } else { 
                series = channelIndex.getAllSeries();
            }
        }

        channelAdapter.setChannelContent(videos, series);
        updateCountLabelForTab();
    }

    private void switchTab(int tab) {
        if (currentTab == tab) return;
        currentTab = tab;
        updateTabStyles();
        refreshTabContent();
    }

    private void enterSeriesMode(String seriesName) {
        expandedSeriesName = seriesName;
        topBarTitle.setText(seriesName);
        if (tabBarWrapper != null) tabBarWrapper.setVisibility(View.GONE);

        List<VideoItem> episodes = channelIndex != null
            ? channelIndex.getEpisodesOf(seriesName) : new ArrayList<VideoItem>();

        episodeAdapter = new EpisodeAdapter(this, episodes);
        episodeAdapter.setListener(new EpisodeAdapter.OnEpisodeClickListener() {
				@Override public void onEpisodeClick(VideoItem item) {
					openPlayer(item);
				}
				@Override public void onEpisodeLongClick(VideoItem item) {
					showVideoOptionsMenu(item, true);
				}
			});
        listView.setAdapter(episodeAdapter);

        updateHeader(seriesName, episodes.size());
        countLabel.setText(episodes.size() + (episodes.size() == 1 ? " capítulo" : " capítulos"));
    }

    private void exitSeriesMode() {
        initChannelMode();
    }

    

    private void openPlayer(VideoItem item) {
        Class<?> target = item.type == VideoItem.TYPE_SERIES
            ? SeriesPlayerActivity.class : PlayerActivity.class;
        String extra = item.type == VideoItem.TYPE_SERIES
            ? SeriesPlayerActivity.EXTRA_VIDEO : PlayerActivity.EXTRA_VIDEO;
        Intent i = new Intent(this, target);
        i.putExtra(extra, item);
        startActivity(i);
    }

    

    private void showVideoOptionsMenu(final VideoItem item, boolean isSeriesEpisode) {
        if (item == null) return;

        final List<String> options = new ArrayList<String>();
        if (isSeriesEpisode) options.add("Capítulo");
        options.add("Nombre");
        options.add("Tags");

        new AlertDialog.Builder(this)
            .setTitle(item.title)
            .setItems(options.toArray(new String[0]), new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface dialog, int which) {
                    String choice = options.get(which);
                    if ("Capítulo".equals(choice))      showChapterDialog(item);
                    else if ("Nombre".equals(choice))   showRenameDialog(item);
                    else if ("Tags".equals(choice))     showTagsDialog(item);
                }
            })
            .setNegativeButton("Cancelar", null)
            .show();
    }

    private void showChapterDialog(final VideoItem item) {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText(item.episode >= 0 ? String.valueOf(item.episode) : "");
        input.setSelection(input.getText().length());

        new AlertDialog.Builder(this)
            .setTitle("Cambiar capítulo")
            .setMessage(item.title)
            .setView(input)
            .setPositiveButton("Guardar", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface dialog, int which) {
                    String txt = input.getText().toString().trim();
                    int newEp;
                    try { newEp = Integer.parseInt(txt); }
                    catch (NumberFormatException e) {
                        Toast.makeText(ChannelActivity.this, "Número inválido", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (channelIndex.setEpisodeNumber(item, newEp)) {
                        refreshCurrentView();
                    } else {
                        Toast.makeText(ChannelActivity.this, "No se pudo cambiar el capítulo", Toast.LENGTH_SHORT).show();
                    }
                }
            })
            .setNegativeButton("Cancelar", null)
            .show();
    }

    private void showRenameDialog(final VideoItem item) {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(item.title != null ? item.title : "");
        input.setSelection(input.getText().length());

        new AlertDialog.Builder(this)
            .setTitle("Renombrar")
            .setView(input)
            .setPositiveButton("Guardar", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface dialog, int which) {
                    String newName = input.getText().toString().trim();
                    if (newName.isEmpty()) return;
                    if (channelIndex.renameVideo(item, newName)) {
                        refreshCurrentView();
                    } else {
                        Toast.makeText(ChannelActivity.this,
                                       "No se pudo renombrar (¿ya existe ese nombre?)", Toast.LENGTH_SHORT).show();
                    }
                }
            })
            .setNegativeButton("Cancelar", null)
            .show();
    }

    private void showTagsDialog(final VideoItem item) {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint("tag1, tag2, tag3…");
        input.setText(item.getTagsString());
        input.setSelection(input.getText().length());

        new AlertDialog.Builder(this)
            .setTitle("Tags")
            .setMessage(item.title)
            .setView(input)
            .setPositiveButton("Guardar", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface dialog, int which) {
                    List<String> tags = VideoItem.parseTagsString(input.getText().toString());
                    channelIndex.setTags(item, tags);
                    refreshCurrentView();
                }
            })
            .setNegativeButton("Cancelar", null)
            .show();
    }

    private void refreshCurrentView() {
        if (expandedSeriesName != null) {
            enterSeriesMode(expandedSeriesName);
        } else {
            initChannelMode();
        }
    }

    

    private int countChannelItems() {
        if (channelIndex == null) return 0;
        return channelIndex.getVideos().size()
			+ channelIndex.getShorts().size()
			+ channelIndex.getAllSeries().size();
    }

    private void updateHeader(String name, int count) {
        nameLabel.setText(name != null ? name : "");
    }

    private void updateCountLabelForTab() {
        if (countLabel == null) return;
        int n = 0;
        String suffix;
        if (currentTab == TAB_VIDEOS) {
            n = channelIndex != null ? channelIndex.getVideos().size() : 0;
            suffix = (n == 1) ? " video" : " videos";
        } else if (currentTab == TAB_SHORTS) {
            n = channelIndex != null ? channelIndex.getShorts().size() : 0;
            suffix = (n == 1) ? " short" : " shorts";
        } else {
            n = channelIndex != null ? channelIndex.getAllSeries().size() : 0;
            suffix = (n == 1) ? " serie" : " series";
        }
        countLabel.setText(n + suffix);
    }

    

    private void buildUi(String displayName, String photoPath, int count) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(COLOR_BG);
        setContentView(root);

        root.addView(buildTopBar());
        root.addView(buildHeader(displayName, photoPath, count));

        tabBarWrapper = buildTabBar();
        root.addView(tabBarWrapper, new LinearLayout.LayoutParams(
						 ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        listView = new ListView(this);
        listView.setDivider(null);
        listView.setDividerHeight(0);
        root.addView(listView, new LinearLayout.LayoutParams(
						 ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
    }

    private View buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(4), dp(10), dp(16), dp(10));

        ImageButton back = new ImageButton(this);
        back.setImageResource(android.R.drawable.ic_menu_revert);
        back.setBackgroundColor(Color.TRANSPARENT);
        back.setColorFilter(COLOR_TEXT);
        bar.addView(back, new LinearLayout.LayoutParams(dp(44), dp(44)));
        back.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) { onBackPressed(); }
			});

        topBarTitle = new TextView(this);
        topBarTitle.setText("Canal");
        topBarTitle.setTextColor(COLOR_TEXT);
        topBarTitle.setTextSize(17);
        topBarTitle.setTypeface(null, Typeface.BOLD);
        topBarTitle.setPadding(dp(8), 0, 0, 0);
        bar.addView(topBarTitle);

        return bar;
    }

    private View buildHeader(String displayName, String photoPath, int count) {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(20), dp(8), dp(20), dp(20));

        avatar = new ImageView(this);
        avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        GradientDrawable avBg = new GradientDrawable();
        avBg.setShape(GradientDrawable.OVAL);
        avBg.setColor(COLOR_AVBG);
        avatar.setBackground(avBg);
        int avSize = dp(64);
        header.addView(avatar, new LinearLayout.LayoutParams(avSize, avSize));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tLp.leftMargin = dp(16);
        header.addView(texts, tLp);

        nameLabel = new TextView(this);
        nameLabel.setText(displayName != null ? displayName : "");
        nameLabel.setTextColor(COLOR_TEXT);
        nameLabel.setTextSize(19);
        nameLabel.setTypeface(null, Typeface.BOLD);
        texts.addView(nameLabel);

        countLabel = new TextView(this);
        countLabel.setTextColor(COLOR_TEXT2);
        countLabel.setTextSize(13);
        countLabel.setPadding(0, dp(4), 0, 0);
        countLabel.setText(count + (count == 1 ? " elemento" : " elementos"));
        texts.addView(countLabel);

        if (photoPath != null && !photoPath.isEmpty()) {
            new AvatarLoader(avatar, avSize).execute(photoPath);
        }

        return header;
    }

    

    private View buildTabBar() {
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setBackgroundColor(COLOR_BG);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding(dp(8), 0, dp(8), 0);

        tabVideosBox = createTab("Videos", TAB_VIDEOS);
        tabShortsBox = createTab("Shorts", TAB_SHORTS);
        tabSeriesBox = createTab("Series", TAB_SERIES);

        tabVideosLbl = (TextView) tabVideosBox.getChildAt(0);
        tabVideosInd = tabVideosBox.getChildAt(1);
        tabShortsLbl = (TextView) tabShortsBox.getChildAt(0);
        tabShortsInd = tabShortsBox.getChildAt(1);
        tabSeriesLbl = (TextView) tabSeriesBox.getChildAt(0);
        tabSeriesInd = tabSeriesBox.getChildAt(1);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            0, dp(46), 1f);
        bar.addView(tabVideosBox, lp);
        bar.addView(tabShortsBox, lp);
        bar.addView(tabSeriesBox, lp);

        wrapper.addView(bar);

        View divider = new View(this);
        divider.setBackgroundColor(COLOR_BORDER);
        wrapper.addView(divider, new LinearLayout.LayoutParams(
							ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));

        return wrapper;
    }

    private FrameLayout createTab(String label, final int tabIndex) {
        FrameLayout tab = new FrameLayout(this);
        tab.setClickable(true);
        tab.setFocusable(true);

        TextView txt = new TextView(this);
        txt.setText(label);
        txt.setTextColor(COLOR_TEXT3);
        txt.setTextSize(14);
        txt.setGravity(Gravity.CENTER);
        tab.addView(txt, new FrameLayout.LayoutParams(
						ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        View ind = new View(this);
        GradientDrawable ibg = new GradientDrawable();
        ibg.setColor(COLOR_PRIMARY);
        ibg.setCornerRadius(dp(2));
        ind.setBackground(ibg);
        FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(dp(28), dp(3));
        ilp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        ilp.bottomMargin = dp(4);
        tab.addView(ind, ilp);
        ind.setVisibility(View.INVISIBLE);

        tab.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) { switchTab(tabIndex); }
			});

        return tab;
    }

    private void updateTabStyles() {
        styleTab(tabVideosLbl, tabVideosInd, currentTab == TAB_VIDEOS);
        styleTab(tabShortsLbl, tabShortsInd, currentTab == TAB_SHORTS);
        styleTab(tabSeriesLbl, tabSeriesInd, currentTab == TAB_SERIES);
    }

    private void styleTab(TextView label, View indicator, boolean selected) {
        if (label == null || indicator == null) return;
        label.setTextColor(selected ? COLOR_TEXT : COLOR_TEXT3);
        label.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
        indicator.setVisibility(selected ? View.VISIBLE : View.INVISIBLE);
    }

    

    private static class AvatarLoader extends AsyncTask<String, Void, Bitmap> {
        private final WeakReference<ImageView> ref;
        private final int size;

        AvatarLoader(ImageView iv, int size) {
            this.ref  = new WeakReference<ImageView>(iv);
            this.size = size;
        }

        @Override
        protected Bitmap doInBackground(String... paths) {
            try {
                Bitmap src = BitmapFactory.decodeFile(paths[0]);
                if (src == null) return null;
                return circularBitmap(src, size);
            } catch (Exception e) { return null; }
        }

        @Override
        protected void onPostExecute(Bitmap bmp) {
            ImageView iv = ref.get();
            if (iv != null && bmp != null) iv.setImageBitmap(bmp);
        }

        private static Bitmap circularBitmap(Bitmap src, int size) {
            Bitmap scaled = Bitmap.createScaledBitmap(src, size, size, true);
            Bitmap output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(output);
            Paint  paint  = new Paint(Paint.ANTI_ALIAS_FLAG);
            BitmapShader shader = new BitmapShader(scaled, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            paint.setShader(shader);
            canvas.drawOval(new RectF(0, 0, size, size), paint);
            return output;
        }
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    
    
    

    private static class EpisodeAdapter extends BaseAdapter {

        private static final int COLOR_BG      = 0xFF0A0A0A;
        private static final int COLOR_CARD    = 0xFF141414;
        private static final int COLOR_THUMB   = 0xFF232323;
        private static final int COLOR_TEXT    = 0xFFFFFFFF;
        private static final int COLOR_TEXT2   = 0xFFA0A0A0;
        private static final int COLOR_PRIMARY = 0xFFFF004D;
        private static final int COLOR_BADGE   = 0xE6000000;

        interface OnEpisodeClickListener {
            void onEpisodeClick(VideoItem item);
            void onEpisodeLongClick(VideoItem item);
        }

        private final Context         ctx;
        private final List<VideoItem> episodes;
        private       OnEpisodeClickListener listener;

        EpisodeAdapter(Context ctx, List<VideoItem> episodes) {
            this.ctx      = ctx;
            this.episodes = episodes != null ? episodes : new ArrayList<VideoItem>();
        }

        void setListener(OnEpisodeClickListener l) { this.listener = l; }

        @Override public int    getCount()          { return episodes.size(); }
        @Override public Object getItem(int pos)    { return episodes.get(pos); }
        @Override public long   getItemId(int pos)  { return pos; }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            EpHolder h;
            if (convertView == null || !(convertView.getTag() instanceof EpHolder)) {
                h = new EpHolder(ctx);
                convertView = h.wrapper;
                convertView.setTag(h);
            } else {
                h = (EpHolder) convertView.getTag();
            }
            final VideoItem item = episodes.get(position);
            h.bind(item);
            convertView.setOnClickListener(new View.OnClickListener() {
					@Override public void onClick(View v) {
						if (listener != null) listener.onEpisodeClick(item);
					}
				});
            convertView.setOnLongClickListener(new View.OnLongClickListener() {
					@Override public boolean onLongClick(View v) {
						if (listener != null) listener.onEpisodeLongClick(item);
						return true;
					}
				});
            return convertView;
        }

        private static class EpHolder {
            final Context      ctx;
            final LinearLayout wrapper;
            final ImageView    thumb;
            final TextView     episodeLabel;
            final TextView     title;
            final TextView     meta;
            final View         progressBar;
            final TextView     duration;

            EpHolder(Context ctx) {
                this.ctx = ctx;

                wrapper = new LinearLayout(ctx);
                wrapper.setOrientation(LinearLayout.VERTICAL);
                wrapper.setPadding(dp(12), dp(5), dp(12), dp(5));
                wrapper.setBackgroundColor(COLOR_BG);

                LinearLayout row = new LinearLayout(ctx);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setPadding(dp(10), dp(10), dp(10), dp(10));
                GradientDrawable rowBg = new GradientDrawable();
                rowBg.setColor(COLOR_CARD);
                rowBg.setCornerRadius(dp(16));
                row.setBackground(rowBg);
                wrapper.addView(row, new LinearLayout.LayoutParams(
									ViewGroup.LayoutParams.MATCH_PARENT,
									ViewGroup.LayoutParams.WRAP_CONTENT));

                FrameLayout thumbBox = new FrameLayout(ctx);
                row.addView(thumbBox, new LinearLayout.LayoutParams(dp(152), dp(86)));

                thumb = new ImageView(ctx);
                thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
                GradientDrawable tbg = new GradientDrawable();
                tbg.setColor(COLOR_THUMB);
                tbg.setCornerRadius(dp(12));
                thumb.setBackground(tbg);
                if (android.os.Build.VERSION.SDK_INT >= 21) thumb.setClipToOutline(true);
                thumbBox.addView(thumb, new FrameLayout.LayoutParams(
									 ViewGroup.LayoutParams.MATCH_PARENT,
									 ViewGroup.LayoutParams.MATCH_PARENT));

                duration = new TextView(ctx);
                duration.setTextColor(COLOR_TEXT);
                duration.setTextSize(10);
                duration.setTypeface(null, Typeface.BOLD);
                duration.setPadding(dp(7), dp(3), dp(7), dp(3));
                GradientDrawable dbg = new GradientDrawable();
                dbg.setColor(COLOR_BADGE);
                dbg.setCornerRadius(dp(8));
                duration.setBackground(dbg);
                FrameLayout.LayoutParams dLp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                dLp.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.END;
                dLp.setMargins(0, 0, dp(7), dp(7));
                thumbBox.addView(duration, dLp);

                progressBar = new View(ctx);
                progressBar.setBackgroundColor(COLOR_PRIMARY);
                FrameLayout.LayoutParams pLp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(4));
                pLp.gravity = android.view.Gravity.BOTTOM;
                thumbBox.addView(progressBar, pLp);
                progressBar.setVisibility(View.GONE);

                LinearLayout textCol = new LinearLayout(ctx);
                textCol.setOrientation(LinearLayout.VERTICAL);
                textCol.setGravity(android.view.Gravity.CENTER_VERTICAL);
                LinearLayout.LayoutParams tcLp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                tcLp.leftMargin = dp(12);
                row.addView(textCol, tcLp);

                episodeLabel = new TextView(ctx);
                episodeLabel.setTextColor(COLOR_PRIMARY);
                episodeLabel.setTextSize(11);
                episodeLabel.setTypeface(null, Typeface.BOLD);
                textCol.addView(episodeLabel);

                title = new TextView(ctx);
                title.setTextColor(COLOR_TEXT);
                title.setTextSize(14);
                title.setTypeface(null, Typeface.BOLD);
                title.setMaxLines(2);
                title.setEllipsize(android.text.TextUtils.TruncateAt.END);
                title.setLineSpacing(dp(2), 1f);
                LinearLayout.LayoutParams tlLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
                tlLp.topMargin = dp(3);
                textCol.addView(title, tlLp);

                meta = new TextView(ctx);
                meta.setTextColor(COLOR_TEXT2);
                meta.setTextSize(12);
                meta.setMaxLines(1);
                LinearLayout.LayoutParams mLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
                mLp.topMargin = dp(5);
                textCol.addView(meta, mLp);
            }

            void bind(VideoItem item) {
                if (item == null) return;

                episodeLabel.setText(item.episode >= 0 ? "Cap. " + item.episode : "Cap. ?");
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
                if (item.thumbPath != null) new ChannelAdapter.ThumbLoader(thumb).execute(item.thumbPath);
            }

            private int dp(int v) {
                return (int) (v * ctx.getResources().getDisplayMetrics().density + 0.5f);
            }
        }
    }
}
