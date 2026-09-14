package com.lexus2026.localtube;

import android.util.*;
import java.io.*;
import java.util.*;
import java.util.regex.*;
import org.json.*;

/**
 * Índice de una miniserie individual.
 * Vive en rootPath/Catalogo/<Canal>/<NombreSerie>/.SIndex.json
 *
 * Responsabilidades:
 *  - Mantener la lista ordenada de capítulos (VideoItem) de la serie
 *  - Determinar el número de episodio de cada capítulo:
 *      1) Extrae números del nombre de archivo (descartando contenido entre paréntesis)
 *      2) Si no hay número, ordena por fecha de creación/modificación del archivo
 *  - Generar y persistir el archivo .SIndex.json
 *  - Ser referenciado desde .CIndex.json del canal padre
 */
public class SeriesIndex {

    private static final String TAG       = "SeriesIndex";
    public  static final String FILE_NAME = ".SIndex.json";

    /** Patrón que elimina todo lo que esté dentro de paréntesis, incluyendo los paréntesis */
    private static final Pattern PARENTHESES = Pattern.compile("\\([^)]*\\)");

    /** Patrón que encuentra cada bloque de dígitos consecutivos del nombre de archivo */
    private static final Pattern DIGIT_RUN = Pattern.compile("\\d+");

    private final File   seriesDir;   // carpeta de la serie dentro del canal
    private final String seriesName;  // nombre legible = nombre de la carpeta
    private List<VideoItem> episodes  = new ArrayList<VideoItem>();

    public SeriesIndex(File seriesDir) {
        this.seriesDir  = seriesDir;
        this.seriesName = seriesDir.getName();
    }

    // -------------------------------------------------------------------------
    // Carga y guardado
    // -------------------------------------------------------------------------

    /** Carga .SIndex.json si existe. */
    public List<VideoItem> load() {
        File f = new File(seriesDir, FILE_NAME);
        if (!f.exists()) { episodes = new ArrayList<VideoItem>(); return episodes; }
        try {
            byte[] b = readFile(f);
            JSONArray arr = new JSONArray(new String(b, "UTF-8"));
            List<VideoItem> loaded = new ArrayList<VideoItem>();
            for (int i = 0; i < arr.length(); i++) loaded.add(fromJson(arr.getJSONObject(i)));
            episodes = loaded;
        } catch (Exception e) {
            Log.e(TAG, "load failed: " + seriesDir.getAbsolutePath(), e);
            episodes = new ArrayList<VideoItem>();
        }
        return episodes;
    }

    /** Persiste .SIndex.json con los episodios ya ordenados. */
    public void save() {
        try {
            JSONArray arr = new JSONArray();
            for (VideoItem v : episodes) arr.put(toJson(v));
            File f = new File(seriesDir, FILE_NAME);
            FileWriter w = new FileWriter(f);
            w.write(arr.toString(2));
            w.close();
        } catch (Exception e) {
            Log.e(TAG, "save failed: " + seriesDir.getAbsolutePath(), e);
        }
    }

    // -------------------------------------------------------------------------
    // Escaneo incremental
    // -------------------------------------------------------------------------

    /**
     * Compara los episodios indexados contra los archivos reales en la carpeta.
     * Agrega nuevos, elimina los borrados, reasigna números de episodio y reordena.
     *
     * @return true si hubo algún cambio
     */
    public boolean scanIncremental(List<File> realVideoFiles,
                                   ChannelIndex.ScanProgressCallback cb) {
        // Mapa de episodios ya indexados: path → VideoItem
        Map<String, VideoItem> indexedByPath = new LinkedHashMap<String, VideoItem>();
        for (VideoItem v : episodes) indexedByPath.put(v.path, v);

        Set<String> realPaths = new HashSet<String>();
        for (File f : realVideoFiles) realPaths.add(f.getAbsolutePath());

        boolean changed = false;

        // Quitar eliminados
        Iterator<Map.Entry<String, VideoItem>> it = indexedByPath.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, VideoItem> e = it.next();
            if (!realPaths.contains(e.getKey())) {
                it.remove();
                changed = true;
                Log.d(TAG, "[" + seriesName + "] Removed: " + e.getKey());
            }
        }

        // Agregar nuevos (thumbnail / metadata ya delegados al ChannelIndex)
        int idx = 0, total = realVideoFiles.size();
        for (File f : realVideoFiles) {
            idx++;
            if (!indexedByPath.containsKey(f.getAbsolutePath())) {
                if (cb != null) cb.onProgress(f.getName(), idx, total);
                // El VideoItem base ya lo construyó ChannelIndex; aquí lo creamos
                // con los datos mínimos que SeriesIndex necesita para ordenar.
                VideoItem item = buildMinimalItem(f);
                indexedByPath.put(item.path, item);
                changed = true;
                Log.d(TAG, "[" + seriesName + "] Added: " + f.getName());
            }
        }

        // Reasignar números de episodio y ordenar
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

    // -------------------------------------------------------------------------
    // Detección de número de episodio
    // -------------------------------------------------------------------------

