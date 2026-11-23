package org.oxideshift.corner;

import android.graphics.*;
import android.graphics.drawable.Drawable;
import java.util.Random;

/** A printed strip with the disc's badge and TRAX tab in warm, two-tone ink. */
final class TraxBanner extends Drawable {
    private final Bitmap[] sprites;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final Path shape=new Path(),wear=new Path();
    private final Rect source=new Rect();
    private final RectF target=new RectF();
    private final ColorFilter tabInk=new ColorMatrixColorFilter(new float[]{
        0,0,.68f,0,64, 0,0,.50f,0,45, 0,0,.27f,0,25, 0,0,0,1,0
    });
    private final float maxBadge;
    private Shader field;
    private float badge,top,bottom;
    private int opacity=255;
    private ColorFilter tint;

    TraxBanner(Bitmap[] sprites,float maxBadge){this.sprites=sprites;this.maxBadge=maxBadge;}
    @Override protected void onBoundsChange(Rect bounds){
        float w=bounds.width(),h=bounds.height();
        if(w<=0 || h<=0)return;
        badge=Math.min(maxBadge,Math.min(w*.19f,h*.82f));
        top=h*.085f;bottom=h*.93f;
        float left=sprites==null?0:badge*.56f;
        shape.reset();shape.moveTo(left,top);shape.lineTo(w,top);shape.lineTo(w-h*.27f,bottom);
        shape.lineTo(left,bottom);shape.close();
        field=new LinearGradient(left,0,w,0,new int[]{0xff552719,0xff281914,0xff1c1511},
                                 new float[]{0,.35f,1},Shader.TileMode.CLAMP);
        wear.reset();
        Random grain=new Random(1967);
        for(int i=0;i<135;i++){
            float x=left+grain.nextFloat()*(w-left),y=i%3==0?top:bottom;
            float d=grain.nextFloat()*h*.07f;
            wear.addCircle(x,y==top?y+d:y-d,h*(.004f+grain.nextFloat()*.008f),Path.Direction.CW);
        }
    }
    private void sprite(Canvas c,int id,int width,int height,float x,float y,float w,float h){
        source.set(0,0,width,height);target.set(x,y,x+w,y+h);
        c.drawBitmap(sprites[id],source,target,paint);
    }
    @Override public void draw(Canvas canvas){
        Rect b=getBounds();float w=b.width(),h=b.height();
        if(w<=0 || h<=0)return;
        int base=canvas.save();canvas.translate(b.left,b.top);
        if(opacity!=255)canvas.saveLayerAlpha(0,0,w,h,opacity);
        int strip=canvas.save();canvas.clipPath(shape);
        paint.setColorFilter(tint);paint.setColor(0xffffffff);paint.setShader(field);
        canvas.drawPath(shape,paint);paint.setShader(null);
        paint.setColor(0xffaa642a);canvas.drawRect(0,top,w,top+h*.019f,paint);
        paint.setColor(0xff75391e);canvas.drawRect(0,bottom-h*.04f,w,bottom,paint);
        paint.setColor(0x80885d38);canvas.drawPath(wear,paint);
        canvas.restoreToCount(strip);
        if(sprites!=null){
            paint.setColor(0xffffffff);paint.setColorFilter(tint);
            paint.setColorFilter(tint!=null?tint:tabInk);
            canvas.save();canvas.translate(badge*.27f,top+badge*.96f);canvas.rotate(-90);
            sprite(canvas,2,16,33,0,0,badge*.24f,badge*.51f);
            canvas.restore();paint.setColorFilter(tint);
            sprite(canvas,0,48,48,0,top,badge*.82f,badge*.82f);
            paint.setColorFilter(null);
        }
        canvas.restoreToCount(base);
    }
    @Override public void setAlpha(int alpha){opacity=alpha;invalidateSelf();}
    @Override public void setColorFilter(ColorFilter filter){tint=filter;invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
