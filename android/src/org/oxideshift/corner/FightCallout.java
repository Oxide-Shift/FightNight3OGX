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
        float rushMs=rocked?260f:210f;
        float rush=(count || reducedMotion)?0f:Math.max(0f,1-age/rushMs);
        float fade=reducedMotion?1f:Math.min(1f,(DURATION-age)/290f);
        float unit=Math.min(w/122f,h/55f)*(1f+.06f*rush*rush);
        float dx=(direction&1)==0?-1f:1f,dy=(direction&2)==0?-1f:1f;
        // Milliseconds since the strike landed (counts land at once).
        float impact=reducedMotion?-1f:count?age:age-rushMs;
        c.save();
        // The strike lands hard: the whole callout shudders for a moment.
        if(!count && impact>=0f && impact<220f){
            float k=1f-impact/220f,s=(float)Math.sin(impact*.11f)*k*k*Math.min(w,h)*.018f;
            c.translate(s*dx,-s*.6f*dy);
        }
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
        // Speed streaks trailing the swipe while it rushes in.
        if(rush>0f){
            ink.setStyle(Paint.Style.STROKE);ink.setStrokeCap(Paint.Cap.ROUND);
            for(int i=0;i<7;i++){
                float y=-13+i*4.3f,len=(26+(i*37%5)*7)*rush,x0=-dx*(54+(i*53%4)*3);
                ink.setStrokeWidth(.6f+(i%3)*.35f);ink.setColor(rocked?0xffdb5345:0xffffc98a);ink.setAlpha(Math.round(200*rush));
                c.drawLine(x0,y,x0-dx*len,y-dy*len*.05f,ink);
            }
            ink.setStyle(Paint.Style.FILL);
        }
        // Impact burst: a ring and spikes thrown out from behind the plate as it lands.
        if(impact>=0f && impact<380f){
            float k=impact/380f,e=1f-(1f-k)*(1f-k)*(1f-k);
            ink.setStyle(Paint.Style.STROKE);ink.setStrokeWidth(3.2f*(1f-k)+.3f);
            ink.setColor(rocked?0xffff8f6b:0xfffff0c8);ink.setAlpha(Math.round(220*(1f-k)*fade));
            float r=(count?16:30)+e*(count?26:46);
            c.drawOval(-r*1.25f,-r*.62f,r*1.25f,r*.62f,ink);
            ink.setStrokeWidth(1.4f*(1f-k)+.2f);
            for(int i=0;i<12;i++){
                double a=i*Math.PI/6+.26;float ca=(float)Math.cos(a),sa=(float)Math.sin(a);
                float r0=r*.9f,r1=r*(1.15f+.25f*((i*7)%3));
                c.drawLine(ca*r0*1.25f,sa*r0*.62f,ca*r1*1.25f,sa*r1*.62f,ink);
            }
            ink.setStyle(Paint.Style.FILL);
            if(impact<90f){
                // A white-hot flash at the instant of contact.
                ink.setShader(new android.graphics.RadialGradient(0,0,60,0xb0fff6e0,0x00fff6e0,android.graphics.Shader.TileMode.CLAMP));
                ink.setAlpha(Math.round(255*(1f-impact/90f)));c.drawOval(-75,-36,75,36,ink);ink.setShader(null);
            }
        }
        ink.setStyle(Paint.Style.FILL);ink.setColor(rocked?0xff651820:0xff201714);ink.setAlpha((int)(246*fade));if(count)c.drawRoundRect(-18,-15,18,15,1,1,ink);else c.drawPath(swipe,ink);
        ink.setColor(rocked?0xffdb5345:0xffc26d38);ink.setAlpha((int)(235*fade));if(count){
            c.drawRect(-18,-15,18,-14,ink);c.drawRect(-18,14,18,15,ink);
        }else c.drawPath(flecks,ink);
        // Letters stamp down after the plate: oversized at contact, settling with a small overshoot.
        float stamp=1f;
        if(impact>=0f && impact<200f){float k=impact/200f;stamp=1f+.34f*(1f-k)*(1f-k)-.05f*(float)Math.sin(Math.PI*k);}
        else if(rush>0f)stamp=1.34f;
        c.save();c.scale(stamp,stamp);
        ink.setTypeface(stencil);ink.setTextAlign(Paint.Align.CENTER);ink.setTextSize(count?28:22);
        float textWidth=ink.measureText(words);
        if(textWidth>87)ink.setTextSize(22*87/textWidth);
        float baseline=-(ink.ascent()+ink.descent())*.5f;
        // The shadow splits wider at contact, then closes up.
        float split=.65f+(impact>=0f && impact<240f?1.6f*(1f-impact/240f):rush*1.2f);
        ink.setColor(0xffaa3425);ink.setAlpha((int)(255*fade));c.drawText(words,split,baseline+split*1.2f,ink);
        ink.setColor(GameInk.TEXT);ink.setAlpha((int)(255*fade));c.drawText(words,0,baseline,ink);
        c.restore();
        c.restore();return true;
    }
}
