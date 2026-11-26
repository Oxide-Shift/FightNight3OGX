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
        setBackground(new CornerInk(GameInk.DARK,0xff513127));
        art=new Artwork(fonts.stencil,fonts.body);
        sweep=new Sweep(ctx,stick,ready);
        addView(sweep,new LayoutParams(-1,0,1));
        LinearLayout tools=new LinearLayout(ctx);
        tools.setBaselineAligned(false);tools.setGravity(Gravity.CENTER_VERTICAL);
        tools.setBackgroundColor(GameInk.DARK);
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
        private final CutmanGesture gesture;

        Sweep(Context ctx,CutmanStroke.Stick stick,BooleanSupplier ready){
            super(ctx);
            gesture=new CutmanGesture((down,x,y)->{
                stick.set(down,x,y);
                art.contact=down;art.stickX=x/32767f;art.stickY=y/32767f;
                postInvalidateOnAnimation();
            },ready);
            setContentDescription("Manual treatment pad. Touch anywhere and sweep your thumb like the right stick. Lift to recenter.");
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        }
        @Override protected void onDraw(Canvas c){art.draw(c,getWidth(),getHeight());}
        void release(){gesture.release();}
        @Override protected void onDetachedFromWindow(){release();super.onDetachedFromWindow();}
        @Override public boolean onTouchEvent(MotionEvent event){return gesture.onTouch(this,event);}
    }

    static final class Artwork {
        final Typeface stencil,body;
        boolean contact;
        float stickX,stickY;
        final Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);
        final RectF box=new RectF();
        int seconds=-1;
        String time="";
        Artwork(Typeface stencil,Typeface body){this.stencil=stencil;this.body=body;}
        private void text(Canvas c,String value,float x,float y,float size,Typeface face,int color,Paint.Align align,float maxWidth){
            ink.setTypeface(face);ink.setStyle(Paint.Style.FILL);ink.setTextAlign(align);
            ink.setColor(color);ink.setTextSize(size);
            float width=ink.measureText(value);
            if(width>maxWidth)ink.setTextSize(size*maxWidth/width);
            c.drawText(value,x,y,ink);
        }
        void draw(Canvas c,int w,int h){
            if(w<=0||h<=0)return;
            float u=Math.min(w/480f,h/240f),margin=18*u,cx=w*.5f,cy=h*.48f;
            float radius=Math.min(w*.28f,h*.25f);
            ink.setStyle(Paint.Style.FILL);
            ink.setColor(GameInk.PAPER);c.drawRect(0,0,w,h*.19f,ink);
            ink.setColor(GameInk.ORANGE);c.drawRect(0,0,margin*.4f,h*.19f,ink);
            text(c,"CUTMAN",margin,h*.144f,32*u,stencil,GameInk.DARK,Paint.Align.LEFT,w*.58f);
            text(c,time,w-margin,h*.144f,26*u,stencil,GameInk.DARK,Paint.Align.RIGHT,w*.27f);
            ink.setStrokeCap(Paint.Cap.ROUND);ink.setStyle(Paint.Style.STROKE);
            box.set(cx-radius,cy-radius,cx+radius,cy+radius);
            ink.setStrokeWidth(2*u);ink.setColor(0xff8b796c);c.drawArc(box,0,360,false,ink);
            for(int i=0;i<24;i++){
                double angle=i*Math.PI/12;float dx=(float)Math.cos(angle),dy=(float)Math.sin(angle);
                boolean quarter=i%6==0;
                ink.setColor(quarter?GameInk.GOLD:0xff8b796c);
                ink.setStrokeWidth((quarter?2:1)*u);
                float end=radius+(quarter?8:4)*u;
                c.drawLine(cx+dx*radius,cy+dy*radius,cx+dx*end,cy+dy*end,ink);
            }
            float x=cx+stickX*radius,y=cy-stickY*radius;
            ink.setColor(contact?GameInk.GOLD:GameInk.MUTED);
            if(contact){
                ink.setStrokeWidth(3*u);c.drawLine(cx,cy,x,y,ink);
                ink.setColor(GameInk.TEXT);c.drawCircle(x,y,8*u,ink);
            }
            ink.setStyle(Paint.Style.FILL);
            c.drawCircle(x,y,(contact?4:3)*u,ink);
            text(c,"SWEEP TO TREAT",cx,h*.86f,24*u,stencil,GameInk.TEXT,Paint.Align.CENTER,w*.90f);
            text(c,"Start anywhere. Lift to recenter.",cx,h*.95f,14*u,body,GameInk.MUTED,Paint.Align.CENTER,w*.92f);
        }
    }
}
