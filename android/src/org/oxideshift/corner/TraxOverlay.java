package org.oxideshift.corner;

import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** A display-level strip so a scene rebuild cannot restart or bury a track cue. */
final class TraxOverlay extends LinearLayout {
    private final TextView title,artist;
    private final Runnable animateStrip=this::drawCue;
    private FightBroadcast.Song song;
    private Bitmap[] art;
    private final FrameLayout host;
    private final boolean single;
    TraxOverlay(FrameLayout host,DiscFonts fonts,boolean single){
        super(host.getContext());this.host=host;this.single=single;
        setOrientation(VERTICAL);setGravity(Gravity.CENTER_VERTICAL);
        setBackground(new TraxBanner(null,dp(69)));
        setPadding(dp(single?65:79),dp(7),dp(24),dp(8));setMinimumHeight(dp(single?54:63));
        title=label(single?17:21,GameInk.GOLD,fonts.stencil);title.setMaxLines(2);
        artist=label(single?12:15,GameInk.MUTED,fonts.body);artist.setSingleLine(true);
        FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(single?dp(360):-1,-2,Gravity.BOTTOM|Gravity.LEFT);
        lp.setMargins(dp(12),0,dp(12),dp(6));setVisibility(GONE);host.addView(this,lp);
    }
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private TextView label(int size,int color,Typeface font){
        TextView v=new TextView(getContext());v.setTextSize(size);v.setTextColor(color);v.setTypeface(font);
        v.setIncludeFontPadding(false);v.setEllipsize(TextUtils.TruncateAt.END);addView(v);return v;
    }
    void show(FightBroadcast.Song next,Bitmap[] sprites,int inset){
        if(sprites!=art){art=sprites;setBackground(new TraxBanner(art,dp(69)));}
        FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams)getLayoutParams();
        int width=single && host.getWidth()>0?Math.min(dp(360),Math.max(1,host.getWidth()-dp(24))):lp.width;
        if(lp.bottomMargin!=inset+dp(6) || width!=lp.width){
            lp.width=width;lp.bottomMargin=inset+dp(6);setLayoutParams(lp);
        }
        if(next==song)return;
        song=next;
        if(song!=null){title.setText(song.title);artist.setText(song.artist);}
        drawCue();
    }
    private void drawCue(){
        removeCallbacks(animateStrip);
        long age=song==null?-1:SystemClock.uptimeMillis()-song.started;
        if(age<0 || age>=6600){setVisibility(GONE);return;}
        setVisibility(VISIBLE);
        boolean animate=ValueAnimator.areAnimatorsEnabled();
        float shift=0;
        if(animate && age<300){float t=1f-age/300f;shift=-t*t;}
        else if(animate && age>=6250){float t=(age-6250)/350f;shift=t*t;}
        setAlpha(1-Math.abs(shift));setTranslationX(dp(115)*shift);
        if(animate && (age<300 || age>=6250))postOnAnimation(animateStrip);
        else postDelayed(animateStrip,(animate?6250:6600)-age);
    }
    void detach(){removeCallbacks(animateStrip);host.removeView(this);}
}
