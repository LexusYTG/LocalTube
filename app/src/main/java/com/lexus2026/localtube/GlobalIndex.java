package com.lexus2026.localtube;

import android.content.*;
import android.os.*;
import android.util.*;
import java.io.*;
import java.util.*;
import org.json.*;

/**
 * GlobalIndex — tabla de contenidos del catálogo.
 *
 * index.json (en rootPath/Catalogo/) contiene un array de entradas:
 * [
 *   { "folderId": "Auronplay", "cIndexPath": "Catalogo/Auronplay/.CIndex.json", "videoAmount": 12 },
 *   ...
 * ]
 *
 * Responsabilidades:
 *  - Detectar canales nuevos/eliminados en rootPath/Catalogo/
 *  - Delegar el escaneo incremental de videos a cada ChannelIndex
 *  - Proveer acceso unificado a todos los VideoItem de todos los canales
 *  - Proveer acceso a ChannelIndex individuales por folderId
 *  - Ofrecer un borrado total de índices + miniaturas (excepto carátulas)
 */
public class GlobalIndex extends VideoIndex {

    private static final String TAG        = "GlobalIndex";
    private static final String CATALOG    = "Catalogo";
    private static final String INDEX_FILE = "index.json";

    private String rootPath;
    private File   catalogDir;

    // Canal folderId → ChannelIndex cargado en memoria
    private final Map<String, ChannelIndex> channels = new LinkedHashMap<String, ChannelIndex>();

    public GlobalIndex(Context context) { super(context); }

    // -------------------------------------------------------------------------
    // Configuración
    // -------------------------------------------------------------------------

    public void setRootPath(String path) {
        this.rootPath   = path;
        this.catalogDir = new File(path, CATALOG);
        catalogDir.mkdirs();
    }

    public String getRootPath() { return rootPath; }
    public File   getCatalogDir() { return catalogDir; }

    // -------------------------------------------------------------------------
    // Carga inicial (rápida, desde disco)
    // -------------------------------------------------------------------------

    /**
     * Lee index.json y carga los ChannelIndex ya existentes en memoria.
     * No escanea archivos nuevos — eso lo hace scanAsync.
     */
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
                if (!channelDir.exists()) continue; // carpeta borrada físicamente

