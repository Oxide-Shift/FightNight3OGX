package org.oxideshift.corner;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Fight Record, lower screen: the punch records while the top screen carries the fight record. Three panels with tabs:
 *  Accuracy (gauges for you and your opponents, head/body split), Punch mix (a donut of the eight retail punch types)
 *  and Where you land (target areas on a boxer's head and body in the damage map's green-to-red heat). Numbers count up
 *  and shapes grow in when the panel or book changes; still with animations off. */
final class FightRecordStage extends View {
    /** The punch records live on the lower screen (the fight record sits on the top screen): three panels. */
    static final String[] SECTIONS={"ACCURACY","PUNCH MIX","WHERE YOU LAND"};
    private java.util.function.IntConsumer pick;
    /** Tapping a panel tab reports its index. */
    void onPick(java.util.function.IntConsumer listener){pick=listener;setClickable(listener!=null);}
    private final RectF[] tabs={new RectF(),new RectF(),new RectF()};
    @Override public boolean onTouchEvent(android.view.MotionEvent e){
        if(pick!=null && e.getActionMasked()==android.view.MotionEvent.ACTION_UP)
            for(int i=0;i<tabs.length;i++)if(tabs[i].contains(e.getX(),e.getY())){pick.accept(i);return true;}
        return pick!=null || super.onTouchEvent(e);
    }
    static final String[] MIX_NAMES={"Jab","Straight right","Left hook","Right hook","Left uppercut","Right uppercut","Signature","Illegal"};
    static final FightRecord.Stat[] MIX={FightRecord.Stat.JAB,FightRecord.Stat.STRAIGHT,FightRecord.Stat.HOOK_L,FightRecord.Stat.HOOK_R,
            FightRecord.Stat.UPPER_L,FightRecord.Stat.UPPER_R,FightRecord.Stat.SIGNATURE,FightRecord.Stat.ILLEGAL};
    private static final int[] MIX_COLORS={0xfff6bf55,0xffe89a3c,0xffd8722e,0xffc04f26,0xff9f3a22,0xff7f2c1e,0xff89bced,0xff8a8a8a};
    private final Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box=new RectF();
    private final Path path=new Path();
    private final Typeface stencil,body;
    private final float dp;
    private final ValueAnimator grow=ValueAnimator.ofFloat(0f,1f);
    private float f=1f;
    private FightRecord record;
    private boolean career=true;
    private int section;

    FightRecordStage(Context c,DiscFonts fonts){
        super(c);stencil=fonts.stencil;body=fonts.body;dp=getResources().getDisplayMetrics().density;
        grow.setDuration(620);grow.setInterpolator(SheetMotion.settle());
        grow.addUpdateListener(a->{f=(float)a.getAnimatedValue();invalidate();});
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }
    /** Direction of the last panel change (+1 next, -1 previous): the new panel slides in from that side. */
    private int slide;
    /** Ease with a small overshoot, for arcs that should land with a kick. */
    static float overshoot(float t){float s=1.6f;t-=1f;return t*t*((s+1)*t+s)+1f;}
    /** 0..1 for an item that starts at `delay` (in units of the whole reveal) and takes `span` of it. */
    static float stagger(float f,float delay,float span){return Math.max(0f,Math.min(1f,(f-delay)/span));}
    void show(FightRecord data,boolean careerBook,int selected){
        boolean changed=data!=record || careerBook!=career || selected!=section;
        int next=Math.max(0,Math.min(SECTIONS.length-1,selected));
        slide=next==section?0:(next-section==1 || next-section==1-SECTIONS.length)?1:-1;
        record=data;career=careerBook;section=next;
        setContentDescription((careerBook?"Career":"All bouts")+", "+SECTIONS[section].toLowerCase(java.util.Locale.ROOT));
        if(changed){
            if(SheetMotion.on() && isShown()){grow.cancel();f=0f;grow.start();}else f=1f;
        }
        invalidate();
    }
    @Override protected void onDetachedFromWindow(){grow.cancel();f=1f;super.onDetachedFromWindow();}

