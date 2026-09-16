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
import android.media.*;
import android.os.*;
import android.util.*;
import java.io.*;
import java.security.*;
import java.util.*;
import java.util.regex.*;
import org.json.*;

public class GlobalIndex {

    private static final String TAG         = "GlobalIndex";
    private static final String CATALOG     = "Catalogo";
    private static final String INDEX_FILE  = "index.json";
    private static final String CINDEX_FILE = ".CIndex.json";
    private static final String SINDEX_FILE = ".SIndex.json";
    private static final String THUMB_DIR   = ".miniaturas";
    private static final String RESTORER_DIR = ".restorer";
    private static final String SHORTS_DIR  = "shorts";

    private static final Pattern PARENTHESES = Pattern.compile("\\([^)]*\\)");
    private static final Pattern DIGIT_RUN   = Pattern.compile("\\d+");

    private static final Set<String> VIDEO_EXTENSIONS = new HashSet<String>(Arrays.asList(
        "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v", "3gp", "ts", "m2ts"
    ));

    private final Context context;
    private String rootPath;
    private File   catalogDir;

    private final Map<String, ChannelEntry> channels = new LinkedHashMap<String, ChannelEntry>();

    public GlobalIndex(Context context) {
        this.context = context;
    }

    public void setRootPath(String path) {
        this.rootPath   = path;
        this.catalogDir = new File(path, CATALOG);
        catalogDir.mkdirs();
    }

    public String getRootPath()   { return rootPath; }
    public File   getCatalogDir() { return catalogDir; }

    public interface ScanCallback {
        void onProgress(String currentFile, int done, int total);
        void onComplete(List<VideoItem> videos);
        void onError(String message);
    }

