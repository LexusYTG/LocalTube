package com.lexus2026.localtube;

import android.app.*;
import java.lang.ref.*;

/**
 * Holds a weak reference to whichever full-screen player Activity
 * (PlayerActivity or SeriesPlayerActivity) is currently alive - including
 * while it's minimized and sitting in the back stack behind MainActivity.
 * This lets the mini-player bar close it (stopPlayback + finish) without
 * having to bring it to the foreground first.
 *
 * NOTE: reconstructed for this session - see PlaybackGuard.java for context.
 */
public class PlayerBridge {

    private static WeakReference<Activity> current;

    public static void register(Activity activity) {
        current = new WeakReference<Activity>(activity);
    }

    public static void unregister(Activity activity) {
        if (current != null && current.get() == activity) {
            current = null;
        }
    }

    public static Activity getCurrent() {
        return current != null ? current.get() : null;
    }

    /**
     * Cierra por completo cualquier reproductor de pantalla completa
     * (PlayerActivity o SeriesPlayerActivity) que siga vivo - ya sea
     * minimizado en la barra inferior de MainActivity o en modo PiP del
     * sistema - para que un reproductor nuevo pueda quedar como el unico
     * activo. Debe llamarse ANTES de registrar el reproductor nuevo.
     */
    public static void closeCurrent() {
        Activity activity = getCurrent();
        if (activity == null) return;
        if (activity instanceof PlaybackGuard.Session) {
            ((PlaybackGuard.Session) activity).stopPlayback();
        } else if (!activity.isFinishing()) {
            activity.finish();
        }
    }
}
