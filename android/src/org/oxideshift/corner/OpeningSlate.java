package org.oxideshift.corner;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.View;

/** A lower-display title card for the opening EA Sports movie. */
final class OpeningSlate extends View {
    private final Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);
    private Typeface face=DiscFonts.FALLBACK.stencil;
    OpeningSlate(Context ctx){
        super(ctx);
        setContentDescription("Fight Night Round 3");
        setBackground(new CornerInk(0xff201613,0xff783929));
    }
    void stencil(Typeface next){face=next;invalidate();}
    private int movie;
    void movie(int value){
        if(movie==value)return;
        movie=value;
        setContentDescription(value==0?"Fight Night Round 3":"Fight Night Round 3. "+caption());
        invalidate();
    }
    private String caption(){return movie==1?"EA SPORTS":"OPENING CINEMATIC";}
    @Override protected void onDraw(Canvas canvas){
        float w=getWidth(),h=getHeight(),unit=Math.min(w/580f,h/400f);
        canvas.save();canvas.translate(w/2,h/2);canvas.scale(unit,unit);
        ink.setStyle(Paint.Style.FILL);ink.setTextAlign(Paint.Align.CENTER);ink.setTypeface(face);
        ink.setColor(0xff873122);canvas.drawRect(-228,-64,228,29,ink);
        ink.setTextSize(80);
        float width=ink.measureText("FIGHT NIGHT");
        if(width>424)ink.setTextSize(80*424/width);
        ink.setColor(0xff381812);canvas.drawText("FIGHT NIGHT",2,12,ink);
        ink.setColor(GameInk.TEXT);canvas.drawText("FIGHT NIGHT",0,9,ink);
        ink.setTextSize(37);ink.setColor(GameInk.GOLD);canvas.drawText("ROUND 3",0,78,ink);
        float edge=ink.measureText("ROUND 3")/2+14;
        canvas.drawRect(-182,63,-edge,66,ink);canvas.drawRect(edge,63,182,66,ink);
        if(movie!=0){
            ink.setTextSize(21);ink.setColor(GameInk.TEXT);canvas.drawText(caption(),0,128,ink);
        }
        canvas.restore();
    }
}
