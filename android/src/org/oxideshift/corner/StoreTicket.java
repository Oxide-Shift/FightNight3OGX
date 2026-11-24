package org.oxideshift.corner;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Locale;

/** A live price tag for the gear highlighted on the upper display. */
final class StoreTicket extends LinearLayout {
    private final CareerBackdrop header=new CareerBackdrop(0xff672510,0xe0391007,0x182a0f06);
    private final TextView department,price,wallet,remainder,notes;
    private final LinearLayout totals;
    StoreTicket(Context ctx,DiscFonts fonts){
        super(ctx);setOrientation(VERTICAL);setBackgroundColor(0xff24120d);
        LinearLayout mast=new LinearLayout(ctx);mast.setOrientation(VERTICAL);mast.setBackground(header);
        mast.setGravity(Gravity.CENTER_VERTICAL);mast.setPadding(dp(14),dp(8),dp(14),dp(8));
        mast.setMinimumHeight(dp(74));addView(mast,new LayoutParams(-1,-2));
        department=type(fonts.stencil,14,GameInk.GOLD);department.setLetterSpacing(.08f);mast.addView(department);
        TextView title=type(fonts.stencil,32,0xffffdfa1);title.setText("FIGHT STORE");
        title.setShadowLayer(dp(2),1,2,0xff230c07);mast.addView(title);
        View rule=new View(ctx);rule.setBackgroundColor(GameInk.ORANGE);addView(rule,new LayoutParams(-1,dp(2)));
        LinearLayout tag=new LinearLayout(ctx);tag.setGravity(Gravity.CENTER_VERTICAL);
        tag.setBackground(new CornerInk(0xff48200f,GameInk.ORANGE));
        tag.setPadding(dp(14),dp(7),dp(14),dp(7));addView(tag,new LayoutParams(-1,-2));
        TextView label=type(fonts.stencil,17,0xffffd9a6);label.setText("ITEM PRICE");
        tag.addView(label,new LayoutParams(0,-2,1));price=type(fonts.stencil,28,GameInk.GOLD);tag.addView(price);
        totals=new LinearLayout(ctx);totals.setOrientation(VERTICAL);
        totals.setBackgroundColor(0xffdcc592);totals.setPadding(dp(14),dp(6),dp(14),dp(8));addView(totals);
        wallet=type(fonts.stencil,17,GameInk.DARK);totals.addView(wallet);
        remainder=type(fonts.stencil,19,GameInk.NAME_RED);totals.addView(remainder);
        notes=type(fonts.stencil,16,0xffffdaa6);notes.setPadding(dp(14),dp(8),dp(14),dp(8));addView(notes);
    }
    void setArt(CareerArt art){header.setArt(art==null?null:art.store);}
    void show(CareerDesk.Page page,CareerDesk.Budget quote){
        int cost=quote==null?-1:quote.cost,cash=quote==null?-1:quote.cash;
        bind(department,page.index==1?"CAREER EQUIPMENT":page.title.toUpperCase(Locale.ROOT));
        bind(price,cost>=0?CareerDesk.money(cost):"\u2014");
        totals.setVisibility(cash>=0?VISIBLE:GONE);
        bind(wallet,cash>=0?"Career wallet   "+CareerDesk.money(cash):"");
        bind(remainder,cost<0 || cash<0?"":(cost>cash?"Short by   ":"After purchase   ")+CareerDesk.money(Math.abs(cash-cost)));
        bind(notes,quote==null || (cost<0 && cash<0)?page.note:page.detail);
    }
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView type(Typeface face,int size,int ink){
        TextView v=new TextView(getContext());v.setTypeface(face);v.setTextSize(size);v.setTextColor(ink);
        v.setIncludeFontPadding(false);return v;
    }
    private static void bind(TextView v,String text){
        if(!text.contentEquals(v.getText()))v.setText(text);
        v.setVisibility(text.isEmpty()?GONE:VISIBLE);
    }
}
