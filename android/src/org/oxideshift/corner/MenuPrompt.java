package org.oxideshift.corner;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;

/** A native Xbox button and caption, sized for the lower touch display. */
final class MenuPrompt extends LinearLayout {
    private static final int[] LIGHT={0xffb7db4b,0xffed7669,0xff7dc8ef,0xfffce272};
    private static final int[] DARK={0xff3b6907,0xff9b231a,0xff125688,0xffa57708};
    private final TextView glyph,label;
    private boolean compact;
    MenuPrompt(Context ctx,int key,Typeface stencil,Runnable action){
        super(ctx);setOrientation(HORIZONTAL);setGravity(Gravity.CENTER_VERTICAL);
        setPadding(dp(8),0,dp(4),0);setMinimumHeight(dp(48));
        setBackground(new RippleDrawable(ColorStateList.valueOf(0x55efb556),null,
                new android.graphics.drawable.ColorDrawable(0xffffffff)));
        setFocusable(true);setClickable(true);String letter=BroadcastCues.KEYS[key].name();
        setContentDescription(letter+", "+FooterLegend.LABELS[key]);setOnClickListener(v->action.run());
        glyph=new TextView(ctx);glyph.setGravity(Gravity.CENTER);glyph.setText(letter);
        glyph.setIncludeFontPadding(false);glyph.setTypeface(stencil);glyph.setTextSize(23);
        glyph.setTextColor(GameInk.TEXT);glyph.setShadowLayer(dp(1),0,dp(1),0xff241511);
        int color=key==BroadcastCues.RETIRE?2:key;
        GradientDrawable cap=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{LIGHT[color],DARK[color]});cap.setShape(GradientDrawable.OVAL);
        cap.setStroke(dp(2),0xff221914);glyph.setBackground(cap);
        int capSize=dp(34);addView(glyph,new LayoutParams(capSize,capSize));
        label=new TextView(ctx);label.setText(FooterLegend.LABELS[key].toUpperCase(java.util.Locale.ROOT));
        label.setTypeface(stencil);label.setTextSize(20);label.setIncludeFontPadding(false);label.setTextColor(GameInk.TEXT);
        label.setSingleLine(true);label.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        LayoutParams lp=new LayoutParams(0,-2,1);
        lp.leftMargin=dp(8);addView(label,lp);
    }
    @Override protected void onMeasure(int widthSpec,int heightSpec){
        boolean narrow=MeasureSpec.getSize(widthSpec)<dp(118);
        if(narrow!=compact){
            compact=narrow;
            setOrientation(narrow?VERTICAL:HORIZONTAL);
            setGravity(narrow?Gravity.CENTER:Gravity.CENTER_VERTICAL);
            setPadding(dp(narrow?2:8),0,dp(narrow?2:4),0);
            glyph.setTextSize(narrow?17:23);
            int cap=dp(narrow?25:34);glyph.setLayoutParams(new LayoutParams(cap,cap));
            label.setTextSize(narrow?14:20);
            LayoutParams lp=narrow?new LayoutParams(-2,-2):new LayoutParams(0,-2,1);
            if(!narrow)lp.leftMargin=dp(8);
            label.setLayoutParams(lp);
        }
        super.onMeasure(widthSpec,heightSpec);
    }
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}
