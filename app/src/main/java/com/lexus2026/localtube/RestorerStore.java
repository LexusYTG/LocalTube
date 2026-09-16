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

import android.util.*;
import java.io.*;
import java.util.*;

public class RestorerStore {

    private static final String TAG          = "RestorerStore";
    private static final String RESTORER_DIR = ".restorer";

    
    public static File getOrCreateDir(String rootPath) {
        File dir = new File(rootPath, RESTORER_DIR);
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    
    public static String assignFileName() {
        
        return UUID.randomUUID().toString().replace("-", "") + ".pos";
    }

    
    public static File buildFile(String rootPath, String fileName) {
        return new File(new File(rootPath, RESTORER_DIR), fileName);
    }

    
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

    
    public static long readPosition(File file) {
        if (file == null || !file.exists()) return 0;
        try {
            FileInputStream fis = new FileInputStream(file);
            byte[] buf = new byte[24]; 
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

    
    public static void clearPosition(File file) {
        if (file != null && file.exists()) file.delete();
    }
}
