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
            StringBuilder items=new StringBuilder();
            for(int i=2;i<=8;i++){
                if((book.reviewed&(1<<i))!=0)continue;
                if(items.length()>0)items.append("   /   ");
                items.append(CareerDesk.find("cac",i).title);
            }
            if(items.length()>0){
                paper.addView(text("STILL TO REVIEW",12,GameInk.GOLD));
                paper.addView(text(items.toString(),16,GameInk.TEXT));
            }
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
    }
}
