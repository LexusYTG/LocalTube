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

    /** true si el número de capítulo fue fijado a mano por el usuario
     *  (el escaneo automático ya no lo vuelve a calcular desde el nombre). */
    public boolean episodeManual;

    /** Etiquetas de organización manual asignadas por el usuario. */
    public List<String> tags = new ArrayList<String>();

    public String thumbPath;

    /** Nombre del archivo dentro de rootPath/.restorer/ que guarda la posición. */
    public String restorerFile;

    /** folderId del canal al que pertenece este video (nombre de carpeta en /Catalogo/). */
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

    /** Tags como texto separado por comas, para mostrar/editar en un diálogo. */
    public String getTagsString() {
        if (tags == null || tags.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tags.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(tags.get(i));
        }
        return sb.toString();
    }

    /** Parsea un texto separado por comas y arma la lista de tags (sin vacíos ni duplicados). */
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


