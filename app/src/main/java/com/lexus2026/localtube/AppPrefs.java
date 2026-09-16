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

public class AppPrefs {

    private static final String PREFS_NAME = "localtube_prefs";
    private static final String KEY_ROOT_PATH       = "root_path";
    private static final String KEY_SPEED           = "playback_speed";
    private static final String KEY_BG_AUDIO        = "bg_audio";
    private static final String KEY_DARK_MODE       = "dark_mode";
    private static final String KEY_AUTOPLAY        = "autoplay";
    private static final String KEY_LANGUAGE        = "language";

    private static AppPrefs instance;
    private final SharedPreferences prefs;

    public static AppPrefs get(Context ctx) {
        if (instance == null) instance = new AppPrefs(ctx.getApplicationContext());
        return instance;
    }

    private AppPrefs(Context ctx) {
        prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public String getRootPath() { return prefs.getString(KEY_ROOT_PATH, null); }
    public void   setRootPath(String p) { prefs.edit().putString(KEY_ROOT_PATH, p).apply(); }

    public float getPlaybackSpeed() { return prefs.getFloat(KEY_SPEED, 1.0f); }
    public void  setPlaybackSpeed(float s) { prefs.edit().putFloat(KEY_SPEED, s).apply(); }

    public boolean isBgAudioEnabled() { return prefs.getBoolean(KEY_BG_AUDIO, true); }
    public void    setBgAudioEnabled(boolean b) { prefs.edit().putBoolean(KEY_BG_AUDIO, b).apply(); }

    public boolean isDarkMode() { return prefs.getBoolean(KEY_DARK_MODE, true); }
    public void    setDarkMode(boolean b) { prefs.edit().putBoolean(KEY_DARK_MODE, b).apply(); }

    public boolean isAutoplay() { return prefs.getBoolean(KEY_AUTOPLAY, true); }
    public void    setAutoplay(boolean b) { prefs.edit().putBoolean(KEY_AUTOPLAY, b).apply(); }

    public String getLanguage() { return prefs.getString(KEY_LANGUAGE, "es"); }
    public void   setLanguage(String lang) { prefs.edit().putString(KEY_LANGUAGE, lang).apply(); }
}
