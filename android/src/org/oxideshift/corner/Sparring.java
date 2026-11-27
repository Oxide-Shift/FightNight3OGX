package org.oxideshift.corner;

import android.graphics.*;

/** Two boxers sparring in silhouette, drawn from simple limbs: the lead boxer works the jab, the other slips and
 *  answers with a straight right, both bouncing on their toes. Original procedural art (nothing from the disc), used on
 *  the transition's LOADING plate in the retail style: near-black figures with a warm rim light. With `t` held at 0 it
 *  is a still guard pose. */
final class Sparring {
    static final long CYCLE_MS=2400;
    private final Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path=new Path();
    Sparring(){ink.setStrokeCap(Paint.Cap.ROUND);ink.setStrokeJoin(Paint.Join.ROUND);}

    /** 0..1 punch extension: snaps out over the first third of `dur`, returns over the rest. Pure, for tests. */
    static float punch(float p,float start,float dur){
        float k=(p-start)/dur;
        if(k<=0f || k>=1f)return 0f;
        return k<.32f?1f-(1f-k/.32f)*(1f-k/.32f):1f-(k-.32f)/.68f;
    }

    /** Both boxers, standing on `floor`, `height` tall, centred on `cx`. */
    void draw(Canvas c,float cx,float floor,float height,long t,int body,int rim){
        float p=(t%CYCLE_MS)/(float)CYCLE_MS;
        double tau=2*Math.PI*p;
        // Lead (left, facing right): jab, double jab. Answer (right, facing left): slips the second, fires a straight.
        float jab=Math.max(punch(p,.04f,.16f),punch(p,.26f,.14f));
        float slip=punch(p,.25f,.3f);
        float cross=punch(p,.5f,.2f);
        float duck=punch(p,.48f,.3f);
        float u=height/100f,gap=height*.6f;
        float bobA=(float)Math.sin(tau*2)*1.6f,bobB=(float)Math.sin(tau*2+1.9)*1.6f;
        float stepA=(float)Math.sin(tau)*2.5f+jab*3f-duck*4f,stepB=-(float)Math.sin(tau+.7)*2.5f-cross*3f+slip*2f;
        for(int pass=0;pass<2;pass++){
            int color=pass==0?rim:body;float off=pass==0?-.9f*u:0f;
            figure(c,cx-gap/2f+stepA*u+off,floor+off,u,+1,bobA,-duck*4f,jab,0f,color);
            figure(c,cx+gap/2f-stepB*u+off,floor+off,u,-1,bobB,-slip*6f,0f,cross,color);
        }
    }

    /** One boxer, feet at (x,floor), facing `dir` (+1 right). `lean` tilts the upper body back (negative) or in. */
    private void figure(Canvas c,float x,float floor,float u,int dir,float bob,float lean,float lead,float rear,int color){
        ink.setColor(color);
        float hipX=x+dir*(lean*.25f)*u,hipY=floor-46*u+bob*u;
        float shX=x+dir*(2f+lean)*u,shY=floor-77*u+bob*u;
        // Legs: lead foot forward, back foot behind on its toes.
        ink.setStyle(Paint.Style.STROKE);
        ink.setStrokeWidth(10f*u);
        float kneeF=x+dir*14*u,kneeB=x-dir*6*u;
        c.drawLine(hipX+dir*3*u,hipY,kneeF,floor-23*u+bob*.5f*u,ink);
        c.drawLine(hipX-dir*3*u,hipY,kneeB,floor-22*u+bob*.5f*u,ink);
        ink.setStrokeWidth(8f*u);
        c.drawLine(kneeF,floor-23*u+bob*.5f*u,x+dir*15*u,floor-2*u,ink);
        c.drawLine(kneeB,floor-22*u+bob*.5f*u,x-dir*17*u,floor-3*u,ink);
        ink.setStrokeWidth(5f*u);
        c.drawLine(x+dir*13*u,floor-1.5f*u,x+dir*21*u,floor-1.5f*u,ink);
        c.drawLine(x-dir*19*u,floor-2*u,x-dir*13*u,floor-1*u,ink);
        // Trunks and torso.
        ink.setStyle(Paint.Style.FILL);
        path.reset();
        path.moveTo(hipX-14*u,hipY+6*u);path.lineTo(hipX+14*u,hipY+6*u);
        path.lineTo(hipX+11*u,hipY-10*u);path.lineTo(hipX-11*u,hipY-10*u);path.close();
        c.drawPath(path,ink);
        path.reset();
        path.moveTo(hipX-9*u,hipY-8*u);path.lineTo(hipX+9*u,hipY-8*u);
        path.lineTo(shX+13*u,shY+1*u);path.lineTo(shX-13*u,shY+1*u);path.close();
        c.drawPath(path,ink);
        c.drawCircle(shX-5*u,shY+2*u,8.5f*u,ink);c.drawCircle(shX+5*u,shY+2*u,8.5f*u,ink);
        // Head, chin tucked behind the lead shoulder.
        c.drawRect(shX-3.6f*u+dir*1.5f*u,shY-7*u,shX+3.6f*u+dir*1.5f*u,shY,ink);
        c.drawCircle(shX+dir*3.2f*u,shY-12*u,7.6f*u,ink);
        // Arms: from guard (gloves by the face) to full extension at head height.
        arm(c,shX+dir*6*u,shY+1*u,u,dir,lead,shX+dir*15*u,shY-7*u,shX+dir*49*u,shY-10*u);
        arm(c,shX-dir*4*u,shY+1*u,u,dir,rear,shX+dir*8*u,shY-9*u,shX+dir*50*u,shY-11*u);
    }
    private void arm(Canvas c,float sx,float sy,float u,int dir,float e,float gx,float gy,float ex,float ey){
        float hx=gx+(ex-gx)*e,hy=gy+(ey-gy)*e;
        // Elbow tucked down at guard, straightening as the punch extends.
        float tuck=1f-e;
        float mx=(sx+hx)/2f-dir*2*u*tuck,my=(sy+hy)/2f+9*u*tuck;
        ink.setStyle(Paint.Style.STROKE);
        ink.setStrokeWidth(7.2f*u);c.drawLine(sx,sy,mx,my,ink);
        ink.setStrokeWidth(6.2f*u);c.drawLine(mx,my,hx,hy,ink);
        ink.setStyle(Paint.Style.FILL);c.drawCircle(hx,hy,6.8f*u,ink);
    }
}
