package org.oxideshift.corner;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** The unlock cue (Notification-Motion study pp. 5-6): the earned medal anchored at the right and a printed receipt
 *  opening to its left. Field and copy share one right-anchored clip; the medal and the two text groups move alone.
 *  The host supplies the award's visible age, so a hidden cue keeps its place and never replays its entrance. */
final class AwardCue extends FrameLayout {
    private final View field,medal;
    private final LinearLayout copy,nameGroup;
    private final TextView deed;
    private final CueMotion.Pose pose=new CueMotion.Pose();
    private final Rect clip=new Rect();
    final Achievements.Award award;

    AwardCue(Context c,DiscFonts fonts,Achievements.Award award){
        super(c);this.award=award;
        float d=getResources().getDisplayMetrics().density;
        field=new View(c);field.setBackground(new Receipt(d));
        addView(field,new LayoutParams(-1,-1));
        copy=new LinearLayout(c);copy.setOrientation(LinearLayout.VERTICAL);copy.setGravity(Gravity.CENTER_VERTICAL);
        copy.setPadding(dp(16),dp(10),dp(96),dp(11));copy.setMinimumHeight(dp(90));
        addView(copy,new LayoutParams(-1,-2));
        nameGroup=new LinearLayout(c);nameGroup.setOrientation(LinearLayout.VERTICAL);copy.addView(nameGroup);
        // Kicker and points read with the title; zero-point awards give that space to the name.
        TextView kicker=label("ACHIEVEMENT UNLOCKED"+(award.points>0?"   ◆ "+award.points:""),12,GameInk.GOLD,fonts.stencil);
        kicker.setLetterSpacing(.08f);nameGroup.addView(kicker);
        TextView title=label(award.title,23,0xfffff4d5,fonts.stencil);title.setMaxLines(2);
        title.setShadowLayer(dp(2),0,dp(1),0xff0a0504);nameGroup.addView(title);
        deed=label(award.deed,15,0xffe8bd76,fonts.body);deed.setMaxLines(3);deed.setPadding(0,dp(2),0,0);copy.addView(deed);
        medal=AchievementGallery.medal(c,true,award);
        LayoutParams spot=new LayoutParams(dp(72),dp(72),Gravity.RIGHT|Gravity.CENTER_VERTICAL);spot.rightMargin=dp(10);
        addView(medal,spot);
        setClipChildren(false);
        setContentDescription("Achievement unlocked. "+award.title+". "+award.deed+(award.points>0?" "+award.points+" Fight Score.":""));
    }
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private TextView label(String s,int size,int color,Typeface font){
        TextView v=new TextView(getContext());v.setText(s);v.setTextSize(size);v.setTextColor(color);v.setTypeface(font);
        v.setIncludeFontPadding(false);v.setEllipsize(TextUtils.TruncateAt.END);return v;
    }
    /** Apply the pose at this visible age. Returns false once the cue has fully exited. */
    boolean render(long visibleAge,boolean reduced){
        CueMotion.sample(CueMotion.Kind.AWARD,visibleAge,reduced,pose);
        if(!pose.visible)return false;
        float d=getResources().getDisplayMetrics().density;
        int w=getWidth(),h=getHeight(),shown=Math.round(w*pose.field);
        clip.set(w-shown,0,w,h);
        field.setClipBounds(clip);copy.setClipBounds(clip);
        medal.setAlpha(pose.emblem);medal.setScaleX(pose.emblemScale);medal.setScaleY(pose.emblemScale);
        medal.setTranslationX(pose.emblemX*d);
        nameGroup.setAlpha(pose.name);nameGroup.setTranslationX(pose.wordsX*d);
        deed.setAlpha(pose.detail);deed.setTranslationX(pose.wordsX*d);
        return true;
    }
    @Override protected void onMeasure(int widthSpec,int heightSpec){
        int width=MeasureSpec.getSize(widthSpec);
        copy.measure(MeasureSpec.makeMeasureSpec(width,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));
        int height=Math.max(dp(90),copy.getMeasuredHeight());
        int w=MeasureSpec.makeMeasureSpec(width,MeasureSpec.EXACTLY),h=MeasureSpec.makeMeasureSpec(height,MeasureSpec.EXACTLY);
        copy.measure(w,h);field.measure(w,h);
        medal.measure(MeasureSpec.makeMeasureSpec(dp(72),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(dp(72),MeasureSpec.EXACTLY));
        setMeasuredDimension(width,height);
    }
    /** Printed results slip: warm dark paper, a gold rule, a clipped oxide edge on the left. */
    private static final class Receipt extends Drawable {
        private final Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float d;private final Path edge=new Path();
        Receipt(float d){this.d=d;}
        @Override public void draw(Canvas c){
            Rect r=getBounds();float w=r.width(),h=r.height(),slant=h*.22f;
            edge.reset();edge.moveTo(r.left+slant,r.top+4*d);edge.lineTo(r.right,r.top+4*d);edge.lineTo(r.right,r.bottom-4*d);
            edge.lineTo(r.left,r.bottom-4*d);edge.close();
            ink.setStyle(Paint.Style.FILL);
            ink.setShader(new LinearGradient(r.left,0,r.right,0,0xff2a1c16,0xff352820,Shader.TileMode.CLAMP));c.drawPath(edge,ink);ink.setShader(null);
            ink.setColor(0xffaa642a);c.drawRect(r.left+slant,r.top+4*d,r.right,r.top+5.5f*d,ink);
            ink.setColor(0xff75391e);c.drawRect(r.left,r.bottom-6*d,r.right,r.bottom-4*d,ink);
            ink.setColor(0xff681e14);
            Path oxide=new Path();oxide.moveTo(r.left+slant,r.top+4*d);oxide.lineTo(r.left+slant+5*d,r.top+4*d);
            oxide.lineTo(r.left+5*d,r.bottom-4*d);oxide.lineTo(r.left,r.bottom-4*d);oxide.close();c.drawPath(oxide,ink);
        }
        @Override public void setAlpha(int a){}
        @Override public void setColorFilter(ColorFilter f){}
        @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
}
