package org.oxideshift.corner;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/**
 * Round tacks either side of the lower timekeeper line: ten per corner, blue on the left, red on the right.
 * A tack lights in its corner's colour for each filed round the judges gave that fighter, filling outward from the
 * clock. Static ink drawn in code (no motion, no assets), confined to the clock row and clear of the centered
 * TIME | R# line.
 */
final class ClockFlank extends View {
    static final int TACKS=10;
    private final View center;
    private final Paint dim=new Paint(Paint.ANTI_ALIAS_FLAG),lit=new Paint(Paint.ANTI_ALIAS_FLAG),glow=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float dp;
    private int blueWon,redWon;
    ClockFlank(Context context,View center){
        super(context);this.center=center;
        dp=context.getResources().getDisplayMetrics().density;
        for(Paint p:new Paint[]{dim,lit,glow})p.setStrokeCap(Paint.Cap.ROUND);
        dim.setColor(GameInk.ORANGE);dim.setAlpha(120);dim.setStrokeWidth(3.2f*dp);
        lit.setStrokeWidth(4.2f*dp);
        glow.setStrokeWidth(10f*dp);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    /** Rounds won by each corner (judges' majority); redraws only when a count changes. */
    void setWins(int blue,int red){
        blue=Math.max(0,Math.min(TACKS,blue));red=Math.max(0,Math.min(TACKS,red));
        if(blue==blueWon && red==redWon)return;
        blueWon=blue;redWon=red;
        setContentDescription(blue+" rounds to blue, "+red+" rounds to red");
        invalidate();
    }
    /** Never size the clock row: take zero height until the parent hands down its exact height. */
    @Override protected void onMeasure(int widthSpec,int heightSpec){
        int height=MeasureSpec.getMode(heightSpec)==MeasureSpec.EXACTLY?MeasureSpec.getSize(heightSpec):0;
        setMeasuredDimension(MeasureSpec.getSize(widthSpec),height);
    }
    @Override protected void onDraw(Canvas c){
        int w=getWidth(),h=getHeight();
        if(w==0 || h==0 || center.getWidth()==0)return;
        float half=Math.min(h*.40f,15*dp),mid=h*.5f,gap=20*dp,edge=16*dp;
        tacks(c,center.getLeft()-gap,edge,-1,mid,half,blueWon,GameInk.BLUE_CORNER);
        tacks(c,center.getRight()+gap,w-edge,1,mid,half,redWon,GameInk.RED_CORNER);
    }
    /** Ten evenly spaced tacks from the inner edge toward the outer limit; the first `won` are lit. */
    private void tacks(Canvas c,float inner,float outer,int dir,float mid,float half,int won,int color){
        float span=Math.abs(outer-inner);
        if(span<TACKS*6*dp)return;
        float step=Math.min(span/(TACKS-1),22*dp);
        lit.setColor(color);glow.setColor(color);glow.setAlpha(60);
        for(int i=0;i<TACKS;i++){
            float x=inner+dir*i*step;
            float slant=3*dp*dir;   // a slight lean, like strokes on tape
            if(i<won){
                c.drawLine(x-slant,mid+half,x+slant,mid-half,glow);
                c.drawLine(x-slant,mid+half,x+slant,mid-half,lit);
            }else c.drawLine(x-slant,mid+half*.8f,x+slant,mid-half*.8f,dim);
        }
    }
}
