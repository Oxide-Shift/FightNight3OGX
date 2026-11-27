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
                float next=tally.level(p,i,health[p]);
                // A fresh hit on this zone (not a recovery between rounds) flashes it and rocks the boxer.
                if(!first && next>target[p][i]+.005f)hitAt[p][i]=android.os.SystemClock.uptimeMillis();
                target[p][i]=next;
                if(first)shown[p][i]=target[p][i];
            }
            worst[p]=tally.worst(p);
        }
        setContentDescription("Damage this bout. "+summary(0)+". "+summary(1)+".");
        invalidate();
    }
    private final int[] health={-1,-1},worst={-1,-1};
    /** When each zone last took a fresh hit (uptime ms), for the flash and flinch. */
    private final long[][] hitAt=new long[2][Tally.AREAS.length];
    static final long FLASH_MS=480,FLINCH_MS=280;
    /** Zone centres and sizes in the figure's 64 x 100 unit frame (x from the centre line). */
    private static final float[][] ZONES={{3.3f,10.2f,3.3f},{-3.3f,10.2f,3.3f},{0,13.6f,2.6f},{0,17.6f,3f},
            {5.4f,16.2f,3.2f},{-5.4f,16.2f,3.2f},{8.5f,40f,9f},{-8.5f,40f,9f}};
    /** Sideways flinch for boxer p at time t, away from the side that was hit. Pure, for tests. */
    static float flinch(long[] hits,long t){
        long best=Long.MAX_VALUE;int zone=-1;
        for(int i=0;i<hits.length;i++){long age=t-hits[i];if(hits[i]>0 && age>=0 && age<best){best=age;zone=i;}}
        if(zone<0 || best>=FLINCH_MS)return 0f;
        float k=1f-best/(float)FLINCH_MS,away=ZONES[zone][0]>0?-1f:ZONES[zone][0]<0?1f:0f;
        if(away==0f)return 0f;
        return away*2.4f*k*k*(float)Math.cos(best*.045);
    }
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
        long now=android.os.SystemClock.uptimeMillis();boolean live=SheetMotion.on();
        for(int p=0;p<2;p++){
            float u=Math.min(half/64f,figH/100f);
            float rock=live?flinch(hitAt[p],now)*u:0f;
            c.save();c.translate(rock,0);
            figure(c,p*half,top,half,figH,p,d);
            if(live)moving|=flashes(c,p*half+half/2f,top+(figH-100*u)/2f,u,p,now);
            c.restore();
            if(rock!=0f)moving=true;
        }
        if(moving)postInvalidateOnAnimation();
    }
    /** A boxer in a high guard: one rim-lit silhouette built from smooth curves. Tracked parts (five face zones and
     *  both sides of the torso) glow in their heat colour inside the outline, starting green; arms and legs stay dark;
     *  gloves and trunks wear the corner colour. Drawn in a 64 x 100 unit frame (origin top centre). The boxer's left
     *  side is on the viewer's right. */
    private void figure(Canvas c,float x0,float y0,float w,float h,int p,float d){
        float u=Math.min(w/64f,h/100f),cx=x0+w/2f,oy=y0+(h-100*u)/2f;
        int accent=p==0?GameInk.BLUE_CORNER:GameInk.RED_CORNER,rgb=accent&0xffffff;
        float[] s=shown[p];
        // Corner light behind the boxer and a shadow on the canvas under his feet.
        ink.setStyle(Paint.Style.FILL);
        ink.setAlpha(255);ink.setShader(new RadialGradient(cx,oy+44*u,54*u,new int[]{(0x3c<<24)|rgb,(0x14<<24)|rgb,rgb},new float[]{0f,.55f,1f},Shader.TileMode.CLAMP));
        c.drawCircle(cx,oy+44*u,54*u,ink);ink.setShader(null);
        ink.setAlpha(255);ink.setShader(new RadialGradient(cx,oy+98.5f*u,19*u,0x88000000,0x00000000,Shader.TileMode.CLAMP));
        c.save();c.scale(1f,.2f,cx,oy+98.5f*u);c.drawCircle(cx,oy+98.5f*u,19*u,ink);c.restore();ink.setShader(null);
        Body body=body(p,cx,oy,u);
        // Back light: a soft corner-coloured halo hugging the outline, then the body itself.
        ink.setStyle(Paint.Style.STROKE);ink.setStrokeJoin(Paint.Join.ROUND);
        ink.setColor((0x16<<24)|rgb);ink.setStrokeWidth(3.2f*u);c.drawPath(body.silhouette,ink);
        ink.setColor((0x8c<<24)|rgb);ink.setStrokeWidth(1.1f*u);c.drawPath(body.silhouette,ink);
        ink.setStyle(Paint.Style.FILL);
        ink.setAlpha(255);ink.setShader(new LinearGradient(cx-20*u,oy,cx+22*u,oy+100*u,new int[]{0xff54392e,0xff2e1d17,0xff170e0b},new float[]{0f,.5f,1f},Shader.TileMode.CLAMP));
        c.drawPath(body.silhouette,ink);ink.setShader(null);
        // Torso: each half carries its own damage as a soft glow, strongest over the ribs.
        c.save();c.clipPath(body.torso);
        for(int side=-1;side<=1;side+=2){
            float v=s[side<0?7:6];int hot=heat(v);
            c.save();
            if(side<0)c.clipRect(cx-30*u,oy,cx,oy+70*u);else c.clipRect(cx,oy,cx+30*u,oy+70*u);
            ink.setAlpha(255);ink.setShader(new RadialGradient(cx+side*8.5f*u,oy+40*u,19*u,new int[]{(0xff<<24)|(hot&0xffffff),(0xe8<<24)|(hot&0xffffff),(0x90<<24)|(hot&0xffffff)},
                    new float[]{0f,.6f,1f},Shader.TileMode.CLAMP));
            c.drawRect(cx-30*u,oy,cx+30*u,oy+70*u,ink);ink.setShader(null);
            c.restore();
        }
        // Light from above, shadow under the pecs and toward the waist.
        ink.setAlpha(255);ink.setShader(new LinearGradient(0,oy+25*u,0,oy+57*u,new int[]{0x30ffffff,0x00ffffff,0x55000000},new float[]{0f,.4f,1f},Shader.TileMode.CLAMP));
        c.drawRect(cx-30*u,oy+20*u,cx+30*u,oy+60*u,ink);ink.setShader(null);
        // Muscle definition: pecs, sternum, abs and the obliques' line, as soft shadow strokes.
        ink.setStyle(Paint.Style.STROKE);ink.setStrokeCap(Paint.Cap.ROUND);ink.setColor(0x50000000);ink.setStrokeWidth(Math.max(1f,.75f*u));
        Path lines=new Path();
        for(int side=-1;side<=1;side+=2){
            lines.moveTo(cx+side*.6f*u,oy+38.2f*u);lines.cubicTo(cx+side*5*u,oy+40.4f*u,cx+side*11*u,oy+39.6f*u,cx+side*16f*u,oy+34.2f*u);
            lines.moveTo(cx+side*7.2f*u,oy+43*u);lines.quadTo(cx+side*9.6f*u,oy+49*u,cx+side*8.4f*u,oy+55*u);
        }
        lines.moveTo(cx,oy+31*u);lines.lineTo(cx,oy+55*u);
        for(float y=43.5f;y<=53.5f;y+=5){lines.moveTo(cx-5.2f*u,oy+y*u);lines.quadTo(cx,oy+(y+.8f)*u,cx+5.2f*u,oy+y*u);}
        c.drawPath(lines,ink);
        ink.setColor(0x26ffffff);ink.setStrokeWidth(Math.max(1f,.5f*u));
        Path catch_=new Path();
        for(int side=-1;side<=1;side+=2){catch_.moveTo(cx+side*3*u,oy+33*u);catch_.quadTo(cx+side*8*u,oy+31.4f*u,cx+side*12.5f*u,oy+32.4f*u);}
        c.drawPath(catch_,ink);
        ink.setStyle(Paint.Style.FILL);
        c.restore();
        for(int side=-1;side<=1;side+=2){float v=s[side<0?7:6];if(v>=.85f)rim(c,body.torso,v,u,side,cx);}
        // Head: base tint from its most-hurt zone, then the five face zones as soft glows inside the skull.
        float hurt=0f;for(int i=0;i<6;i++)hurt=Math.max(hurt,s[i]);
        c.save();c.clipPath(body.head);
        int skull=MenuArt.Switch.blend(0xff3a2620,heat(hurt),.42f);
        ink.setAlpha(255);ink.setShader(new RadialGradient(cx-2.4f*u,oy+7*u,15*u,MenuArt.Switch.blend(skull,0xffffffff,.22f),MenuArt.Switch.blend(skull,0xff000000,.28f),Shader.TileMode.CLAMP));
        c.drawRect(cx-10*u,oy,cx+10*u,oy+24*u,ink);ink.setShader(null);
        glowZone(c,cx+3.3f*u,oy+10.2f*u,3.3f*u,s[0]);   // LEFT_EYE (viewer's right)
        glowZone(c,cx-3.3f*u,oy+10.2f*u,3.3f*u,s[1]);   // RIGHT_EYE
        glowZone(c,cx,oy+13.6f*u,2.6f*u,s[2]);          // NOSE
        glowZone(c,cx,oy+17.6f*u,3f*u,s[3]);            // MOUTH
        glowZone(c,cx+5.4f*u,oy+16.2f*u,3.2f*u,s[4]);   // LEFT_JAW
        glowZone(c,cx-5.4f*u,oy+16.2f*u,3.2f*u,s[5]);   // RIGHT_JAW
        // Brow ridge and cheek shadows give the face its shape.
        ink.setStyle(Paint.Style.STROKE);ink.setStrokeCap(Paint.Cap.ROUND);ink.setStrokeWidth(Math.max(1f,.7f*u));ink.setColor(0x66000000);
        Path face=new Path();
        face.moveTo(cx-6*u,oy+8.4f*u);face.quadTo(cx-3*u,oy+7.2f*u,cx-.7f*u,oy+8.6f*u);
        face.moveTo(cx+6*u,oy+8.4f*u);face.quadTo(cx+3*u,oy+7.2f*u,cx+.7f*u,oy+8.6f*u);
        face.moveTo(cx-1.6f*u,oy+18.3f*u);face.quadTo(cx,oy+19*u,cx+1.6f*u,oy+18.3f*u);
        c.drawPath(face,ink);
        ink.setColor(0x99000000);ink.setStrokeWidth(1.6f*u);c.drawPath(body.head,ink);ink.setStyle(Paint.Style.FILL);
        c.restore();
        // Trunks in the corner colour with a waistband and side stripes, shaded like satin.
        c.save();c.clipPath(body.trunks);
        ink.setAlpha(255);ink.setShader(new LinearGradient(cx-15*u,0,cx+15*u,0,new int[]{MenuArt.Switch.blend(accent,0xff000000,.55f),MenuArt.Switch.blend(accent,0xffffffff,.12f),
                MenuArt.Switch.blend(accent,0xff000000,.3f),MenuArt.Switch.blend(accent,0xff000000,.6f)},new float[]{0f,.38f,.62f,1f},Shader.TileMode.CLAMP));
        c.drawRect(cx-16*u,oy+52*u,cx+16*u,oy+72*u,ink);ink.setShader(null);
        ink.setColor(MenuArt.Switch.blend(accent,0xffffffff,.55f));c.drawRect(cx-16*u,oy+54.6f*u,cx+16*u,oy+57.4f*u,ink);
        ink.setColor(0x55000000);c.drawRect(cx-16*u,oy+57.4f*u,cx+16*u,oy+58*u,ink);
        ink.setColor(0xccffffff);
        for(int side=-1;side<=1;side+=2){Path stripe=new Path();stripe.moveTo(cx+side*12.5f*u,oy+58*u);stripe.lineTo(cx+side*13.6f*u,oy+58*u);
            stripe.lineTo(cx+side*14.8f*u,oy+70.5f*u);stripe.lineTo(cx+side*13.6f*u,oy+70.5f*u);stripe.close();c.drawPath(stripe,ink);}
        c.restore();
        // Boots.
        for(int side=-1;side<=1;side+=2){
            float l=cx+(side<0?-11.4f:3.8f)*u,r=cx+(side<0?-3.8f:11.4f)*u;
            ink.setAlpha(255);ink.setShader(new LinearGradient(0,oy+93.6f*u,0,oy+99.6f*u,0xff3b302b,0xff0d0908,Shader.TileMode.CLAMP));
            c.drawRoundRect(l,oy+93.6f*u,r,oy+99.6f*u,2.4f*u,2.4f*u,ink);ink.setShader(null);
            ink.setColor(0x40ffffff);c.drawRect(l+1.2f*u,oy+95*u,r-1.2f*u,oy+95.5f*u,ink);
        }
        // Gloves up in the guard: glossy leather in the corner colour with white cuffs and laces.
        for(int side=-1;side<=1;side+=2){
            float gx=cx+side*14f*u,gy=oy+17.8f*u;
            ink.setColor(0xffeee4d4);c.save();c.rotate(side*-28,cx+side*16f*u,oy+25*u);
            c.drawRoundRect(cx+side*16f*u-3.4f*u,oy+23.4f*u,cx+side*16f*u+3.4f*u,oy+26.8f*u,1.4f*u,1.4f*u,ink);c.restore();
            Path glove=new Path();
            glove.addOval(new RectF(gx-5.4f*u,gy-6.4f*u,gx+5.4f*u,gy+5.6f*u),Path.Direction.CW);
            Path thumb=new Path();thumb.addOval(new RectF(gx-side*4.8f*u-2.2f*u,gy+.8f*u,gx-side*4.8f*u+2.2f*u,gy+5.8f*u),Path.Direction.CW);
            glove.op(thumb,Path.Op.UNION);
            ink.setColor(0xd00b0604);ink.setStyle(Paint.Style.STROKE);ink.setStrokeWidth(1.4f*u);c.drawPath(glove,ink);ink.setStyle(Paint.Style.FILL);
            ink.setAlpha(255);ink.setShader(new RadialGradient(gx-side*1.6f*u-1*u,gy-2.6f*u,8.4f*u,new int[]{MenuArt.Switch.blend(accent,0xffffffff,.6f),accent,MenuArt.Switch.blend(accent,0xff000000,.4f)},
                    new float[]{0f,.45f,1f},Shader.TileMode.CLAMP));
            c.drawPath(glove,ink);ink.setShader(null);
            ink.setColor(0x99ffffff);c.drawOval(gx-3.6f*u,gy-5.2f*u,gx-.2f*u,gy-3.2f*u,ink);
            ink.setStyle(Paint.Style.STROKE);ink.setStrokeWidth(Math.max(1f,.5f*u));ink.setColor(0x55000000);
            c.drawLine(gx+side*.4f*u,gy+3.6f*u,gx+side*3.6f*u,gy+2.6f*u,ink);ink.setStyle(Paint.Style.FILL);
        }
        ink.setStyle(Paint.Style.FILL);ink.setStrokeCap(Paint.Cap.BUTT);
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
    /** Fresh hits: a white-hot core that cools to the zone's heat and a ring thrown outward. True while any runs. */
    private boolean flashes(Canvas c,float cx,float oy,float u,int p,long now){
        boolean any=false;
        for(int i=0;i<ZONES.length;i++){
            long age=now-hitAt[p][i];
            if(hitAt[p][i]==0 || age<0 || age>=FLASH_MS)continue;
            any=true;
            float k=age/(float)FLASH_MS,x=cx+ZONES[i][0]*u,y=oy+ZONES[i][1]*u,r=ZONES[i][2]*u;
            int hot=heat(Math.max(.6f,shown[p][i]))&0xffffff;
            ink.setStyle(Paint.Style.FILL);
            float core=r*(1.6f-.6f*k);
            ink.setShader(new RadialGradient(x,y,core,new int[]{(Math.round(235*(1f-k))<<24)|0xfff6dc,(Math.round(160*(1f-k))<<24)|hot,hot},
                    new float[]{0f,.45f,1f},Shader.TileMode.CLAMP));
            c.drawCircle(x,y,core,ink);ink.setShader(null);
            ink.setStyle(Paint.Style.STROKE);ink.setStrokeWidth(Math.max(1f,1.2f*u*(1f-k)));
            ink.setColor((Math.round(220*(1f-k)*(1f-k))<<24)|0xfff0c8);
            // Face zones are small: their ring can travel; torso zones are big, so theirs only swells a little.
            float ring=r*(1.1f+(i>=6?.45f:2.4f)*(1f-(1f-k)*(1f-k)));
            c.drawOval(x-ring*(i>=6?.8f:1f),y-ring*(i>=6?1.15f:.85f),x+ring*(i>=6?.8f:1f),y+ring*(i>=6?1.15f:.85f),ink);
            ink.setStyle(Paint.Style.FILL);
        }
        return any;
    }
    /** The boxer's outline pieces in screen coordinates. */
    private static final class Body {final Path silhouette,head,torso,trunks;
        Body(Path s,Path h,Path t,Path k){silhouette=s;head=h;torso=t;trunks=k;}}
    private final Body[] cached=new Body[2];private final float[][] cachedAt=new float[2][3];
    /** A boxer in a high guard, front view, in a 64 x 100 unit frame (origin top centre): smooth anatomical curves
     *  (traps, rounded delts, a V-taper to the waist, biceps, bent knees and calves) joined into one silhouette. */
    private Body body(int p,float cx,float oy,float u){
        float[] at=cachedAt[p];
        if(cached[p]!=null && cx==at[0] && oy==at[1] && u==at[2])return cached[p];
        // Right half outlines from the top centre down; mirror() closes them into symmetric shapes.
        Path head=mirror(cx,oy,u,new float[]{0,1f, 5f,1.5f, 7.8f,5.2f, 8.3f,10.2f, 7.7f,14.8f, 6f,18.6f, 3.4f,21.6f, 0,22.8f});
        Path torso=mirror(cx,oy,u,new float[]{0,19.6f, 4.8f,19.8f, 6.2f,23f, 12.6f,25f, 18.2f,26.8f, 21.6f,30.8f, 21f,35.4f,
                17.6f,37.8f, 16.6f,43f, 14f,48.6f, 12.8f,53.2f, 13.2f,56.6f, 0,56.6f});
        Path trunks=mirror(cx,oy,u,new float[]{0,54.4f, 12.6f,54.4f, 13.6f,61f, 14.8f,70.8f, 8f,71.6f, 1.4f,70.6f, 0,66.6f});
        Path silhouette=new Path(head);
        silhouette.op(torso,Path.Op.UNION);silhouette.op(trunks,Path.Op.UNION);
        for(int side=-1;side<=1;side+=2){
            // Legs: thigh, knee, calf bulge, ankle; slightly apart in a fighting stance.
            Path leg=spline(cx,oy,u,side,new float[]{1.2f,66, 14.2f,66, 14f,74.4f, 12f,81.6f, 12.8f,87.4f, 10.8f,93.4f, 10.4f,96.4f,
                    4.6f,96.4f, 4f,91.6f, 4.4f,86.2f, 3.2f,80.6f, 1.8f,73.6f});
            silhouette.op(leg,Path.Op.UNION);
            // Arms: upper arm with a biceps bulge out to a tucked elbow, forearm angled up to the glove.
            silhouette.op(limb(cx+side*18.2f*u,oy+29.4f*u,5.4f*u,cx+side*21.6f*u,oy+41f*u,4f*u),Path.Op.UNION);
            silhouette.op(circle(cx+side*20.6f*u,oy+34.4f*u,5f*u),Path.Op.UNION);
            silhouette.op(limb(cx+side*21.6f*u,oy+41.2f*u,4.2f*u,cx+side*15.6f*u,oy+25.4f*u,3.4f*u),Path.Op.UNION);
            silhouette.op(circle(cx+side*19.8f*u,oy+34f*u,3.9f*u),Path.Op.UNION);   // forearm mass
            // Ears.
            silhouette.op(oval(cx+side*8.2f*u,oy+11.6f*u,1.5f*u,2.6f*u),Path.Op.UNION);
        }
        cached[p]=new Body(silhouette,head,torso,trunks);at[0]=cx;at[1]=oy;at[2]=u;
        return cached[p];
    }
    private static Path circle(float x,float y,float r){Path p=new Path();p.addCircle(x,y,r,Path.Direction.CW);return p;}
    private static Path oval(float x,float y,float rx,float ry){Path p=new Path();p.addOval(new RectF(x-rx,y-ry,x+rx,y+ry),Path.Direction.CW);return p;}
    /** A tapered capsule between two circles (outer tangents plus both end caps). */
    private static Path limb(float x1,float y1,float r1,float x2,float y2,float r2){
        float dx=x2-x1,dy=y2-y1,len=(float)Math.hypot(dx,dy);
        Path p=circle(x1,y1,r1);p.op(circle(x2,y2,r2),Path.Op.UNION);
        if(len<=Math.abs(r1-r2))return p;
        float ux=dx/len,uy=dy/len,k=(r1-r2)/len,q=(float)Math.sqrt(Math.max(0,1-k*k));
        float nx1=ux*k-uy*q,ny1=uy*k+ux*q,nx2=ux*k+uy*q,ny2=uy*k-ux*q;
        Path hull=new Path();
        hull.moveTo(x1+nx1*r1,y1+ny1*r1);hull.lineTo(x2+nx1*r2,y2+ny1*r2);
        hull.lineTo(x2+nx2*r2,y2+ny2*r2);hull.lineTo(x1+nx2*r1,y1+ny2*r1);hull.close();
        p.op(hull,Path.Op.UNION);return p;
    }
    /** Mirror a right-half outline (x>=0 points from the top centre to the bottom centre) into a smooth closed shape. */
    private static Path mirror(float cx,float oy,float u,float[] half){
        int n=half.length/2,m=2*n-2;
        float[] pts=new float[2*m];
        for(int i=0;i<n;i++){pts[2*i]=half[2*i];pts[2*i+1]=half[2*i+1];}
        for(int i=1;i<n-1;i++){int j=n-1-i;pts[2*(n-1+i)]=-half[2*j];pts[2*(n-1+i)+1]=half[2*j+1];}
        return spline(cx,oy,u,1,pts);
    }
    /** A closed Catmull-Rom spline through unit points (x mirrored by side), as cubic Beziers: smooth, no corners. */
    private static Path spline(float cx,float oy,float u,int side,float[] xy){
        int n=xy.length/2;Path p=new Path();
        float[] x=new float[n],y=new float[n];
        for(int i=0;i<n;i++){x[i]=cx+side*xy[2*i]*u;y[i]=oy+xy[2*i+1]*u;}
        p.moveTo(x[0],y[0]);
        for(int i=0;i<n;i++){
            int a=(i-1+n)%n,b=(i+1)%n,e=(i+2)%n;
            p.cubicTo(x[i]+(x[b]-x[a])/6f,y[i]+(y[b]-y[a])/6f,x[b]-(x[e]-x[i])/6f,y[b]-(y[e]-y[i])/6f,x[b],y[b]);
        }
        p.close();return p;
    }
    /** One face zone: a soft glow in its heat colour that fades into the skull, with a bright core when hurt. */
    private void glowZone(Canvas c,float x,float y,float r,float v){
        int hot=heat(v)&0xffffff;
        ink.setStyle(Paint.Style.FILL);
        ink.setAlpha(255);ink.setShader(new RadialGradient(x,y,r,new int[]{(0xff<<24)|hot,(0xe6<<24)|hot,hot},new float[]{0f,.55f,1f},Shader.TileMode.CLAMP));
        c.drawCircle(x,y,r,ink);ink.setShader(null);
        if(v>=.7f){ink.setColor((Math.round(Math.min(200f,600*(v-.6f)))<<24)|0xffe6c0);c.drawCircle(x,y,r*.28f,ink);}
    }
    /** Badly hurt torso halves pick up a hot rim along their outline. */
    private void rim(Canvas c,Path part,float v,float u,int side,float cx){
        c.save();
        if(side<0)c.clipRect(cx-40*u,0,cx,getHeight());else c.clipRect(cx,0,cx+40*u,getHeight());
        ink.setStyle(Paint.Style.STROKE);ink.setStrokeWidth(1.8f*u);
        ink.setColor((Math.round(Math.min(255f,1400*(v-.8f)))<<24)|0xff3a1a);c.drawPath(part,ink);
        ink.setStyle(Paint.Style.FILL);c.restore();
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
