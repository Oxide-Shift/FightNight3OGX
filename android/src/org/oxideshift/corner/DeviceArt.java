package org.oxideshift.corner;

import android.graphics.*;

/** Product-shot style handhelds for the Options plate: drawn flat, then tilted into a three-quarter view with
 *  an extruded edge and a soft floor shadow, like the disc's own controller photo. No logos or brand marks. */
final class DeviceArt {
    static final int THOR=0, DUO=1, PHONE=2, COUNT=3;
    private DeviceArt(){}

    /** A size x size transparent bitmap of one device. */
    static Bitmap render(int kind,int size){
        int flat=1024;
        Bitmap front=Bitmap.createBitmap(flat,flat,Bitmap.Config.ARGB_8888);
        Canvas f=new Canvas(front);
        RectF bounds;
        switch(kind){
            case THOR:bounds=clamshell(f,flat,false);break;
            case DUO:bounds=clamshell(f,flat,true);break;
            default:bounds=phone(f,flat);break;
        }
        Bitmap out=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(out);
        // Three-quarter view: the right edge recedes, the whole device leans back a little.
        float l=bounds.left,t=bounds.top,r=bounds.right,b=bounds.bottom;
        float s=size/(float)flat,cx=size*.5f,cy=size*.52f,hw=(r-l)*s*.5f*.92f,hh=(b-t)*s*.5f*.92f;
        float[] src={l,t,r,t,r,b,l,b};
        float[] dst={cx-hw*1.02f,cy-hh*.98f, cx+hw*.92f,cy-hh*.84f, cx+hw*.96f,cy+hh*.86f, cx-hw*.98f,cy+hh*1.02f};
        Matrix m=new Matrix();m.setPolyToPoly(src,0,dst,0,4);
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        // Floor shadow.
        p.setShader(new RadialGradient(cx,cy+hh*1.02f,hw*1.15f,0x8a000000,0x00000000,Shader.TileMode.CLAMP));
        c.save();c.scale(1f,.16f,cx,cy+hh*1.02f);c.drawCircle(cx,cy+hh*1.02f,hw*1.15f,p);c.restore();
        p.setShader(null);
        // Extruded edge: darker copies stepped down-left give the shell its thickness.
        Paint edge=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        edge.setColorFilter(new PorterDuffColorFilter(0xff0b0b0d,PorterDuff.Mode.SRC_IN));
        int depth=Math.max(2,Math.round(size*.024f));
        for(int i=depth;i>0;i--){
            c.save();c.translate(-i*.55f,i);c.concat(m);c.drawBitmap(front,0,0,edge);c.restore();
        }
        Paint rim=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        rim.setColorFilter(new PorterDuffColorFilter(0xff3a3a40,PorterDuff.Mode.SRC_IN));
        c.save();c.translate(-.6f,.9f);c.concat(m);c.drawBitmap(front,0,0,rim);c.restore();
        c.save();c.concat(m);c.drawBitmap(front,0,0,p);c.restore();
        front.recycle();
        return out;
    }

