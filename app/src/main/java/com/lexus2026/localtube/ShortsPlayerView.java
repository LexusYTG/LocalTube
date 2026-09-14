package com.lexus2026.localtube;

import android.content.*;
import android.graphics.*;
import android.media.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;

public class ShortsPlayerView extends FrameLayout
        implements SurfaceHolder.Callback, MediaPlayer.OnPreparedListener,
        MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener,
        PlaybackGuard.Session {

    private final VideoIndex index;
    private List<VideoItem> shorts = new ArrayList<VideoItem>();
    private int currentIndex = 0;

    private SurfaceView surfaceView;
    private ProgressBar seekBar;
    private TextView txtTitle;
    private ImageView pauseIcon;
    private MediaPlayer mediaPlayer;
    private boolean isPrepared = false;
    private boolean userPaused = false;
    private boolean surfaceReady = false;
    private long playStartTime = 0;

    private GestureDetector gestureDetector;

    private final android.os.Handler handler = new android.os.Handler();
    private final Runnable progressRunnable = new Runnable() {
        @Override public void run() {
            if (mediaPlayer != null && isPrepared) {
                seekBar.setProgress(mediaPlayer.getCurrentPosition());
            }
            handler.postDelayed(this, 200);
        }
    };

    public ShortsPlayerView(Context ctx, VideoIndex index) {
        super(ctx);
        this.index = index;
        setBackgroundColor(Color.BLACK);
        buildUi();
    }

    private void buildUi() {
        surfaceView = new SurfaceView(getContext());
        addView(surfaceView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        surfaceView.getHolder().addCallback(this);

        txtTitle = new TextView(getContext());
        txtTitle.setTextColor(Color.WHITE);
        txtTitle.setTextSize(14);
        txtTitle.setMaxLines(2);
        txtTitle.setPadding(dp(16), 0, dp(16), 0);
        FrameLayout.LayoutParams titleLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.gravity = Gravity.BOTTOM;
        titleLp.setMargins(0, 0, 0, dp(28));
        addView(txtTitle, titleLp);

        seekBar = new ProgressBar(getContext(), null, android.R.attr.progressBarStyleHorizontal);
        seekBar.setBackgroundColor(0x40FFFFFF); // faint track so the line is visible even at 0%
        FrameLayout.LayoutParams seekLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(3));
        seekLp.gravity = Gravity.BOTTOM;
        addView(seekBar, seekLp);

        // Big centered play icon shown whenever playback is paused, so pausing
        // via tap has a clear, visible confirmation (not just silent).
        pauseIcon = new ImageView(getContext());
        pauseIcon.setImageResource(android.R.drawable.ic_media_play);
        pauseIcon.setColorFilter(Color.WHITE);
        pauseIcon.setBackgroundColor(0x66000000);
        int iconSize = dp(64);
        pauseIcon.setPadding(dp(16), dp(16), dp(16), dp(16));
        FrameLayout.LayoutParams iconLp = new FrameLayout.LayoutParams(iconSize, iconSize);
        iconLp.gravity = Gravity.CENTER;
        addView(pauseIcon, iconLp);
        pauseIcon.setVisibility(View.GONE);

        gestureDetector = new GestureDetector(getContext(),
            new GestureDetector.SimpleOnGestureListener() {
                private static final int SWIPE_THRESHOLD = 80;
                private static final int SWIPE_VELOCITY  = 100;

                @Override
                public boolean onSingleTapConfirmed(MotionEvent e) {
                    togglePlay();
                    return true;
                }

                @Override
                public boolean onFling(MotionEvent e1, MotionEvent e2,
                        float velocityX, float velocityY) {
                    if (e1 == null || e2 == null) return false;
                    float diffY = e2.getY() - e1.getY();
                    if (Math.abs(diffY) > SWIPE_THRESHOLD
                            && Math.abs(velocityY) > SWIPE_VELOCITY) {
                        if (diffY < 0) goNext();
                        else goPrev();
                        return true;
                    }
                    return false;
                }
            });

        setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View v, MotionEvent e) {
                gestureDetector.onTouchEvent(e);
                // Must return true (not just forward the detector's result):
                // GestureDetector.onDown() returns false by default, so if we
                // returned that raw value here, ACTION_DOWN would be reported
                // as "unhandled" and Android would never deliver the follow-up
                // ACTION_MOVE/ACTION_UP for that gesture to this view - which
                // is exactly why taps and swipes weren't registering at all.
                return true;
            }
        });
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    public void setData(List<VideoItem> list, int startIndex) {
        shorts = list != null ? list : new ArrayList<VideoItem>();
        currentIndex = shorts.isEmpty() ? 0 : Math.max(0, Math.min(startIndex, shorts.size() - 1));
        // If the surface isn't ready yet (e.g. this view was just created/attached
        // right after switching to the Shorts tab), don't touch the MediaPlayer now.
        // surfaceCreated() will load the current index once the surface is valid.
        if (surfaceReady) {
            loadCurrent();
        }
    }

    public void jumpTo(int idx) {
        if (shorts.isEmpty()) return;
        currentIndex = Math.max(0, Math.min(idx, shorts.size() - 1));
        // Same race as setData(): jumpTo() can be called before surfaceCreated()
        // has fired. currentIndex is already updated above, so surfaceCreated()
        // will pick it up and load it as soon as the surface becomes valid.
        if (surfaceReady) {
            loadCurrent();
        }
    }

    private void goNext() {
        if (shorts.isEmpty()) return;
        currentIndex = (currentIndex + 1) % shorts.size();
        loadCurrent();
    }

    private void goPrev() {
        if (shorts.isEmpty()) return;
        currentIndex = (currentIndex - 1 + shorts.size()) % shorts.size();
        loadCurrent();
    }

    private void loadCurrent() {
        if (shorts.isEmpty()) return;
        savePosition();
        VideoItem item = shorts.get(currentIndex);
        txtTitle.setText(item.title);
        isPrepared = false;
        userPaused = false;
        seekBar.setProgress(0);
        seekBar.setMax(100);
        pauseIcon.setVisibility(View.GONE);

        if (mediaPlayer == null) {
            mediaPlayer = new MediaPlayer();
        } else {
            mediaPlayer.reset();
        }
        mediaPlayer.setOnPreparedListener(this);
        mediaPlayer.setOnCompletionListener(this);
        mediaPlayer.setOnErrorListener(this);

        try {
            mediaPlayer.setDataSource(item.path);
            // Only attach the Surface and prepare if it's actually valid right
            // now. loadCurrent() is called from several places (swipe, resume,
            // tab switch, surfaceCreated itself) and the SurfaceView's Surface
            // may not exist yet the first time this view is attached to the
            // window - calling setDisplay() on a not-yet-created/released
            // Surface throws IllegalArgumentException. When that's the case we
            // just leave the source set; surfaceCreated() calls loadCurrent()
            // again once the surface is valid, finishing the job then.
            if (surfaceReady && surfaceView.getHolder().getSurface() != null
                    && surfaceView.getHolder().getSurface().isValid()) {
                mediaPlayer.setDisplay(surfaceView.getHolder());
                mediaPlayer.prepareAsync();
            }
        } catch (IOException e) {
            goNext();
        }
    }

    private void updatePauseIcon() {
        if (pauseIcon == null) return;
        boolean playing = mediaPlayer != null && mediaPlayer.isPlaying();
        pauseIcon.setVisibility(playing ? View.GONE : View.VISIBLE);
    }

    public void pausePlayback() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            savePosition();
            mediaPlayer.pause();
        }
        updatePauseIcon();
    }

    public void resumePlayback() {
        if (mediaPlayer != null && isPrepared && !userPaused) {
            mediaPlayer.start();
            playStartTime = System.currentTimeMillis();
            PlaybackGuard.claim(this, this);
        } else if (mediaPlayer == null && !shorts.isEmpty()) {
            loadCurrent();
        }
        updatePauseIcon();
    }

    private void togglePlay() {
        if (mediaPlayer == null || !isPrepared) return;
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            userPaused = true;
        } else {
            mediaPlayer.start();
            userPaused = false;
            PlaybackGuard.claim(this, this);
        }
        updatePauseIcon();
    }

    private void savePosition() {
        if (!isPrepared || mediaPlayer == null || shorts.isEmpty()) return;
        VideoItem item = shorts.get(currentIndex);
        index.recordPlay(item.id,
                System.currentTimeMillis() - playStartTime,
                mediaPlayer.getCurrentPosition(),
                mediaPlayer.getDuration());
    }

    @Override
    public void stopPlayback() {
        pausePlayback();
    }

    public void release() {
        handler.removeCallbacksAndMessages(null);
        savePosition();
        PlaybackGuard.release(this);
        if (mediaPlayer != null) { mediaPlayer.release(); mediaPlayer = null; }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        surfaceReady = true;
        // currentIndex may have been changed by setData()/jumpTo() while the
        // surface wasn't ready yet; load whatever is current now, not just when
        // mediaPlayer is null, so a pending jumpTo() isn't lost.
        if (!shorts.isEmpty()) loadCurrent();
    }

    @Override public void surfaceChanged(SurfaceHolder h, int fmt, int w, int hh) {}

    @Override
    public void surfaceDestroyed(SurfaceHolder h) {
        surfaceReady = false;
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        isPrepared = true;
        seekBar.setMax(mp.getDuration());
        mp.setLooping(false);
        mp.start();
        playStartTime = System.currentTimeMillis();
        handler.post(progressRunnable);
        PlaybackGuard.claim(this, this);
        updatePauseIcon();
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        goNext();
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        goNext();
        return true;
    }
}
