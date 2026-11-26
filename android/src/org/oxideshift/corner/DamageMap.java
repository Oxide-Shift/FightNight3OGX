package org.oxideshift.corner;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Where each boxer has been hurt this bout, from the retail damage routine's own per-punch log: eyes, nose,
 *  mouth, jaws and both sides of the body. Two boxer silhouettes (blue corner left, red corner right) are
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
                // The log names the boxer who landed the punch (owner on Thor: his own hits showed on his boxer),
                // so the damage belongs to the other one.
                total[1-player][at]+=amount;revision++;return true;
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
    /** A boxer in a guard, Tarkov-style: every part is a filled segment with a dark seam. Tracked parts (face zones,
     *  both sides of the torso) heat up with damage; arms, legs and neck stay dark; gloves and trunks wear the corner
     *  colour. Drawn in a 60 x 100 unit frame (origin top centre). The boxer's left side is on the viewer's right. */
    private void figure(Canvas c,float x0,float y0,float w,float h,int p,float d){
        float u=Math.min(w/64f,h/100f),cx=x0+w/2f,oy=y0+(h-100*u)/2f;
        int accent=p==0?GameInk.BLUE_CORNER:GameInk.RED_CORNER;
        int idle=0xff3a2219,seam=0xff0d0705;
        float[] s=shown[p];
        // Legs.
        for(int side=-1;side<=1;side+=2){
            seg(c,poly(cx,oy,u,side*2.5f,66,side*11.5f,66,side*9.5f,99,side*3.5f,99),idle,seam,d);
        }
        // Trunks in the corner colour, with a waistband.
        int trunks=MenuArt.Switch.blend(accent,0xff120806,.45f);
        seg(c,poly(cx,oy,u,-12.5f,55,12.5f,55,13.5f,68,1.5f,68,0,63,-1.5f,68,-13.5f,68),trunks,seam,d);
        ink.setColor(accent);c.drawRect(cx-12.5f*u,oy+55*u,cx+12.5f*u,oy+57.5f*u,ink);
        // Torso halves: viewer's left is the boxer's right side (RIGHT_BODY), viewer's right is LEFT_BODY.
        seg(c,poly(cx,oy,u,0,25,-17,26,-15,40,-11,55,0,55),heat(s[7]),seam,d);
        seg(c,poly(cx,oy,u,0,25,17,26,15,40,11,55,0,55),heat(s[6]),seam,d);
        glow(c,poly(cx,oy,u,0,25,-17,26,-15,40,-11,55,0,55),s[7],d);
        glow(c,poly(cx,oy,u,0,25,17,26,15,40,11,55,0,55),s[6],d);
        // Neck.
        seg(c,poly(cx,oy,u,-3.5f,20,3.5f,20,4.5f,26,-4.5f,26),idle,seam,d);
        // Head: dark base, then the five face zones.
        ink.setStyle(Paint.Style.FILL);ink.setColor(idle);c.drawOval(cx-8*u,oy+2*u,cx+8*u,oy+22*u,ink);
        zone(c,cx+3.2f*u,oy+9.5f*u,2.4f*u,1.6f*u,s[0]);   // LEFT_EYE (viewer's right)
        zone(c,cx-3.2f*u,oy+9.5f*u,2.4f*u,1.6f*u,s[1]);   // RIGHT_EYE
        zone(c,cx,oy+13*u,1.4f*u,2.2f*u,s[2]);            // NOSE
        zone(c,cx,oy+17.3f*u,3.2f*u,1.3f*u,s[3]);         // MOUTH
        zone(c,cx+5.6f*u,oy+16*u,2.3f*u,3f*u,s[4]);       // LEFT_JAW
        zone(c,cx-5.6f*u,oy+16*u,2.3f*u,3f*u,s[5]);       // RIGHT_JAW
        ink.setStyle(Paint.Style.STROKE);ink.setStrokeWidth(Math.max(1f,1.2f*d));ink.setColor(seam);
        c.drawOval(cx-8*u,oy+2*u,cx+8*u,oy+22*u,ink);
        // Arms up in a guard: upper arm to the elbow, forearm up to the glove beside the chin.
        ink.setStrokeCap(Paint.Cap.ROUND);
        for(int side=-1;side<=1;side+=2){
            float sx=cx+side*17*u,sy=oy+27.5f*u,ex=cx+side*20.5f*u,ey=oy+44*u,gx=cx+side*12.5f*u,gy=oy+21*u;
            ink.setColor(seam);ink.setStrokeWidth(7.4f*u);c.drawLine(sx,sy,ex,ey,ink);c.drawLine(ex,ey,gx,gy,ink);
            ink.setColor(idle);ink.setStrokeWidth(5.6f*u);c.drawLine(sx,sy,ex,ey,ink);c.drawLine(ex,ey,gx,gy,ink);
            ink.setStyle(Paint.Style.FILL);ink.setColor(seam);c.drawCircle(gx,gy,5.6f*u,ink);
            ink.setColor(accent);c.drawCircle(gx,gy,4.8f*u,ink);
            ink.setColor(0x33ffffff);c.drawCircle(gx-1.4f*u,gy-1.4f*u,1.8f*u,ink);
            ink.setStyle(Paint.Style.STROKE);
        }
        ink.setStrokeCap(Paint.Cap.BUTT);ink.setStyle(Paint.Style.FILL);
        float sp=getResources().getDisplayMetrics().scaledDensity;
        ink.setTextAlign(Paint.Align.CENTER);ink.setTextSize(13*sp);ink.setColor(GameInk.TEXT);
        String name=names[p];
        float room=w-12*d;if(ink.measureText(name)>room)ink.setTextSize(ink.getTextSize()*room/ink.measureText(name));
        float base=y0+h+16*d;
        c.drawText(name,cx,base,ink);
        ink.setTextSize(11*sp);ink.setColor(GameInk.MUTED);
        c.drawText("HEAD "+Math.round(headTotals[p])+"   BODY "+Math.round(bodyTotals[p]),cx,base+15*d,ink);
    }
    /** A closed polygon from unit coordinates (x,y pairs) in the figure frame. */
    private Path poly(float cx,float oy,float u,float... xy){
        Path path=new Path();
        for(int i=0;i<xy.length;i+=2){float x=cx+xy[i]*u,y=oy+xy[i+1]*u;if(i==0)path.moveTo(x,y);else path.lineTo(x,y);}
        path.close();return path;
    }
    private void seg(Canvas c,Path part,int fill,int seam,float d){
        ink.setStyle(Paint.Style.FILL);ink.setColor(fill);c.drawPath(part,ink);
        ink.setStyle(Paint.Style.STROKE);ink.setStrokeWidth(Math.max(1f,1.2f*d));ink.setColor(seam);c.drawPath(part,ink);
        ink.setStyle(Paint.Style.FILL);
    }
    /** Badly hurt parts get a hot rim. */
    private void glow(Canvas c,Path part,float v,float d){
        if(v<.6f)return;
        ink.setStyle(Paint.Style.STROKE);ink.setStrokeWidth(2*d);ink.setColor((Math.round(200*(v-.4f))<<24)|0xff3a1a);
        c.drawPath(part,ink);ink.setStyle(Paint.Style.FILL);
    }
    private void zone(Canvas c,float x,float y,float rx,float ry,float v){
        ink.setStyle(Paint.Style.FILL);ink.setColor(v<=0f?0xff4a2c20:heat(v));c.drawOval(x-rx,y-ry,x+rx,y+ry,ink);
        if(v>=.6f){ink.setStyle(Paint.Style.STROKE);ink.setStrokeWidth(1.2f*getResources().getDisplayMetrics().density);
            ink.setColor(0xccff5a2a);c.drawOval(x-rx,y-ry,x+rx,y+ry,ink);ink.setStyle(Paint.Style.FILL);}
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
