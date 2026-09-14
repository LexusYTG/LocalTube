package com.lexus2026.localtube;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.media.*;
import android.os.*;

/**
 * Servicio de reproducción en segundo plano.
 *
 * Muestra una notificación persistente estilo YouTube/VLC con:
 *  - Carátula del video (thumbnail extraído del archivo)
 *  - Título del video
 *  - Barra de progreso (setProgress)
 *  - Botón Anterior  (ACTION_NOTIF_PREV)
 *  - Botón Play/Pause (ACTION_NOTIF_TOGGLE)
 *  - Botón Siguiente  (ACTION_NOTIF_NEXT)
 *
 * La Activity envía la información vía Intent extras al llamar startService(),
 * y puede actualizar progreso en cualquier momento con ACTION_NOTIF_UPDATE.
 *
 * Acciones de control (broadcast desde los botones de la notificación):
 *  ACTION_NOTIF_TOGGLE → PlayerActivity/SeriesPlayerActivity hace toggle
 *  ACTION_NOTIF_PREV   → ir al video anterior
 *  ACTION_NOTIF_NEXT   → ir al siguiente video / episodio
 *
 * El receiver que escucha estas acciones vive en la Activity. Este servicio
 * solo gestiona la notificación; el control real lo hace el player.
 */
public class MediaPlaybackService extends Service {

    // ── Canal y notificación ─────────────────────────────────────────────────
    private static final String CHANNEL_ID = "localtube_playback";
    private static final int    NOTIF_ID   = 1;

    // ── Acciones de control (enviadas como broadcast a la Activity) ───────────
    public static final String ACTION_NOTIF_TOGGLE = "com.lexus2026.localtube.NOTIF_TOGGLE";
    public static final String ACTION_NOTIF_PREV   = "com.lexus2026.localtube.NOTIF_PREV";
    public static final String ACTION_NOTIF_NEXT   = "com.lexus2026.localtube.NOTIF_NEXT";

    // ── Acción para actualizar la notificación sin reiniciar el servicio ──────
    public static final String ACTION_NOTIF_UPDATE = "com.lexus2026.localtube.NOTIF_UPDATE";

    // ── Extras del Intent ────────────────────────────────────────────────────
    public static final String EXTRA_TITLE       = "notif_title";
    public static final String EXTRA_POSITION_MS = "notif_position_ms";
    public static final String EXTRA_DURATION_MS = "notif_duration_ms";
    public static final String EXTRA_PLAYING      = "notif_playing";
    public static final String EXTRA_VIDEO_PATH   = "notif_video_path";
    public static final String EXTRA_HAS_PREV     = "notif_has_prev";
    public static final String EXTRA_HAS_NEXT     = "notif_has_next";

    // ── Estado interno ───────────────────────────────────────────────────────
    private String  title       = "";
    private int     positionMs  = 0;
    private int     durationMs  = 0;
    private boolean isPlaying   = true;
    private String  videoPath   = null;
    private boolean hasPrev     = false;
    private boolean hasNext     = false;
    private Bitmap  thumbnail   = null;

    private NotificationManager notifManager;
    private final Handler handler = new Handler();

    // Actualiza el progreso de la notificación cada segundo mientras reproduce.
    private final Runnable progressTick = new Runnable() {
        @Override public void run() {
            if (isPlaying && durationMs > 0) {
                positionMs = Math.min(positionMs + 1000, durationMs);
                postNotification();
            }
            handler.postDelayed(this, 1000);
        }
    };

    // (No receiver en el servicio: los PendingIntent de los botones de la
    // notificación envían el broadcast directamente al sistema, y la Activity
    // lo recibe con su propio pipReceiver. Tener un receiver aquí también
    // causaba un bucle: servicio recibe → re-emite → Activity recibe →
    // updateNotificationState → startService → servicio recibe... infinito.)

    // ── Ciclo de vida ────────────────────────────────────────────────────────

    @Override
    public void onCreate() {
        super.onCreate();
        notifManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            applyExtras(intent);
        }

        // Cargar thumbnail si hay ruta de video
        if (videoPath != null) {
            loadThumbnailAsync(videoPath);
        }

        startForeground(NOTIF_ID, buildNotification());
        handler.removeCallbacks(progressTick);
        handler.postDelayed(progressTick, 1000);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(progressTick);
        if (thumbnail != null && !thumbnail.isRecycled()) {
            thumbnail.recycle();
            thumbnail = null;
        }
        if (notifManager != null) {
            notifManager.cancel(NOTIF_ID);
        }
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    // ── Datos del intent ─────────────────────────────────────────────────────

