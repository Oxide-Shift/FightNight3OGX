package org.oxideshift.corner;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.View;

/** Career options (career directive p. 22): an index of the seven option pages with the active one marked, a large
 *  drawn icon for it, and what the category affects. Only the game's route is shown, never a setting value. */
final class OptionsSheet extends View {
    static final String[] LABELS={"Overview","Gameplay","Corner audio","Controller","EA Trax","Profile","Saved settings"};
    private final Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint words=new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Path icon=new Path();
    private final RectF box=new RectF();
    private final Typeface stencil,body;
    private final float dp,sp;
    private final ValueAnimator move=ValueAnimator.ofFloat(0f,1f);
    private float fraction=1f,wedgeFrom=-1,wedge=-1;
    private CareerDesk.Page page;

    OptionsSheet(Context context,DiscFonts fonts){
        super(context);stencil=fonts.stencil;body=fonts.body;
        dp=getResources().getDisplayMetrics().density;sp=getResources().getDisplayMetrics().scaledDensity;
        move.setDuration(360);
        move.addUpdateListener(a->{fraction=(float)a.getAnimatedValue();invalidate();});
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        setVisibility(GONE);
    }
    static boolean handles(CareerDesk.Page p){return p!=null && p.is("options");}
    boolean show(CareerDesk.Page next){
        if(!handles(next)){setVisibility(GONE);page=null;return false;}
        boolean entering=getVisibility()!=VISIBLE;
        setVisibility(VISIBLE);
        if(next!=page){
            wedgeFrom=page==null?next.index-1:wedge;wedge=next.index-1;page=next;
            setContentDescription(next.title+". "+next.note+" "+next.detail);
            if(SheetMotion.on() && isAttachedToWindow()){
                move.cancel();move.setInterpolator(new android.view.animation.OvershootInterpolator(1.1f));fraction=0;move.start();
            }else fraction=1f;
            invalidate();
        }
        if(entering)SheetMotion.appear(this);
        return true;
    }
    @Override protected void onDetachedFromWindow(){move.cancel();fraction=1f;super.onDetachedFromWindow();}

