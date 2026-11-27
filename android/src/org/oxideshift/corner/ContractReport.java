package org.oxideshift.corner;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.LinearLayout;
import android.widget.TextView;

/** The lower-screen copy of the contract currently highlighted in the game. */
final class ContractReport extends LinearLayout {
    interface Browse { boolean step(boolean forward); }

    // Dark burnt-red field with cream type: the cream paper read too bright beside the retail menus.
    private static final int FIELD=0xff1f0e0a, INK=GameInk.TEXT;
    private static final int RIVAL=0xffef6b4a;
    private final CareerBackdrop stock=new CareerBackdrop(FIELD,0xe0451a10,0xec170b08);
    void setArt(CareerArt art){stock.setArt(art==null?null:art.contract);}
    private final Browse browse;
    private final TextView opponent, record, purse, plan, gesture;
    private final TextView purseValue, bonusValue, objective;
    private final LinearLayout figures, stakes, bonusCell, objectiveRow;
    private final Tape tape;
    private final int slop;
    private float downX,downY;
    private boolean horizontal,tracking,canBrowse;
    private CareerDesk.Offer shown;

    ContractReport(Context context, DiscFonts fonts, Browse browse){
        super(context);
        this.browse=browse;
        slop=ViewConfiguration.get(context).getScaledTouchSlop();
        setOrientation(VERTICAL);
        setBackground(stock);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        LinearLayout mast=new LinearLayout(context);
        mast.setOrientation(VERTICAL);
        mast.setPadding(dp(12),dp(4),dp(12),dp(4));
        mast.setBackground(new CornerInk(0xff3d160e,GameInk.ORANGE));
        addView(mast);
        TextView label=type("SCOUTING REPORT",fonts.stencil,16,GameInk.GOLD);
        label.setLetterSpacing(.12f);
        LinearLayout top=new LinearLayout(context);
        top.setGravity(Gravity.CENTER_VERTICAL);
        mast.addView(top);
        top.addView(label,new LayoutParams(0,-2,1));
        gesture=type("Swipe for offers\nTap for full report",fonts.body,13,GameInk.TEXT);
        gesture.setGravity(Gravity.END);gesture.setPadding(dp(8),0,0,0);
        top.addView(gesture);
        opponent=type("",fonts.stencil,33,RIVAL);
        opponent.setPadding(dp(12),dp(5),dp(12),0);addView(opponent);
        record=type("",fonts.body,16,GameInk.MUTED);
        record.setPadding(dp(12),0,dp(12),dp(4));addView(record);
        // The money and the bonus condition are what a contract is chosen on: largest type after the name.
        stakes=new LinearLayout(context);
        stakes.setOrientation(VERTICAL);
        stakes.setPadding(dp(12),dp(6),dp(12),dp(7));
        stakes.setBackground(new CornerInk(0xff2a120c,GameInk.ORANGE));
        LinearLayout money=new LinearLayout(context);
        stakes.addView(money);
        LinearLayout purseCell=cell(context,fonts,"PURSE");
        purseValue=(TextView)purseCell.getChildAt(1);
        money.addView(purseCell,new LayoutParams(0,-2,1));
        bonusCell=cell(context,fonts,"BONUS");
        bonusValue=(TextView)bonusCell.getChildAt(1);
        LayoutParams bonusSize=new LayoutParams(0,-2,1);bonusSize.setMarginStart(dp(10));
        money.addView(bonusCell,bonusSize);
        objectiveRow=new LinearLayout(context);
        objectiveRow.setOrientation(VERTICAL);
        objectiveRow.setPadding(0,dp(6),0,0);
        TextView objectiveLabel=type("BONUS OBJECTIVE",fonts.stencil,13,GameInk.GOLD);
        objectiveLabel.setLetterSpacing(.1f);
        objectiveRow.addView(objectiveLabel);
        objective=type("",fonts.body,21,GameInk.TEXT);
        objective.setPadding(0,dp(2),0,0);
        objectiveRow.addView(objective);
        stakes.addView(objectiveRow);
        addView(stakes,new LayoutParams(-1,-2));
        // The matchup continues the same broadcast band: stakes above, tale of the tape below.
        tape=new Tape(context,fonts);
        addView(tape,new LayoutParams(-1,-2));
        figures=new LinearLayout(context);
        figures.setOrientation(VERTICAL);
        figures.setPadding(dp(12),dp(6),dp(12),dp(6));
        addView(figures);
        purse=type("",fonts.body,14,GameInk.MUTED);
        figures.addView(purse);
        plan=type("",fonts.body,15,INK);
        plan.setPadding(0,dp(3),0,dp(3));
        figures.addView(plan);
        setVisibility(GONE);
    }

