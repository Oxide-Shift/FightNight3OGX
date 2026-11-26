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
    /** One locked scale for every drill (career directive p. 7): bars no longer stretch to each table's own peak. */
    static final int SCALE=10;
    // Dark burnt-red sheet, matching the scouting report.
    static final int FIELD=0xff1f0e0a,STRIPE=0xff2b130d,GAIN=0xff9fd27a,LOSS=0xffef6b4a;
    private final TextView stage,title,cue,legend,fee,wallet;
    private final LinearLayout table,quote;
    private final TextView[] values=new TextView[8];
    private final Meter[] meters=new Meter[8];
    private final ImageView portrait;
    private final CareerBackdrop mastArt=new CareerBackdrop(0xff722311,0xe9340c04,0x28592a10);
    void setBackdrop(CareerArt art){mastArt.setArt(art==null?null:art.training);}
    private Bitmap[] photos;
    private int photoIndex;
    void setArt(Bitmap[] art){
        photos=art;portrait.setImageBitmap(art!=null && photoIndex>=0 && photoIndex<art.length?art[photoIndex]:null);
    }
    private CareerDesk.Page lastPage,lastDrill;
    private int[] lastRatings;

    TrainingSheet(Context context,DiscFonts fonts,boolean compact){
        super(context);setOrientation(VERTICAL);
        setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xff4c190d,0xff281109,0xff160b07}));
        FrameLayout header=new FrameLayout(context);header.setBackground(mastArt);
        addView(header,new LayoutParams(-1,-2));
        portrait=new ImageView(context);portrait.setScaleType(ImageView.ScaleType.CENTER_CROP);
        portrait.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        header.addView(portrait,new FrameLayout.LayoutParams(dp(compact?100:142),dp(compact?56:76),Gravity.RIGHT|Gravity.CENTER_VERTICAL));
        LinearLayout mast=new LinearLayout(context);mast.setOrientation(VERTICAL);
        mast.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0x38301007,0x85301007,0x00301007}));
        mast.setMinimumHeight(dp(compact?56:76));mast.setGravity(Gravity.CENTER_VERTICAL);
        mast.setPadding(dp(12),dp(6),dp(compact?96:136),dp(6));header.addView(mast);
        stage=label(fonts.stencil,compact?13:15,GameInk.GOLD);stage.setLetterSpacing(.09f);mast.addView(stage);
        title=label(fonts.stencil,compact?25:33,0xffffe2a8);title.setShadowLayer(dp(2),1,2,0xff210a04);mast.addView(title);
        View keyline=new View(context);keyline.setBackgroundColor(GameInk.ORANGE);addView(keyline,new LayoutParams(-1,dp(2)));
        LinearLayout body=new LinearLayout(context);body.setOrientation(VERTICAL);
        body.setBackgroundColor(FIELD);
        body.setPadding(dp(12),dp(5),dp(12),dp(7));addView(body);
        cue=label(fonts.body,compact?14:17,GameInk.TEXT);body.addView(cue);
        quote=new LinearLayout(context);quote.setOrientation(VERTICAL);body.addView(quote);
        fee=label(fonts.stencil,30,GameInk.TEXT);wallet=label(fonts.body,17,GameInk.MUTED);
        quote.addView(fee);quote.addView(wallet);
        legend=label(fonts.stencil,compact?14:16,GameInk.GOLD);legend.setLetterSpacing(.08f);legend.setPadding(0,dp(8),0,dp(3));body.addView(legend);
        table=new LinearLayout(context);table.setOrientation(VERTICAL);body.addView(table);
        for(int i=0;i<4;i++){
            LinearLayout row=new LinearLayout(context);row.setBaselineAligned(false);table.addView(row);
            for(int col=0;col<2;col++){
                int k=i+4*col;
                LinearLayout cell=new LinearLayout(context);cell.setOrientation(VERTICAL);
                cell.setPadding(dp(6),dp(compact?1:3),dp(6),dp(compact?2:5));cell.setBackgroundColor(i%2==0?STRIPE:FIELD);
                LayoutParams slot=new LayoutParams(0,-2,1);slot.setMargins(0,0,dp(col==0?3:0),dp(1));row.addView(cell,slot);
                LinearLayout labels=new LinearLayout(context);labels.setGravity(Gravity.CENTER_VERTICAL);cell.addView(labels);
                TextView name=label(fonts.body,compact?14:16,GameInk.TEXT);name.setText(STATS[k]);
            labels.addView(name,new LayoutParams(0,-2,1));values[k]=label(fonts.stencil,compact?14:18,GameInk.TEXT);labels.addView(values[k]);
                meters[k]=new Meter(context);cell.addView(meters[k],new LayoutParams(-1,dp(3)));
            }
        }
        poster=new CampPoster(context,fonts);poster.setBackgroundColor(FIELD);
        addView(poster,new LayoutParams(-1,dp(compact?132:176),1f));
        setVisibility(GONE);
    }
    private final CampPoster poster;
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
        boolean entering=getVisibility()!=VISIBLE;
        setVisibility(VISIBLE);
        if(entering)post(()->SheetMotion.enter(this));
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
            // Three receipts that look different: a genuine zero fee (gold "No fee"), an unknown fee (wallet only),
            // and a fee above the wallet (red "Short by", never colour alone).
            boolean shortBy=budget.cash>=0 && budget.cost>budget.cash;
            bind(fee,budget.cost<0?"":budget.cost==0?"No fee":CareerDesk.money(budget.cost)+" fee");
            fee.setTextColor(budget.cost==0?GameInk.GOLD:GameInk.TEXT);
            bind(wallet,"");
            CharSequence receipt=receipt(budget);
            if(receipt.length()>0){wallet.setText(receipt);wallet.setVisibility(VISIBLE);}
            wallet.setContentDescription(shortBy?receipt+". Not enough cash to hire.":null);
        }
        int[] ratings=trainer?null:desk.training(!result);
        if(trainer)poster.showQuote(budget);else poster.showTraining(ratings,result);
        bind(cue,trainer?(budget==null?"Trainer quote pending.":""):
                result?(ratings==null?"Waiting for the official session scores.":""):
                drill!=null?drill.detail:"Select a drill to see its training effects.");
        // "Session complete" only stamps real results; forecast values never appear under Results.
        bind(legend,ratings==null?"":result?"SESSION COMPLETE":"FORECAST");
        table.setVisibility(ratings==null?GONE:VISIBLE);
        if(ratings!=null && !Arrays.equals(lastRatings,ratings)){
            for(int i=0;i<8;i++){
                // Values roll and bars grow in a quick cascade, from the previous drill's figure (or zero).
                int n=ratings[i],from=lastRatings==null?0:lastRatings[i];
                values[i].setTextColor(n>0?GAIN:n<0?LOSS:GameInk.MUTED);values[i].setVisibility(VISIBLE);
                SheetMotion.rollPercent(values[i],from,n,30L*i);meters[i].set(n,30L*i);
            }
        }
        if((lastPage!=page || lastDrill!=drill) && isShown() && isAttachedToWindow() && ValueAnimator.areAnimatorsEnabled()){
            title.animate().cancel();title.setTranslationX(dp(12));title.setAlpha(.5f);
            title.animate().translationX(0).alpha(1).setDuration(170).start();
        }
        lastPage=page;lastDrill=drill;lastRatings=ratings;
    }
    /** "In camp / After hiring / Short by" with the shortfall in red text, or "" when the wallet is unknown. */
    static CharSequence receipt(CareerDesk.Budget budget){
        if(budget.cash<0)return "";
        android.text.SpannableStringBuilder s=new android.text.SpannableStringBuilder("In camp   "+CareerDesk.money(budget.cash));
        if(budget.cost<0)return s;
        boolean afford=budget.cash>=budget.cost;
        s.append("     ");
        int start=s.length();
        s.append(afford?"After hiring   ":"Short by   ").append(CareerDesk.money(Math.abs(budget.cash-budget.cost)));
        s.setSpan(new android.text.style.ForegroundColorSpan(afford?GameInk.TEXT:LOSS),start,s.length(),0);
        return s;
    }
    /** Fraction of the locked scale a change fills, and whether it runs past the scale. */
    static float fill(int value){return Math.max(-1f,Math.min(1f,value/(float)SCALE));}
    static boolean beyond(int value){return Math.abs(value)>SCALE;}
    private static final class Meter extends View {
        private final Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);
        private int value;
        private float shown;
        private ValueAnimator grow;
        Meter(Context c){super(c);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        void set(int next,long delay){
            value=next;
            if(grow!=null)grow.cancel();
            float target=fill(next);
            if(!SheetMotion.on() || !isShown()){shown=target;invalidate();return;}
            grow=ValueAnimator.ofFloat(shown,target).setDuration(SheetMotion.ROLL_MS);
            grow.setStartDelay(delay);grow.setInterpolator(SheetMotion.settle());
            grow.addUpdateListener(a->{shown=(float)a.getAnimatedValue();invalidate();});
            grow.start();
        }
        @Override protected void onDetachedFromWindow(){if(grow!=null)grow.cancel();shown=fill(value);super.onDetachedFromWindow();}
        @Override protected void onDraw(Canvas c){
            float w=getWidth(),h=getHeight(),zero=w/2;
            c.drawColor(0xff4a2419);ink.setColor(0xffa58a6c);c.drawRect(zero,0,zero+1,h,ink);
            float end=zero+zero*shown;ink.setColor(shown<0?LOSS:GAIN);
            c.drawRect(Math.min(zero,end),0,Math.max(zero,end),h,ink);
            if(beyond(value)){
                // Past the locked scale: a bright cap at the end, the label carries the exact figure.
                ink.setColor(GameInk.GOLD);float cap=Math.max(2,h);
                c.drawRect(value>0?w-cap:0,0,value>0?w:cap,h,ink);
            }
        }
    }
}
