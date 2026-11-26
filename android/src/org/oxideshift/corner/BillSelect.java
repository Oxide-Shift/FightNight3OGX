package org.oxideshift.corner;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

/** Opaque ticket with an ink wedge so controller focus survives busy retail artwork. */
final class BillSelect extends Drawable {
    private final Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path notch=new Path();
    private final boolean primary;
    private final float density;
    private boolean selected;

    BillSelect(float density,boolean primary){this.density=density;this.primary=primary;}
    @Override public boolean isStateful(){return true;}
    @Override protected boolean onStateChange(int[] states){
        boolean active=false;
        for(int state:states){
            if(state==android.R.attr.state_focused || state==android.R.attr.state_selected ||
                    state==android.R.attr.state_pressed){active=true;break;}
        }
        if(active==selected)return false;
        selected=active;invalidateSelf();return true;
    }
    @Override protected void onBoundsChange(Rect b){
        float w=Math.min(b.width()*.06f,10*density);
        notch.reset();notch.moveTo(b.left,b.top);notch.lineTo(b.left+w,b.top);
        notch.lineTo(b.left+w*.42f,b.bottom);notch.lineTo(b.left,b.bottom);notch.close();
    }
    @Override public void draw(Canvas c){
        Rect b=getBounds();
        ink.setColor(selected?0xff602c20:primary?0xff813525:0xff282421);c.drawRect(b,ink);
        ink.setColor(selected?GameInk.GOLD:primary?GameInk.RED_CORNER:0xff66564a);
        c.drawPath(notch,ink);
        if(selected)c.drawRect(b.left,b.bottom-density,b.right,b.bottom,ink);
    }
    @Override public void setAlpha(int alpha){}
    @Override public void setColorFilter(ColorFilter filter){}
    @Override public int getOpacity(){return PixelFormat.OPAQUE;}
}
