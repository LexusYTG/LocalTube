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

public class PlaybackGuard {

    public interface Session {
        void stopPlayback();
    }

    private static Session activeSession;

    
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
