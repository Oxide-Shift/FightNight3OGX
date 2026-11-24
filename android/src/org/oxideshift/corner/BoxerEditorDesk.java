package org.oxideshift.corner;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import static org.oxideshift.corner.CornerControls.Key;

/** Touch controls for the active page of the original Career boxer editor. */
final class BoxerEditorDesk extends LinearLayout {
    final CareerDesk.Page page;
    private final Typeface stencil;
    private final CornerControls.Output output;
    private final LinearLayout controls,footer;
    private final ScrollView scroll;
    private LinearLayout values,name;
    private boolean nameSelected;
    boolean help;
    boolean dialog;

    BoxerEditorDesk(Context c,Typeface stencil,CareerDesk.Page page,CornerControls.Output output){
        super(c);this.stencil=stencil;this.page=page;this.output=output;
        setOrientation(VERTICAL);setBackground(new CornerInk(0xff25120d,0xff74301c));
        scroll=new ScrollView(c);scroll.setFillViewport(true);
        addView(scroll,new LayoutParams(-1,0,1));
        controls=new LinearLayout(c);controls.setOrientation(VERTICAL);
        controls.setPadding(dp(12),dp(8),dp(12),dp(5));scroll.addView(controls);
        footer=new LinearLayout(c);footer.setBackgroundColor(0xff310e07);
        addView(footer,new LayoutParams(-1,-2));
        show(false);
    }
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private TextView type(String value,int size,int color){
        TextView t=new TextView(getContext());t.setTypeface(stencil);t.setText(value);t.setTextColor(color);
        t.setTextSize(size);t.setIncludeFontPadding(false);return t;
    }
    private LinearLayout row(){
        LinearLayout line=new LinearLayout(getContext());line.setBaselineAligned(false);controls.addView(line);
        return line;
    }
    private void command(LinearLayout line,String cap,String name,Key key){
        LinearLayout tile=new LinearLayout(getContext());tile.setGravity(Gravity.CENTER_VERTICAL);tile.setPadding(dp(8),dp(3),dp(4),dp(3));
        tile.setMinimumHeight(dp(48));tile.setFocusable(true);tile.setContentDescription(cap+", "+name);
        tile.setBackground(new RippleDrawable(ColorStateList.valueOf(0x558e5d31),new CornerInk(0xff321d14,0xff875335),null));
        tile.setOnClickListener(v->output.press(key));tile.setSoundEffectsEnabled(false);
        TextView icon=type(cap,15,GameInk.GOLD);icon.setGravity(Gravity.CENTER);
        tile.addView(icon,new LayoutParams(dp(30),-2));
        TextView action=type(name,16,GameInk.TEXT);action.setPadding(dp(5),0,0,0);tile.addView(action,new LayoutParams(0,-2,1));
        LayoutParams size=new LayoutParams(0,-2,1);size.setMargins(dp(2),dp(2),dp(2),dp(2));line.addView(tile,size);
    }
    private void foot(PadIcon.Cap cap,String name,Key key){
        MenuPrompt p=new MenuPrompt(getContext(),cap,name,stencil,()->output.press(key));
        footer.addView(p,new LayoutParams(0,-2,1));
    }
    void nameField(boolean selected){
        nameSelected=selected;
        if(name!=null){
            name.setVisibility(help || selected?VISIBLE:GONE);
            values.setVisibility(help || !selected?VISIBLE:GONE);
        }
    }
    void showDialog(){
        dialog=true;help=false;controls.removeAllViews();footer.removeAllViews();
        controls.addView(type("EDIT BOXER",14,GameInk.GOLD));
        TextView title=type("CONFIRMATION",27,GameInk.TEXT);title.setPadding(dp(2),0,0,dp(6));controls.addView(title);
        LinearLayout line=row();command(line,"↑","Choice up",Key.UP);command(line,"↓","Choice down",Key.DOWN);
        foot(PadIcon.Cap.A,"Select",Key.A);
    }
    void show(boolean expanded){
        help=expanded;controls.removeAllViews();footer.removeAllViews();scroll.scrollTo(0,0);
        if(page.index!=1 || help)controls.addView(type("EDIT BOXER",14,GameInk.GOLD));
        TextView title=type(help?"CONTROLS":page.title.toUpperCase(java.util.Locale.ROOT),27,GameInk.TEXT);
        title.setPadding(dp(2),0,0,dp(6));controls.addView(title);
        LinearLayout line=row();
        command(line,"↑","Field up",Key.UP);command(line,"↓","Field down",Key.DOWN);
        values=row();command(values,"←","Previous value",Key.LEFT);command(values,"→","Next value",Key.RIGHT);
        if(page.index==7)values.setVisibility(GONE);
        if(page.index==2){name=row();command(name,"A","Edit name",Key.A);nameField(nameSelected);}
        if(page.index>1){
            line=row();command(line,"LT","Previous tab",Key.LT);command(line,"RT","Next tab",Key.RT);
            if(page.index==7)command(row(),"X","Toggle highlight",Key.X);
            if(expanded){
                line=row();command(line,"LB","Rotate left",Key.LB);command(line,"RB","Rotate right",Key.RB);
                if(page.index!=4)command(row(),"R3","Randomize",Key.R3);
            }
        }
        if(page.index==1)foot(PadIcon.Cap.A,"Select",Key.A);
        else foot(PadIcon.Cap.START,"Accept",Key.START);
        if(help)foot(PadIcon.Cap.B,"Close help",Key.B);
        else{
            if(page.index==1)foot(PadIcon.Cap.B,"Back",Key.B);
            foot(PadIcon.Cap.Y,"Help",Key.Y);
        }
    }
}