    private LinearLayout cell(Context context,DiscFonts fonts,String title){
        LinearLayout cell=new LinearLayout(context);
        cell.setOrientation(VERTICAL);
        TextView label=type(title,fonts.stencil,13,GameInk.GOLD);
        label.setLetterSpacing(.12f);
        cell.addView(label);
        TextView value=type("",fonts.stencil,36,GameInk.TEXT);
        value.setMaxLines(1);
        // Long amounts shrink to the column instead of wrapping or clipping.
        value.setAutoSizeTextTypeUniformWithConfiguration(18,36,1,android.util.TypedValue.COMPLEX_UNIT_SP);
        cell.addView(value,new LayoutParams(-1,Math.round(40*getResources().getDisplayMetrics().scaledDensity)));
        return cell;
    }
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView type(String text,Typeface font,int size,int color){
        TextView v=new TextView(getContext());
        v.setTypeface(font);v.setTextColor(color);v.setTextSize(size);v.setText(text);
        v.setIncludeFontPadding(false);
        return v;
    }
    void show(CareerDesk.Page page,CareerDesk.Offer offer){
        if(page==null || !page.is("mcon")){
            setVisibility(GONE);shown=null;canBrowse=false;return;
        }
        boolean entering=getVisibility()!=VISIBLE;
        canBrowse=page.index==1;
        setVisibility(VISIBLE);
        // Posted so the sections below are bound and laid out before they rise in.
        if(entering)post(()->SheetMotion.enter(this));
        setClickable(canBrowse && offer!=null);
        setFocusable(canBrowse && offer!=null);
        gesture.setVisibility(canBrowse && offer!=null?VISIBLE:GONE);
        if(!entering && shown==offer)return;
        shown=offer;
        if(offer!=null && isShown() && isAttachedToWindow() && ValueAnimator.areAnimatorsEnabled()){
            SheetMotion.nudge(opponent,1);
        }
        opponent.setText(offer==null?"NO OFFER YET":offer.name);
        record.setVisibility(offer==null || offer.rival!=null?VISIBLE:GONE);
        if(offer==null)record.setText("Review your career options while the next opponent is pending.");
        else if(offer.rival!=null)record.setText(recordLine(offer.rival));
        figures.setVisibility(offer==null?GONE:VISIBLE);
        tape.setVisibility(offer==null?GONE:VISIBLE);
        bindStakes(offer);
        if(offer==null){tape.bind(null);return;}
        String details=offer.own==null?"":"Your record   "+recordLine(offer.own);
        purse.setText(details);purse.setVisibility(details.isEmpty()?GONE:VISIBLE);
        tape.bind(offer);
        String experience=offer.experience();
        plan.setText(offer.plan()+(experience.isEmpty()?"":" "+experience));
    }

