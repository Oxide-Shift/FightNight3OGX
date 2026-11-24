package org.oxideshift.corner;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;

/** Optional upper-display readout. The game surface owns the frame measurement. */
final class TopFpsCounter extends TextView implements Runnable {
    private final GameScreen screen;
    private final CornerSession session;
    private boolean running,pausedMenu;
    private int previous=-1;

    TopFpsCounter(Context context,GameScreen screen,CornerSession session){
        super(context);
        this.screen=screen;this.session=session;
        setTextSize(TypedValue.COMPLEX_UNIT_DIP,12);
        setTypeface(Typeface.MONOSPACE,Typeface.BOLD);
        setTextColor(0xff39ff14);setBackgroundColor(Color.TRANSPARENT);
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

    boolean showInScene(LiveFrame.Scene scene,boolean bout,boolean pauseTitle,boolean cameraShot){
        boolean playing=scene==LiveFrame.Scene.ROUND || scene==LiveFrame.Scene.CORNER ||
                scene==LiveFrame.Scene.TREATMENT;
        if(pauseTitle || scene==LiveFrame.Scene.PAUSE)pausedMenu=true;
        else if(playing || scene==LiveFrame.Scene.LOADING || (!bout && scene==LiveFrame.Scene.MENU))pausedMenu=false;
        // A pause subpage can lose the pause title and the round HUD together.
        return !pausedMenu && (!bout || playing || (scene==LiveFrame.Scene.RINGSIDE && cameraShot));
    }
    @Override public void run(){
        if(!running)return;
        int value=screen.displayedFps();
        LiveFrame frame=screen.latestFrame();
        boolean visible=false;
        if(value>=0 && frame!=null && !session.cabinetOpen){
            boolean bout=session.inLiveBout();
            LiveFrame.Scene scene=frame.scene(bout);
            visible=showInScene(scene,bout,frame.pauseRow()>=0,session.cameraShot()!=0);
            if(!screen.current(frame))visible=getVisibility()==VISIBLE;
        }
        if(visible && value!=previous){setText(Integer.toString(value));previous=value;}
        setVisibility(visible?VISIBLE:INVISIBLE);
        postDelayed(this,125);
    }
}
