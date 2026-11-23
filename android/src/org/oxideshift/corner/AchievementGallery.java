package org.oxideshift.corner;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;

/** The in-game medal cabinet and its matching broadcast ribbon. */
final class AchievementGallery extends LinearLayout {
    private static final int PAPER=0xffffe4be, GOLD=0xffffb626, SILVER=0xffdfc9ab, RED=0xffaa321f;
    private final ScrollView scroll;
    private final LinearLayout footer;
    private final Runnable close;
    private final TextView tally;
    private final LinearLayout cards;
    private final boolean wide;
    private final DiscFonts fonts;
    private final Achievements awards;
    private final TextView[] tabs=new TextView[Achievements.Category.values().length];
    private int selected=-1;
    private final java.util.function.Consumer<DiscMenuSounds.Cue> sound;

    static int dp(Context c,float n){return Math.round(c.getResources().getDisplayMetrics().density*n);}
    static TextView text(Context c,String value,int size,int color,Typeface font){
        TextView v=new TextView(c);v.setText(value);v.setTextSize(size);v.setTextColor(color);v.setTypeface(font);
        v.setIncludeFontPadding(false);return v;
    }
    private static void pad(View v,int x,int y){
        int a=dp(v.getContext(),x),b=dp(v.getContext(),y);v.setPadding(a,b,a,b);
    }
    static GradientDrawable plate(int fill,int edge){
        GradientDrawable g=new GradientDrawable();g.setColor(fill);g.setStroke(1,edge);return g;
    }
    private static LinearLayout stack(Context c){
        LinearLayout v=new LinearLayout(c);v.setOrientation(VERTICAL);return v;
    }
    AchievementGallery(Context c,DiscFonts f,Achievements awards,int width,Bitmap[] art,Runnable close,
                       java.util.function.Consumer<DiscMenuSounds.Cue> sound){
        super(c);setOrientation(VERTICAL);this.sound=sound;this.close=close;
        setBackground(new CabinetBackdrop(art));
        fonts=f;this.awards=awards;wide=width>=dp(c,680);
        LinearLayout mast=new LinearLayout(c);mast.setGravity(Gravity.CENTER_VERTICAL);
        pad(mast,wide?22:10,3);
        addView(mast,new LayoutParams(-1,-2));
        if(art!=null){
            View logo=new View(c){
                final Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
                final RectF pos=new RectF();
                @Override protected void onDraw(Canvas canvas){
                    float w=getWidth(),h=getHeight();
                    pos.set(w*.38f,0,w,h);
                    if(art[2]!=null)canvas.drawBitmap(art[2],null,pos,ink);
                    pos.set(0,h*.14f,w*.79f,h*.94f);
                    if(art[1]!=null)canvas.drawBitmap(art[1],null,pos,ink);
                }
            };
            logo.setContentDescription("Fight Night Round 3");
            LayoutParams size=new LayoutParams(dp(c,wide?108:79),dp(c,wide?75:56));size.rightMargin=dp(c,10);mast.addView(logo,size);
        }
        LinearLayout heading=stack(c);mast.addView(heading,new LayoutParams(0,-2,1));
        TextView title=text(c,"ACHIEVEMENTS",wide?42:30,GOLD,f.title);
        title.setTextScaleX(.91f);title.setShadowLayer(dp(c,3),dp(c,2),dp(c,3),0xff150502);heading.addView(title);
        tally=text(c,"",13,PAPER,f.body);tally.setShadowLayer(2,1,1,0xff130501);heading.addView(tally);
        LinearLayout nav=stack(c);pad(nav,wide?18:7,2);nav.setBackgroundColor(0xbb230a04);addView(nav,new LayoutParams(-1,-2));
        int perRow=wide?tabs.length:4;
        LinearLayout line=null;
        for(Achievements.Category category:Achievements.Category.values()){
            int index=category.ordinal();
            if(index%perRow==0){line=new LinearLayout(c);nav.addView(line);}
            int have=0,total=0;
            for(Achievements.Award a:Achievements.Award.values())if(a.category==category){total++;if(awards.earned(a))have++;}
            TextView tab=text(c,category.title.toUpperCase(java.util.Locale.ROOT)+"\n"+have+"/"+total,wide?15:13,PAPER,f.title);
            tab.setGravity(Gravity.CENTER);tab.setMinHeight(dp(c,wide?42:36));
            tab.setSoundEffectsEnabled(false);tab.setOnClickListener(v->showCategory(index));
            tab.setContentDescription(category.title+", "+have+" of "+total+" earned");
            tabs[index]=tab;LayoutParams slot=new LayoutParams(0,-2,1);slot.setMargins(dp(c,2),dp(c,2),dp(c,2),0);
            line.addView(tab,slot);
        }
        scroll=new ScrollView(c);addView(scroll,new LayoutParams(-1,0,1));
        cards=stack(c);pad(cards,wide?18:7,5);scroll.addView(cards);showCategory(0);
        footer=controls(c);
        addView(footer,new LayoutParams(-1,-2));
    }
    void promptsOnLower(boolean lower){footer.setVisibility(lower?GONE:VISIBLE);}
    LinearLayout controls(Context c){
        LinearLayout bar=new LinearLayout(c);bar.setGravity(Gravity.CENTER_VERTICAL);
        pad(bar,10,2);bar.setBackgroundColor(0xff310e07);
        for(int step:new int[]{-1,1}){
            MenuPrompt move=new MenuPrompt(c,step<0?PadIcon.Cap.LB:PadIcon.Cap.RB,
                    step<0?"Prev":"Next",fonts.stencil,
                    ()->showCategory((selected+tabs.length+step)%tabs.length));
            move.setContentDescription(step<0?"LB, previous category":"RB, next category");
            bar.addView(move,new LayoutParams(0,-2,1));
        }
        bar.addView(new MenuPrompt(c,1,fonts.stencil,close),new LayoutParams(0,-2,1));
        return bar;
    }
    private void showCategory(int index){
        if(index==selected)return;
        if(selected>=0)sound.accept(DiscMenuSounds.Cue.HORIZONTAL);
        selected=index;Context c=getContext();
        for(int i=0;i<tabs.length;i++){
            boolean active=i==index;tabs[i].setSelected(active);
            tabs[i].setBackgroundColor(active?0xba581503:0x00000000);
            tabs[i].setTextColor(active?0xffffc137:0xffe2bc8c);
            tabs[i].setShadowLayer(dp(c,2),1,2,0xff0d0301);
            android.graphics.drawable.GradientDrawable bar=new GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,new int[]{0x00ffad28,0xffffad28,0x00ffad28});
            bar.setBounds(0,0,dp(c,48),dp(c,2));
            tabs[i].setCompoundDrawables(null,null,null,active?bar:null);
        }
        boolean classics=Achievements.Category.values()[index]==Achievements.Category.CLASSICS;
        tally.setText(awards.count()+" / "+Achievements.Award.values().length+" EARNED"+
                (classics?"    \u2022    "+awards.classicPoints()+" / 1,000 G":""));
        cards.removeAllViews();
        int columns=wide?2:1,visible=0;
        LinearLayout line=null;
        for(Achievements.Award a:Achievements.Award.values())if(a.category.ordinal()==index){
            if(visible++%columns==0){
                line=new LinearLayout(c);line.setBaselineAligned(false);cards.addView(line,new LayoutParams(-1,-2));
            }
            boolean earned=awards.earned(a);
            LinearLayout row=new LinearLayout(c);row.setGravity(Gravity.CENTER_VERTICAL);pad(row,9,8);
            row.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                    earned?new int[]{0xef5b210a,0xcf361005,0xae260c05}:new int[]{0xde250c06,0xc3230b05,0x97200a05}));
            LayoutParams slot=new LayoutParams(0,-1,1);slot.setMargins(dp(c,2),dp(c,2),dp(c,2),dp(c,2));
            line.addView(row,slot);
            row.addView(new Medal(c,earned,a),new LayoutParams(dp(c,34),dp(c,40)));
            LinearLayout copy=stack(c);copy.setPadding(dp(c,8),0,0,0);row.addView(copy,new LayoutParams(0,-2,1));
            TextView name=text(c,a.title.toUpperCase(java.util.Locale.ROOT),19,earned?GOLD:0xffffe8c1,fonts.title);
            name.setShadowLayer(1,0,1,0xff24160c);copy.addView(name);
            copy.addView(text(c,a.goal,15,SILVER,fonts.body));
            if(a.points>0)row.addView(text(c,a.points+" G",15,GOLD,fonts.body));
            row.setContentDescription(a.title+". "+(a.points>0?a.points+" G. ":"")+(earned?"Earned. ":"Locked. ")+a.goal);
            row.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        }
        if(wide && visible%2==1)line.addView(new View(c),new LayoutParams(0,1,1));
        scroll.scrollTo(0,0);
    }
    boolean navigate(KeyEvent event){
        int code=event.getKeyCode();
        switch(code){
            case KeyEvent.KEYCODE_DPAD_LEFT:case KeyEvent.KEYCODE_BUTTON_L1:
            case KeyEvent.KEYCODE_DPAD_RIGHT:case KeyEvent.KEYCODE_BUTTON_R1:
                if(event.getAction()==KeyEvent.ACTION_DOWN && event.getRepeatCount()==0){
                    int step=(code==KeyEvent.KEYCODE_DPAD_LEFT || code==KeyEvent.KEYCODE_BUTTON_L1)?-1:1;
                    showCategory((selected+tabs.length+step)%tabs.length);
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:case KeyEvent.KEYCODE_DPAD_DOWN:
                case KeyEvent.KEYCODE_PAGE_UP:case KeyEvent.KEYCODE_PAGE_DOWN:
                boolean page=code==KeyEvent.KEYCODE_PAGE_UP || code==KeyEvent.KEYCODE_PAGE_DOWN;
                int direction=code==KeyEvent.KEYCODE_DPAD_DOWN || code==KeyEvent.KEYCODE_PAGE_DOWN?1:-1;
                if(event.getAction()==KeyEvent.ACTION_DOWN && scroll.canScrollVertically(direction)){
                    if(event.getRepeatCount()==0)sound.accept(selected==Achievements.Category.JUDGES.ordinal()
                            ?DiscMenuSounds.Cue.SCORECARD_PAGE:DiscMenuSounds.Cue.VERTICAL);
                    scroll.smoothScrollBy(0,direction*(page?scroll.getHeight():dp(getContext(),72)));
                }
                return true;
            default:return false;
        }
    }
    /** The ring and translucent numeral are separate sprites in the retail main menu. */
    private static final class CabinetBackdrop extends android.graphics.drawable.Drawable {
        private final Bitmap photo,numeral;
        private final Rect crop=new Rect();
        private final RectF numberBounds=new RectF();
        private final Paint paint=new Paint(Paint.FILTER_BITMAP_FLAG);
        private LinearGradient wash;
        CabinetBackdrop(Bitmap[] art){photo=art==null?null:art[0];numeral=art==null?null:art[2];}
        @Override protected void onBoundsChange(Rect r){
            if(r.isEmpty())return;
            wash=new LinearGradient(r.left,r.top,r.right,r.bottom,
                    new int[]{0x300d0301,0x101c0600,0x202e0500},null,Shader.TileMode.CLAMP);
            float side=Math.min(r.height()*1.2f,r.width()*.78f),right=r.right+side*.12f;
            numberBounds.set(right-side,r.top-side*.11f,right,r.top+side*.89f);
            if(photo!=null){
                int w=photo.getWidth(),h=Math.min(416,photo.getHeight());
                float scale=Math.max(r.width()/(float)w,r.height()/(float)h);
                int sw=Math.min(w,Math.max(1,Math.round(r.width()/scale)));
                int sh=Math.min(h,Math.max(1,Math.round(r.height()/scale)));
                crop.set((w-sw)/2,(h-sh)/2,(w+sw)/2,(h+sh)/2);
            }
        }
        @Override public void draw(Canvas canvas){
            Rect r=getBounds();paint.setShader(null);paint.setColor(0xff5b3020);canvas.drawRect(r,paint);
            if(photo!=null && !crop.isEmpty()){paint.setColor(0xffffffff);canvas.drawBitmap(photo,crop,r,paint);}
            if(numeral!=null){paint.setColor(0xd8ffffff);canvas.drawBitmap(numeral,null,numberBounds,paint);}
            if(wash!=null){paint.setColor(0xffffffff);paint.setShader(wash);canvas.drawRect(r,paint);paint.setShader(null);}
        }
        @Override public void setAlpha(int alpha){}
        @Override public void setColorFilter(ColorFilter filter){}
        @Override public int getOpacity(){return PixelFormat.OPAQUE;}
    }
    static View ribbon(Context c,DiscFonts f,Achievements.Award a){
        LinearLayout banner=new LinearLayout(c);banner.setGravity(Gravity.CENTER_VERTICAL);
        banner.setBackground(plate(0xfb29221e,0xffbb8a4e));pad(banner,12,7);
        banner.addView(new Medal(c,true,a),new LayoutParams(dp(c,48),dp(c,48)));
        LinearLayout words=stack(c);words.setPadding(dp(c,9),0,0,0);banner.addView(words);
        TextView kicker=text(c,a.points>0?"ACHIEVEMENT UNLOCKED   "+a.points+" G":"ACHIEVEMENT UNLOCKED",12,GOLD,f.body);kicker.setLetterSpacing(.08f);words.addView(kicker);
        words.addView(text(c,a.title,22,PAPER,f.stencil));
        banner.setContentDescription("Achievement unlocked. "+a.title);return banner;
    }
    private static final class Medal extends View {
        private static final String[] CELLS={
            "BURGER_KING","DODGE","EVERLAST","UNDER_ARMOUR","ESPN_WNF","ESPN_FNF","ESPN_PPV","EA_SPORTS",
            "FIRST_WIN","STOPPAGE","DEEP_WATER","FIRST_ROUND_KO","LATE_STOPPAGE","CLEAN_KO","TECHNICAL_KO","COMEBACK",
            "TRADED_DOWNS","LATE_WIN","LAND_25","LAND_150","HAYMAKERS","ACCURATE","SHARPSHOOTER","OUTLAND",
            "ELUSIVE","STAGGER","TWO_DOWNS","UNANIMOUS_ROUND","SPLIT_ROUND","BIG_ROUND","WIDE_ROUND","THREE_STRAIGHT",
            "SIX_ROUNDS","ANSWER_BACK","EIGHT_SCORED","CONSENSUS","TURN_CARDS","FIRST_DRILL","BREAKTHROUGH","BAG_DRILL",
            "DUMMY_DRILL","WEIGHT_DRILL","POWER_GAIN","SPEED_GAIN","STAMINA_GAIN","THREE_GAINS","STRONG_SESSION","FIVE_WINS",
            "TEN_WINS","TWENTY_FIVE_WINS","TEN_KOS","TWENTY_FIVE_KOS","UNBEATEN","KO_RATIO","VETERAN","STEP_UP",
            "FIRST_TROPHY","FOUR_TROPHIES","HALF_CASE","FULL_CASE"
        };
        private static Bitmap atlas;
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        private final Rect src=new Rect();
        private final RectF dest=new RectF();
        Medal(Context c,boolean earned,Achievements.Award award){
            super(c);
            if(atlas==null)try(java.io.InputStream in=c.getAssets().open("achievement-badges.webp")){
                atlas=android.graphics.BitmapFactory.decodeStream(in);
            }catch(java.io.IOException e){android.util.Log.w("FightNight","Achievement artwork unavailable",e);}
            int cell=java.util.Arrays.asList(CELLS).indexOf(award.name());
            if(cell>=0)src.set(cell%8*128,cell/8*128,(cell%8+1)*128,(cell/8+1)*128);
            if(!earned){
                android.graphics.ColorMatrix gray=new android.graphics.ColorMatrix();
                gray.setSaturation(.10f);
                paint.setColorFilter(new android.graphics.ColorMatrixColorFilter(gray));paint.setAlpha(165);
            }
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }
        @Override protected void onSizeChanged(int w,int h,int oldW,int oldH){
            float radius=Math.min(w,h)/2f;
            dest.set(w/2f-radius,h/2f-radius,w/2f+radius,h/2f+radius);
        }
        @Override protected void onDraw(Canvas c){
            if(atlas!=null && !src.isEmpty())c.drawBitmap(atlas,src,dest,paint);
        }
    }
}
