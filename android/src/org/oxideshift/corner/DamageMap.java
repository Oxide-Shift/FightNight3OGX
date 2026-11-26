package org.oxideshift.corner;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Where each boxer has been hurt this bout, from the retail damage routine's own per-punch log: eyes, nose,
 *  mouth, jaws and both sides of the body. Two head-and-torso figures (blue corner left, red corner right) are
 *  shaded by the damage each region has taken, relative to the most-hurt region in the bout, with head and body
 *  totals underneath. The game's amounts have no published unit, so the figures compare, they do not measure. */
final class DamageMap extends View {
    /** Running totals for one bout. Pure data, so it can be tested off device. */
    static final class Tally {
        static final String[] AREAS={"LEFT_EYE","RIGHT_EYE","NOSE","MOUTH","LEFT_JAW","RIGHT_JAW","LEFT_BODY","RIGHT_BODY"};
        final float[][] total=new float[2][AREAS.length];
        long revision;
        static int area(String name){
            if(name==null)return -1;
            for(int i=0;i<AREAS.length;i++)if(name.endsWith(AREAS[i]))return i;
            return -1;
        }
        /** Parse "FNR3/1 DAMAGE <player> <area> <HEAD|BODY> <amount> [attack]". Returns false for anything else. */
        synchronized boolean line(String line){
            if(!line.startsWith("FNR3/1 DAMAGE "))return false;
            String[] p=line.substring(14).split(" ");
            if(p.length!=4 && p.length!=5)return false;
            try{
                int player=Integer.parseInt(p[0]),at=area(p[1]);
                float amount=Float.parseFloat(p[3]);
                if(player<0 || player>1 || at<0 || !(amount>=0 && amount<1000))return false;
                total[player][at]+=amount;revision++;return true;
            }catch(NumberFormatException e){return false;}
        }
        synchronized void clear(){for(float[] t:total)java.util.Arrays.fill(t,0f);revision++;}
        synchronized float head(int p){float s=0;for(int i=0;i<6;i++)s+=total[p][i];return s;}
        synchronized float body(int p){return total[p][6]+total[p][7];}
        synchronized float peak(){float m=0;for(float[] t:total)for(float v:t)m=Math.max(m,v);return m;}
    }

    private final Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path shape=new Path();
    private final Typeface face;
    private final float[][] shown=new float[2][Tally.AREAS.length];
    private float[][] target=new float[2][Tally.AREAS.length];
    private long revision=-1;
    private String[] names={"BLUE","RED"};

