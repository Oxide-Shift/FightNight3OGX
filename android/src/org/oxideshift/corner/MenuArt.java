package org.oxideshift.corner;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.View;

/** Drawn pieces for the Achievements and Music top screens. */
final class MenuArt {
    private MenuArt(){}

    /** A category tab: active fill and underline, plus a thin bar showing how much of the category is earned. */
    static final class TabMeter extends Drawable {
        private final Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int have,total;private final boolean active;private final float dp;
        TabMeter(int have,int total,boolean active,float dp){this.have=have;this.total=total;this.active=active;this.dp=dp;}
        static float share(int have,int total){return total<=0?0f:Math.max(0f,Math.min(1f,have/(float)total));}
        @Override public void draw(Canvas c){
            Rect r=getBounds();
            if(active){ink.setColor(0xba581503);c.drawRect(r,ink);}
            float y=r.bottom-5*dp,left=r.left+8*dp,right=r.right-8*dp;
            ink.setColor(active?0x66000000:0x44000000);c.drawRoundRect(left,y,right,y+3*dp,2*dp,2*dp,ink);
            ink.setColor(have>=total && total>0?0xffffd27a:0xffe8ac32);
            c.drawRoundRect(left,y,left+(right-left)*share(have,total),y+3*dp,2*dp,2*dp,ink);
            if(active){ink.setColor(0xffffad28);c.drawRect(r.left,r.bottom-1.5f*dp,r.right,r.bottom,ink);}
        }
        @Override public void setAlpha(int a){}
        @Override public void setColorFilter(ColorFilter f){}
        @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }

    /** Overall progress ring for the Achievements header. */
    static final class Ring extends View {
        private final Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF box=new RectF();
        private final Typeface face;
        private int have,total;
        Ring(Context c,Typeface face){super(c);this.face=face;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        void set(int earned,int all){have=earned;total=all;invalidate();}
        @Override protected void onDraw(Canvas c){
            float d=getResources().getDisplayMetrics().density,w=getWidth(),h=getHeight(),r=Math.min(w,h)/2f-4*d;
            float cx=w/2,cy=h/2;
            ink.setStyle(Paint.Style.STROKE);ink.setStrokeWidth(5*d);ink.setColor(0x66000000);c.drawCircle(cx,cy,r,ink);
            ink.setColor(0xffe8ac32);ink.setStrokeCap(Paint.Cap.ROUND);box.set(cx-r,cy-r,cx+r,cy+r);
            c.drawArc(box,-90,360f*TabMeter.share(have,total),false,ink);ink.setStrokeCap(Paint.Cap.BUTT);
            ink.setStyle(Paint.Style.FILL);ink.setTypeface(face);ink.setTextAlign(Paint.Align.CENTER);ink.setColor(GameInk.TEXT);
            ink.setTextSize(r*.62f);c.drawText(MenuStage.percent(have,total)+"%",cx,cy+r*.22f,ink);
        }
    }

