package org.oxideshift.corner;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** A touch-only notebook that follows the retail editor's current tab. */
final class BoxerEditorDesk extends LinearLayout {
    static final class Notebook {
        int plan, reviewed;
        void clear(){plan=0;reviewed=0;}
    }

    private static final String[] PLANS={"Outboxer","Counter","Pressure"};
    private static final String[] TACTICS={
        "Work behind the jab. Step back after the second punch and make your opponent cross the gap again.",
        "Draw a jab at the edge of range. Slip outside it and answer while the lead hand is coming back.",
        "Cut off the exit with a side step. Work the body when your opponent covers up, then bring the hook upstairs."
    };
    private static final String[] RATINGS={
        "Compare speed and stamina. Think about which one gives out first when you jab and move for a whole round.",
        "Compare speed and power. Pick whether you want a quicker reply or a heavier one when a counter opens up.",
        "Compare stamina and power. Plan around the combinations you can still finish late in a round."
    };
    private final Notebook book;
    private final CareerDesk.Page page;
    private final Typeface font;
    private final LinearLayout paper;
    private final TextView progress, note, mark;
    private final TextView[] plans=new TextView[PLANS.length];
    private final NotebookIndex index;
    /** The first editor tab (2-8) not yet marked reviewed, or -1 when all seven are. */
    static int nextUnreviewed(int reviewed){
        for(int i=2;i<=8;i++)if((reviewed&(1<<i))==0)return i;
        return -1;
    }

    BoxerEditorDesk(Context c,Typeface font,CareerDesk.Page page,Notebook book,FightBroadcast.Camp camp){
        super(c);this.font=font;this.page=page;this.book=book;
        setOrientation(VERTICAL);setBackgroundColor(0xff291c16);
        LinearLayout header=new LinearLayout(c);header.setOrientation(VERTICAL);
        header.setPadding(dp(12),dp(6),dp(12),dp(6));
        header.setBackground(new CornerInk(0xff5c2317,0xff9d4b24));addView(header);
        header.addView(text("CAREER   /   CORNER NOTEBOOK",12,GameInk.GOLD));
        header.addView(text(page.index==1?"Edit boxer":page.title,24,GameInk.TEXT));
        ScrollView scroll=new ScrollView(c);addView(scroll,new LayoutParams(-1,0,1));
        paper=new LinearLayout(c);paper.setOrientation(VERTICAL);paper.setPadding(dp(12),dp(8),dp(12),dp(6));
        scroll.addView(paper);
        if(camp!=null && camp.boxer!=null){
            FightBroadcast.Record r=camp.boxer;
            paper.addView(text("RECORD   "+r.wins+" W   "+r.losses+" L   "+r.draws+" D     /     "+r.knockouts+" KO",14,GameInk.GOLD));
        }
        if(page.index==1){
            paper.addView(text("Build checklist",20,GameInk.TEXT));
            paper.addView(text("Mark a tab reviewed as you go. Your checklist stays here until you leave Edit Boxer.",15,GameInk.MUTED));
            // The next unchecked tab gets the largest type on the page (career directive p. 15).
            int next=nextUnreviewed(book.reviewed);
            TextView label=text(next<0?"ALL TABS REVIEWED":"NEXT UP",12,GameInk.GOLD);label.setPadding(0,dp(6),0,0);paper.addView(label);
            if(next>=0)paper.addView(text(CareerDesk.find("cac",next).title,26,GameInk.TEXT));
        }
        if(page.index<=4){
            TextView heading=text("Fight plan",19,GameInk.TEXT);paper.addView(heading);
            LinearLayout row=new LinearLayout(c);row.setBaselineAligned(false);paper.addView(row);
            for(int i=0;i<plans.length;i++){
                final int choice=i;
                TextView chip=action(PLANS[i]);plans[i]=chip;
                LayoutParams lp=new LayoutParams(0,-2,1);lp.rightMargin=i==plans.length-1?0:dp(3);
                row.addView(chip,lp);
                chip.setOnClickListener(v->{book.plan=choice;update();});
            }
        }
        note=text("",16,GameInk.TEXT);note.setPadding(0,dp(7),0,dp(4));paper.addView(note);
        // The notebook index fills whatever height is left above the footer.
        scroll.setFillViewport(true);
        index=new NotebookIndex(c);
        LayoutParams indexSize=new LayoutParams(-1,dp(176),1f);indexSize.topMargin=dp(6);
        paper.addView(index,indexSize);
        LinearLayout footer=new LinearLayout(c);footer.setGravity(Gravity.CENTER_VERTICAL);
        footer.setBackgroundColor(0xff392419);footer.setPadding(dp(12),dp(3),dp(8),dp(3));addView(footer);
        progress=text("",14,GameInk.GOLD);footer.addView(progress,new LayoutParams(0,-2,1));
        mark=action("");footer.addView(mark);mark.setVisibility(page.index==1?GONE:VISIBLE);
        mark.setOnClickListener(v->{book.reviewed^=1<<page.index;update();});
        update();
    }
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView text(String label,int size,int color){
        TextView v=new TextView(getContext());v.setText(label);v.setTextSize(size);v.setTextColor(color);
        v.setTypeface(font);v.setIncludeFontPadding(false);return v;
    }
    private TextView action(String label){
        TextView v=text(label,14,GameInk.TEXT);v.setMinHeight(dp(44));v.setGravity(Gravity.CENTER);
        v.setPadding(dp(4),dp(3),dp(4),dp(3));v.setFocusable(true);return v;
    }
    private void tint(TextView v,boolean chosen){
        v.setSelected(chosen);v.setTextColor(chosen?0xff281a12:GameInk.TEXT);
        v.setBackground(new CornerInk(chosen?0xffe9b35f:0xff4c3325,chosen?0xffffcd7b:0xff895331));
    }
    private void update(){
        for(int i=0;i<plans.length;i++)if(plans[i]!=null)tint(plans[i],i==book.plan);
        String hint=page.index==4?RATINGS[book.plan]:page.index<=3?TACTICS[book.plan]:page.detail;
        if(page.index==2 || page.index==3)hint=page.detail+"\n\n"+hint;
        note.setText(hint);
        boolean checked=(book.reviewed&(1<<page.index))!=0;
        progress.setText(Integer.bitCount(book.reviewed)+" / 7 TABS REVIEWED");
        mark.setText(checked?"Reviewed  •  Undo":"Mark reviewed");tint(mark,checked);
        if(index!=null)index.bind(book.reviewed,book.plan);
    }

