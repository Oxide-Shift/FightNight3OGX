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
    private final TextView department,price,wallet,remainder,notes,state;
    private final LinearLayout totals;
    private final CampPoster poster;
    static final int FIELD=0xff1f0e0a;
    StoreTicket(Context ctx,DiscFonts fonts){
        super(ctx);setOrientation(VERTICAL);setBackgroundColor(FIELD);
        LinearLayout mast=new LinearLayout(ctx);mast.setOrientation(VERTICAL);mast.setBackground(header);
        mast.setGravity(Gravity.CENTER_VERTICAL);mast.setPadding(dp(14),dp(8),dp(14),dp(8));
        mast.setMinimumHeight(dp(74));addView(mast,new LayoutParams(-1,-2));
        department=type(fonts.stencil,14,GameInk.GOLD);department.setLetterSpacing(.08f);mast.addView(department);
        TextView title=type(fonts.stencil,32,0xffffdfa1);title.setText("FIGHT STORE");
        title.setShadowLayer(dp(2),1,2,0xff230c07);mast.addView(title);
        View rule=new View(ctx);rule.setBackgroundColor(GameInk.ORANGE);addView(rule,new LayoutParams(-1,dp(2)));
        // Shop ticket: the price is the largest figure; the receipt below answers "what will this leave me?"
        LinearLayout tag=new LinearLayout(ctx);tag.setGravity(Gravity.CENTER_VERTICAL);
        tag.setBackground(new CornerInk(0xff2a120c,GameInk.ORANGE));
        tag.setPadding(dp(14),dp(8),dp(14),dp(8));addView(tag,new LayoutParams(-1,-2));
        TextView label=type(fonts.stencil,15,GameInk.GOLD);label.setText("ITEM PRICE");label.setLetterSpacing(.12f);
        tag.addView(label,new LayoutParams(0,-2,1));
        price=type(fonts.stencil,40,GameInk.TEXT);price.setShadowLayer(dp(2),0,dp(1),0xff0a0504);tag.addView(price);
        state=type(fonts.stencil,13,GameInk.GOLD);state.setLetterSpacing(.1f);state.setPadding(dp(14),dp(6),dp(14),0);addView(state);
        totals=new LinearLayout(ctx);totals.setOrientation(VERTICAL);
        totals.setPadding(dp(14),dp(6),dp(14),dp(6));addView(totals);
        wallet=type(fonts.body,17,GameInk.MUTED);totals.addView(wallet);
        remainder=type(fonts.stencil,22,GameInk.TEXT);remainder.setPadding(0,dp(3),0,0);totals.addView(remainder);
        notes=type(fonts.body,16,GameInk.TEXT);notes.setPadding(dp(14),dp(4),dp(14),dp(8));addView(notes);
        poster=new CampPoster(ctx,fonts);poster.setBackgroundColor(FIELD);
        addView(poster,new LayoutParams(-1,dp(150),1f));
    }
    void setArt(CareerArt art){header.setArt(art==null?null:art.store);}
    void show(CareerDesk.Page page,CareerDesk.Budget quote){
        int cost=quote==null?-1:quote.cost,cash=quote==null?-1:quote.cash;
        bind(department,page.index==1?"CAREER EQUIPMENT":page.title.toUpperCase(Locale.ROOT));
        // The price rolls between items as the player browses gear.
        price.setVisibility(VISIBLE);SheetMotion.rollMoney(price,cost>=0?CareerDesk.money(cost):"\u2014");
        // Preview never deducts; the purchase desk only confirms upstairs (career directive p. 20).
        bind(state,page.index==3?"PREVIEW  ·  NOT PURCHASED":page.index==2?"CONFIRM ON THE UPPER SCREEN":"");
        totals.setVisibility(cash>=0?VISIBLE:GONE);
        bind(wallet,cash>=0?"Available cash   "+CareerDesk.money(cash):"");
        remainder.setTextColor(cost>cash?TrainingSheet.LOSS:GameInk.TEXT);
        bind(remainder,cost<0 || cash<0?"":(cost>cash?"SHORT BY   ":"AFTER PURCHASE   ")+CareerDesk.money(Math.abs(cash-cost)));
        bind(notes,quote==null || (cost<0 && cash<0)?page.note:page.detail);
        poster.showQuote(quote,true);
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
