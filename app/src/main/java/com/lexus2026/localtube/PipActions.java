package com.lexus2026.localtube;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.*;
import java.util.*;

/**
 * Construye las RemoteAction que Android muestra en el menú del PiP
 * (los botones que aparecen cuando el usuario toca la ventanita).
 *
 * Los iconos se generan con PlayerUi.renderIconBitmap() para que tengan
 * la misma estética que los controles in-app (nada de recursos del sistema).
 *
 * Solo se debe llamar en API 26+ (Android O); los call-sites ya están
 * guardados por Build.VERSION_CODES.O.
 */
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

    /**
     * Acciones para PlayerActivity (video suelto):
     *   [◀◀ Video anterior]  [▶/⏸ Play/Pausa]  [▶▶ Video siguiente]
     *
     * Es el mismo set de 3 botones que usa YouTube en PiP. Android solo
     * muestra 3 RemoteAction en la vista compacta, así que estos son los
     * que priorizamos. Si no hay anterior/siguiente, el botón se muestra
     * igual (al pulsarlo la Activity avisa con un Toast "No hay ...").
     */
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

    /** Acciones para SeriesPlayerActivity: retroceder, play/pausa, siguiente episodio. */
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