    DamageMap(Context c,Typeface face){super(c);this.face=face;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);}
    void show(Tally tally,FightBroadcast.Fighters fighters){synchronized(tally){showLocked(tally,fighters);}}
    private void showLocked(Tally tally,FightBroadcast.Fighters fighters){
        if(fighters!=null)names=new String[]{fighters.blue.toUpperCase(java.util.Locale.ROOT),fighters.red.toUpperCase(java.util.Locale.ROOT)};
        if(tally.revision==revision)return;
        // A freshly built map shows the bout so far at once; only new hits glow in.
        boolean first=revision<0;
        revision=tally.revision;
        float peak=Math.max(1f,tally.peak());
        for(int p=0;p<2;p++)for(int i=0;i<Tally.AREAS.length;i++){
            target[p][i]=tally.total[p][i]/peak;
            if(first)shown[p][i]=target[p][i];
        }
        setContentDescription("Damage this bout. "+names[0]+": head "+Math.round(tally.head(0))+", body "+Math.round(tally.body(0))+
                ". "+names[1]+": head "+Math.round(tally.head(1))+", body "+Math.round(tally.body(1))+".");
        headTotals[0]=tally.head(0);headTotals[1]=tally.head(1);bodyTotals[0]=tally.body(0);bodyTotals[1]=tally.body(1);
        invalidate();
    }
    private final float[] headTotals=new float[2],bodyTotals=new float[2];

    @Override protected void onDraw(Canvas c){
        float d=getResources().getDisplayMetrics().density,w=getWidth(),h=getHeight();
        // New damage glows in: shaded values ease toward their targets.
        boolean moving=false;
        for(int p=0;p<2;p++)for(int i=0;i<shown[p].length;i++){
            float delta=target[p][i]-shown[p][i];
            if(Math.abs(delta)>.004f && SheetMotion.on()){shown[p][i]+=delta*.18f;moving=true;}else shown[p][i]=target[p][i];
        }
        ink.setStyle(Paint.Style.FILL);ink.setShader(null);
        ink.setColor(0xff1d0d09);c.drawRoundRect(0,0,w,h,8*d,8*d,ink);
        ink.setColor(0xff75391e);c.drawRect(8*d,0,w-8*d,1.5f*d,ink);
        ink.setTypeface(face);ink.setTextAlign(Paint.Align.LEFT);ink.setTextSize(12*getResources().getDisplayMetrics().scaledDensity);
        ink.setColor(GameInk.GOLD);c.drawText("DAMAGE THIS BOUT",12*d,20*d,ink);
        float half=w/2f,top=30*d,figH=h-top-34*d;
        for(int p=0;p<2;p++)figure(c,p*half,top,half,figH,p,d);
        if(moving)postInvalidateOnAnimation();
    }
    private void figure(Canvas c,float x0,float y0,float w,float h,int p,float d){
        float cx=x0+w/2f,headR=Math.min(w*.2f,h*.24f),hy=y0+headR+4*d;
        int accent=p==0?GameInk.BLUE_CORNER:GameInk.RED_CORNER;
        // Torso: two halves (the boxer's left side is on the viewer's right).
        float tw=headR*2.6f,ty=hy+headR*1.25f,th=Math.min(h-(ty-y0)-4*d,headR*2.2f);
        for(int side=0;side<2;side++){
            float l=side==0?cx:cx-tw/2,r=side==0?cx+tw/2:cx;
            shape.reset();
            shape.moveTo(l,ty);shape.lineTo(r,ty);
            shape.lineTo(side==0?r-tw*.12f:r,ty+th);shape.lineTo(side==0?l:l+tw*.12f,ty+th);shape.close();
            ink.setColor(heat(shown[p][side==0?6:7]));c.drawPath(shape,ink);
        }
        ink.setStyle(Paint.Style.STROKE);ink.setStrokeWidth(1.5f*d);ink.setColor(0x66a58a6c);
        c.drawLine(cx,ty,cx,ty+th,ink);ink.setStyle(Paint.Style.FILL);
        // Head: jaws either side, eyes, nose, mouth.
        ink.setColor(0xff2a1712);c.drawCircle(cx,hy,headR,ink);
        float jawY=hy+headR*.45f;
        blob(c,cx+headR*.55f,jawY,headR*.38f,headR*.32f,shown[p][4]);
        blob(c,cx-headR*.55f,jawY,headR*.38f,headR*.32f,shown[p][5]);
        blob(c,cx+headR*.38f,hy-headR*.25f,headR*.26f,headR*.18f,shown[p][0]);
        blob(c,cx-headR*.38f,hy-headR*.25f,headR*.26f,headR*.18f,shown[p][1]);
        blob(c,cx,hy+headR*.05f,headR*.14f,headR*.22f,shown[p][2]);
        blob(c,cx,hy+headR*.52f,headR*.3f,headR*.12f,shown[p][3]);
        ink.setStyle(Paint.Style.STROKE);ink.setStrokeWidth(2*d);ink.setColor(accent);c.drawCircle(cx,hy,headR,ink);
        ink.setStyle(Paint.Style.FILL);
        float sp=getResources().getDisplayMetrics().scaledDensity;
        ink.setTextAlign(Paint.Align.CENTER);ink.setTextSize(13*sp);ink.setColor(GameInk.TEXT);
        String name=names[p];
        float room=w-12*d;if(ink.measureText(name)>room)ink.setTextSize(ink.getTextSize()*room/ink.measureText(name));
        float base=y0+h+16*d;
        c.drawText(name,cx,base,ink);
        ink.setTextSize(11*sp);ink.setColor(GameInk.MUTED);
        c.drawText("HEAD "+Math.round(headTotals[p])+"   BODY "+Math.round(bodyTotals[p]),cx,base+15*d,ink);
    }
    private void blob(Canvas c,float x,float y,float rx,float ry,float v){
        ink.setColor(heat(v));c.drawOval(x-rx,y-ry,x+rx,y+ry,ink);
    }
    /** Untouched regions sit in dark leather; damage warms through orange to a hot red. */
    static int heat(float v){
        v=Math.max(0f,Math.min(1f,v));
        if(v<=0f)return 0xff3a2219;
        // Any hit at all is clearly marked; the rest of the range runs orange to hot red.
        v=.2f+.8f*v;
        if(v<.6f)return MenuArt.Switch.blend(0xff7a3414,0xffe8701a,(v-.2f)/.4f);
        return MenuArt.Switch.blend(0xffe8701a,0xffff2a14,(v-.6f)/.4f);
    }
}