    /** Only text the game itself sent: no bonus cell until the contract screen reports one. */
    private void bindStakes(CareerDesk.Offer offer){
        String amount=offer==null?"":offer.purseText();
        CareerDesk.Terms terms=offer==null?null:offer.terms;
        boolean bonus=terms!=null && terms.hasBonus();
        stakes.setVisibility(amount.isEmpty() && !bonus?GONE:VISIBLE);
        SheetMotion.rollMoney(purseValue,amount);
        ((View)purseValue.getParent()).setVisibility(amount.isEmpty()?GONE:VISIBLE);
        bonusCell.setVisibility(bonus?VISIBLE:GONE);
        SheetMotion.rollMoney(bonusValue,bonus?terms.bonus:"");
        boolean condition=bonus && !terms.objective.isEmpty();
        objectiveRow.setVisibility(condition?VISIBLE:GONE);
        objective.setText(condition?terms.objective:"");
        StringBuilder spoken=new StringBuilder();
        if(!amount.isEmpty())spoken.append("Purse ").append(amount).append(". ");
        if(bonus)spoken.append("Bonus ").append(terms.bonus).append(". ");
        if(condition)spoken.append("Bonus objective: ").append(terms.objective).append('.');
        stakes.setContentDescription(spoken.toString().trim());
    }
    /** Tale of the tape caption. Both numbers sit on one fixed 0-100 axis with the player on the left;
     *  a missing side suppresses the difference and gaps under five read as close (career directive p. 6). */
    static String lead(int own,int rival){
        if(own<0 || rival<0)return "Unrated";
        int edge=own-rival;
        if(Math.abs(edge)<5)return edge==0?"Even":"Close";
        return (edge>0?"You +":"Opp. +")+Math.abs(edge);
    }
    private static String recordLine(FightBroadcast.Record r){
        return r.wins+"-"+r.losses+"-"+r.draws+"   "+r.knockouts+" KO";
    }
    @Override public boolean onTouchEvent(MotionEvent event){
        if(!canBrowse)return super.onTouchEvent(event);
        float dx=event.getX()-downX,dy=event.getY()-downY;
        switch(event.getActionMasked()){
        case MotionEvent.ACTION_DOWN:
            downX=event.getX();downY=event.getY();horizontal=false;tracking=true;return true;
        case MotionEvent.ACTION_MOVE:
            if(tracking && Math.abs(dx)>slop && Math.abs(dx)>Math.abs(dy)*1.4f){
                horizontal=true;getParent().requestDisallowInterceptTouchEvent(true);
            }else if(!horizontal && Math.abs(dy)>slop)tracking=false;
            return true;
        case MotionEvent.ACTION_UP:
            if(tracking && horizontal && Math.abs(dx)>=Math.max(dp(30),2*slop) && Math.abs(dx)>Math.abs(dy)*1.4f)
                browse.step(dx<0);
            else if(tracking && !horizontal && Math.abs(dx)<=slop && Math.abs(dy)<=slop)performClick();
            // Fall through to release gesture ownership.
        case MotionEvent.ACTION_CANCEL:
            horizontal=false;tracking=false;getParent().requestDisallowInterceptTouchEvent(false);return true;
        default:return true;
        }
    }
    @Override public boolean performClick(){return super.performClick();}
    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info){
        super.onInitializeAccessibilityNodeInfo(info);
        if(canBrowse){
            info.setScrollable(true);
            if(shown!=null)info.addAction(new AccessibilityNodeInfo.AccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK,"Open full scouting report"));
            info.addAction(new AccessibilityNodeInfo.AccessibilityAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD,"Next offer"));
            info.addAction(new AccessibilityNodeInfo.AccessibilityAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD,"Previous offer"));
        }
    }
    @Override public boolean performAccessibilityAction(int action,Bundle arguments){
        if(canBrowse && (action==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD || action==AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD))
            return browse.step(action==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
        return super.performAccessibilityAction(action,arguments);
    }

    private final class Tape extends View {
        private final Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Typeface font;
        private final String[] labels={"Power","Speed","Stamina"};
        private final float sp=getResources().getDisplayMetrics().scaledDensity;
        private final ValueAnimator reveal=ValueAnimator.ofFloat(0f,1f);
        private float fraction=1f;
        private CareerDesk.Offer data;
        Tape(Context ctx,DiscFonts fonts){
            super(ctx);font=fonts.stencil;
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
            // Rows reveal one after another (see onDraw), so the whole sweep runs a little longer.
            reveal.setDuration(560);reveal.setInterpolator(SheetMotion.settle());
            reveal.addUpdateListener(a->{fraction=(float)a.getAnimatedValue();invalidate();});
        }
        void bind(CareerDesk.Offer next){
            data=next;
            reveal.cancel();fraction=1;
            if(next!=null){
                StringBuilder description=new StringBuilder("Your corner compared with "+next.name+". ");
                for(int i=0;i<3;i++)description.append(labels[i]).append(": ").append(score(next.ratings[i]))
                    .append(" to ").append(score(next.ratings[i+3])).append(". ");
                setContentDescription(description);
                if(isShown() && isAttachedToWindow() && ValueAnimator.areAnimatorsEnabled())reveal.start();
            }
            invalidate();
        }
        @Override protected void onVisibilityChanged(View changed,int visibility){
            super.onVisibilityChanged(changed,visibility);
            if(reveal!=null && visibility!=VISIBLE){reveal.cancel();fraction=1;}
        }
        @Override protected void onDetachedFromWindow(){reveal.cancel();fraction=1;super.onDetachedFromWindow();}
        /** Header plus three rows; each row is a value line and a shared 0-100 rail. */
        static final int HEADER_SP=30,ROW_SP=36,FOOT_SP=8;
        static final int FIELD=0xff1d0d09,BAND=0xff3d160e,TRACK=0xff684237,YOU=GameInk.GOLD,OPP=0xffef9979;
        private final android.graphics.Path wear=new android.graphics.Path();
        @Override protected void onMeasure(int widthSpec,int heightSpec){
            setMeasuredDimension(MeasureSpec.getSize(widthSpec),resolveSize(Math.round((HEADER_SP+3*ROW_SP+FOOT_SP)*sp),heightSpec));
        }
        @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){
            super.onSizeChanged(w,h,oldw,oldh);
            // Film-edge perforations down the left margin, as on Career Central's matchup card.
            wear.reset();
            for(float y=dp(6);y<h-dp(6);y+=dp(14))wear.addRoundRect(dp(2),y,dp(6),y+dp(7),dp(1),dp(1),android.graphics.Path.Direction.CW);
        }
        private String score(int n){return n<0?"\u2014":Integer.toString(n);}
        private void text(Canvas c,String s,float x,float baseline,float size,int color,Paint.Align align){
            ink.setTypeface(font);ink.setTextAlign(align);ink.setColor(color);ink.setTextSize(size*sp);
            c.drawText(s,x,baseline,ink);
        }
        @Override protected void onDraw(Canvas c){
            if(data==null)return;
            float w=getWidth(),h=getHeight(),left=dp(16),right=w-dp(12);
            int text=GameInk.TEXT,muted=GameInk.MUTED;
            // Black broadcast canvas with an oxide header strip and burnt-orange rules, matching the stakes band.
            ink.setShader(null);ink.setColor(FIELD);c.drawRect(0,0,w,h,ink);
            ink.setColor(BAND);c.drawRect(0,0,w,22*sp,ink);
            ink.setColor(GameInk.ORANGE);c.drawRect(0,0,w,dp(2),ink);c.drawRect(0,22*sp,w,22*sp+dp(1),ink);
            ink.setColor(0xff681e14);c.drawRect(0,0,dp(8),h,ink);
            ink.setColor(0xff130c09);c.drawPath(wear,ink);
            ink.setLetterSpacing(.12f);
            float split=left+(right-left)*.66f;
            ink.setTypeface(font);ink.setTextSize(13*sp);
            float room=split-ink.measureText("YOU")-dp(12)-left;
            ink.setTextSize(14*sp);
            float titleWidth=ink.measureText("TALE OF THE TAPE");
            // Large text on a narrow screen: shrink the title rather than let it run into the YOU heading.
            text(c,"TALE OF THE TAPE",left,16*sp,titleWidth>room && titleWidth>0?14*room/titleWidth:14,YOU,Paint.Align.LEFT);
            text(c,"YOU",split,16*sp,13,YOU,Paint.Align.RIGHT);
            text(c,"OPP.",right,16*sp,13,OPP,Paint.Align.RIGHT);
            ink.setLetterSpacing(0f);
            float railLeft=left+(right-left)*.30f,railRight=right;
            for(int i=0;i<3;i++){
                float top=(HEADER_SP+ROW_SP*i)*sp,base=top+17*sp,rail=top+27*sp;
                int own=data.ratings[i],rival=data.ratings[i+3],edge=own-rival;
                boolean known=own>=0 && rival>=0,close=known && Math.abs(edge)<5;
                int ownInk=known && !close && edge>0?YOU:text,rivalInk=known && !close && edge<0?OPP:text;
                ink.setLetterSpacing(.06f);
                text(c,labels[i].toUpperCase(java.util.Locale.ROOT),left,base,16,text,Paint.Align.LEFT);
                ink.setLetterSpacing(0f);
                text(c,lead(own,rival),railLeft-dp(8),rail+4*sp,13,close || !known?muted:edge>0?YOU:OPP,Paint.Align.RIGHT);
                ink.setShadowLayer(1.5f*dp(1),0,dp(1),0xff0a0504);
                text(c,score(own),split,base,20,ownInk,Paint.Align.RIGHT);
                text(c,score(rival),right,base,20,rivalInk,Paint.Align.RIGHT);
                ink.clearShadowLayer();
                // Shared 0-100 rail with quarter ticks; both markers grow in with the reveal.
                ink.setColor(TRACK);c.drawRect(railLeft,rail-dp(2),railRight,rail+dp(2),ink);
                for(int q=0;q<=4;q++){float x=railLeft+(railRight-railLeft)*q/4f;c.drawRect(x-dp(1)/2f,rail-dp(4),x+dp(1)/2f,rail+dp(4),ink);}
                float row=Math.max(0f,Math.min(1f,(fraction-.18f*i)/.64f));
                float o=own<0?-1:railLeft+(railRight-railLeft)*Math.min(100,own)/100f*row;
                float r=rival<0?-1:railLeft+(railRight-railLeft)*Math.min(100,rival)/100f*row;
                if(known && !close){ink.setColor(edge>0?0xffe8ac32:0xffd85a3b);c.drawRect(Math.min(o,r),rail-dp(3),Math.max(o,r),rail+dp(3),ink);}
                if(own>=0){ink.setColor(YOU);c.drawRect(o-dp(2.5f),rail-dp(7),o+dp(2.5f),rail+dp(7),ink);}
                if(rival>=0){ink.setColor(OPP);c.drawCircle(r,rail,dp(5),ink);}
            }
        }
    }
}
