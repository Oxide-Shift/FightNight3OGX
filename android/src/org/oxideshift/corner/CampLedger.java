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
    private static final int PAPER=GameInk.PAPER, INK=GameInk.DARK, RED=GameInk.NAME_RED, GOLD=0xffa86b20;
    private final TextView heading, amount, caption, balance;
    private final CaseRack rack;
    CampLedger(Context context,DiscFonts fonts){
        super(context);
        setOrientation(VERTICAL);
        setPadding(dp(15),dp(6),dp(12),dp(8));
        setBackground(new CornerInk(PAPER,RED));
        heading=line(fonts.stencil,12,RED);
        heading.setLetterSpacing(.08f);
        amount=line(fonts.stencil,25,INK);
        rack=new CaseRack(context);
        addView(rack,new LayoutParams(-1,dp(17)));
        caption=line(Typeface.DEFAULT,15,INK);
        balance=line(fonts.stencil,15,RED);
        setVisibility(GONE);
    }
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView line(Typeface face,int size,int color){
        TextView v=new TextView(getContext());
        v.setTypeface(face);v.setTextSize(size);v.setTextColor(color);v.setIncludeFontPadding(false);
        addView(v);return v;
    }
    private static void bind(TextView view,String value){
        if(!value.contentEquals(view.getText()))view.setText(value);
        view.setVisibility(value.isEmpty()?GONE:VISIBLE);
    }
    boolean show(CareerDesk desk,CareerDesk.Page page){
        boolean trophy=page!=null && page.is("trophycase");
        boolean shop=page!=null && page.is("fightstore");
        if(!trophy && !shop){setVisibility(GONE);return false;}
        setVisibility(VISIBLE);
        rack.setVisibility(trophy && desk.trophies()>=0?VISIBLE:GONE);
        if(trophy){
            int mask=desk.trophies(),slot=desk.selectedTrophy();
            bind(heading,"TROPHY CABINET");
            bind(amount,mask>=0?Integer.bitCount(mask)+" / 14 earned":"");
            rack.show(mask,slot);
            bind(caption,slot>=0 && mask>=0?"Trophy "+(slot+1)+"   "+((mask & (1<<slot))!=0?"Earned":"Locked"):page.note);
            bind(balance,"");
        }else{
            CareerDesk.Budget quote=desk.storeBudget();
            int cost=quote==null?-1:quote.cost, cash=quote==null?-1:quote.cash;
            bind(heading,"ITEM PRICE");
            bind(amount,cost>=0?CareerDesk.money(cost):"");
            bind(caption,cash>=0?"Career wallet    "+CareerDesk.money(cash):cost<0?page.note:"");
            bind(balance,cost<0 || cash<0?"":cash<cost?"Short by    "+CareerDesk.money(cost-cash):
                    "After purchase    "+CareerDesk.money(cash-cost));
        }
        return true;
    }
    private final class CaseRack extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF cell=new RectF();
        private int mask=-1,slot=-1;
        CaseRack(Context context){super(context);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        void show(int earned,int selected){if(mask!=earned || slot!=selected){mask=earned;slot=selected;invalidate();}}
        @Override protected void onDraw(Canvas canvas){
            float gap=dp(2),width=(getWidth()-13*gap)/14f;
            if(width<=0 || mask<0)return;
            for(int i=0;i<14;i++){
                float x=i*(width+gap);
                paint.setColor((mask&(1<<i))!=0?GOLD:0xffc8b89c);
                cell.set(x,dp(3),x+width,dp(10));canvas.drawRoundRect(cell,dp(1),dp(1),paint);
                if(slot==i){paint.setColor(RED);canvas.drawRect(x,dp(12),x+width,dp(14),paint);}
            }
        }
    }
}
