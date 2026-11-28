package org.oxideshift.corner;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/** Optional upper-display readout. The game surface owns the frame measurement.
 *  Alpha 84: shown whenever the game screen has a measurement. It used to hide by scene (pause menus, intros,
 *  replays) from a pixel classifier, which players saw as the counter dropping out; with frames delivered on the
 *  GPU path there may also be no CPU frame to classify, which hid it entirely. It still steps aside for the gallery. */
final class TopFpsCounter extends TextView implements Runnable {
    private final GameScreen screen;
    private final CornerSession session;
    private boolean running;
    private int previous=-1;

    TopFpsCounter(Context context,GameScreen screen,CornerSession session,Typeface face){
        super(context);
        this.screen=screen;this.session=session;
        setTextSize(TypedValue.COMPLEX_UNIT_DIP,12);
        setTypeface(face);
        setTextColor(0xff39ff14);setBackgroundColor(Color.TRANSPARENT);
        setShadowLayer(2f,1f,1f,0xff000000);   // readable over white rings and menus
        setIncludeFontPadding(false);setSingleLine(true);
        setFocusable(false);setClickable(false);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        setVisibility(INVISIBLE);
    }

    void running(boolean on){
        removeCallbacks(this);running=on;
        if(on)post(this);
        else {previous=-1;setVisibility(INVISIBLE);}
    }
    @Override protected void onDetachedFromWindow(){running(false);super.onDetachedFromWindow();}

    /** Visible whenever there is a measurement (0 when frames stall, which is worth seeing too), except over the
     *  achievements / EA Trax gallery, which covers the game. */
    static boolean visibleFor(int fps,boolean galleryOpen){return fps>=0 && !galleryOpen;}

    @Override public void run(){
        if(!running)return;
        int value=screen.displayedFps();
        boolean visible=visibleFor(value,session!=null && session.cabinetOpen);
        if(visible && value!=previous){setText(Integer.toString(value));previous=value;}
        if(visible){
            // Overlays added after the counter (achievements, EA Trax card) must not cover it.
            ViewGroup parent=getParent() instanceof ViewGroup?(ViewGroup)getParent():null;
            if(parent!=null && parent.indexOfChild(this)!=parent.getChildCount()-1)bringToFront();
        }
        setVisibility(visible?VISIBLE:INVISIBLE);
        postDelayed(this,125);
    }
}
