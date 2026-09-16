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

import java.io.*;
import java.util.*;

public class VideoItem implements Serializable {

    public static final int TYPE_VIDEO  = 0;
    public static final int TYPE_SHORT  = 1;
    public static final int TYPE_SERIES = 2;
    public static final int TYPE_MOVIE  = 3;

    public String id;
    public String path;
    public String title;
    public String fileName;

    public long duration;
    public long fileSize;
    public int width;
    public int height;
    public String mimeType;
    public long dateAdded;
    public long dateModified;

    public String category;
    public int type;

    public String seriesName;
    public int episode;

    
    public boolean episodeManual;

    
    public List<String> tags = new ArrayList<String>();

    public String thumbPath;

    
    public String restorerFile;

    
    public String channelId;

    public int playCount;
    public long totalWatchTime;
    public long lastWatched;
    public long lastPosition;
    public float completionRate;

    public float score;
    public String genre;

    public VideoItem() {}

    public String getDurationFormatted() {
        long s = duration / 1000;
        long h = s / 3600;
        long m = (s % 3600) / 60;
        long sec = s % 60;
        if (h > 0) return String.format("%d:%02d:%02d", h, m, sec);
        return String.format("%d:%02d", m, sec);
    }

    public String getFileSizeFormatted() {
        if (fileSize < 1024) return fileSize + " " + Lang.get("unit_b");
        if (fileSize < 1024 * 1024) return String.format("%.1f " + Lang.get("unit_kb"), fileSize / 1024f);
        if (fileSize < 1024L * 1024 * 1024) return String.format("%.1f " + Lang.get("unit_mb"), fileSize / (1024f * 1024));
        return String.format("%.2f " + Lang.get("unit_gb"), fileSize / (1024f * 1024 * 1024));
    }

    public String getResolution() {
        if (width <= 0 || height <= 0) return "";
        if (height >= 2160) return Lang.get("res_4k");
        if (height >= 1080) return "1080p";
        if (height >= 720)  return "720p";
        if (height >= 480)  return "480p";
        return height + "p";
    }

    public boolean isShort() {
        return type == TYPE_SHORT;
    }

    
    public String getTagsString() {
        if (tags == null || tags.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tags.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(tags.get(i));
        }
        return sb.toString();
    }

    
    public static List<String> parseTagsString(String s) {
        List<String> out = new ArrayList<String>();
        if (s == null) return out;
        String[] parts = s.split(",");
        for (String p : parts) {
            String t = p.trim();
            if (!t.isEmpty() && !out.contains(t)) out.add(t);
        }
        return out;
    }

    @Override
    public String toString() {
        return title + " [" + getDurationFormatted() + "]";
    }
}
