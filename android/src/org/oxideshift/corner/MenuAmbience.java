package org.oxideshift.corner;

import android.graphics.*;

/** Arena atmosphere behind the lower-screen menu pages: two warm spotlights sweeping slowly from the rafters,
 *  haze drifting through their light, and photographers' flashes popping in the upper tiers. Pure
 *  function of time (no state), drawn under the page; the host redraws it at about 30 fps while menus show. */
final class MenuAmbience {
    static final long FRAME_MS=33;
    private final Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path beam=new Path();

    /** Deterministic hash in [0,1) so flashes land in the same places for the same moment. */
    static float noise(long n){
        n=(n^(n>>>33))*0xff51afd7ed558ccdL;n=(n^(n>>>33))*0xc4ceb9fe1a85ec53L;n^=n>>>33;
        return (n>>>40)/(float)(1L<<24);
    }
    /** Flash k (one slot every 420 ms): its position and brightness at time t, or brightness 0 when not lit. */
    static float flash(long t,long slot,float[] at){
        long start=slot*420+(long)(noise(slot*7+1)*300);
        long age=t-start;
        if(noise(slot*7+5)<.45f || age<0 || age>260){return 0f;}
        // Upper tiers: the band above the page's plates and cards, where the flashes stay visible.
        at[0]=.04f+.92f*noise(slot*7+2);at[1]=.12f+.22f*noise(slot*7+3);
        float rise=Math.min(1f,age/40f),fall=1f-Math.max(0f,(age-40)/220f);
        return rise*fall*fall*(.55f+.45f*noise(slot*7+4));
    }
    private final float[] spot=new float[2];

    void draw(Canvas c,int w,int h,float dp,long t){
        if(w<=0 || h<=0)return;
        ink.setStyle(Paint.Style.FILL);
        // Spotlights: each pivots from above the screen, swinging a few degrees on its own slow period.
        for(int k=0;k<2;k++){
            float px=k==0?w*.18f:w*.82f,py=-h*.12f;
            double swing=Math.sin(t/(k==0?7100.0:8900.0)*Math.PI*2+(k==0?0:1.7))*Math.toRadians(9);
            double aim=Math.toRadians(k==0?72:108)+swing;
            float len=h*1.25f,spread=(float)Math.toRadians(8.5);
            float ex1=px+(float)Math.cos(aim-spread)*len,ey1=py+(float)Math.sin(aim-spread)*len;
            float ex2=px+(float)Math.cos(aim+spread)*len,ey2=py+(float)Math.sin(aim+spread)*len;
            beam.reset();beam.moveTo(px,py);beam.lineTo(ex1,ey1);beam.lineTo(ex2,ey2);beam.close();
            float fx=px+(float)Math.cos(aim)*len,fy=py+(float)Math.sin(aim)*len;
            ink.setShader(new LinearGradient(px,py,fx,fy,0x1effd9a0,0x00ffd9a0,Shader.TileMode.CLAMP));
            c.drawPath(beam,ink);
        }
        ink.setShader(null);
        // Haze: three soft, wide glows drifting sideways at different speeds and wrapping around.
        for(int k=0;k<3;k++){
            float y=h*(.22f+.24f*k),radius=h*.14f;
            float drift=(float)((t/(18000.0+k*5000)+k*.37)%1.0);
            float x=-w*.4f+drift*w*1.8f;
            c.save();c.translate(x,y);c.scale(3.2f,1f);
            ink.setShader(new RadialGradient(0,0,radius,0x12f0c8a0,0x00f0c8a0,Shader.TileMode.CLAMP));
            c.drawCircle(0,0,radius,ink);
            c.restore();
        }
        ink.setShader(null);
        // Camera flashes in the crowd: a hot core, a soft halo and a four-point star while bright.
        long now=t/420;
        for(long slot=now-1;slot<=now;slot++){
            float b=flash(t,slot,spot);
            if(b<=0f)continue;
            float x=spot[0]*w,y=spot[1]*h,r=(4+6*b)*dp;
            ink.setShader(new RadialGradient(x,y,r*3.2f,Color.argb(Math.round(110*b),255,240,214),0x00fff0d6,Shader.TileMode.CLAMP));
            c.drawCircle(x,y,r*3.2f,ink);ink.setShader(null);
            // Tapered four-point star: thin diamonds, longer across than up and down.
            ink.setColor(Color.argb(Math.round(210*b),255,248,234));
            float across=r*2.2f,up=r*1.4f,waist=Math.max(.8f*dp,r*.16f);
            beam.reset();
            beam.moveTo(x-across,y);beam.lineTo(x,y-waist);beam.lineTo(x+across,y);beam.lineTo(x,y+waist);beam.close();
            beam.moveTo(x,y-up);beam.lineTo(x+waist,y);beam.lineTo(x,y+up);beam.lineTo(x-waist,y);beam.close();
            c.drawPath(beam,ink);
            ink.setColor(Color.argb(Math.round(255*b),255,255,250));c.drawCircle(x,y,r*.32f,ink);
        }
    }
}