    /** ON / OFF switch with a sliding knob. */
    static final class Switch extends View {
        private final Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Typeface face;
        private final ValueAnimator slide=ValueAnimator.ofFloat(0f,1f);
        private float knob;private boolean on,known;
        Switch(Context c,Typeface face){
            super(c);this.face=face;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            slide.setDuration(180);slide.addUpdateListener(a->{knob=(float)a.getAnimatedValue();invalidate();});
        }
        void set(boolean value){
            if(known && value==on)return;
            boolean animate=known && SheetMotion.on() && isShown();
            on=value;known=true;slide.cancel();
            if(animate){slide.setFloatValues(knob,on?1f:0f);slide.setDuration(260);slide.setInterpolator(new android.view.animation.OvershootInterpolator(2f));slide.start();}
            else{knob=on?1f:0f;invalidate();}
        }
        @Override protected void onDraw(Canvas c){
            float d=getResources().getDisplayMetrics().density,w=getWidth(),h=getHeight(),r=h/2f;
            int track=blend(0xff2a140e,0xff8a4a10,knob);
            ink.setStyle(Paint.Style.FILL);ink.setColor(track);c.drawRoundRect(0,0,w,h,r,r,ink);
            ink.setStyle(Paint.Style.STROKE);ink.setStrokeWidth(1.5f*d);ink.setColor(blend(0x66a58a6c,0xffe8ac32,knob));c.drawRoundRect(0,0,w,h,r,r,ink);
            ink.setStyle(Paint.Style.FILL);ink.setTypeface(face);ink.setTextSize(h*.42f);
            ink.setColor(on?GameInk.TEXT:GameInk.MUTED);ink.setTextAlign(on?Paint.Align.LEFT:Paint.Align.RIGHT);
            c.drawText(on?"ON":"OFF",on?r*.7f:w-r*.7f,h*.66f,ink);
            float kx=r+(w-2*r)*knob;
            ink.setColor(0x66000000);c.drawCircle(kx,r+d,r-3*d,ink);
            ink.setColor(blend(0xffa58a6c,0xffffd27a,knob));c.drawCircle(kx,r,r-3*d,ink);
        }
        static int blend(int a,int b,float t){
            t=Math.max(0f,Math.min(1f,t));
            int aa=a>>>24,ar=a>>16&255,ag=a>>8&255,ab=a&255,ba=b>>>24,br=b>>16&255,bg=b>>8&255,bb=b&255;
            return Math.round(aa+(ba-aa)*t)<<24|Math.round(ar+(br-ar)*t)<<16|Math.round(ag+(bg-ag)*t)<<8|Math.round(ab+(bb-ab)*t);
        }
    }

