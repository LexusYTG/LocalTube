package com.lexus2026.localtube;

/**
 * Makes sure only one playback session (PlayerActivity, SeriesPlayerActivity,
 * or the embedded ShortsPlayerView) is actually producing audio/video at a
 * time. Whoever claims playback stops whichever session held it before.
 *
 * NOTE: this class was referenced (PlaybackGuard.Session / .claim / .release)
 * by the files handed over for this session but was not itself included in
 * the upload, so it was reconstructed here from those call sites. Please
 * double check it matches whatever version you had before.
 */
public class PlaybackGuard {

    public interface Session {
        void stopPlayback();
    }

    private static Session activeSession;

    /**
     * Called when a session starts/resumes playback. "owner" is accepted for
     * call-site symmetry (every caller currently passes "this, this") but is
     * not otherwise used - the session reference is what matters.
     */
    public static void claim(Object owner, Session session) {
        if (activeSession != null && activeSession != session) {
            activeSession.stopPlayback();
        }
        activeSession = session;
    }

    public static void release(Session session) {
        if (activeSession == session) {
            activeSession = null;
        }
    }
}
