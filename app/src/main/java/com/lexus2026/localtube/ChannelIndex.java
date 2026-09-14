package com.lexus2026.localtube;

import android.graphics.*;
import android.media.*;
import android.util.*;
import java.io.*;
import java.security.*;
import java.util.*;
import org.json.*;

/**
 * Index de un canal individual.
 * Vive en rootPath/Catalogo/<NombreCanal>/.CIndex.json
 *
 * Estructura de carpetas soportada:
 *   Catalogo/
 *     MiCanal/
 *       video1.mp4          ← video suelto  (TYPE_VIDEO)
 *       video2.mp4
 *       shorts/             ← videos cortos (TYPE_SHORT). NO se trata como serie.
 *         short1.mp4
 *         short2.mp4
 *       MiSerie/            ← subcarpeta = miniserie (TYPE_SERIES)
 *         cap01.mp4
 *         cap02.mp4
 *         .SIndex.json      ← generado por SeriesIndex
 *       OtraSerie/
 *         ...
 *       .CIndex.json        ← este archivo
 *       .miniaturas/
 *       .restorer/
 *
 * Reglas de clasificación (basadas SOLO en la ubicación del archivo):
 *   - Archivo directamente en la carpeta del canal → TYPE_VIDEO
 *   - Archivo dentro de shorts/                    → TYPE_SHORT
 *   - Archivo dentro de cualquier otra subcarpeta  → episodio de serie (TYPE_SERIES)
 *
 * Ya no se detectan shorts por relación de aspecto / duración. La carpeta manda.
 */
public class ChannelIndex {

    private static final String TAG          = "ChannelIndex";
    static  final String        FILE_NAME    = ".CIndex.json";
    private static final String THUMB_DIR    = ".miniaturas";
    private static final String RESTORER_DIR = ".restorer";
    /** Nombre (case-insensitive) de la subcarpeta reservada para shorts. */
    static  final String        SHORTS_DIR   = "shorts";

    private static final Set<String> VIDEO_EXTENSIONS = new HashSet<String>(Arrays.asList(
																				"mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v", "3gp", "ts", "m2ts"
																			));

    private final File        channelDir;
    private final File        thumbDir;
    private final File        restorerDir;
    private       ChannelData channelData;

    /** Videos sueltos (TYPE_VIDEO) y shorts (TYPE_SHORT), juntos en la misma lista. */
    private List<VideoItem> videos = new ArrayList<VideoItem>();

    /** Series detectadas en subcarpetas: nombre de carpeta → SeriesIndex */
    private final Map<String, SeriesIndex> seriesMap = new LinkedHashMap<String, SeriesIndex>();

    public ChannelIndex(File channelDir) {
        this.channelDir  = channelDir;
        this.thumbDir    = new File(channelDir, THUMB_DIR);
        this.restorerDir = new File(channelDir, RESTORER_DIR);
    }

    // -------------------------------------------------------------------------
    // Inicialización
    // -------------------------------------------------------------------------

    public void ensureStructure() {
        thumbDir.mkdirs();
        restorerDir.mkdirs();
        new File(channelDir, SHORTS_DIR).mkdirs();

        channelData = ChannelData.load(channelDir);
        if (channelData == null) channelData = ChannelData.createDefault(channelDir);
    }

    /** Devuelve true si el directorio es la carpeta de shorts (case-insensitive). */
    private static boolean isShortsFolder(File dir) {
        return dir != null && dir.getName().equalsIgnoreCase(SHORTS_DIR);
    }

    // -------------------------------------------------------------------------
    // Carga
    // -------------------------------------------------------------------------

