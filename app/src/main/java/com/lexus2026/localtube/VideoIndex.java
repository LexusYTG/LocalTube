package com.lexus2026.localtube;

import android.content.*;
import android.graphics.*;
import android.media.*;
import android.os.*;
import android.util.*;
import java.io.*;
import java.security.*;
import java.util.*;
import org.json.*;

public class VideoIndex {

    private static final String TAG        = "VideoIndex";
    private static final String INDEX_FILE = "index.json";
    private static final String THUMB_DIR  = ".localtube_thumbs";

    public static final String CAT_SHORTS  = "Shorts";
    public static final String CAT_SERIES  = "Series";
    public static final String CAT_MOVIES  = "Peliculas";
    public static final String CAT_GENERAL = "General";

    private static final Set<String> VIDEO_EXTENSIONS = new HashSet<String>();
    static {
        VIDEO_EXTENSIONS.add("mp4");
        VIDEO_EXTENSIONS.add("mkv");
        VIDEO_EXTENSIONS.add("avi");
        VIDEO_EXTENSIONS.add("mov");
        VIDEO_EXTENSIONS.add("webm");
        VIDEO_EXTENSIONS.add("flv");
        VIDEO_EXTENSIONS.add("3gp");
        VIDEO_EXTENSIONS.add("ts");
        VIDEO_EXTENSIONS.add("m4v");
        VIDEO_EXTENSIONS.add("wmv");
        VIDEO_EXTENSIONS.add("mpg");
        VIDEO_EXTENSIONS.add("mpeg");
    }

    private final Context context;
    private String rootPath;
    private File thumbDir;
    private List<VideoItem> videos = new ArrayList<VideoItem>();

    public interface ScanCallback {
        void onProgress(String currentFile, int done, int total);
        void onComplete(List<VideoItem> videos);
        void onError(String message);
    }

    public VideoIndex(Context context) {
        this.context = context;
    }

    public void setRootPath(String path) {
        this.rootPath = path;
        this.thumbDir = new File(path, THUMB_DIR);
        if (!thumbDir.exists()) thumbDir.mkdirs();
        RestorerStore.getOrCreateDir(path); // crear .restorer/
        ensureCategoryFolders();
    }

    public String getRootPath() { return rootPath; }

    private void ensureCategoryFolders() {
        if (rootPath == null) return;
        File root = new File(rootPath);
        if (!root.exists()) return;
        new File(root, CAT_SHORTS).mkdirs();
        new File(root, CAT_SERIES).mkdirs();
        new File(root, CAT_MOVIES).mkdirs();
    }

    public List<VideoItem> loadFromDisk() {
        if (rootPath == null) return new ArrayList<VideoItem>();
        File indexFile = new File(rootPath, INDEX_FILE);
        if (!indexFile.exists()) return new ArrayList<VideoItem>();
        try {
            byte[] bytes = readFile(indexFile);
            String json = new String(bytes, "UTF-8");
            JSONArray arr = new JSONArray(json);
            List<VideoItem> loaded = new ArrayList<VideoItem>();
            for (int i = 0; i < arr.length(); i++) {
                loaded.add(fromJson(arr.getJSONObject(i)));
            }
            videos = loaded;
            return videos;
        } catch (Exception e) {
            Log.e(TAG, "Error loading index", e);
        }
        return new ArrayList<VideoItem>();
    }

