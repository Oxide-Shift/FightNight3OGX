package org.oxideshift.corner;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;

/** The in-game medal cabinet and its matching broadcast ribbon. */
final class AchievementGallery extends LinearLayout {
    private static final int PAPER=GameInk.TEXT, GOLD=GameInk.GOLD, SILVER=GameInk.MUTED, RED=GameInk.ORANGE;
    interface MusicSettings {
        boolean enabled(int row);
        void toggle(int row);
        void openTrax();
    }
    private final MusicSettings music;
    private boolean revealSelectionAfterLayout;
    private final java.util.Set<MenuPrompt> musicActions=java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
    private int openKey=-1,openDevice=-1;
    private final android.util.SparseBooleanArray backPresses=new android.util.SparseBooleanArray();
    private static final String[] OPTIONS={"On During Fights","FN3 Expanded","EA Trax"};
    private static final String[] DESCRIPTIONS={"Carry the current song through loading, walkouts and every round.",
            "Add "+ExpandedTracks.TITLES.length+" songs by "+ExpandedTracks.ARTIST+" to the EA Trax rotation.","Choose the original soundtrack tracks in your rotation."};
    private final ScrollView scroll;
    private final LinearLayout footer;
    private final Runnable close;
    private final TextView tally;
    private final LinearLayout cards;
    private final boolean wide;
    private final Bitmap[] galleryArt;
    private final DiscFonts fonts;
    private final Achievements awards;
    private final TextView[] tabs=new TextView[Achievements.Category.values().length];
    private int selected=-1,chosenAward,awardScroll;
    private final java.util.ArrayList<Achievements.Award> awardOrder=new java.util.ArrayList<>();
    private final java.util.ArrayList<View> awardRows=new java.util.ArrayList<>();
    private final java.util.Set<MenuPrompt> detailsActions=java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
    private View awardDetail;
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
                       java.util.function.Consumer<DiscMenuSounds.Cue> sound,MusicSettings music){
        super(c);this.music=music;setOrientation(VERTICAL);this.sound=sound;this.close=close;
        galleryArt=art;setBackground(new CabinetBackdrop(art));
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
            logo.setContentDescription("Fight Night Round 3 OGX");
            LayoutParams size=new LayoutParams(dp(c,wide?108:79),dp(c,wide?75:56));size.rightMargin=dp(c,10);mast.addView(logo,size);
        }
        LinearLayout heading=stack(c);mast.addView(heading,new LayoutParams(0,-2,1));
        TextView title=text(c,music==null?"ACHIEVEMENTS":"MUSIC",wide?42:30,GOLD,f.stencil);
        title.setShadowLayer(dp(c,3),dp(c,2),dp(c,3),0xff150502);heading.addView(title);
        tally=text(c,"",13,PAPER,f.stencil);tally.setShadowLayer(2,1,1,0xff130501);heading.addView(tally);
        if(music!=null){
            tally.setVisibility(GONE);
            // Only a music row owns controller focus. Keep the scrollable area tight to the rows
            // even with the footer on a second display, and allow large text to scroll on small screens.
            FrameLayout body=new FrameLayout(c);
            addView(body,new LayoutParams(-1,0,1));
            scroll=new ScrollView(c,null,0);
            scroll.setFocusableInTouchMode(false);scroll.setFocusable(false);
            scroll.setDefaultFocusHighlightEnabled(false);
            scroll.setBackground(null);scroll.setForeground(null);
            scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
            body.addView(scroll,new FrameLayout.LayoutParams(-1,-2,Gravity.TOP));
            cards=stack(c);pad(cards,wide?22:10,8);scroll.addView(cards);
            selected=0;
            for(int i=0;i<OPTIONS.length;i++){
                LayoutParams size=new LayoutParams(-1,-2);size.bottomMargin=dp(c,4);
                cards.addView(new MusicRow(c,i),size);
            }
            showMusicRows();
            footer=controls(c);addView(footer,new LayoutParams(-1,-2));return;
        }
        HorizontalScrollView strip=new HorizontalScrollView(c);
        strip.setHorizontalScrollBarEnabled(false);strip.setFillViewport(true);
        strip.setDefaultFocusHighlightEnabled(false);strip.setFocusable(false);
        LinearLayout line=new LinearLayout(c);pad(line,wide?18:7,2);strip.addView(line);
        if(!wide)categoryArrow(line,-1);
        addView(strip,new LayoutParams(-1,-2));
        for(Achievements.Category category:Achievements.Category.values()){
            int index=category.ordinal();
            int have=0,total=0;
            for(Achievements.Award a:Achievements.Award.values())if(a.category==category){total++;if(awards.earned(a))have++;}
            TextView tab=text(c,category.title.toUpperCase(java.util.Locale.ROOT)+"\n"+have+"/"+total,wide?15:13,PAPER,f.stencil);
            pad(tab,9,1);tab.setMaxLines(2);tab.setGravity(Gravity.CENTER);tab.setMinHeight(dp(c,wide?42:36));
            tab.setSoundEffectsEnabled(false);tab.setOnClickListener(v->showCategory(index));
            tab.setContentDescription(category.title+", "+have+" of "+total+" earned");
            tabs[index]=tab;LayoutParams slot=new LayoutParams(-2,-2,1);slot.setMargins(dp(c,2),dp(c,2),dp(c,2),0);
            line.addView(tab,slot);
        }
        if(!wide)categoryArrow(line,1);
        scroll=new ScrollView(c);scroll.setDefaultFocusHighlightEnabled(false);addView(scroll,new LayoutParams(-1,0,1));
        cards=stack(c);pad(cards,wide?18:7,5);scroll.addView(cards);showCategory(0);
        footer=controls(c);
        addView(footer,new LayoutParams(-1,-2));
    }
    final class LowerPage extends FrameLayout {
        private final TextView section,title,detail;
        private String lastTitle,lastDetail;
        private int lastCategory=-1,lastCount=-1;
        LowerPage(Context c){
            super(c);setBackground(new CabinetBackdrop(galleryArt));setClickable(true);
            // This full-screen panel intercepts touches. Focus styling belongs
            // to its individual controls, including after a controller wake.
            setFocusable(false);setDefaultFocusHighlightEnabled(false);
            LinearLayout textBlock=stack(c);pad(textBlock,26,10);
            section=text(c,music!=null?"EA TRAX":"ACHIEVEMENTS",17,GOLD,fonts.stencil);
            title=text(c,"",32,PAPER,fonts.stencil);title.setMaxLines(3);
            detail=text(c,"",19,SILVER,fonts.body);detail.setMaxLines(3);
            for(TextView v:new TextView[]{section,title,detail}){
                v.setShadowLayer(dp(c,3),1,2,0xff190803);textBlock.addView(v);
            }
            addView(textBlock,new FrameLayout.LayoutParams(-1,-2,Gravity.CENTER_VERTICAL));
            addView(controls(c),new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));
        }
        void update(FightBroadcast.Song song){
            String heading,sub;
            if(music!=null){
                heading=song==null?"No track playing":song.title;
                sub=song==null?"":song.artist;
            }else{
                int count=awards.count();
                if(lastCategory==selected && lastCount==count)return;
                lastCategory=selected;lastCount=count;
                Achievements.Category category=Achievements.Category.values()[selected];
                int have=0,total=0;
                for(Achievements.Award a:Achievements.Award.values())if(a.category==category){total++;if(awards.earned(a))have++;}
                heading=category.title;sub=have+" / "+total+" earned";
            }
            if(!heading.equals(lastTitle)){title.setText(heading);lastTitle=heading;}
            if(!sub.equals(lastDetail)){detail.setText(sub);lastDetail=sub;}
        }
    }
    void promptsOnLower(boolean lower){footer.setVisibility(lower?GONE:VISIBLE);}
    LinearLayout controls(Context c){
        LinearLayout bar=new LinearLayout(c);bar.setGravity(Gravity.CENTER_VERTICAL);
        pad(bar,10,2);bar.setBackgroundColor(0xff310e07);
        if(music!=null){
            bar.addView(new MenuPrompt(c,PadIcon.Cap.LB,"Previous",fonts.stencil,()->moveMusic(-1)),new LayoutParams(0,-2,1));
            bar.addView(new MenuPrompt(c,PadIcon.Cap.RB,"Next",fonts.stencil,()->moveMusic(1)),new LayoutParams(0,-2,1));
            MenuPrompt confirm=new MenuPrompt(c,PadIcon.Cap.A,musicAction(),fonts.stencil,()->toggleMusic(selected));
            musicActions.add(confirm);
            bar.addView(confirm,new LayoutParams(0,-2,1));
            bar.addView(new MenuPrompt(c,1,fonts.stencil,close),new LayoutParams(0,-2,1));return bar;
        }
        for(int step:new int[]{-1,1}){
            MenuPrompt move=new MenuPrompt(c,step<0?PadIcon.Cap.LB:PadIcon.Cap.RB,
                    step<0?"Prev":"Next",fonts.stencil,
                    ()->showCategory((selected+tabs.length+step)%tabs.length));
            move.setContentDescription(step<0?"LB, previous category":"RB, next category");
            bar.addView(move,new LayoutParams(0,-2,1));
        }
        MenuPrompt details=new MenuPrompt(c,PadIcon.Cap.A,"Details",fonts.stencil,this::openAward);
        detailsActions.add(details);details.setVisibility(awardDetail==null?VISIBLE:GONE);
        bar.addView(details,new LayoutParams(0,-2,1));
        bar.addView(new MenuPrompt(c,1,fonts.stencil,close),new LayoutParams(0,-2,1));
        return bar;
    }
    private void categoryArrow(LinearLayout line,int step){
        Context c=line.getContext();
        TextView arrow=text(c,step<0?"<":">",21,GOLD,fonts.stencil);
        arrow.setGravity(Gravity.CENTER);
        arrow.setContentDescription(step<0?"Previous category":"Next category");
        arrow.setSoundEffectsEnabled(false);
        arrow.setOnClickListener(v->showCategory((selected+tabs.length+step)%tabs.length));
        line.addView(arrow,new LayoutParams(dp(c,45),-1));
    }
    private final class MusicRow extends LinearLayout {
        private final int index;
        private final TextView title,state;
        MusicRow(Context c,int index){
            super(c);this.index=index;
            setGravity(Gravity.CENTER_VERTICAL);pad(this,14,10);
            setFocusable(true);setFocusedByDefault(index==0);
            setDefaultFocusHighlightEnabled(false);setSoundEffectsEnabled(false);
            setMinimumHeight(dp(c,52));
            setPadding(dp(c,20),dp(c,8),dp(c,14),dp(c,8));
            setBackground(new BillSelect(getResources().getDisplayMetrics().density,false));
            LinearLayout label=stack(c);addView(label,new LayoutParams(0,-2,1));
            title=text(c,OPTIONS[index].toUpperCase(java.util.Locale.ROOT),wide?25:21,PAPER,fonts.stencil);
            label.addView(title);label.addView(text(c,DESCRIPTIONS[index],wide?16:14,GameInk.TEXT,fonts.body));
            state=text(c,"",21,SILVER,fonts.stencil);pad(state,8,2);addView(state);
            setOnFocusChangeListener((v,focused)->{if(focused)selectMusic(index);});
            setOnClickListener(v->toggleMusic(index));
        }
        void update(){
            boolean active=selected==index,playlist=index==2,enabled=!playlist && music.enabled(index);
            setSelected(active);title.setTextColor(active?GOLD:PAPER);
            state.setText(playlist?"OPEN":enabled?"ON":"OFF");state.setTextColor(active || enabled?GOLD:SILVER);
            setContentDescription(OPTIONS[index]+", "+(playlist?"open playlist":enabled?"on":"off"));
        }
    }
    boolean playlistSelected(){return music!=null && selected==2;}
    private String musicAction(){return selected==2?"Open":music.enabled(selected)?"Turn off":"Turn on";}
    private void showMusicRows(){
        for(int i=0;i<cards.getChildCount();i++)((MusicRow)cards.getChildAt(i)).update();
        for(MenuPrompt action:musicActions)action.setCaption(musicAction());
    }
    private void selectMusic(int index){
        selectMusic(index,true);
    }
    private void selectMusic(int index,boolean audible){
        if(selected==index)return;
        selected=index;if(audible)sound.accept(DiscMenuSounds.Cue.VERTICAL);showMusicRows();
        View row=cards.getChildAt(index);
        if(!isInTouchMode())row.requestFocus();
        if(row.getHeight()==0 || scroll.getHeight()==0)revealSelectionAfterLayout=true;
        else row.requestRectangleOnScreen(new Rect(0,0,row.getWidth(),row.getHeight()),!audible);
    }
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b){
        super.onLayout(changed,l,t,r,b);
        if(revealSelectionAfterLayout){
            View row=cards.getChildAt(selected);
            if(row.getHeight()>0 && scroll.getHeight()>0){
                revealSelectionAfterLayout=false;
                row.requestRectangleOnScreen(new Rect(0,0,row.getWidth(),row.getHeight()),true);
            }
        }
    }
    private void moveMusic(int step){int count=cards.getChildCount();selectMusic((selected+step+count)%count);}
    void selectPlaylist(){if(music!=null)selectMusic(2,false);}
    private void toggleMusic(int index){
        if(index==2){music.openTrax();return;}
        selected=index;music.toggle(index);sound.accept(DiscMenuSounds.Cue.FORWARD);showMusicRows();
        announceForAccessibility(cards.getChildAt(index).getContentDescription());
    }
    private void showCategory(int index){
        if(index==selected)return;
        if(selected>=0)sound.accept(DiscMenuSounds.Cue.HORIZONTAL);
        selected=index;Context c=getContext();
        for(int i=0;i<tabs.length;i++){
            boolean active=i==index;tabs[i].setSelected(active);
            if(!wide)tabs[i].setVisibility(active?VISIBLE:GONE);
            tabs[i].setBackgroundColor(active?0xba581503:0x00000000);
            tabs[i].setTextColor(active?0xffffc137:0xffe2bc8c);
            tabs[i].setShadowLayer(dp(c,2),1,2,0xff0d0301);
            android.graphics.drawable.GradientDrawable bar=new GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,new int[]{0x00ffad28,0xffffad28,0x00ffad28});
            bar.setBounds(0,0,dp(c,48),dp(c,2));
            tabs[i].setCompoundDrawables(null,null,null,active?bar:null);
        }
        tally.setText(awards.count()+" / "+Achievements.Award.values().length+" EARNED"+
                (wide?"    \u2022    FIGHT SCORE  ":"  \u2022  SCORE ")+awards.fightScore());
        cards.removeAllViews();awardDetail=null;chosenAward=0;awardOrder.clear();awardRows.clear();
        for(MenuPrompt action:detailsActions)action.setVisibility(VISIBLE);
        int columns=wide?2:1,visible=0;
        LinearLayout line=null;
        for(Achievements.Award a:Achievements.Award.values())if(a.category.ordinal()==index){
            if(visible++%columns==0){
                line=new LinearLayout(c);line.setBaselineAligned(false);cards.addView(line,new LayoutParams(-1,-2));
            }
            boolean earned=awards.earned(a);
            LinearLayout row=new LinearLayout(c);row.setGravity(Gravity.CENTER_VERTICAL);pad(row,9,8);
            final int item=awardOrder.size();awardOrder.add(a);awardRows.add(row);
            row.setMinimumHeight(dp(c,52));row.setPadding(dp(c,15),dp(c,7),dp(c,9),dp(c,7));
            row.setBackground(new BillSelect(getResources().getDisplayMetrics().density,false));
            row.setSelected(item==0);row.setSoundEffectsEnabled(false);
            row.setOnClickListener(v->{selectAward(item,false);openAward();});
            LayoutParams slot=new LayoutParams(0,-1,1);slot.setMargins(dp(c,2),dp(c,2),dp(c,2),dp(c,2));
            line.addView(row,slot);
            row.addView(new Medal(c,earned,a),new LayoutParams(dp(c,34),dp(c,40)));
            LinearLayout copy=stack(c);copy.setPadding(dp(c,8),0,0,0);row.addView(copy,new LayoutParams(0,-2,1));
            TextView name=text(c,a.title.toUpperCase(java.util.Locale.ROOT),19,earned?GOLD:0xffffe8c1,fonts.stencil);
            name.setShadowLayer(1,0,1,0xff24160c);copy.addView(name);
            copy.addView(text(c,a.goal,15,GameInk.TEXT,fonts.body));
            if(a.points>0)row.addView(text(c,"\u25c6 "+a.points,15,GOLD,fonts.stencil));
            row.setContentDescription(a.title+". "+(a.points>0?a.points+" Fight Score. ":"")+(earned?"Earned. ":"Locked. ")+a.goal);
            row.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        }
        if(wide && visible%2==1)line.addView(new View(c),new LayoutParams(0,1,1));
        final View selectedTab=tabs[index];
        selectedTab.post(()->selectedTab.requestRectangleOnScreen(new Rect(0,0,selectedTab.getWidth(),selectedTab.getHeight()),false));
        scroll.scrollTo(0,0);
    }
    private void selectAward(int index,boolean audible){
        if(index<0 || index>=awardOrder.size() || index==chosenAward)return;
        awardRows.get(chosenAward).setSelected(false);chosenAward=index;
        View row=awardRows.get(index);row.setSelected(true);
        row.requestRectangleOnScreen(new Rect(0,0,row.getWidth(),row.getHeight()),true);
        if(audible){
            sound.accept(DiscMenuSounds.Cue.VERTICAL);
            row.announceForAccessibility(row.getContentDescription());
        }
    }
    private void openAward(){
        if(awardDetail!=null || awardOrder.isEmpty())return;
        Context c=getContext();Achievements.Award award=awardOrder.get(chosenAward);
        boolean earned=awards.earned(award);awardScroll=scroll.getScrollY();
        for(int i=0;i<cards.getChildCount();i++)cards.getChildAt(i).setVisibility(GONE);
        LinearLayout sheet=stack(c);sheet.setBackgroundColor(GameInk.PAPER);pad(sheet,18,15);
        LinearLayout header=new LinearLayout(c);header.setGravity(Gravity.CENTER_VERTICAL);sheet.addView(header);
        header.addView(new Medal(c,earned,award),new LayoutParams(dp(c,wide?90:66),dp(c,wide?90:66)));
        LinearLayout title=stack(c);title.setPadding(dp(c,12),0,0,0);header.addView(title,new LayoutParams(0,-2,1));
        title.addView(text(c,earned?"EARNED":"LOCKED",15,GameInk.NAME_RED,fonts.body));
        title.addView(text(c,award.title,wide?32:25,GameInk.DARK,fonts.stencil));
        View rule=new View(c);rule.setBackgroundColor(GameInk.ORANGE);
        LayoutParams edge=new LayoutParams(-1,dp(c,2));edge.setMargins(0,dp(c,10),0,dp(c,10));sheet.addView(rule,edge);
        sheet.addView(text(c,earned?award.deed:award.goal,19,GameInk.DARK,fonts.body));
        if(award.points>0)sheet.addView(text(c,award.points+" Fight Score",15,GameInk.NAME_RED,fonts.body));
        awardDetail=sheet;cards.addView(sheet);scroll.scrollTo(0,0);
        for(MenuPrompt action:detailsActions)action.setVisibility(GONE);
        sound.accept(DiscMenuSounds.Cue.FORWARD);
        sheet.announceForAccessibility(award.title+". "+(earned?award.deed:award.goal));
    }
    boolean closeAward(){
        if(awardDetail==null)return false;
        cards.removeView(awardDetail);awardDetail=null;
        for(int i=0;i<cards.getChildCount();i++)cards.getChildAt(i).setVisibility(VISIBLE);
        for(MenuPrompt action:detailsActions)action.setVisibility(VISIBLE);
        int category=selected,position=awardScroll;
        scroll.post(()->{if(awardDetail==null && selected==category)scroll.scrollTo(0,position);});
        return true;
    }
    boolean handleKey(KeyEvent event,boolean transitioning){
        int code=event.getKeyCode();
        if(code==KeyEvent.KEYCODE_BUTTON_B){
            int device=event.getDeviceId();
            if(event.getAction()==KeyEvent.ACTION_DOWN && event.getRepeatCount()==0)backPresses.put(device,true);
            if(event.getAction()==KeyEvent.ACTION_UP){
                // The press that backed out of retail EA Trax can finish on this new parent.
                boolean pressedHere=backPresses.get(device);
                backPresses.delete(device);
                if(pressedHere && !event.isCanceled())close.run();
            }
            return true;
        }
        if(code==KeyEvent.KEYCODE_VOLUME_UP || code==KeyEvent.KEYCODE_VOLUME_DOWN ||
                code==KeyEvent.KEYCODE_VOLUME_MUTE || event.isSystem())return false;
        boolean direction=code==KeyEvent.KEYCODE_DPAD_UP || code==KeyEvent.KEYCODE_DPAD_DOWN ||
                code==KeyEvent.KEYCODE_BUTTON_L1 || code==KeyEvent.KEYCODE_BUTTON_R1;
        if(!transitioning || direction)navigate(event);
        return true;
    }
    boolean navigate(KeyEvent event){
        int code=event.getKeyCode();
        if(music!=null){
            boolean step=code==KeyEvent.KEYCODE_DPAD_UP || code==KeyEvent.KEYCODE_DPAD_DOWN ||
                    code==KeyEvent.KEYCODE_BUTTON_L1 || code==KeyEvent.KEYCODE_BUTTON_R1;
            boolean toggle=code==KeyEvent.KEYCODE_BUTTON_A || code==KeyEvent.KEYCODE_DPAD_CENTER ||
                    code==KeyEvent.KEYCODE_ENTER || code==KeyEvent.KEYCODE_DPAD_LEFT || code==KeyEvent.KEYCODE_DPAD_RIGHT;
            boolean confirm=toggle && code!=KeyEvent.KEYCODE_DPAD_LEFT && code!=KeyEvent.KEYCODE_DPAD_RIGHT;
            if(event.getAction()==KeyEvent.ACTION_UP && code==openKey && event.getDeviceId()==openDevice){
                openKey=-1;
                if(!event.isCanceled() && selected==2)music.openTrax();
                return true;
            }
            if(event.getAction()==KeyEvent.ACTION_DOWN && event.getRepeatCount()==0){
                openKey=-1;
                if(selected==2 && confirm){openKey=code;openDevice=event.getDeviceId();}
                else if(step)moveMusic(code==KeyEvent.KEYCODE_DPAD_UP || code==KeyEvent.KEYCODE_BUTTON_L1?-1:1);
                else if(toggle && (selected!=2 || code!=KeyEvent.KEYCODE_DPAD_LEFT && code!=KeyEvent.KEYCODE_DPAD_RIGHT))toggleMusic(selected);
            }
            return step || toggle;
        }
        switch(code){
            case KeyEvent.KEYCODE_BUTTON_A:case KeyEvent.KEYCODE_DPAD_CENTER:case KeyEvent.KEYCODE_ENTER:
                if(event.getAction()==KeyEvent.ACTION_DOWN && event.getRepeatCount()==0)openAward();
                return true;
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
                if(!page && awardDetail==null){
                    if(event.getAction()==KeyEvent.ACTION_DOWN)selectAward(chosenAward+direction,true);
                    return true;
                }
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
    static View ribbon(Context c,DiscFonts f,Achievements.Award a,int hostWidth){
        LinearLayout banner=new LinearLayout(c);banner.setGravity(Gravity.CENTER_VERTICAL);
        banner.setBackground(plate(0xfb29221e,0xffbb8a4e));pad(banner,12,7);
        banner.addView(new Medal(c,true,a),new LayoutParams(dp(c,48),dp(c,48)));
        LinearLayout words=stack(c);words.setPadding(dp(c,9),0,0,0);
        int labelWidth=Math.max(1,Math.min(dp(c,300),hostWidth-dp(c,72)));
        banner.addView(words,new LayoutParams(labelWidth,LayoutParams.WRAP_CONTENT));
        TextView kicker=text(c,"ACHIEVEMENT UNLOCKED   \u25c6 "+a.points,12,GOLD,f.stencil);kicker.setLetterSpacing(.08f);words.addView(kicker);
        words.addView(text(c,a.title,22,PAPER,f.stencil));
        TextView deed=text(c,a.deed,15,PAPER,f.body);
        words.addView(deed);
        banner.setContentDescription("Achievement unlocked. "+a.title+". "+a.deed+" "+a.points+" Fight Score.");return banner;
    }
    private static final class Medal extends View {

        private static Bitmap atlas;
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        private final Rect src=new Rect();
        private final RectF dest=new RectF();
        Medal(Context c,boolean earned,Achievements.Award award){
            super(c);
            if(atlas==null)try(java.io.InputStream in=c.getAssets().open("achievement-badges.webp")){
                atlas=android.graphics.BitmapFactory.decodeStream(in);
            }catch(java.io.IOException e){android.util.Log.w("FightNight","Achievement artwork unavailable",e);}
            int cell=award.ordinal();
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
