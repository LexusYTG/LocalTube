package com.lexus2026.localtube;

import android.*;
import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;

public class MainActivity extends Activity
{

    private static final int REQ_PERMISSION     = 100;
    private static final int REQ_MANAGE_STORAGE = 101;

    private static final int TAB_HOME    = 0;
    private static final int TAB_SHORTS  = 1;
    private static final int TAB_HISTORY = 2;
    private static final int TAB_MOVIES  = 3;

    private static final int COLOR_BG      = 0xFF0A0A0A;
    private static final int COLOR_SURFACE = 0xFF141414;
    private static final int COLOR_ELEV    = 0xFF1F1F1F;
    private static final int COLOR_PRIMARY = 0xFFFF004D;
    private static final int COLOR_TEXT    = 0xFFFFFFFF;
    private static final int COLOR_TEXT2   = 0xFFA0A0A0;
    private static final int COLOR_TEXT3   = 0xFF5C5C5C;
    private static final int COLOR_BORDER  = 0xFF262626;

    private static final String KEY_ALREADY_LOADED = "already_loaded";
    private static final String KEY_CURRENT_TAB    = "current_tab";

    private GlobalIndex index;
    private RecommendationEngine engine;

    private FrameLayout tabContainer;
    private ProgressBar progressBar;
    private TextView statusText;

    private LinearLayout tabHome, tabShorts, tabSeries, tabMovies;
    private TextView labelHome, labelShorts, labelSeries, labelMovies;
    private View indHome, indShorts, indSeries, indMovies;

    private int currentTab = TAB_HOME;
    private boolean alreadyLoaded = false;

    private HomeFragment     homeFragment;
    private ShortsFragment   shortsFragment;
    private HistoryFragment  historyFragment;
    private MoviesFragment   moviesFragment;

    private View miniPlayerBar;
    private TextView miniPlayerTitle;

    @Override
    protected void onCreate(Bundle savedInstanceState)
	{
        super.onCreate(savedInstanceState);
        Lang.setLang(AppPrefs.get(this).getLanguage());
        if (savedInstanceState != null)
		{
            alreadyLoaded = savedInstanceState.getBoolean(KEY_ALREADY_LOADED, false);
            currentTab    = savedInstanceState.getInt(KEY_CURRENT_TAB, TAB_HOME);
        }

        buildUi();

        engine = new RecommendationEngine();
        index  = new GlobalIndex(this);

        String rootPath = AppPrefs.get(this).getRootPath();
        if (rootPath == null)
		{
            File def = new File(Environment.getExternalStorageDirectory(), "Movies");
            if (!def.exists()) def = Environment.getExternalStorageDirectory();
            rootPath = def.getAbsolutePath();
            AppPrefs.get(this).setRootPath(rootPath);
        }
        index.setRootPath(rootPath);

        checkPermissionsAndLoad();
    }