    public List<VideoItem> loadFromDisk() {
        if (catalogDir == null) return new ArrayList<VideoItem>();
        channels.clear();
        File indexFile = new File(catalogDir, INDEX_FILE);
        if (!indexFile.exists()) return new ArrayList<VideoItem>();
        try {
            byte[] b = readFile(indexFile);
            JSONArray arr = new JSONArray(new String(b, "UTF-8"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject entry = arr.getJSONObject(i);
                String folderId = entry.optString("folderId");
                if (folderId == null || folderId.isEmpty()) continue;
                File channelDir = new File(catalogDir, folderId);
                if (!channelDir.exists()) continue;
                ChannelEntry ce = new ChannelEntry(channelDir);
                ce.ensureStructure();
                ce.load();
                channels.put(folderId, ce);
            }
        } catch (Exception e) {
            Log.e(TAG, "loadFromDisk failed", e);
        }
        return getAll();
    }

    public void scanAsync(final ScanCallback callback) {
        new AsyncTask<Void, Object[], Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                try {
                    if (catalogDir == null) {
                        publishProgress(new Object[]{"error", "Sin ruta de catálogo"});
                        return null;
                    }
                    File[] subdirs = catalogDir.listFiles(new FileFilter() {
                        @Override public boolean accept(File f) {
                            return f.isDirectory() && !f.getName().startsWith(".");
                        }
                    });
                    if (subdirs == null) subdirs = new File[0];

                    Set<String> realFolderIds = new HashSet<String>();
                    for (File d : subdirs) realFolderIds.add(d.getName());

                    Iterator<String> it = channels.keySet().iterator();
                    while (it.hasNext()) {
                        if (!realFolderIds.contains(it.next())) {
                            it.remove();
                        }
                    }

                    for (File d : subdirs) {
                        String fid = d.getName();
                        if (!channels.containsKey(fid)) {
                            ChannelEntry ce = new ChannelEntry(d);
                            ce.ensureStructure();
                            ce.load();
                            channels.put(fid, ce);
                        }
                    }

                    int chanTotal = channels.size();
                    int chanIdx   = 0;
                    for (Map.Entry<String, ChannelEntry> entry : channels.entrySet()) {
                        chanIdx++;
                        final String chanName = entry.getKey();
                        final ChannelEntry ce = entry.getValue();
                        publishProgress(new Object[]{"progress", "Canal: " + chanName, chanIdx, chanTotal});
                        boolean changed = ce.scanIncremental(new ChannelProgressCallback() {
                            @Override public void onProgress(String fileName, int cur, int tot) {
                                publishProgress(new Object[]{"progress", chanName + " / " + fileName, cur, tot});
                            }
                        });
                        if (changed) ce.save();
                    }

                    saveGlobalIndex();
                    publishProgress(new Object[]{"complete"});
                } catch (Exception e) {
                    Log.e(TAG, "scanAsync error", e);
                    publishProgress(new Object[]{"error", e.getMessage()});
                }
                return null;
            }

            @Override
            @SuppressWarnings("unchecked")
            protected void onProgressUpdate(Object[]... values) {
                Object[] v = values[0];
                String type = (String) v[0];
                if ("progress".equals(type)) {
                    callback.onProgress((String) v[1], (Integer) v[2], (Integer) v[3]);
                } else if ("complete".equals(type)) {
                    callback.onComplete(getAll());
                } else if ("error".equals(type)) {
                    callback.onError((String) v[1]);
                }
            }
        }.execute();
    }

    public void wipeAllIndexData() {
        if (catalogDir == null || !catalogDir.exists()) return;
        File globalIndex = new File(catalogDir, INDEX_FILE);
        if (globalIndex.exists()) globalIndex.delete();
        File[] channelDirs = catalogDir.listFiles(new FileFilter() {
            @Override public boolean accept(File f) {
                return f.isDirectory() && !f.getName().startsWith(".");
            }
        });
        if (channelDirs != null) {
            for (File ch : channelDirs) wipeChannelIndexData(ch);
        }
        channels.clear();
    }

    private void wipeChannelIndexData(File channelDir) {
        ChannelData cd = ChannelData.load(channelDir);
        String preservedPhoto = (cd != null && cd.photoPath != null && !cd.photoPath.isEmpty())
            ? cd.photoPath : null;
        File cIndex = new File(channelDir, CINDEX_FILE);
        if (cIndex.exists()) cIndex.delete();
        File thumbDir = new File(channelDir, THUMB_DIR);
        if (thumbDir.exists()) {
            File[] thumbs = thumbDir.listFiles();
            if (thumbs != null) {
                for (File t : thumbs) {
                    if (preservedPhoto != null && preservedPhoto.equals(t.getAbsolutePath())) continue;
                    t.delete();
                }
            }
        }
        File restorerDir = new File(channelDir, RESTORER_DIR);
        if (restorerDir.exists()) {
            File[] posFiles = restorerDir.listFiles();
            if (posFiles != null) for (File p : posFiles) p.delete();
        }
        File[] subdirs = channelDir.listFiles(new FileFilter() {
            @Override public boolean accept(File f) {
                return f.isDirectory() && !f.getName().startsWith(".") && !f.getName().equalsIgnoreCase(SHORTS_DIR);
            }
        });
        if (subdirs != null) {
            for (File sub : subdirs) {
                File sIndex = new File(sub, SINDEX_FILE);
                if (sIndex.exists()) sIndex.delete();
            }
        }
    }

    private void saveGlobalIndex() {
        try {
            JSONArray arr = new JSONArray();
            for (Map.Entry<String, ChannelEntry> entry : channels.entrySet()) {
                ChannelEntry ce = entry.getValue();
                ChannelData  cd = ce.getChannelData();
                JSONObject   o  = new JSONObject();
                o.put("folderId",    entry.getKey());
                o.put("cIndexPath",  CATALOG + "/" + entry.getKey() + "/" + CINDEX_FILE);
                o.put("videoAmount", ce.getAllVideos().size());
                o.put("seriesCount", ce.getAllSeries().size());
                o.put("displayName", cd != null ? cd.displayName : entry.getKey());
                o.put("photoPath",   cd != null && cd.photoPath != null ? cd.photoPath : "");
                arr.put(o);
            }
            File f = new File(catalogDir, INDEX_FILE);
            FileWriter w = new FileWriter(f);
            w.write(arr.toString(2));
            w.close();
        } catch (Exception e) {
            Log.e(TAG, "saveGlobalIndex failed", e);
        }
    }

    public List<VideoItem> getAll() {
        List<VideoItem> all = new ArrayList<VideoItem>();
        for (ChannelEntry ce : channels.values()) all.addAll(ce.getAllVideos());
        return all;
    }

    public List<VideoItem> getByChannel(String folderId) {
        ChannelEntry ce = channels.get(folderId);
        return ce != null ? ce.getAllVideos() : new ArrayList<VideoItem>();
    }

    public VideoItem findById(String id) {
        for (ChannelEntry ce : channels.values()) {
            VideoItem v = ce.findById(id);
            if (v != null) return v;
        }
        return null;
    }

    public ChannelEntry getChannelIndexForVideo(String videoId) {
        for (ChannelEntry ce : channels.values()) {
            if (ce.findById(videoId) != null) return ce;
        }
        return null;
    }

    public Collection<ChannelEntry> getChannels() { return channels.values(); }

    public ChannelEntry getChannel(String folderId) { return channels.get(folderId); }

    public File getRestorerFile(String videoId) {
        ChannelEntry ce = getChannelIndexForVideo(videoId);
        return ce != null ? ce.getRestorerFile(videoId) : null;
    }

    public File ensureRestorerFile(String videoId, String rootPathFallback) {
        ChannelEntry ce = getChannelIndexForVideo(videoId);
        return ce != null ? ce.ensureRestorerFile(videoId) : null;
    }

    public void recordPlay(String id, long watchedMs, long position, long totalDuration) {
        ChannelEntry ce = getChannelIndexForVideo(id);
        if (ce != null) ce.recordPlay(id, watchedMs, position, totalDuration);
    }

    public List<VideoItem> getByType(int type) {
        List<VideoItem> out = new ArrayList<VideoItem>();
        for (VideoItem v : getAll()) if (v.type == type) out.add(v);
        return out;
    }

    public List<VideoItem> getShorts() { return getByType(VideoItem.TYPE_SHORT); }
    public List<VideoItem> getMovies() { return getByType(VideoItem.TYPE_MOVIE); }

    public List<VideoItem> getSeries() {
        List<VideoItem> all = getByType(VideoItem.TYPE_SERIES);
        Collections.sort(all, new Comparator<VideoItem>() {
            @Override public int compare(VideoItem a, VideoItem b) {
                int sn = (a.seriesName == null ? "" : a.seriesName)
                    .compareTo(b.seriesName == null ? "" : b.seriesName);
                if (sn != 0) return sn;
                return Integer.compare(
                    a.episode < 0 ? Integer.MAX_VALUE : a.episode,
                    b.episode < 0 ? Integer.MAX_VALUE : b.episode);
            }
        });
        return all;
    }

    public List<String> getSeriesNames() {
        Set<String> names = new HashSet<String>();
        for (VideoItem v : getAll()) {
            if (v.type == VideoItem.TYPE_SERIES && v.seriesName != null) names.add(v.seriesName);
        }
        List<String> list = new ArrayList<String>(names);
        Collections.sort(list);
        return list;
    }

    public List<VideoItem> getEpisodesOf(String seriesName) {
        if (seriesName == null) return new ArrayList<VideoItem>();
        for (ChannelEntry ce : channels.values()) {
            List<VideoItem> eps = ce.getEpisodesOf(seriesName);
            if (!eps.isEmpty()) return eps;
        }
        List<VideoItem> out = new ArrayList<VideoItem>();
        for (VideoItem v : getAll()) {
            if (v.type == VideoItem.TYPE_SERIES && seriesName.equals(v.seriesName)) out.add(v);
        }
        Collections.sort(out, new Comparator<VideoItem>() {
            @Override public int compare(VideoItem a, VideoItem b) {
                return Integer.compare(
                    a.episode < 0 ? Integer.MAX_VALUE : a.episode,
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
        for (VideoItem v : getAll()) {
            if (v.type != VideoItem.TYPE_SHORT) out.add(v);
            if (out.size() >= max) break;
        }
        return out;
    }

    public List<VideoItem> search(String query) {
        String q = query.toLowerCase().trim();
        List<VideoItem> out = new ArrayList<VideoItem>();
        for (VideoItem v : getAll()) {
            if ((v.title      != null && v.title.toLowerCase().contains(q)) ||
                (v.category   != null && v.category.toLowerCase().contains(q)) ||
                (v.seriesName != null && v.seriesName.toLowerCase().contains(q)) ||
                (v.fileName   != null && v.fileName.toLowerCase().contains(q))) {
                out.add(v);
            }
        }
        return out;
    }

    public void renameChannel(String folderId, String newName) {
        ChannelEntry ce = channels.get(folderId);
        if (ce == null) return;
        ChannelData cd = ce.getChannelData();
        if (cd == null) return;
        cd.displayName = newName;
        cd.save(ce.getChannelDir());
        saveGlobalIndex();
    }

    public void setChannelPhoto(String folderId, String photoPath) {
        ChannelEntry ce = channels.get(folderId);
        if (ce == null) return;
        ChannelData cd = ce.getChannelData();
        if (cd == null) return;
        cd.photoPath = photoPath;
        cd.save(ce.getChannelDir());
        saveGlobalIndex();
    }

    private interface ChannelProgressCallback {
        void onProgress(String fileName, int current, int total);
    }

    public class ChannelEntry {

        private final File        channelDir;
        private final File        thumbDir;
        private final File        restorerDir;
        private       ChannelData channelData;
        private List<VideoItem>   videos   = new ArrayList<VideoItem>();
        private final Map<String, SeriesEntry> seriesMap = new LinkedHashMap<String, SeriesEntry>();

        ChannelEntry(File channelDir) {
            this.channelDir  = channelDir;
            this.thumbDir    = new File(channelDir, THUMB_DIR);
            this.restorerDir = new File(channelDir, RESTORER_DIR);
        }

        void ensureStructure() {
            thumbDir.mkdirs();
            restorerDir.mkdirs();
            new File(channelDir, SHORTS_DIR).mkdirs();
            channelData = ChannelData.load(channelDir);
            if (channelData == null) channelData = ChannelData.createDefault(channelDir);
        }

        void load() {
            File f = new File(channelDir, CINDEX_FILE);
            if (f.exists()) {
                try {
                    byte[] b = readFile(f);
                    JSONArray arr = new JSONArray(new String(b, "UTF-8"));
                    List<VideoItem> loaded = new ArrayList<VideoItem>();
                    for (int i = 0; i < arr.length(); i++) loaded.add(fromJson(arr.getJSONObject(i)));
                    videos = loaded;
                    String displayName = resolveDisplayName();
                    for (VideoItem v : videos) v.category = displayName;
                } catch (Exception e) {
                    Log.e(TAG, "load failed: " + channelDir.getName(), e);
                    videos = new ArrayList<VideoItem>();
                }
            } else {
                videos = new ArrayList<VideoItem>();
            }

            seriesMap.clear();
            File[] subdirs = channelDir.listFiles(new FileFilter() {
                @Override public boolean accept(File file) {
                    return file.isDirectory()
                        && !file.getName().startsWith(".")
                        && !file.getName().equalsIgnoreCase(SHORTS_DIR);
                }
            });
            if (subdirs != null) {
                String displayName  = resolveDisplayName();
                String channelId    = channelDir.getName();
                for (File sub : subdirs) {
                    SeriesEntry se = new SeriesEntry(sub);
                    se.load();
                    for (VideoItem ep : se.getEpisodes()) {
                        ep.channelId = channelId;
                        ep.category  = displayName;
                    }
                    seriesMap.put(sub.getName(), se);
                }
            }
        }

        void save() {
            try {
                JSONArray arr = new JSONArray();
                for (VideoItem v : videos) arr.put(toJson(v));
                File f = new File(channelDir, CINDEX_FILE);
                FileWriter w = new FileWriter(f);
                w.write(arr.toString(2));
                w.close();
            } catch (Exception e) {
                Log.e(TAG, "save failed: " + channelDir.getName(), e);
            }
            for (SeriesEntry se : seriesMap.values()) se.save();
            if (channelData != null) {
                channelData.videoAmount = getAllVideos().size();
                channelData.save(channelDir);
            }
        }

        boolean scanIncremental(ChannelProgressCallback cb) {
            boolean changed = false;
            changed |= scanLooseAndShorts(cb);
            changed |= scanSeriesFolders(cb);
            return changed;
        }

        private boolean scanLooseAndShorts(ChannelProgressCallback cb) {
            List<File>    realFiles  = new ArrayList<File>();
            List<Boolean> realShorts = new ArrayList<Boolean>();

            File[] entries = channelDir.listFiles();
            if (entries != null) {
                for (File f : entries) {
                    if (!f.isDirectory()) {
                        String ext = getExtension(f.getName()).toLowerCase();
                        if (VIDEO_EXTENSIONS.contains(ext)) {
                            realFiles.add(f);
                            realShorts.add(Boolean.FALSE);
                        }
                    }
                }
            }

            File shortsFolder = new File(channelDir, SHORTS_DIR);
            if (shortsFolder.exists() && shortsFolder.isDirectory()) {
                File[] sf = shortsFolder.listFiles();
                if (sf != null) {
                    for (File f : sf) {
                        if (!f.isDirectory()) {
                            String ext = getExtension(f.getName()).toLowerCase();
                            if (VIDEO_EXTENSIONS.contains(ext)) {
                                realFiles.add(f);
                                realShorts.add(Boolean.TRUE);
                            }
                        }
                    }
                }
            }

            Map<String, VideoItem> indexedByPath = new LinkedHashMap<String, VideoItem>();
            for (VideoItem v : videos) indexedByPath.put(v.path, v);

            Set<String> realPaths = new HashSet<String>();
            for (File f : realFiles) realPaths.add(f.getAbsolutePath());

            boolean changed = false;

            Iterator<Map.Entry<String, VideoItem>> it = indexedByPath.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, VideoItem> entry = it.next();
                if (!realPaths.contains(entry.getKey())) {
                    deleteThumb(entry.getValue().thumbPath);
                    deleteRestorerFile(entry.getValue().restorerFile);
                    it.remove();
                    changed = true;
                }
            }

            int idx = 0, total = realFiles.size();
            for (int i = 0; i < realFiles.size(); i++) {
                File    f         = realFiles.get(i);
                boolean shortFlag = realShorts.get(i).booleanValue();
                idx++;
                String    path         = f.getAbsolutePath();
                int       expectedType = shortFlag ? VideoItem.TYPE_SHORT : VideoItem.TYPE_VIDEO;
                VideoItem existing     = indexedByPath.get(path);
                if (existing != null) {
                    if (existing.type != expectedType) {
                        existing.type = expectedType;
                        changed = true;
                    }
                } else {
                    if (cb != null) cb.onProgress(f.getName(), idx, total);
                    VideoItem item = buildVideoItem(f, null, shortFlag);
                    if (item != null) {
                        indexedByPath.put(item.path, item);
                        changed = true;
                    }
                }
            }

            videos = new ArrayList<VideoItem>(indexedByPath.values());
            return changed;
        }

        private boolean scanSeriesFolders(ChannelProgressCallback cb) {
            boolean changed = false;

            File[] subdirs = channelDir.listFiles(new FileFilter() {
                @Override public boolean accept(File f) {
                    return f.isDirectory()
                        && !f.getName().startsWith(".")
                        && !f.getName().equalsIgnoreCase(SHORTS_DIR);
                }
            });
            if (subdirs == null) subdirs = new File[0];

            Set<String> realSeriesNames = new HashSet<String>();
            for (File sub : subdirs) realSeriesNames.add(sub.getName());

            Iterator<String> it = seriesMap.keySet().iterator();
            while (it.hasNext()) {
                if (!realSeriesNames.contains(it.next())) {
                    it.remove();
                    changed = true;
                }
            }

            for (File sub : subdirs) {
                String      seriesName = sub.getName();
                SeriesEntry se         = seriesMap.get(seriesName);
                if (se == null) {
                    se = new SeriesEntry(sub);
                    se.load();
                    seriesMap.put(seriesName, se);
                    changed = true;
                }

                List<File> episodeFiles = collectVideoFilesInDir(sub);
                boolean seriesChanged = se.scanIncremental(episodeFiles, cb);

                if (seriesChanged) {
                    for (VideoItem ep : se.getEpisodes()) {
                        if (ep.thumbPath == null || ep.thumbPath.isEmpty()
                            || ep.id == null || ep.id.isEmpty()) {
                            VideoItem full = buildVideoItem(new File(ep.path), seriesName, false);
                            if (full != null) se.mergeVideoItem(full);
                        }
                    }
                    changed = true;
                }
            }

            return changed;
        }

        public List<VideoItem> getVideos() {
            List<VideoItem> out = new ArrayList<VideoItem>();
            for (VideoItem v : videos) if (v.type != VideoItem.TYPE_SHORT) out.add(v);
            return out;
        }

        public List<VideoItem> getShorts() {
            List<VideoItem> out = new ArrayList<VideoItem>();
            for (VideoItem v : videos) if (v.type == VideoItem.TYPE_SHORT) out.add(v);
            return out;
        }

        public List<VideoItem> getAllVideos() {
            List<VideoItem> all = new ArrayList<VideoItem>(videos);
            for (SeriesEntry se : seriesMap.values()) all.addAll(se.getEpisodes());
            return all;
        }

        public Map<String, SeriesEntry> getSeriesMap()    { return seriesMap; }
        public Collection<SeriesEntry>  getAllSeries()     { return seriesMap.values(); }

        public List<VideoItem> getEpisodesOf(String seriesName) {
            SeriesEntry se = seriesMap.get(seriesName);
            return se != null ? se.getEpisodes() : new ArrayList<VideoItem>();
        }

        public ChannelData getChannelData() { return channelData; }
        public File        getChannelDir()  { return channelDir; }
        public File        getThumbDir()    { return thumbDir; }
        public String      getFolderId()    { return channelDir.getName(); }

        public VideoItem findById(String id) {
            for (VideoItem v : getAllVideos()) if (id != null && id.equals(v.id)) return v;
            return null;
        }

        public File getRestorerFile(String videoId) {
            VideoItem v = findById(videoId);
            if (v == null || v.restorerFile == null) return null;
            return new File(restorerDir, v.restorerFile);
        }

        public File ensureRestorerFile(String videoId) {
            VideoItem v = findById(videoId);
            if (v == null) return null;
            if (v.restorerFile == null || v.restorerFile.isEmpty()) {
                v.restorerFile = RestorerStore.assignFileName();
                save();
            }
            return new File(restorerDir, v.restorerFile);
        }

        public void recordPlay(String id, long watchedMs, long position, long totalDuration) {
            VideoItem v = findById(id);
            if (v == null) return;
            v.playCount++;
            v.totalWatchTime += watchedMs;
            v.lastWatched     = System.currentTimeMillis();
            v.lastPosition    = position;
            if (totalDuration > 0) v.completionRate = (float) position / totalDuration;
            save();
        }

        public boolean setEpisodeNumber(VideoItem item, int newEpisode) {
            if (item == null || item.seriesName == null) return false;
            SeriesEntry se = seriesMap.get(item.seriesName);
            if (se == null) return false;
            item.episode       = newEpisode;
            item.episodeManual = true;
            se.resort();
            save();
            return true;
        }

        public boolean renameVideo(VideoItem item, String newBaseName) {
            if (item == null || item.path == null || newBaseName == null) return false;
            String clean = sanitizeFileName(newBaseName.trim());
            if (clean.isEmpty()) return false;
            File oldFile = new File(item.path);
            if (!oldFile.exists()) return false;
            String ext         = getExtension(oldFile.getName());
            String newFileName = ext.isEmpty() ? clean : clean + "." + ext;
            File   newFile     = new File(oldFile.getParentFile(), newFileName);
            if (!newFile.equals(oldFile) && newFile.exists()) return false;
            if (!oldFile.equals(newFile) && !oldFile.renameTo(newFile)) return false;
            item.path     = newFile.getAbsolutePath();
            item.fileName = newFile.getName();
            item.title    = clean;
            save();
            return true;
        }

        public boolean setTags(VideoItem item, List<String> tags) {
            if (item == null) return false;
            item.tags = tags != null ? new ArrayList<String>(tags) : new ArrayList<String>();
            save();
            return true;
        }

        private VideoItem buildVideoItem(File f, String seriesName, boolean isShort) {
            VideoItem item    = new VideoItem();
            item.path         = f.getAbsolutePath();
            item.fileName     = f.getName();
            item.id           = md5(item.path);
            item.fileSize     = f.length();
            item.dateModified = f.lastModified();
            item.dateAdded    = System.currentTimeMillis();
            item.channelId    = channelDir.getName();
            item.category     = resolveDisplayName();

            if (isShort) {
                item.type = VideoItem.TYPE_SHORT;
            } else if (seriesName != null) {
                item.type       = VideoItem.TYPE_SERIES;
                item.seriesName = seriesName;
            } else {
                item.type = VideoItem.TYPE_VIDEO;
            }

            String base = item.fileName;
            int dot = base.lastIndexOf('.');
            if (dot > 0) base = base.substring(0, dot);
            item.title = base.replace('_', ' ').replace('-', ' ').trim();

            MediaMetadataRetriever mmr = new MediaMetadataRetriever();
            try {
                mmr.setDataSource(item.path);
                String durStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                if (durStr != null) item.duration = Long.parseLong(durStr);
                String wStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
                String hStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
                if (wStr != null) item.width  = Integer.parseInt(wStr);
                if (hStr != null) item.height = Integer.parseInt(hStr);
                item.mimeType  = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE);
                item.thumbPath = generateThumbnail(mmr, item.id);
            } catch (Exception e) {
                Log.w(TAG, "MMR failed for " + item.path + ": " + e.getMessage());
            } finally {
                try { mmr.release(); } catch (Exception ignored) {}
            }

            item.restorerFile = RestorerStore.assignFileName();
            return item;
        }

        private String generateThumbnail(MediaMetadataRetriever mmr, String id) {
            File thumbFile = new File(thumbDir, id + ".jpg");
            if (thumbFile.exists()) return thumbFile.getAbsolutePath();
            try {
                Bitmap bmp = mmr.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                if (bmp == null) return null;

                int rotation = 0;
                try {
                    String r = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
                    if (r != null) rotation = Integer.parseInt(r);
                } catch (Exception ignored) {}

                if (rotation != 0) {
                    android.graphics.Matrix mx = new android.graphics.Matrix();
                    mx.postRotate(rotation);
                    Bitmap rot = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), mx, true);
                    bmp.recycle();
                    bmp = rot;
                }

                int srcW = bmp.getWidth(), srcH = bmp.getHeight();
                int dstW, dstH;
                if (srcW >= srcH) { dstW = 320; dstH = Math.max(1, srcH * 320 / srcW); }
                else              { dstH = 320; dstW = Math.max(1, srcW * 320 / srcH); }

                Bitmap scaled = Bitmap.createScaledBitmap(bmp, dstW, dstH, true);
                FileOutputStream fos = new FileOutputStream(thumbFile);
                scaled.compress(Bitmap.CompressFormat.JPEG, 80, fos);
                fos.close();
                bmp.recycle();
                scaled.recycle();
                return thumbFile.getAbsolutePath();
            } catch (Exception e) {
                Log.w(TAG, "Thumb failed for " + id);
                return null;
            }
        }

        private void deleteThumb(String thumbPath) {
            if (thumbPath != null) { File f = new File(thumbPath); if (f.exists()) f.delete(); }
        }

        private void deleteRestorerFile(String restorerFile) {
            if (restorerFile != null) {
                File f = new File(restorerDir, restorerFile);
                if (f.exists()) f.delete();
            }
        }

        private String resolveDisplayName() {
            return (channelData != null && channelData.displayName != null
                && !channelData.displayName.isEmpty())
                ? channelData.displayName : channelDir.getName();
        }

        private List<File> collectVideoFilesInDir(File dir) {
            List<File> out = new ArrayList<File>();
            File[] files = dir.listFiles();
            if (files == null) return out;
            for (File f : files) {
                if (!f.isDirectory()) {
                    String ext = getExtension(f.getName()).toLowerCase();
                    if (VIDEO_EXTENSIONS.contains(ext)) out.add(f);
                }
            }
            return out;
        }

        private  String sanitizeFileName(String name) {
            return name.replaceAll("[\\\\/:*?\"<>|]", "").trim();
        }

        private JSONObject toJson(VideoItem v) throws Exception {
            JSONObject o = new JSONObject();
            o.put("id",             v.id           == null ? "" : v.id);
            o.put("path",           v.path         == null ? "" : v.path);
            o.put("title",          v.title        == null ? "" : v.title);
            o.put("fileName",       v.fileName     == null ? "" : v.fileName);
            o.put("channelId",      v.channelId    == null ? "" : v.channelId);
            o.put("duration",       v.duration);
            o.put("fileSize",       v.fileSize);
            o.put("width",          v.width);
            o.put("height",         v.height);
            o.put("mimeType",       v.mimeType     == null ? "" : v.mimeType);
            o.put("dateAdded",      v.dateAdded);
            o.put("dateModified",   v.dateModified);
            o.put("category",       v.category     == null ? "" : v.category);
            o.put("type",           v.type);
            o.put("seriesName",     v.seriesName   == null ? "" : v.seriesName);
            o.put("episode",        v.episode);
            o.put("episodeManual",  v.episodeManual);
            JSONArray tagsArr = new JSONArray();
            if (v.tags != null) for (String t : v.tags) tagsArr.put(t);
            o.put("tags",           tagsArr);
            o.put("thumbPath",      v.thumbPath    == null ? "" : v.thumbPath);
            o.put("restorerFile",   v.restorerFile == null ? "" : v.restorerFile);
            o.put("playCount",      v.playCount);
            o.put("totalWatchTime", v.totalWatchTime);
            o.put("lastWatched",    v.lastWatched);
            o.put("lastPosition",   v.lastPosition);
            o.put("completionRate", v.completionRate);
            o.put("score",          v.score);
            o.put("genre",          v.genre        == null ? "" : v.genre);
            return o;
        }

        private VideoItem fromJson(JSONObject o) throws Exception {
            VideoItem v = new VideoItem();
            v.id            = o.optString("id");
            v.path          = o.optString("path");
            v.title         = o.optString("title");
            v.fileName      = o.optString("fileName");
            v.channelId     = o.optString("channelId");
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
            if (v.seriesName  != null && v.seriesName.isEmpty())  v.seriesName  = null;
            v.episode       = o.optInt("episode", -1);
            v.episodeManual = o.optBoolean("episodeManual", false);
            v.tags          = new ArrayList<String>();
            JSONArray tagsArr = o.optJSONArray("tags");
            if (tagsArr != null) {
                for (int i = 0; i < tagsArr.length(); i++) {
                    String t = tagsArr.optString(i, null);
                    if (t != null && !t.isEmpty()) v.tags.add(t);
                }
            }
            v.thumbPath     = o.optString("thumbPath");
            if (v.thumbPath    != null && v.thumbPath.isEmpty())    v.thumbPath    = null;
            v.restorerFile  = o.optString("restorerFile");
            if (v.restorerFile != null && v.restorerFile.isEmpty()) v.restorerFile = null;
            v.playCount      = o.optInt("playCount");
            v.totalWatchTime = o.optLong("totalWatchTime");
            v.lastWatched    = o.optLong("lastWatched");
            v.lastPosition   = o.optLong("lastPosition");
            v.completionRate = (float) o.optDouble("completionRate");
            v.score          = (float) o.optDouble("score");
            v.genre          = o.optString("genre");
            if (v.genre     != null && v.genre.isEmpty())     v.genre     = null;
            if (v.channelId != null && v.channelId.isEmpty()) v.channelId = null;
            return v;
        }
    }

    public class SeriesEntry {

        private final File   seriesDir;
        private final String seriesName;
        private List<VideoItem> episodes = new ArrayList<VideoItem>();

        SeriesEntry(File seriesDir) {
            this.seriesDir  = seriesDir;
            this.seriesName = seriesDir.getName();
        }

        List<VideoItem> load() {
            File f = new File(seriesDir, SINDEX_FILE);
            if (!f.exists()) { episodes = new ArrayList<VideoItem>(); return episodes; }
            try {
                byte[] b = readFile(f);
                JSONArray arr = new JSONArray(new String(b, "UTF-8"));
                List<VideoItem> loaded = new ArrayList<VideoItem>();
                for (int i = 0; i < arr.length(); i++) loaded.add(fromJson(arr.getJSONObject(i)));
                episodes = loaded;
            } catch (Exception e) {
                Log.e(TAG, "SeriesEntry load failed: " + seriesDir.getAbsolutePath(), e);
                episodes = new ArrayList<VideoItem>();
            }
            return episodes;
        }

        void save() {
            try {
                JSONArray arr = new JSONArray();
                for (VideoItem v : episodes) arr.put(toJson(v));
                File f = new File(seriesDir, SINDEX_FILE);
                FileWriter w = new FileWriter(f);
                w.write(arr.toString(2));
                w.close();
            } catch (Exception e) {
                Log.e(TAG, "SeriesEntry save failed: " + seriesDir.getAbsolutePath(), e);
            }
        }

        boolean scanIncremental(List<File> realVideoFiles, ChannelProgressCallback cb) {
            Map<String, VideoItem> indexedByPath = new LinkedHashMap<String, VideoItem>();
            for (VideoItem v : episodes) indexedByPath.put(v.path, v);

            Set<String> realPaths = new HashSet<String>();
            for (File f : realVideoFiles) realPaths.add(f.getAbsolutePath());

            boolean changed = false;

            Iterator<Map.Entry<String, VideoItem>> it = indexedByPath.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, VideoItem> e = it.next();
                if (!realPaths.contains(e.getKey())) {
                    it.remove();
                    changed = true;
                }
            }

            int idx = 0, total = realVideoFiles.size();
            for (File f : realVideoFiles) {
                idx++;
                if (!indexedByPath.containsKey(f.getAbsolutePath())) {
                    if (cb != null) cb.onProgress(f.getName(), idx, total);
                    VideoItem item = buildMinimalItem(f);
                    indexedByPath.put(item.path, item);
                    changed = true;
                }
            }

            List<VideoItem> list = new ArrayList<VideoItem>(indexedByPath.values());
            assignEpisodeNumbers(list);
            Collections.sort(list, new Comparator<VideoItem>() {
                @Override public int compare(VideoItem a, VideoItem b) {
                    int ea = a.episode < 0 ? Integer.MAX_VALUE : a.episode;
                    int eb = b.episode < 0 ? Integer.MAX_VALUE : b.episode;
                    return Integer.compare(ea, eb);
                }
            });
            episodes = list;
            return changed;
        }

        public String          getSeriesName() { return seriesName; }
        public File            getSeriesDir()  { return seriesDir; }
        public List<VideoItem> getEpisodes()   { return episodes; }
        public int             getCount()      { return episodes.size(); }

        public VideoItem getFirstEpisode() {
            return episodes.isEmpty() ? null : episodes.get(0);
        }

        void mergeVideoItem(VideoItem full) {
            for (int i = 0; i < episodes.size(); i++) {
                if (full.path != null && full.path.equals(episodes.get(i).path)) {
                    VideoItem old      = episodes.get(i);
                    full.episode       = old.episode;
                    full.episodeManual = old.episodeManual;
                    full.tags          = old.tags;
                    episodes.set(i, full);
                    return;
                }
            }
            episodes.add(full);
            assignEpisodeNumbers(episodes);
            resort();
        }

        void resort() {
            Collections.sort(episodes, new Comparator<VideoItem>() {
                @Override public int compare(VideoItem a, VideoItem b) {
                    int ea = a.episode < 0 ? Integer.MAX_VALUE : a.episode;
                    int eb = b.episode < 0 ? Integer.MAX_VALUE : b.episode;
                    return Integer.compare(ea, eb);
                }
            });
        }

        private VideoItem buildMinimalItem(File f) {
            VideoItem item    = new VideoItem();
            item.path         = f.getAbsolutePath();
            item.fileName     = f.getName();
            item.fileSize     = f.length();
            item.dateModified = f.lastModified();
            item.type         = VideoItem.TYPE_SERIES;
            item.seriesName   = seriesName;
            String base = item.fileName;
            int d = base.lastIndexOf('.');
            if (d > 0) base = base.substring(0, d);
            item.title = base.replace('_', ' ').replace('-', ' ').trim();
            return item;
        }

        private void assignEpisodeNumbers(List<VideoItem> items) {
            List<VideoItem> withNumber    = new ArrayList<VideoItem>();
            List<VideoItem> withoutNumber = new ArrayList<VideoItem>();
            for (VideoItem v : items) {
                if (v.episodeManual) {
                    if (v.episode < 0) v.episode = 0;
                    withNumber.add(v);
                    continue;
                }
                int num = extractEpisodeNumber(v.fileName != null ? v.fileName : "");
                v.episode = num;
                if (num >= 0) withNumber.add(v);
                else          withoutNumber.add(v);
            }
            Collections.sort(withoutNumber, new Comparator<VideoItem>() {
                @Override public int compare(VideoItem a, VideoItem b) {
                    return Long.compare(a.dateModified, b.dateModified);
                }
            });
            int maxNum = 0;
            for (VideoItem v : withNumber) if (v.episode > maxNum) maxNum = v.episode;
            int seq = maxNum + 1;
            for (VideoItem v : withoutNumber) v.episode = seq++;
        }

        private int extractEpisodeNumber(String fileName) {
            if (fileName == null || fileName.isEmpty()) return -1;
            String name = fileName;
            int dot = name.lastIndexOf('.');
            if (dot > 0) name = name.substring(0, dot);
            name = PARENTHESES.matcher(name).replaceAll(" ");
            Matcher m = DIGIT_RUN.matcher(name);
            while (m.find()) {
                int start = m.start(), end = m.end();
                boolean stuckBefore = start > 0 && Character.isLetter(name.charAt(start - 1));
                boolean stuckAfter  = end < name.length() && Character.isLetter(name.charAt(end));
                if (stuckBefore || stuckAfter) continue;
                try { return Integer.parseInt(m.group()); }
                catch (NumberFormatException ignored) {}
            }
            return -1;
        }

        private JSONObject toJson(VideoItem v) throws Exception {
            JSONObject o = new JSONObject();
            o.put("id",             v.id           == null ? "" : v.id);
            o.put("path",           v.path         == null ? "" : v.path);
            o.put("title",          v.title        == null ? "" : v.title);
            o.put("fileName",       v.fileName     == null ? "" : v.fileName);
            o.put("channelId",      v.channelId    == null ? "" : v.channelId);
            o.put("episode",        v.episode);
            o.put("episodeManual",  v.episodeManual);
            JSONArray tagsArr = new JSONArray();
            if (v.tags != null) for (String t : v.tags) tagsArr.put(t);
            o.put("tags",           tagsArr);
            o.put("seriesName",     seriesName);
            o.put("duration",       v.duration);
            o.put("fileSize",       v.fileSize);
            o.put("width",          v.width);
            o.put("height",         v.height);
            o.put("mimeType",       v.mimeType     == null ? "" : v.mimeType);
            o.put("dateAdded",      v.dateAdded);
            o.put("dateModified",   v.dateModified);
            o.put("thumbPath",      v.thumbPath    == null ? "" : v.thumbPath);
            o.put("restorerFile",   v.restorerFile == null ? "" : v.restorerFile);
            o.put("playCount",      v.playCount);
            o.put("totalWatchTime", v.totalWatchTime);
            o.put("lastWatched",    v.lastWatched);
            o.put("lastPosition",   v.lastPosition);
            o.put("completionRate", v.completionRate);
            o.put("score",          v.score);
            return o;
        }

        private VideoItem fromJson(JSONObject o) throws Exception {
            VideoItem v = new VideoItem();
            v.id            = o.optString("id");
            v.path          = o.optString("path");
            v.title         = o.optString("title");
            v.fileName      = o.optString("fileName");
            v.channelId     = o.optString("channelId");
            v.episode       = o.optInt("episode", -1);
            v.episodeManual = o.optBoolean("episodeManual", false);
            v.tags          = new ArrayList<String>();
            JSONArray tagsArr = o.optJSONArray("tags");
            if (tagsArr != null) {
                for (int i = 0; i < tagsArr.length(); i++) {
                    String t = tagsArr.optString(i, null);
                    if (t != null && !t.isEmpty()) v.tags.add(t);
                }
            }
            v.seriesName    = seriesName;
            v.duration      = o.optLong("duration");
            v.fileSize      = o.optLong("fileSize");
            v.width         = o.optInt("width");
            v.height        = o.optInt("height");
            v.mimeType      = o.optString("mimeType");
            v.dateAdded     = o.optLong("dateAdded");
            v.dateModified  = o.optLong("dateModified");
            v.thumbPath     = o.optString("thumbPath");
            if (v.thumbPath    != null && v.thumbPath.isEmpty())    v.thumbPath    = null;
            v.restorerFile  = o.optString("restorerFile");
            if (v.restorerFile != null && v.restorerFile.isEmpty()) v.restorerFile = null;
            v.playCount      = o.optInt("playCount");
            v.totalWatchTime = o.optLong("totalWatchTime");
            v.lastWatched    = o.optLong("lastWatched");
            v.lastPosition   = o.optLong("lastPosition");
            v.completionRate = (float) o.optDouble("completionRate");
            v.score          = (float) o.optDouble("score");
            v.type           = VideoItem.TYPE_SERIES;
            if (v.channelId != null && v.channelId.isEmpty()) v.channelId = null;
            return v;
        }
    }

    private static String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(input.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) { return String.valueOf(input.hashCode()); }
    }

    private static String getExtension(String name) {
        int i = name.lastIndexOf('.');
        return i >= 0 ? name.substring(i + 1) : "";
    }

    private static byte[] readFile(File f) throws IOException {
        FileInputStream fis = new FileInputStream(f);
        byte[] b = new byte[(int) f.length()];
        fis.read(b);
        fis.close();
        return b;
    }
}