    /** Lower-screen music transport: Back, Play/Pause, Next as three touch keys. Back and Next are stencil plates
     *  with double chevrons; the centre key is a gold disc whose glyph morphs between pause bars and a play
     *  triangle. A press dips the key and flashes its rim. */
    static final class Transport extends View {
        interface Action {void run(int action);}
        private final Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path glyph=new Path();
        private final Typeface face;
        private final Action action;
        private final ValueAnimator morph=ValueAnimator.ofFloat(0f,1f),press=ValueAnimator.ofFloat(1f,0f);
        private float playShape;private boolean paused,known;
        private int pressed=-2;private float pressFade;
        Transport(Context c,Typeface face,Action action){
            super(c);this.face=face;this.action=action;
            morph.setDuration(220);morph.addUpdateListener(a->{playShape=(float)a.getAnimatedValue();invalidate();});
            press.setDuration(260);press.addUpdateListener(a->{pressFade=(float)a.getAnimatedValue();invalidate();});
            setContentDescription("Music controls: back, pause, next");
        }
        void paused(boolean value){
            if(known && value==paused)return;
            boolean animate=known && SheetMotion.on() && isShown();
            paused=value;known=true;morph.cancel();
            // Paused shows the play triangle (what a tap will do); playing shows pause bars.
            if(animate){morph.setFloatValues(playShape,paused?1f:0f);morph.setInterpolator(SheetMotion.settle());morph.start();}
            else{playShape=paused?1f:0f;invalidate();}
        }
        private int key(float x){float w=getWidth();return x<w*.34f?-1:x>w*.66f?1:0;}
        @Override public boolean onTouchEvent(android.view.MotionEvent e){
            if(e.getActionMasked()==android.view.MotionEvent.ACTION_DOWN){
                pressed=key(e.getX());press.cancel();pressFade=1f;invalidate();return true;
            }
            if(e.getActionMasked()==android.view.MotionEvent.ACTION_UP){
                int k=key(e.getX());
                if(k==pressed){action.run(k);performClick();}
                if(SheetMotion.on())press.start();else{pressFade=0f;invalidate();}
                return true;
            }
            if(e.getActionMasked()==android.view.MotionEvent.ACTION_CANCEL){pressFade=0f;pressed=-2;invalidate();}
            return true;
        }
        @Override public boolean performClick(){return super.performClick();}
        @Override protected void onDraw(Canvas c){
            float d=getResources().getDisplayMetrics().density,w=getWidth(),h=getHeight();
            ink.setStyle(Paint.Style.FILL);ink.setShader(null);
            ink.setColor(0xff1d0d09);c.drawRect(0,0,w,h,ink);
            ink.setColor(0xff75391e);c.drawRect(0,0,w,1.5f*d,ink);
            float cy=h/2f,r=Math.min(h*.36f,w*.1f);
            for(int k=-1;k<=1;k++){
                float cx=w/2f+k*w*.3f,dip=pressed==k?pressFade:0f,s=1f-.06f*dip;
                c.save();c.scale(s,s,cx,cy);
                if(k==0){
                    ink.setColor(0x66000000);c.drawCircle(cx,cy+2*d,r,ink);
                    ink.setColor(Switch.blend(0xffe8ac32,0xffffd27a,dip));c.drawCircle(cx,cy,r,ink);
                    ink.setColor(0xff7a4a12);c.drawCircle(cx,cy,r*.84f,ink);
                    ink.setColor(0xffffe8b0);drawPlayPause(c,cx,cy,r*.42f);
                }else{
                    float pw=w*.2f,ph=h*.56f;
                    ink.setColor(Switch.blend(0xff3a1a10,0xff6a2e14,dip));c.drawRoundRect(cx-pw/2,cy-ph/2,cx+pw/2,cy+ph/2,6*d,6*d,ink);
                    ink.setStyle(Paint.Style.STROKE);ink.setStrokeWidth(1.5f*d);ink.setColor(Switch.blend(0x99a58a6c,0xffffd27a,dip));
                    c.drawRoundRect(cx-pw/2,cy-ph/2,cx+pw/2,cy+ph/2,6*d,6*d,ink);ink.setStyle(Paint.Style.FILL);
                    ink.setColor(GameInk.GOLD);
                    float a=ph*.2f,gx=cx-pw*.22f;
                    for(int n=0;n<2;n++){
                        float x=gx+(k<0?-n:n)*a*.9f;
                        glyph.reset();
                        if(k>0){glyph.moveTo(x-a*.5f,cy-a);glyph.lineTo(x+a*.5f,cy);glyph.lineTo(x-a*.5f,cy+a);}
                        else{glyph.moveTo(x+a*.5f,cy-a);glyph.lineTo(x-a*.5f,cy);glyph.lineTo(x+a*.5f,cy+a);}
                        glyph.close();c.drawPath(glyph,ink);
                    }
                    ink.setTypeface(face);ink.setTextSize(Math.min(ph*.3f,16*getResources().getDisplayMetrics().scaledDensity));
                    ink.setTextAlign(Paint.Align.LEFT);ink.setColor(GameInk.TEXT);
                    c.drawText(k<0?"BACK":"NEXT",cx+pw*.02f,cy+ink.getTextSize()*.36f,ink);
                }
                c.restore();
            }
        }
        /** Pause bars at 0, a play triangle at 1: the two bars' outer corners slide into the triangle's shape. */
        private void drawPlayPause(Canvas c,float cx,float cy,float s){
            float t=playShape;
            float barW=s*.38f,gap=s*.28f;
            // Left bar becomes the left half of the triangle, right bar the right half.
            float lx0=cx-gap/2-barW,lx1=cx-gap/2,rx0=cx+gap/2,rx1=cx+gap/2+barW;
            float tx0=cx-s*.42f,tmid=cx+s*.12f,tx1=cx+s*.68f;
            float aL=lerp(lx0,tx0,t),bL=lerp(lx1,tmid,t),aR=lerp(rx0,tmid,t),bR=lerp(rx1,tx1,t);
            float midTopL=lerp(cy-s,cy-s*.5f,t),midTopR=lerp(cy-s,cy-s*.5f,t),tipTop=lerp(cy-s,cy,t);
            glyph.reset();
            glyph.moveTo(aL,cy-s);glyph.lineTo(bL,midTopL);glyph.lineTo(bL,lerp(cy+s,cy+s*.5f,t));glyph.lineTo(aL,cy+s);glyph.close();
            glyph.moveTo(aR,midTopR);glyph.lineTo(bR,tipTop);glyph.lineTo(bR,lerp(cy+s,cy,t));glyph.lineTo(aR,lerp(cy+s,cy+s*.5f,t));glyph.close();
            c.drawPath(glyph,ink);
        }
        private static float lerp(float a,float b,float t){return a+(b-a)*t;}
    }

