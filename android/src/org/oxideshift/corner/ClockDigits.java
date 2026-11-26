package org.oxideshift.corner;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ReplacementSpan;

/** Preserve the outlines of imported proportional digits in a tabular clock. */
final class ClockDigits extends ReplacementSpan {
    private static final String DIGITS="0123456789";
    static CharSequence format(String time,Paint paint){
        int width=0;
        for(int i=0;i<DIGITS.length();i++)width=Math.max(width,(int)Math.ceil(paint.measureText(DIGITS,i,i+1)));
        SpannableString display=new SpannableString(time);
        for(int i=0;i<time.length();i++)if(Character.isDigit(time.charAt(i)))
            display.setSpan(new ClockDigits(width),i,i+1,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return display;
    }
    private final int width;
    private ClockDigits(int width){this.width=width;}
    @Override public int getSize(Paint paint,CharSequence text,int start,int end,Paint.FontMetricsInt metrics){
        if(metrics!=null)paint.getFontMetricsInt(metrics);
        return width;
    }
    @Override public void draw(Canvas canvas,CharSequence text,int start,int end,float x,int top,int y,int bottom,Paint paint){
        float inset=(width-paint.measureText(text,start,end))*.5f;
        canvas.drawText(text,start,end,x+inset,y,paint);
    }
}