    /** Seven editor tabs with pencil ticks beside a seven-segment progress ring, over a faint plan watermark.
     *  Only the notebook's own state is drawn: reviewed bits, the chosen plan and the tab on screen. */
    private final class NotebookIndex extends android.view.View {
        private final android.graphics.Paint ink=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Path tick=new android.graphics.Path();
        private final android.graphics.RectF arc=new android.graphics.RectF();
        private final float dp=getResources().getDisplayMetrics().density,sp=getResources().getDisplayMetrics().scaledDensity;
        private int reviewed=-1,plan;
        private int fresh=-1;
        private float sweep=1f;
        private android.animation.ValueAnimator grow;
        NotebookIndex(Context c){super(c);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);}
        void bind(int bits,int chosen){
            int added=reviewed<0?0:bits&~reviewed;
            reviewed=bits;plan=chosen;
            fresh=added==0?-1:Integer.numberOfTrailingZeros(added);
            setContentDescription(Integer.bitCount(bits)+" of 7 tabs reviewed. Fight plan "+PLANS[chosen]+".");
            if(grow!=null)grow.cancel();
            if(fresh>=0 && SheetMotion.on() && isShown()){
                // The newly ticked segment and its pencil tick draw themselves in.
                grow=android.animation.ValueAnimator.ofFloat(0f,1f).setDuration(320);
                grow.setInterpolator(SheetMotion.settle());
                grow.addUpdateListener(a->{sweep=(float)a.getAnimatedValue();invalidate();});
                sweep=0;grow.start();
            }else sweep=1f;
            invalidate();
        }
        @Override protected void onDetachedFromWindow(){if(grow!=null)grow.cancel();sweep=1f;super.onDetachedFromWindow();}
        private void label(android.graphics.Canvas c,String s,float x,float y,float size,int color,android.graphics.Paint.Align align){
            ink.setStyle(android.graphics.Paint.Style.FILL);ink.setTypeface(font);ink.setTextSize(size*sp);ink.setColor(color);ink.setTextAlign(align);
            c.drawText(s,x,y,ink);
        }
        @Override protected void onDraw(android.graphics.Canvas c){
            if(reviewed<0)return;
            float w=getWidth(),h=getHeight();
            // Card: dark field, burnt-orange top rule, faint plan watermark.
            ink.setStyle(android.graphics.Paint.Style.FILL);ink.setColor(0xff1f0e0a);c.drawRect(0,0,w,h,ink);
            ink.setColor(GameInk.ORANGE);c.drawRect(0,0,w,2*dp,ink);
            c.save();c.rotate(-7,w*.6f,h*.55f);
            ink.setTypeface(font);ink.setTextAlign(android.graphics.Paint.Align.CENTER);ink.setTextSize(Math.min(h*.36f,w*.2f));
            ink.setColor(0x14fff8e9);c.drawText(PLANS[plan].toUpperCase(java.util.Locale.ROOT),w*.6f,h*.55f+ink.getTextSize()*.35f,ink);
            c.restore();
            label(c,"NOTEBOOK INDEX",8*dp,18*sp,12,GameInk.GOLD,android.graphics.Paint.Align.LEFT);
            // Left: the seven tabs.
            int next=nextUnreviewed(reviewed);
            float ringSpace=Math.min(w*.40f,h-30*dp);
            float listRight=w-ringSpace-8*dp,row=(h-30*dp)/7f,size=Math.max(11f,Math.min(15f,row/sp*.62f));
            for(int k=0;k<7;k++){
                int tab=k+2;boolean done=(reviewed&(1<<tab))!=0,here=page.index==tab,isNext=tab==next;
                float top=26*dp+row*k,base=top+row*.70f,box=Math.min(row*.62f,16*dp),bx=8*dp,by=top+(row-box)/2;
                ink.setStyle(android.graphics.Paint.Style.STROKE);ink.setStrokeWidth(1.5f*dp);
                ink.setColor(done?0xffe8ac32:0x99a58a6c);c.drawRect(bx,by,bx+box,by+box,ink);
                if(done){
                    // Pencil tick: a short down-stroke and a long up-stroke, drawn in on a fresh mark.
                    float reveal=tab==fresh?sweep:1f;
                    tick.reset();tick.moveTo(bx+box*.18f,by+box*.52f);tick.lineTo(bx+box*.42f,by+box*.80f);tick.lineTo(bx+box*1.05f,by-box*.10f);
                    android.graphics.PathMeasure pm=new android.graphics.PathMeasure(tick,false);
                    android.graphics.Path part=new android.graphics.Path();pm.getSegment(0,pm.getLength()*reveal,part,true);
                    ink.setStrokeWidth(2.6f*dp);ink.setStrokeCap(android.graphics.Paint.Cap.ROUND);ink.setStrokeJoin(android.graphics.Paint.Join.ROUND);
                    ink.setColor(0xffffd98a);c.drawPath(part,ink);
                }
                String title=CareerDesk.find("cac",tab).title;
                int color=here?GameInk.GOLD:done?GameInk.MUTED:GameInk.TEXT;
                label(c,title,bx+box+8*dp,base,size,color,android.graphics.Paint.Align.LEFT);
                ink.setTypeface(font);ink.setTextSize(size*sp);float tw=ink.measureText(title);
                if(here){ink.setStyle(android.graphics.Paint.Style.FILL);ink.setColor(GameInk.GOLD);
                    c.drawRect(bx+box+8*dp,base+3*dp,bx+box+8*dp+tw,base+4.5f*dp,ink);}
                if(isNext && bx+box+16*dp+tw+40*dp<listRight)
                    label(c,"NEXT",bx+box+14*dp+tw,base,Math.max(9f,size-4),0xffef9979,android.graphics.Paint.Align.LEFT);
            }
            // Right: seven-segment ring with the count.
            float r=Math.min(ringSpace*.42f,(h-30*dp)*.42f),cx=w-ringSpace/2f-4*dp,cy=26*dp+(h-30*dp)/2f;
            arc.set(cx-r,cy-r,cx+r,cy+r);
            ink.setStyle(android.graphics.Paint.Style.STROKE);ink.setStrokeCap(android.graphics.Paint.Cap.BUTT);
            float thick=Math.max(6*dp,r*.18f);ink.setStrokeWidth(thick);
            for(int k=0;k<7;k++){
                int tab=k+2;boolean done=(reviewed&(1<<tab))!=0;
                float start=-90+k*(360f/7)+3,span=360f/7-6;
                ink.setColor(0xff4a2419);c.drawArc(arc,start,span,false,ink);
                if(done){ink.setColor(0xffe8ac32);c.drawArc(arc,start,span*(tab==fresh?sweep:1f),false,ink);}
                if(page.index==tab){ink.setStrokeWidth(2*dp);ink.setColor(0xffffd98a);
                    float outer=r+thick/2+3*dp;android.graphics.RectF o=new android.graphics.RectF(cx-outer,cy-outer,cx+outer,cy+outer);
                    c.drawArc(o,start,span,false,ink);ink.setStrokeWidth(thick);}
            }
            int count=Integer.bitCount(reviewed);
            ink.setShadowLayer(2*dp,0,dp,0xff0a0504);
            label(c,count+"/7",cx,cy+r*.18f,Math.max(14f,r*.55f/sp),GameInk.TEXT,android.graphics.Paint.Align.CENTER);
            ink.clearShadowLayer();
            label(c,"REVIEWED",cx,cy+r*.18f+Math.max(12*sp,r*.30f),Math.max(9f,Math.min(12f,r*.20f/sp)),GameInk.MUTED,android.graphics.Paint.Align.CENTER);
        }
    }
}
