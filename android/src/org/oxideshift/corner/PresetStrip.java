package org.oxideshift.corner;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import java.util.function.IntConsumer;

/** Three directly selectable choices. Keyboard focus and the saved choice have separate outlines. */
final class PresetStrip extends RadioGroup {
    PresetStrip(Context context,String title,String[] labels,int selected,IntConsumer change){
        super(context);setOrientation(HORIZONTAL);setContentDescription(title);
        int[][] states={{-android.R.attr.state_enabled},{android.R.attr.state_checked},{}};
        ColorStateList text=new ColorStateList(states,new int[]{0xffab9f90,FightMenu.INK,FightMenu.PAPER});
        for(int i=0;i<labels.length;i++){
            RadioButton item=new RadioButton(context);
            item.setId(View.generateViewId());item.setSaveEnabled(false);
            item.setButtonDrawable(null);item.setBackgroundTintList(null);
            item.setText(labels[i]);item.setGravity(Gravity.CENTER);item.setTextSize(16);
            item.setTextColor(text);item.setTypeface(DiscFonts.FALLBACK.body);
            item.setPadding(dp(3),dp(3),dp(3),dp(3));item.setMinHeight(dp(48));
            item.setBackground(new RippleDrawable(ColorStateList.valueOf(0x45ffffff),background(),null));
            LayoutParams lp=new LayoutParams(0,-2,1);if(i>0)lp.leftMargin=dp(5);addView(item,lp);
        }
        check(getChildAt(selected).getId());
        setOnCheckedChangeListener((group,id)->{
            View item=findViewById(id);
            if(item!=null)change.accept(indexOfChild(item));
        });
    }
    private int dp(int units){return Math.round(units*getResources().getDisplayMetrics().density);}
    private GradientDrawable tile(int fill,int border,int stroke){
        GradientDrawable o=new GradientDrawable();o.setColor(fill);o.setCornerRadius(dp(4));o.setStroke(dp(stroke),border);return o;
    }
    private StateListDrawable background(){
        int checked=android.R.attr.state_checked,focus=android.R.attr.state_focused,enabled=android.R.attr.state_enabled;
        StateListDrawable bg=new StateListDrawable();
        bg.addState(new int[]{-enabled,checked},tile(0xff554b3d,0xff756852,1));
        bg.addState(new int[]{-enabled},tile(0xff25211d,0xff433b32,1));
        bg.addState(new int[]{focus,checked},tile(0xffebbd70,0xffffffff,3));
        bg.addState(new int[]{focus},tile(0xff473a2c,0xfffff3da,3));
        bg.addState(new int[]{checked},tile(0xffebbd70,0xffffd999,1));
        bg.addState(new int[0],tile(0xff302a24,0xff675847,1));return bg;
    }
    int index(){return indexOfChild(findViewById(getCheckedRadioButtonId()));}
    @Override public void setEnabled(boolean enabled){
        super.setEnabled(enabled);
        for(int i=0;i<getChildCount();i++)getChildAt(i).setEnabled(enabled);
    }
}
