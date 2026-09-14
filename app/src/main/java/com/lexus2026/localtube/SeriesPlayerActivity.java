package com.lexus2026.localtube;

import android.app.*;
import android.content.*;
import android.content.res.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.media.*;
import android.os.*;
import android.util.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.ref.*;
import java.util.*;

public class SeriesPlayerActivity extends Activity
implements MediaPlayer.OnPreparedListener, MediaPlayer.OnCompletionListener,
MediaPlayer.OnErrorListener, PlaybackGuard.Session {

    public static final String EXTRA_VIDEO = "extra_video";

    /**
     * Claves para onSaveInstanceState. La Activity se recrea al entrar o salir
     * de PiP porque el sistema dispara cambios de configuración (screenSize,
     * screenLayout, etc.) y no los declaramos en el manifest. Sin esto, onCreate
     * vuelve a leer EXTRA_VIDEO, que contiene el video ORIGINAL, y el cambio de
     * capítulo hecho en PiP se pierde al maximizar (y viceversa).
     */
    private static final String STATE_VIDEO_ID = "state_video_id";
    private static final String STATE_POSITION = "state_position";

    private static final int COLOR_TEXT  = 0xFFFFFFFF;
    private static final int COLOR_TEXT2 = 0xFFCCCCCC;

    private FrameLayout root;
    private GLPlayerView glView;
    private FrameLayout controlsOverlay;

    private PlayerUi.IconButton btnPlay, btnSeekBack, btnSeekFwd, btnNextEp, btnPip, btnMinimize;
    private PlayerUi.SeekBar seekBar;
    private PlayerUi.VolumeSlider volumeBar;
    private TextView txtCurrentTime, txtTotalTime, txtTitle, txtEpisode;
    private ProgressBar progressBuffering;
    private FrameLayout endCard;
    private ImageView endCover;
    private TextView endText;

    private GlobalIndex index;
    private MediaPlayer mediaPlayer;
    private VideoItem currentVideo;
    private boolean isPrepared = false;
    private boolean prepareRequested = false;

    /** Surface activa del GLSurfaceView. */
    private Surface currentSurface;

    /** Posición viva en memoria. Fuente primaria en onPrepared. */
    private int lastKnownPositionMs = 0;

    /** Último tick del progressRunnable para detectar resets implícitos. */
    private long lastTickPosition = 0;
    private long lastTickTime = 0;

    /** true mientras un seek disparado por onPrepared no se completó. */
    private boolean seekInProgress = false;

    private boolean controlsVisible = false;
    private long playStartTime = 0;

    /**
     * Senal estatica que indica que esta Activity debe autodestruirse en el
     * proximo onResume/onPictureInPictureModeChanged (usado cuando finish()
     * es ignorado por el sistema porque estamos en PiP). Mismo mecanismo que
     * PlayerActivity.sRequestedClose.
     */
    static boolean sRequestedClose = false;

    private final Handler handler = new Handler();
    private final Runnable hideRunnable = new Runnable() {
        @Override public void run() { hideControls(); }
    };

    /**
     * Tick de 500 ms. Actualiza UI + persiste restorer. Detecta incoherencias
     * (posición que retrocede) para no pisar lastKnownPositionMs si el
     * MediaPlayer fue reseteado de forma implícita.
     */
    private final Runnable progressRunnable = new Runnable() {
        @Override public void run() {
            if (mediaPlayer != null && isPrepared) {
                int pos = mediaPlayer.getCurrentPosition();
                long now = System.currentTimeMillis();

                boolean coherent;
                if (lastTickTime == 0) {
                    coherent = pos > 0;
                } else {
                    long deltaWall = now - lastTickTime;
                    long deltaPos  = pos - lastTickPosition;
                    coherent = (deltaPos >= 0 && deltaPos < deltaWall * 4);
                }

                if (coherent && !seekInProgress && pos > 0) {
                    lastKnownPositionMs = pos;
                }

                lastTickPosition = pos;
                lastTickTime     = now;

                seekBar.setProgress(pos);
                txtCurrentTime.setText(formatTime(pos));

                if (currentVideo != null && lastKnownPositionMs > 0) {
                    java.io.File rf = index.getRestorerFile(currentVideo.id);
                    if (rf != null) RestorerStore.writePosition(rf, lastKnownPositionMs);
                }

                // Actualizar barra de progreso en la notificación de segundo plano.
                updateNotificationProgress();
            }
            handler.postDelayed(this, 500);
        }
    };

    private static final int HIDE_DELAY = 3000;
    private GestureDetector gestureDetector;

    // ── PiP receiver ─────────────────────────────────────────────────────────
    private boolean pipReceiverRegistered = false;

    private final BroadcastReceiver pipReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent != null ? intent.getAction() : null;
            if (action == null) return;

            if (PipActions.ACTION_TOGGLE.equals(action)
				|| MediaPlaybackService.ACTION_NOTIF_TOGGLE.equals(action)) {
                togglePlay();
                refreshPipParams();
                updateNotificationState();
            } else if (PipActions.ACTION_REWIND.equals(action)) {
                seekDelta(-10000);
            } else if (PipActions.ACTION_NEXT.equals(action)
					   || MediaPlaybackService.ACTION_NOTIF_NEXT.equals(action)) {
                goNextEpisode();
                refreshPipParams();
            } else if (MediaPlaybackService.ACTION_NOTIF_PREV.equals(action)) {
                // Series no tiene episodio anterior expuesto, ignorar
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                             WindowManager.LayoutParams.FLAG_FULLSCREEN);

        // Index primero: lo necesitamos para resolver el video por ID al
        // restaurar tras una recreación por cambio de configuración (PiP).
        index = new GlobalIndex(this);
        String root2 = AppPrefs.get(this).getRootPath();
        if (root2 != null) { index.setRootPath(root2); index.loadFromDisk(); }

        if (savedInstanceState != null) {
            String savedId = savedInstanceState.getString(STATE_VIDEO_ID);
            if (savedId != null) {
                currentVideo = index.findById(savedId);
            }
            lastKnownPositionMs = savedInstanceState.getInt(STATE_POSITION, 0);
        }
        if (currentVideo == null) {
            currentVideo = (VideoItem) getIntent().getSerializableExtra(EXTRA_VIDEO);
        }
        if (currentVideo == null) { finish(); return; }

        sRequestedClose = false;

        // Garantiza una sola fuente de reproduccion: si queda un reproductor
        // de video/serie residente (minimizado en la barra o en PiP), se
        // cierra por completo antes de arrancar este.
        PlayerBridge.closeCurrent();

        buildUi();

        initGestures();
        loadCurrent();
        registerPipReceiver();

        PlayerBridge.register(this);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        // Refrescar la posición viva antes de persistirla.
        if (mediaPlayer != null && isPrepared) {
            try {
                int pos = mediaPlayer.getCurrentPosition();
                if (pos > 0) lastKnownPositionMs = pos;
            } catch (Exception ignored) {}
        }
        if (currentVideo != null && currentVideo.id != null) {
            outState.putString(STATE_VIDEO_ID, currentVideo.id);
        }
        outState.putInt(STATE_POSITION, lastKnownPositionMs);
    }

    private void registerPipReceiver() {
        IntentFilter filter = new IntentFilter();
        // Acciones PiP (solo API 26+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            filter.addAction(PipActions.ACTION_TOGGLE);
            filter.addAction(PipActions.ACTION_REWIND);
            filter.addAction(PipActions.ACTION_NEXT);
        }
        // Acciones de la notificación de segundo plano (todas las versiones)
        filter.addAction(MediaPlaybackService.ACTION_NOTIF_TOGGLE);
        filter.addAction(MediaPlaybackService.ACTION_NOTIF_PREV);
        filter.addAction(MediaPlaybackService.ACTION_NOTIF_NEXT);
        try {
            registerReceiver(pipReceiver, filter);
            pipReceiverRegistered = true;
        } catch (Exception ignored) {
            pipReceiverRegistered = false;
        }
    }

    private void unregisterPipReceiver() {
        if (!pipReceiverRegistered) return;
        try { unregisterReceiver(pipReceiver); } catch (Exception ignored) {}
        pipReceiverRegistered = false;
    }

    // ── UI ───────────────────────────────────────────────────────────────────

    private void buildUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        setContentView(root, new ViewGroup.LayoutParams(
						   ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        glView = new GLPlayerView(this);
        glView.setListener(new GLPlayerView.Listener() {
				@Override public void onSurfaceReady(Surface surface) {
					currentSurface = surface;
					if (mediaPlayer == null) return;

					// Guardar posición antes de cualquier manipulación.
					if (isPrepared) {
						try {
							int pos = mediaPlayer.getCurrentPosition();
							if (pos > 0) lastKnownPositionMs = pos;
						} catch (Exception ignored) {}
					}

					// Algunos devices resetean implícitamente el MediaPlayer al
					// recibir una Surface nueva estando preparado. getDuration()
					// devuelve 0 (o lanza IllegalStateException en Idle).
					boolean mediaPlayerWasReset = false;
					if (isPrepared) {
						try {
							long d = mediaPlayer.getDuration();
							mediaPlayerWasReset = (d <= 0);
						} catch (Exception e) {
							mediaPlayerWasReset = true;
						}
					}

					try { mediaPlayer.setSurface(surface); } catch (Exception ignored) {}

					if (mediaPlayerWasReset && currentVideo != null) {
						// Re-preparar conservando lastKnownPositionMs.
						try {
							mediaPlayer.reset();
							mediaPlayer.setDataSource(currentVideo.path);
							mediaPlayer.setSurface(surface);
							isPrepared = false;
							prepareRequested = true;
							mediaPlayer.prepareAsync();
							progressBuffering.setVisibility(View.VISIBLE);
						} catch (Exception ignored) {}
					} else if (isPrepared) {
						if (lastKnownPositionMs > 0) {
							try {
								seekInProgress = true;
								mediaPlayer.setOnSeekCompleteListener(
									new MediaPlayer.OnSeekCompleteListener() {
										@Override public void onSeekComplete(MediaPlayer mp) {
											seekInProgress = false;
										}
									});
								mediaPlayer.seekTo(lastKnownPositionMs);
							} catch (Exception ignored) {
								seekInProgress = false;
							}
						}
					} else if (!prepareRequested) {
						prepareRequested = true;
						mediaPlayer.prepareAsync();
						progressBuffering.setVisibility(View.VISIBLE);
					}
				}
				@Override public void onSurfaceDestroyed() {
					currentSurface = null;
					if (mediaPlayer != null && isPrepared) {
						try {
							int pos = mediaPlayer.getCurrentPosition();
							if (pos > 0) lastKnownPositionMs = pos;
						} catch (Exception ignored) {}
					}
				}
			});
        root.addView(glView, new FrameLayout.LayoutParams(
						 ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        progressBuffering = new ProgressBar(this);
        FrameLayout.LayoutParams bufLp = new FrameLayout.LayoutParams(
			ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bufLp.gravity = Gravity.CENTER;
        root.addView(progressBuffering, bufLp);

        controlsOverlay = new FrameLayout(this);
        GradientDrawable overlayBg = new GradientDrawable(
			GradientDrawable.Orientation.TOP_BOTTOM,
			new int[]{ 0xCC000000, 0x33000000, 0x33000000, 0xCC000000 });
        controlsOverlay.setBackground(overlayBg);
        root.addView(controlsOverlay, new FrameLayout.LayoutParams(
						 ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // ── Top ──
        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);
        topRow.setPadding(dp(10), dp(10), dp(10), dp(10));

        btnMinimize = new PlayerUi.IconButton(this, PlayerUi.ICON_MINIMIZE);
        btnMinimize.setBgColor(0x33FFFFFF);
        btnMinimize.setBgColorPressed(0x66FFFFFF);
        topRow.addView(btnMinimize, new LinearLayout.LayoutParams(dp(44), dp(44)));
        btnMinimize.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) { minimize(); }
			});

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
			0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleLp.leftMargin = dp(12);
        topRow.addView(titleBox, titleLp);

        txtTitle = new TextView(this);
        txtTitle.setTextColor(COLOR_TEXT);
        txtTitle.setTextSize(15);
        txtTitle.setTypeface(null, Typeface.BOLD);
        txtTitle.setSingleLine(true);
        txtTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        titleBox.addView(txtTitle);

        txtEpisode = new TextView(this);
        txtEpisode.setTextColor(COLOR_TEXT2);
        txtEpisode.setTextSize(11);
        LinearLayout.LayoutParams epLp = new LinearLayout.LayoutParams(
			ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        epLp.topMargin = dp(2);
        titleBox.addView(txtEpisode, epLp);

        btnPip = new PlayerUi.IconButton(this, PlayerUi.ICON_PIP);
        btnPip.setBgColor(0x33FFFFFF);
        btnPip.setBgColorPressed(0x66FFFFFF);
        topRow.addView(btnPip, new LinearLayout.LayoutParams(dp(44), dp(44)));
        btnPip.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) { enterPip(); }
			});
        btnPip.setVisibility(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
							 ? View.VISIBLE : View.GONE);

        FrameLayout.LayoutParams topLp = new FrameLayout.LayoutParams(
			ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        topLp.gravity = Gravity.TOP;
        controlsOverlay.addView(topRow, topLp);

        // ── Center ──
        LinearLayout centerRow = new LinearLayout(this);
        centerRow.setOrientation(LinearLayout.HORIZONTAL);
        centerRow.setGravity(Gravity.CENTER);

        btnSeekBack = new PlayerUi.IconButton(this, PlayerUi.ICON_REWIND);
        btnSeekBack.setBgColor(0x33FFFFFF);
        btnSeekBack.setBgColorPressed(0x66FFFFFF);
        btnSeekBack.setIconScale(0.72f);

        btnPlay = new PlayerUi.IconButton(this, PlayerUi.ICON_PAUSE);
        btnPlay.setBgColor(PlayerUi.COLOR_ACCENT);
        btnPlay.setBgColorPressed(0xFFFF3366);
        btnPlay.setIconScale(0.50f);

        btnSeekFwd = new PlayerUi.IconButton(this, PlayerUi.ICON_FORWARD);
        btnSeekFwd.setBgColor(0x33FFFFFF);
        btnSeekFwd.setBgColorPressed(0x66FFFFFF);
        btnSeekFwd.setIconScale(0.72f);

        btnNextEp = new PlayerUi.IconButton(this, PlayerUi.ICON_NEXT);
        btnNextEp.setBgColor(0x33FFFFFF);
        btnNextEp.setBgColorPressed(0x66FFFFFF);
        btnNextEp.setIconScale(0.60f);

        addWithMargin(centerRow, btnSeekBack, dp(56));
        addWithMargin(centerRow, btnPlay,     dp(76));
        addWithMargin(centerRow, btnSeekFwd,  dp(56));
        addWithMargin(centerRow, btnNextEp,   dp(56));

        btnPlay.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) { togglePlay(); }
			});
        btnSeekBack.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) { seekDelta(-10000); }
			});
        btnSeekFwd.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) { seekDelta(10000); }
			});
        btnNextEp.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) { goNextEpisode(); }
			});

        FrameLayout.LayoutParams centerLp = new FrameLayout.LayoutParams(
			ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        centerLp.gravity = Gravity.CENTER;
        controlsOverlay.addView(centerRow, centerLp);

        // ── Bottom ──
        LinearLayout bottomBox = new LinearLayout(this);
        bottomBox.setOrientation(LinearLayout.VERTICAL);
        bottomBox.setPadding(dp(16), dp(8), dp(16), dp(22));

        LinearLayout seekRow = new LinearLayout(this);
        seekRow.setOrientation(LinearLayout.HORIZONTAL);
        seekRow.setGravity(Gravity.CENTER_VERTICAL);

        txtCurrentTime = new TextView(this);
        txtCurrentTime.setTextColor(COLOR_TEXT);
        txtCurrentTime.setTextSize(12);
        txtCurrentTime.setText("0:00");
        txtCurrentTime.setTypeface(Typeface.MONOSPACE);

        seekBar = new PlayerUi.SeekBar(this);
        LinearLayout.LayoutParams seekLp = new LinearLayout.LayoutParams(
			0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        seekLp.setMargins(dp(12), 0, dp(12), 0);
        seekBar.setOnSeekListener(new PlayerUi.SeekBar.OnSeekListener() {
				@Override public void onSeekStart() {
					handler.removeCallbacks(hideRunnable);
				}
				@Override public void onSeekProgress(int progressMs) {
					txtCurrentTime.setText(formatTime(progressMs));
				}
				@Override public void onSeekEnd(int progressMs) {
					if (isPrepared && mediaPlayer != null) {
						mediaPlayer.seekTo(progressMs);
						lastKnownPositionMs = progressMs;
						lastTickPosition = progressMs;
						lastTickTime = System.currentTimeMillis();
						if (currentVideo != null) {
							java.io.File rf = index.getRestorerFile(currentVideo.id);
							if (rf != null) RestorerStore.writePosition(rf, progressMs);
						}
					}
					resetHide();
				}
			});

        txtTotalTime = new TextView(this);
        txtTotalTime.setTextColor(COLOR_TEXT);
        txtTotalTime.setTextSize(12);
        txtTotalTime.setText("0:00");
        txtTotalTime.setTypeface(Typeface.MONOSPACE);

        seekRow.addView(txtCurrentTime);
        seekRow.addView(seekBar, seekLp);
        seekRow.addView(txtTotalTime);
        bottomBox.addView(seekRow);

        LinearLayout ctrlRow = new LinearLayout(this);
        ctrlRow.setOrientation(LinearLayout.HORIZONTAL);
        ctrlRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams ctrlLp = new LinearLayout.LayoutParams(
			ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ctrlLp.topMargin = dp(10);

        View spacer = new View(this);
        ctrlRow.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));

        volumeBar = new PlayerUi.VolumeSlider(this);
        LinearLayout.LayoutParams volLp = new LinearLayout.LayoutParams(
			dp(180), ViewGroup.LayoutParams.WRAP_CONTENT);
        ctrlRow.addView(volumeBar, volLp);

        bottomBox.addView(ctrlRow, ctrlLp);

        FrameLayout.LayoutParams bottomLp = new FrameLayout.LayoutParams(
			ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bottomLp.gravity = Gravity.BOTTOM;
        controlsOverlay.addView(bottomBox, bottomLp);

        final AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        int maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        volumeBar.setMax(maxVol);
        volumeBar.setValue(am.getStreamVolume(AudioManager.STREAM_MUSIC));
        volumeBar.setOnVolumeChangeListener(new PlayerUi.VolumeSlider.OnVolumeChangeListener() {
				@Override public void onVolumeChanged(int value) {
					am.setStreamVolume(AudioManager.STREAM_MUSIC, value, 0);
				}
			});

        // ── End card ──
        endCard = new FrameLayout(this);
        endCard.setBackgroundColor(Color.BLACK);
        endCard.setVisibility(View.GONE);
        root.addView(endCard, new FrameLayout.LayoutParams(
						 ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout endBox = new LinearLayout(this);
        endBox.setOrientation(LinearLayout.VERTICAL);
        endBox.setGravity(Gravity.CENTER_HORIZONTAL);

        endCover = new ImageView(this);
        endCover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        LinearLayout.LayoutParams coverLp = new LinearLayout.LayoutParams(dp(200), dp(280));
        endBox.addView(endCover, coverLp);

        endText = new TextView(this);
        endText.setTextColor(COLOR_TEXT);
        endText.setTextSize(22);
        endText.setTypeface(null, Typeface.BOLD);
        endText.setGravity(Gravity.CENTER);
        endText.setText("Fin");
        LinearLayout.LayoutParams endTextLp = new LinearLayout.LayoutParams(
			ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        endTextLp.topMargin = dp(20);
        endBox.addView(endText, endTextLp);

        FrameLayout.LayoutParams endBoxLp = new FrameLayout.LayoutParams(
			ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        endBoxLp.gravity = Gravity.CENTER;
        endCard.addView(endBox, endBoxLp);

        controlsOverlay.setVisibility(View.GONE);
        controlsVisible = false;
    }

    private void addWithMargin(LinearLayout row, View v, int size) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        lp.setMargins(dp(8), 0, dp(8), 0);
        row.addView(v, lp);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    // ── Ciclo de vida ────────────────────────────────────────────────────────

    @Override
    protected void onPause() {
        super.onPause();

        if (mediaPlayer != null && isPrepared && currentVideo != null) {
            try {
                int pos = mediaPlayer.getCurrentPosition();
                if (pos > 0) lastKnownPositionMs = pos;
            } catch (Exception ignored) {}
            java.io.File rf = index.getRestorerFile(currentVideo.id);
            if (rf != null && lastKnownPositionMs > 0) {
                RestorerStore.writePosition(rf, lastKnownPositionMs);
            }
        }

        if (mediaPlayer == null) return;
        if (mediaPlayer.isPlaying()) {
            if (AppPrefs.get(this).isBgAudioEnabled()) {
                savePosition();
                isServiceRunning = true;
                startService(buildNotifStartIntent());
            } else {
                mediaPlayer.pause();
                updatePlayButton();
            }
        }
    }

    /** Construye el Intent de inicio del servicio con metadatos del episodio actual. */
    private Intent buildNotifStartIntent() {
        int pos = (mediaPlayer != null && isPrepared) ? mediaPlayer.getCurrentPosition() : 0;
        int dur = (mediaPlayer != null && isPrepared) ? mediaPlayer.getDuration()        : 0;
        boolean playing = mediaPlayer != null && mediaPlayer.isPlaying();
        boolean hasNext = (currentVideo != null) && (index.getNextEpisode(currentVideo) != null);
        return MediaPlaybackService.buildStartIntent(this, currentVideo,
													 pos, dur, playing, false, hasNext);
    }

    private boolean isServiceRunning = false;
    private boolean wasInPip = false;

    private void updateNotificationProgress() {
        if (!isServiceRunning) return;
        if (mediaPlayer == null || !isPrepared) return;
        int pos    = mediaPlayer.getCurrentPosition();
        int dur    = mediaPlayer.getDuration();
        boolean pl = mediaPlayer.isPlaying();
        startService(MediaPlaybackService.buildUpdateIntent(this, pos, dur, pl));
    }

    private void updateNotificationState() {
        if (!isServiceRunning) return;
        if (mediaPlayer == null || !isPrepared) return;
        int pos    = mediaPlayer.getCurrentPosition();
        int dur    = mediaPlayer.getDuration();
        boolean pl = mediaPlayer.isPlaying();
        startService(MediaPlaybackService.buildUpdateIntent(this, pos, dur, pl));
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Si otro reproductor pidio que este se cierre (p.ej. se abrio un
        // video nuevo mientras este estaba en PiP), autodestruirse aqui -
        // funciona incluso cuando finish() externo fue ignorado por el
        // sistema por estar en PiP.
        if (sRequestedClose) {
            sRequestedClose = false;
            if (mediaPlayer != null && isPrepared) {
                try { mediaPlayer.pause(); } catch (Exception ignored) {}
            }
            finish();
            return;
        }

        // Si veníamos de PiP, no tocar el servicio aquí.
        // onPictureInPictureModeChanged (que llega justo después) decide si
        // el usuario expandió (→ parar servicio) o cerró la X (→ arrancar servicio).
        if (wasInPip) return;

        if (mediaPlayer != null && isPrepared && !mediaPlayer.isPlaying()
			&& !AppPrefs.get(this).isBgAudioEnabled()) {
            mediaPlayer.start();
            updatePlayButton();
        }
        isServiceRunning = false;
        stopService(new Intent(this, MediaPlaybackService.class));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);

        if (mediaPlayer != null && isPrepared && currentVideo != null) {
            try {
                int pos = mediaPlayer.getCurrentPosition();
                if (pos > 0) lastKnownPositionMs = pos;
            } catch (Exception ignored) {}
            java.io.File rf = index.getRestorerFile(currentVideo.id);
            if (rf != null && lastKnownPositionMs > 0) {
                RestorerStore.writePosition(rf, lastKnownPositionMs);
            }
        }
        savePosition();

        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        PlaybackGuard.release(this);
        PlayerBridge.unregister(this);
        MiniPlayerState.clear();
        unregisterPipReceiver();
    }

    @Override
    public void onBackPressed() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
			&& mediaPlayer != null && mediaPlayer.isPlaying()) {
            enterPip();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void onUserLeaveHint() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
			&& mediaPlayer != null && mediaPlayer.isPlaying()) {
            enterPip();
        }
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPiP, Configuration cfg) {
        controlsOverlay.setVisibility(View.GONE);
        controlsVisible = false;
        handler.removeCallbacks(hideRunnable);

        // Persistir posición en cada transición.
        if (mediaPlayer != null && isPrepared && currentVideo != null) {
            try {
                int pos = mediaPlayer.getCurrentPosition();
                if (pos > 0) lastKnownPositionMs = pos;
            } catch (Exception ignored) {}
            java.io.File rf = index.getRestorerFile(currentVideo.id);
            if (rf != null && lastKnownPositionMs > 0) {
                RestorerStore.writePosition(rf, lastKnownPositionMs);
            }
        }

        if (isInPiP) {
            // Entrando al PiP: mantener runnables activos.
            handler.removeCallbacks(progressRunnable);
            handler.post(progressRunnable);
        } else if (sRequestedClose) {
            // Otro reproductor pidio cerrar este mientras estaba en PiP.
            sRequestedClose = false;
            wasInPip = false;
            if (mediaPlayer != null && isPrepared) {
                try { mediaPlayer.pause(); } catch (Exception ignored) {}
            }
            isServiceRunning = false;
            stopService(new Intent(this, MediaPlaybackService.class));
            finish();
        } else {
            // Saliendo del PiP. Dos sub-casos:
            //   a) El usuario expandió (volvió a fullscreen): isFinishing()==false
            //   b) El usuario cerró la X de la ventanita: isFinishing()==true,
            //      el audio sigue → arrancar servicio de notificación.
            wasInPip = false;

            if (isFinishing()) {
                // Caso b: PiP cerrado con X.
                if (mediaPlayer != null && isPrepared && mediaPlayer.isPlaying()
					&& AppPrefs.get(this).isBgAudioEnabled()) {
                    isServiceRunning = true;
                    startService(buildNotifStartIntent());
                } else if (mediaPlayer != null && isPrepared) {
                    try { mediaPlayer.pause(); } catch (Exception ignored) {}
                    updatePlayButton();
                }
            } else {
                // Caso a: usuario expandió — volvemos a foreground.
                isServiceRunning = false;
                stopService(new Intent(this, MediaPlaybackService.class));
                showControls();
                scheduleHide();
                if (mediaPlayer != null && isPrepared && !mediaPlayer.isPlaying()) {
                    mediaPlayer.start();
                    updatePlayButton();
                }
                handler.removeCallbacks(progressRunnable);
                handler.post(progressRunnable);
            }
        }
    }

    @Override
    public void stopPlayback() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            savePosition();
            mediaPlayer.pause();
        }
        isServiceRunning = false;
        stopService(new Intent(this, MediaPlaybackService.class));
        MiniPlayerState.clear();
        requestClose();
    }

    /**
     * Cierra esta Activity aunque este en PiP. finish() es ignorado por el
     * sistema en algunos dispositivos mientras la ventana PiP esta activa,
     * asi que ademas dejamos la senal sRequestedClose para que se
     * autodestruya en el siguiente onResume/onPictureInPictureModeChanged
     * si finish() no surtio efecto de inmediato.
     */
    private void requestClose() {
        if (isFinishing()) return;
        sRequestedClose = true;
        finish();
    }

    private void minimize() {
        if (mediaPlayer != null) {
            MiniPlayerState.set(MiniPlayerState.SERIES, currentVideo,
								isPrepared ? mediaPlayer.getCurrentPosition() : currentVideo.lastPosition);
        }
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(i);
    }

    // ── Carga de episodio ────────────────────────────────────────────────────

    /**
     * Prepara el MediaPlayer para currentVideo. NO resetea lastKnownPositionMs:
     * el llamador decide si es un capítulo nuevo (posición 0) o una restauración
     * tras recreación (posición guardada).
     */
    private void loadCurrent() {
        endCard.setVisibility(View.GONE);
        isPrepared = false;
        prepareRequested = false;

        // Solo resets de estado transitorio.
        lastTickPosition = 0;
        lastTickTime = 0;
        seekInProgress = false;

        progressBuffering.setVisibility(View.VISIBLE);
        seekBar.setProgress(0);
        txtCurrentTime.setText(formatTime(0));
        txtTotalTime.setText(formatTime(0));
        txtTitle.setText(currentVideo.seriesName != null
						 ? currentVideo.seriesName : currentVideo.title);
        txtEpisode.setText(currentVideo.episode >= 0
						   ? "Capitulo " + currentVideo.episode
						   : currentVideo.title);

        if (mediaPlayer == null) {
            mediaPlayer = new MediaPlayer();
        } else {
            mediaPlayer.reset();
        }
        mediaPlayer.setOnPreparedListener(this);
        mediaPlayer.setOnCompletionListener(this);
        mediaPlayer.setOnErrorListener(this);

        try {
            mediaPlayer.setDataSource(currentVideo.path);
        } catch (IOException e) {
            Toast.makeText(this, "No se pudo abrir el video", Toast.LENGTH_SHORT).show();
            return;
        }

        if (currentSurface != null && currentSurface.isValid()) {
            try {
                mediaPlayer.setSurface(currentSurface);
                prepareRequested = true;
                mediaPlayer.prepareAsync();
            } catch (Exception e) {
                prepareRequested = false;
            }
        }
    }

    private void goNextEpisode() {
        VideoItem next = index.getNextEpisode(currentVideo);
        if (next == null) { showEndCard(); return; }
        savePosition();
        if (mediaPlayer != null && isPrepared && currentVideo != null) {
            try {
                java.io.File rf = index.getRestorerFile(currentVideo.id);
                if (rf != null) RestorerStore.writePosition(rf, mediaPlayer.getCurrentPosition());
            } catch (Exception ignored) {}
        }
        currentVideo = next;
        // Capítulo nuevo: arrancamos desde donde corresponda (restorer propio o 0).
        lastKnownPositionMs = 0;
        loadCurrent();
    }

    private void showEndCard() {
        savePosition();
        if (mediaPlayer != null && mediaPlayer.isPlaying()) mediaPlayer.pause();
        updatePlayButton();
        controlsOverlay.setVisibility(View.GONE);
        controlsVisible = false;
        endCard.setVisibility(View.VISIBLE);
        if (currentVideo.thumbPath != null) {
            new CoverLoader(endCover).execute(currentVideo.thumbPath);
        }
        endText.setText("Fin de " + (currentVideo.seriesName != null
						? currentVideo.seriesName : ""));
    }

    // ── Gestos ───────────────────────────────────────────────────────────────

    private void initGestures() {
        gestureDetector = new GestureDetector(this,
			new GestureDetector.SimpleOnGestureListener() {
				@Override
				public boolean onSingleTapConfirmed(MotionEvent e) {
					if (endCard.getVisibility() == View.VISIBLE) return true;
					if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
						&& isInPictureInPictureMode()) {
						return true;
					}
					if (controlsVisible) {
						handler.removeCallbacks(hideRunnable);
						hideControls();
					} else {
						showControls();
						scheduleHide();
					}
					return true;
				}
				@Override
				public boolean onDoubleTap(MotionEvent e) {
					if (!isPrepared) return false;
					if (e.getX() < glView.getWidth() / 2f) seekDelta(-10000);
					else seekDelta(10000);
					return true;
				}
			});

        View.OnTouchListener touchRouter = new View.OnTouchListener() {
            @Override public boolean onTouch(View v, MotionEvent e) {
                gestureDetector.onTouchEvent(e);
                return true;
            }
        };
        glView.setOnTouchListener(touchRouter);
        controlsOverlay.setOnTouchListener(touchRouter);
    }

    // ── MediaPlayer callbacks ────────────────────────────────────────────────

    @Override
    public void onPrepared(MediaPlayer mp) {
        isPrepared = true;
        progressBuffering.setVisibility(View.GONE);
        seekBar.setMax(mp.getDuration());
        txtTotalTime.setText(formatTime(mp.getDuration()));

        int videoW = mp.getVideoWidth();
        int videoH = mp.getVideoHeight();
        if (videoW > 0 && videoH > 0) glView.setVideoSize(videoW, videoH);

        // Prioridad: lastKnownPositionMs (viva o restaurada por recreación)
        // > restorer file > lastPosition del índice.
        long resumePos = 0;
        if (lastKnownPositionMs > 0) {
            resumePos = lastKnownPositionMs;
        } else {
            java.io.File rf = currentVideo != null ? index.getRestorerFile(currentVideo.id) : null;
            if (rf != null) resumePos = RestorerStore.readPosition(rf);
            if (resumePos <= 0) resumePos = currentVideo.lastPosition;
        }

        if (resumePos > 0 && resumePos < mp.getDuration() - 5000) {
            seekInProgress = true;
            lastKnownPositionMs = (int) resumePos;
            mp.setOnSeekCompleteListener(new MediaPlayer.OnSeekCompleteListener() {
					@Override public void onSeekComplete(MediaPlayer mp2) {
						seekInProgress = false;
					}
				});
            mp.seekTo((int) resumePos);
        } else {
            lastKnownPositionMs = 0;
        }

        mp.start();
        if (lastKnownPositionMs == 0) {
            lastKnownPositionMs = mp.getCurrentPosition();
        }
        lastTickPosition = mp.getCurrentPosition();
        lastTickTime = System.currentTimeMillis();

        playStartTime = System.currentTimeMillis();
        updatePlayButton();
        handler.removeCallbacks(progressRunnable);
        handler.post(progressRunnable);

        PlaybackGuard.claim(this, this);
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        goNextEpisode();
        refreshPipParams();
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        Toast.makeText(this, "Error de reproduccion (" + what + ")", Toast.LENGTH_SHORT).show();
        return true;
    }

    // ── Acciones ─────────────────────────────────────────────────────────────

    private void togglePlay() {
        if (!isPrepared) return;
        if (mediaPlayer.isPlaying()) mediaPlayer.pause();
        else mediaPlayer.start();
        updatePlayButton();
        resetHide();
    }

    private void seekDelta(int deltaMs) {
        if (!isPrepared) return;
        int pos = mediaPlayer.getCurrentPosition() + deltaMs;
        if (pos < 0) pos = 0;
        if (pos > mediaPlayer.getDuration()) pos = mediaPlayer.getDuration();
        mediaPlayer.seekTo(pos);
        lastKnownPositionMs = pos;
        lastTickPosition = pos;
        lastTickTime = System.currentTimeMillis();
        if (currentVideo != null) {
            java.io.File rf = index.getRestorerFile(currentVideo.id);
            if (rf != null) RestorerStore.writePosition(rf, pos);
        }
        txtCurrentTime.setText(formatTime(pos));
        resetHide();
    }

    private boolean inPip() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isInPictureInPictureMode();
    }

    private void showControls() {
        if (inPip()) return;
        controlsOverlay.setVisibility(View.VISIBLE);
        controlsVisible = true;
    }

    private void hideControls() {
        controlsOverlay.setVisibility(View.GONE);
        controlsVisible = false;
    }

    private void scheduleHide() {
        handler.removeCallbacks(hideRunnable);
        if (inPip()) return;
        handler.postDelayed(hideRunnable, HIDE_DELAY);
    }

    private void resetHide() {
        if (inPip()) return;
        showControls();
        scheduleHide();
    }

    // ── PiP ──────────────────────────────────────────────────────────────────

    private void enterPip() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        if (mediaPlayer != null && isPrepared && currentVideo != null) {
            try {
                int pos = mediaPlayer.getCurrentPosition();
                if (pos > 0) lastKnownPositionMs = pos;
            } catch (Exception ignored) {}
            java.io.File rf = index.getRestorerFile(currentVideo.id);
            if (rf != null && lastKnownPositionMs > 0) {
                RestorerStore.writePosition(rf, lastKnownPositionMs);
            }
        }

        controlsOverlay.setVisibility(View.GONE);
        controlsVisible = false;
        handler.removeCallbacks(hideRunnable);

        wasInPip = true;
        try {
            enterPictureInPictureMode(buildPipParams());
        } catch (Exception ignored) {}
    }

    private PictureInPictureParams buildPipParams() {
        int w = currentVideo.width  > 0 ? currentVideo.width  : 16;
        int h = currentVideo.height > 0 ? currentVideo.height : 9;
        int g = gcd(w, h);

        boolean playing = mediaPlayer != null && mediaPlayer.isPlaying();
        ArrayList<RemoteAction> actions = PipActions.seriesActions(this, playing);

        return new PictureInPictureParams.Builder()
			.setAspectRatio(new Rational(w / g, h / g))
			.setActions(actions)
			.build();
    }

    private void refreshPipParams() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        if (!isInPictureInPictureMode()) return;
        try {
            setPictureInPictureParams(buildPipParams());
        } catch (Exception ignored) {}
    }

    private void updatePlayButton() {
        boolean playing = mediaPlayer != null && mediaPlayer.isPlaying();
        btnPlay.setIcon(playing ? PlayerUi.ICON_PAUSE : PlayerUi.ICON_PLAY);
        refreshPipParams();
    }

    private void savePosition() {
        if (!isPrepared || mediaPlayer == null || currentVideo == null) return;
        try {
            index.recordPlay(currentVideo.id,
							 System.currentTimeMillis() - playStartTime,
							 mediaPlayer.getCurrentPosition(),
							 mediaPlayer.getDuration());
        } catch (Exception ignored) {}
    }

    private String formatTime(long ms) {
        long s = ms / 1000, h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        return h > 0
			? String.format("%d:%02d:%02d", h, m, sec)
			: String.format("%d:%02d", m, sec);
    }

    private int gcd(int a, int b) { return b == 0 ? a : gcd(b, a % b); }

    static class CoverLoader extends AsyncTask<String, Void, Bitmap> {
        private final WeakReference<ImageView> ref;
        CoverLoader(ImageView iv) { ref = new WeakReference<ImageView>(iv); }

        @Override
        protected Bitmap doInBackground(String... paths) {
            try { return BitmapFactory.decodeFile(paths[0]); }
            catch (Exception e) { return null; }
        }

        @Override
        protected void onPostExecute(Bitmap bmp) {
            ImageView iv = ref.get();
            if (iv != null && bmp != null) iv.setImageBitmap(bmp);
        }
    }
}


