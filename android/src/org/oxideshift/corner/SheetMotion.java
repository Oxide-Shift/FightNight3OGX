package org.oxideshift.corner;

import android.animation.ValueAnimator;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.TextView;

/** One motion language for the career lower screen (career directive p. 27: settle 80 ms, sheet change 140 ms,
 *  Back 100 ms). Every helper is a direct swap when the system has animations turned off. */
final class SheetMotion {
    static final int ENTER_MS=340,STAGGER_MS=48,CHANGE_MS=190,ROLL_MS=520;
    /** Built on demand so the number helpers stay usable without the Android animation framework. */
    static Interpolator settle(){return new DecelerateInterpolator(2f);}
    static Interpolator slam(){return new OvershootInterpolator(1.6f);}
    /** A soft landing with a little give, for things arriving rather than being stamped. */
    static Interpolator arrive(){return new OvershootInterpolator(.9f);}
    private SheetMotion(){}

    static boolean on(){return ValueAnimator.areAnimatorsEnabled();}
    private static float dp(View v,float n){return n*v.getResources().getDisplayMetrics().density;}

    /** A sheet arriving: each visible section rises 18dp from slightly smaller and fades in, top to bottom, each
     *  landing with a little give. */
    static void enter(ViewGroup sheet){
        if(!on() || !sheet.isAttachedToWindow())return;
        int k=0;
        for(int i=0;i<sheet.getChildCount();i++){
            View child=sheet.getChildAt(i);
            if(child.getVisibility()!=View.VISIBLE)continue;
            child.animate().cancel();
            child.setAlpha(0f);child.setTranslationY(dp(child,18));child.setScaleX(.96f);child.setScaleY(.96f);
            child.animate().alpha(1f).translationY(0).scaleX(1f).scaleY(1f).setStartDelay(STAGGER_MS*k++)
                    .setDuration(ENTER_MS).setInterpolator(arrive()).start();
        }
    }
    /** A single view becoming visible: rises and grows into place. */
    static void appear(View v){
        if(!on() || !v.isAttachedToWindow())return;
        v.animate().cancel();v.setAlpha(0f);v.setTranslationY(dp(v,14));v.setScaleX(.95f);v.setScaleY(.95f);
        v.animate().alpha(1f).translationY(0).scaleX(1f).scaleY(1f).setStartDelay(0).setDuration(ENTER_MS).setInterpolator(arrive()).start();
    }
    /** New content in the same place: a slide-and-fade from the browse direction with a slight push. */
    static void nudge(View v,int direction){
        if(!on() || !v.isShown())return;
        v.animate().cancel();v.setAlpha(.2f);v.setTranslationX(dp(v,24)*direction);v.setScaleX(.985f);v.setScaleY(.985f);
        v.animate().alpha(1f).translationX(0).scaleX(1f).scaleY(1f).setStartDelay(0).setDuration(CHANGE_MS).setInterpolator(settle()).start();
    }
    /** A stamp landing: oversized, tilted and transparent, slamming to size, then a short squash as it hits. */
    static void slam(View v){
        if(!on() || !v.isShown())return;
        v.animate().cancel();v.setScaleX(1.6f);v.setScaleY(1.6f);v.setAlpha(0f);v.setRotation(-6f);
        v.animate().scaleX(1f).scaleY(1f).alpha(1f).rotation(0f).setStartDelay(60).setDuration(240)
                .setInterpolator(new android.view.animation.AccelerateInterpolator(1.4f))
                .withEndAction(()->{
                    v.setScaleX(1.07f);v.setScaleY(.93f);
                    v.animate().scaleX(1f).scaleY(1f).setStartDelay(0).setDuration(260).setInterpolator(new OvershootInterpolator(3f)).start();
                }).start();
    }
    /** A value that just finished changing: a quick swell so the eye catches it. */
    static void pulse(View v){
        if(!on() || !v.isShown())return;
        v.setScaleX(1.14f);v.setScaleY(1.14f);
        v.animate().scaleX(1f).scaleY(1f).setStartDelay(0).setDuration(320).setInterpolator(new OvershootInterpolator(2.4f)).start();
    }

    /** Whole dollars from "$12,500", or -1 when the text is anything else (retail text stays as written). */
    static long dollars(CharSequence text){
        String s=text==null?"":text.toString().trim();
        if(!s.matches("\\$[0-9]{1,3}(,[0-9]{3})*"))return -1;
        return Long.parseLong(s.substring(1).replace(",",""));
    }
    /** Money rolls from the shown amount to the new one; anything that is not a plain amount is set directly. */
    static void rollMoney(TextView v,String text){
        Object running=v.getTag(R_ROLL);
        if(running instanceof ValueAnimator)((ValueAnimator)running).cancel();
        long from=dollars(v.getText()),to=dollars(text);
        if(!on() || !v.isShown() || to<0 || from<0 || from==to){v.setText(text);return;}
        ValueAnimator roll=ValueAnimator.ofFloat(0f,1f).setDuration(ROLL_MS);
        roll.setInterpolator(settle());
        roll.addUpdateListener(a->{
            float f=(float)a.getAnimatedValue();
            v.setText(f>=1f?text:CareerDesk.money((int)Math.round(from+(to-from)*f)));
        });
        roll.addListener(new android.animation.AnimatorListenerAdapter(){
            private boolean cancelled;
            @Override public void onAnimationCancel(android.animation.Animator a){cancelled=true;}
            @Override public void onAnimationEnd(android.animation.Animator a){if(!cancelled)pulse(v);}
        });
        v.setTag(R_ROLL,roll);roll.start();
    }
    /** "+5%" style values roll between whole percentages. */
    static void rollPercent(TextView v,int from,int to,long delay){
        Object running=v.getTag(R_ROLL);
        if(running instanceof ValueAnimator)((ValueAnimator)running).cancel();
        if(!on() || !v.isShown() || from==to){v.setText(percent(to));return;}
        ValueAnimator roll=ValueAnimator.ofFloat(0f,1f).setDuration(ROLL_MS);
        roll.setInterpolator(settle());roll.setStartDelay(delay);
        roll.addUpdateListener(a->v.setText(percent(Math.round(from+(to-from)*(float)a.getAnimatedValue()))));
        roll.addListener(new android.animation.AnimatorListenerAdapter(){
            private boolean cancelled;
            @Override public void onAnimationCancel(android.animation.Animator a){cancelled=true;}
            @Override public void onAnimationEnd(android.animation.Animator a){if(!cancelled)pulse(v);}
        });
        v.setTag(R_ROLL,roll);roll.start();
    }
    static String percent(int n){return (n>0?"+":"")+n+"%";}
    /** View tag key for a running roll (an app-private id outside the framework's range). */
    static final int R_ROLL=0x7f0f0001;
}