    private void buildUi()
	{
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(COLOR_BG);
        setContentView(root);

        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        root.addView(main, new FrameLayout.LayoutParams(
						 ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        main.addView(buildTopBar(), new LinearLayout.LayoutParams(
						 ViewGroup.LayoutParams.MATCH_PARENT, dp(62)));

        FrameLayout contentWrapper = new FrameLayout(this);
        main.addView(contentWrapper, new LinearLayout.LayoutParams(
						 ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        tabContainer = new FrameLayout(this);
        contentWrapper.addView(tabContainer, new FrameLayout.LayoutParams(
								   ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        progressBar = new ProgressBar(this);
        FrameLayout.LayoutParams pbLp = new FrameLayout.LayoutParams(
			ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pbLp.gravity = Gravity.CENTER;
        contentWrapper.addView(progressBar, pbLp);
        progressBar.setVisibility(View.GONE);

        statusText = new TextView(this);
        statusText.setTextColor(COLOR_TEXT2);
        statusText.setTextSize(14);
        statusText.setGravity(Gravity.CENTER);
        statusText.setLineSpacing(dp(6), 1f);
        FrameLayout.LayoutParams stLp = new FrameLayout.LayoutParams(
			ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        stLp.gravity = Gravity.CENTER;
        stLp.leftMargin = dp(32);
        stLp.rightMargin = dp(32);
        contentWrapper.addView(statusText, stLp);
        statusText.setVisibility(View.GONE);

        main.addView(buildBottomNav(), new LinearLayout.LayoutParams(
						 ViewGroup.LayoutParams.MATCH_PARENT, dp(66)));

        buildMiniPlayerBar(root);

        updateTabStyles();
    }

    private View buildTopBar()
	{
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(COLOR_SURFACE);
        bar.setPadding(dp(18), 0, dp(10), 0);

        View accent = new View(this);
        GradientDrawable accentBg = new GradientDrawable();
        accentBg.setColor(COLOR_PRIMARY);
        accentBg.setCornerRadius(dp(3));
        accent.setBackground(accentBg);
        LinearLayout.LayoutParams accentLp = new LinearLayout.LayoutParams(dp(4), dp(24));
        accentLp.rightMargin = dp(12);
        bar.addView(accent, accentLp);

        TextView title = new TextView(this);
        title.setText("LocalTube");
        title.setTextColor(COLOR_TEXT);
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setLetterSpacing(0.02f);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
			0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        bar.addView(title, titleLp);

        bar.addView(circleIconButton(android.R.drawable.ic_menu_search,
						new View.OnClickListener() {
							@Override public void onClick(View v)
							{
								startActivity(new Intent(MainActivity.this, SearchActivity.class));
							}
						}));

        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(dp(6), 1);
        bar.addView(new View(this), sp);

        bar.addView(circleIconButton(android.R.drawable.ic_menu_preferences,
						new View.OnClickListener() {
							@Override public void onClick(View v)
							{
								startActivity(new Intent(MainActivity.this, SettingsActivity.class));
							}
						}));

        return bar;
    }

    private ImageButton circleIconButton(int iconRes, View.OnClickListener listener)
	{
        ImageButton btn = new ImageButton(this);
        btn.setImageResource(iconRes);
        btn.setColorFilter(COLOR_TEXT);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(COLOR_ELEV);
        btn.setBackground(bg);
        btn.setPadding(dp(9), dp(9), dp(9), dp(9));
        btn.setOnClickListener(listener);
        return btn;
    }

    private View buildBottomNav()
	{
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setBackgroundColor(COLOR_SURFACE);

        View divider = new View(this);
        divider.setBackgroundColor(COLOR_BORDER);
        wrapper.addView(divider, new LinearLayout.LayoutParams(
							ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setBackgroundColor(COLOR_SURFACE);
        wrapper.addView(nav, new LinearLayout.LayoutParams(
							ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        tabHome   = createTab(Lang.get("tab_home"));
        tabShorts = createTab(Lang.get("tab_shorts"));
        tabSeries = createTab(Lang.get("tab_history"));
        tabMovies = createTab(Lang.get("tab_movies"));

        labelHome   = (TextView) tabHome.getChildAt(1);
        labelShorts = (TextView) tabShorts.getChildAt(1);
        labelSeries = (TextView) tabSeries.getChildAt(1);
        labelMovies = (TextView) tabMovies.getChildAt(1);

        indHome   = tabHome.getChildAt(0);
        indShorts = tabShorts.getChildAt(0);
        indSeries = tabSeries.getChildAt(0);
        indMovies = tabMovies.getChildAt(0);

        nav.addView(tabHome,   tabLp());
        nav.addView(tabShorts, tabLp());
        nav.addView(tabSeries, tabLp());
        nav.addView(tabMovies, tabLp());

        tabHome.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v)
				{ switchTab(TAB_HOME); }
			});
        tabShorts.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v)
				{ switchTab(TAB_SHORTS); }
			});
        tabSeries.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v)
				{ switchTab(TAB_HISTORY); }
			});
        tabMovies.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v)
				{ switchTab(TAB_MOVIES); }
			});

        return wrapper;
    }