    public void scanAsync(final ScanCallback callback) {
        new AsyncTask<Void, Object[], Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                try {
                    File root = new File(rootPath);
                    if (!root.exists() || !root.isDirectory()) {
                        publishProgress(new Object[]{"error", "Carpeta no encontrada: " + rootPath});
                        return null;
                    }
                    List<File> found = new ArrayList<File>();
                    collectVideoFiles(root, found);
                    int total = found.size();
                    List<VideoItem> result = new ArrayList<VideoItem>();
                    for (int i = 0; i < found.size(); i++) {
                        File f = found.get(i);
                        publishProgress(new Object[]{"progress", f.getName(), i, total});
                        VideoItem item = buildVideoItem(f);
                        if (item != null) {
                            VideoItem existing = findById(item.id);
                            if (existing != null) {
                                item.playCount      = existing.playCount;
                                item.totalWatchTime = existing.totalWatchTime;
                                item.lastWatched    = existing.lastWatched;
                                item.lastPosition   = existing.lastPosition;
                                item.completionRate = existing.completionRate;
                                // Preservar el archivo de restorer para no perder el vínculo
                                if (existing.restorerFile != null && !existing.restorerFile.isEmpty()) {
                                    item.restorerFile = existing.restorerFile;
                                }
                            }
                            result.add(item);
                        }
                    }
                    videos = result;
                    saveToDisk();
                    publishProgress(new Object[]{"complete"});
                } catch (Exception e) {
                    Log.e(TAG, "Scan error", e);
                    publishProgress(new Object[]{"error", e.getMessage()});
                }
                return null;
            }