    private void text(Canvas c,String s,float x,float y,Typeface face,float size,int color,Paint.Align align){
        ink.setStyle(Paint.Style.FILL);ink.setTypeface(face);ink.setTextSize(size*sp);ink.setColor(color);ink.setTextAlign(align);
        c.drawText(s,x,y,ink);
    }
    private float paragraph(Canvas c,String s,float x,float y,float width,Typeface face,float size,int color){
        words.setTypeface(face);words.setTextSize(size*sp);words.setColor(color);
        StaticLayout layout=StaticLayout.Builder.obtain(s,0,s.length(),words,Math.max(1,(int)width))
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(2*dp,1f).build();
        c.save();c.translate(x,y);layout.draw(c);c.restore();
        return layout.getHeight();
    }
    @Override protected void onDraw(Canvas c){
        if(page==null)return;
        float w=getWidth(),h=getHeight();
        ink.setStyle(Paint.Style.FILL);
        ink.setShader(new LinearGradient(0,0,w,h,0xff2b130d,0xff140906,Shader.TileMode.CLAMP));c.drawRect(0,0,w,h,ink);ink.setShader(null);
        ink.setColor(GameInk.ORANGE);c.drawRect(0,0,w,2*dp,ink);
        ink.setColor(0xff681e14);c.drawRect(0,0,8*dp,h,ink);
        ink.setColor(0xff130c09);for(float y=6*dp;y<h-6*dp;y+=14*dp)c.drawRoundRect(2*dp,y,6*dp,y+7*dp,dp,dp,ink);
        boolean narrow=w<460*dp;
        // Watermark: the active category's icon, huge and faint, behind the index.
        float wm=Math.min(h*.30f,w*.26f);
        drawIconFaint(c,page.index,w-wm*1.05f,h-wm*1.05f,wm);
        // Feature: icon medallion, category title and its consequence, across the top.
        float featureW=w-28*dp,fx=16*dp,fy=12*dp;
        float r=Math.min(narrow?40*dp:52*dp,w*.12f),icx=fx+r+4*dp,icy=fy+r+6*dp;
        float pop=.55f+.45f*Math.min(1.2f,fraction);
        ink.setColor(0xff3a1a10);c.drawCircle(icx,icy,r,ink);
        ink.setStyle(Paint.Style.STROKE);ink.setStrokeWidth(3*dp);ink.setColor(GameInk.ORANGE);c.drawCircle(icx,icy,r,ink);
        ink.setStrokeWidth(dp);ink.setColor(0x66ffd27a);c.drawCircle(icx,icy,r-6*dp,ink);
        c.save();c.scale(pop,pop,icx,icy);drawIcon(c,page.index,icx,icy,r*.62f);c.restore();
        float tx=icx+r+14*dp,tw=w-12*dp-tx;
        text(c,"CAREER OPTIONS  ·  "+String.format(java.util.Locale.ROOT,"%02d / 07",page.index),tx,fy+16*sp,stencil,12,GameInk.GOLD,Paint.Align.LEFT);
        ink.setTypeface(stencil);ink.setTextSize(28*sp);
        float span=ink.measureText(page.title.toUpperCase(java.util.Locale.ROOT)),size=span>tw && span>0?28*tw/span:28;
        ink.setShadowLayer(2*dp,0,dp,0xff0a0504);
        text(c,page.title.toUpperCase(java.util.Locale.ROOT),tx,fy+16*sp+size*sp*1.05f,stencil,size,GameInk.TEXT,Paint.Align.LEFT);
        ink.clearShadowLayer();
        float py=Math.max(icy+r+12*dp,fy+16*sp+size*sp*1.05f+12*dp);
        // Wide: the description sits beside the medallion; narrow: below it, full width.
        float textX=narrow?fx:tx,textW=narrow?featureW:tw;
        if(!narrow)py=fy+16*sp+size*sp*1.05f+10*dp;
        py+=paragraph(c,page.note,textX,py,textW,body,16,GameInk.TEXT)+8*dp;
        text(c,"WHAT IT AFFECTS",textX,py+12*sp,stencil,11,GameInk.GOLD,Paint.Align.LEFT);
        py+=18*sp;
        py+=paragraph(c,page.detail,textX,py,textW,body,15,GameInk.MUTED)+12*dp;
        py=Math.max(py,icy+r+14*dp);
        // Index: all seven option pages across the full width, a gold wedge sliding to the active one.
        float lx=fx+4*dp,lw=w-12*dp-lx;
        float ly=py,row=Math.max(28*dp,Math.min(46*dp,(h-ly-10*dp)/7f));
        ink.setStyle(Paint.Style.FILL);ink.setColor(0x33000000);c.drawRect(lx-6*dp,ly-4*dp,lx+lw,ly+row*7+4*dp,ink);
        ink.setColor(GameInk.ORANGE);c.drawRect(lx-6*dp,ly-4*dp,lx+lw,ly-3*dp,ink);
        float slide=wedgeFrom+(wedge-wedgeFrom)*Math.min(1f,fraction);
        float wy=ly+row*slide;
        ink.setColor(0xff4a1d12);c.drawRect(lx-6*dp,wy,lx+lw,wy+row,ink);
        ink.setColor(GameInk.GOLD);
        icon.reset();icon.moveTo(lx-6*dp,wy);icon.lineTo(lx+2*dp,wy+row/2);icon.lineTo(lx-6*dp,wy+row);icon.close();c.drawPath(icon,ink);
        float labelSize=Math.min(15f,row/sp*.44f),noteX=lx+34*dp+(narrow?0:150*dp);
        for(int k=0;k<7;k++){
            boolean here=k==wedge;
            float y=ly+row*k+row*.64f;
            text(c,String.format(java.util.Locale.ROOT,"%02d",k+1),lx+8*dp,y,stencil,12,here?GameInk.GOLD:0x99a58a6c,Paint.Align.LEFT);
            text(c,LABELS[k].toUpperCase(java.util.Locale.ROOT),lx+34*dp,y,stencil,labelSize,here?GameInk.TEXT:GameInk.MUTED,Paint.Align.LEFT);
            if(!narrow){
                // Each category's own one-line purpose from the route list.
                CareerDesk.Page p=CareerDesk.find("options",k+1);
                String s=p==null?"":p.note;
                ink.setTypeface(body);ink.setTextSize(13*sp);
                float room=lx+lw-8*dp-noteX;
                while(s.length()>4 && ink.measureText(s)>room)s=s.substring(0,s.length()-2).trim()+"…";
                if(room>60*dp)text(c,s,noteX,y,body,13,here?0xffe8d9bd:0x99a58a6c,Paint.Align.LEFT);
            }
        }
    }
    /** Space the sheet needs: feature block plus seven index rows (it scrolls on short screens). */
    @Override protected void onMeasure(int widthSpec,int heightSpec){
        int width=MeasureSpec.getSize(widthSpec);
        boolean narrow=width<460*dp;
        float need=(narrow?150*dp+110*sp:40*dp+120*sp)+7*Math.max(28*dp,22*sp)+24*dp;
        int spec=MeasureSpec.getMode(heightSpec)==MeasureSpec.UNSPECIFIED?0:MeasureSpec.getSize(heightSpec);
        setMeasuredDimension(width,Math.max(spec,Math.round(need)));
    }
    private void drawIconFaint(Canvas c,int index,float cx,float cy,float s){
        float stroke=ink.getStrokeWidth();
        ink.setStyle(Paint.Style.STROKE);ink.setStrokeWidth(Math.max(4*dp,s*.08f));
        int saved=ink.getColor();
        faint=true;drawIcon(c,index,cx,cy,s*.8f);faint=false;
        ink.setStrokeWidth(stroke);ink.setColor(saved);
    }
    private boolean faint;
    /** A line icon per option page, drawn in gold. */
    private void drawIcon(Canvas c,int index,float cx,float cy,float s){
        ink.setStyle(Paint.Style.STROKE);ink.setStrokeWidth(Math.max(2.5f*dp,s*.11f));ink.setStrokeCap(Paint.Cap.ROUND);
        ink.setStrokeJoin(Paint.Join.ROUND);ink.setColor(faint?0x14ffd27a:0xffffd27a);
        switch(index){
            case 1: // sliders
                for(int k=0;k<3;k++){float y=cy-s*.55f+s*.55f*k;c.drawLine(cx-s*.8f,y,cx+s*.8f,y,ink);
                    ink.setStyle(Paint.Style.FILL);c.drawCircle(cx+s*(k==1?.35f:-.35f+k*.2f),y,s*.17f,ink);ink.setStyle(Paint.Style.STROKE);}
                break;
            case 2: // stopwatch
                c.drawCircle(cx,cy+s*.08f,s*.72f,ink);c.drawLine(cx,cy+s*.08f,cx+s*.32f,cy-s*.28f,ink);
                c.drawLine(cx-s*.18f,cy-s*.85f,cx+s*.18f,cy-s*.85f,ink);c.drawLine(cx,cy-s*.85f,cx,cy-s*.64f,ink);break;
            case 3: // speaker with waves
                icon.reset();icon.moveTo(cx-s*.8f,cy-s*.25f);icon.lineTo(cx-s*.45f,cy-s*.25f);icon.lineTo(cx-s*.05f,cy-s*.65f);
                icon.lineTo(cx-s*.05f,cy+s*.65f);icon.lineTo(cx-s*.45f,cy+s*.25f);icon.lineTo(cx-s*.8f,cy+s*.25f);icon.close();c.drawPath(icon,ink);
                box.set(cx-s*.25f,cy-s*.45f,cx+s*.45f,cy+s*.45f);c.drawArc(box,-50,100,false,ink);
                box.set(cx-s*.45f,cy-s*.8f,cx+s*.85f,cy+s*.8f);c.drawArc(box,-50,100,false,ink);break;
            case 4: // gamepad
                box.set(cx-s*.95f,cy-s*.45f,cx+s*.95f,cy+s*.5f);c.drawRoundRect(box,s*.4f,s*.4f,ink);
                c.drawLine(cx-s*.62f,cy,cx-s*.22f,cy,ink);c.drawLine(cx-s*.42f,cy-s*.2f,cx-s*.42f,cy+s*.2f,ink);
                ink.setStyle(Paint.Style.FILL);c.drawCircle(cx+s*.38f,cy-s*.1f,s*.1f,ink);c.drawCircle(cx+s*.6f,cy+s*.12f,s*.1f,ink);break;
            case 5: // vinyl record
                c.drawCircle(cx,cy,s*.85f,ink);ink.setStrokeWidth(dp);c.drawCircle(cx,cy,s*.6f,ink);c.drawCircle(cx,cy,s*.45f,ink);
                ink.setStyle(Paint.Style.FILL);c.drawCircle(cx,cy,s*.2f,ink);break;
            case 6: // profile
                c.drawCircle(cx,cy-s*.3f,s*.32f,ink);box.set(cx-s*.7f,cy+s*.1f,cx+s*.7f,cy+s*1.3f);c.drawArc(box,180,180,false,ink);break;
            default: // memory card
                icon.reset();icon.moveTo(cx-s*.6f,cy-s*.8f);icon.lineTo(cx+s*.35f,cy-s*.8f);icon.lineTo(cx+s*.6f,cy-s*.55f);
                icon.lineTo(cx+s*.6f,cy+s*.8f);icon.lineTo(cx-s*.6f,cy+s*.8f);icon.close();c.drawPath(icon,ink);
                box.set(cx-s*.35f,cy-s*.1f,cx+s*.35f,cy+s*.5f);c.drawRect(box,ink);
                c.drawLine(cx-s*.3f,cy-s*.55f,cx+s*.15f,cy-s*.55f,ink);
        }
        ink.setStyle(Paint.Style.FILL);
    }
}
