package com.lexus2026.localtube;

/**
 * Holds the "minimized" player info so MainActivity can render a small
 * mini-player bar after the user taps the minimize button inside
 * PlayerActivity or SeriesPlayerActivity. The real MediaPlayer keeps living
 * inside that (now backgrounded) Activity - this class only stores what's
 * needed to draw the bar and to know which screen to bring back to front.
 *
 * NOTE: reconstructed for this session - see PlaybackGuard.java for context.
 */
public class MiniPlayerState {

    public static final int NONE   = 0;
    public static final int NORMAL = 1;
    public static final int SERIES = 2;

    private static int type = NONE;
    private static VideoItem video;
    private static long position;

    public static void set(int t, VideoItem v, long pos) {
        type = t;
        video = v;
        position = pos;
    }

    public static void clear() {
        type = NONE;
        video = null;
        position = 0;
    }

    public static boolean isActive() {
        return type != NONE && video != null;
    }

    public static int getType() { return type; }
    public static VideoItem getVideo() { return video; }
    public static long getPosition() { return position; }
}
