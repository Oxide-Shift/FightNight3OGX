package org.oxideshift.corner;

import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** A display-level EA Trax cue so a scene rebuild cannot restart or bury it (Notification-Motion study p. 4).
 *  Three coextensive layers: the printed sleeve and the copy (title, artist) revealed left to right together, and the
 *  disc badge with its tab on top, which catches first and leaves last. Age comes from the song's playback event, so a
 *  highlight movement or rebuild never replays the entrance. */
final class TraxOverlay extends FrameLayout {
    private final TextView title,artist;
    private final View sleeve,emblem;
    private final LinearLayout copy;
    private final Runnable animateStrip=this::drawCue;
    private final CueMotion.Pose pose=new CueMotion.Pose();
    private final Rect clip=new Rect();
    private FightBroadcast.Song song;
    private Bitmap[] art;
    private final FrameLayout host;
    private final boolean single;
    private final float badgeCap;
    TraxOverlay(FrameLayout host,DiscFonts fonts,boolean single){
        super(host.getContext());this.host=host;this.single=single;
        // Larger than the study's 46dp badge and 21sp title, for reading at handheld distance.
        badgeCap=dp(single?72:92);
        setMinimumHeight(dp(single?72:92));
        sleeve=new View(getContext());emblem=new View(getContext());
        sleeve.setBackground(new TraxBanner(null,badgeCap,TraxBanner.SLEEVE));
        addView(sleeve,new LayoutParams(-1,-1));
        copy=new LinearLayout(getContext());copy.setOrientation(LinearLayout.VERTICAL);copy.setGravity(Gravity.CENTER_VERTICAL);
        copy.setPadding(dp(single?70:90),dp(9),dp(30),dp(10));
        // The copy sets the strip's height (at least the minimum); sleeve and emblem match it.
        copy.setMinimumHeight(dp(single?72:92));
        addView(copy,new LayoutParams(-1,-2));
        title=label(single?20:26,0xfffff4d5,fonts.stencil);title.setMaxLines(2);
        title.setShadowLayer(dp(2),0,dp(1),0xff0a0504);
        artist=label(single?14:18,0xffe8bd76,fonts.body);artist.setSingleLine(true);
        artist.setPadding(0,dp(2),0,0);
        addView(emblem,new LayoutParams(-1,-1));
        setClipChildren(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(dp(single?360:420),-2,Gravity.BOTTOM|Gravity.LEFT);
        lp.setMargins(dp(12),0,dp(12),dp(6));setVisibility(GONE);host.addView(this,lp);
    }
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private TextView label(int size,int color,Typeface font){
        TextView v=new TextView(getContext());v.setTextSize(size);v.setTextColor(color);v.setTypeface(font);
        v.setIncludeFontPadding(false);v.setEllipsize(TextUtils.TruncateAt.END);copy.addView(v);return v;
    }
    void show(FightBroadcast.Song next,Bitmap[] sprites,int inset){
        if(sprites!=art){
            art=sprites;
            sleeve.setBackground(new TraxBanner(art,badgeCap,TraxBanner.SLEEVE));
            emblem.setBackground(new TraxBanner(art,badgeCap,TraxBanner.EMBLEM));
            // Missing badge art: keep a text-only sleeve and release the emblem inset.
            copy.setPadding(art==null?dp(18):dp(single?70:90),copy.getPaddingTop(),copy.getPaddingRight(),copy.getPaddingBottom());
        }
        FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams)getLayoutParams();
        int target=dp(single?360:420);
        int width=host.getWidth()>0?Math.min(target,Math.max(1,host.getWidth()-dp(24))):target;
        if(lp.bottomMargin!=inset+dp(6) || width!=lp.width){
            lp.width=width;lp.bottomMargin=inset+dp(6);setLayoutParams(lp);
        }
        if(next==song)return;
        song=next;
        if(song!=null){
            // Skip: stale metadata goes at once and the cue starts at the new playback's age.
            title.setText(song.title);artist.setText(song.artist);
            artist.setVisibility(TextUtils.isEmpty(song.artist)?GONE:VISIBLE);
        }
        drawCue();
    }
    private void drawCue(){
        removeCallbacks(animateStrip);
        long age=song==null?-1:SystemClock.uptimeMillis()-song.started;
        boolean reduced=!ValueAnimator.areAnimatorsEnabled();
        CueMotion.sample(CueMotion.Kind.TRAX,age,reduced,pose);
        if(!pose.visible){setVisibility(GONE);return;}
        setVisibility(VISIBLE);
        render();
        long wake=reduced?CueMotion.end(CueMotion.Kind.TRAX)-age:CueMotion.nextWake(CueMotion.Kind.TRAX,age);
        if(wake<=0)postOnAnimation(animateStrip);else postDelayed(animateStrip,wake);
    }
    /** Apply the pose: sleeve and copy share one left-to-right clip; the emblem and words move on their own. */
    private void render(){
        float d=getResources().getDisplayMetrics().density;
        int w=getWidth(),h=getHeight(),shown=Math.round(w*pose.field);
        clip.set(0,0,shown,h);
        sleeve.setClipBounds(clip);copy.setClipBounds(clip);
        emblem.setAlpha(pose.emblem);emblem.setTranslationX(pose.emblemX*d);
        title.setAlpha(pose.name);artist.setAlpha(pose.detail);
        title.setTranslationX(pose.wordsX*d);artist.setTranslationX(pose.wordsX*d);
    }
    /** The copy decides the height; sleeve and emblem are measured to exactly the same box. */
    @Override protected void onMeasure(int widthSpec,int heightSpec){
        int width=MeasureSpec.getSize(widthSpec);
        copy.measure(MeasureSpec.makeMeasureSpec(width,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));
        int height=Math.max(getSuggestedMinimumHeight(),copy.getMeasuredHeight());
        int w=MeasureSpec.makeMeasureSpec(width,MeasureSpec.EXACTLY),h=MeasureSpec.makeMeasureSpec(height,MeasureSpec.EXACTLY);
        copy.measure(w,h);sleeve.measure(w,h);emblem.measure(w,h);
        setMeasuredDimension(width,height);
    }
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b){
        super.onLayout(changed,l,t,r,b);
        if(changed && getVisibility()==VISIBLE)render();
    }
    void detach(){removeCallbacks(animateStrip);host.removeView(this);}
}
