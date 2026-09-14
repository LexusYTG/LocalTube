package com.lexus2026.localtube;

import android.util.*;
import java.io.*;
import org.json.*;

/**
 * Representa los metadatos de un canal.
 * Vive en rootPath/Catalogo/<NombreCanal>/.Cdata.json
 *
 * Campos:
 *   folderId    — nombre exacto de la carpeta del canal (clave de enlace en GlobalIndex)
 *   displayName — nombre visible que el usuario puede cambiar desde ajustes
 *   photoPath   — ruta a la imagen del canal (guardada como canal_photo.jpg en /.miniaturas/)
 *   videoAmount — cantidad de videos declarados en .CIndex.json (usado para escaneo incremental)
 */
public class ChannelData {

    private static final String TAG       = "ChannelData";
    static final String         FILE_NAME = ".Cdata.json";

    public String folderId;       // nombre de carpeta, inmutable
    public String displayName;    // nombre editable por el usuario
    public String photoPath;      // ruta a canal_photo.jpg, puede ser null
    public int    videoAmount;    // conteo declarado de videos

    public ChannelData() {}

    // -------------------------------------------------------------------------
    // Persistencia
    // -------------------------------------------------------------------------

    /** Lee .Cdata.json desde la carpeta del canal. Devuelve null si no existe o falla. */
    public static ChannelData load(File channelDir) {
        File f = new File(channelDir, FILE_NAME);
        if (!f.exists()) return null;
        try {
            byte[] b = readFile(f);
            JSONObject o = new JSONObject(new String(b, "UTF-8"));
            ChannelData cd = new ChannelData();
            cd.folderId    = o.optString("folderId");
            cd.displayName = o.optString("displayName");
            cd.photoPath   = o.optString("photoPath");
            if (cd.photoPath != null && cd.photoPath.isEmpty()) cd.photoPath = null;
            cd.videoAmount = o.optInt("videoAmount", 0);
            return cd;
        } catch (Exception e) {
            Log.e(TAG, "load failed: " + channelDir, e);
            return null;
        }
    }

    /** Escribe (o crea) .Cdata.json en la carpeta del canal. */
    public void save(File channelDir) {
        try {
            JSONObject o = new JSONObject();
            o.put("folderId",    folderId    == null ? "" : folderId);
            o.put("displayName", displayName == null ? "" : displayName);
            o.put("photoPath",   photoPath   == null ? "" : photoPath);
            o.put("videoAmount", videoAmount);
            File f = new File(channelDir, FILE_NAME);
            FileWriter w = new FileWriter(f);
            w.write(o.toString(2));
            w.close();
        } catch (Exception e) {
            Log.e(TAG, "save failed: " + channelDir, e);
        }
    }

    /**
     * Crea un .Cdata.json inicial para una carpeta recién detectada.
     * displayName = nombre de carpeta, sin foto aún.
     */
    public static ChannelData createDefault(File channelDir) {
        ChannelData cd = new ChannelData();
        cd.folderId    = channelDir.getName();
        cd.displayName = channelDir.getName();
        cd.photoPath   = null;
        cd.videoAmount = 0;
        cd.save(channelDir);
        return cd;
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
