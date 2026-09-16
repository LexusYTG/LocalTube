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
import android.net.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;

public class SettingsActivity extends Activity {

    private static final int REQ_OPEN_FOLDER = 42;
    private static final int REQ_PICK_PHOTO  = 43;

    private GlobalIndex channelsIndex;
    private String pendingChannelFolderId;

    private static final int COLOR_BG      = 0xFF0A0A0A;
    private static final int COLOR_SURFACE = 0xFF141414;
    private static final int COLOR_ELEV    = 0xFF1F1F1F;
    private static final int COLOR_TEXT    = 0xFFFFFFFF;
    private static final int COLOR_TEXT2   = 0xFFA0A0A0;
    private static final int COLOR_PRIMARY = 0xFFFF004D;

    private TextView folderLabel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppPrefs prefs = AppPrefs.get(this);
        Lang.setLang(prefs.getLanguage());
        buildUi();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(COLOR_BG);
        setContentView(root);

        root.addView(buildTopBar(), new LinearLayout.LayoutParams(
                         ViewGroup.LayoutParams.MATCH_PARENT, dp(62)));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        root.addView(scroll, new LinearLayout.LayoutParams(
                         ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, dp(8), 0, dp(28));
        scroll.addView(content);

        final AppPrefs prefs = AppPrefs.get(this);

        content.addView(sectionTitle(Lang.get("section_library")));
        content.addView(folderCard(prefs));
        content.addView(scanCard(prefs));
        content.addView(channelsCard(prefs));

        content.addView(sectionTitle(Lang.get("section_playback")));
        Switch swBgAudio = switchCard(content, Lang.get("bg_audio_title"),
                                      Lang.get("bg_audio_desc"), prefs.isBgAudioEnabled());
        swBgAudio.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override public void onCheckedChanged(CompoundButton b, boolean c) {
                    prefs.setBgAudioEnabled(c);
                }
            });

        Switch swAutoplay = switchCard(content, Lang.get("autoplay_title"),
                                       Lang.get("autoplay_desc"), prefs.isAutoplay());
        swAutoplay.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override public void onCheckedChanged(CompoundButton b, boolean c) {
                    prefs.setAutoplay(c);
                }
            });

        content.addView(sectionTitle(Lang.get("section_language")));
        content.addView(languageCard(prefs));
    }

    private View buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(COLOR_SURFACE);
        bar.setPadding(dp(10), 0, dp(10), 0);

        ImageButton back = new ImageButton(this);
        back.setImageResource(android.R.drawable.ic_menu_revert);
        back.setColorFilter(COLOR_TEXT);
        GradientDrawable bbg = new GradientDrawable();
        bbg.setShape(GradientDrawable.OVAL);
        bbg.setColor(0xFF1F1F1F);
        back.setBackground(bbg);
        back.setPadding(dp(9), dp(9), dp(9), dp(9));
        bar.addView(back, new LinearLayout.LayoutParams(dp(44), dp(44)));
        back.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { finish(); }
            });

        TextView title = new TextView(this);
        title.setText(Lang.get("settings_title"));
        title.setTextColor(COLOR_TEXT);
        title.setTextSize(19);
        title.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = dp(12);
        bar.addView(title, lp);

        return bar;
    }

    private View sectionTitle(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(COLOR_PRIMARY);
        tv.setTextSize(12);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setLetterSpacing(0.12f);
        tv.setPadding(dp(24), dp(20), dp(24), dp(8));
        return tv;
    }

    private View folderCard(final AppPrefs prefs) {
        LinearLayout card = card();
        card.setOrientation(LinearLayout.VERTICAL);

        TextView label = new TextView(this);
        label.setText(Lang.get("folder_root"));
        label.setTextColor(COLOR_TEXT);
        label.setTextSize(15);
        label.setTypeface(null, Typeface.BOLD);
        card.addView(label);

        folderLabel = new TextView(this);
        folderLabel.setText(prefs.getRootPath() != null ? prefs.getRootPath() : Lang.get("folder_none"));
        folderLabel.setTextColor(COLOR_TEXT2);
        folderLabel.setTextSize(12);
        folderLabel.setPadding(0, dp(8), 0, dp(14));
        card.addView(folderLabel);

        TextView btn = pillButton(Lang.get("folder_pick"), false);
        card.addView(btn);
        btn.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { openFolderPicker(); }
            });

        return card;
    }

    private View scanCard(final AppPrefs prefs) {
        LinearLayout card = card();
        card.setOrientation(LinearLayout.VERTICAL);

        TextView label = new TextView(this);
        label.setText(Lang.get("scan_title"));
        label.setTextColor(COLOR_TEXT);
        label.setTextSize(15);
        label.setTypeface(null, Typeface.BOLD);
        card.addView(label);

        TextView desc = new TextView(this);
        desc.setText(Lang.get("scan_desc"));
        desc.setTextColor(COLOR_TEXT2);
        desc.setTextSize(12);
        desc.setPadding(0, dp(8), 0, dp(14));
        card.addView(desc);

        TextView btn = pillButton(Lang.get("scan_btn"), true);
        card.addView(btn);
        btn.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    startIncrementalScan(prefs);
                }
            });
        btn.setOnLongClickListener(new View.OnLongClickListener() {
                @Override public boolean onLongClick(View v) {
                    confirmFullReindex(prefs);
                    return true;
                }
            });

        
        TextView hint = new TextView(this);
        hint.setText(Lang.get("scan_reindex_desc"));
        hint.setTextColor(COLOR_TEXT2);
        hint.setTextSize(11);
        hint.setPadding(0, dp(10), 0, 0);
        card.addView(hint);

        return card;
    }

    
    private void startIncrementalScan(final AppPrefs prefs) {
        Toast.makeText(SettingsActivity.this, Lang.get("scan_running"), Toast.LENGTH_SHORT).show();
        GlobalIndex idx = new GlobalIndex(SettingsActivity.this);
        String root = prefs.getRootPath();
        if (root == null) return;
        idx.setRootPath(root);
        idx.loadFromDisk();
        idx.scanAsync(new GlobalIndex.ScanCallback() {
                @Override public void onProgress(String f, int d, int t) {}
                @Override public void onComplete(List<VideoItem> videos) {
                    Toast.makeText(SettingsActivity.this,
                                   videos.size() + Lang.get("scan_done"), Toast.LENGTH_SHORT).show();
                }
                @Override public void onError(String msg) {
                    Toast.makeText(SettingsActivity.this,
                                   Lang.get("scan_error") + msg, Toast.LENGTH_SHORT).show();
                }
            });
    }

    
    private void confirmFullReindex(final AppPrefs prefs) {
        new AlertDialog.Builder(this)
            .setTitle(Lang.get("scan_reindex_title"))
            .setMessage(Lang.get("scan_reindex_confirm"))
            .setPositiveButton(Lang.get("scan_reindex_btn"), new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int which) {
                    performFullReindex(prefs);
                }
            })
            .setNegativeButton(Lang.get("cancel"), null)
            .show();
    }

    
    private void performFullReindex(final AppPrefs prefs) {
        final String root = prefs.getRootPath();
        if (root == null) {
            Toast.makeText(this, Lang.get("scan_no_root"), Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, Lang.get("scan_reindex_running"), Toast.LENGTH_SHORT).show();

        final GlobalIndex idx = new GlobalIndex(SettingsActivity.this);
        idx.setRootPath(root);

        new Thread(new Runnable() {
				@Override public void run() {
					
					idx.wipeAllIndexData();

					runOnUiThread(new Runnable() {
							@Override public void run() {
								Toast.makeText(SettingsActivity.this,
											   Lang.get("scan_running"), Toast.LENGTH_SHORT).show();

								
								idx.loadFromDisk();

								idx.scanAsync(new GlobalIndex.ScanCallback() {
										@Override public void onProgress(String f, int d, int t) {}
										@Override public void onComplete(List<VideoItem> videos) {
											Toast.makeText(SettingsActivity.this,
														   Lang.get("scan_reindex_done")
														   + videos.size()
														   + Lang.get("scan_done"),
														   Toast.LENGTH_LONG).show();
										}
										@Override public void onError(String msg) {
											Toast.makeText(SettingsActivity.this,
														   Lang.get("scan_error") + msg, Toast.LENGTH_SHORT).show();
										}
									});
							}
						});
				}
			}).start();
    }

    private View channelsCard(final AppPrefs prefs) {
        LinearLayout card = card();
        card.setOrientation(LinearLayout.VERTICAL);

        TextView label = new TextView(this);
        label.setText(Lang.get("channels_title"));
        label.setTextColor(COLOR_TEXT);
        label.setTextSize(15);
        label.setTypeface(null, Typeface.BOLD);
        card.addView(label);

        TextView desc = new TextView(this);
        desc.setText(Lang.get("channels_desc"));
        desc.setTextColor(COLOR_TEXT2);
        desc.setTextSize(12);
        desc.setPadding(0, dp(8), 0, dp(14));
        card.addView(desc);

        TextView btn = pillButton(Lang.get("channels_btn"), false);
        card.addView(btn);
        btn.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { showManageChannelsDialog(prefs); }
            });

        return card;
    }

    private GlobalIndex loadChannelsIndex(AppPrefs prefs) {
        String root = prefs.getRootPath();
        if (root == null) {
            Toast.makeText(this, Lang.get("scan_no_root"), Toast.LENGTH_SHORT).show();
            return null;
        }
        channelsIndex = new GlobalIndex(this);
        channelsIndex.setRootPath(root);
        channelsIndex.loadFromDisk();
        return channelsIndex;
    }

    private void showManageChannelsDialog(final AppPrefs prefs) {
        GlobalIndex idx = loadChannelsIndex(prefs);
        if (idx == null) return;

        final Collection<GlobalIndex.ChannelEntry> channels = idx.getChannels();
        if (channels.isEmpty()) {
            Toast.makeText(this, Lang.get("channels_empty"), Toast.LENGTH_SHORT).show();
            return;
        }

        final List<GlobalIndex.ChannelEntry> list = new ArrayList<GlobalIndex.ChannelEntry>(channels);
        String[] labels = new String[list.size()];
        for (int i = 0; i < list.size(); i++) {
            GlobalIndex.ChannelEntry ci = list.get(i);
            ChannelData cd = ci.getChannelData();
            String name = cd != null && cd.displayName != null && !cd.displayName.isEmpty()
                ? cd.displayName : ci.getFolderId();
            labels[i] = name + " (" + ci.getVideos().size() + ")";
        }

        new AlertDialog.Builder(this)
            .setTitle(Lang.get("channels_title"))
            .setItems(labels, new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int which) {
                    showChannelOptionsDialog(list.get(which));
                }
            })
            .setNegativeButton(Lang.get("close"), null)
            .show();
    }

    private void showChannelOptionsDialog(final GlobalIndex.ChannelEntry ci) {
        ChannelData cd = ci.getChannelData();
        String name = cd != null && cd.displayName != null ? cd.displayName : ci.getFolderId();

        new AlertDialog.Builder(this)
            .setTitle(name)
            .setItems(new String[]{Lang.get("channel_rename"), Lang.get("channel_photo")},
            new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int which) {
                    if (which == 0) showRenameDialog(ci);
                    else pickChannelPhoto(ci);
                }
            })
            .setNegativeButton(Lang.get("cancel"), null)
            .show();
    }

    private void showRenameDialog(final GlobalIndex.ChannelEntry ci) {
        ChannelData cd = ci.getChannelData();
        final EditText input = new EditText(this);
        input.setTextColor(COLOR_TEXT);
        input.setText(cd != null && cd.displayName != null ? cd.displayName : ci.getFolderId());
        input.setSelection(input.getText().length());
        int pad = dp(20);
        input.setPadding(pad, dp(10), pad, dp(10));

        new AlertDialog.Builder(this)
            .setTitle(Lang.get("channel_rename_title"))
            .setView(input)
            .setPositiveButton(Lang.get("channel_save"), new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int which) {
                    String newName = input.getText().toString().trim();
                    if (newName.isEmpty()) return;
                    channelsIndex.renameChannel(ci.getFolderId(), newName);
                    Toast.makeText(SettingsActivity.this, Lang.get("channel_saved"), Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton(Lang.get("cancel"), null)
            .show();
    }

    private void pickChannelPhoto(GlobalIndex.ChannelEntry ci) {
        pendingChannelFolderId = ci.getFolderId();
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(intent, REQ_PICK_PHOTO);
    }

    private void applyChannelPhoto(Uri imageUri) {
        if (pendingChannelFolderId == null || channelsIndex == null) return;
        GlobalIndex.ChannelEntry ci = channelsIndex.getChannel(pendingChannelFolderId);
        if (ci == null) return;

        try {
            File thumbDir = ci.getThumbDir();
            if (!thumbDir.exists()) thumbDir.mkdirs();
            File dest = new File(thumbDir, "chanel_photo.jpg");

            InputStream in = getContentResolver().openInputStream(imageUri);
            FileOutputStream out = new FileOutputStream(dest);
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            in.close();
            out.close();

            channelsIndex.setChannelPhoto(pendingChannelFolderId, dest.getAbsolutePath());
            Toast.makeText(this, Lang.get("channel_photo_saved"), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, Lang.get("channel_photo_error"), Toast.LENGTH_SHORT).show();
        } finally {
            pendingChannelFolderId = null;
        }
    }

    private static final String[] LANG_CODES  = {"es", "en", "ru", "de", "zh"};
    private static final String[] LANG_LABELS = {"Español", "English", "Русский", "Deutsch", "中文"};

    private View languageCard(final AppPrefs prefs) {
        LinearLayout card = card();
        card.setOrientation(LinearLayout.VERTICAL);

        TextView label = new TextView(this);
        label.setText(Lang.get("lang_interface"));
        label.setTextColor(COLOR_TEXT);
        label.setTextSize(15);
        label.setTypeface(null, Typeface.BOLD);
        card.addView(label);

        final TextView currentLabel = new TextView(this);
        currentLabel.setTextColor(COLOR_TEXT2);
        currentLabel.setTextSize(12);
        currentLabel.setPadding(0, dp(8), 0, dp(14));
        card.addView(currentLabel);

        String saved = prefs.getLanguage();
        String display = LANG_LABELS[0];
        for (int i = 0; i < LANG_CODES.length; i++) {
            if (LANG_CODES[i].equals(saved)) { display = LANG_LABELS[i]; break; }
        }
        currentLabel.setText(display);

        TextView btn = pillButton(Lang.get("lang_btn"), false);
        card.addView(btn);
        btn.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    new AlertDialog.Builder(SettingsActivity.this)
                        .setTitle(Lang.get("lang_select_title"))
                        .setItems(LANG_LABELS, new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface d, int which) {
                                String code = LANG_CODES[which];
                                prefs.setLanguage(code);
                                Lang.setLang(code);
                                Toast.makeText(SettingsActivity.this,
                                               Lang.get("lang_changed") + LANG_LABELS[which], Toast.LENGTH_SHORT).show();
                                Intent restart = new Intent(SettingsActivity.this, SettingsActivity.class);
                                restart.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                                startActivity(restart);
                                finish();
                            }
                        })
                        .setNegativeButton(Lang.get("cancel"), null)
                        .show();
                }
            });

        return card;
    }

    private Switch switchCard(LinearLayout parent, String title, String desc, boolean checked) {
        LinearLayout card = card();
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tlLp = new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        card.addView(texts, tlLp);

        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(COLOR_TEXT);
        t.setTextSize(15);
        t.setTypeface(null, Typeface.BOLD);
        texts.addView(t);

        TextView d = new TextView(this);
        d.setText(desc);
        d.setTextColor(COLOR_TEXT2);
        d.setTextSize(12);
        d.setPadding(0, dp(4), 0, 0);
        texts.addView(d);

        Switch sw = new Switch(this);
        sw.setChecked(checked);
        card.addView(sw);

        parent.addView(card);
        return sw;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(COLOR_ELEV);
        bg.setCornerRadius(dp(20));
        card.setBackground(bg);
        card.setPadding(dp(20), dp(18), dp(20), dp(18));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(16), dp(6), dp(16), dp(6));
        card.setLayoutParams(lp);
        return card;
    }

    private TextView pillButton(String text, boolean primary) {
        TextView btn = new TextView(this);
        btn.setText(text);
        btn.setTextColor(COLOR_TEXT);
        btn.setTextSize(14);
        btn.setTypeface(null, Typeface.BOLD);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(dp(20), dp(14), dp(20), dp(14));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(primary ? COLOR_PRIMARY : 0xFF2A2A2A);
        bg.setCornerRadius(dp(24));
        btn.setBackground(bg);
        return btn;
    }

    private void openFolderPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);

        startActivityForResult(intent, REQ_OPEN_FOLDER);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_OPEN_FOLDER && resultCode == RESULT_OK && data != null) {
            Uri treeUri = data.getData();
            if (treeUri == null) return;
            getContentResolver().takePersistableUriPermission(
                treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            String path = uriToPath(treeUri);
            if (path != null) {
                AppPrefs.get(this).setRootPath(path);
                folderLabel.setText(path);
                Toast.makeText(this, Lang.get("folder_set"), Toast.LENGTH_SHORT).show();
            } else {
                String uriStr = treeUri.toString();
                AppPrefs.get(this).setRootPath(uriStr);
                folderLabel.setText(uriStr);
                Toast.makeText(this, Lang.get("folder_set"), Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == REQ_PICK_PHOTO && resultCode == RESULT_OK && data != null) {
            Uri imageUri = data.getData();
            if (imageUri != null) applyChannelPhoto(imageUri);
        }
    }

    private String uriToPath(Uri treeUri) {
        try {
            String docId = android.provider.DocumentsContract.getTreeDocumentId(treeUri);
            if (docId == null) return null;
            String[] parts = docId.split(":");
            if (parts.length < 2) return null;
            String type = parts[0];
            String relative = parts[1];
            if ("primary".equalsIgnoreCase(type)) {
                return android.os.Environment.getExternalStorageDirectory() + "/" + relative;
            } else {
                java.io.File storage = new java.io.File("/storage/" + type);
                if (storage.exists()) return storage.getAbsolutePath() + "/" + relative;
            }
        } catch (Exception e) {
            android.util.Log.w("SettingsActivity", "uriToPath failed", e);
        }
        return null;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