    // ---- shared parts -------------------------------------------------------------------------------------
    private static Paint fill(int color){Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(color);return p;}
    private static void shell(Canvas c,RectF r,float radius,int top,int bottom){
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(r.left,r.top,r.right*.3f+r.left*.7f,r.bottom,top,bottom,Shader.TileMode.CLAMP));
        c.drawRoundRect(r,radius,radius,p);
        // Specular sweep across the upper-left of the plastic.
        p.setShader(new LinearGradient(r.left,r.top,r.left+r.width()*.55f,r.top+r.height()*.55f,0x2effffff,0x00ffffff,Shader.TileMode.CLAMP));
        c.drawRoundRect(r,radius,radius,p);
        p.setShader(null);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3);p.setColor(0x33ffffff);
        RectF in=new RectF(r);in.inset(2,2);c.drawRoundRect(in,radius-2,radius-2,p);
    }
    /** A dark glass screen with a faint arena glow, so it reads as switched on. */
    private static void screen(Canvas c,RectF r,float radius,boolean ring){
        c.drawRoundRect(r,radius,radius,fill(0xff050506));
        RectF g=new RectF(r);g.inset(r.width()*.025f,r.width()*.025f);
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new RadialGradient(g.centerX(),g.centerY()+g.height()*.1f,Math.max(g.width(),g.height())*.62f,
                new int[]{0xff8a3a1c,0xff3b170d,0xff120807},new float[]{0f,.55f,1f},Shader.TileMode.CLAMP));
        c.drawRect(g,p);p.setShader(null);
        if(ring){
            // A ring and two silhouettes under the lights.
            float cx=g.centerX(),cy=g.centerY()+g.height()*.12f,w=g.width()*.62f,h=g.height()*.22f;
            Path apron=new Path();apron.moveTo(cx-w*.5f,cy);apron.lineTo(cx+w*.5f,cy);apron.lineTo(cx+w*.62f,cy+h);apron.lineTo(cx-w*.62f,cy+h);apron.close();
            c.drawPath(apron,fill(0xff2a1a33));
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(1,g.height()*.012f));p.setColor(0xffc8473a);
            for(int i=0;i<3;i++){float y=cy-h*(.25f+.28f*i);c.drawLine(cx-w*.5f,y,cx+w*.5f,y,p);}
            p.setStyle(Paint.Style.FILL);p.setColor(0xff0d0606);
            float bh=g.height()*.42f;
            boxer(c,cx-w*.17f,cy+h*.25f,bh,1,p);
            boxer(c,cx+w*.17f,cy+h*.25f,bh*.97f,-1,p);
            p.setShader(new RadialGradient(cx,g.top,g.width()*.5f,0x55ffe2b0,0x00ffe2b0,Shader.TileMode.CLAMP));
            c.drawRect(g,p);p.setShader(null);
        }
        // Glass reflection.
        Path glare=new Path();glare.moveTo(r.left,r.top);glare.lineTo(r.left+r.width()*.42f,r.top);
        glare.lineTo(r.left+r.width()*.18f,r.bottom);glare.lineTo(r.left,r.bottom);glare.close();
        p.setShader(new LinearGradient(r.left,r.top,r.left+r.width()*.4f,r.bottom,0x22ffffff,0x00ffffff,Shader.TileMode.CLAMP));
        c.save();c.clipRect(r);c.drawPath(glare,p);c.restore();
    }
    /** A small boxer in silhouette, facing (dir=1 right, -1 left), feet at y, height h. */
    private static void boxer(Canvas c,float x,float y,float h,int dir,Paint p){
        float u=h/10f;
        Path b=new Path();
        // Legs in a staggered stance.
        b.moveTo(x-u*1.6f,y);b.lineTo(x-u*.9f,y-u*4.4f);b.lineTo(x+u*.6f,y-u*4.4f);b.lineTo(x+u*1.9f,y);
        b.lineTo(x+u*1.1f,y);b.lineTo(x,y-u*3.2f);b.lineTo(x-u*.8f,y);b.close();
        // Torso leaning toward the opponent.
        b.moveTo(x-u*1.2f,y-u*4.2f);b.cubicTo(x-u*1.6f,y-u*6.2f,x-u*1.3f,y-u*7.6f,x+dir*u*.2f-u*1.4f,y-u*8f);
        b.lineTo(x+dir*u*.2f+u*1.4f,y-u*8f);b.cubicTo(x+u*1.4f,y-u*7.4f,x+u*1.5f,y-u*6f,x+u*1.1f,y-u*4.2f);b.close();
        c.drawPath(b,p);
        c.drawCircle(x+dir*u*.55f,y-u*8.9f,u*.85f,p);               // head
        c.drawCircle(x+dir*u*1.9f,y-u*7.9f,u*.72f,p);               // lead glove
        c.drawCircle(x+dir*u*1.1f,y-u*8.5f,u*.68f,p);               // rear glove guarding the chin
    }
    private static void stick(Canvas c,float x,float y,float r){
        c.drawCircle(x,y,r*1.18f,fill(0xff0c0c0e));
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new RadialGradient(x-r*.3f,y-r*.35f,r*1.25f,new int[]{0xff4a4a50,0xff1d1d21,0xff0a0a0b},new float[]{0f,.6f,1f},Shader.TileMode.CLAMP));
        c.drawCircle(x,y,r,p);p.setShader(null);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(r*.08f);p.setColor(0x40ffffff);
        c.drawCircle(x,y,r*.66f,p);
    }
    private static void dpad(Canvas c,float x,float y,float s){
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        c.drawCircle(x,y,s*1.05f,fill(0xff0e0e10));
        p.setShader(new LinearGradient(x-s,y-s,x+s,y+s,0xff3c3c42,0xff141417,Shader.TileMode.CLAMP));
        float a=s*.34f;
        c.drawRoundRect(new RectF(x-a,y-s*.86f,x+a,y+s*.86f),a*.4f,a*.4f,p);
        c.drawRoundRect(new RectF(x-s*.86f,y-a,x+s*.86f,y+a),a*.4f,a*.4f,p);
        p.setShader(null);c.drawCircle(x,y,a*.45f,fill(0xff111114));
    }
    /** Face buttons: muted colours, top/right/left/bottom = Y/B/X/A on an Xbox-style layout. */
    private static void face(Canvas c,float x,float y,float s,boolean colour){
        int[] inks=colour?new int[]{0xffd8b23a,0xffc7432f,0xff3a6fc0,0xff4f9a3c}:new int[]{0xff2c2c31,0xff2c2c31,0xff2c2c31,0xff2c2c31};
        float[][] at={{0,-1},{1,0},{-1,0},{0,1}};
        for(int i=0;i<4;i++){
            float bx=x+at[i][0]*s,by=y+at[i][1]*s,r=s*.48f;
            c.drawCircle(bx,by+r*.12f,r,fill(0xff070708));
            Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setShader(new RadialGradient(bx-r*.3f,by-r*.4f,r*1.3f,lighten(inks[i]),inks[i],Shader.TileMode.CLAMP));
            c.drawCircle(bx,by,r,p);
        }
    }
    private static int lighten(int c){
        int r=Math.min(255,((c>>16)&255)+70),g=Math.min(255,((c>>8)&255)+70),b=Math.min(255,(c&255)+70);
        return 0xff000000|(r<<16)|(g<<8)|b;
    }
    private static void pill(Canvas c,float x,float y,float w){
        c.drawRoundRect(new RectF(x-w*.5f,y-w*.2f,x+w*.5f,y+w*.2f),w*.2f,w*.2f,fill(0xff222226));
    }

    // ---- devices ------------------------------------------------------------------------------------------
    /** Open clamshell: 16:9 top display over a lower body with the second screen between the controls.
     *  The Thor's lower screen is near-square (1240x1080); the RP Duo's is 4:3 (1280x960). */
    private static RectF clamshell(Canvas c,int n,boolean duo){
        float w=n*.86f,x0=(n-w)*.5f;
        float lidH=w*.56f,bodyH=w*.6f,gap=w*.035f,top=(n-lidH-bodyH-gap)*.5f;
        RectF lid=new RectF(x0+w*.04f,top,x0+w*.96f,top+lidH);
        RectF body=new RectF(x0,top+lidH+gap,x0+w,top+lidH+gap+bodyH);
        int hi=duo?0xff5c606a:0xff3a3442, lo=duo?0xff24272d:0xff17141b;
        shell(c,lid,w*.05f,hi,lo);
        float bez=lid.width()*.045f;
        RectF top16=new RectF(lid.left+bez,lid.top+bez,lid.right-bez,lid.bottom-bez);
        float h16=top16.width()*9/16;if(h16<top16.height()){float d=(top16.height()-h16)*.5f;top16.top+=d;top16.bottom-=d;}
        screen(c,top16,w*.012f,true);
        // Hinge.
        Paint hinge=new Paint(Paint.ANTI_ALIAS_FLAG);
        hinge.setShader(new LinearGradient(0,lid.bottom,0,body.top+gap,0xff2a2a2f,0xff0d0d10,Shader.TileMode.CLAMP));
        c.drawRoundRect(new RectF(lid.left+w*.1f,lid.bottom-gap*.3f,lid.right-w*.1f,body.top+gap*.6f),gap,gap,hinge);
        shell(c,body,w*.09f,hi,lo);
        // Lower screen.
        float sh=bodyH*.74f,sw=duo?sh*4/3:sh*1240/1080f;
        RectF low=new RectF(body.centerX()-sw*.5f,body.top+bodyH*.12f,body.centerX()+sw*.5f,body.top+bodyH*.12f+sh);
        c.drawRoundRect(new RectF(low.left-bez*.7f,low.top-bez*.7f,low.right+bez*.7f,low.bottom+bez*.7f),w*.02f,w*.02f,fill(0xff0a0a0c));
        screen(c,low,w*.008f,false);
        // Corner card on the lower screen: two coloured bars.
        Paint bar=fill(0xff5b8fd6);c.drawRect(low.left+sw*.08f,low.top+sh*.18f,low.left+sw*.46f,low.top+sh*.22f,bar);
        bar.setColor(0xffe0644f);c.drawRect(low.left+sw*.54f,low.top+sh*.18f,low.left+sw*.92f,low.top+sh*.22f,bar);
        bar.setColor(0x55f0e2c0);
        for(int i=0;i<3;i++){c.drawRect(low.left+sw*.08f,low.top+sh*(.34f+.13f*i),low.left+sw*.92f,low.top+sh*(.36f+.13f*i),bar);}
        float side=(body.width()-sw)*.25f,lx=body.left+side*1.05f,rx=body.right-side*1.05f;
        float r=Math.min(side*.62f,bodyH*.11f);
        if(duo){stick(c,lx,body.top+bodyH*.62f,r);dpad(c,lx,body.top+bodyH*.3f,r*1.05f);
            face(c,rx,body.top+bodyH*.3f,r*.95f,true);stick(c,rx,body.top+bodyH*.62f,r);}
        else{stick(c,lx,body.top+bodyH*.28f,r);dpad(c,lx,body.top+bodyH*.62f,r*1.05f);
            face(c,rx,body.top+bodyH*.28f,r*.95f,false);stick(c,rx,body.top+bodyH*.62f,r);}
        pill(c,lx,body.top+bodyH*.86f,r*.9f);pill(c,rx,body.top+bodyH*.86f,r*.9f);
        return new RectF(body.left,lid.top,body.right,body.bottom);
    }
    /** A phone in landscape, held by a telescopic controller. */
    private static RectF phone(Canvas c,int n){
        float pw=n*.6f,ph=pw*.47f,cx=n*.5f,cy=n*.5f;
        RectF phone=new RectF(cx-pw*.5f,cy-ph*.5f,cx+pw*.5f,cy+ph*.5f);
        float gw=n*.17f,gh=ph*1.22f;
        RectF left=new RectF(phone.left-gw+n*.012f,cy-gh*.5f,phone.left+n*.03f,cy+gh*.5f);
        RectF right=new RectF(phone.right-n*.03f,cy-gh*.5f,phone.right+gw-n*.012f,cy+gh*.5f);
        // Telescopic bridge behind the phone.
        c.drawRoundRect(new RectF(left.centerX(),cy+ph*.18f,right.centerX(),cy+ph*.36f),ph*.08f,ph*.08f,fill(0xff141417));
        shell(c,left,gw*.42f,0xff3e3e45,0xff151518);
        shell(c,right,gw*.42f,0xff3e3e45,0xff151518);
        shell(c,phone,ph*.12f,0xff2a2a30,0xff0b0b0d);
        RectF glass=new RectF(phone);glass.inset(ph*.04f,ph*.04f);
        screen(c,glass,ph*.09f,true);
        float r=gw*.2f;
        stick(c,left.centerX()-gw*.04f,cy-gh*.2f,r);dpad(c,left.centerX()-gw*.04f,cy+gh*.16f,r*1.05f);
        face(c,right.centerX()+gw*.04f,cy-gh*.2f,r*.95f,true);stick(c,right.centerX()+gw*.04f,cy+gh*.16f,r);
        return new RectF(left.left,left.top,right.right,left.bottom);
    }
}
