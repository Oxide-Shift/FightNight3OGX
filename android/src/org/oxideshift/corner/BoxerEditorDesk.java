package org.oxideshift.corner;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import static org.oxideshift.corner.CornerControls.Key;

/** A local comparison sheet for the retail career editor. Pinning never changes the boxer or save. */
final class BoxerEditorDesk extends LinearLayout {
    static final class References {
        final Bitmap[] pages=new Bitmap[9];
        void clear(){java.util.Arrays.fill(pages,null);}
    }
    final CareerDesk.Page page;
    private final References refs;
    private final Typeface stencil;
    private final CornerControls.Output output;
    private final LinearLayout sheet,footer;
    private final FrameLayout body;
    private final TextView title,section;
    private TextView pin;
    private MenuPrompt editName;
    private Proof proof;
    private boolean nameSelected;
    private boolean ratingsGuide;
    private FightBroadcast.Record campRecord;
    private TextView record;
    boolean help,dialog;

    BoxerEditorDesk(Context c,Typeface stencil,CareerDesk.Page page,References refs,CornerControls.Output output){
        super(c);this.stencil=stencil;this.page=page;this.refs=refs;this.output=output;
        setOrientation(VERTICAL);setBackgroundColor(0xff24170f);
        LinearLayout head=new LinearLayout(c);head.setOrientation(VERTICAL);head.setPadding(dp(12),dp(5),dp(12),dp(4));
        head.setBackground(new CornerInk(0xff542213,0xff923b1c));addView(head,new LayoutParams(-1,-2));
        section=text("CAREER  /  EDIT BOXER",12,GameInk.GOLD);head.addView(section);
        title=text("",24,GameInk.TEXT);head.addView(title);
        body=new FrameLayout(c);addView(body,new LayoutParams(-1,0,1));
        sheet=new LinearLayout(c);sheet.setOrientation(VERTICAL);sheet.setPadding(dp(8),dp(4),dp(8),dp(2));
        footer=new LinearLayout(c);footer.setGravity(Gravity.CENTER);footer.setBackgroundColor(0xff321a11);
        addView(footer,new LayoutParams(-1,-2));show(false);
    }
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView text(String value,int sp,int color){
        TextView v=new TextView(getContext());v.setText(value);v.setTypeface(stencil);v.setTextColor(color);
        v.setTextSize(sp);v.setIncludeFontPadding(false);return v;
    }
    private void prompt(LinearLayout row,PadIcon.Cap cap,String label,Key key){
        MenuPrompt v=new MenuPrompt(getContext(),cap,label,stencil,()->output.press(key));
        row.addView(v,new LayoutParams(0,-2,1));if(key==Key.A && page.index==2 && !dialog)editName=v;
    }
    void nameField(boolean selected){
        nameSelected=selected;if(editName!=null)editName.setVisibility(selected?VISIBLE:GONE);
    }
    void showDialog(){dialog=true;show(false);}
    void camp(FightBroadcast.Camp camp){
        campRecord=camp==null?null:camp.boxer;
        showRecord();
    }
    private void showRecord(){
        if(record==null)return;
        record.setVisibility(campRecord==null?GONE:VISIBLE);
        if(campRecord!=null)record.setText("CAREER RECORD   "+campRecord.wins+" W   "+campRecord.losses+" L   "+
                campRecord.draws+" D   /   "+campRecord.knockouts+" KO");
    }
    void show(boolean expanded){
        help=expanded;proof=null;pin=null;editName=null;record=null;
        body.removeAllViews();sheet.removeAllViews();footer.removeAllViews();
        if(sheet.getParent()!=null)((ViewGroup)sheet.getParent()).removeView(sheet);
        title.setText(dialog?"Confirm changes":expanded?"Editor controls":page.title);
        if(help){
            ScrollView scroller=new ScrollView(getContext());scroller.addView(sheet);body.addView(scroller);
            sheet.addView(text(page.note+" "+page.detail,16,GameInk.TEXT));
            if(page.index>1){
                LinearLayout tabs=row();prompt(tabs,PadIcon.Cap.LT,"Previous tab",Key.LT);prompt(tabs,PadIcon.Cap.RT,"Next tab",Key.RT);
                LinearLayout rotate=row();prompt(rotate,PadIcon.Cap.LB,"Rotate left",Key.LB);prompt(rotate,PadIcon.Cap.RB,"Rotate right",Key.RB);
                if(page.index!=4)prompt(row(),PadIcon.Cap.R3,"Randomize",Key.R3);
                if(page.index==7)prompt(row(),PadIcon.Cap.X,"Highlight",Key.X);
            }
            prompt(footer,PadIcon.Cap.B,"Close guide",Key.B);return;
        }
        if(page.index==1 && !dialog){
            ScrollView scroll=new ScrollView(getContext());scroll.addView(sheet);body.addView(scroll);
            record=text("",14,GameInk.GOLD);sheet.addView(record);
            showRecord();
            LinearLayout tabs=row();
            for(int i=0;i<2;i++){
                final boolean ratings=i==1;
                TextView tab=text(ratings?"Ratings":"Appearance",17,ratings==ratingsGuide?GameInk.GOLD:GameInk.TEXT);
                tab.setGravity(Gravity.CENTER);tab.setMinHeight(dp(44));tab.setFocusable(true);
                tab.setSelected(ratings==ratingsGuide);
                tab.setBackground(new CornerInk(ratings==ratingsGuide?0xff654022:0xff37251b,0xff95542d));
                tab.setOnClickListener(v->{if(ratingsGuide!=ratings){ratingsGuide=ratings;show(false);}});
                tabs.addView(tab,new LayoutParams(0,-2,1));
            }
            TextView heading=text(ratingsGuide?"Compare your allocation":"Compare two looks",22,GameInk.TEXT);
            heading.setPadding(dp(8),dp(12),dp(8),dp(3));sheet.addView(heading);
            TextView note=text(ratingsGuide?"Open Fighter ratings and pin the sheet before spending points. The original values stay alongside your current allocation.":
                    "Choose your boxer above. On an appearance tab, tap Pin reference before changing a feature. Rotate back to the same angle to compare.",16,GameInk.MUTED);
            note.setPadding(dp(8),0,dp(8),dp(6));sheet.addView(note);
            prompt(footer,PadIcon.Cap.A,"Select",Key.A);prompt(footer,PadIcon.Cap.B,"Back",Key.B);
            prompt(footer,PadIcon.Cap.Y,"Guide",Key.Y);return;
        }
        body.addView(sheet,new FrameLayout.LayoutParams(-1,-1));
        boolean compare=!dialog && page.index>1;
        if(compare){
            LinearLayout tools=row();tools.setGravity(Gravity.CENTER_VERTICAL);
            String label=page.index==4?"RATINGS SHEET":page.index>=7?"FACE STUDY":"FIGHTER STUDY";
            tools.addView(text(label,14,GameInk.GOLD),new LayoutParams(0,-2,1));
            pin=text("",14,GameInk.TEXT);pin.setGravity(Gravity.CENTER);pin.setPadding(dp(12),0,dp(12),0);
            pin.setMinHeight(dp(44));pin.setFocusable(true);pin.setBackground(new CornerInk(0xff583520,0xffb07841));
            tools.addView(pin);pin.setEnabled(false);
            pin.setOnClickListener(v->{
                if(proof==null || proof.live==null)return;
                refs.pages[page.index]=refs.pages[page.index]==null?proof.live.copy(Bitmap.Config.ARGB_8888,false):null;
                updatePin();proof.invalidate();
            });
            updatePin();
        }
        proof=new Proof(getContext());sheet.addView(proof,new LayoutParams(-1,0,1));
        if(dialog){prompt(footer,PadIcon.Cap.A,"Select",Key.A);return;}
        if(page.index==2){prompt(footer,PadIcon.Cap.A,"Edit name",Key.A);nameField(nameSelected);}
        prompt(footer,PadIcon.Cap.START,"Accept",Key.START);
        prompt(footer,PadIcon.Cap.Y,"Guide",Key.Y);
    }
    private void updatePin(){
        pin.setText(refs.pages[page.index]==null?"PIN REFERENCE":"CLEAR REFERENCE");
        pin.setContentDescription(pin.getText());
    }
    private LinearLayout row(){LinearLayout line=new LinearLayout(getContext());sheet.addView(line,new LayoutParams(-1,-2));return line;}
    void observe(LiveFrame f){if(proof!=null)proof.observe(f);}

