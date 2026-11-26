package org.oxideshift.corner;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;

/** A native Xbox button and caption, sized for the lower touch display. */
final class MenuPrompt extends LinearLayout {
    private final PadIcon glyph;
    private final TextView label;
    private final PadIcon.Cap cap;
    private int horizontalWidth;
    private boolean compact;
    MenuPrompt(Context ctx,int key,Typeface stencil,Runnable action){
        this(ctx,PadIcon.Cap.valueOf(BroadcastCues.KEYS[key].name()),FooterLegend.LABELS[key],stencil,action);
    }
    MenuPrompt(Context ctx,PadIcon.Cap cap,String caption,Typeface stencil,Runnable action){
        super(ctx);this.cap=cap;setOrientation(HORIZONTAL);setGravity(Gravity.CENTER);
        setPadding(dp(3),0,dp(3),0);setMinimumHeight(dp(48));
        setBackground(new RippleDrawable(ColorStateList.valueOf(0x55efb556),null,
                new ColorDrawable(0xffffffff)));
        setFocusable(true);setClickable(true);setSoundEffectsEnabled(false);
        setOnClickListener(v->action.run());
        glyph=new PadIcon(ctx,cap,stencil);
        addView(glyph,new LayoutParams(dp(38),dp(36)));
        label=new TextView(ctx);
        label.setTypeface(stencil);label.setTextSize(19);label.setIncludeFontPadding(false);
        setCaption(caption);
        label.setTextColor(0xffffe5b4);label.setShadowLayer(dp(1),0,dp(1),0xff190b06);
        label.setSingleLine(true);label.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        LayoutParams lp=new LayoutParams(-2,-2);lp.leftMargin=dp(4);addView(label,lp);
    }
    void setCaption(String caption){
        String text=caption.toUpperCase(java.util.Locale.ROOT);
        if(text.contentEquals(label.getText()))return;
        label.setText(text);setContentDescription(cap.name()+", "+caption);
        float full=android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP,19,getResources().getDisplayMetrics());
        horizontalWidth=dp(48)+(int)Math.ceil(label.getPaint().measureText(text)*full/label.getTextSize());
        requestLayout();
    }
    @Override protected void onMeasure(int widthSpec,int heightSpec){
        boolean narrow=MeasureSpec.getMode(widthSpec)!=MeasureSpec.UNSPECIFIED &&
                MeasureSpec.getSize(widthSpec)<Math.max(dp(118),horizontalWidth);
        if(narrow!=compact){
            compact=narrow;
            setOrientation(narrow?VERTICAL:HORIZONTAL);
            setGravity(Gravity.CENTER);
            setPadding(dp(2),0,dp(2),0);
            glyph.setLayoutParams(new LayoutParams(dp(narrow?30:38),dp(narrow?26:36)));
            label.setTextSize(narrow?14:19);
            LayoutParams lp=new LayoutParams(-2,-2);
            if(!narrow)lp.leftMargin=dp(4);
            label.setLayoutParams(lp);
        }
        super.onMeasure(widthSpec,heightSpec);
    }
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}
