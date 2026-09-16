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