    /**
     * Para cada VideoItem de la lista (que no tenga episodeManual = true):
     *  1) Limpia el nombre de archivo: elimina extensión y todo lo que esté entre paréntesis.
     *  2) Busca el primer bloque de dígitos que no esté pegado a letras → número de episodio.
     *  3) Si no se encontró ningún número válido, deja episode = -1 (se ordena por fecha al final).
     * Los VideoItem con episodeManual = true conservan su número tal cual, sin recalcularlo.
     * Luego, los que quedaron con episode = -1 se ordenan entre sí por dateModified (fecha
     * de creación/modificación del archivo, que MediaMetadataRetriever preserva).
     */
    static void assignEpisodeNumbers(List<VideoItem> items) {
        List<VideoItem> withNumber    = new ArrayList<VideoItem>();
        List<VideoItem> withoutNumber = new ArrayList<VideoItem>();

        for (VideoItem v : items) {
            if (v.episodeManual) {
                // El usuario fijó el capítulo a mano: no se recalcula desde el nombre.
                if (v.episode < 0) v.episode = 0;
                withNumber.add(v);
                continue;
            }
            int num = extractEpisodeNumber(v.fileName != null ? v.fileName : "");
            v.episode = num;
            if (num >= 0) withNumber.add(v);
            else          withoutNumber.add(v);
        }

        // Los sin número: ordenar por fecha de modificación del archivo
        Collections.sort(withoutNumber, new Comparator<VideoItem>() {
				@Override public int compare(VideoItem a, VideoItem b) {
					return Long.compare(a.dateModified, b.dateModified);
				}
			});

        // Asignar números secuenciales a los sin número, continuando tras el máximo
        int maxNum = 0;
        for (VideoItem v : withNumber) if (v.episode > maxNum) maxNum = v.episode;

        int seq = maxNum + 1;
        for (VideoItem v : withoutNumber) {
            v.episode = seq++;
        }
    }

    /**
     * Extrae el número de episodio del nombre de archivo.
     *
     * Algoritmo:
     *  1) Quitar extensión (.mp4, .mkv, etc.)
     *  2) Eliminar todo lo que esté entre paréntesis  →  "Capitulo 03 (360p)" → "Capitulo 03 "
     *  3) Recorrer cada bloque de dígitos consecutivos del nombre restante y quedarse
     *     con el PRIMERO que no esté pegado a una letra (ni antes ni después).
     *
     *     Esto descarta números "pegados" a texto, como el "0" de "PIST0LA" (censura
     *     típica de YouTube para esquivar palabras baneadas: T0 y 0L son letra+dígito
     *     pegados, así que ese bloque se ignora y se sigue buscando).
     *
     *     En cambio, un número pegado a un símbolo (no letra) sí es válido, p.ej.
     *     "Capitulo_1" o "1_Capitulo": "_" no es letra, así que "1" se acepta.
     *
     * @return número ≥ 0, o -1 si no se encontró ningún bloque de dígitos válido
     */
    static int extractEpisodeNumber(String fileName) {
        if (fileName == null || fileName.isEmpty()) return -1;

        // 1) Quitar extensión
        String name = fileName;
        int dot = name.lastIndexOf('.');
        if (dot > 0) name = name.substring(0, dot);

        // 2) Eliminar contenido entre paréntesis (p.ej. calidades de SnapTuber)
        name = PARENTHESES.matcher(name).replaceAll(" ");

        // 3) Buscar el primer bloque de dígitos que no esté pegado a letras
        Matcher m = DIGIT_RUN.matcher(name);
        while (m.find()) {
            int start = m.start();
            int end   = m.end();

            boolean stuckToLetterBefore = start > 0 && Character.isLetter(name.charAt(start - 1));
            boolean stuckToLetterAfter  = end < name.length() && Character.isLetter(name.charAt(end));

            if (stuckToLetterBefore || stuckToLetterAfter) {
                continue; // número pegado a texto (censura tipo PIST0LA): se descarta
            }

            try { return Integer.parseInt(m.group()); }
            catch (NumberFormatException ignored) {}
        }

        return -1;
    }

    // -------------------------------------------------------------------------
    // Acceso a datos
    // -------------------------------------------------------------------------

    public String          getSeriesName() { return seriesName; }
    public File            getSeriesDir()  { return seriesDir; }
    public List<VideoItem> getEpisodes()   { return episodes; }
    public int             getCount()      { return episodes.size(); }

    /** Primer episodio (capítulo 1), usado para la carátula de la serie. */
    public VideoItem getFirstEpisode() {
        return episodes.isEmpty() ? null : episodes.get(0);
    }

