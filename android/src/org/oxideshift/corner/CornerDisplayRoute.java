package org.oxideshift.corner;

import android.hardware.display.DisplayManager;
import android.view.Display;

/** Null means keep every required control on the primary display.
 * preferredId is valid only for the current connection/session. */
final class CornerDisplayRoute {
    static Display choose(Display host, DisplayManager manager, int currentId, int preferredId) {
        if (host == null || !host.isValid()) return null;
        Display only = null, current = null, preferred = null;
        int count = 0;
        for (Display d : manager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)) {
            int id = d.getDisplayId();
            if (!d.isValid() || id == host.getDisplayId() || d.getState() == Display.STATE_OFF) continue;
            count++;
            only = d;
            if (id == preferredId) preferred = d;
            if (id == currentId) current = d;
        }
        if (preferred != null) return preferred;
        if (current != null) return current;
        return count == 1 ? only : null;
    }
}

