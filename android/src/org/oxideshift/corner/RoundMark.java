package org.oxideshift.corner;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;

/** A large stencil round number set low in the empty lower field between rounds, like a printed fight bill. */
final class RoundMark extends Drawable {
    private final Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final String text;
    RoundMark(int round,Typeface face){
        text="R"+round;
        ink.setTypeface(face);ink.setColor(GameInk.ORANGE);ink.setAlpha(46);ink.setTextAlign(Paint.Align.RIGHT);
    }
    @Override public void draw(Canvas c){
        Rect b=getBounds();
        if(b.isEmpty())return;
        ink.setTextSize(b.height()*.46f);
        float width=ink.measureText(text);
        if(width>b.width()*.62f)ink.setTextSize(ink.getTextSize()*b.width()*.62f/width);
        c.drawText(text,b.right-b.width()*.035f,b.bottom-b.height()*.06f,ink);
    }
    @Override public void setAlpha(int alpha){ink.setAlpha(Math.min(alpha,46));}
    @Override public void setColorFilter(ColorFilter filter){ink.setColorFilter(filter);}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