    private void applyExtras(Intent intent) {
        String action = intent.getAction();

        if (ACTION_NOTIF_UPDATE.equals(action)) {
            // Actualización liviana: solo progreso y estado
            positionMs = intent.getIntExtra(EXTRA_POSITION_MS, positionMs);
            durationMs = intent.getIntExtra(EXTRA_DURATION_MS, durationMs);
            isPlaying  = intent.getBooleanExtra(EXTRA_PLAYING, isPlaying);
            // Reiniciar tick según estado actual
            handler.removeCallbacks(progressTick);
            if (isPlaying) handler.postDelayed(progressTick, 1000);
            postNotification();
            return;
        }

        // Inicio / cambio de video: leer todos los extras
        String newTitle = intent.getStringExtra(EXTRA_TITLE);
        if (newTitle != null) title = newTitle;

        String newPath = intent.getStringExtra(EXTRA_VIDEO_PATH);
        if (newPath != null && !newPath.equals(videoPath)) {
            videoPath = newPath;
            // Forzar recarga del thumbnail
            if (thumbnail != null && !thumbnail.isRecycled()) {
                thumbnail.recycle();
                thumbnail = null;
            }
        }

        positionMs = intent.getIntExtra(EXTRA_POSITION_MS, positionMs);
        durationMs = intent.getIntExtra(EXTRA_DURATION_MS, durationMs);
        isPlaying  = intent.getBooleanExtra(EXTRA_PLAYING, true);
        hasPrev    = intent.getBooleanExtra(EXTRA_HAS_PREV, false);
        hasNext    = intent.getBooleanExtra(EXTRA_HAS_NEXT, false);
    }

    // ── Thumbnail ────────────────────────────────────────────────────────────

    private void loadThumbnailAsync(final String path) {
        new Thread(new Runnable() {
				@Override public void run() {
					Bitmap bmp = extractThumbnail(path);
					if (bmp != null) {
						if (thumbnail != null && !thumbnail.isRecycled()) thumbnail.recycle();
						thumbnail = bmp;
						postNotification();
					}
				}
			}).start();
    }

