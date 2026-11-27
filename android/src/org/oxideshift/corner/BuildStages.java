package org.oxideshift.corner;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.View;

/** Prepare game: the four build stages as a strip of plates (Prepare, Translate, Compile, Finish). Done plates are
 *  gold, the current one carries a moving stripe and a slow pulse, later ones wait dimmed. Still with animations
 *  off. The stage comes from FightMenu.buildPhase ("3/4 · Compile"). */
final class BuildStages extends View {
    static final String[] NAMES={"PREPARE","TRANSLATE","COMPILE","FINISH"};
    private final Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path plate=new Path();
    private Typeface face=Typeface.DEFAULT_BOLD;
    private int stage=-1;
    long frozenAt=-1;   // previews

    BuildStages(Context c){super(c);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
    void font(Typeface t){if(t!=null){face=t;invalidate();}}
    /** 0-3 from a phase label such as "3/4 · Compile", -1 for anything else. */
    static int stageOf(String phase){
        if(phase==null || phase.length()<3 || phase.charAt(1)!='/')return -1;
        int n=phase.charAt(0)-'1';
        return n>=0 && n<4?n:-1;
    }
    void stage(int next){if(next!=stage){stage=next;invalidate();}}
    private float dp(float n){return n*getResources().getDisplayMetrics().density;}

    @Override protected void onDraw(Canvas c){
        float w=getWidth(),h=getHeight();if(w<=0||h<=0)return;
        long t=frozenAt>=0?frozenAt:SystemClock.uptimeMillis();
        boolean moving=frozenAt>=0 || ValueAnimator.areAnimatorsEnabled();
        float gap=dp(6),slant=dp(8),pw=(w-gap*3)/4f,ph=h*.52f;
        ink.setTypeface(face);ink.setTextAlign(Paint.Align.CENTER);ink.setTextSize(Math.min(h*.26f,dp(12)));ink.setLetterSpacing(.08f);
        for(int i=0;i<4;i++){
            float x=i*(pw+gap);
            plate.reset();plate.moveTo(x+slant,0);plate.lineTo(x+pw,0);plate.lineTo(x+pw-slant,ph);plate.lineTo(x,ph);plate.close();
            boolean done=i<stage,current=i==stage;
            ink.setShader(null);
            ink.setColor(done?0xffd9a441:current?0xff7a2a1a:0xff2c201a);
            c.drawPath(plate,ink);
            if(current){
                // Pulse and a stripe sweeping across the working stage.
                float pulse=moving?.5f+.5f*(float)Math.sin(t/260.0):1f;
                ink.setColor(Color.argb(Math.round(70+80*pulse),246,191,85));c.drawPath(plate,ink);
                if(moving){
                    float sx=x-pw*.4f+((t%1400)/1400f)*(pw*1.8f);
                    ink.setShader(new LinearGradient(sx-pw*.25f,0,sx+pw*.25f,0,new int[]{0x00fff3d6,0x90fff3d6,0x00fff3d6},null,Shader.TileMode.CLAMP));
                    c.save();c.clipPath(plate);c.drawRect(x,0,x+pw,ph,ink);c.restore();ink.setShader(null);
                }
            }
            ink.setColor(done?0xfff6bf55:current?GameInk.TEXT:0xff7a6a5c);
            c.drawText(NAMES[i],x+pw/2f,h-dp(1),ink);
        }
        if(moving && stage>=0 && stage<4 && isShown())postInvalidateOnAnimation();
    }
}
