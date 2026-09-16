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

public class SearchActivity extends Activity {

    private static final int COLOR_BG      = 0xFF0A0A0A;
    private static final int COLOR_SURFACE = 0xFF141414;
    private static final int COLOR_ELEV    = 0xFF1F1F1F;
    private static final int COLOR_TEXT    = 0xFFFFFFFF;
    private static final int COLOR_TEXT2   = 0xFFA0A0A0;

    private GlobalIndex index;
    private VideoAdapter adapter;
    private EditText searchBox;
    private ListView listView;
    private TextView emptyView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();

        index = new GlobalIndex(this);
        String root = AppPrefs.get(this).getRootPath();
        if (root != null) { index.setRootPath(root); index.loadFromDisk(); }

        adapter = new VideoAdapter(this);
        adapter.setListener(new VideoAdapter.OnVideoClickListener() {
				@Override public void onVideoClick(VideoItem item, int position) {
					Intent i = new Intent(SearchActivity.this, PlayerActivity.class);
					i.putExtra(PlayerActivity.EXTRA_VIDEO, item);
					startActivity(i);
				}
			});
        listView.setAdapter(adapter);

        searchBox.addTextChangedListener(new TextWatcher() {
				@Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
				@Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
				@Override public void afterTextChanged(Editable s) {
					String q = s.toString().trim();
					if (q.isEmpty()) adapter.setItems(index.getAll());
					else adapter.setItems(index.search(q));
					updateEmpty();
				}
			});

        adapter.setItems(index.getAll());
        updateEmpty();
        searchBox.requestFocus();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(COLOR_BG);
        setContentView(root);

        root.addView(buildTopBar(), new LinearLayout.LayoutParams(
						 ViewGroup.LayoutParams.MATCH_PARENT, dp(70)));

        listView = new ListView(this);
        listView.setDivider(null);
        listView.setDividerHeight(0);
        listView.setVerticalScrollBarEnabled(false);
        listView.setBackgroundColor(COLOR_BG);
        listView.setPadding(0, dp(6), 0, dp(12));
        listView.setClipToPadding(false);
        root.addView(listView, new LinearLayout.LayoutParams(
						 ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        emptyView = new TextView(this);
        emptyView.setTextColor(COLOR_TEXT2);
        emptyView.setTextSize(14);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setVisibility(View.GONE);
        root.addView(emptyView, new LinearLayout.LayoutParams(
						 ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private View buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(COLOR_SURFACE);
        bar.setPadding(dp(8), 0, dp(8), 0);

        ImageButton back = new ImageButton(this);
        back.setImageResource(android.R.drawable.ic_menu_revert);
        back.setColorFilter(COLOR_TEXT);
        GradientDrawable bbg = new GradientDrawable();
        bbg.setShape(GradientDrawable.OVAL);
        bbg.setColor(COLOR_ELEV);
        back.setBackground(bbg);
        back.setPadding(dp(9), dp(9), dp(9), dp(9));
        bar.addView(back, new LinearLayout.LayoutParams(dp(44), dp(44)));
        back.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) { finish(); }
			});

        FrameLayout searchWrap = new FrameLayout(this);
        LinearLayout.LayoutParams swLp = new LinearLayout.LayoutParams(0, dp(48), 1f);
        swLp.leftMargin = dp(10);
        swLp.rightMargin = dp(4);
        bar.addView(searchWrap, swLp);

        GradientDrawable sBg = new GradientDrawable();
        sBg.setColor(COLOR_ELEV);
        sBg.setCornerRadius(dp(24));
        searchWrap.setBackground(sBg);
        searchWrap.setPadding(dp(16), 0, dp(16), 0);

        searchBox = new EditText(this);
        searchBox.setHint("Buscar videos...");
        searchBox.setHintTextColor(COLOR_TEXT2);
        searchBox.setTextColor(COLOR_TEXT);
        searchBox.setTextSize(14);
        searchBox.setSingleLine(true);
        searchBox.setBackgroundColor(Color.TRANSPARENT);
        searchBox.setPadding(0, 0, 0, 0);
        searchBox.setTypeface(Typeface.DEFAULT);
        FrameLayout.LayoutParams sbLp = new FrameLayout.LayoutParams(
			ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        sbLp.gravity = Gravity.CENTER_VERTICAL;
        searchWrap.addView(searchBox, sbLp);

        return bar;
    }

    private void updateEmpty() {
        if (adapter.getCount() == 0) {
            emptyView.setVisibility(View.VISIBLE);
            if (searchBox.getText().toString().trim().isEmpty())
                emptyView.setText("Escribe algo para buscar");
            else
                emptyView.setText("Sin resultados");
            listView.setVisibility(View.GONE);
        } else {
            emptyView.setVisibility(View.GONE);
            listView.setVisibility(View.VISIBLE);
        }
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