    private long v(FightRecord.Stat s){return record==null?0:record.get(career,s);}
    private String n(double value){return String.format(java.util.Locale.US,"%,d",Math.round(value));}
    private void text(Canvas c,String s,float x,float y,Typeface face,float size,int color,Paint.Align align){
        ink.setStyle(Paint.Style.FILL);ink.setShader(null);ink.setTypeface(face);ink.setTextSize(size*dp);ink.setColor(color);ink.setTextAlign(align);
        c.drawText(s,x,y,ink);
    }
    private float fit(String s,Typeface face,float size,float width){
        ink.setTypeface(face);ink.setTextSize(size*dp);float span=ink.measureText(s);
        return span>width && span>0?size*width/span:size;
    }

    @Override protected void onDraw(Canvas c){
        float w=getWidth(),h=getHeight();if(w<=0||h<=0)return;
        // Opaque first: the cascading rows leave the shared paint at a low alpha, which would let the screen below
        // show through the background on the next frame.
        ink.setStyle(Paint.Style.FILL);ink.setColor(0xff000000);
        ink.setShader(new RadialGradient(w*.5f,h*.4f,Math.max(w,h)*.8f,0xff3d1a10,0xff120806,Shader.TileMode.CLAMP));
        c.drawRect(0,0,w,h,ink);ink.setShader(null);
        boolean live=SheetMotion.on() && isShown();
        long t=android.os.SystemClock.uptimeMillis();
        if(live){
            // Two arena spotlights drifting slowly across the back wall.
            for(int k=0;k<2;k++){
                float phase=(float)((t/(k==0?9000.0:13000.0)+k*.37)%1.0);
                float x=-w*.3f+phase*w*1.6f,spread=w*.18f;
                path.reset();path.moveTo(x-spread*.15f,0);path.lineTo(x+spread*.15f,0);path.lineTo(x+spread,h);path.lineTo(x-spread,h);path.close();
                ink.setShader(new LinearGradient(0,0,0,h,0x1effd8a0,0x00ffd8a0,Shader.TileMode.CLAMP));
                c.drawPath(path,ink);ink.setShader(null);
            }
        }
        ink.setColor(GameInk.ORANGE);c.drawRect(0,0,w,2*dp,ink);
        float pad=16*dp;
        text(c,(career?"CAREER":"ALL BOUTS")+" PUNCH RECORD",pad,pad+10*dp,stencil,12,GameInk.GOLD,Paint.Align.LEFT);
        // Panel tabs: tap or D-pad left/right. The active one carries a gold bar.
        float tabTop=pad+20*dp,tabH=30*dp,tabW=(w-2*pad)/SECTIONS.length;
        for(int i=0;i<SECTIONS.length;i++){
            float x=pad+i*tabW;tabs[i].set(x,tabTop,x+tabW,tabTop+tabH);
            boolean on=i==section;
            ink.setColor(on?0xff5a2414:0xff2a140d);c.drawRect(x+2*dp,tabTop,x+tabW-2*dp,tabTop+tabH,ink);
            if(on){ink.setColor(GameInk.GOLD);c.drawRect(x+2*dp,tabTop+tabH-3*dp,x+2*dp+(tabW-4*dp)*f,tabTop+tabH,ink);}
            text(c,SECTIONS[i],x+tabW/2,tabTop+tabH/2+5*dp,stencil,fit(SECTIONS[i],stencil,13,tabW-12*dp),on?0xffffc137:0xffb8957a,Paint.Align.CENTER);
        }
        float top=tabTop+tabH+12*dp;
        if(v(FightRecord.Stat.BOUTS)==0){
            text(c,"NO BOUTS YET",w/2,top+(h-top)*.42f,stencil,28,GameInk.TEXT,Paint.Align.CENTER);
            text(c,career?"Finish a career fight to start this book.":"Finish any fight to start this book.",w/2,top+(h-top)*.42f+28*dp,body,15,GameInk.MUTED,Paint.Align.CENTER);
            if(live)postInvalidateOnAnimation();
            return;
        }
        // The new panel slides in from the side it was paged toward.
        c.save();
        c.clipRect(0,top-4*dp,w,h);
        if(slide!=0)c.translate((1f-f)*(1f-f)*slide*w*.25f,0);
        switch(section){
            case 0:punches(c,w,h,top,pad);break;
            case 1:mix(c,w,h,top,pad);break;
            default:target(c,w,h,top,pad,t,live);break;
        }
        c.restore();
        if(live)postInvalidateOnAnimation();
    }

