package org.oxideshift.corner;

import android.view.MotionEvent;
import android.view.View;
import java.util.function.BooleanSupplier;

/** Routes a single treatment finger into the same stick used by the physical controller. */
final class CutmanGesture implements View.OnTouchListener {
    private final CutmanStroke stroke;
    private final BooleanSupplier ready;

    CutmanGesture(CutmanStroke.Stick stick, BooleanSupplier ready) {
        stroke = new CutmanStroke(stick);
        this.ready = ready;
    }

    boolean active() { return stroke.finger >= 0; }

    void release() { stroke.release(); }

    @Override public boolean onTouch(View view, MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            if (!ready.getAsBoolean()) return false;
            stroke.begin(event.getPointerId(0), event.getX(), event.getY());
            return true;
        }
        if (!active()) return false;
        int index = event.findPointerIndex(stroke.finger);
        if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP || index < 0 ||
                (action == MotionEvent.ACTION_POINTER_UP && index == event.getActionIndex())) {
            release();
        } else if (action == MotionEvent.ACTION_MOVE) {
            float travel = Math.min(view.getWidth() * .15f, view.getHeight() * .23f);
            for (int sample = 0; sample < event.getHistorySize(); sample++) {
                stroke.move(stroke.finger, event.getHistoricalX(index, sample), event.getHistoricalY(index, sample), travel);
            }
            stroke.move(stroke.finger, event.getX(index), event.getY(index), travel);
        }
        return true;
    }
}
