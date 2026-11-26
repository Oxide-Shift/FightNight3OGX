package org.oxideshift.corner;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.View;

/** The lower screen under the Achievements and Music menus.
 *  Spotlight: the award selected above, large, with its category and overall progress.
 *  Record: the track playing now on a spinning disc, with the music settings mirrored as chips.
 *  Everything drawn comes from the award list, the player's earned set, the music settings and the playing song. */
final class MenuStage extends View {
    private final Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final TextPaint words=new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box=new RectF();
    private final Typeface stencil,body;
    private final float dp,sp;
    private final ValueAnimator pop=ValueAnimator.ofFloat(0f,1f),spin=ValueAnimator.ofFloat(0f,360f);
    private float fraction=1f,angle;
    // Spotlight state
    private Achievements.Award award;
    private boolean earned;
    private String category="";
    private int have,total,allHave,allTotal,score;
    // Record state
    private boolean record;
    private FightBroadcast.Song song;
    private boolean fights,expanded;

    MenuStage(Context c,DiscFonts fonts){
        super(c);stencil=fonts.stencil;body=fonts.body;
        dp=getResources().getDisplayMetrics().density;sp=getResources().getDisplayMetrics().scaledDensity;
        pop.setDuration(520);pop.addUpdateListener(a->{fraction=(float)a.getAnimatedValue();invalidate();});
        // 33 1/3 rpm: one turn every 1.8 s.
        spin.setDuration(1800);spin.setRepeatCount(ValueAnimator.INFINITE);
        spin.setInterpolator(new android.view.animation.LinearInterpolator());
        spin.addUpdateListener(a->{angle=(float)a.getAnimatedValue();invalidate();});
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }
    void spotlight(Achievements.Award next,boolean isEarned,String categoryTitle,int categoryHave,int categoryTotal,int overallHave,int overallTotal,int fightScore){
        boolean changed=record || next!=award || isEarned!=earned;
        record=false;spin.cancel();
        award=next;earned=isEarned;category=categoryTitle;have=categoryHave;total=categoryTotal;allHave=overallHave;allTotal=overallTotal;score=fightScore;
        setContentDescription(next==null?categoryTitle:next.title+". "+(isEarned?"Earned. "+next.deed:"Locked. "+next.goal)+
                " "+categoryTitle+", "+categoryHave+" of "+categoryTotal+" earned.");
        if(changed)animatePop();
        invalidate();
    }
    void record(FightBroadcast.Song playing,boolean fightsOn,boolean expandedOn){record(playing,fightsOn,expandedOn,false);}
    void record(FightBroadcast.Song playing,boolean fightsOn,boolean expandedOn,boolean isPaused){
        boolean changed=!record || playing!=song && (playing==null || song==null || !playing.title.equals(song.title));
        record=true;song=playing;fights=fightsOn;expanded=expandedOn;paused=isPaused;
        setContentDescription(playing==null?"No track playing.":(isPaused?"Paused: ":"Now playing ")+playing.title+" by "+playing.artist+".");
        if(changed)animatePop();
        // A paused record stops where it is (the angle is kept) and picks up from there.
        if(playing!=null && !isPaused && SheetMotion.on() && isAttachedToWindow()){
            if(!spin.isStarted())spin.start();else if(spin.isPaused())spin.resume();
        }else if(isPaused && spin.isStarted())spin.pause();
        else spin.cancel();
        invalidate();
    }
    private boolean paused;
    private void animatePop(){
        if(SheetMotion.on() && isShown()){pop.cancel();fraction=0;pop.setInterpolator(new android.view.animation.OvershootInterpolator(1.4f));pop.start();}
        else fraction=1f;
    }
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();if(record && song!=null && SheetMotion.on())spin.start();}
    @Override protected void onDetachedFromWindow(){pop.cancel();spin.cancel();fraction=1f;super.onDetachedFromWindow();}

    /** Whole-number percentage, guarded against an empty set. */
    static int percent(int have,int total){return total<=0?0:Math.round(100f*have/total);}

    private void text(Canvas c,String s,float x,float y,Typeface face,float size,int color,Paint.Align align){
        ink.setStyle(Paint.Style.FILL);ink.setShader(null);ink.setTypeface(face);ink.setTextSize(size*sp);ink.setColor(color);ink.setTextAlign(align);
        c.drawText(s,x,y,ink);
    }
    private float paragraph(Canvas c,String s,float x,float y,float width,Typeface face,float size,int color,int maxLines){
        words.setTypeface(face);words.setTextSize(size*sp);words.setColor(color);
        StaticLayout layout=StaticLayout.Builder.obtain(s,0,s.length(),words,Math.max(1,(int)width))
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(2*dp,1f).setMaxLines(maxLines)
                .setEllipsize(android.text.TextUtils.TruncateAt.END).build();
        c.save();c.translate(x,y);layout.draw(c);c.restore();
        return layout.getHeight();
    }
    private float fit(String s,Typeface face,float size,float width){
        ink.setTypeface(face);ink.setTextSize(size*sp);float span=ink.measureText(s);
        return span>width && span>0?size*width/span:size;
    }
    @Override protected void onDraw(Canvas c){
        float w=getWidth(),h=getHeight();
        ink.setStyle(Paint.Style.FILL);
        ink.setShader(new RadialGradient(w*.5f,h*.38f,Math.max(w,h)*.8f,0xff3d1a10,0xff120806,Shader.TileMode.CLAMP));
        c.drawRect(0,0,w,h,ink);ink.setShader(null);
        ink.setColor(GameInk.ORANGE);c.drawRect(0,0,w,2*dp,ink);
        if(record)drawRecord(c,w,h);else drawSpotlight(c,w,h);
    }

    private void drawSpotlight(Canvas c,float w,float h){
        if(award==null){text(c,category.toUpperCase(java.util.Locale.ROOT),w/2,h/2,stencil,22,GameInk.TEXT,Paint.Align.CENTER);return;}
        float pad=18*dp;
        float r=Math.min(Math.min(w*.16f,h*.19f),78*dp),cx=pad+r+18*dp,cy=pad+r+26*sp;
        text(c,category.toUpperCase(java.util.Locale.ROOT)+"   ·   SELECTED AWARD",pad,pad+8*sp,stencil,12,GameInk.GOLD,Paint.Align.LEFT);
        float pop=Math.max(0f,fraction);
        if(earned){
            // Light rays slowly sweeping behind an earned medal, and a warm glow.
            c.save();c.rotate(pop*40f,cx,cy);
            ink.setColor(0x22ffd27a);
            for(int k=0;k<12;k++){
                Path ray=new Path();double a=Math.toRadians(k*30),b=Math.toRadians(k*30+9);
                ray.moveTo(cx,cy);ray.lineTo(cx+(float)Math.cos(a)*r*1.9f,cy+(float)Math.sin(a)*r*1.9f);
                ray.lineTo(cx+(float)Math.cos(b)*r*1.9f,cy+(float)Math.sin(b)*r*1.9f);ray.close();c.drawPath(ray,ink);
            }
            c.restore();
            // Warm glow in solid rings (renders identically everywhere).
            for(int k=4;k>=1;k--){ink.setColor((0x14*k<<24)|0xffd27a);c.drawCircle(cx,cy,r*(1f+.08f*k),ink);}
        }
        // Medal: the award's own artwork, popping in with a bounce.
        float s=r*(.55f+.45f*pop);
        Bitmap atlas=AchievementGallery.medalAtlas(getContext());
        box.set(cx-s,cy-s,cx+s,cy+s);
        if(atlas!=null){
            ink.setColorFilter(earned?null:greyed());ink.setAlpha(earned?255:170);
            c.drawBitmap(atlas,AchievementGallery.medalCell(award),box,ink);
            ink.setColorFilter(null);ink.setAlpha(255);
        }else{
            // Artwork unavailable: a plain medal disc with a star, never another award's art.
            ink.setColor(earned?0xffe8ac32:0xff4a2a1c);c.drawCircle(cx,cy,s,ink);
            ink.setColor(earned?0xffc98a22:0xff3a2016);c.drawCircle(cx,cy,s*.78f,ink);
            Path star=new Path();
            for(int k=0;k<10;k++){double a=Math.toRadians(-90+36*k);float rr=k%2==0?s*.5f:s*.22f;
                float px=cx+(float)Math.cos(a)*rr,py=cy+(float)Math.sin(a)*rr;if(k==0)star.moveTo(px,py);else star.lineTo(px,py);}
            star.close();ink.setColor(earned?0xffffe08a:0xff5a3a26);c.drawPath(star,ink);
        }
        ink.setStyle(Paint.Style.STROKE);ink.setStrokeWidth(3*dp);ink.setColor(earned?0xffffd27a:0x66a58a6c);c.drawCircle(cx,cy,s+4*dp,ink);
        ink.setStyle(Paint.Style.FILL);
        // Words: state, title, goal or deed, points.
        float tx=cx+r+20*dp,tw=w-pad-tx;
        boolean stacked=tw<170*dp;
        if(stacked){tx=pad;tw=w-2*pad;}
        float ty=stacked?cy+r+22*dp:pad+30*sp;
        text(c,earned?"EARNED":"LOCKED",tx,ty,stencil,13,earned?GameInk.GOLD:0xffef9979,Paint.Align.LEFT);
        String title=award.title.toUpperCase(java.util.Locale.ROOT);
        float size=fit(title,stencil,30,tw);
        ink.setShadowLayer(2*dp,0,dp,0xff0a0504);
        text(c,title,tx,ty+size*sp*1.1f,stencil,size,GameInk.TEXT,Paint.Align.LEFT);
        ink.clearShadowLayer();
        float py=ty+size*sp*1.1f+10*dp;
        py+=paragraph(c,earned?award.deed:award.goal,tx,py,tw,body,16,GameInk.TEXT,3)+8*dp;
        if(award.points>0)text(c,"◆ "+award.points+" FIGHT SCORE",tx,py+12*sp,stencil,14,GameInk.GOLD,Paint.Align.LEFT);
        // Progress: the category bar and an overall ring, along the bottom.
        float base=h-pad,ringR=Math.min(30*dp,h*.09f),barLeft=pad,barRight=w-pad-ringR*2-24*dp;
        float barY=base-14*dp;
        text(c,category.toUpperCase(java.util.Locale.ROOT)+"   "+have+" / "+total,barLeft,barY-12*dp,stencil,13,GameInk.TEXT,Paint.Align.LEFT);
        ink.setColor(0xff4a2419);c.drawRoundRect(barLeft,barY-4*dp,barRight,barY+4*dp,4*dp,4*dp,ink);
        float fill=barLeft+(barRight-barLeft)*(total<=0?0:have/(float)total)*Math.min(1f,pop);
        ink.setShader(new LinearGradient(barLeft,0,barRight,0,0xffc98a22,0xffffe08a,Shader.TileMode.CLAMP));
        c.drawRoundRect(barLeft,barY-4*dp,Math.max(barLeft+8*dp,fill),barY+4*dp,4*dp,4*dp,ink);ink.setShader(null);
        float rcx=w-pad-ringR,rcy=base-ringR-2*dp;
        ink.setStyle(Paint.Style.STROKE);ink.setStrokeWidth(5*dp);ink.setColor(0xff4a2419);c.drawCircle(rcx,rcy,ringR,ink);
        ink.setColor(0xffe8ac32);ink.setStrokeCap(Paint.Cap.ROUND);
        box.set(rcx-ringR,rcy-ringR,rcx+ringR,rcy+ringR);
        c.drawArc(box,-90,360f*(allTotal<=0?0:allHave/(float)allTotal)*Math.min(1f,pop),false,ink);
        ink.setStyle(Paint.Style.FILL);ink.setStrokeCap(Paint.Cap.BUTT);
        text(c,percent(allHave,allTotal)+"%",rcx,rcy+5*sp,stencil,14,GameInk.TEXT,Paint.Align.CENTER);
        text(c,allHave+" / "+allTotal,rcx,rcy+ringR+14*sp,stencil,10,GameInk.MUTED,Paint.Align.CENTER);
    }
    private static ColorFilter greyed(){
        ColorMatrix m=new ColorMatrix();m.setSaturation(.1f);return new ColorMatrixColorFilter(m);
    }

    private void drawRecord(Canvas c,float w,float h){
        float pad=18*dp;
        boolean wide=w>h*1.05f;
        float r=Math.min(wide?Math.min(h*.36f,w*.26f):Math.min(w*.34f,h*.24f),130*dp);
        float cx=wide?pad+r+6*dp:w/2,cy=wide?h*.46f:pad+r+16*dp;
        // Sleeve edge behind the disc.
        ink.setColor(0xff2a120c);c.drawRoundRect(cx-r*1.05f,cy-r*1.05f,cx+r*.15f,cy+r*1.05f,6*dp,6*dp,ink);
        ink.setColor(GameInk.ORANGE);c.drawRect(cx-r*1.05f,cy-r*1.05f,cx-r*1.05f+3*dp,cy+r*1.05f,ink);
        // Disc, grooves and a gold label, turning while a track plays.
        float pop=Math.max(0f,fraction),slide=(1f-Math.min(1f,pop))*r*.4f;
        c.save();c.translate(slide,0);c.rotate(angle,cx,cy);
        ink.setColor(0xff0c0807);c.drawCircle(cx,cy,r,ink);
        ink.setStyle(Paint.Style.STROKE);ink.setStrokeWidth(dp);
        for(float g=r*.42f;g<r*.97f;g+=r*.045f){ink.setColor(g%(r*.09f)<r*.045f?0x22ffffff:0x14ffffff);c.drawCircle(cx,cy,g,ink);}
        ink.setStyle(Paint.Style.FILL);
        // A light sheen that stays fixed as the disc turns reads as rotation.
        c.restore();c.save();c.translate(slide,0);
        ink.setShader(new SweepGradient(cx,cy,new int[]{0x00ffffff,0x18ffffff,0x00ffffff,0x00ffffff,0x18ffffff,0x00ffffff},null));
        c.drawCircle(cx,cy,r,ink);ink.setShader(null);
        c.rotate(angle,cx,cy);
        ink.setColor(0xffe8ac32);c.drawCircle(cx,cy,r*.36f,ink);
        ink.setColor(0xffffd27a);c.drawCircle(cx,cy,r*.30f,ink);
        ink.setColor(0xffe8ac32);c.drawCircle(cx,cy,r*.27f,ink);
        ink.setColor(0xff7a4a12);
        ink.setTypeface(stencil);ink.setTextAlign(Paint.Align.CENTER);ink.setTextSize(Math.max(8*sp,r*.11f));
        c.drawText("EA TRAX",cx,cy-r*.13f,ink);
        ink.setTextSize(r*.05f);
        float label=ink.measureText("FIGHT NIGHT ROUND 3");
        if(label>r*.46f)ink.setTextSize(ink.getTextSize()*r*.46f/label);
        c.drawText("FIGHT NIGHT ROUND 3",cx,cy+r*.18f,ink);
        ink.setColor(0xff120806);c.drawCircle(cx,cy,r*.04f,ink);
        c.restore();
        // Words: now playing, title, artist, and the settings as chips.
        float tx=wide?cx+r+24*dp:pad,tw=w-pad-tx,ty=wide?h*.24f:cy+r+28*dp;
        text(c,song==null?"NOTHING PLAYING":paused?"PAUSED":"NOW PLAYING",tx,ty,stencil,13,paused?0xffef9979:GameInk.GOLD,Paint.Align.LEFT);
        String title=song==null?"EA Trax":song.title;
        float size=fit(title,stencil,32,tw);
        ink.setShadowLayer(2*dp,0,dp,0xff0a0504);
        float py=ty+8*dp+paragraph(c,title,tx,ty+6*dp,tw,stencil,size<22?22:size,GameInk.TEXT,2);
        ink.clearShadowLayer();
        if(song!=null)py+=paragraph(c,song.artist,tx,py+4*dp,tw,body,18,GameInk.MUTED,2)+6*dp;
        py+=14*dp;
        py=chip(c,"FIGHTS",fights,tx,py,tw);
        chip(c,"FN3 EXPANDED",expanded,tx,py,tw);
    }
    private float chip(Canvas c,String label,boolean on,float x,float y,float room){
        ink.setTypeface(stencil);ink.setTextSize(13*sp);
        String state=on?"ON":"OFF";
        float lw=ink.measureText(label),sw=ink.measureText(state),cw=lw+sw+34*dp,ch=26*dp;
        ink.setStyle(Paint.Style.FILL);ink.setColor(on?0xff4a2a10:0xff2a140e);c.drawRoundRect(x,y,x+cw,y+ch,ch/2,ch/2,ink);
        ink.setStyle(Paint.Style.STROKE);ink.setStrokeWidth(1.5f*dp);ink.setColor(on?0xffe8ac32:0x66a58a6c);c.drawRoundRect(x,y,x+cw,y+ch,ch/2,ch/2,ink);
        ink.setStyle(Paint.Style.FILL);
        text(c,label,x+12*dp,y+ch*.66f,stencil,13,GameInk.TEXT,Paint.Align.LEFT);
        text(c,state,x+cw-12*dp,y+ch*.66f,stencil,13,on?GameInk.GOLD:GameInk.MUTED,Paint.Align.RIGHT);
        return y+ch+8*dp;
    }
}
