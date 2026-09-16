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
import org.json.*;

public class ChannelData {

    private static final String TAG       = "ChannelData";
    static final String         FILE_NAME = ".Cdata.json";

    public String folderId;       
    public String displayName;    
    public String photoPath;      
    public int    videoAmount;    

    public ChannelData() {}

    
    
    

    
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

    
    public static ChannelData createDefault(File channelDir) {
        ChannelData cd = new ChannelData();
        cd.folderId    = channelDir.getName();
        cd.displayName = channelDir.getName();
        cd.photoPath   = null;
        cd.videoAmount = 0;
        cd.save(channelDir);
        return cd;
    }

    
    
    

    private static byte[] readFile(File f) throws IOException {
        FileInputStream fis = new FileInputStream(f);
        byte[] b = new byte[(int) f.length()];
        fis.read(b);
        fis.close();
        return b;
    }
}
