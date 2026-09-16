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
import android.content.res.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.media.*;
import android.os.*;
import android.util.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;

public class PlayerActivity extends Activity
implements MediaPlayer.OnPreparedListener,
MediaPlayer.OnCompletionListener,
MediaPlayer.OnErrorListener,
PlaybackGuard.Session {

    public static final String EXTRA_VIDEO    = "extra_video";
    public static final String EXTRA_VIDEO_ID = "extra_video_id";

    private static final int COLOR_TEXT    = 0xFFFFFFFF;
    private static final int COLOR_PRIMARY = 0xFFFF004D;
    private static final int COLOR_PILL    = 0xCC1F1F1F;

    private FrameLayout root;
    private FrameLayout playerContainer;
    private GLPlayerView glView;
    private FrameLayout controlsOverlay;

    private PlayerUi.IconButton btnPlay, btnPrev, btnNext,
	btnSeekBack, btnSeekFwd, btnPip, btnMinimize;
    private PlayerUi.SeekBar seekBar;
    private PlayerUi.VolumeSlider volumeBar;
    private TextView txtCurrentTime, txtTotalTime, txtTitle, txtSpeed;
    private ProgressBar progressBuffering;

    private MediaPlayer mediaPlayer;
    private VideoItem currentVideo;
    private GlobalIndex index;
    private boolean isPrepared = false;
    private boolean prepareRequested = false;
    private long playStartTime = 0;

    
    private Surface currentSurface;

    private int lastKnownPositionMs = 0;
    private long lastRestorerWriteMs = 0;

    private java.io.File restorerFile;
    private final Runnable restorerRunnable = new Runnable() {
        @Override public void run() {
            if (mediaPlayer != null && isPrepared && restorerFile != null) {
                RestorerStore.writePosition(restorerFile, mediaPlayer.getCurrentPosition());
                lastRestorerWriteMs = System.currentTimeMillis();
            }
            handler.postDelayed(this, 10_000);
        }
    };

    private boolean controlsVisible = false;
    private boolean wasInPip = false;

    static boolean sRequestedClose = false;

    private final Handler handler = new Handler();

    private final Runnable hideRunnable = new Runnable() {
        @Override public void run() { hideControls(); }
    };

    private final Runnable progressRunnable = new Runnable() {
        @Override public void run() {
            if (mediaPlayer != null && isPrepared) {
                int pos = mediaPlayer.getCurrentPosition();
                if (pos > 0) lastKnownPositionMs = pos;
                seekBar.setProgress(pos);
                txtCurrentTime.setText(formatTime(pos));

                if (restorerFile != null && pos > 0) {
                    long now = System.currentTimeMillis();
                    if (now - lastRestorerWriteMs >= 2000) {
                        RestorerStore.writePosition(restorerFile, pos);
                        lastRestorerWriteMs = now;
                    }
                }

                updateNotificationProgress();
            }
            handler.postDelayed(this, 500);
        }
    };

    private static final int[]    SPEEDS_NUM = {50, 75, 100, 125, 150, 200};
    private static final String[] SPEEDS_LBL = {"0.5x","0.75x","1x","1.25x","1.5x","2x"};
    private int   speedIndex    = 2;
    private float playbackSpeed = 1.0f;
    private static final int HIDE_DELAY = 3000;

    private GestureDetector gestureDetector;

    
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
            } else if (PipActions.ACTION_FORWARD.equals(action)) {
                seekDelta(10000);
            } else if (PipActions.ACTION_PREV.equals(action)
					   || MediaPlaybackService.ACTION_NOTIF_PREV.equals(action)) {
                goPrevVideo();
                refreshPipParams();
            } else if (PipActions.ACTION_NEXT.equals(action)
					   || MediaPlaybackService.ACTION_NOTIF_NEXT.equals(action)) {
                goNextVideo();
                refreshPipParams();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        sRequestedClose = false;
        currentVideo = (VideoItem) getIntent().getSerializableExtra(EXTRA_VIDEO);
        if (currentVideo == null) { finish(); return; }

        PlayerBridge.closeCurrent();

        index = new GlobalIndex(this);
        String root = AppPrefs.get(this).getRootPath();
        if (root != null) { index.setRootPath(root); index.loadFromDisk(); }

        resolveRestorerFile();

        playbackSpeed = AppPrefs.get(this).getPlaybackSpeed();
        speedIndex    = speedIndexFromFloat(playbackSpeed);

        buildUi();

        initPlayer();
        initGestures();
        registerPipReceiver();
        PlayerBridge.register(this);
    }

    private void registerPipReceiver() {
        IntentFilter filter = new IntentFilter();
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            filter.addAction(PipActions.ACTION_TOGGLE);
            filter.addAction(PipActions.ACTION_REWIND);
            filter.addAction(PipActions.ACTION_FORWARD);
            filter.addAction(PipActions.ACTION_PREV);   
            filter.addAction(PipActions.ACTION_NEXT);   
        }
        
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

    

    private void buildUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        setContentView(root);

        playerContainer = new FrameLayout(this);
        root.addView(playerContainer, new FrameLayout.LayoutParams(
						 ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        glView = new GLPlayerView(this);
        glView.setListener(new GLPlayerView.Listener() {
				@Override public void onSurfaceReady(Surface surface) {
					currentSurface = surface;
					if (mediaPlayer == null) return;
					mediaPlayer.setSurface(surface);
					if (isPrepared) {
						if (lastKnownPositionMs > 0) {
							try { mediaPlayer.seekTo(lastKnownPositionMs); } catch (Exception ignored) {}
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
						int pos = mediaPlayer.getCurrentPosition();
						if (pos > 0) lastKnownPositionMs = pos;
					}
				}
			});
        playerContainer.addView(glView, new FrameLayout.LayoutParams(
									ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        progressBuffering = new ProgressBar(this);
        FrameLayout.LayoutParams pbLp = new FrameLayout.LayoutParams(
			ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pbLp.gravity = Gravity.CENTER;
        root.addView(progressBuffering, pbLp);

        controlsOverlay = new FrameLayout(this);
        GradientDrawable overlayBg = new GradientDrawable(
			GradientDrawable.Orientation.TOP_BOTTOM,
			new int[]{ 0xCC000000, 0x33000000, 0x33000000, 0xCC000000 });
        controlsOverlay.setBackground(overlayBg);
        root.addView(controlsOverlay, new FrameLayout.LayoutParams(
						 ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        buildTopSection();
        buildCenterSection();
        buildBottomSection();

        controlsOverlay.setVisibility(View.GONE);
        controlsVisible = false;
    }

    private void buildTopSection() {
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

        txtTitle = new TextView(this);
        txtTitle.setText(currentVideo.title);
        txtTitle.setTextColor(COLOR_TEXT);
        txtTitle.setTextSize(15);
        txtTitle.setTypeface(null, Typeface.BOLD);
        txtTitle.setSingleLine(true);
        txtTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
			0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleLp.leftMargin = dp(12);
        topRow.addView(txtTitle, titleLp);

        btnPip = new PlayerUi.IconButton(this, PlayerUi.ICON_PIP);
        btnPip.setBgColor(0x33FFFFFF);
        btnPip.setBgColorPressed(0x66FFFFFF);
        topRow.addView(btnPip, new LinearLayout.LayoutParams(dp(44), dp(44)));
        btnPip.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) { enterPip(); }
			});
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) btnPip.setVisibility(View.GONE);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
			ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.TOP;
        controlsOverlay.addView(topRow, lp);
    }

    private void buildCenterSection() {
        LinearLayout centerRow = new LinearLayout(this);
        centerRow.setOrientation(LinearLayout.HORIZONTAL);
        centerRow.setGravity(Gravity.CENTER);

        btnPrev = new PlayerUi.IconButton(this, PlayerUi.ICON_PREV);
        btnPrev.setBgColor(0x33FFFFFF);
        btnPrev.setBgColorPressed(0x66FFFFFF);
        btnPrev.setIconScale(0.60f);

        btnSeekBack = new PlayerUi.IconButton(this, PlayerUi.ICON_REWIND);
        btnSeekBack.setBgColor(0x33FFFFFF);
        btnSeekBack.setBgColorPressed(0x66FFFFFF);
        btnSeekBack.setIconScale(0.72f);

        btnPlay = new PlayerUi.IconButton(this, PlayerUi.ICON_PLAY);
        btnPlay.setBgColor(PlayerUi.COLOR_ACCENT);
        btnPlay.setBgColorPressed(0xFFFF3366);
        btnPlay.setIconScale(0.50f);

        btnSeekFwd = new PlayerUi.IconButton(this, PlayerUi.ICON_FORWARD);
        btnSeekFwd.setBgColor(0x33FFFFFF);
        btnSeekFwd.setBgColorPressed(0x66FFFFFF);
        btnSeekFwd.setIconScale(0.72f);

        btnNext = new PlayerUi.IconButton(this, PlayerUi.ICON_NEXT);
        btnNext.setBgColor(0x33FFFFFF);
        btnNext.setBgColorPressed(0x66FFFFFF);
        btnNext.setIconScale(0.60f);

        addCenterButton(centerRow, btnPrev,     dp(52));
        addCenterButton(centerRow, btnSeekBack, dp(52));
        addCenterButton(centerRow, btnPlay,     dp(72));
        addCenterButton(centerRow, btnSeekFwd,  dp(52));
        addCenterButton(centerRow, btnNext,     dp(52));

        btnPlay.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) { togglePlay(); }
			});
        btnSeekBack.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) { seekDelta(-10000); }
			});
        btnSeekFwd.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) { seekDelta(10000); }
			});
        btnPrev.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) { goPrevVideo(); }
			});
        btnNext.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) { goNextVideo(); }
			});

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
			ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER;
        controlsOverlay.addView(centerRow, lp);
    }

    private void addCenterButton(LinearLayout row, View b, int size) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        lp.setMargins(dp(6), 0, dp(6), 0);
        row.addView(b, lp);
    }

    private void buildBottomSection() {
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
						if (restorerFile != null) {
							RestorerStore.writePosition(restorerFile, progressMs);
							lastRestorerWriteMs = System.currentTimeMillis();
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

        txtSpeed = new TextView(this);
        txtSpeed.setText(SPEEDS_LBL[speedIndex]);
        txtSpeed.setTextColor(COLOR_TEXT);
        txtSpeed.setTextSize(12);
        txtSpeed.setTypeface(null, Typeface.BOLD);
        txtSpeed.setGravity(Gravity.CENTER);
        GradientDrawable sbg = new GradientDrawable();
        sbg.setColor(COLOR_PILL);
        sbg.setCornerRadius(dp(20));
        txtSpeed.setBackground(sbg);
        txtSpeed.setPadding(dp(16), dp(8), dp(16), dp(8));
        txtSpeed.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) { cycleSpeed(); }
			});
        ctrlRow.addView(txtSpeed);

        View spacer = new View(this);
        ctrlRow.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));

        volumeBar = new PlayerUi.VolumeSlider(this);
        LinearLayout.LayoutParams volLp = new LinearLayout.LayoutParams(
			dp(180), ViewGroup.LayoutParams.WRAP_CONTENT);
        ctrlRow.addView(volumeBar, volLp);

        bottomBox.addView(ctrlRow, ctrlLp);

        FrameLayout.LayoutParams bpLp = new FrameLayout.LayoutParams(
			ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bpLp.gravity = Gravity.BOTTOM;
        controlsOverlay.addView(bottomBox, bpLp);

        final AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        int maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        volumeBar.setMax(maxVol);
        volumeBar.setValue(am.getStreamVolume(AudioManager.STREAM_MUSIC));
        volumeBar.setOnVolumeChangeListener(new PlayerUi.VolumeSlider.OnVolumeChangeListener() {
				@Override public void onVolumeChanged(int value) {
					am.setStreamVolume(AudioManager.STREAM_MUSIC, value, 0);
				}
			});
    }

    

    private void initPlayer() {
        mediaPlayer = new MediaPlayer();
        mediaPlayer.setOnPreparedListener(this);
        mediaPlayer.setOnCompletionListener(this);
        mediaPlayer.setOnErrorListener(this);
        try {
            mediaPlayer.setDataSource(currentVideo.path);
        } catch (IOException e) {
            Toast.makeText(this, "No se pudo abrir el video", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void initGestures() {
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
				@Override
				public boolean onSingleTapConfirmed(MotionEvent e) {
					if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isInPictureInPictureMode()) {
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
					if (e.getX() < glView.getWidth() / 2f) {
						seekDelta(-10000);
						Toast.makeText(PlayerActivity.this, "-10s", Toast.LENGTH_SHORT).show();
					} else {
						seekDelta(10000);
						Toast.makeText(PlayerActivity.this, "+10s", Toast.LENGTH_SHORT).show();
					}
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

    

    private List<VideoItem> buildPlaylist() {
        List<VideoItem> all = index.getAll();
        List<VideoItem> sameChannel = new ArrayList<VideoItem>();
        List<VideoItem> sameType    = new ArrayList<VideoItem>();
        if (currentVideo == null) return sameType;

        for (VideoItem v : all) {
            if (v.type != currentVideo.type) continue;
            sameType.add(v);
            if (currentVideo.channelId != null && !currentVideo.channelId.isEmpty()
				&& currentVideo.channelId.equals(v.channelId)) {
                sameChannel.add(v);
            }
        }
        if (sameChannel.size() > 1) return sameChannel;
        if (sameType.size()    > 1) return sameType;
        if (!sameChannel.isEmpty()) return sameChannel;
        return sameType;
    }

    private VideoItem findAdjacent(boolean next) {
        List<VideoItem> list = buildPlaylist();
        if (list.size() < 2) return null;
        int idx = -1;
        for (int i = 0; i < list.size(); i++) {
            if (currentVideo.id != null && currentVideo.id.equals(list.get(i).id)) {
                idx = i;
                break;
            }
        }
        if (idx < 0) return null;
        int target = next ? idx + 1 : idx - 1;
        if (target < 0 || target >= list.size()) return null;
        return list.get(target);
    }

    private void goNextVideo() {
        VideoItem target = findAdjacent(true);
        if (target == null) {
            Toast.makeText(this, "No hay siguiente video", Toast.LENGTH_SHORT).show();
            return;
        }
        switchVideo(target);
    }

    private void goPrevVideo() {
        VideoItem target = findAdjacent(false);
        if (target == null) {
            Toast.makeText(this, "No hay video anterior", Toast.LENGTH_SHORT).show();
            return;
        }
        switchVideo(target);
    }

    private void switchVideo(VideoItem target) {
        if (target == null) return;

        savePosition();
        if (mediaPlayer != null && isPrepared && restorerFile != null) {
            RestorerStore.writePosition(restorerFile, mediaPlayer.getCurrentPosition());
        }
        handler.removeCallbacks(progressRunnable);
        handler.removeCallbacks(restorerRunnable);

        currentVideo = target;
        resolveRestorerFile();

        lastKnownPositionMs = 0;
        lastRestorerWriteMs = 0;

        isPrepared = false;
        prepareRequested = false;

        progressBuffering.setVisibility(View.VISIBLE);
        seekBar.setProgress(0);
        txtCurrentTime.setText(formatTime(0));
        txtTotalTime.setText(formatTime(0));
        txtTitle.setText(currentVideo.title);

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

    private void resolveRestorerFile() {
        if (currentVideo == null || currentVideo.id == null) {
            restorerFile = null;
            return;
        }
        restorerFile = index.getRestorerFile(currentVideo.id);
        if (restorerFile == null) {
            String root = AppPrefs.get(this).getRootPath();
            if (root != null) {
                restorerFile = index.ensureRestorerFile(currentVideo.id, root);
            }
        }
    }

    

    @Override
    protected void onPause() {
        super.onPause();

        if (mediaPlayer != null && isPrepared && restorerFile != null) {
            RestorerStore.writePosition(restorerFile, mediaPlayer.getCurrentPosition());
            lastRestorerWriteMs = System.currentTimeMillis();
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

    private Intent buildNotifStartIntent() {
        int pos = (mediaPlayer != null && isPrepared) ? mediaPlayer.getCurrentPosition() : 0;
        int dur = (mediaPlayer != null && isPrepared) ? mediaPlayer.getDuration()        : 0;
        boolean playing = mediaPlayer != null && mediaPlayer.isPlaying();
        boolean prev = findAdjacent(false) != null;
        boolean next = findAdjacent(true)  != null;
        return MediaPlaybackService.buildStartIntent(this, currentVideo,
													 pos, dur, playing, prev, next);
    }

    private boolean isServiceRunning = false;

    private void updateNotificationProgress() {
        if (!isServiceRunning) return;
        if (mediaPlayer == null || !isPrepared) return;
        int pos     = mediaPlayer.getCurrentPosition();
        int dur     = mediaPlayer.getDuration();
        boolean pl  = mediaPlayer.isPlaying();
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

        if (sRequestedClose) {
            sRequestedClose = false;
            if (mediaPlayer != null && isPrepared) {
                try { mediaPlayer.pause(); } catch (Exception ignored) {}
            }
            finish();
            return;
        }

        if (wasInPip) {
            return;
        }

        if (mediaPlayer != null && isPrepared && !mediaPlayer.isPlaying()
			&& !AppPrefs.get(this).isBgAudioEnabled()) {
            mediaPlayer.start();
            updatePlayButton();
        }
        if (isPrepared) {
            handler.removeCallbacks(restorerRunnable);
            handler.post(restorerRunnable);
        }
        isServiceRunning = false;
        stopService(new Intent(this, MediaPlaybackService.class));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);

        if (mediaPlayer != null && isPrepared && restorerFile != null) {
            RestorerStore.writePosition(restorerFile, mediaPlayer.getCurrentPosition());
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
    public void stopPlayback() {
        if (mediaPlayer != null && isPrepared) {
            try { mediaPlayer.pause(); } catch (Exception ignored) {}
        }
        isServiceRunning = false;
        stopService(new Intent(this, MediaPlaybackService.class));
        MiniPlayerState.clear();
        requestClose();
    }

    private void requestClose() {
        if (isFinishing()) return;
        sRequestedClose = true;
        finish();
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

        if (mediaPlayer != null && isPrepared && restorerFile != null) {
            RestorerStore.writePosition(restorerFile, mediaPlayer.getCurrentPosition());
            lastRestorerWriteMs = System.currentTimeMillis();
        }

        if (isInPiP) {
            if (isPrepared) {
                handler.removeCallbacks(restorerRunnable);
                handler.post(restorerRunnable);
            }
        } else if (sRequestedClose) {
            sRequestedClose = false;
            wasInPip = false;
            if (mediaPlayer != null && isPrepared) {
                try { mediaPlayer.pause(); } catch (Exception ignored) {}
            }
            isServiceRunning = false;
            stopService(new Intent(this, MediaPlaybackService.class));
            finish();
        } else {
            wasInPip = false;

            if (isFinishing()) {
                if (mediaPlayer != null && isPrepared && mediaPlayer.isPlaying()
					&& AppPrefs.get(this).isBgAudioEnabled()) {
                    isServiceRunning = true;
                    startService(buildNotifStartIntent());
                } else if (mediaPlayer != null && isPrepared) {
                    try { mediaPlayer.pause(); } catch (Exception ignored) {}
                    updatePlayButton();
                }
            } else {
                isServiceRunning = false;
                stopService(new Intent(this, MediaPlaybackService.class));
                showControls();
                scheduleHide();
                if (mediaPlayer != null && isPrepared && !mediaPlayer.isPlaying()) {
                    mediaPlayer.start();
                    updatePlayButton();
                }
                if (isPrepared) {
                    handler.removeCallbacks(restorerRunnable);
                    handler.post(restorerRunnable);
                }
            }
        }
    }

    

    @Override
    public void onPrepared(MediaPlayer mp) {
        isPrepared = true;
        progressBuffering.setVisibility(View.GONE);
        seekBar.setMax(mp.getDuration());
        txtTotalTime.setText(formatTime(mp.getDuration()));

        int videoW = mp.getVideoWidth();
        int videoH = mp.getVideoHeight();
        if (videoW > 0 && videoH > 0) {
            MediaMetadataRetriever mmr = new MediaMetadataRetriever();
            try {
                mmr.setDataSource(currentVideo.path);
                String rotStr = mmr.extractMetadata(
					MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
                if (rotStr != null) {
                    int rot = Integer.parseInt(rotStr);
                    if (rot == 90 || rot == 270) {
                        int tmp = videoW; videoW = videoH; videoH = tmp;
                    }
                }
            } catch (Exception ignored) {
            } finally {
                try { mmr.release(); } catch (Exception ignored) {}
            }
            glView.setVideoSize(videoW, videoH);
        }

        long resumePos = 0;
        if (lastKnownPositionMs > 0) {
            resumePos = lastKnownPositionMs;
        } else {
            resumePos = RestorerStore.readPosition(restorerFile);
            if (resumePos <= 0) resumePos = currentVideo.lastPosition;
        }

        if (resumePos > 0 && resumePos < mp.getDuration() - 5000) {
            mp.seekTo((int) resumePos);
        }
        applySpeed();
        PlaybackGuard.claim(this, this);
        mp.start();
        lastKnownPositionMs = mp.getCurrentPosition();
        playStartTime = System.currentTimeMillis();
        updatePlayButton();
        handler.post(progressRunnable);
        handler.post(restorerRunnable);
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        savePosition();
        RestorerStore.clearPosition(restorerFile);
        lastKnownPositionMs = 0;
        handler.removeCallbacks(restorerRunnable);
        updatePlayButton();
        showControls();
        handler.removeCallbacks(hideRunnable);
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        Toast.makeText(this, "Error de reproduccion (" + what + ")", Toast.LENGTH_SHORT).show();
        return true;
    }

    

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
        if (restorerFile != null) {
            RestorerStore.writePosition(restorerFile, pos);
            lastRestorerWriteMs = System.currentTimeMillis();
        }
        txtCurrentTime.setText(formatTime(pos));
        resetHide();
    }

    private void cycleSpeed() {
        speedIndex = (speedIndex + 1) % SPEEDS_NUM.length;
        playbackSpeed = SPEEDS_NUM[speedIndex] / 100f;
        AppPrefs.get(this).setPlaybackSpeed(playbackSpeed);
        txtSpeed.setText(SPEEDS_LBL[speedIndex]);
        applySpeed();
        resetHide();
    }

    private void applySpeed() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && mediaPlayer != null && isPrepared) {
            android.media.PlaybackParams pp = new android.media.PlaybackParams();
            pp.setSpeed(playbackSpeed);
            mediaPlayer.setPlaybackParams(pp);
        }
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

    

    private void minimize() {
        if (mediaPlayer != null) {
            MiniPlayerState.set(MiniPlayerState.NORMAL, currentVideo,
								isPrepared ? mediaPlayer.getCurrentPosition() : currentVideo.lastPosition);
        }
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(i);
    }

    private void enterPip() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        if (mediaPlayer != null && isPrepared && restorerFile != null) {
            RestorerStore.writePosition(restorerFile, mediaPlayer.getCurrentPosition());
            lastRestorerWriteMs = System.currentTimeMillis();
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
        ArrayList<RemoteAction> actions = PipActions.videoActions(this, playing);

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
        index.recordPlay(currentVideo.id,
						 System.currentTimeMillis() - playStartTime,
						 mediaPlayer.getCurrentPosition(),
						 mediaPlayer.getDuration());
    }

    private String formatTime(long ms) {
        long s = ms / 1000, h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        return h > 0
			? String.format("%d:%02d:%02d", h, m, sec)
			: String.format("%d:%02d", m, sec);
    }

    private int speedIndexFromFloat(float speed) {
        int t = Math.round(speed * 100);
        for (int i = 0; i < SPEEDS_NUM.length; i++) {
            if (SPEEDS_NUM[i] == t) return i;
        }
        return 2;
    }

    private int gcd(int a, int b) { return b == 0 ? a : gcd(b, a % b); }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