    private LinearLayout createTab(String label)
	{
        LinearLayout tab = new LinearLayout(this);
        tab.setOrientation(LinearLayout.VERTICAL);
        tab.setGravity(Gravity.CENTER_HORIZONTAL);

        View indicator = new View(this);
        GradientDrawable ibg = new GradientDrawable();
        ibg.setColor(COLOR_PRIMARY);
        ibg.setCornerRadius(dp(4));
        indicator.setBackground(ibg);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(28), dp(3));
        ilp.topMargin = dp(8);
        tab.addView(indicator, ilp);
        indicator.setVisibility(View.INVISIBLE);

        TextView txt = new TextView(this);
        txt.setText(label);
        txt.setTextColor(COLOR_TEXT3);
        txt.setTextSize(13);
        txt.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
			ViewGroup.LayoutParams.WRAP_CONTENT, 0, 1f);
        tlp.topMargin = dp(2);
        tab.addView(txt, tlp);

        return tab;
    }

    private LinearLayout.LayoutParams tabLp()
	{
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
    }

    private void updateTabStyles()
	{
        styleTab(labelHome,   indHome,   currentTab == TAB_HOME);
        styleTab(labelShorts, indShorts, currentTab == TAB_SHORTS);
        styleTab(labelSeries, indSeries, currentTab == TAB_HISTORY);
        styleTab(labelMovies, indMovies, currentTab == TAB_MOVIES);
    }

    private void styleTab(TextView label, View indicator, boolean selected)
	{
        label.setTextColor(selected ? COLOR_TEXT : COLOR_TEXT3);
        label.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
        indicator.setVisibility(selected ? View.VISIBLE : View.INVISIBLE);
    }

    private void checkPermissionsAndLoad()
	{
        if (Build.VERSION.SDK_INT >= 30)
		{
            if (!hasAllFilesAccess())
			{ requestAllFilesAccess(); return; }
            loadContent();
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
		{
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
				!= PackageManager.PERMISSION_GRANTED)
			{
                requestPermissions(new String[]{
									   Manifest.permission.READ_EXTERNAL_STORAGE,
									   Manifest.permission.WRITE_EXTERNAL_STORAGE
								   }, REQ_PERMISSION);
                return;
            }
        }
        loadContent();
    }

    private boolean hasAllFilesAccess()
	{
        if (Build.VERSION.SDK_INT < 30) return true;
        try
		{
            java.lang.reflect.Method m = Environment.class.getMethod("isExternalStorageManager");
            return (Boolean) m.invoke(null);
        }
		catch (Exception e)
		{ return false; }
    }

    private void requestAllFilesAccess()
	{
        try
		{
            Intent intent = new Intent("android.settings.MANAGE_APP_ALL_FILES_ACCESS_PERMISSION");
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, REQ_MANAGE_STORAGE);
        }
		catch (Exception e)
		{
            startActivityForResult(new Intent(
									   "android.settings.MANAGE_ALL_FILES_ACCESS_PERMISSION"), REQ_MANAGE_STORAGE);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
	{
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_MANAGE_STORAGE)
		{
            if (hasAllFilesAccess()) loadContent();
            else showStatus(Lang.get("permission_files"));
        }
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] res)
	{
        if (req == REQ_PERMISSION && res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED)
		{
            loadContent();
        }
		else
		{
            showStatus(Lang.get("permission_denied"));
        }
    }

    private void loadContent()
	{
        List<VideoItem> cached = index.loadFromDisk();
        if (!cached.isEmpty())
		{ onDataReady(); alreadyLoaded = true; }
        else startScan();
    }

    public void startScan()
	{
        showLoading(Lang.get("scan_loading"));
        index.scanAsync(new GlobalIndex.ScanCallback() {
				@Override public void onProgress(String file, int done, int total)
				{
					statusText.setText(Lang.get("scan_progress") + done + "/" + total + "\n" + file);
				}
				@Override public void onComplete(List<VideoItem> videos)
				{
					progressBar.setVisibility(View.GONE);
					statusText.setVisibility(View.GONE);
					tabContainer.setVisibility(View.VISIBLE);
					if (videos.isEmpty())
					{
						showStatus(Lang.get("scan_no_videos")
								   + AppPrefs.get(MainActivity.this).getRootPath());
					}
					else
					{
						onDataReady();
						alreadyLoaded = true;
						Toast.makeText(MainActivity.this,
									   videos.size() + Lang.get("videos_indexed"), Toast.LENGTH_SHORT).show();
					}
				}
				@Override public void onError(String message)
				{
					progressBar.setVisibility(View.GONE);
					showStatus(Lang.get("error_prefix") + message);
				}
			});
    }

    private void onDataReady()
	{
        statusText.setVisibility(View.GONE);
        progressBar.setVisibility(View.GONE);
        tabContainer.setVisibility(View.VISIBLE);
        homeFragment   = new HomeFragment(this, index, engine);
        shortsFragment = new ShortsFragment(this, index);
        historyFragment = new HistoryFragment(this, index);
        moviesFragment = new MoviesFragment(this, index);
        switchTab(currentTab);
    }

    private void switchTab(int tab)
	{
        if (shortsFragment != null && currentTab == TAB_SHORTS && tab != TAB_SHORTS)
		{
            shortsFragment.onHidden();
        }
        currentTab = tab;
        updateTabStyles();
        tabContainer.removeAllViews();

        if (homeFragment == null) return;

        View view = null;
        switch (tab)
		{
            case TAB_HOME:    view = homeFragment.getView();    break;
            case TAB_SHORTS:  view = shortsFragment.getView();  break;
            case TAB_HISTORY: view = historyFragment.getView(); break;
            case TAB_MOVIES:  view = moviesFragment.getView();  break;
        }
        if (view != null)
		{
            if (view.getParent() != null) ((ViewGroup) view.getParent()).removeView(view);
            tabContainer.addView(view, new FrameLayout.LayoutParams(
									 ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }

        if (tab == TAB_SHORTS && shortsFragment != null) shortsFragment.onShown();
    }

    @Override
    protected void onResume()
	{
        super.onResume();
        String savedLang = AppPrefs.get(this).getLanguage();
        if (!savedLang.equals(Lang.getLang()))
		{
            Lang.setLang(savedLang);
            recreate();
            return;
        }
        if (alreadyLoaded && !index.getAll().isEmpty() && homeFragment != null) homeFragment.refresh();
        if (alreadyLoaded && historyFragment != null) historyFragment.refresh();
        if (currentTab == TAB_SHORTS && shortsFragment != null) shortsFragment.onShown();
        updateMiniPlayerBar();
    }

    @Override
    protected void onPause()
	{
        super.onPause();
        if (currentTab == TAB_SHORTS && shortsFragment != null) shortsFragment.onHidden();
    }

    private void buildMiniPlayerBar(FrameLayout root)
	{
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(16), 0, dp(8), 0);
        bar.setVisibility(View.GONE);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(COLOR_ELEV);
        bg.setCornerRadius(dp(22));
        bar.setBackground(bg);
        bar.setElevation(dp(8));

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
			ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        lp.gravity = Gravity.BOTTOM;
        lp.bottomMargin = dp(66 + 12);
        lp.leftMargin = dp(12);
        lp.rightMargin = dp(12);
        root.addView(bar, lp);

        View accent = new View(this);
        GradientDrawable accentBg = new GradientDrawable();
        accentBg.setColor(COLOR_PRIMARY);
        accentBg.setCornerRadius(dp(2));
        accent.setBackground(accentBg);
        bar.addView(accent, new LinearLayout.LayoutParams(dp(4), dp(30)));

        miniPlayerTitle = new TextView(this);
        miniPlayerTitle.setTextColor(COLOR_TEXT);
        miniPlayerTitle.setTextSize(14);
        miniPlayerTitle.setTypeface(null, Typeface.BOLD);
        miniPlayerTitle.setSingleLine(true);
        miniPlayerTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
			0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleLp.leftMargin = dp(14);
        bar.addView(miniPlayerTitle, titleLp);

        ImageButton btnClose = new ImageButton(this);
        btnClose.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
        btnClose.setColorFilter(COLOR_TEXT2);
        GradientDrawable cbg = new GradientDrawable();
        cbg.setShape(GradientDrawable.OVAL);
        cbg.setColor(0x33FFFFFF);
        btnClose.setBackground(cbg);
        btnClose.setPadding(dp(10), dp(10), dp(10), dp(10));
        bar.addView(btnClose, new LinearLayout.LayoutParams(dp(40), dp(40)));

        bar.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v)
				{ expandMiniPlayer(); }
			});
        btnClose.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v)
				{ closeMiniPlayer(); }
			});

        miniPlayerBar = bar;
    }

    private void updateMiniPlayerBar()
	{
        if (miniPlayerBar == null) return;
        if (MiniPlayerState.isActive())
		{
            VideoItem v = MiniPlayerState.getVideo();
            miniPlayerTitle.setText(v != null ? v.title : "");
            miniPlayerBar.setVisibility(View.VISIBLE);
        }
		else
		{
            miniPlayerBar.setVisibility(View.GONE);
        }
    }

    private void expandMiniPlayer()
	{
        if (!MiniPlayerState.isActive()) return;
        Class<?> target = MiniPlayerState.getType() == MiniPlayerState.SERIES
			? SeriesPlayerActivity.class : PlayerActivity.class;
        Intent i = new Intent(this, target);
        i.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(i);
    }

    private void closeMiniPlayer()
	{
        Activity playerActivity = PlayerBridge.getCurrent();
        if (playerActivity != null)
		{
            if (playerActivity instanceof PlaybackGuard.Session)
			{
                ((PlaybackGuard.Session) playerActivity).stopPlayback();
            }
            if (!playerActivity.isFinishing()) playerActivity.finish();
        }
        MiniPlayerState.clear();
        updateMiniPlayerBar();
    }

    private int dp(int v)
	{
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState)
	{
        super.onSaveInstanceState(outState);
        outState.putBoolean(KEY_ALREADY_LOADED, alreadyLoaded);
        outState.putInt(KEY_CURRENT_TAB, currentTab);
    }

    public void openPlayer(VideoItem item)
	{
        // Refrescar desde el índice para tener lastPosition actualizado
        VideoItem fresh = item.id != null ? index.findById(item.id) : null;
        Intent i = new Intent(this, PlayerActivity.class);
        i.putExtra(PlayerActivity.EXTRA_VIDEO, fresh != null ? fresh : item);
        startActivity(i);
    }

    public void openSeriesPlayer(VideoItem item)
	{
        VideoItem fresh = item.id != null ? index.findById(item.id) : null;
        Intent i = new Intent(this, SeriesPlayerActivity.class);
        i.putExtra(SeriesPlayerActivity.EXTRA_VIDEO, fresh != null ? fresh : item);
        startActivity(i);
    }

    public void openShorts(int absIndex)
	{
        switchTab(TAB_SHORTS);
        if (shortsFragment != null) shortsFragment.jumpTo(absIndex);
    }

    public GlobalIndex getIndex()
	{ return index; }
    public RecommendationEngine getEngine()
	{ return engine; }

    private void showStatus(String msg)
	{
        statusText.setText(msg);
        statusText.setVisibility(View.VISIBLE);
        tabContainer.setVisibility(View.GONE);
        progressBar.setVisibility(View.GONE);
    }

    private void showLoading(String msg)
	{
        statusText.setText(msg);
        statusText.setVisibility(View.VISIBLE);
        tabContainer.setVisibility(View.GONE);
        progressBar.setVisibility(View.VISIBLE);
    }
}



