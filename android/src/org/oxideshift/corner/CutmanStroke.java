package org.oxideshift.corner;

/** One finger owns the treatment stick until lifted, cancelled, or the corner ends. */
final class CutmanStroke {
    interface Stick { void set(boolean down,int x,int y); }
    private final Stick output;
    int finger=-1;
    float originX,originY;
    CutmanStroke(Stick output){this.output=output;}
    void begin(int id,float atX,float atY){
        if(finger>=0)return;
        finger=id;originX=atX;originY=atY;output.set(true,0,0);
    }
    void move(int id,float atX,float atY,float radius){
        if(id!=finger || finger<0)return;
        float horizontal=(atX-originX)/Math.max(1,radius);
        float vertical=(originY-atY)/Math.max(1,radius);
        float scale=(float)Math.max(1,Math.hypot(horizontal,vertical));
        float x=horizontal/scale,y=vertical/scale;
        output.set(true,Math.round(32767*x),Math.round(32767*y));
    }
    void release(){
        if(finger<0)return;
        finger=-1;output.set(false,0,0);
    }
}
