package org.oxideshift.corner;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.util.function.BooleanSupplier;

/** Manual recovery. The upper display supplies the boxer and the live tool readout. */
final class CutmanDesk extends LinearLayout {
    private final Sweep sweep;
    private final Artwork art;

    CutmanDesk(Context ctx,DiscFonts fonts,CutmanStroke.Stick stick,BooleanSupplier ready,CornerControls.Output keys){
        super(ctx);
        setOrientation(VERTICAL);
        setBackground(new CornerInk(0xff211713,0xff7d3927));
        art=new Artwork(fonts.stencil);
        sweep=new Sweep(ctx,stick,ready);
        addView(sweep,new LayoutParams(-1,0,1));
        LinearLayout tools=new LinearLayout(ctx);
        tools.setBaselineAligned(false);tools.setGravity(Gravity.CENTER_VERTICAL);
        tools.setBackgroundColor(0xff310e07);
        addView(tools,new LayoutParams(-1,-2));
        tool(tools,"Left side","Treat the left side",CornerControls.Key.LEFT_SIDE,fonts,ready,keys);
        tool(tools,"Switch tool","Switch between cuts and swelling",CornerControls.Key.Y,fonts,ready,keys);
        tool(tools,"Right side","Treat the right side",CornerControls.Key.RIGHT_SIDE,fonts,ready,keys);
    }

    private void tool(LinearLayout row,String name,String description,CornerControls.Key key,
                      DiscFonts fonts,BooleanSupplier ready,CornerControls.Output keys){
        PadIcon.Cap cap=key==CornerControls.Key.Y?PadIcon.Cap.Y:
                key==CornerControls.Key.LEFT_SIDE?PadIcon.Cap.LEFT:PadIcon.Cap.RIGHT;
        MenuPrompt button=new MenuPrompt(getContext(),cap,name,fonts.stencil,()->{
            if(ready.getAsBoolean())keys.press(key);
        });
        button.setContentDescription(description);
        row.addView(button,new LayoutParams(0,dp(58),1));
    }

    private int dp(float v){return Math.round(v*getResources().getDisplayMetrics().density);}
    void release(){sweep.release();}
    void time(int seconds){
        if(seconds!=art.seconds){
            art.seconds=seconds;art.time=seconds<0?"":FightBroadcast.Bout.time(seconds);
            sweep.invalidate();
        }
    }

    private final class Sweep extends View {
        private final CutmanStroke stroke;
        private final BooleanSupplier ready;
        Sweep(Context ctx,CutmanStroke.Stick stick,BooleanSupplier ready){
            super(ctx);this.stroke=new CutmanStroke(stick);this.ready=ready;
            setContentDescription("Manual treatment pad. Touch anywhere and sweep your thumb like the right stick. Lift to recenter.");
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        }
        @Override protected void onDraw(Canvas c){art.draw(c,getWidth(),getHeight());}
        void release(){
            stroke.release();
        }
        @Override protected void onDetachedFromWindow(){release();super.onDetachedFromWindow();}
        @Override public boolean onTouchEvent(MotionEvent e){
            int action=e.getActionMasked();
            if(action==MotionEvent.ACTION_DOWN){
                if(!ready.getAsBoolean())return false;
                stroke.begin(e.getPointerId(0),e.getX(),e.getY());return true;
            }
            if(stroke.finger<0)return false;
            int index=e.findPointerIndex(stroke.finger);
            if(action==MotionEvent.ACTION_CANCEL || action==MotionEvent.ACTION_UP || index<0 ||
                    (action==MotionEvent.ACTION_POINTER_UP && index==e.getActionIndex())){
                release();return true;
            }
            if(action==MotionEvent.ACTION_MOVE){
                // Preserve the original thumb travel regardless of the size of the printed guide.
                float travel=Math.min(getWidth()*.15f,getHeight()*.23f);
                stroke.move(stroke.finger,e.getX(index),e.getY(index),travel);
            }
            return true;
        }
    }

    static final class Artwork {
        final Typeface stencil;
        final Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);
        final RectF box=new RectF();
        int seconds=-1;
        String time="";
        Artwork(Typeface stencil){this.stencil=stencil;}
        private void text(Canvas c,String value,float x,float y,float size,Typeface face,int color,Paint.Align align,float maxWidth){
            ink.setTypeface(face);ink.setStyle(Paint.Style.FILL);ink.setTextAlign(align);
            ink.setColor(color);ink.setTextSize(size);
            float width=ink.measureText(value);
            if(width>maxWidth)ink.setTextSize(size*maxWidth/width);
            c.drawText(value,x,y,ink);
        }
        void draw(Canvas c,int w,int h){
            if(w<=0||h<=0)return;
            float u=Math.min(w/480f,h/240f),m=20*u,cx=w*.5f,cy=h*.5f,r=Math.min(w*.25f,h*.26f);
            ink.setStyle(Paint.Style.FILL);
            int save=c.save();c.rotate(-14,w*.82f,h*.43f);ink.setColor(0xff30211a);
            float a=Math.min(w,h)*.34f, ax=w*.84f,ay=h*.42f;
            c.drawRect(ax-a,ay-a*.32f,ax+a,ay+a*.32f,ink);c.drawRect(ax-a*.32f,ay-a,ax+a*.32f,ay+a,ink);c.restoreToCount(save);
            text(c,"CUTMAN",m,h*.17f,38*u,stencil,GameInk.GOLD,Paint.Align.LEFT,w*.58f);
            text(c,time,w-m,h*.17f,32*u,stencil,GameInk.TEXT,Paint.Align.RIGHT,w*.24f);
            ink.setStrokeCap(Paint.Cap.ROUND);ink.setStyle(Paint.Style.STROKE);ink.setStrokeWidth(Math.max(1,2*u));
            ink.setColor(0xff754534);box.set(cx-r,cy-r,cx+r,cy+r);
            c.drawArc(box,12,156,false,ink);ink.setStrokeWidth(5*u);ink.setColor(0xffc78046);
            c.drawArc(box,193,154,false,ink);
            for(int i=0;i<9;i++){
                double angle=Math.toRadians(195+i*18.75);float dx=(float)Math.cos(angle),dy=(float)Math.sin(angle);
                ink.setStrokeWidth(1.5f*u);ink.setColor(0xffa0734d);
                c.drawLine(cx+dx*(r+6*u),cy+dy*(r+6*u),cx+dx*(r+10*u),cy+dy*(r+10*u),ink);
            }
            ink.setStyle(Paint.Style.FILL);ink.setColor(0xffaa774c);
            c.drawCircle(cx,cy,3*u,ink);
            text(c,"SWEEP TO TREAT",cx,h*.83f,25*u,stencil,GameInk.TEXT,Paint.Align.CENTER,w*.85f);
            text(c,"Start anywhere. Lift to recenter.",cx,h*.94f,16*u,stencil,GameInk.MUTED,Paint.Align.CENTER,w*.9f);
        }
    }
}