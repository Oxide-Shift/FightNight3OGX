package org.oxideshift.corner;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;

/** Career Central's film-edge rail, red ink and warm lettering around live career records. */
final class CareerScouting extends View {
    static final int FIELD=0xff24130f,TYPE=GameInk.TEXT;
    private static final int FADED=GameInk.MUTED,GOLD=GameInk.GOLD,RED=0xffef9979,TRACK=0xff684237;
    private final Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path rail=new Path(),wear=new Path();
    private Shader field,band;
    private final float unit,sp;
    private FightBroadcast.Camp camp;
    private final Typeface stencil;

    CareerScouting(Context ctx,Typeface stencil){
        super(ctx);this.stencil=stencil;
        unit=getResources().getDisplayMetrics().density;
        sp=getResources().getDisplayMetrics().scaledDensity;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        setVisibility(GONE);
    }

    void show(FightBroadcast.Camp value){
        if(camp==value)return;
        camp=value;
        setContentDescription(value==null?null:value.notes());
        setVisibility(value==null?GONE:VISIBLE);
        requestLayout();invalidate();
    }

    private boolean stacked(int width){
        return camp!=null && camp.opponent!=null && width<240*sp;
    }
    @Override protected void onMeasure(int widthSpec,int heightSpec){
        int width=MeasureSpec.getSize(widthSpec);
        int lines=stacked(width)?213:129;
        if(camp!=null && !camp.odds.isEmpty())lines+=17;
        if(camp!=null && camp.boxer.wins==0 && (camp.opponent==null || camp.opponent.wins==0))lines-=16;
        int height=(int)Math.ceil(lines*sp+8*unit);
        setMeasuredDimension(resolveSize(width,widthSpec),resolveSize(height,heightSpec));
    }
    private void text(Canvas c,String value,float x,float baseline,float width,float size,int color){
        ink.setTypeface(stencil);ink.setColor(color);ink.setTextSize(size*sp);
        float advance=ink.measureText(value);
        if(advance>width && advance>0)ink.setTextSize(ink.getTextSize()*width/advance);
        c.drawText(value,x,baseline,ink);
    }
    private void rule(Canvas c,float left,float top,float right,float height,int color){
        ink.setColor(color);c.drawRect(left,top,right,top+height,ink);
    }
    private void side(Canvas c,FightBroadcast.Record record,boolean own,float x,float y,float w){
        int accent=own?GOLD:RED;
        rule(c,x,y+4*sp,x+3*unit,10*sp,accent);
        text(c,own?"YOUR CORNER":"OPPONENT",x+7*unit,y+14*sp,w-7*unit,14,accent);
        String wl=record.wins+"\u2013"+record.losses+"\u2013"+record.draws;
        ink.setShadowLayer(1.5f*unit,0,unit,0xff160906);
        text(c,wl,x,y+45*sp,w,34,TYPE);
        ink.clearShadowLayer();
        if(record.bouts()==0){text(c,"Career debut",x,y+64*sp,w,15,FADED);return;}
        text(c,record.knockouts+(record.knockouts==1?" knockout win":" knockout wins"),x,y+63*sp,w,15,TYPE);
        if(record.wins==0)return;
        float top=y+69*sp;
        rule(c,x,top,x+w,3*unit,TRACK);
        rule(c,x,top,x+w*record.knockouts/record.wins,3*unit,own?0xffe8ac32:0xffd85a3b);
        int pct=Math.round(100f*record.knockouts/record.wins);
        text(c,pct+"% of wins by KO",x,y+86*sp,w,13,FADED);
    }
    @Override protected void onDraw(Canvas c){
        if(camp==null)return;
        float w=getWidth(),h=getHeight(),pad=17*unit;
        ink.setColor(0xffffffff);ink.setShader(field);c.drawRect(0,0,w,h,ink);
        ink.setShader(band);c.drawRect(0,0,w,29*sp,ink);ink.setShader(null);
        ink.setColor(0xff681e14);c.drawPath(rail,ink);
        ink.setColor(0xff130c09);c.drawPath(wear,ink);
        text(c,camp.opponent==null?"FIGHT RECORD":"CONTRACT MATCHUP",pad,21*sp,w*.53f,19,TYPE);
        float labelX=w*.55f;
        text(c,camp.purse.isEmpty()?"W / L / D":camp.purse,labelX,21*sp,w-labelX-pad,15,TYPE);
        rule(c,0,29*sp,w,unit,0xff9e4a2b);
        float y=34*sp;
        if(camp.opponent==null){side(c,camp.boxer,true,pad,y,w-2*pad);return;}
        if(stacked(getWidth())){
            side(c,camp.boxer,true,pad,y,w-2*pad);
            side(c,camp.opponent,false,pad,y+88*sp,w-2*pad);
        }else{
            float split=w*.5f;
            rule(c,split,y+4*sp,split+unit,79*sp,TRACK);
            side(c,camp.boxer,true,pad,y,split-2*pad);
            side(c,camp.opponent,false,split+pad,y,split-2*pad);
        }
        if(!camp.odds.isEmpty()){
            float bottom=h-8*unit;
            rule(c,pad,bottom-16*sp,w-pad,unit,TRACK);
            text(c,camp.odds,pad,bottom-2*sp,w-2*pad,13,FADED);
        }
    }
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){
        super.onSizeChanged(w,h,oldw,oldh);
        field=new LinearGradient(0,0,w,h,0xff421a12,0xff140e0c,Shader.TileMode.CLAMP);
        band=new LinearGradient(0,0,w,0,0xffa22b1b,0xff4a1710,Shader.TileMode.CLAMP);
        rail.reset();wear.reset();
        rail.addRect(0,0,8*unit,h,Path.Direction.CW);
        for(float y=4*unit;y<h;y+=14*unit)wear.addRoundRect(unit,y,6*unit,y+7*unit,unit,unit,Path.Direction.CW);
    }
}