    private Bitmap extractThumbnail(String path) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(path);
            // Pedir el frame en el 10% del video, o al principio si falla
            Bitmap frame = null;
            try {
                String durStr = retriever.extractMetadata(
					MediaMetadataRetriever.METADATA_KEY_DURATION);
                long durMs = durStr != null ? Long.parseLong(durStr) : 0;
                long timeUs = durMs > 0 ? (durMs / 10) * 1000L : 0L;
                frame = retriever.getFrameAtTime(
					timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            } catch (Exception ignored) {}

            if (frame == null) {
                frame = retriever.getFrameAtTime(0);
            }

            if (frame != null) {
                // Escalar a 256x144 (16:9) para la notificación
                return scaleBitmap(frame, 256, 144);
            }
        } catch (Exception ignored) {
        } finally {
            try { retriever.release(); } catch (Exception ignored) {}
        }
        return makeFallbackThumbnail();
    }

    private Bitmap scaleBitmap(Bitmap src, int targetW, int targetH) {
        if (src.getWidth() == targetW && src.getHeight() == targetH) return src;
        Bitmap scaled = Bitmap.createScaledBitmap(src, targetW, targetH, true);
        if (scaled != src) src.recycle();
        return scaled;
    }

    /** Thumbnail de fallback: rectángulo oscuro con icono de play. */
    private Bitmap makeFallbackThumbnail() {
        int w = 256, h = 144;
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(0xFF1A1A1A);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xFFFF004D);
        float cx = w / 2f, cy = h / 2f, r = h * 0.25f;
        float[] pts = {
            cx - r * 0.4f, cy - r * 0.7f,
            cx + r * 0.8f, cy,
            cx - r * 0.4f, cy + r * 0.7f
        };
        android.graphics.Path path = new android.graphics.Path();
        path.moveTo(pts[0], pts[1]);
        path.lineTo(pts[2], pts[3]);
        path.lineTo(pts[4], pts[5]);
        path.close();
        c.drawPath(path, p);
        return bmp;
    }

    // ── Notificación ─────────────────────────────────────────────────────────

    private void postNotification() {
        if (notifManager == null) return;
        notifManager.notify(NOTIF_ID, buildNotification());
    }

    private Notification buildNotification() {
        // Intent para abrir la app al tocar la notificación
        Intent openApp = new Intent(this, MainActivity.class);
        openApp.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) piFlags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent openPi = PendingIntent.getActivity(this, 0, openApp, piFlags);

        // PendingIntents para cada botón de control
        PendingIntent prevPi   = makeBroadcastPi(ACTION_NOTIF_PREV,   1);
        PendingIntent togglePi = makeBroadcastPi(ACTION_NOTIF_TOGGLE, 2);
        PendingIntent nextPi   = makeBroadcastPi(ACTION_NOTIF_NEXT,   3);

        // Iconos: usar los del sistema para máxima compatibilidad en la notificación
        int iconPrev   = android.R.drawable.ic_media_previous;
        int iconPlay   = isPlaying ? android.R.drawable.ic_media_pause
			: android.R.drawable.ic_media_play;
        int iconNext   = android.R.drawable.ic_media_next;

        // Calcular progreso (0–100)
        int progressPct = (durationMs > 0)
			? (int) ((positionMs * 100L) / durationMs)
			: 0;

        // Subtítulo con tiempos
        String subtitle = formatTime(positionMs) + " / " + formatTime(durationMs);

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            //noinspection deprecation
            builder = new Notification.Builder(this);
        }

        builder.setSmallIcon(android.R.drawable.ic_media_play)
			.setContentTitle(title.isEmpty() ? "LocalTube" : title)
			.setContentText(subtitle)
			.setContentIntent(openPi)
			.setOngoing(true)
			.setOnlyAlertOnce(true)
			.setShowWhen(false)
			.setProgress(100, progressPct, false);

        // Carátula como large icon
        if (thumbnail != null && !thumbnail.isRecycled()) {
            builder.setLargeIcon(thumbnail);
        }

        // Botones de acción
        if (hasPrev) {
            builder.addAction(iconPrev, "Anterior", prevPi);
        }
        builder.addAction(iconPlay, isPlaying ? "Pausar" : "Reproducir", togglePi);
        if (hasNext) {
            builder.addAction(iconNext, "Siguiente", nextPi);
        }

        // MediaStyle: muestra los botones en la fila compacta y usa el
        // botón de play/pausa como botón destacado (índice según los que existan).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Notification.MediaStyle style = new Notification.MediaStyle();
            // Mostrar hasta 3 botones en la vista compacta (collapsed)
            if (hasPrev && hasNext) {
                style.setShowActionsInCompactView(0, 1, 2); // prev, toggle, next
            } else if (hasPrev) {
                style.setShowActionsInCompactView(0, 1);    // prev, toggle
            } else if (hasNext) {
                style.setShowActionsInCompactView(0, 1);    // toggle, next
            } else {
                style.setShowActionsInCompactView(0);       // solo toggle
            }
            builder.setStyle(style);
        }

        // Visibilidad en pantalla de bloqueo
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder.setVisibility(Notification.VISIBILITY_PUBLIC);
        }

        return builder.build();
    }

    private PendingIntent makeBroadcastPi(String action, int requestCode) {
        Intent intent = new Intent(action);
        intent.setPackage(getPackageName());
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(this, requestCode, intent, flags);
    }

    // ── Canal de notificación ────────────────────────────────────────────────

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
				CHANNEL_ID,
				"LocalTube Reproducción",
				NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Reproducción de audio/video en segundo plano");
            ch.setShowBadge(false);
            ch.enableVibration(false);
            ch.enableLights(false);
            if (notifManager != null) notifManager.createNotificationChannel(ch);
        }
    }

    // ── Utilidades ───────────────────────────────────────────────────────────

    private String formatTime(long ms) {
        long s = ms / 1000, h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        return h > 0
			? String.format("%d:%02d:%02d", h, m, sec)
			: String.format("%d:%02d", m, sec);
    }

    // ── Helper estático para construir el Intent de inicio ──────────────────

    /**
     * Crea el Intent para arrancar/actualizar el servicio con todos los datos
     * del video actual. Llamar desde la Activity en onPause().
     */
    public static Intent buildStartIntent(
		Context ctx,
		VideoItem video,
		int positionMs,
		int durationMs,
		boolean isPlaying,
		boolean hasPrev,
		boolean hasNext) {

        Intent i = new Intent(ctx, MediaPlaybackService.class);
        if (video != null) {
            i.putExtra(EXTRA_TITLE,      video.title != null ? video.title : "");
            i.putExtra(EXTRA_VIDEO_PATH, video.path  != null ? video.path  : "");
        }
        i.putExtra(EXTRA_POSITION_MS, positionMs);
        i.putExtra(EXTRA_DURATION_MS, durationMs);
        i.putExtra(EXTRA_PLAYING,     isPlaying);
        i.putExtra(EXTRA_HAS_PREV,    hasPrev);
        i.putExtra(EXTRA_HAS_NEXT,    hasNext);
        return i;
    }

    /**
     * Crea el Intent para actualizar solo el progreso (liviano, sin recargar thumbnail).
     * Llamar desde el progressRunnable de la Activity cuando está en segundo plano.
     */
    public static Intent buildUpdateIntent(
		Context ctx,
		int positionMs,
		int durationMs,
		boolean isPlaying) {

        Intent i = new Intent(ctx, MediaPlaybackService.class);
        i.setAction(ACTION_NOTIF_UPDATE);
        i.putExtra(EXTRA_POSITION_MS, positionMs);
        i.putExtra(EXTRA_DURATION_MS, durationMs);
        i.putExtra(EXTRA_PLAYING,     isPlaying);
        return i;
    }
}

