package org.oxideshift.corner;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** A career panel uses the background belonging to that screen on the player's disc. */
final class CareerBackdrop extends Drawable {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final Rect source=new Rect();
    private final int base, left, right;
    private Bitmap art;
    private LinearGradient wash;

    CareerBackdrop(int base,int left,int right){
        this.base=base;this.left=left;this.right=right;
    }
    void setArt(Bitmap next){
        if(art==next)return;
        art=next;onBoundsChange(getBounds());invalidateSelf();
    }
    @Override protected void onBoundsChange(Rect b){
        if(b.isEmpty())return;
        wash=new LinearGradient(b.left,b.top,b.right,b.top,left,right,Shader.TileMode.CLAMP);
        if(art==null)return;
        int w=art.getWidth(),h=art.getHeight();
        float scale=Math.max((float)b.width()/w,(float)b.height()/h);
        int dx=(w-Math.round(b.width()/scale))/2,dy=(h-Math.round(b.height()/scale))/2;
        source.set(dx,dy,w-dx,h-dy);
    }
    @Override public void draw(Canvas c){
        Rect b=getBounds();paint.setColor(base);c.drawRect(b,paint);
        if(art!=null && !source.isEmpty()){
            paint.setColor(0xffffffff);c.drawBitmap(art,source,b,paint);
        }
        if(wash!=null){
            paint.setColor(0xffffffff);paint.setShader(wash);c.drawRect(b,paint);paint.setShader(null);
        }
    }
    @Override public void setAlpha(int alpha){}
    @Override public void setColorFilter(ColorFilter filter){}
    @Override public int getOpacity(){return PixelFormat.OPAQUE;}
}