    private void gauge(Canvas c,float cx,float cy,float r,int percent,int color,String title,String detail){
        ink.setStyle(Paint.Style.STROKE);ink.setStrokeWidth(r*.2f);ink.setStrokeCap(Paint.Cap.ROUND);
        box.set(cx-r,cy-r,cx+r,cy+r);
        ink.setColor(0xff4a2419);c.drawArc(box,180,180,false,ink);
        // The arc kicks slightly past its value and settles; a glowing tip rides its end.
        float sweep=180f*Math.max(0,percent)/100f*Math.min(1.06f,overshoot(f));
        ink.setColor(color);c.drawArc(box,180,sweep,false,ink);
        ink.setStyle(Paint.Style.FILL);ink.setStrokeCap(Paint.Cap.BUTT);
        if(percent>0){
            double a=Math.toRadians(180+sweep);
            float tx=cx+(float)Math.cos(a)*r,tyy=cy+(float)Math.sin(a)*r;
            ink.setShader(new RadialGradient(tx,tyy,r*.32f,(0xa0<<24)|(color&0xffffff),color&0xffffff,Shader.TileMode.CLAMP));
            c.drawCircle(tx,tyy,r*.32f,ink);ink.setShader(null);
            ink.setColor(0xfffff3d6);c.drawCircle(tx,tyy,r*.07f,ink);
        }
        text(c,percent<0?"—":Math.round(percent*f)+"%",cx,cy-4*dp,stencil,fit("100%",stencil,30,r*1.3f),GameInk.TEXT,Paint.Align.CENTER);
        text(c,title,cx,cy+18*dp,stencil,12,color,Paint.Align.CENTER);
        text(c,detail,cx,cy+34*dp,body,13,GameInk.MUTED,Paint.Align.CENTER);
    }
    private void punches(Canvas c,float w,float h,float top,float pad){
        long thrown=v(FightRecord.Stat.THROWN),landed=v(FightRecord.Stat.LANDED),taken=v(FightRecord.Stat.ABSORBED),against=v(FightRecord.Stat.OPP_THROWN);
        float r=Math.min((w-3*pad)/4.4f,h*.24f),cy=top+r+12*dp;
        gauge(c,w*.27f,cy,r,FightRecord.percent(landed,thrown),GameInk.GOLD,"YOUR ACCURACY",n(landed*f)+" of "+n(thrown*f));
        gauge(c,w*.73f,cy,r,FightRecord.percent(taken,against),0xffff7865,"OPPONENTS",n(taken*f)+" of "+n(against*f));
        // Stat tiles between the gauges and the head/body bar.
        long bouts=Math.max(1,v(FightRecord.Stat.BOUTS));
        String[] tileValues={n(thrown*f),n(landed*f),n(taken*f),n(landed/(double)bouts*f)};
        String[] tileLabels={"THROWN","LANDED","TAKEN","LANDED / BOUT"};
        float ty=cy+48*dp,th=Math.min(52*dp,h-pad-62*dp-ty),gap=6*dp,tw=(w-2*pad-3*gap)/4f;
        if(th>28*dp)for(int i=0;i<4;i++){
            // Tiles pop in one after another.
            float pop=stagger(f,.12f+i*.1f,.45f);if(pop<=0f)continue;
            float x=pad+i*(tw+gap),scale=.7f+.3f*Math.min(1.05f,overshoot(pop));
            c.save();c.scale(scale,scale,x+tw/2,ty+th/2);
            box.set(x,ty,x+tw,ty+th);
            ink.setColor(0xff2a140d);c.drawRoundRect(box,6*dp,6*dp,ink);
            ink.setColor(i==2?0xffff7865:GameInk.GOLD);c.drawRect(x,ty,x+3*dp,ty+th,ink);
            text(c,tileValues[i],x+tw/2,ty+th*.55f,stencil,fit(tileValues[i],stencil,20,tw-12*dp),GameInk.TEXT,Paint.Align.CENTER);
            text(c,tileLabels[i],x+tw/2,ty+th-7*dp,stencil,fit(tileLabels[i],stencil,9,tw-8*dp),GameInk.MUTED,Paint.Align.CENTER);
            c.restore();
        }
        long head=v(FightRecord.Stat.HEAD),bodyShots=v(FightRecord.Stat.BODY),all=Math.max(1,head+bodyShots);
        float by=h-pad-20*dp,bh=12*dp,left=pad,right=w-pad,split=left+(right-left)*(head/(float)all)*f;
        text(c,"HEAD "+FightRecord.percent(head,head+bodyShots)+"%",left,by-bh-6*dp,stencil,12,GameInk.TEXT,Paint.Align.LEFT);
        text(c,"BODY "+FightRecord.percent(bodyShots,head+bodyShots)+"%",right,by-bh-6*dp,stencil,12,GameInk.TEXT,Paint.Align.RIGHT);
        ink.setColor(0xffaf3927);c.drawRoundRect(left,by-bh,right,by,bh/2,bh/2,ink);
        ink.setColor(GameInk.GOLD);c.drawRoundRect(left,by-bh,Math.max(left+bh,split),by,bh/2,bh/2,ink);
    }
    private void mix(Canvas c,float w,float h,float top,float pad){
        long total=0;long[] counts=new long[MIX.length];
        for(int i=0;i<MIX.length;i++){counts[i]=v(MIX[i]);total+=counts[i];}
        float r=Math.min((h-top-pad)*.44f,w*.22f),cx=pad+r+8*dp,cy=top+(h-top-pad)/2f;
        box.set(cx-r,cy-r,cx+r,cy+r);
        ink.setStyle(Paint.Style.STROKE);ink.setStrokeWidth(r*.42f);
        // Segments draw in turn around the ring, like a hand sweeping the dial.
        float start=-90,reveal=360f*f;
        ink.setColor(0xff2a140d);c.drawCircle(cx,cy,r,ink);
        for(int i=0;i<MIX.length && total>0 && reveal>0;i++){
            float full=360f*counts[i]/total;
            if(full<=0)continue;
            float sweep=Math.min(full,reveal);reveal-=full;
            ink.setColor(MIX_COLORS[i]);c.drawArc(box,start,Math.max(.5f,sweep-1.2f),false,ink);start+=full;
        }
        ink.setStyle(Paint.Style.FILL);
        text(c,n(total*f),cx,cy+4*dp,stencil,fit(n(total),stencil,24,r*1.1f),GameInk.TEXT,Paint.Align.CENTER);
        text(c,"LANDED",cx,cy+20*dp,stencil,10,GameInk.MUTED,Paint.Align.CENTER);
        float lx=cx+r+26*dp,row=Math.min(22*dp,(h-top-pad)/MIX.length),ly=cy-row*MIX.length/2f+row*.7f;
        for(int i=0;i<MIX.length;i++){
            // Legend rows cascade in from the right.
            float in=stagger(f,i*.06f,.4f);if(in<=0f)continue;
            float y=ly+i*row,dx=(1f-in)*(1f-in)*40*dp;
            int alpha=Math.round(255*in);
            ink.setColor(MIX_COLORS[i]);ink.setAlpha(alpha);c.drawRoundRect(lx+dx,y-9*dp,lx+dx+12*dp,y+1*dp,2*dp,2*dp,ink);
            text(c,MIX_NAMES[i],lx+dx+20*dp,y,body,14,withAlpha(GameInk.TEXT,alpha),Paint.Align.LEFT);
            int p=FightRecord.percent(counts[i],total);
            text(c,p<0?"—":Math.round(p*in)+"%",w-pad+dx,y,stencil,14,withAlpha(GameInk.GOLD,alpha),Paint.Align.RIGHT);
        }
    }
    /** The opponent's head and torso facing the viewer: their left side is on the viewer's right. */
    static int withAlpha(int color,int alpha){return (Math.max(0,Math.min(255,alpha))<<24)|(color&0xffffff);}
    private long clock;private boolean breathing;private int hottest=-1;
    private void target(Canvas c,float w,float h,float top,float pad,long t,boolean live){
        clock=t;breathing=live;
        long[] hits=new long[FightRecord.AREA_STATS.length];long most=1,all=0;
        for(int i=0;i<hits.length;i++){hits[i]=v(FightRecord.AREA_STATS[i]);most=Math.max(most,hits[i]);all+=hits[i];}
        hottest=-1;for(int i=0;i<hits.length;i++)if(hits[i]>0 && hits[i]==most){hottest=i;break;}
        Integer[] order={0,1,2,3,4,5,6,7};
        java.util.Arrays.sort(order,(a,b)->Long.compare(hits[b],hits[a]));
        for(int k=0;k<8;k++)rankOf[order[k]]=7-k;
        float u=Math.min((h-top-pad-14*dp)/90f,(w*.5f)/66f),cx=w*.33f,oy=top+2*dp;
        int skin=0xff3a241b,edge=0xff5a3a2c;
        // Shoulders and torso: a V from the shoulders to the waist.
        path.reset();
        path.moveTo(cx-30*u,oy+44*u);path.cubicTo(cx-30*u,oy+38*u,cx-16*u,oy+35*u,cx-8*u,oy+35*u);
        path.lineTo(cx+8*u,oy+35*u);path.cubicTo(cx+16*u,oy+35*u,cx+30*u,oy+38*u,cx+30*u,oy+44*u);
        path.lineTo(cx+20*u,oy+88*u);path.lineTo(cx-20*u,oy+88*u);path.close();
        ink.setColor(skin);c.drawPath(path,ink);
        ink.setStyle(Paint.Style.STROKE);ink.setStrokeWidth(1.5f*dp);ink.setColor(edge);c.drawPath(path,ink);ink.setStyle(Paint.Style.FILL);
        // Ribs: their left body is on the viewer's right.
        ellipse(c,cx+11*u,oy+64*u,8.5f*u,15*u,hits[6],most,6);
        ellipse(c,cx-11*u,oy+64*u,8.5f*u,15*u,hits[7],most,7);
        // Neck, ears and head with a jawline.
        ink.setColor(skin);c.drawRect(cx-6.5f*u,oy+28*u,cx+6.5f*u,oy+38*u,ink);
        c.drawOval(cx-17*u,oy+12*u,cx-11*u,oy+22*u,ink);c.drawOval(cx+11*u,oy+12*u,cx+17*u,oy+22*u,ink);
        path.reset();
        path.moveTo(cx,oy);path.cubicTo(cx+10*u,oy,cx+14*u,oy+6*u,cx+14*u,oy+16*u);
        path.cubicTo(cx+14*u,oy+24*u,cx+9*u,oy+31*u,cx,oy+32*u);
        path.cubicTo(cx-9*u,oy+31*u,cx-14*u,oy+24*u,cx-14*u,oy+16*u);
        path.cubicTo(cx-14*u,oy+6*u,cx-10*u,oy,cx,oy);path.close();
        c.drawPath(path,ink);
        ink.setStyle(Paint.Style.STROKE);ink.setColor(edge);c.drawPath(path,ink);ink.setStyle(Paint.Style.FILL);
        ellipse(c,cx+6*u,oy+13*u,3.6f*u,2.4f*u,hits[0],most,0);   // left eye (viewer's right)
        ellipse(c,cx-6*u,oy+13*u,3.6f*u,2.4f*u,hits[1],most,1);   // right eye
        ellipse(c,cx,oy+18.5f*u,2.4f*u,3.6f*u,hits[2],most,2);    // nose
        ellipse(c,cx,oy+25*u,5f*u,1.8f*u,hits[3],most,3);         // mouth
        ellipse(c,cx+9.5f*u,oy+24*u,3.2f*u,4.6f*u,hits[4],most,4);// left jaw
        ellipse(c,cx-9.5f*u,oy+24*u,3.2f*u,4.6f*u,hits[5],most,5);// right jaw
        // Ranking of areas on the right.
        String[] names={"Left eye","Right eye","Nose","Mouth","Left jaw","Right jaw","Left body","Right body"};
        float lx=w*.6f,row=Math.min(24*dp,(h-top-pad-20*dp)/8f),ly=top+row*.8f;
        text(c,"MOST HIT",lx,ly-row*.15f,stencil,11,GameInk.GOLD,Paint.Align.LEFT);
        for(int k=0;k<8;k++){
            int i=order[k];float y=ly+(k+1)*row;
            float in=stagger(f,.1f+k*.05f,.4f);if(in<=0f)continue;
            float dx=(1f-in)*(1f-in)*36*dp;int alpha=Math.round(255*in);
            ink.setColor(DamageMap.heat(hits[i]/(float)most));ink.setAlpha(alpha);
            c.drawCircle(lx+dx+5*dp,y-5*dp,5*dp*(k==0?Math.min(1.15f,overshoot(in)):1f),ink);ink.setAlpha(255);
            text(c,names[i],lx+dx+16*dp,y,body,14,withAlpha(GameInk.TEXT,alpha),Paint.Align.LEFT);
            int p=FightRecord.percent(hits[i],all);
            text(c,p<0?"—":Math.round(p*in)+"%",w-pad+dx,y,stencil,14,withAlpha(GameInk.GOLD,alpha),Paint.Align.RIGHT);
        }
        text(c,"Facing you: their left is on your right",cx,h-pad,body,11,GameInk.MUTED,Paint.Align.CENTER);
    }
    /** One target area: an oval in its heat colour with a soft glow, dim when never hit. */
    private void ellipse(Canvas c,float x,float y,float rx,float ry,long hits,long most,int zone){
        ink.setColor(0xff4a3226);c.drawOval(x-rx,y-ry,x+rx,y+ry,ink);
        if(hits==0)return;
        // Zones light coolest-first so the hottest lands last, each with a flash as it ignites.
        float in=stagger(f,.08f+rankOf[zone]*.07f,.35f);if(in<=0f)return;
        int heat=DamageMap.heat(hits/(float)most*in);
        float glow=1.45f,flash=in<1f?(float)Math.sin(Math.PI*in)*.5f:0f;
        if(zone==hottest && breathing && in>=1f)glow+=.22f*(.5f+.5f*(float)Math.sin(clock/260.0));
        glow+=flash;
        ink.setShader(new RadialGradient(x,y,Math.max(rx,ry)*glow,(0x48<<24)|(heat&0xffffff),heat&0xffffff,Shader.TileMode.CLAMP));
        c.drawOval(x-rx*glow,y-ry*glow,x+rx*glow,y+ry*glow,ink);ink.setShader(null);
        ink.setColor(heat);ink.setAlpha(Math.round(255*Math.min(1f,in*1.5f)));c.drawOval(x-rx,y-ry,x+rx,y+ry,ink);ink.setAlpha(255);
        if(zone==hottest && in>=1f){
            // A thin ring pulses out from the most-hit area.
            float ring=breathing?(clock%1400)/1400f:0f;
            if(ring>0f){
                ink.setStyle(Paint.Style.STROKE);ink.setStrokeWidth(1.5f*dp);ink.setColor(withAlpha(0xfffff3d6,Math.round(200*(1f-ring))));
                // A fixed few dp outward whatever the zone's size, so a rib zone's ring stays on the body.
                float grow=ring*Math.min(10*dp,Math.min(rx,ry)*.9f);
                c.drawOval(x-rx-grow,y-ry-grow,x+rx+grow,y+ry+grow,ink);ink.setStyle(Paint.Style.FILL);
            }
        }
    }
    private final int[] rankOf=new int[8];
}