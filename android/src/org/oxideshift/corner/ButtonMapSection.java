package org.oxideshift.corner;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Fight Setup > Controller buttons: one row per Xbox control showing the physical button that presses it.
 *  Choosing a row waits for a button press (ButtonCapture) and swaps; the map is stored in prefs as "padMap"
 *  (absent means the default wiring) and applied by CornerActivity through {@code changed}. */
final class ButtonMapSection extends LinearLayout {
    static final String KEY="padMap";
    static final String HINT="Pick an Xbox control, then press the button you want for it. The two buttons swap. "
            +"In the game, Options > Controller still decides what each Xbox control does.";
    private final SharedPreferences prefs;
    private final Runnable changed;
    private final TextView status;
    private final TextView[] value=new TextView[PadMap.SLOTS];
    private final LinearLayout[] tile=new LinearLayout[PadMap.SLOTS];
    private final Button reset;
    private PadMap map;

    ButtonMapSection(Context c,SharedPreferences prefs,Typeface face,Runnable changed){
        super(c);setOrientation(VERTICAL);this.prefs=prefs;this.changed=changed;
        map=PadMap.decode(prefs.getString(KEY,""));
        status=text(HINT,13,GameInk.MUTED,face);status.setPadding(dp(4),dp(4),dp(4),dp(6));addView(status);
        LinearLayout columns=new LinearLayout(c);columns.setPadding(dp(19),dp(2),dp(16),0);
        TextView left=text("XBOX CONTROL",11,GameInk.GOLD,face),right=text("YOUR BUTTON",11,GameInk.GOLD,face);
        left.setLetterSpacing(.1f);right.setLetterSpacing(.1f);
        columns.addView(left,new LayoutParams(0,-2,1));columns.addView(right);addView(columns);
        for(int xbox:PadMap.ORDER){
            LinearLayout row=new LinearLayout(c);row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackground(new RippleDrawable(ColorStateList.valueOf(0x44f8d58a),new BillSelect(density(),false),null));
            row.setFocusable(true);row.setClickable(true);row.setMinimumHeight(dp(44));row.setPadding(dp(19),0,dp(16),0);
            row.addView(text(PadMap.XBOX[xbox],16,GameInk.TEXT,face),new LayoutParams(0,-2,1));
            TextView physical=text("",15,GameInk.MUTED,face);row.addView(physical);
            value[xbox]=physical;tile[xbox]=row;
            row.setOnClickListener(v->assign(xbox));
            LayoutParams lp=new LayoutParams(-1,-2);lp.topMargin=dp(3);addView(row,lp);
        }
        reset=new Button(c,null,android.R.attr.borderlessButtonStyle);
        reset.setBackground(new RippleDrawable(ColorStateList.valueOf(0x44f8d58a),new BillSelect(density(),false),null));
        reset.setText("Reset to default buttons");reset.setAllCaps(false);reset.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
        reset.setMinHeight(dp(48));reset.setMinimumHeight(dp(48));reset.setTextSize(16);reset.setTypeface(face);
        reset.setPadding(dp(19),dp(3),dp(12),dp(4));reset.setTextColor(new ColorStateList(new int[][]{{-android.R.attr.state_enabled},{}},new int[]{0x80baafa0,GameInk.TEXT}));
        reset.setOnClickListener(v->save(PadMap.standard(),"Default buttons restored. "+HINT));
        LayoutParams lp=new LayoutParams(-1,-2);lp.topMargin=dp(6);addView(reset,lp);
        refresh();
    }
    private float density(){return getResources().getDisplayMetrics().density;}
    private int dp(float n){return Math.round(n*density());}
    private TextView text(String s,int size,int color,Typeface face){
        TextView view=new TextView(getContext());view.setText(s);view.setTextSize(size);view.setTextColor(color);
        view.setTypeface(face);return view;
    }
    PadMap map(){return map;}
    private void assign(int xbox){
        if(!(getContext() instanceof android.app.Activity))return;
        ButtonCapture.show((android.app.Activity)getContext(),PadMap.XBOX[xbox],physical->{
            int before=map.physical(xbox),moved=map.slot(physical);
            save(map.assign(xbox,physical),note(xbox,before,physical,moved));
        });
    }
    static String note(int xbox,int before,int physical,int moved){
        if(before==physical)return PadMap.XBOX[xbox]+" is already on "+PadMap.PHYSICAL[physical]+".";
        return PadMap.XBOX[xbox]+" is now on "+PadMap.PHYSICAL[physical]+". "+PadMap.XBOX[moved]+" moved to "+PadMap.PHYSICAL[before]+".";
    }
    void save(PadMap next,String message){
        map=next;
        if(next.isStandard())prefs.edit().remove(KEY).apply();
        else prefs.edit().putString(KEY,next.encode()).apply();
        status.setText(message);refresh();
        if(changed!=null)changed.run();
    }
    private void refresh(){
        for(int xbox=0;xbox<PadMap.SLOTS;xbox++){
            int physical=map.physical(xbox);
            value[xbox].setText(PadMap.PHYSICAL[physical]);
            value[xbox].setTextColor(physical==xbox?GameInk.MUTED:GameInk.GOLD);
            tile[xbox].setContentDescription(PadMap.XBOX[xbox]+", pressed by "+PadMap.PHYSICAL[physical]);
        }
        reset.setEnabled(!map.isStandard());
    }
}