    public List<VideoItem> load() {
        // Cargar videos sueltos + shorts
        File f = new File(channelDir, FILE_NAME);
        if (f.exists()) {
            try {
                byte[] b = readFile(f);
                JSONArray arr = new JSONArray(new String(b, "UTF-8"));
                List<VideoItem> loaded = new ArrayList<VideoItem>();
                for (int i = 0; i < arr.length(); i++) loaded.add(fromJson(arr.getJSONObject(i)));
                videos = loaded;
                String currentDisplayName = (channelData != null && channelData.displayName != null
                    && !channelData.displayName.isEmpty())
                    ? channelData.displayName : channelDir.getName();
                for (VideoItem v : videos) v.category = currentDisplayName;
            } catch (Exception e) {
                Log.e(TAG, "load failed: " + channelDir.getName(), e);
                videos = new ArrayList<VideoItem>();
            }
        } else {
            videos = new ArrayList<VideoItem>();
        }

        // Cargar series (excluyendo la carpeta shorts)
        seriesMap.clear();
        File[] subdirs = channelDir.listFiles(new FileFilter() {
				@Override public boolean accept(File file) {
					return file.isDirectory()
						&& !file.getName().startsWith(".")
						&& !isShortsFolder(file);
				}
			});
        if (subdirs != null) {
            String currentDisplayName = (channelData != null && channelData.displayName != null
                && !channelData.displayName.isEmpty())
                ? channelData.displayName : channelDir.getName();
            String currentChannelId = channelDir.getName();
            for (File sub : subdirs) {
                SeriesIndex si = new SeriesIndex(sub);
                si.load();
                for (VideoItem ep : si.getEpisodes()) {
                    ep.channelId = currentChannelId;
                    ep.category  = currentDisplayName;
                }
                seriesMap.put(sub.getName(), si);
                Log.d(TAG, "Loaded series: " + sub.getName()
                      + " (" + si.getCount() + " ep.)");
            }
        }

        return getAllVideos();
    }

    // -------------------------------------------------------------------------
    // Guardado
    // -------------------------------------------------------------------------

    public void save() {
        // Videos sueltos + shorts → .CIndex.json
        try {
            JSONArray arr = new JSONArray();
            for (VideoItem v : videos) arr.put(toJson(v));
            File f = new File(channelDir, FILE_NAME);
            FileWriter w = new FileWriter(f);
            w.write(arr.toString(2));
            w.close();
        } catch (Exception e) {
            Log.e(TAG, "save (videos) failed: " + channelDir.getName(), e);
        }

        for (SeriesIndex si : seriesMap.values()) {
            si.save();
        }

        if (channelData != null) {
            channelData.videoAmount = getAllVideos().size();
            channelData.save(channelDir);
        }
    }

    // -------------------------------------------------------------------------
    // Escaneo incremental
    // -------------------------------------------------------------------------

    public boolean scanIncremental(ScanProgressCallback progressCallback) {
        boolean changed = false;
        changed |= scanLooseAndShorts(progressCallback);
        changed |= scanSeriesFolders(progressCallback);
        return changed;
    }

