package org.oxideshift.corner;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** The live trainer quote, store receipt or trophy case for the highlighted item. */
final class CampLedger extends LinearLayout {
    private final TextView heading, amount, caption, balance;
    private final TrophyCabinet rack;
    private final LinearLayout trophyPanel;
    private final StoreTicket ticket;
    void setArt(CareerArt art){ticket.setArt(art);}
    CampLedger(Context context,DiscFonts fonts){
        super(context);
        setOrientation(VERTICAL);
        ticket=new StoreTicket(context,fonts);addView(ticket,new LayoutParams(-1,-2,1f));
        trophyPanel=new LinearLayout(context);trophyPanel.setOrientation(VERTICAL);addView(trophyPanel,new LayoutParams(-1,-2,1f));
        trophyPanel.setPadding(dp(12),dp(8),dp(12),dp(8));
        trophyPanel.setBackground(new CornerInk(0xff1f0e0a,GameInk.ORANGE));
        heading=line(fonts.stencil,14,GameInk.GOLD);
        heading.setLetterSpacing(.12f);
        amount=line(fonts.stencil,34,GameInk.TEXT);
        amount.setShadowLayer(dp(2),0,dp(1),0xff0a0504);
        caption=line(fonts.stencil,20,GameInk.TEXT);caption.setPadding(0,dp(2),0,dp(6));
        // The cabinet takes the rest of the lower screen.
        rack=new TrophyCabinet(context,fonts.stencil);
        trophyPanel.addView(rack,new LayoutParams(-1,dp(170),1f));
        balance=line(fonts.stencil,15,TrainingSheet.LOSS);
        setVisibility(GONE);
    }
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView line(Typeface face,int size,int color){
        TextView v=new TextView(getContext());
        v.setTypeface(face);v.setTextSize(size);v.setTextColor(color);v.setIncludeFontPadding(false);
        trophyPanel.addView(v);return v;
    }
    private static void bind(TextView view,String value){
        if(!value.contentEquals(view.getText()))view.setText(value);
        view.setVisibility(value.isEmpty()?GONE:VISIBLE);
    }
    boolean show(CareerDesk desk,CareerDesk.Page page){
        boolean trophy=page!=null && page.is("trophycase");
        boolean shop=page!=null && page.is("fightstore");
        if(!trophy && !shop){setVisibility(GONE);return false;}
        boolean entering=getVisibility()!=VISIBLE;
        setVisibility(VISIBLE);
        if(entering)post(()->SheetMotion.enter(this));
        trophyPanel.setVisibility(trophy?VISIBLE:GONE);
        ticket.setVisibility(shop?VISIBLE:GONE);
        rack.setVisibility(trophy?VISIBLE:GONE);
        if(trophy){
            int mask=desk.trophies(),slot=desk.selectedTrophy();
            bind(heading,"CAREER TROPHY CABINET");
            bind(amount,TrophyCabinet.count(mask).toUpperCase(java.util.Locale.ROOT));
            rack.show(mask,slot);
            String selected=TrophyCabinet.caption(mask,slot);
            bind(caption,selected.isEmpty()?page.note:selected);
            caption.setTextColor(selected.isEmpty()?GameInk.MUTED:(mask&(1<<slot))!=0?GameInk.GOLD:GameInk.TEXT);
            bind(balance,"");
        }else{
            ticket.show(page,desk.storeBudget());
        }
        return true;
    }
}