                ChannelIndex ci = new ChannelIndex(channelDir);
                ci.ensureStructure();
                ci.load();
                channels.put(folderId, ci);
            }
        } catch (Exception e) {
            Log.e(TAG, "loadFromDisk failed", e);
        }
        return getAll();
    }

    // -------------------------------------------------------------------------
    // Borrado total de índices + miniaturas (reindexado completo)
    // -------------------------------------------------------------------------

    /**
     * Borra TODOS los archivos de índice del catálogo (.CIndex.json,
     * .SIndex.json, index.json) y todas las miniaturas generadas en las
     * carpetas .miniaturas/ de cada canal, EXCEPTO las carátulas de canal
     * (cuyo path se lee de .Cdata.json).
     *
     * También limpia los archivos .restorer/*.pos, ya que al borrar los
     * índices se pierden los UUIDs que los vinculaban a cada video y
     * quedarían como basura huérfana.
     *
     * El siguiente scanAsync() reconstruye todo desde cero.
     */
    public void wipeAllIndexData() {
        if (catalogDir == null || !catalogDir.exists()) return;

        // 1) index.json global
        File globalIndex = new File(catalogDir, INDEX_FILE);
        if (globalIndex.exists()) globalIndex.delete();

        // 2) Cada canal
        File[] channelDirs = catalogDir.listFiles(new FileFilter() {
				@Override public boolean accept(File f) {
					return f.isDirectory() && !f.getName().startsWith(".");
				}
			});
        if (channelDirs != null) {
            for (File ch : channelDirs) {
                wipeChannelIndexData(ch);
            }
        }

        // 3) Vaciar el mapa en memoria (el siguiente scanAsync lo repoblará)
        channels.clear();
    }

    /** Borra los índices + miniaturas (excepto carátula) + .pos de un canal. */
    private void wipeChannelIndexData(File channelDir) {
        // Leer .Cdata.json ANTES de tocar nada: contiene photoPath con la
        // ruta absoluta de la carátula que debemos preservar.
        ChannelData cd = ChannelData.load(channelDir);
        String preservedPhotoPath = (cd != null
			&& cd.photoPath != null
			&& !cd.photoPath.isEmpty())
			? cd.photoPath : null;

        // — .CIndex.json (índice de videos sueltos + shorts) —
        File cIndex = new File(channelDir, ChannelIndex.FILE_NAME);
        if (cIndex.exists()) cIndex.delete();

        // — .miniaturas/* excepto la carátula —
        File thumbDir = new File(channelDir, ".miniaturas");
        if (thumbDir.exists()) {
            File[] thumbs = thumbDir.listFiles();
            if (thumbs != null) {
                for (File t : thumbs) {
                    if (preservedPhotoPath != null
						&& preservedPhotoPath.equals(t.getAbsolutePath())) {
                        continue; // es la carátula del canal → preservar
                    }
                    t.delete();
                }
            }
        }

        // — .restorer/*.pos (quedarán huérfanos al perder los UUIDs) —
        File restorerDir = new File(channelDir, ".restorer");
        if (restorerDir.exists()) {
            File[] posFiles = restorerDir.listFiles();
            if (posFiles != null) {
                for (File p : posFiles) p.delete();
            }
        }

        // — .SIndex.json de cada serie (excluyendo shorts/ y carpetas ocultas) —
        File[] subdirs = channelDir.listFiles(new FileFilter() {
				@Override public boolean accept(File f) {
					return f.isDirectory()
						&& !f.getName().startsWith(".")
						&& !f.getName().equalsIgnoreCase("shorts");
				}
			});
        if (subdirs != null) {
            for (File sub : subdirs) {
                File sIndex = new File(sub, SeriesIndex.FILE_NAME);
                if (sIndex.exists()) sIndex.delete();
            }
        }
    }

    // -------------------------------------------------------------------------
    // Escaneo incremental asíncrono
    // -------------------------------------------------------------------------

    public interface ScanCallback {
        void onProgress(String currentFile, int done, int total);
        void onComplete(List<VideoItem> videos);
        void onError(String message);
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

                    // --- Paso 1: detectar canales ---
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
                        String id = it.next();
                        if (!realFolderIds.contains(id)) {
                            Log.d(TAG, "Canal eliminado: " + id);
                            it.remove();
                        }
                    }

                    for (File d : subdirs) {
                        String fid = d.getName();
                        if (!channels.containsKey(fid)) {
                            Log.d(TAG, "Canal nuevo: " + fid);
                            ChannelIndex ci = new ChannelIndex(d);
                            ci.ensureStructure();
                            ci.load();
                            channels.put(fid, ci);
                        }
                    }

                    // --- Paso 2: escaneo incremental por canal ---
                    int chanTotal = channels.size();
                    int chanIdx   = 0;
                    for (Map.Entry<String, ChannelIndex> entry : channels.entrySet()) {
                        chanIdx++;
                        final String chanName = entry.getKey();
                        final ChannelIndex ci = entry.getValue();
                        publishProgress(new Object[]{"progress",
                                            "Canal: " + chanName, chanIdx, chanTotal});

                        boolean changed = ci.scanIncremental(new ChannelIndex.ScanProgressCallback() {
								@Override public void onProgress(String fileName, int cur, int tot) {
									publishProgress(new Object[]{"progress",
														chanName + " / " + fileName, cur, tot});
								}
							});

                        if (changed) ci.save();
                    }

                    // --- Paso 3: actualizar index.json global ---
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

    // -------------------------------------------------------------------------
    // Persistencia del index global
    // -------------------------------------------------------------------------

    private void saveGlobalIndex() {
        try {
            JSONArray arr = new JSONArray();
            for (Map.Entry<String, ChannelIndex> entry : channels.entrySet()) {
                ChannelIndex ci = entry.getValue();
                ChannelData  cd = ci.getChannelData();
                JSONObject   o  = new JSONObject();
                o.put("folderId",    entry.getKey());
                o.put("cIndexPath",  CATALOG + "/" + entry.getKey() + "/" + ChannelIndex.FILE_NAME);
                o.put("videoAmount", ci.getAllVideos().size());
                o.put("seriesCount", ci.getAllSeries().size());
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

    // -------------------------------------------------------------------------
    // Acceso a datos
    // -------------------------------------------------------------------------

    public List<VideoItem> getAll() {
        List<VideoItem> all = new ArrayList<VideoItem>();
        for (ChannelIndex ci : channels.values()) all.addAll(ci.getAllVideos());
        return all;
    }

    public List<VideoItem> getByChannel(String folderId) {
        ChannelIndex ci = channels.get(folderId);
        return ci != null ? ci.getAllVideos() : new ArrayList<VideoItem>();
    }

    public VideoItem findById(String id) {
        for (ChannelIndex ci : channels.values()) {
            VideoItem v = ci.findById(id);
            if (v != null) return v;
        }
        return null;
    }

    public ChannelIndex getChannelIndexForVideo(String videoId) {
        for (ChannelIndex ci : channels.values()) {
            if (ci.findById(videoId) != null) return ci;
        }
        return null;
    }

    public Collection<ChannelIndex> getChannels() { return channels.values(); }

    public ChannelIndex getChannel(String folderId) { return channels.get(folderId); }

    // -------------------------------------------------------------------------
    // Delegación de operaciones comunes
    // -------------------------------------------------------------------------

    public File getRestorerFile(String videoId) {
        ChannelIndex ci = getChannelIndexForVideo(videoId);
        return ci != null ? ci.getRestorerFile(videoId) : null;
    }

    public File ensureRestorerFile(String videoId, String rootPathFallback) {
        ChannelIndex ci = getChannelIndexForVideo(videoId);
        return ci != null ? ci.ensureRestorerFile(videoId) : null;
    }

    public void recordPlay(String id, long watchedMs, long position, long totalDuration) {
        ChannelIndex ci = getChannelIndexForVideo(id);
        if (ci != null) ci.recordPlay(id, watchedMs, position, totalDuration);
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
					return Integer.compare(a.episode < 0 ? Integer.MAX_VALUE : a.episode,
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
        for (ChannelIndex ci : channels.values()) {
            List<VideoItem> eps = ci.getEpisodesOf(seriesName);
            if (!eps.isEmpty()) return eps;
        }
        List<VideoItem> out = new ArrayList<VideoItem>();
        for (VideoItem v : getAll()) {
            if (v.type == VideoItem.TYPE_SERIES && seriesName.equals(v.seriesName)) out.add(v);
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

    // -------------------------------------------------------------------------
    // Gestión de canales
    // -------------------------------------------------------------------------

    public void renameChannel(String folderId, String newName) {
        ChannelIndex ci = channels.get(folderId);
        if (ci == null) return;
        ChannelData cd = ci.getChannelData();
        if (cd == null) return;
        cd.displayName = newName;
        cd.save(ci.getChannelDir());
        saveGlobalIndex();
    }

    public void setChannelPhoto(String folderId, String photoPath) {
        ChannelIndex ci = channels.get(folderId);
        if (ci == null) return;
        ChannelData cd = ci.getChannelData();
        if (cd == null) return;
        cd.photoPath = photoPath;
        cd.save(ci.getChannelDir());
        saveGlobalIndex();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static byte[] readFile(File f) throws IOException {
        FileInputStream fis = new FileInputStream(f);
        byte[] b = new byte[(int) f.length()];
        fis.read(b);
        fis.close();
        return b;
    }
}
