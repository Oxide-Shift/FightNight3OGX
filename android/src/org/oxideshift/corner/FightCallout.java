package org.oxideshift.corner;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import java.util.Random;

/** A short lower-screen stencil strike, painted over the desk without taking input. */
final class FightCallout {
    static final long DURATION=BoutAnnouncements.DURATION;
    private final Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path swipe=new Path(), flecks=new Path();
    private String words;
    private long started;
    private int impactCorner;
    private int direction;
    private boolean count,reducedMotion,rocked;
    private int rockedCorner;

    FightCallout(){
        swipe.moveTo(-50,-11);swipe.lineTo(43,-14);swipe.lineTo(49,-10);
        swipe.lineTo(47,-6);swipe.lineTo(52,-6);swipe.lineTo(47,10);
        swipe.lineTo(-43,14);swipe.lineTo(-48,11);swipe.close();
        Random spray=new Random(314159);
        for(int i=0;i<100;i++){
            float x=spray.nextFloat()*103-52;
            float y=(i%2==0?1:-1)*(9+spray.nextFloat()*7)-x*.035f;
            flecks.addCircle(x,y,.12f+spray.nextFloat()*.54f,Path.Direction.CW);
        }
    }
    void start(String text,long time){
        words=text;started=time;
        reducedMotion=!ValueAnimator.areAnimatorsEnabled();
        count=text.length()<=2 && Character.isDigit(text.charAt(0));
        rocked="ROCKED!!".equals(text);
        if(rocked){
            direction=rockedCorner^(rockedCorner>>1);
            rockedCorner=(rockedCorner+1)&3;
        }else if(text.startsWith("STAGGER") || text.startsWith("KNOCK") || text.startsWith("DOWN")){
            // Walk around the four corners. Consecutive strikes have opposite slopes.
            direction=impactCorner^(impactCorner>>1);
            impactCorner=(impactCorner+1)&3;
        }else if(!count)direction^=1;
    }
    void clear(){words=null;}
    boolean isRocked(){return words!=null && rocked;}
    boolean draw(Canvas c,int w,int h,Typeface stencil,long now,android.graphics.Rect rockedArea){
        if(words==null || w==0 || h==0)return false;
        long age=now-started;
        if(age>=DURATION || age<0){clear();return false;}
        float rush=(count || reducedMotion)?0f:Math.max(0f,1-age/(rocked?260f:210f));
        float fade=reducedMotion?1f:Math.min(1f,(DURATION-age)/290f);
        float unit=Math.min(w/122f,h/55f)*(1f+.06f*rush*rush);
        float dx=(direction&1)==0?-1f:1f,dy=(direction&2)==0?-1f:1f;
        c.save();
        if(rocked && rockedArea!=null && !rockedArea.isEmpty()){
            // The HUD has already laid out its numbers and meters at the user's text size.
            // Spray enters from beyond this area and becomes fully visible as it settles.
            c.clipRect(rockedArea);
            unit=Math.min(rockedArea.width()/122f,rockedArea.height()/43f)*(1f+.06f*rush*rush);
            c.translate(rockedArea.exactCenterX()+dx*rockedArea.width()*.39f*rush*rush,
                    rockedArea.exactCenterY()+dy*rockedArea.height()*.52f*rush*rush);
        }else{
            c.translate(w*.5f+dx*w*(rocked?.39f:.26f)*rush*rush,
                    h*(rocked?.76f:.52f)+dy*h*(rocked?.15f:.21f)*rush*rush);
        }
        c.rotate(count || reducedMotion?0f:dx*dy*(4f+3f*rush));c.scale(unit,unit);
        ink.setStyle(Paint.Style.FILL);ink.setColor(rocked?0xff651820:0xff201714);ink.setAlpha((int)(246*fade));if(count)c.drawRoundRect(-18,-15,18,15,1,1,ink);else c.drawPath(swipe,ink);
        ink.setColor(rocked?0xffdb5345:0xffc26d38);ink.setAlpha((int)(235*fade));if(count){
            c.drawRect(-18,-15,18,-14,ink);c.drawRect(-18,14,18,15,ink);
        }else c.drawPath(flecks,ink);
        ink.setTypeface(stencil);ink.setTextAlign(Paint.Align.CENTER);ink.setTextSize(count?28:22);
        float textWidth=ink.measureText(words);
        if(textWidth>87)ink.setTextSize(22*87/textWidth);
        float baseline=-(ink.ascent()+ink.descent())*.5f;
        ink.setColor(0xffaa3425);ink.setAlpha((int)(255*fade));c.drawText(words,.65f,baseline+.8f,ink);
        ink.setColor(GameInk.TEXT);ink.setAlpha((int)(255*fade));c.drawText(words,0,baseline,ink);
        c.restore();return true;
    }
}
