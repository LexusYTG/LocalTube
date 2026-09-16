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
import java.lang.ref.*;

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
