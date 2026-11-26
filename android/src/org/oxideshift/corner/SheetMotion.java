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
    static final int ENTER_MS=260,STAGGER_MS=40,CHANGE_MS=140,ROLL_MS=420;
    /** Built on demand so the number helpers stay usable without the Android animation framework. */
    static Interpolator settle(){return new DecelerateInterpolator(2f);}
    static Interpolator slam(){return new OvershootInterpolator(1.6f);}
    private SheetMotion(){}

    static boolean on(){return ValueAnimator.areAnimatorsEnabled();}
    private static float dp(View v,float n){return n*v.getResources().getDisplayMetrics().density;}

    /** A sheet arriving: each visible section rises 12dp and fades in, top to bottom. */
    static void enter(ViewGroup sheet){
        if(!on() || !sheet.isAttachedToWindow())return;
        int k=0;
        for(int i=0;i<sheet.getChildCount();i++){
            View child=sheet.getChildAt(i);
            if(child.getVisibility()!=View.VISIBLE)continue;
            child.animate().cancel();
            child.setAlpha(0f);child.setTranslationY(dp(child,12));
            child.animate().alpha(1f).translationY(0).setStartDelay(STAGGER_MS*k++).setDuration(ENTER_MS).setInterpolator(settle()).start();
        }
    }
    /** A single view becoming visible. */
    static void appear(View v){
        if(!on() || !v.isAttachedToWindow())return;
        v.animate().cancel();v.setAlpha(0f);v.setTranslationY(dp(v,10));
        v.animate().alpha(1f).translationY(0).setStartDelay(0).setDuration(ENTER_MS).setInterpolator(settle()).start();
    }
    /** New content in the same place: a short slide-and-fade from the browse direction. */
    static void nudge(View v,int direction){
        if(!on() || !v.isShown())return;
        v.animate().cancel();v.setAlpha(.35f);v.setTranslationX(dp(v,10)*direction);
        v.animate().alpha(1f).translationX(0).setStartDelay(0).setDuration(CHANGE_MS).setInterpolator(settle()).start();
    }
    /** A stamp landing: oversized and transparent to full size, with a small bounce. */
    static void slam(View v){
        if(!on() || !v.isShown())return;
        v.animate().cancel();v.setScaleX(1.35f);v.setScaleY(1.35f);v.setAlpha(0f);
        v.animate().scaleX(1f).scaleY(1f).alpha(1f).setStartDelay(60).setDuration(220).setInterpolator(slam()).start();
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
        v.setTag(R_ROLL,roll);roll.start();
    }
    static String percent(int n){return (n>0?"+":"")+n+"%";}
    /** View tag key for a running roll (an app-private id outside the framework's range). */
    static final int R_ROLL=0x7f0f0001;
}
