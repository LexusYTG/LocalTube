package com.lexus2026.localtube;

import android.util.*;
import java.io.*;
import java.util.*;

/**
 * Gestiona la carpeta rootPath/.restorer/
 *
 * Cada video tiene un archivo propio cuyo nombre es un UUID aleatorio
 * asignado en el momento de indexar (restorerFile en VideoItem).
 * El contenido del archivo es simplemente la posición en milisegundos
 * como texto plano ("123456\n"), lo que permite escrituras de ~10 bytes
 * cada 10 segundos sin tocar el index.json.
 *
 * Flujo:
 *   1. VideoIndex.buildVideoItem()  → RestorerStore.assignFile(rootPath)
 *      devuelve nombre de archivo (UUID). Se guarda en VideoItem.restorerFile
 *      y se persiste en index.json.
 *   2. PlayerActivity (cada 10 s)  → RestorerStore.writePosition(file, posMs)
 *   3. PlayerActivity.onPrepared() → RestorerStore.readPosition(file)
 *      si > 0 → seekTo()
 */
public class RestorerStore {

    private static final String TAG          = "RestorerStore";
    private static final String RESTORER_DIR = ".restorer";

    /** Crea la carpeta si no existe y devuelve el File. */
    public static File getOrCreateDir(String rootPath) {
        File dir = new File(rootPath, RESTORER_DIR);
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    /**
     * Genera un nombre de archivo UUID para un video recién indexado.
     * Solo devuelve el nombre (sin path); el path completo se construye
     * con buildFile().
     */
    public static String assignFileName() {
        // UUID v4 → 122 bits de aleatoriedad, imposible de adivinar/colisionar
        return UUID.randomUUID().toString().replace("-", "") + ".pos";
    }

    /** Construye el File a partir de rootPath + nombre asignado. */
    public static File buildFile(String rootPath, String fileName) {
        return new File(new File(rootPath, RESTORER_DIR), fileName);
    }

    /**
     * Escribe la posición (ms) en el archivo de restorer del video.
     * Operación rápida: ~10 bytes, sin JSON, sin bloqueos del índice.
     * Se puede llamar desde cualquier hilo.
     */
    public static void writePosition(File file, long positionMs) {
        if (file == null) return;
        try {
            byte[] data = Long.toString(positionMs).getBytes("UTF-8");
            FileOutputStream fos = new FileOutputStream(file, false);
            fos.write(data);
            fos.close();
        } catch (IOException e) {
            Log.w(TAG, "writePosition failed: " + e.getMessage());
        }
    }

    /**
     * Lee la posición guardada. Devuelve 0 si el archivo no existe
     * o tiene contenido inválido.
     */
    public static long readPosition(File file) {
        if (file == null || !file.exists()) return 0;
        try {
            FileInputStream fis = new FileInputStream(file);
            byte[] buf = new byte[24]; // suficiente para Long.MAX_VALUE
            int n = fis.read(buf);
            fis.close();
            if (n <= 0) return 0;
            String s = new String(buf, 0, n, "UTF-8").trim();
            return Long.parseLong(s);
        } catch (Exception e) {
            Log.w(TAG, "readPosition failed: " + e.getMessage());
            return 0;
        }
    }

    /** Borra el archivo de posición (p.ej. al completar el video). */
    public static void clearPosition(File file) {
        if (file != null && file.exists()) file.delete();
    }
}

