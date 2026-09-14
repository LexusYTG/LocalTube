package com.lexus2026.localtube;

import android.view.*;
import android.widget.*;
import java.util.*;

public class ShortsFragment {

    private final MainActivity activity;
    private final VideoIndex index;
    private final FrameLayout root;
    private ShortsPlayerView player;
    private TextView emptyView;

    public ShortsFragment(MainActivity activity, VideoIndex index) {
        this.activity = activity;
        this.index    = index;

        root = new FrameLayout(activity);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.setBackgroundColor(0xFF000000);

        buildContent();
    }

    public View getView() { return root; }

    private void buildContent() {
        List<VideoItem> shorts = index.getShorts();
        if (shorts.isEmpty()) {
            emptyView = new TextView(activity);
            emptyView.setText("Sin shorts.\nPone videos verticales en la carpeta Shorts/");
            emptyView.setTextColor(0xFF8E8E93);
            emptyView.setTextSize(14);
            int pad = dp(32);
            emptyView.setPadding(pad, pad * 2, pad, pad);
            root.addView(emptyView);
            return;
        }

        player = new ShortsPlayerView(activity, index);
        root.addView(player, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        player.setData(shorts, 0);
    }

    public void jumpTo(int absIndex) {
        if (player == null) return;
        player.jumpTo(absIndex);
    }

    public void onShown() {
        if (player != null) player.resumePlayback();
    }

    public void onHidden() {
        if (player != null) player.pausePlayback();
    }

    private int dp(int v) {
        return (int) (v * activity.getResources().getDisplayMetrics().density + 0.5f);
    }
}