            @Override
            protected void onProgressUpdate(Object[]... values) {
                Object[] v = values[0];
                String type = (String) v[0];
                if ("progress".equals(type)) {
                    callback.onProgress((String) v[1], (Integer) v[2], (Integer) v[3]);
                } else if ("complete".equals(type)) {
                    callback.onComplete(videos);
                } else if ("error".equals(type)) {
                    callback.onError((String) v[1]);
                }
            }
        }.execute();
    }

    private void collectVideoFiles(File dir, List<File> out) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                if (!f.getName().startsWith(".")) collectVideoFiles(f, out);
            } else {
                String ext = getExtension(f.getName()).toLowerCase();
                if (VIDEO_EXTENSIONS.contains(ext)) out.add(f);
            }
        }
    }

    private VideoItem buildVideoItem(File f) {
        VideoItem item = new VideoItem();
        item.path     = f.getAbsolutePath();
        item.fileName = f.getName();
        item.id       = md5(item.path);
        item.fileSize = f.length();
        item.dateModified = f.lastModified();

        File parent = f.getParentFile();
        String parentName = parent != null ? parent.getName() : CAT_GENERAL;
        item.category = parentName;

        classifyItem(item, f, parentName);

        String base = item.fileName;
        int dot = base.lastIndexOf('.');
        if (dot > 0) base = base.substring(0, dot);
        item.title = base.replace('_', ' ').replace('-', ' ').trim();

        if (item.type == VideoItem.TYPE_SERIES) {
            item.seriesName = parentName;
            item.episode = extractEpisodeNumber(item.title);
        }

        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        try {
            mmr.setDataSource(item.path);
            String durStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (durStr != null) item.duration = Long.parseLong(durStr);
            String wStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
            String hStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
            if (wStr != null) item.width  = Integer.parseInt(wStr);
            if (hStr != null) item.height = Integer.parseInt(hStr);
            item.mimeType = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE);
            if (item.type == VideoItem.TYPE_SHORT) {
                redetectShort(item);
            }
            item.thumbPath = generateThumbnail(mmr, item.id);
        } catch (Exception e) {
            Log.w(TAG, "MMR failed for " + item.path + ": " + e.getMessage());
        } finally {
            try { mmr.release(); } catch (Exception ignored) {}
        }
        // Asignar archivo de restorer si es un item nuevo (los existentes ya lo traen del JSON)
        if (item.restorerFile == null || item.restorerFile.isEmpty()) {
            item.restorerFile = RestorerStore.assignFileName();
        }
        item.dateAdded = System.currentTimeMillis();
        return item;
    }

    private void classifyItem(VideoItem item, File f, String parentName) {
        String pLower = parentName.toLowerCase();
        if (pLower.equals(CAT_SHORTS.toLowerCase())) {
            item.type = VideoItem.TYPE_SHORT;
        } else if (pLower.equals(CAT_MOVIES.toLowerCase())) {
            item.type = VideoItem.TYPE_MOVIE;
        } else if (pLower.equals(CAT_SERIES.toLowerCase())) {
            item.type = VideoItem.TYPE_VIDEO;
        } else {
            File grandParent = f.getParentFile() != null ? f.getParentFile().getParentFile() : null;
            if (grandParent != null) {
                String gpName = grandParent.getName().toLowerCase();
                if (gpName.equals(CAT_SERIES.toLowerCase())) {
                    item.type = VideoItem.TYPE_SERIES;
                } else {
                    item.type = VideoItem.TYPE_VIDEO;
                }
            } else {
                item.type = VideoItem.TYPE_VIDEO;
            }
        }
    }

    private void redetectShort(VideoItem item) {
        if (item.width > 0 && item.height > 0 && item.height > item.width) {
            item.type = VideoItem.TYPE_SHORT;
        }
    }

    private int extractEpisodeNumber(String title) {
        String digitsOnly = title.replaceAll("[^0-9]", "");
        if (digitsOnly.isEmpty()) return -1;
        try {
            return Integer.parseInt(digitsOnly);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private String generateThumbnail(MediaMetadataRetriever mmr, String id) {
        File thumbFile = new File(thumbDir, id + ".jpg");
        if (thumbFile.exists()) return thumbFile.getAbsolutePath();
        try {
            Bitmap bmp = mmr.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            if (bmp == null) return null;

            int rotation = 0;
            try {
                String rotStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
                if (rotStr != null) rotation = Integer.parseInt(rotStr);
            } catch (Exception ignored) {}

            Bitmap oriented = bmp;
            if (rotation != 0) {
                android.graphics.Matrix matrix = new android.graphics.Matrix();
                matrix.postRotate(rotation);
                oriented = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), matrix, true);
                bmp.recycle();
            }

            int srcW = oriented.getWidth();
            int srcH = oriented.getHeight();
            int dstW, dstH;
            if (srcW >= srcH) {
                dstW = 320;
                dstH = Math.max(1, srcH * 320 / srcW);
            } else {
                dstH = 320;
                dstW = Math.max(1, srcW * 320 / srcH);
            }

            Bitmap scaled = Bitmap.createScaledBitmap(oriented, dstW, dstH, true);
            FileOutputStream fos = new FileOutputStream(thumbFile);
            scaled.compress(Bitmap.CompressFormat.JPEG, 80, fos);
            fos.close();
            oriented.recycle();
            scaled.recycle();
            return thumbFile.getAbsolutePath();
        } catch (Exception e) {
            Log.w(TAG, "Thumb failed for id " + id);
            return null;
        }
    }

    private void saveToDisk() {
        if (rootPath == null) return;
        try {
            JSONArray arr = new JSONArray();
            for (VideoItem v : videos) arr.put(toJson(v));
            File f = new File(rootPath, INDEX_FILE);
            FileWriter w = new FileWriter(f);
            w.write(arr.toString(2));
            w.close();
        } catch (Exception e) {
            Log.e(TAG, "Save index failed", e);
        }
    }

    private JSONObject toJson(VideoItem v) throws Exception {
        JSONObject o = new JSONObject();
        o.put("id",            v.id            == null ? "" : v.id);
        o.put("path",          v.path          == null ? "" : v.path);
        o.put("title",         v.title         == null ? "" : v.title);
        o.put("fileName",      v.fileName      == null ? "" : v.fileName);
        o.put("duration",      v.duration);
        o.put("fileSize",      v.fileSize);
        o.put("width",         v.width);
        o.put("height",        v.height);
        o.put("mimeType",      v.mimeType      == null ? "" : v.mimeType);
        o.put("dateAdded",     v.dateAdded);
        o.put("dateModified",  v.dateModified);
        o.put("category",      v.category      == null ? "" : v.category);
        o.put("type",          v.type);
        o.put("seriesName",    v.seriesName    == null ? "" : v.seriesName);
        o.put("episode",       v.episode);
        o.put("thumbPath",     v.thumbPath     == null ? "" : v.thumbPath);
        o.put("restorerFile",  v.restorerFile  == null ? "" : v.restorerFile);
        o.put("playCount",     v.playCount);
        o.put("totalWatchTime",v.totalWatchTime);
        o.put("lastWatched",   v.lastWatched);
        o.put("lastPosition",  v.lastPosition);
        o.put("completionRate",v.completionRate);
        o.put("score",         v.score);
        o.put("genre",         v.genre == null ? "" : v.genre);
        return o;
    }

    private VideoItem fromJson(JSONObject o) throws Exception {
        VideoItem v = new VideoItem();
        v.id            = o.optString("id");
        v.path          = o.optString("path");
        v.title         = o.optString("title");
        v.fileName      = o.optString("fileName");
        v.duration      = o.optLong("duration");
        v.fileSize      = o.optLong("fileSize");
        v.width         = o.optInt("width");
        v.height        = o.optInt("height");
        v.mimeType      = o.optString("mimeType");
        v.dateAdded     = o.optLong("dateAdded");
        v.dateModified  = o.optLong("dateModified");
        v.category      = o.optString("category");
        v.type          = o.optInt("type", VideoItem.TYPE_VIDEO);
        v.seriesName    = o.optString("seriesName");
        v.episode       = o.optInt("episode", -1);
        v.thumbPath     = o.optString("thumbPath");
        if (v.thumbPath != null && v.thumbPath.isEmpty()) v.thumbPath = null;
        v.restorerFile  = o.optString("restorerFile");
        if (v.restorerFile != null && v.restorerFile.isEmpty()) v.restorerFile = null;
        if (v.seriesName != null && v.seriesName.isEmpty()) v.seriesName = null;
        v.playCount      = o.optInt("playCount");
        v.totalWatchTime = o.optLong("totalWatchTime");
        v.lastWatched    = o.optLong("lastWatched");
        v.lastPosition   = o.optLong("lastPosition");
        v.completionRate = (float) o.optDouble("completionRate");
        v.score          = (float) o.optDouble("score");
        v.genre          = o.optString("genre");
        if (v.genre != null && v.genre.isEmpty()) v.genre = null;
        return v;
    }

    public List<VideoItem> getAll() { return videos; }

    public VideoItem findById(String id) {
        for (VideoItem v : videos) if (id != null && id.equals(v.id)) return v;
        return null;
    }

    public List<VideoItem> getByType(int type) {
        List<VideoItem> out = new ArrayList<VideoItem>();
        for (VideoItem v : videos) if (v.type == type) out.add(v);
        return out;
    }

    public List<VideoItem> getShorts() {
        return getByType(VideoItem.TYPE_SHORT);
    }

    public List<VideoItem> getMovies() {
        return getByType(VideoItem.TYPE_MOVIE);
    }

    public List<VideoItem> getSeries() {
        List<VideoItem> all = getByType(VideoItem.TYPE_SERIES);
        Collections.sort(all, new Comparator<VideoItem>() {
				@Override public int compare(VideoItem a, VideoItem b) {
					int sn = (a.seriesName == null ? "" : a.seriesName)
                        .compareTo(b.seriesName == null ? "" : b.seriesName);
					if (sn != 0) return sn;
					return Integer.compare(a.episode < 0 ? Integer.MAX_VALUE : a.episode,
										   b.episode < 0 ? Integer.MAX_VALUE : b.episode);
				}
			});
        return all;
    }

    public List<String> getSeriesNames() {
        Set<String> names = new HashSet<String>();
        for (VideoItem v : videos) {
            if (v.type == VideoItem.TYPE_SERIES && v.seriesName != null) {
                names.add(v.seriesName);
            }
        }
        List<String> list = new ArrayList<String>(names);
        Collections.sort(list);
        return list;
    }

    public List<VideoItem> getEpisodesOf(String seriesName) {
        List<VideoItem> out = new ArrayList<VideoItem>();
        for (VideoItem v : videos) {
            if (v.type == VideoItem.TYPE_SERIES && seriesName != null && seriesName.equals(v.seriesName)) {
                out.add(v);
            }
        }
        Collections.sort(out, new Comparator<VideoItem>() {
				@Override public int compare(VideoItem a, VideoItem b) {
					return Integer.compare(a.episode < 0 ? Integer.MAX_VALUE : a.episode,
										   b.episode < 0 ? Integer.MAX_VALUE : b.episode);
				}
			});
        return out;
    }

    public VideoItem getNextEpisode(VideoItem current) {
        if (current == null || current.seriesName == null) return null;
        List<VideoItem> episodes = getEpisodesOf(current.seriesName);
        for (int i = 0; i < episodes.size() - 1; i++) {
            if (episodes.get(i).id != null && episodes.get(i).id.equals(current.id)) {
                return episodes.get(i + 1);
            }
        }
        return null;
    }

    public List<VideoItem> getMixed(int max) {
        List<VideoItem> out = new ArrayList<VideoItem>();
        for (VideoItem v : videos) {
            if (v.type != VideoItem.TYPE_SHORT) out.add(v);
            if (out.size() >= max) break;
        }
        return out;
    }

    public List<VideoItem> search(String query) {
        String q = query.toLowerCase().trim();
        List<VideoItem> out = new ArrayList<VideoItem>();
        for (VideoItem v : videos) {
            if ((v.title    != null && v.title.toLowerCase().contains(q)) ||
                (v.category != null && v.category.toLowerCase().contains(q)) ||
                (v.seriesName != null && v.seriesName.toLowerCase().contains(q)) ||
                (v.fileName != null && v.fileName.toLowerCase().contains(q))) {
                out.add(v);
            }
        }
        return out;
    }

    /**
     * Devuelve el File de restorer asignado a un video, o null si no hay rootPath
     * o el video no tiene restorerFile asignado.
     */
    public java.io.File getRestorerFile(String videoId) {
        if (rootPath == null) return null;
        VideoItem v = findById(videoId);
        if (v == null || v.restorerFile == null) return null;
        return RestorerStore.buildFile(rootPath, v.restorerFile);
    }

    /**
     * Como getRestorerFile, pero si el item existe y no tiene restorerFile asignado
     * (fue indexado antes del sistema restorer), lo asigna ahora y persiste el índice.
     * Nunca devuelve null si el video existe en el índice.
     */
    public java.io.File ensureRestorerFile(String videoId, String rootPathFallback) {
        String rp = rootPath != null ? rootPath : rootPathFallback;
        if (rp == null) return null;
        VideoItem v = findById(videoId);
        if (v == null) {
            // Video no está en el índice aún: crear un archivo temporal con el id como nombre
            String tmpName = RestorerStore.assignFileName();
            return RestorerStore.buildFile(rp, tmpName);
        }
        if (v.restorerFile == null || v.restorerFile.isEmpty()) {
            v.restorerFile = RestorerStore.assignFileName();
            saveToDisk(); // persistir la asignación
        }
        return RestorerStore.buildFile(rp, v.restorerFile);
    }

    public void recordPlay(String id, long watchedMs, long position, long totalDuration) {
        VideoItem v = findById(id);
        if (v == null) return;
        v.playCount++;
        v.totalWatchTime += watchedMs;
        v.lastWatched = System.currentTimeMillis();
        v.lastPosition = position;
        if (totalDuration > 0) v.completionRate = (float) position / totalDuration;
        saveToDisk();
    }

    private static String getExtension(String name) {
        int i = name.lastIndexOf('.');
        return i >= 0 ? name.substring(i + 1) : "";
    }

    private static String md5(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] bytes = md.digest(s.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(s.hashCode());
        }
    }

    private static byte[] readFile(File f) throws IOException {
        java.io.FileInputStream fis = new java.io.FileInputStream(f);
        byte[] data = new byte[(int) f.length()];
        fis.read(data);
        fis.close();
        return data;
    }
}