    private final class Proof extends View {
        final Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        final RectF dst=new RectF();
        Bitmap live;
        int[] pixels;
        int sequence=-1;
        long last;
        Proof(Context c){super(c);ink.setTypeface(stencil);setContentDescription(dialog?"Current confirmation":page.title+" live view");}
        void observe(LiveFrame f){
            long now=android.os.SystemClock.uptimeMillis();
            if(sequence==f.sequence || (live!=null && now-last<160))return;
            sequence=f.sequence;last=now;
            // Coordinates are in the guest's 640x480 layout. LiveFrame maps both display aspect ratios.
            int x=300,y=150,w=254,h=265;
            if(dialog){x=121;y=163;w=398;h=154;}
            else if(page.index==4){x=53;y=165;w=257;h=244;}
            else if(page.index>=7){x=296;y=149;w=259;h=222;}
            if(live==null){live=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);pixels=new int[w*h];}
            for(int j=0;j<h;j++)for(int i=0;i<w;i++)pixels[j*w+i]=f.at(x+i,y+j);
            live.setPixels(pixels,0,w,0,0,w,h);if(pin!=null)pin.setEnabled(true);invalidate();
        }
        @Override protected void onDraw(Canvas c){
            if(live==null)return;
            Bitmap ref=dialog?null:refs.pages[page.index];
            float split=ref==null?0:(getWidth()+dp(4))*.5f;
            if(ref!=null)photo(c,ref,0,split-dp(4),"PINNED");
            photo(c,live,split,getWidth(),dialog?"":"CURRENT");
        }
        private void photo(Canvas c,Bitmap image,float left,float right,String caption){
            float band=caption.isEmpty()?0:dp(20),h=getHeight()-band;
            if(right<=left || h<=0)return;
            ink.setColor(0xff170e0b);c.drawRect(left,0,right,getHeight(),ink);
            float scale=Math.min((right-left)/image.getWidth(),h/image.getHeight());
            float x=(left+right-image.getWidth()*scale)*.5f,y=(h-image.getHeight()*scale)*.5f;
            dst.set(x,y,x+image.getWidth()*scale,y+image.getHeight()*scale);
            ink.setColor(-1);c.drawBitmap(image,null,dst,ink);
            if(!caption.isEmpty()){
                ink.setColor(GameInk.ORANGE);c.drawRect(left,h,right,h+dp(1),ink);
                ink.setColor(GameInk.TEXT);ink.setTextSize(dp(12));c.drawText(caption,left+dp(7),h+dp(15),ink);
            }
        }
    }
}
