package org.oxideshift.corner;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.util.Arrays;
import java.util.Locale;

/** A read-only corner sheet driven by the retail trainer, estimate and result callbacks. */
final class TrainingSheet extends LinearLayout {
    private static final String[] STATS={"Power","Speed","Agility","Stamina","Chin","Heart","Cuts","Body"};
    private final TextView stage,title,cue,legend,fee,wallet;
    private final LinearLayout table,quote;
    private final TextView[] values=new TextView[8];
    private final Meter[] meters=new Meter[8];
    private final ImageView portrait;
    private Bitmap[] photos;
    private int photoIndex;
    void setArt(Bitmap[] art){
        photos=art;portrait.setImageBitmap(art==null?null:art[photoIndex]);
    }
    private CareerDesk.Page lastPage,lastDrill;
    private int[] lastRatings;

    TrainingSheet(Context context,DiscFonts fonts,boolean compact){
        super(context);setOrientation(VERTICAL);
        setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xff5a2013,0xff32170f,0xff191311}));
        FrameLayout header=new FrameLayout(context);addView(header,new LayoutParams(-1,-2));
        portrait=new ImageView(context);portrait.setScaleType(ImageView.ScaleType.CENTER_CROP);
        portrait.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        header.addView(portrait,new FrameLayout.LayoutParams(dp(compact?78:116),dp(compact?54:74),Gravity.RIGHT|Gravity.CENTER_VERTICAL));
        LinearLayout mast=new LinearLayout(context);mast.setOrientation(VERTICAL);
        mast.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xff49190e,0xfc49190e,0x0049190e}));
        mast.setPadding(dp(12),dp(6),dp(compact?64:96),dp(6));header.addView(mast);
        stage=label(fonts.body,compact?11:13,GameInk.GOLD);stage.setLetterSpacing(.09f);mast.addView(stage);
        title=label(fonts.stencil,compact?24:30,GameInk.TEXT);mast.addView(title);
        View keyline=new View(context);keyline.setBackgroundColor(0xffa64924);addView(keyline,new LayoutParams(-1,dp(2)));
        LinearLayout body=new LinearLayout(context);body.setOrientation(VERTICAL);
        body.setPadding(dp(12),dp(5),dp(12),dp(7));addView(body);
        cue=label(fonts.body,compact?13:16,GameInk.TEXT);body.addView(cue);
        quote=new LinearLayout(context);quote.setOrientation(VERTICAL);body.addView(quote);
        fee=label(fonts.stencil,30,GameInk.GOLD);wallet=label(fonts.body,17,GameInk.TEXT);
        quote.addView(fee);quote.addView(wallet);
        legend=label(fonts.stencil,compact?13:16,GameInk.GOLD);legend.setPadding(0,dp(8),0,dp(3));body.addView(legend);
        table=new LinearLayout(context);table.setOrientation(VERTICAL);body.addView(table);
        for(int i=0;i<4;i++){
            LinearLayout row=new LinearLayout(context);row.setBaselineAligned(false);table.addView(row);
            for(int col=0;col<2;col++){
                int k=i+4*col;
                LinearLayout cell=new LinearLayout(context);cell.setOrientation(VERTICAL);
                cell.setPadding(dp(6),dp(compact?1:3),dp(6),dp(compact?2:5));cell.setBackgroundColor(i%2==0?0xff332019:0xff281b16);
                LayoutParams slot=new LayoutParams(0,-2,1);slot.setMargins(0,0,dp(col==0?3:0),dp(1));row.addView(cell,slot);
                LinearLayout labels=new LinearLayout(context);labels.setGravity(Gravity.CENTER_VERTICAL);cell.addView(labels);
                TextView name=label(fonts.body,compact?13:16,GameInk.MUTED);name.setText(STATS[k]);
                labels.addView(name,new LayoutParams(0,-2,1));values[k]=label(fonts.stencil,compact?14:18,GameInk.TEXT);labels.addView(values[k]);
                meters[k]=new Meter(context);cell.addView(meters[k],new LayoutParams(-1,dp(3)));
            }
        }
        setVisibility(GONE);
    }
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView label(Typeface face,int size,int color){
        TextView v=new TextView(getContext());v.setIncludeFontPadding(false);v.setTypeface(face);
        v.setTextSize(size);v.setTextColor(color);return v;
    }
    private static void bind(TextView v,String text){
        if(!text.contentEquals(v.getText()))v.setText(text);
        v.setVisibility(text.isEmpty()?GONE:VISIBLE);
    }
    static boolean handles(CareerDesk.Page p){return p!=null && (p.is("drill") || p.is("train"));}
    void show(CareerDesk desk,CareerDesk.Page page){
        if(desk==null || !handles(page)){
            setVisibility(GONE);lastPage=null;lastDrill=null;lastRatings=null;return;
        }
        setVisibility(VISIBLE);
        boolean trainer=page.is("train") && page.index==3,result=page.is("train") && page.index==1;
        CareerDesk.Page drill=page.is("drill")?page:desk.selectedDrill();
        int picture=trainer || drill==null?0:drill.index;
        if(picture!=photoIndex){photoIndex=picture;setArt(photos);}
        bind(stage,trainer?"CAMP STAFF":result?"TRAINING RESULTS":
                page.is("drill")?"TRAINING IN SESSION":"TRAINING SELECT");
        bind(title,(trainer?"Hire a trainer":drill!=null?drill.title:result?"Session results":"Training floor").toUpperCase(Locale.ROOT));
        CareerDesk.Budget budget=trainer?desk.trainerBudget():null;
        quote.setVisibility(budget!=null?VISIBLE:GONE);
        if(budget!=null){
            bind(fee,budget.cost<0?"":budget.cost==0?"No fee":CareerDesk.money(budget.cost)+" fee");
            String balance=budget.cash<0?"":"In camp   "+CareerDesk.money(budget.cash);
            if(budget.cash>=0 && budget.cost>=0)balance+=(budget.cash>=budget.cost?"     After hiring   ":"     Short by   ")+
                    CareerDesk.money(Math.abs(budget.cash-budget.cost));
            bind(wallet,balance);
        }
        int[] ratings=trainer?null:desk.training(!result);
        bind(cue,trainer?(budget==null?"Trainer quote pending.":""):
                result?(ratings==null?"Waiting for the official session scores.":""):
                drill!=null?drill.detail:"Select a drill to see its training effects.");
        bind(legend,ratings==null?"":result?"ATTRIBUTE CHANGES":"POTENTIAL CHANGE");
        table.setVisibility(ratings==null?GONE:VISIBLE);
        if(ratings!=null && !Arrays.equals(lastRatings,ratings)){
            int peak=1;for(int n:ratings)peak=Math.max(peak,Math.abs(n));
            for(int i=0;i<8;i++){
                int n=ratings[i];values[i].setTextColor(n>0?GameInk.GOLD:n<0?0xfffa9468:GameInk.MUTED);
                bind(values[i],(n>0?"+":"")+n+"%");meters[i].set(n,peak);
            }
        }
        if((lastPage!=page || lastDrill!=drill) && isShown() && isAttachedToWindow() && ValueAnimator.areAnimatorsEnabled()){
            title.animate().cancel();title.setTranslationX(dp(12));title.setAlpha(.5f);
            title.animate().translationX(0).alpha(1).setDuration(170).start();
        }
        lastPage=page;lastDrill=drill;lastRatings=ratings;
    }
    private static final class Meter extends View {
        private final Paint ink=new Paint();
        private int value,peak=1;
        Meter(Context c){super(c);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        void set(int next,int maximum){value=next;peak=maximum;invalidate();}
        @Override protected void onDraw(Canvas c){
            float w=getWidth(),h=getHeight(),zero=w/2;
            c.drawColor(0xff503d30);ink.setColor(0xffad9077);c.drawRect(zero,0,zero+1,h,ink);
            float end=zero+zero*value/peak;ink.setColor(value<0?0xffcd653a:GameInk.GOLD);
            c.drawRect(Math.min(zero,end),0,Math.max(zero,end),h,ink);
        }
    }
}