    /**
     * Escanea la raíz del canal y la subcarpeta shorts/.
     *   - Archivos en la raíz       → TYPE_VIDEO
     *   - Archivos en shorts/       → TYPE_SHORT
     * Los items existentes en el índice se conservan (con su playCount, etc.) y
     * solo se corrige su `type` si cambió de carpeta.
     */
    private boolean scanLooseAndShorts(ScanProgressCallback cb) {
        // Recolectar archivos reales + tipo esperado
        List<File>    realFiles = new ArrayList<File>();
        List<Boolean> realShort = new ArrayList<Boolean>();

        // Raíz del canal → videos normales
        File[] entries = channelDir.listFiles();
        if (entries != null) {
            for (File f : entries) {
                if (!f.isDirectory()) {
                    String ext = getExtension(f.getName()).toLowerCase();
                    if (VIDEO_EXTENSIONS.contains(ext)) {
                        realFiles.add(f);
                        realShort.add(Boolean.FALSE);
                    }
                }
            }
        }

        // Subcarpeta shorts/ → shorts
        File shortsDir = new File(channelDir, SHORTS_DIR);
        if (shortsDir.exists() && shortsDir.isDirectory()) {
            File[] shortsFiles = shortsDir.listFiles();
            if (shortsFiles != null) {
                for (File f : shortsFiles) {
                    if (!f.isDirectory()) {
                        String ext = getExtension(f.getName()).toLowerCase();
                        if (VIDEO_EXTENSIONS.contains(ext)) {
                            realFiles.add(f);
                            realShort.add(Boolean.TRUE);
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

        // Quitar eliminados
        Iterator<Map.Entry<String, VideoItem>> it = indexedByPath.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, VideoItem> entry = it.next();
            if (!realPaths.contains(entry.getKey())) {
                deleteThumb(entry.getValue().thumbPath);
                deleteRestorerFile(entry.getValue().restorerFile);
                it.remove();
                changed = true;
                Log.d(TAG, "Removed loose: " + entry.getKey());
            }
        }

        // Agregar nuevos / corregir tipos
        int idx = 0, total = realFiles.size();
        for (int i = 0; i < realFiles.size(); i++) {
            File f = realFiles.get(i);
            boolean shortFlag = realShort.get(i).booleanValue();
            idx++;
            String path = f.getAbsolutePath();
            int expectedType = shortFlag ? VideoItem.TYPE_SHORT : VideoItem.TYPE_VIDEO;
            VideoItem existing = indexedByPath.get(path);

            if (existing != null) {
                if (existing.type != expectedType) {
                    existing.type = expectedType;
                    changed = true;
                    Log.d(TAG, "Type corrected: " + f.getName()
                          + " → " + (shortFlag ? "SHORT" : "VIDEO"));
                }
            } else {
                if (cb != null) cb.onProgress(f.getName(), idx, total);
                VideoItem item = buildVideoItem(f, null, shortFlag);
                if (item != null) {
                    indexedByPath.put(item.path, item);
                    changed = true;
                    Log.d(TAG, "Added " + (shortFlag ? "short: " : "loose: ") + f.getName());
                }
            }
        }

        videos = new ArrayList<VideoItem>(indexedByPath.values());
        return changed;
    }

    /**
     * Detecta subcarpetas de series (excluyendo shorts/), crea/actualiza
     * SeriesIndex para cada una y construye los VideoItem completos para
     * cada episodio nuevo.
     */
    private boolean scanSeriesFolders(ScanProgressCallback cb) {
        boolean changed = false;

        File[] subdirs = channelDir.listFiles(new FileFilter() {
				@Override public boolean accept(File f) {
					return f.isDirectory()
						&& !f.getName().startsWith(".")
						&& !isShortsFolder(f);
				}
			});
        if (subdirs == null) subdirs = new File[0];

        Set<String> realSeriesNames = new HashSet<String>();
        for (File sub : subdirs) realSeriesNames.add(sub.getName());

        // Quitar series cuya carpeta ya no existe
        Iterator<String> it = seriesMap.keySet().iterator();
        while (it.hasNext()) {
            String name = it.next();
            if (!realSeriesNames.contains(name)) {
                it.remove();
                changed = true;
                Log.d(TAG, "Series removed: " + name);
            }
        }

        // Procesar cada subcarpeta
        for (File sub : subdirs) {
            String seriesName = sub.getName();
            SeriesIndex si = seriesMap.get(seriesName);
            if (si == null) {
                si = new SeriesIndex(sub);
                si.load();
                seriesMap.put(seriesName, si);
                changed = true;
                Log.d(TAG, "Series new: " + seriesName);
            }

            List<File> episodeFiles = collectVideoFilesInDir(sub);
            boolean seriesChanged = si.scanIncremental(episodeFiles, cb);

            if (seriesChanged) {
                for (VideoItem ep : si.getEpisodes()) {
                    if (ep.thumbPath == null || ep.thumbPath.isEmpty()
                        || ep.id == null || ep.id.isEmpty()) {
                        VideoItem full = buildVideoItem(new File(ep.path), seriesName, false);
                        if (full != null) si.mergeVideoItem(full);
                    }
                }
                changed = true;
            }
        }

        return changed;
    }

    // -------------------------------------------------------------------------
    // Acceso a datos
    // -------------------------------------------------------------------------

    /** Videos sueltos del canal (NO incluye shorts ni episodios de series). */
    public List<VideoItem> getVideos() {
        List<VideoItem> out = new ArrayList<VideoItem>();
        for (VideoItem v : videos) {
            if (v.type != VideoItem.TYPE_SHORT) out.add(v);
        }
        return out;
    }

    /** Shorts del canal (videos que están dentro de la carpeta shorts/). */
    public List<VideoItem> getShorts() {
        List<VideoItem> out = new ArrayList<VideoItem>();
        for (VideoItem v : videos) {
            if (v.type == VideoItem.TYPE_SHORT) out.add(v);
        }
        return out;
    }

    /** Todos los VideoItem del canal: videos sueltos + shorts + episodios de todas las series. */
    public List<VideoItem> getAllVideos() {
        List<VideoItem> all = new ArrayList<VideoItem>(videos);
        for (SeriesIndex si : seriesMap.values()) all.addAll(si.getEpisodes());
        return all;
    }

    public Map<String, SeriesIndex> getSeriesMap() { return seriesMap; }

    public Collection<SeriesIndex> getAllSeries() { return seriesMap.values(); }

    public List<VideoItem> getEpisodesOf(String seriesName) {
        SeriesIndex si = seriesMap.get(seriesName);
        return si != null ? si.getEpisodes() : new ArrayList<VideoItem>();
    }

    public ChannelData getChannelData() { return channelData; }
    public File        getChannelDir()  { return channelDir; }
    public File        getThumbDir()    { return thumbDir; }
    public String      getFolderId()    { return channelDir.getName(); }

    public VideoItem findById(String id) {
        for (VideoItem v : getAllVideos())
            if (id != null && id.equals(v.id)) return v;
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

    // -------------------------------------------------------------------------
    // Edición manual
    // -------------------------------------------------------------------------

    public boolean setEpisodeNumber(VideoItem item, int newEpisode) {
        if (item == null || item.seriesName == null) return false;
        SeriesIndex si = seriesMap.get(item.seriesName);
        if (si == null) return false;

        item.episode       = newEpisode;
        item.episodeManual = true;
        si.resort();
        save();
        return true;
    }

    public boolean renameVideo(VideoItem item, String newBaseName) {
        if (item == null || item.path == null || newBaseName == null) return false;

        String clean = sanitizeFileName(newBaseName.trim());
        if (clean.isEmpty()) return false;

        File oldFile = new File(item.path);
        if (!oldFile.exists()) return false;

        String ext = getExtension(oldFile.getName());
        String newFileName = ext.isEmpty() ? clean : clean + "." + ext;
        File newFile = new File(oldFile.getParentFile(), newFileName);

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

    private static String sanitizeFileName(String name) {
        return name.replaceAll("[\\\\/:*?\"<>|]", "").trim();
    }

    // -------------------------------------------------------------------------
    // Construcción de VideoItem
    // -------------------------------------------------------------------------

    /**
     * @param f          archivo de video
     * @param seriesName null = video suelto o short; nombre = episodio de serie
     * @param isShort    true = marcar como TYPE_SHORT (video dentro de shorts/)
     */
    private VideoItem buildVideoItem(File f, String seriesName, boolean isShort) {
        VideoItem item    = new VideoItem();
        item.path         = f.getAbsolutePath();
        item.fileName     = f.getName();
        item.id           = md5(item.path);
        item.fileSize     = f.length();
        item.dateModified = f.lastModified();
        item.dateAdded    = System.currentTimeMillis();
        item.channelId    = channelDir.getName();
        item.category     = (channelData != null && channelData.displayName != null
            && !channelData.displayName.isEmpty())
            ? channelData.displayName
            : channelDir.getName();

        if (isShort) {
            item.type = VideoItem.TYPE_SHORT;
        } else if (seriesName != null) {
            item.type       = VideoItem.TYPE_SERIES;
            item.seriesName = seriesName;
        } else {
            item.type = VideoItem.TYPE_VIDEO;
        }

        // Título desde nombre de archivo
        String base = item.fileName;
        int dot = base.lastIndexOf('.');
        if (dot > 0) base = base.substring(0, dot);
        item.title = base.replace('_', ' ').replace('-', ' ').trim();

        // Metadatos multimedia
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

            // El tipo NO se toca aquí: depende exclusivamente de la carpeta.
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
            Bitmap bmp = mmr.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
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
                bmp.recycle(); bmp = rot;
            }

            int srcW = bmp.getWidth(), srcH = bmp.getHeight();
            int dstW, dstH;
            if (srcW >= srcH) { dstW = 320; dstH = Math.max(1, srcH * 320 / srcW); }
            else              { dstH = 320; dstW = Math.max(1, srcW * 320 / srcH); }

            Bitmap scaled = Bitmap.createScaledBitmap(bmp, dstW, dstH, true);
            FileOutputStream fos = new FileOutputStream(thumbFile);
            scaled.compress(Bitmap.CompressFormat.JPEG, 80, fos);
            fos.close();
            bmp.recycle(); scaled.recycle();
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

    // -------------------------------------------------------------------------
    // Recolección de archivos de video
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // JSON serialización
    // -------------------------------------------------------------------------

    private JSONObject toJson(VideoItem v) throws Exception {
        JSONObject o = new JSONObject();
        o.put("id",             v.id            == null ? "" : v.id);
        o.put("path",           v.path          == null ? "" : v.path);
        o.put("title",          v.title         == null ? "" : v.title);
        o.put("fileName",       v.fileName      == null ? "" : v.fileName);
        o.put("channelId",      v.channelId     == null ? "" : v.channelId);
        o.put("duration",       v.duration);
        o.put("fileSize",       v.fileSize);
        o.put("width",          v.width);
        o.put("height",         v.height);
        o.put("mimeType",       v.mimeType      == null ? "" : v.mimeType);
        o.put("dateAdded",      v.dateAdded);
        o.put("dateModified",   v.dateModified);
        o.put("category",       v.category      == null ? "" : v.category);
        o.put("type",           v.type);
        o.put("seriesName",     v.seriesName    == null ? "" : v.seriesName);
        o.put("episode",        v.episode);
        o.put("episodeManual",  v.episodeManual);
        JSONArray tagsArr = new JSONArray();
        if (v.tags != null) for (String t : v.tags) tagsArr.put(t);
        o.put("tags",           tagsArr);
        o.put("thumbPath",      v.thumbPath     == null ? "" : v.thumbPath);
        o.put("restorerFile",   v.restorerFile  == null ? "" : v.restorerFile);
        o.put("playCount",      v.playCount);
        o.put("totalWatchTime", v.totalWatchTime);
        o.put("lastWatched",    v.lastWatched);
        o.put("lastPosition",   v.lastPosition);
        o.put("completionRate", v.completionRate);
        o.put("score",          v.score);
        o.put("genre",          v.genre         == null ? "" : v.genre);
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
        if (v.seriesName != null && v.seriesName.isEmpty()) v.seriesName = null;
        v.episode       = o.optInt("episode", -1);
        v.episodeManual = o.optBoolean("episodeManual", false);
        v.tags = new ArrayList<String>();
        JSONArray tagsArr = o.optJSONArray("tags");
        if (tagsArr != null) {
            for (int i = 0; i < tagsArr.length(); i++) {
                String t = tagsArr.optString(i, null);
                if (t != null && !t.isEmpty()) v.tags.add(t);
            }
        }
        v.thumbPath     = o.optString("thumbPath");
        if (v.thumbPath != null && v.thumbPath.isEmpty()) v.thumbPath = null;
        v.restorerFile  = o.optString("restorerFile");
        if (v.restorerFile != null && v.restorerFile.isEmpty()) v.restorerFile = null;
        v.playCount      = o.optInt("playCount");
        v.totalWatchTime = o.optLong("totalWatchTime");
        v.lastWatched    = o.optLong("lastWatched");
        v.lastPosition   = o.optLong("lastPosition");
        v.completionRate = (float) o.optDouble("completionRate");
        v.score          = (float) o.optDouble("score");
        v.genre          = o.optString("genre");
        if (v.genre    != null && v.genre.isEmpty())    v.genre    = null;
        if (v.channelId != null && v.channelId.isEmpty()) v.channelId = null;
        return v;
    }

    // -------------------------------------------------------------------------
    // Utilidades
    // -------------------------------------------------------------------------

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

    public interface ScanProgressCallback {
        void onProgress(String fileName, int current, int total);
    }
}
