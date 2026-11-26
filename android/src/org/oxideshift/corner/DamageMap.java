package org.oxideshift.corner;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Where each boxer has been hurt this bout, from the retail damage routine's own per-punch log: eyes, nose,
 *  mouth, jaws and both sides of the body. Two boxer silhouettes (blue corner left, red corner right) are
 *  shaded green, yellow, orange, then red as each region takes damage (see Tally.level), with head and body
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
                // The routine runs twice per landed punch (overall damage, then body damage, which is 0 for head
                // punches; Thor log 2026-11-26). Keep the first line of each pair.
                String key=p[0]+" "+p[1]+" "+(p.length==5?p[4]:"");
                if(key.equals(lastKey) && !skippedLast){skippedLast=true;return false;}
                skippedLast=false;lastKey=key;
                // The log names the boxer who landed the punch (owner on Thor: his own hits showed on his boxer),
                // so the damage belongs to the other one.
                total[1-player][at]+=amount;revision++;return true;
            }catch(NumberFormatException e){return false;}
        }
        private String lastKey="";private boolean skippedLast;
        /** 0..1 for region i of boxer p whose health is healthPercent. The boxer's health loss sets how hot the map
         *  can get (full health is all green, so it recovers between rounds as health does); the most-hit region
         *  takes that full value and the others scale by their share of the damage. */
        synchronized float level(int p,int i,int healthPercent){
            float most=0f;for(float v:total[p])most=Math.max(most,v);
            if(most<=0f || total[p][i]<=0f || healthPercent<0)return 0f;
            float loss=Math.max(0f,Math.min(1f,1f-healthPercent/100f));
            return loss*(float)Math.sqrt(total[p][i]/most);
        }
        /** The most-hit region of boxer p, or -1. */
        synchronized int worst(int p){
            int at=-1;float most=0f;
            for(int i=0;i<AREAS.length;i++)if(total[p][i]>most){most=total[p][i];at=i;}
            return at;
        }
        synchronized void clear(){for(float[] t:total)java.util.Arrays.fill(t,0f);lastKey="";skippedLast=false;revision++;}
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
    /** health: {blue, red} health percent from the bout (or -1 when unknown). */
    void show(Tally tally,FightBroadcast.Fighters fighters,int[] health){synchronized(tally){showLocked(tally,fighters,health);}}
    private void showLocked(Tally tally,FightBroadcast.Fighters fighters,int[] health){
        if(fighters!=null)names=new String[]{fighters.blue.toUpperCase(java.util.Locale.ROOT),fighters.red.toUpperCase(java.util.Locale.ROOT)};
        if(tally.revision==revision && health[0]==this.health[0] && health[1]==this.health[1])return;
        // A freshly built map shows the bout so far at once; only changes glow in.
        boolean first=revision<0;
        revision=tally.revision;this.health[0]=health[0];this.health[1]=health[1];
        for(int p=0;p<2;p++){
            for(int i=0;i<Tally.AREAS.length;i++){
                target[p][i]=tally.level(p,i,health[p]);
                if(first)shown[p][i]=target[p][i];
            }
            worst[p]=tally.worst(p);
        }
        setContentDescription("Damage this bout. "+summary(0)+". "+summary(1)+".");
        invalidate();
    }
    private final int[] health={-1,-1},worst={-1,-1};
    private static final String[] AREA_NAMES={"LEFT EYE","RIGHT EYE","NOSE","MOUTH","LEFT JAW","RIGHT JAW","LEFT BODY","RIGHT BODY"};
    private String summary(int p){
        return names[p]+(health[p]>=0?": health "+health[p]+" percent":"")+(worst[p]>=0?", most hit "+AREA_NAMES[worst[p]].toLowerCase(java.util.Locale.ROOT):", untouched");
    }

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
        // Faint scan lines across the panel, like a medical readout.
        ink.setColor(0x0affffff);
        for(float y=28*d;y<h-4*d;y+=4*d)c.drawRect(6*d,y,w-6*d,y+Math.max(1f,.6f*d),ink);
        float half=w/2f,top=30*d,figH=h-top-34*d;
        for(int p=0;p<2;p++)figure(c,p*half,top,half,figH,p,d);
        if(moving)postInvalidateOnAnimation();
    }
    /** A boxer in a high guard, Tarkov-style: every part is a shaded segment with a dark seam. Tracked parts (face
     *  zones, both sides of the torso) start green and heat up with damage; neck, arms and legs are dark; gloves and
     *  trunks wear the corner colour. Drawn in a 64 x 100 unit frame (origin top centre). The boxer's left side is on
     *  the viewer's right. */
    private void figure(Canvas c,float x0,float y0,float w,float h,int p,float d){
        float u=Math.min(w/64f,h/100f),cx=x0+w/2f,oy=y0+(h-100*u)/2f;
        int accent=p==0?GameInk.BLUE_CORNER:GameInk.RED_CORNER;
        int idle=0xff33201a,seam=0xff0b0604;
        float[] s=shown[p];
        // Corner-coloured glow behind the boxer and a shadow on the canvas under his feet.
        ink.setStyle(Paint.Style.FILL);
        ink.setShader(new RadialGradient(cx,oy+46*u,52*u,(0x30<<24)|(accent&0xffffff),accent&0xffffff,Shader.TileMode.CLAMP));
        c.drawCircle(cx,oy+46*u,52*u,ink);ink.setShader(null);
        ink.setColor(0x66000000);c.drawOval(cx-17*u,oy+97*u,cx+17*u,oy+101.5f*u,ink);
        // Legs in a boxer's stance, with knees and boots.
        for(int side=-1;side<=1;side+=2){
            Path leg=curve(cx,oy,u,side,new float[]{2.2f,66, 12.5f,66, 12f,80, 10.8f,96, 3.6f,96, 3.4f,80});
            seg(c,leg,idle,seam,d);shade(c,leg,oy+66*u,oy+96*u);
            float a=cx+side*3.0f*u,b=cx+side*11.8f*u,l=Math.min(a,b),r=Math.max(a,b);
            ink.setColor(seam);c.drawRoundRect(l,oy+93.8f*u,r,oy+99.2f*u,1.6f*u,1.6f*u,ink);
            ink.setColor(0xff1c1411);c.drawRoundRect(l+.5f*u,oy+94.3f*u,r-.5f*u,oy+98.7f*u,1.3f*u,1.3f*u,ink);
            ink.setColor(0x33ffffff);c.drawRect(l+1*u,oy+94.6f*u,r-1*u,oy+95.2f*u,ink);
        }
        // Trunks in the corner colour: waistband, side stripe, split hem.
        int trunks=MenuArt.Switch.blend(accent,0xff120806,.5f);
        Path shorts=poly(cx,oy,u,-12.2f,55,12.2f,55,13.6f,69,1.4f,69,0,64.5f,-1.4f,69,-13.6f,69);
        seg(c,shorts,trunks,seam,d);shade(c,shorts,oy+55*u,oy+69*u);
        ink.setColor(accent);c.drawRect(cx-12.2f*u,oy+55*u,cx+12.2f*u,oy+58*u,ink);
        ink.setColor(0x66ffffff);c.drawRect(cx-12.2f*u,oy+55*u,cx+12.2f*u,oy+55.6f*u,ink);
        ink.setColor(MenuArt.Switch.blend(accent,0xffffffff,.35f));
        c.drawRect(cx-13.1f*u,oy+59*u,cx-11.9f*u,oy+68.5f*u,ink);c.drawRect(cx+11.9f*u,oy+59*u,cx+13.1f*u,oy+68.5f*u,ink);
        // Torso halves with shoulders and a tapered waist. Viewer's left is the boxer's RIGHT_BODY.
        for(int side=-1;side<=1;side+=2){
            Path half=curve(cx,oy,u,side,new float[]{0,24, 9,23.4f, 16.8f,26.6f, 18.6f,31.5f, 16.2f,41, 12f,48, 11.4f,55.2f, 0,55.2f});
            float v=s[side<0?7:6];
            seg(c,half,heat(v),seam,d);shade(c,half,oy+24*u,oy+55*u);glow(c,half,v,d);
        }
        // Muscle lines: pecs and abs, drawn as darker seams over the heat colour.
        ink.setStyle(Paint.Style.STROKE);ink.setStrokeWidth(Math.max(1f,.9f*d));ink.setColor(0x55000000);
        for(int side=-1;side<=1;side+=2){
            Path pec=new Path();pec.moveTo(cx+side*1*u,oy+36*u);pec.quadTo(cx+side*8*u,oy+38.5f*u,cx+side*14*u,oy+33*u);c.drawPath(pec,ink);
        }
        for(float y=41;y<=51;y+=5){c.drawLine(cx-5*u,oy+y*u,cx+5*u,oy+y*u,ink);}
        c.drawLine(cx,oy+37*u,cx,oy+54*u,ink);
        ink.setStyle(Paint.Style.FILL);
        // Neck and traps.
        Path neck=poly(cx,oy,u,-3.6f,19,3.6f,19,4.6f,24.4f,7.5f,25,0,26,-7.5f,25,-4.6f,24.4f);
        seg(c,neck,idle,seam,d);
        // Head: tinted by its most-hurt zone, ears, then the five face zones.
        float hurt=0f;for(int i=0;i<6;i++)hurt=Math.max(hurt,s[i]);
        int skull=MenuArt.Switch.blend(idle,heat(hurt),.38f);
        ink.setColor(seam);c.drawOval(cx-9.2f*u,oy+8.5f*u,cx-6.6f*u,oy+14*u,ink);c.drawOval(cx+6.6f*u,oy+8.5f*u,cx+9.2f*u,oy+14*u,ink);
        ink.setColor(skull);c.drawOval(cx-8.8f*u,oy+9*u,cx-7f*u,oy+13.5f*u,ink);c.drawOval(cx+7f*u,oy+9*u,cx+8.8f*u,oy+13.5f*u,ink);
        Path headShape=new Path();headShape.addOval(new RectF(cx-7.8f*u,oy+1.5f*u,cx+7.8f*u,oy+21.5f*u),Path.Direction.CW);
        seg(c,headShape,skull,seam,d);shade(c,headShape,oy+1.5f*u,oy+21.5f*u);
        zone(c,cx+3.2f*u,oy+9.5f*u,2.4f*u,1.6f*u,s[0]);   // LEFT_EYE (viewer's right)
        zone(c,cx-3.2f*u,oy+9.5f*u,2.4f*u,1.6f*u,s[1]);   // RIGHT_EYE
        zone(c,cx,oy+13*u,1.4f*u,2.2f*u,s[2]);            // NOSE
        zone(c,cx,oy+17.3f*u,3.2f*u,1.3f*u,s[3]);         // MOUTH
        zone(c,cx+5.5f*u,oy+16*u,2.2f*u,3f*u,s[4]);       // LEFT_JAW
        zone(c,cx-5.5f*u,oy+16*u,2.2f*u,3f*u,s[5]);       // RIGHT_JAW
        // Arms up in a high guard: upper arm to the elbow, forearm to a glossy glove with a white cuff.
        ink.setStrokeCap(Paint.Cap.ROUND);ink.setStyle(Paint.Style.STROKE);
        for(int side=-1;side<=1;side+=2){
            float sx=cx+side*17.2f*u,sy=oy+29*u,ex=cx+side*21*u,ey=oy+44.5f*u,gx=cx+side*12.8f*u,gy=oy+20.5f*u;
            ink.setColor(seam);ink.setStrokeWidth(8f*u);c.drawLine(sx,sy,ex,ey,ink);c.drawLine(ex,ey,gx,gy,ink);
            ink.setColor(idle);ink.setStrokeWidth(6.2f*u);c.drawLine(sx,sy,ex,ey,ink);c.drawLine(ex,ey,gx,gy,ink);
            ink.setColor(0x22ffffff);ink.setStrokeWidth(1.6f*u);
            c.drawLine(sx-side*1.4f*u,sy,ex-side*1.6f*u,ey,ink);
            // Cuff where the forearm meets the glove.
            float cuffX=gx+(ex-gx)*.28f,cuffY=gy+(ey-gy)*.28f;
            ink.setColor(seam);ink.setStrokeWidth(7.6f*u);c.drawPoint(cuffX,cuffY,ink);
            ink.setColor(0xffe8dccb);ink.setStrokeWidth(6.2f*u);c.drawPoint(cuffX,cuffY,ink);
            ink.setStyle(Paint.Style.FILL);
            ink.setColor(seam);c.drawCircle(gx,gy,6f*u,ink);
            ink.setShader(new RadialGradient(gx-1.8f*u,gy-1.8f*u,6.4f*u,MenuArt.Switch.blend(accent,0xffffffff,.45f),
                    MenuArt.Switch.blend(accent,0xff000000,.35f),Shader.TileMode.CLAMP));
            c.drawCircle(gx,gy,5.2f*u,ink);ink.setShader(null);
            ink.setColor(0x88ffffff);c.drawOval(gx-3.4f*u,gy-3.9f*u,gx-.6f*u,gy-2.2f*u,ink);
            ink.setStyle(Paint.Style.STROKE);
        }
        ink.setStrokeCap(Paint.Cap.BUTT);ink.setStyle(Paint.Style.FILL);
        float sp=getResources().getDisplayMetrics().scaledDensity;
        ink.setTextAlign(Paint.Align.CENTER);ink.setTextSize(13*sp);ink.setColor(GameInk.TEXT);
        String name=names[p];
        float room=w-12*d;if(ink.measureText(name)>room)ink.setTextSize(ink.getTextSize()*room/ink.measureText(name));
        float base=y0+h+16*d;
        c.drawText(name,cx,base,ink);
        // Health (the same number as the fighter card) in its heat colour, and the area taking the most punishment.
        ink.setTextSize(11*sp);
        String hp=health[p]>=0?"HEALTH "+health[p]+"%":"HEALTH —";
        String most=worst[p]>=0?"   ·   MOST HIT "+AREA_NAMES[worst[p]]:"   ·   UNTOUCHED";
        float hw=ink.measureText(hp),mw=ink.measureText(most),left=cx-(hw+mw)/2f;
        ink.setTextAlign(Paint.Align.LEFT);
        ink.setColor(health[p]>=0?heat(1f-health[p]/100f):GameInk.MUTED);c.drawText(hp,left,base+15*d,ink);
        ink.setColor(GameInk.MUTED);c.drawText(most,left+hw,base+15*d,ink);
        ink.setTextAlign(Paint.Align.CENTER);
    }
    /** A closed polygon from unit coordinates (x,y pairs) in the figure frame. */
    private Path poly(float cx,float oy,float u,float... xy){
        Path path=new Path();
        for(int i=0;i<xy.length;i+=2){float x=cx+xy[i]*u,y=oy+xy[i+1]*u;if(i==0)path.moveTo(x,y);else path.lineTo(x,y);}
        path.close();return path;
    }
    /** A smooth closed outline through unit points (x,y pairs, x measured outward), mirrored to one side. Corners are
     *  rounded by running quadratic curves through the midpoints. */
    private Path curve(float cx,float oy,float u,int side,float[] xy){
        int n=xy.length/2;
        float[] x=new float[n],y=new float[n];
        for(int i=0;i<n;i++){x[i]=cx+side*xy[2*i]*u;y[i]=oy+xy[2*i+1]*u;}
        Path path=new Path();
        path.moveTo((x[n-1]+x[0])/2f,(y[n-1]+y[0])/2f);
        for(int i=0;i<n;i++){int j=(i+1)%n;path.quadTo(x[i],y[i],(x[i]+x[j])/2f,(y[i]+y[j])/2f);}
        path.close();return path;
    }
    /** Light from above: a soft highlight at the top of a part fading to shadow at the bottom. */
    private void shade(Canvas c,Path part,float top,float bottom){
        ink.setStyle(Paint.Style.FILL);
        ink.setShader(new LinearGradient(0,top,0,bottom,new int[]{0x2effffff,0x00ffffff,0x40000000},new float[]{0f,.45f,1f},Shader.TileMode.CLAMP));
        c.drawPath(part,ink);ink.setShader(null);
    }
    private void seg(Canvas c,Path part,int fill,int seam,float d){
        ink.setStyle(Paint.Style.FILL);ink.setColor(fill);c.drawPath(part,ink);
        ink.setStyle(Paint.Style.STROKE);ink.setStrokeWidth(Math.max(1f,1.2f*d));ink.setColor(seam);c.drawPath(part,ink);
        ink.setStyle(Paint.Style.FILL);
    }
    /** Badly hurt parts get a hot rim. */
    private void glow(Canvas c,Path part,float v,float d){
        if(v<.85f)return;
        ink.setStyle(Paint.Style.STROKE);ink.setStrokeWidth(2*d);ink.setColor((Math.round(Math.min(255f,1400*(v-.8f)))<<24)|0xff3a1a);
        c.drawPath(part,ink);ink.setStyle(Paint.Style.FILL);
    }
    private void zone(Canvas c,float x,float y,float rx,float ry,float v){
        ink.setStyle(Paint.Style.FILL);ink.setColor(0xff0b0604);c.drawOval(x-rx-.6f*getResources().getDisplayMetrics().density,y-ry-.6f*getResources().getDisplayMetrics().density,
                x+rx+.6f*getResources().getDisplayMetrics().density,y+ry+.6f*getResources().getDisplayMetrics().density,ink);
        ink.setColor(heat(v));c.drawOval(x-rx,y-ry,x+rx,y+ry,ink);
        ink.setColor(0x40ffffff);c.drawOval(x-rx*.6f,y-ry*.85f,x+rx*.2f,y-ry*.2f,ink);
        if(v>=.85f){ink.setStyle(Paint.Style.STROKE);ink.setStrokeWidth(1.2f*getResources().getDisplayMetrics().density);
            ink.setColor(0xccff5a2a);c.drawOval(x-rx,y-ry,x+rx,y+ry,ink);ink.setStyle(Paint.Style.FILL);}
    }
    /** Healthy regions start green. Damage runs to yellow (about 12 average hits), orange (about 28) and only
     *  reaches red after a long beating (about 90). */
    static final float[] STOPS={0f,.45f,.7f,.88f};
    static final int[] COLORS={0xff3fae4a,0xffe8cc3a,0xfff08a1a,0xffff2a14};
    static int heat(float v){
        v=Math.max(0f,Math.min(1f,v));
        for(int k=1;k<STOPS.length;k++)
            if(v<=STOPS[k])return MenuArt.Switch.blend(COLORS[k-1],COLORS[k],(v-STOPS[k-1])/(STOPS[k]-STOPS[k-1]));
        return COLORS[COLORS.length-1];
    }
}