    /**
     * Actualiza un VideoItem completo (construido por ChannelIndex con thumbnail, etc.)
     * en la lista de episodios, buscándolo por path.
     */
    public void mergeVideoItem(VideoItem full) {
        for (int i = 0; i < episodes.size(); i++) {
            if (full.path != null && full.path.equals(episodes.get(i).path)) {
                // Conservar el número de episodio, el flag manual y los tags ya asignados
                VideoItem old = episodes.get(i);
                full.episode       = old.episode;
                full.episodeManual = old.episodeManual;
                full.tags          = old.tags;
                episodes.set(i, full);
                return;
            }
        }
        // No estaba: agregar y reasignar
        episodes.add(full);
        assignEpisodeNumbers(episodes);
        resort();
    }

    /** Reordena los episodios según su campo `episode` actual. Útil tras un cambio manual. */
    public void resort() {
        Collections.sort(episodes, new Comparator<VideoItem>() {
				@Override public int compare(VideoItem a, VideoItem b) {
					int ea = a.episode < 0 ? Integer.MAX_VALUE : a.episode;
					int eb = b.episode < 0 ? Integer.MAX_VALUE : b.episode;
					return Integer.compare(ea, eb);
				}
			});
    }

    // -------------------------------------------------------------------------
    // Construcción mínima de VideoItem (solo para ordenar; el ChannelIndex
    // construye el completo con thumbnail y metadatos multimedia)
    // -------------------------------------------------------------------------

    private VideoItem buildMinimalItem(File f) {
        VideoItem item  = new VideoItem();
        item.path       = f.getAbsolutePath();
        item.fileName   = f.getName();
        item.fileSize   = f.length();
        item.dateModified = f.lastModified();
        item.type       = VideoItem.TYPE_SERIES;
        item.seriesName = seriesName;

        String base = item.fileName;
        dot: {
            int d = base.lastIndexOf('.');
            if (d > 0) base = base.substring(0, d);
        }
        item.title = base.replace('_', ' ').replace('-', ' ').trim();
        return item;
    }

    // -------------------------------------------------------------------------
    // JSON
    // -------------------------------------------------------------------------

    private JSONObject toJson(VideoItem v) throws Exception {
        JSONObject o = new JSONObject();
        o.put("id",           v.id           == null ? "" : v.id);
        o.put("path",         v.path         == null ? "" : v.path);
        o.put("title",        v.title        == null ? "" : v.title);
        o.put("fileName",     v.fileName     == null ? "" : v.fileName);
        o.put("channelId",    v.channelId    == null ? "" : v.channelId);
        o.put("episode",      v.episode);
        o.put("episodeManual", v.episodeManual);
        JSONArray tagsArr = new JSONArray();
        if (v.tags != null) for (String t : v.tags) tagsArr.put(t);
        o.put("tags",         tagsArr);
        o.put("seriesName",   seriesName);
        o.put("duration",     v.duration);
        o.put("fileSize",     v.fileSize);
        o.put("width",        v.width);
        o.put("height",       v.height);
        o.put("mimeType",     v.mimeType     == null ? "" : v.mimeType);
        o.put("dateAdded",    v.dateAdded);
        o.put("dateModified", v.dateModified);
        o.put("thumbPath",    v.thumbPath    == null ? "" : v.thumbPath);
        o.put("restorerFile", v.restorerFile == null ? "" : v.restorerFile);
        o.put("playCount",    v.playCount);
        o.put("totalWatchTime", v.totalWatchTime);
        o.put("lastWatched",  v.lastWatched);
        o.put("lastPosition", v.lastPosition);
        o.put("completionRate", v.completionRate);
        o.put("score",        v.score);
        return o;
    }

    private VideoItem fromJson(JSONObject o) throws Exception {
        VideoItem v = new VideoItem();
        v.id           = o.optString("id");
        v.path         = o.optString("path");
        v.title        = o.optString("title");
        v.fileName     = o.optString("fileName");
        v.channelId    = o.optString("channelId");
        v.episode      = o.optInt("episode", -1);
        v.episodeManual = o.optBoolean("episodeManual", false);
        v.tags = new ArrayList<String>();
        JSONArray tagsArr = o.optJSONArray("tags");
        if (tagsArr != null) {
            for (int i = 0; i < tagsArr.length(); i++) {
                String t = tagsArr.optString(i, null);
                if (t != null && !t.isEmpty()) v.tags.add(t);
            }
        }
        v.seriesName   = seriesName;
        v.duration     = o.optLong("duration");
        v.fileSize     = o.optLong("fileSize");
        v.width        = o.optInt("width");
        v.height       = o.optInt("height");
        v.mimeType     = o.optString("mimeType");
        v.dateAdded    = o.optLong("dateAdded");
        v.dateModified = o.optLong("dateModified");
        v.thumbPath    = o.optString("thumbPath");
        if (v.thumbPath != null && v.thumbPath.isEmpty()) v.thumbPath = null;
        v.restorerFile = o.optString("restorerFile");
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

    // -------------------------------------------------------------------------
    // Utilidades
    // -------------------------------------------------------------------------

    private static byte[] readFile(File f) throws IOException {
        FileInputStream fis = new FileInputStream(f);
        byte[] b = new byte[(int) f.length()];
        fis.read(b);
        fis.close();
        return b;
    }
}

