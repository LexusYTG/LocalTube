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

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.*;
import java.util.*;

public class PipActions {

    public static final String ACTION_TOGGLE  = "com.lexus2026.localtube.PIP_TOGGLE";
    public static final String ACTION_REWIND  = "com.lexus2026.localtube.PIP_REWIND";
    public static final String ACTION_FORWARD = "com.lexus2026.localtube.PIP_FORWARD";
    public static final String ACTION_NEXT    = "com.lexus2026.localtube.PIP_NEXT";
    public static final String ACTION_PREV    = "com.lexus2026.localtube.PIP_PREV";

    private static final int ICON_SIZE_DP = 32;
    private static final int ICON_COLOR   = 0xFFFFFFFF;

    private PipActions() {}

    public static RemoteAction build(Context ctx, int iconCode,
                                     String title, String desc, String action) {
        Bitmap bmp = PlayerUi.renderIconBitmap(ctx, iconCode, ICON_SIZE_DP, ICON_COLOR);
        Icon icon  = Icon.createWithBitmap(bmp);

        Intent intent = new Intent(action);
        intent.setPackage(ctx.getPackageName());

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pi = PendingIntent.getBroadcast(ctx, iconCode, intent, flags);

        return new RemoteAction(icon, title, desc, pi);
    }

    
    public static ArrayList<RemoteAction> videoActions(Context ctx, boolean playing) {
        ArrayList<RemoteAction> out = new ArrayList<RemoteAction>();

        out.add(build(ctx, PlayerUi.ICON_PREV,
                      "Video anterior",
                      "Reproducir video anterior",
                      ACTION_PREV));

        out.add(build(ctx, playing ? PlayerUi.ICON_PAUSE : PlayerUi.ICON_PLAY,
                      playing ? "Pausar" : "Reproducir",
                      playing ? "Pausar reproduccion" : "Reanudar reproduccion",
                      ACTION_TOGGLE));

        out.add(build(ctx, PlayerUi.ICON_NEXT,
                      "Siguiente video",
                      "Reproducir siguiente video",
                      ACTION_NEXT));

        return out;
    }

    
    public static ArrayList<RemoteAction> seriesActions(Context ctx, boolean playing) {
        ArrayList<RemoteAction> out = new ArrayList<RemoteAction>();

        out.add(build(ctx, PlayerUi.ICON_REWIND,
                      "Retroceder 10s",
                      "Retroceder 10 segundos",
                      ACTION_REWIND));

        out.add(build(ctx, playing ? PlayerUi.ICON_PAUSE : PlayerUi.ICON_PLAY,
                      playing ? "Pausar" : "Reproducir",
                      playing ? "Pausar reproduccion" : "Reanudar reproduccion",
                      ACTION_TOGGLE));

        out.add(build(ctx, PlayerUi.ICON_NEXT,
                      "Siguiente episodio",
                      "Reproducir siguiente episodio",
                      ACTION_NEXT));

        return out;
    }
}
