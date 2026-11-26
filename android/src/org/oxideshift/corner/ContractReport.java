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

    private static final int PAPER=GameInk.PAPER, INK=GameInk.DARK;
    private static final int OWN=0xff3c513b, RIVAL=GameInk.NAME_RED;
    private final CareerBackdrop stock=new CareerBackdrop(PAPER,0xeef2e6ca,0xc0e5d6bc);
    void setArt(CareerArt art){stock.setArt(art==null?null:art.contract);}
    private final Browse browse;
    private final TextView opponent, record, purse, plan, gesture;
    private final LinearLayout figures;
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
        opponent=type("",fonts.stencil,33,GameInk.NAME_RED);
        opponent.setPadding(dp(12),dp(5),dp(12),0);addView(opponent);
        record=type("",fonts.body,16,INK);
        record.setPadding(dp(12),0,dp(12),dp(4));addView(record);
        View rule=new View(context);rule.setBackgroundColor(0xff8e713d);
        LayoutParams ruleSize=new LayoutParams(-1,dp(1));ruleSize.setMargins(dp(12),0,dp(12),0);addView(rule,ruleSize);
        figures=new LinearLayout(context);
        figures.setOrientation(VERTICAL);
        figures.setPadding(dp(12),dp(6),dp(12),dp(6));
        addView(figures);
        purse=type("",fonts.body,14,INK);
        figures.addView(purse);
        tape=new Tape(context,fonts);
        figures.addView(tape,new LayoutParams(-1,-2));
        plan=type("",fonts.body,15,INK);
        plan.setPadding(0,dp(3),0,dp(3));
        figures.addView(plan);
        setVisibility(GONE);
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
        setClickable(canBrowse && offer!=null);
        setFocusable(canBrowse && offer!=null);
        gesture.setVisibility(canBrowse && offer!=null?VISIBLE:GONE);
        if(!entering && shown==offer)return;
        shown=offer;
        if(offer!=null && isShown() && isAttachedToWindow() && ValueAnimator.areAnimatorsEnabled()){
            opponent.animate().cancel();opponent.setTranslationX(dp(8));
            opponent.animate().translationX(0).setDuration(200).start();
        }
        opponent.setText(offer==null?"NO OFFER YET":offer.name);
        record.setVisibility(offer==null || offer.rival!=null?VISIBLE:GONE);
        if(offer==null)record.setText("Review your career options while the next opponent is pending.");
        else if(offer.rival!=null)record.setText(recordLine(offer.rival));
        figures.setVisibility(offer==null?GONE:VISIBLE);
        if(offer==null){tape.bind(null);return;}
        String details=offer.own==null?"":"Your record   "+recordLine(offer.own);
        if(offer.purse>=0)details+=(details.isEmpty()?"":"\n" )+"Total Earnings for the Fight   "+CareerDesk.money(offer.purse);
        purse.setText(details);purse.setVisibility(details.isEmpty()?GONE:VISIBLE);
        tape.bind(offer);
        String experience=offer.experience();
        plan.setText(offer.plan()+(experience.isEmpty()?"":" "+experience));
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
            reveal.setDuration(260);
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
        @Override protected void onMeasure(int widthSpec,int heightSpec){
            setMeasuredDimension(MeasureSpec.getSize(widthSpec),resolveSize(Math.round(84*sp),heightSpec));
        }
        private String score(int n){return n<0?"\u2014":Integer.toString(n);}
        private void text(Canvas c,String s,float x,float baseline,float size,int color,Paint.Align align){
            ink.setTypeface(font);ink.setTextAlign(align);ink.setColor(color);ink.setTextSize(size*sp);
            c.drawText(s,x,baseline,ink);
        }
        @Override protected void onDraw(Canvas c){
            if(data==null)return;
            float w=getWidth(),center=w*.54f;
            text(c,"TALE OF THE TAPE",0,14*sp,13,INK,Paint.Align.LEFT);
            ink.setColor(0xff8e783e);c.drawRect(0,18*sp,w,18*sp+dp(1),ink);
            for(int i=0;i<3;i++){
                float y=(34+21*i)*sp;
                int own=data.ratings[i],rival=data.ratings[i+3],edge=own-rival;
                boolean known=own>=0 && rival>=0;
                String lead=!known?"Unrated":edge==0?"Even":(edge>0?"You +":"Opponent +")+Math.abs(edge);
                int color=known && edge!=0?(edge>0?OWN:RIVAL):INK;
                text(c,labels[i],0,y,15,INK,Paint.Align.LEFT);
                text(c,lead,w,y,15,color,Paint.Align.RIGHT);
                float half=Math.min(w*.12f,42*sp),offset=half*Math.min(1f,Math.abs(edge)/25f)*fraction;
                ink.setColor(0xffa59154);c.drawRect(center-half,y-6*sp,center+half,y-3*sp,ink);
                ink.setColor(color);
                if(known)c.drawRect(center-(edge>0?offset:0),y-6*sp,center+(edge<0?offset:0),y-3*sp,ink);
                ink.setColor(INK);c.drawRect(center,y-8*sp,center+dp(1),y-sp,ink);
            }
        }
    }
}
