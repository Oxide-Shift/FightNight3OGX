package org.oxideshift.corner;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Controller pictograms drawn at the density of the display hosting the footer. */
final class PadIcon extends View {
    enum Cap {
        A(0xffbfd674,0xff36531a), B(0xffee7960,0xff7a2018), X(0xff83badf,0xff184f73),
        Y(0xfff7d56c,0xff946b16), LEFT(0xffaa9781,0xff44382c), RIGHT(0xffaa9781,0xff44382c),
        LB(0xffaeaaa0,0xff534e48), RB(0xffaeaaa0,0xff534e48), START(0xffb9b4aa,0xff534e48);
        final int light, dark;
        Cap(int light,int dark){this.light=light;this.dark=dark;}
    }
    private final Cap cap;
    private final boolean shoulder;
    private final Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path shape=new Path();
    private final LinearGradient finish;

    PadIcon(Context context,Cap cap,Typeface face){
        super(context);
        this.cap=cap;
        shoulder=cap==Cap.LB || cap==Cap.RB;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        ink.setTypeface(face);
        ink.setTextAlign(Paint.Align.CENTER);
        finish=new LinearGradient(0,6,0,30,cap.light,cap.dark,Shader.TileMode.CLAMP);
        if(shoulder){
            shape.moveTo(4,24);shape.quadTo(5,10,13,8);shape.lineTo(31,7);
            shape.quadTo(34,7,34,11);shape.lineTo(34,24);
            shape.quadTo(34,28,30,28);shape.lineTo(7,28);shape.quadTo(3,28,4,24);
            shape.close();
        }else shape.addCircle(19,18,14,Path.Direction.CW);
    }
    @Override protected void onDraw(Canvas canvas){
        float scale=Math.min(getWidth()/38f,getHeight()/36f);
        canvas.save();
        canvas.translate((getWidth()-38*scale)/2,(getHeight()-36*scale)/2);
        canvas.scale(scale,scale);
        canvas.save();
        if(cap==Cap.RB)canvas.scale(-1,1,19,18);
        ink.setColor(0xff170d09);ink.setStyle(Paint.Style.FILL_AND_STROKE);ink.setStrokeWidth(3.5f);
        canvas.drawPath(shape,ink);
        ink.setStyle(Paint.Style.FILL);ink.setColor(0xffffffff);ink.setShader(finish);
        canvas.drawPath(shape,ink);ink.setShader(null);
        ink.setStyle(Paint.Style.STROKE);ink.setStrokeWidth(.9f);ink.setColor(0xffdbbc90);
        canvas.drawPath(shape,ink);ink.setStyle(Paint.Style.FILL);canvas.restore();
        if(cap==Cap.LEFT || cap==Cap.RIGHT){
            canvas.save();
            if(cap==Cap.RIGHT)canvas.scale(-1,1,19,18);
            ink.setColor(0xffffedcf);ink.setStrokeWidth(2.4f);ink.setStrokeCap(Paint.Cap.ROUND);
            canvas.drawLine(12,18,25,18,ink);canvas.drawLine(12,18,17,13,ink);
            canvas.drawLine(12,18,17,23,ink);canvas.restore();canvas.restore();return;
        }
        if(cap==Cap.START){
            ink.setColor(0xffffedcf);Path play=new Path();
            play.moveTo(16,11);play.lineTo(25,18);play.lineTo(16,25);play.close();canvas.drawPath(play,ink);
            canvas.restore();return;
        }
        ink.setTextSize(shoulder?14:21);
        float baseline=18.5f-(ink.ascent()+ink.descent())/2;
        ink.setColor(0xff231911);canvas.drawText(cap.name(),19.5f,baseline+1,ink);
        ink.setColor(0xffffedcf);canvas.drawText(cap.name(),19,baseline,ink);
        canvas.restore();
    }
}