    /** Card overlay: earned cards get a gold rim and corner tick; locked cards a dim veil and a small padlock. */
    static final class CardMark extends Drawable {
        private final Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final boolean earned;private final float dp;
        CardMark(boolean earned,float dp){this.earned=earned;this.dp=dp;}
        @Override public void draw(Canvas c){
            Rect r=getBounds();
            if(earned){
                ink.setStyle(Paint.Style.STROKE);ink.setStrokeWidth(1.5f*dp);ink.setColor(0xccffc24a);
                c.drawRect(r.left+dp,r.top+dp,r.right-dp,r.bottom-dp,ink);
                ink.setStyle(Paint.Style.FILL);ink.setColor(0xffe8ac32);
                Path tab=new Path();tab.moveTo(r.right-18*dp,r.top);tab.lineTo(r.right,r.top);tab.lineTo(r.right,r.top+18*dp);tab.close();c.drawPath(tab,ink);
                ink.setStyle(Paint.Style.STROKE);ink.setStrokeWidth(1.6f*dp);ink.setColor(0xff3a1a10);ink.setStrokeCap(Paint.Cap.ROUND);
                c.drawLine(r.right-10*dp,r.top+6*dp,r.right-7.5f*dp,r.top+8.5f*dp,ink);c.drawLine(r.right-7.5f*dp,r.top+8.5f*dp,r.right-3*dp,r.top+3.5f*dp,ink);
            }else{
                ink.setStyle(Paint.Style.FILL);ink.setColor(0x30000000);c.drawRect(r,ink);
                float x=r.right-14*dp,y=r.top+8*dp;
                ink.setColor(0x99a58a6c);c.drawRoundRect(x-5*dp,y+3*dp,x+5*dp,y+10*dp,1.5f*dp,1.5f*dp,ink);
                ink.setStyle(Paint.Style.STROKE);ink.setStrokeWidth(1.6f*dp);
                c.drawArc(x-3.5f*dp,y-1*dp,x+3.5f*dp,y+6*dp,180,180,false,ink);
            }
        }
        @Override public void setAlpha(int a){}
        @Override public void setColorFilter(ColorFilter f){}
        @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }

    /** A sliver of record groove in the margin of the Music page (visual study p. 9). Decorative. */
    static final class Groove extends Drawable {
        private final Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);
        @Override public void draw(Canvas c){
            Rect r=getBounds();float cx=r.right+r.height()*.22f,cy=r.centerY(),R=r.height()*.62f;
            ink.setStyle(Paint.Style.FILL);ink.setColor(0xcc0c0807);c.drawCircle(cx,cy,R,ink);
            ink.setStyle(Paint.Style.STROKE);ink.setStrokeWidth(1f);
            for(float g=R*.35f;g<R*.98f;g+=R*.03f){ink.setColor(((int)(g/(R*.03f)))%2==0?0x1effffff:0x10ffffff);c.drawCircle(cx,cy,g,ink);}
            ink.setStyle(Paint.Style.FILL);ink.setColor(0xffe8ac32);c.drawCircle(cx,cy,R*.3f,ink);
        }
        @Override public void setAlpha(int a){}
        @Override public void setColorFilter(ColorFilter f){}
        @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
}
