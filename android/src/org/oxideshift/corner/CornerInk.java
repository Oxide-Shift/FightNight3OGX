package org.oxideshift.corner;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** A fixed screen-printed edge shared by launcher and ringside surfaces. */
final class CornerInk extends Drawable {
    private final int base,accent;
    private final Path edge=new Path();
    private final Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);
    CornerInk(int base,int accent){this.base=base;this.accent=accent;}
    @Override protected void onBoundsChange(Rect b){
        edge.reset();int w=b.width(),h=b.height();
        float unit=Math.max(1,Math.min(w,h)/150f),strip=Math.min(w*.045f,unit*7);
        edge.moveTo(b.left,b.top);edge.lineTo(b.left+strip*1.4f,b.top);
        edge.lineTo(b.left+strip*.3f,b.bottom);edge.lineTo(b.left,b.bottom);edge.close();
    }
    @Override public void draw(Canvas c){
        ink.setColor(base);c.drawRect(getBounds(),ink);ink.setColor(accent);
        c.drawPath(edge,ink);
    }
    @Override public void setAlpha(int x){}
    @Override public void setColorFilter(ColorFilter x){}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
