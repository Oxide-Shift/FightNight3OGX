package org.oxideshift.corner;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;

import static org.oxideshift.corner.CornerControls.Key;
import static org.oxideshift.corner.LiveFrame.Scene;

/** A second-screen corner desk fed by the same published Xbox frame as the upper display. */
final class RingsideView extends LinearLayout {
    static final int INK=0xff171310, PAPER=GameInk.TEXT, GOLD=GameInk.GOLD;
    private static final int PANEL=0xff302720,RULE=0xff514239, RED=GameInk.RED_CORNER, BLUE=GameInk.BLUE_CORNER;
    private Typeface headline=DiscFonts.FALLBACK.stencil;
    private DiscFonts usedFonts;
    private final CornerControls.Output pad;
    private final CutmanStroke.Stick stick;
    private final Hero hero;
    private final FightCallout announcement=new FightCallout();
    private BoutAnnouncements.Notice announced;
    private final View rule;
    private final TextView launchHint;
    private final OpeningSlate openingSlate;
    private final LinearLayout content;
    private final ScrollView scroll;
    private final FrameLayout desk;
    private final LinearLayout menuDock;
    private final TextView[] nameplates=new TextView[2];
    private final TextView[] records=new TextView[2],punchCounts=new TextView[2];
    private FightBroadcast.Fighters fighters;
    private final MenuContext menuContext=new MenuContext();
    private MenuContext.Page currentPage;
    private int missingPageReads;
    private final LinearLayout careerActions;
    private boolean compactCareer;
    private CareerDesk.Page careerPage;
    private CareerDesk.Page editorPage;
    private BoxerEditorDesk editorDesk;
    private final BoxerEditorDesk.Notebook notebook=new BoxerEditorDesk.Notebook();
    private boolean editorVisit;
    private long careerRevision=-1;
    private boolean careerDetails;
    private final MenuTips tips=new MenuTips();
    private FightBroadcast.Camp displayedCamp;
    private static final String[] CORNER_TIPS={
        "Watch the stamina bars after a long combination. A tired opponent can leave an opening for a counter.",
        "Use the jab to find your range before you throw the rear hand.",
        "Check the scorecard between rounds. Each judge can see a close round differently."
    };
    private TextView menuHint,menuLead;
    private RootIllustration rootIllustration;
    private Bitmap[] rootArt;
    void setRootArt(Bitmap[] value){
        rootArt=value;if(rootIllustration!=null)rootIllustration.setPlates(value);
    }
    private CareerScouting scouting;
    private ContractReport contract;
    private TrainingSheet training;
    private Bitmap[] trainingArt;
    private CareerArt careerArt;
    private final CareerBackdrop gymWall=new CareerBackdrop(INK,0xeb211008,0xa92d130a);
    private final CareerBackdrop shopWall=new CareerBackdrop(INK,0xdf1e1009,0x8034190a);
    private final CareerBackdrop contractWall=new CareerBackdrop(INK,0xe022120b,0xb33b1f0e);
    void setCareerArt(CareerArt art){
        if(careerArt==art)return;
        careerArt=art;
        gymWall.setArt(art==null?null:art.training);
        shopWall.setArt(art==null?null:art.store);
        contractWall.setArt(art==null?null:art.contract);
        if(training!=null)training.setBackdrop(art);
        if(contract!=null)contract.setArt(art);
        if(ledger!=null)ledger.setArt(art);
    }
    void setTrainingArt(Bitmap[] art){
        if(trainingArt==art)return;
        trainingArt=art;if(training!=null)training.setArt(art);
    }
    private TrainingSheet trainingSheet(){
        TrainingSheet sheet=new TrainingSheet(getContext(),usedFonts==null?DiscFonts.FALLBACK:usedFonts,
                getWidth()<=dp(360) || getHeight()<=dp(320));
        sheet.setArt(trainingArt);sheet.setBackdrop(careerArt);return sheet;
    }
    private CampLedger ledger;
    private boolean careerGuide;
    private boolean careerBranch;
    private int careerFocus=-1;
    private static final String[] CENTRAL_HEADINGS={"Fight preparation","Fight store","Trophy case","Edit boxer","Career options"};
    private static final String[][] CENTRAL_NOTES={
        {"Lead with the jab and see how your opponent answers before you throw the rear hand.",
         "A high knockout count tells you how often a win ended early. Check how many bouts that boxer has fought.",
         "After a long combination, step out while your stamina comes back.",
         "A body shot can draw the guard down. Watch the hands before you follow upstairs.",
         "The judges upstairs write the scorecards. Your corner downstairs reads those same cards between rounds."},
        {"Check your cash before you buy. Compare the price with what the item adds to your boxer.",
         "Pick one attribute to build around, then compare the gear that helps it.",
         "Check your balance again after a bout before making your next purchase."},
        {"Look back over the milestones your boxer has earned.",
         "Visit the trophy case after a title fight to review your career achievements."},
        {"Use the shoulder controls shown in the editor to move between pages.",
         "Check your stance before leaving the editor. It determines which hand leads your jab."},
        {"Check your round length here before the next bell.",
         "If the crowd drowns out your coach, open Audio and adjust Speech and Crowd separately."}
    };
    private static final String[] CAMP_TIPS={
        "The record sheet follows your career boxer, including a legend you rebuilt.",
        "Check your balance before picking up gear. Your purse can change what you can afford.",
        "The matchup appears when a fight is scheduled. Return after signing to scout your opponent."
    };
    private long lastMenuRead;
    private int lastMenuSequence;
    private long nextFooterRead,rootSettleUntil;
    private boolean followingRootMotion(long now){return now<rootSettleUntil;}
    private boolean wasAtRoot,cabinetCovered;
    private long lastRecognizedAt;
    private String resultHeading="";
    private CutmanDesk cutman;
    private final DeskScene deskScene=new DeskScene();
    private final BroadcastCues cues=new BroadcastCues();
    private int footerMask;
    private final LinearLayout fightPanel;
    private TextView roundLabel,roundValue;
    private ClockFlank clockFlank;
    private FightBroadcast.Card flankCard;
    private FightBroadcast.Bout bout;
    private final Paint backdropPaint=new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Rect backdropSrc=new Rect(),backdropDst=new Rect();
    private final Rect rockedArea=new Rect();
    private final Runnable tick=this::refreshFrame;
    private Scene scene;
    private GameScreen screen;
    private int sequence;
    private long lastSeen;
    private boolean activeView;
    private CornerSession session;
    private FightBroadcast.Card lastCard;
    private LinearLayout judgePanel,punchPanel;
    private TextView scoreHeading;
    private final TextView[] numbers=new TextView[4];
    private final ProgressBar[] meters=new ProgressBar[4];
    private final int[] levels={-1,-1,-1,-1};
    private Bitmap cornerArt;
    int traxInset(){
        return desk.getVisibility()==VISIBLE?Math.max(0,getHeight()-desk.getBottom()):dp(48);
    }
    private boolean forceCapture;

    RingsideView(Context ctx,CornerControls.Output pad,CutmanStroke.Stick stick){
        super(ctx);this.pad=pad;this.stick=stick;
        setOrientation(VERTICAL);setBackgroundColor(INK);setWillNotDraw(false);
        launchHint=type("Choose your settings and launch the game.",22,PAPER);
        launchHint.setAllCaps(true);launchHint.setTypeface(headline);launchHint.setLetterSpacing(.04f);
        launchHint.setShadowLayer(dp(3),dp(2),dp(2),0xff55291a);
        launchHint.setBackground(new CornerInk(INK,0xff713322));
        launchHint.setGravity(Gravity.CENTER);launchHint.setPadding(dp(24),0,dp(24),0);
        addView(launchHint,new LayoutParams(-1,-1));
        openingSlate=new OpeningSlate(ctx);addView(openingSlate,new LayoutParams(-1,-1));
        hero=new Hero(ctx);addView(hero,new LayoutParams(-1,dp(115)));
        rule=new View(ctx);rule.setBackgroundColor(GOLD);addView(rule,new LayoutParams(-1,dp(2)));
        fightPanel=new LinearLayout(ctx);fightPanel.setOrientation(VERTICAL);
        fightPanel.setPadding(dp(12),0,dp(12),0);addView(fightPanel,new LayoutParams(-1,-2));
        makeFighters();
        scroll=new ScrollView(ctx);scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false);
        desk=new FrameLayout(ctx);
        addView(desk,new LayoutParams(-1,0,1));desk.addView(scroll,new FrameLayout.LayoutParams(-1,-1));
        content=new LinearLayout(ctx);content.setOrientation(VERTICAL);content.setPadding(dp(12),dp(7),dp(12),dp(7));
        scroll.addView(content);
        careerActions=new LinearLayout(ctx);careerActions.setOrientation(VERTICAL);
        careerActions.setPadding(dp(6),0,dp(6),0);
        addView(careerActions,new LayoutParams(-1,-2));
        menuDock=new LinearLayout(ctx);menuDock.setOrientation(VERTICAL);
        menuDock.setBackgroundColor(0xff310e07);
        menuDock.setPadding(dp(8),0,dp(8),dp(3));addView(menuDock,new LayoutParams(-1,-2));
        build();
    }
    int dp(float v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private TextView type(String text,int size,int color){
        TextView view=new TextView(getContext());view.setText(text);view.setTextSize(size);
        view.setTextColor(color);view.setTypeface(headline);view.setIncludeFontPadding(false);
        return view;
    }
    private Drawable field(int color,int edge){return new CornerInk(color,edge);}
    private LayoutParams cell(int height){
        LayoutParams lp=new LayoutParams(0,height,1);lp.setMargins(dp(2),dp(3),dp(2),dp(2));return lp;
    }
    private TextView label(String s){
        TextView v=type(s,15,GOLD);v.setTypeface(headline);v.setLetterSpacing(.045f);v.setPadding(dp(2),dp(6),0,dp(3));content.addView(v);
        return v;
    }
    private void fighter(LinearLayout pair,String name,int color,int index){
        LinearLayout card=new LinearLayout(getContext());card.setOrientation(VERTICAL);card.setBackground(field(PANEL,RULE));
        pair.addView(card,cell(-2));
        View edge=new View(getContext());edge.setBackgroundColor(color);card.addView(edge,new LayoutParams(-1,dp(3)));
        TextView corner=type(name,12,PAPER);corner.setTypeface(headline);corner.setLetterSpacing(.04f);corner.setPadding(dp(8),dp(5),0,0);card.addView(corner);
        TextView nameplate=type("",22,PAPER);nameplate.setTypeface(headline);
        nameplate.setMaxLines(2);nameplate.setGravity(Gravity.CENTER_VERTICAL);
        nameplate.setPadding(dp(8),0,dp(6),0);
        nameplate.setAutoSizeTextTypeUniformWithConfiguration(13,24,1,android.util.TypedValue.COMPLEX_UNIT_SP);
        nameplates[index]=nameplate;
        card.addView(nameplate,new LayoutParams(-1,dp(39)));
        records[index]=type("",14,GOLD);records[index].setPadding(dp(8),0,dp(6),dp(3));
        card.addView(records[index]);
        drawNames();
        meter(card,index*2,"Health",index==0?BLUE:RED);
        meter(card,index*2+1,"Stamina",GOLD);
    }
    private void drawNames(){
        for(int i=0;i<nameplates.length;i++){
            TextView v=nameplates[i];if(v==null)continue;
            String name=fighters==null?"":i==0?fighters.blue:fighters.red;
            if(!name.contentEquals(v.getText()))v.setText(name);
            v.setVisibility(name.isEmpty()?GONE:VISIBLE);
            FightBroadcast.Record rec=fighters==null?null:fighters.records[i];
            TextView record=records[i];
            if(record!=null){
                record.setVisibility(rec==null?GONE:VISIBLE);
                if(rec!=null)record.setText(rec.wins+"W   "+rec.losses+"L   "+rec.draws+"D");
            }
        }
    }
    private void makeFighters(){
        fightPanel.removeAllViews();
        FrameLayout clock=new FrameLayout(getContext());
        fightPanel.addView(clock,new LayoutParams(-1,-2));
        // One centered timekeeper line: [TIME] | [R#]. The round shares the clock's baseline area at a quieter size.
        LinearLayout line=new LinearLayout(getContext());line.setGravity(Gravity.CENTER);
        clock.addView(line,new FrameLayout.LayoutParams(-2,-2,Gravity.CENTER));
        roundValue=type("",40,PAPER);roundValue.setSingleLine(true);roundValue.setGravity(Gravity.CENTER);
        roundValue.setMinHeight(dp(50));
        line.addView(roundValue,new LinearLayout.LayoutParams(-2,-2));
        View rule=new View(getContext());rule.setBackgroundColor(GameInk.ORANGE);
        LinearLayout.LayoutParams ruleSize=new LinearLayout.LayoutParams(dp(3),dp(30));ruleSize.setMargins(dp(12),0,dp(12),0);
        line.addView(rule,ruleSize);
        roundLabel=type("",30,GOLD);roundLabel.setSingleLine(true);
        line.addView(roundLabel,new LinearLayout.LayoutParams(-2,-2));
        ClockFlank flank=new ClockFlank(getContext(),line);clockFlank=flank;flankCard=null;
        clock.addView(flank,0,new FrameLayout.LayoutParams(-1,dp(50),Gravity.CENTER_VERTICAL));   // = clock row minimum height
        line.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->flank.invalidate());
        LinearLayout pair=new LinearLayout(getContext());fightPanel.addView(pair);
        fighter(pair,"BLUE CORNER",BLUE,0);fighter(pair,"RED CORNER",RED,1);
        drawBout();
        showFighters();
    }
    private void showFighters(){
        boolean show=screen!=null && bout!=null &&
            (scene==Scene.ROUND || scene==Scene.PAUSE || scene==Scene.TOTALS ||
             (scene==Scene.RINGSIDE && (bout.seconds>0 && session!=null && session.judging()!=null &&
                 session.judging().last()<bout.round)) ||
             // Between rounds the cards carry names, records and both meters (Alpha 80 between-round screen).
             ((scene==Scene.CORNER || scene==Scene.RINGSIDE) && inBout() && bout.round>0));
        fightPanel.setVisibility(show?VISIBLE:GONE);
    }
    private void drawBout(){
        if(bout==null)return;
        String round="R"+bout.round;
        if(!round.contentEquals(roundLabel.getText()))roundLabel.setText(round);
        String time=FightBroadcast.Bout.time(bout.seconds);
        if(!time.contentEquals(roundValue.getText())){
            roundValue.setText(ClockDigits.format(time,roundValue.getPaint()));
            ((View)roundValue.getParent()).setContentDescription("Round "+bout.round+", "+time);
        }
        for(int i=0;i<4;i++)updateLevel(i,bout.levels[i]);
        drawPunches();
    }
    private void meter(LinearLayout parent,int i,String name,int tint){
        LinearLayout line=new LinearLayout(getContext());line.setGravity(Gravity.CENTER_VERTICAL);
        line.setPadding(dp(8),0,dp(8),0);parent.addView(line);
        line.addView(type(name,14,PAPER),new LayoutParams(0,-2,1));
        numbers[i]=type("—",14,PAPER);line.addView(numbers[i]);
        ProgressBar bar=new ProgressBar(getContext(),null,android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);bar.setProgressTintList(ColorStateList.valueOf(tint));
        bar.setProgressBackgroundTintList(ColorStateList.valueOf(RULE));meters[i]=bar;
        LayoutParams lp=new LayoutParams(-1,dp(5));lp.setMargins(dp(8),dp(1),dp(8),dp(6));parent.addView(bar,lp);
        drawMeter(i);
    }
    private void drawMeter(int i){
        if(meters[i]==null)return;
        int v=levels[i];meters[i].setProgress(Math.max(0,v));
        numbers[i].setText(v<0?"—":v+"%");
    }
    private void scoreRow(String[] values,boolean heading){
        LinearLayout row=new LinearLayout(getContext());judgePanel.addView(row);
        for(int i=0;i<values.length;i++){
            TextView v=type(values[i],heading?15:20,heading?GameInk.NAME_RED:GameInk.DARK);
            v.setGravity((i==0?Gravity.START:Gravity.CENTER_HORIZONTAL)|Gravity.CENTER_VERTICAL);
            int height=dp(heading?25:31);
            if(!heading && i>0){
                v.setTypeface(headline);
                v.setPadding(dp(3),0,dp(3),0);
                v.setMaxLines(1);
                v.setAutoSizeTextTypeUniformWithConfiguration(11,20,1,android.util.TypedValue.COMPLEX_UNIT_SP);
            }
            v.setMinHeight(height);
            row.addView(v,new LayoutParams(0,!heading && i>0?height:-2,i==0?1.25f:1f));
        }
    }
    private FightBroadcast.Card scorecard(){
        return session==null?null:scene==Scene.FINAL?session.scored():session.judging();
    }
    private void drawScorecard(){
        if(judgePanel==null)return;
        judgePanel.removeAllViews();
        FightBroadcast.Card card=scorecard();lastCard=card;
        int last=card==null?-1:card.last();
        scoreHeading.setVisibility(last<0?GONE:VISIBLE);
        judgePanel.setVisibility(last<0?GONE:VISIBLE);
        if(last<0)return;
        scoreRow(new String[]{"Blue – Red","Judge 1","Judge 2","Judge 3"},true);
        String[] total={"Total","","",""};
        for(int j=0;j<3;j++)total[j+1]=card.total(j,0)+"–"+card.total(j,1);
        scoreRow(total,false);
        for(int k=last;k>=0;k--){
            int[] points=card.rows[k];if(points==null)continue;
            String[] cells={"Round "+k,"","",""};
            for(int j=0;j<3;j++)cells[j+1]=points[2*j]+"–"+points[2*j+1];
            scoreRow(cells,false);
        }
    }
    private void drawPunches(){
        if(punchPanel==null)return;
        FightBroadcast.Bout latest=scene==Scene.FINAL && session!=null?session.totals():bout;
        int[] count=latest==null?null:latest.punches;
        punchPanel.setVisibility(count==null?GONE:VISIBLE);
        if(count==null)return;
        for(int side=0;side<2;side++){
            TextView view=punchCounts[side];
            if(view==null)continue;
            int landed=count[side*2],thrown=count[side*2+1];
            int key=side*2;
            if(landed==lastPunches[key] && thrown==lastPunches[key+1])continue;
            lastPunches[key]=landed;lastPunches[key+1]=thrown;
            view.setText(landed+" / "+thrown);
            view.setContentDescription((side==0?"Blue":"Red")+" corner: "+landed+" of "+thrown+" punches landed");
        }
    }
    private final int[] lastPunches={-1,-1,-1,-1};
    private void addPunches(){
        punchPanel=new LinearLayout(getContext());punchPanel.setOrientation(VERTICAL);
        content.addView(punchPanel);
        TextView title=type("PUNCHES LANDED / THROWN",15,GOLD);title.setTypeface(headline);
        title.setPadding(dp(2),dp(6),0,dp(3));punchPanel.addView(title);
        LinearLayout line=new LinearLayout(getContext());punchPanel.addView(line);
        for(int i=0;i<2;i++){
            TextView value=type("",18,PAPER);value.setTypeface(headline);value.setGravity(Gravity.CENTER);
            value.setBackground(field(PANEL,i==0?BLUE:RED));punchCounts[i]=value;
            line.addView(value,cell(dp(27)));
        }
        java.util.Arrays.fill(lastPunches,-1);
        drawPunches();
    }
    /** Between rounds (corner and ringside scenes of a live bout): the next round and up to two rule-based tips. */
    private void addBetweenRounds(){
        int next=BetweenRounds.upcoming(bout);
        if(next>0)hero.title="Round "+next+" next";
        if(next>0)desk.setBackground(new RoundMark(next,headline));
        // On the Thor the judges' card fills the lower half; keep these lines above it so they are visible without scrolling.
        int before=content.getChildCount();
        if(bout!=null && bout.punches!=null){
            if(punchPanel==null)addPunches();
            int[] p=bout.punches;
            String accuracy="ACCURACY   Blue "+(p[1]>0?p[0]*100/p[1]+"%":"—")+"   ·   Red "+(p[3]>0?p[2]*100/p[3]+"%":"—");
            TextView stat=type(accuracy,17,PAPER);stat.setTypeface(headline);stat.setPadding(dp(4),dp(8),dp(4),dp(2));
            content.addView(stat,new LayoutParams(-1,-2));
        }
        int[] downs=session==null?null:session.playerKnockdowns();
        if(downs!=null){
            TextView kd=type("KNOCKDOWNS   You scored "+downs[0]+"   ·   You took "+downs[1],17,PAPER);
            kd.setTypeface(headline);kd.setPadding(dp(4),dp(2),dp(4),dp(4));content.addView(kd,new LayoutParams(-1,-2));
        }
        java.util.List<String> tips=BetweenRounds.tips(bout==null?null:bout.levels,bout==null?null:bout.punches,
                session==null?-1:session.playerSide());
        if(!tips.isEmpty()){
            label("CORNER TIPS");
            for(String tip:tips){
                TextView v=type("TIP  ·  "+tip,16,PAPER);
                v.setTypeface(usedFonts==null?headline:usedFonts.body);v.setLineSpacing(dp(2),1f);
                v.setPadding(dp(8),dp(4),dp(8),dp(4));content.addView(v,new LayoutParams(-1,-2));
            }
        }
        // Move what was just added (punches if new, accuracy, knockdowns, tips) ahead of the judges' card.
        int card=scoreHeading!=null && scoreHeading.getParent()==content?content.indexOfChild(scoreHeading):-1;
        if(card>=0 && card<before){
            java.util.List<View> added=new java.util.ArrayList<>();
            while(content.getChildCount()>before){
                int last=content.getChildCount()-1;
                added.add(0,content.getChildAt(last));content.removeViewAt(last);
            }
            for(int i=0;i<added.size();i++)content.addView(added.get(i),card+i);
        }
    }
    private void addScorecard(){
        addPunches();
        scoreHeading=label("OFFICIAL SCORECARD");
        judgePanel=new LinearLayout(getContext());judgePanel.setOrientation(VERTICAL);
        judgePanel.setPadding(dp(8),dp(5),dp(8),dp(7));judgePanel.setBackground(field(GameInk.PAPER,0xff8f785d));
        content.addView(judgePanel);drawScorecard();
    }
    void useFonts(DiscFonts fonts){
        if(usedFonts==fonts)return;
        usedFonts=fonts;
        headline=fonts.stencil;
        openingSlate.stencil(headline);launchHint.setTypeface(headline);
        makeFighters();build();hero.invalidate();
    }
    void setCornerArt(Bitmap photo){
        if(cornerArt!=photo){cornerArt=photo;invalidate();}
    }
    void follow(GameScreen next,CornerSession owner){
        if(session==owner && screen==next)return;
        if(session!=null){session.lowerCallouts=false;}
        session=owner;editorPage=null;editorVisit=false;notebook.clear();careerBranch=false;careerFocus=-1;announced=null;announcement.clear();
        if(screen!=null){screen.lowerShows(BroadcastCues.NONE);screen.timing.suspend();}
        deskScene.reset();wasAtRoot=false;cabinetCovered=false;nextFooterRead=0;currentPage=null;missingPageReads=0;tips.leave();displayedCamp=null;lastMenuRead=0;screen=next;sequence=0;lastSeen=0;scene=null;footerMask=0;
        lastCard=null;bout=null;fighters=null;drawNames();java.util.Arrays.fill(levels,-1);
        boolean opening=next!=null && owner!=null && owner.openingSlate();
        if(opening){scene=Scene.INTRO;deskScene.observe(scene,0,SystemClock.uptimeMillis());}
        animate().cancel();setTranslationX(0);setAlpha(1);
        build();schedule();

    }
    boolean showingMenu(){return scene==Scene.MENU || scene==Scene.PAUSE;}
    void releaseInput(){if(cutman!=null)cutman.release();}
    void activate(boolean value){
        if(activeView==value)return;
        activeView=value;syncDisplayOwnership();
        if(!value){if(cutman!=null)cutman.release();if(screen!=null){screen.timing.suspend();screen.lowerShows(BroadcastCues.NONE);}}
        else forceCapture=true;schedule();
    }
    void frontChanged(){
        boolean root=session!=null && session.atRoot();
        if(wasAtRoot && !root && MenuContext.isMain(currentPage)){
            currentPage=null;missingPageReads=0;tips.leave();
        }
        wasAtRoot=root;nextFooterRead=0;
        // The scroll callback can precede the caption's first visible changed frame.
        rootSettleUntil=root?SystemClock.uptimeMillis()+350:0;
        forceCapture=true;schedule();
    }
    private void schedule(){
        removeCallbacks(tick);
        if(activeView && isAttachedToWindow() && screen!=null)post(tick);
    }
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();syncDisplayOwnership();schedule();}
    @Override protected void onDetachedFromWindow(){
        if(session!=null){session.lowerCallouts=false;}
        removeCallbacks(tick);
        if(screen!=null){screen.timing.suspend();screen.lowerShows(BroadcastCues.NONE);}
        super.onDetachedFromWindow();
    }
    private void build(){
        if(editorDesk!=null){desk.removeView(editorDesk);editorDesk=null;}
        scroll.setVisibility(VISIBLE);
        if(cutman!=null){cutman.release();removeView(cutman);cutman=null;}
        menuDock.removeAllViews();menuDock.setVisibility(scene==Scene.MENU && screen!=null?VISIBLE:GONE);
        content.removeAllViews();rootIllustration=null;scroll.scrollTo(0,0);menuHint=null;menuLead=null;scouting=null;contract=null;training=null;ledger=null;judgePanel=null;punchPanel=null;java.util.Arrays.fill(punchCounts,null);
        resultHeading="";hero.career=false;careerActions.removeAllViews();careerActions.setVisibility(GONE);compactCareer=false;careerPage=null;careerDetails=false;careerRevision=-1;
        boolean showingHome=screen==null;
        launchHint.setVisibility(showingHome?VISIBLE:GONE);
        hero.setVisibility(showingHome?GONE:VISIBLE);
        rule.setVisibility(showingHome?GONE:VISIBLE);
        desk.setVisibility(showingHome?GONE:VISIBLE);desk.setBackground(null);
        boolean intro=!showingHome && scene==Scene.INTRO;
        openingSlate.setVisibility(intro?VISIBLE:GONE);
        if(!showingHome && editorPage!=null){
            hero.setVisibility(GONE);rule.setVisibility(GONE);scroll.setVisibility(GONE);
            fightPanel.setVisibility(GONE);menuDock.setVisibility(GONE);
            editorDesk=new BoxerEditorDesk(getContext(),headline,editorPage,notebook,session.camp());
            desk.addView(editorDesk,new FrameLayout.LayoutParams(-1,-1));return;
        }
        if(intro){
            hero.setVisibility(GONE);rule.setVisibility(GONE);desk.setVisibility(GONE);
            menuDock.setVisibility(GONE);fightPanel.setVisibility(GONE);
            return;
        }
        if(showingHome){
            fightPanel.setVisibility(GONE);
            return;
        }else if(scene==Scene.LOADING || scene==Scene.TRAINING){
            careerPage=session==null?null:session.career.page;
            if(careerPage!=null && careerPage.is("drill")){
                desk.setBackground(gymWall);
                hero.career=true;compactCareer=true;hero.title=careerPage.title;
                hero.setVisibility(GONE);rule.setVisibility(GONE);
                training=trainingSheet();
                content.addView(training,new LayoutParams(-1,-2));
                training.show(session.career,careerPage);careerRevision=session.career.revision;
            }else{
                hero.title="Loading";
                quietHint("Waiting for the next screen…");
            }
        }else if(scene==Scene.ROUND){
            hero.title="In your corner";
            addScorecard();
        }else if(scene==Scene.TREATMENT){
            hero.setVisibility(GONE);rule.setVisibility(GONE);desk.setVisibility(GONE);
            cutman=new CutmanDesk(getContext(),usedFonts!=null?usedFonts:DiscFonts.FALLBACK,stick,
                    ()->verified()!=null,pad);
            cutman.time(session==null?-1:session.recoverySeconds());
            addView(cutman,new LayoutParams(-1,-1));
        }else if(scene==Scene.PAUSE){
            hero.title="Paused";
            addScorecard();
        }else if(scene==Scene.FINAL || scene==Scene.TOTALS){
            boolean end=scene==Scene.FINAL;
            resultHeading=session.result()+session.finish();
            hero.title=end?finalHeading():"Fight totals";
            FightBroadcast.Fighters names=end?session.lastNames():session.fighters();
            TextView matchup=type(names==null?"Blue corner  /  Red corner":names.blue+"  /  "+names.red,20,PAPER);
            matchup.setTypeface(headline);matchup.setPadding(dp(5),dp(6),dp(5),dp(4));content.addView(matchup);
            if(end && !session.finish().isEmpty())label(session.finish().equals("KO")?"KNOCKOUT":"TECHNICAL KNOCKOUT");
            addScorecard();
        }else if(scene==null){
            hero.title="Fight Night Round 3";
            quietHint("Starting the game…");
        }else{
            boolean menu=scene==Scene.MENU;
            hero.title=menu?((footerMask&4)!=0?"Boxer options":"Menu options"):"Ringside";
            if(!menu && session!=null && session.judging()!=null && session.judging().round>0)addScorecard();
            if(!menu && inBout())addBetweenRounds();
            if(menu || !inBout()){
                careerGuide=false;
                menuLead=type("",23,PAPER);
                menuLead.setPadding(dp(10),dp(14),dp(10),dp(2));
                menuLead.setVisibility(GONE);content.addView(menuLead,new LayoutParams(-1,-2));
                menuHint=type("",18,GameInk.MUTED);
                menuHint.setTypeface(usedFonts==null?headline:usedFonts.body);
                menuHint.setMaxWidth(dp(570));menuHint.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
                menuHint.setLineSpacing(dp(2),1f);menuHint.setPadding(dp(10),dp(8),dp(10),dp(8));
                content.addView(menuHint,new LayoutParams(-1,-2));
                rootIllustration=new RootIllustration(getContext());rootIllustration.setPlates(rootArt);
                content.addView(rootIllustration,new LayoutParams(-1,0,1));
                describeMenu();
            }
            if(menu)buildMenuDock();
        }
        showFighters();sizeHero();hero.setContentDescription(hero.title);hero.invalidate();
    }
    private void buildMenuDock(){
        menuDock.removeAllViews();menuDock.setVisibility(footerMask==0?GONE:VISIBLE);
        if(footerMask==0)return;
        LinearLayout keys=new LinearLayout(getContext());keys.setBaselineAligned(false);
        menuDock.addView(keys,new LayoutParams(-1,-2));
        for(int i=0;i<BroadcastCues.KEYS.length;i++)if((footerMask&(1<<i))!=0)guestButton(keys,i);
    }
    private void careerPanels(CareerDesk.Page page,boolean scout){
        if(scout && scouting==null){
            scouting=new CareerScouting(getContext(),headline);
            LayoutParams sheet=new LayoutParams(-1,-2);sheet.setMargins(dp(3),dp(5),dp(3),dp(4));
            content.addView(scouting,0,sheet);
        }
        if(page==null)return;
        DiscFonts fonts=usedFonts==null?DiscFonts.FALLBACK:usedFonts;
        if(page.is("mcon") && contract==null){
            contract=new ContractReport(getContext(),fonts,this::browseOffer);contract.setArt(careerArt);
            contract.setOnClickListener(v->{if(activeOfferFrame()!=null)pad.press(Key.X);});
            content.addView(contract,0,new LayoutParams(-1,-2));
        }else if(TrainingSheet.handles(page) && training==null){
            training=trainingSheet();content.addView(training,0,new LayoutParams(-1,-2));
        }else if((page.is("fightstore") || page.is("trophycase")) && ledger==null){
            ledger=new CampLedger(getContext(),fonts);ledger.setArt(careerArt);
            content.addView(ledger,0,new LayoutParams(-1,-2));
        }
    }
    private void quietHint(String text){
        menuHint=type(text,15,PAPER);menuHint.setTypeface(headline);
        menuHint.setGravity(Gravity.CENTER);menuHint.setPadding(dp(6),dp(4),dp(6),dp(8));
        content.addView(menuHint,new LayoutParams(-1,-2));
    }
    private void describeMenu(){
        if(menuHint==null || (scene!=Scene.MENU && scene!=Scene.RINGSIDE))return;
        FightBroadcast.Camp camp=session==null?null:session.camp();
        CareerDesk desk=session==null?null:session.career;
        boolean root=MenuContext.isMain(currentPage);
        if(rootIllustration!=null)rootIllustration.select(root?currentPage:null);
        CareerDesk.Page page=desk==null || root?null:desk.page;
        boolean central=(page!=null && page.is("careercentral") && page.index==1) ||
                (page==null && currentPage!=null && "Career Central".equals(currentPage.title));
        if(desk!=null)careerRevision=desk.revision;
        boolean changed=page!=careerPage;
        if(changed){careerPage=page;careerDetails=false;scroll.scrollTo(0,0);}
        if(changed)buildCareerActions(page,central);
        boolean setup=!root && careerSetup(currentPage);
        boolean deeper=careerBranch && currentPage==null && camp!=null;
        String title=root?currentPage.title:currentPage!=null?currentPage.heading:deeper?"Camp notebook":"From your corner";
        if(menuLead!=null){
            menuLead.setVisibility(root?VISIBLE:GONE);
            if(root && !currentPage.heading.contentEquals(menuLead.getText()))menuLead.setText(currentPage.heading);
        }
        String[] choices=currentPage!=null?currentPage.tips:deeper?CAMP_TIPS:CORNER_TIPS;
        if(central && careerFocus>=0){
            title=CENTRAL_HEADINGS[careerFocus];choices=CENTRAL_NOTES[careerFocus];
        }
        if(central && careerFocus<0)title="Career Central";
        boolean dedicated=page!=null && !central;
        String hint;
        boolean offers=page!=null && page.is("mcon");
        if(dedicated){tips.leave();title=offers?"Scouting report":page.title;hint=offers?"":desk.text(page,careerDetails);}
        else hint=tips.select(title,choices,SystemClock.uptimeMillis());
        displayedCamp=camp;
        boolean scout=central || (deeper && !dedicated);
        careerPanels(page,scout);
        if(contract!=null)contract.show(page,offers?desk.selectedOffer():null);
        boolean atTraining=TrainingSheet.handles(page);
        boolean shop=page!=null && page.is("fightstore");
        Drawable wall=shop?shopWall:offers?contractWall:atTraining?gymWall:null;
        if(this.desk.getBackground()!=wall)this.desk.setBackground(wall);
        if(training!=null)training.show(desk,page);
        boolean atLedger=ledger!=null && ledger.show(desk,page);
        menuHint.setVisibility(offers || atTraining || (atLedger && !careerDetails)?GONE:VISIBLE);
        hero.setVisibility(offers || atTraining || shop?GONE:VISIBLE);rule.setVisibility(hero.getVisibility());
        if(scouting!=null)scouting.show(scout?camp:null);
        boolean guide=dedicated || (central && camp==null) || setup;
        if(guide!=careerGuide || dedicated!=compactCareer){
            careerGuide=guide;compactCareer=dedicated;sizeHero();
            menuHint.setBackground(guide?new CornerInk(dedicated?GameInk.TEXT:CareerScouting.FIELD,0xffa6412a):null);
            menuHint.setTextColor(dedicated?0xff37291e:guide?CareerScouting.TYPE:GameInk.MUTED);
            menuHint.setTextSize(dedicated?16:guide?22:18);
            menuHint.setLineSpacing(dp(dedicated?1:2),1);
            menuHint.setMinHeight(guide && !dedicated?dp(80):0);
            int side=dp(guide && !dedicated?17:10),vertical=dp(dedicated?5:guide?16:8);
            menuHint.setPadding(side,vertical,side,vertical);
        }
        if(!hint.contentEquals(menuHint.getText())){
            int line=hint.indexOf('\n');
            if(dedicated && line>0){
                android.text.SpannableString text=new android.text.SpannableString(hint);
                text.setSpan(new android.text.style.ForegroundColorSpan(0xff80331f),0,line,0);
                menuHint.setText(text);
            }else menuHint.setText(hint);
        }
        boolean career=central || setup || deeper || guide;
        if(hero.career!=career){hero.career=career;sizeHero();hero.invalidate();}
        boolean newTitle=!title.equals(hero.title);
        if(newTitle){hero.title=title;hero.setContentDescription(title);hero.invalidate();}
        if(career && (changed || newTitle) && isAttachedToWindow() && activeView &&
                android.animation.ValueAnimator.areAnimatorsEnabled()){
            hero.revealTitle();
            if(!offers){
                menuHint.animate().cancel();menuHint.setTranslationY(dp(5));
                menuHint.animate().translationY(0).setDuration(180).start();
            }
        }
    }
    private void careerAction(LinearLayout line,String label,Runnable action){
        TextView key=type(label,14,GOLD);
        key.setMaxLines(1);
        key.setMinHeight(dp(48));key.setGravity(Gravity.CENTER);key.setFocusable(true);
        key.setPadding(dp(3),0,dp(3),0);
        key.setBackground(new RippleDrawable(ColorStateList.valueOf(0x55efb556),
                field(0xff38271e,0xff975634),null));
        key.setOnClickListener(v->action.run());
        LayoutParams slot=new LayoutParams(0,-2,1);slot.setMargins(dp(2),0,dp(2),dp(2));line.addView(key,slot);
    }
    private LiveFrame activeOfferFrame(){
        CareerDesk.Page page=session==null?null:session.career.page;
        if(page==null || !page.is("mcon") || page.index!=1 || page!=careerPage || session.career.selectedOffer()==null)return null;
        LiveFrame frame=verified();
        return frame!=null && frame.scoutPrompt() && screen.current(frame)?frame:null;
    }
    private boolean browseOffer(boolean forward){
        if(activeOfferFrame()==null)return false;
        return pad.press(forward?Key.RIGHT:Key.LEFT);
    }
    private void buildCareerActions(CareerDesk.Page page,boolean central){
        careerActions.removeAllViews();
        boolean visible=page!=null && !central && !page.is("mcon") && !page.is("drill") &&
                !page.is("train") && !page.is("trophycase") && !page.is("fightstore");
        careerActions.setVisibility(visible?VISIBLE:GONE);
        if(!visible)return;
        LinearLayout line=new LinearLayout(getContext());careerActions.addView(line,new LayoutParams(-1,-2));
        careerAction(line,careerDetails?"Overview":"Corner notes",()->{
            if(session==null || session.career.page!=page)return;
            careerDetails=!careerDetails;buildCareerActions(page,false);describeMenu();
        });
    }
    private static boolean careerSetup(MenuContext.Page p){
        return p!=null && ("Start a New Career".equals(p.title) || "Create Champ Info".equals(p.title));
    }
    private void guestButton(LinearLayout group,int index){
        CornerControls.Key key=BroadcastCues.KEYS[index];
        MenuPrompt view=new MenuPrompt(getContext(),index,headline,()->{
            LiveFrame f=verified();if(f==null)return;
            BroadcastCues fresh=new BroadcastCues();fresh.read(f);
            if(fresh.knownPrompts(f,1<<index)!=0 && screen.current(f)){pad.press(key);}
        });
        group.addView(view,new LayoutParams(0,-2,1));
    }
    private void syncDisplayOwnership(){
        if(session==null)return;
        session.lowerCallouts=activeView && isAttachedToWindow() && screen!=null;
    }
    private int heroHeight(){return dp(hero.career?46:compactCareer?32:scene==Scene.MENU?46:scene==Scene.ROUND || scene==Scene.TOTALS?52:64);}
    private void sizeHero(){
        ViewGroup.LayoutParams lp=hero.getLayoutParams();int h=heroHeight();
        if(lp!=null&&lp.height!=h){lp.height=h;hero.setLayoutParams(lp);}
    }
    @Override protected void onSizeChanged(int w,int h,int oldW,int oldH){
        super.onSizeChanged(w,h,oldW,oldH);if(hero!=null)post(this::build);
    }
    private LiveFrame verified(){
        if(!activeView||screen==null||SystemClock.uptimeMillis()-lastSeen>1800)return null;
        LiveFrame f=screen.latestFrame();
        return f!=null && f.scene(inBout())==scene && screen.current(f)?f:null;
    }
    private void refreshFrame(){
        if(!activeView||!isAttachedToWindow()||screen==null)return;
        if(session!=null && session.cabinetOpen){
            if(!cabinetCovered){cabinetCovered=true;screen.lowerShows(BroadcastCues.NONE);menuDock.setAlpha(0f);}
            postDelayed(tick,80);return;
        }
        if(cabinetCovered){cabinetCovered=false;forceCapture=true;nextFooterRead=0;}
        syncReadings();
        LiveFrame f=screen.latestFrame();
        if(f==null && scene==null && session!=null && session.openingSlate()){scene=Scene.INTRO;build();}
        // A menu can stop publishing frames before its prompts finish settling.
        boolean checkResult=session!=null && session.showingResult() && SystemClock.uptimeMillis()-lastMenuRead>=400;
        boolean readSettledMenu=editorPage==null && (scene==Scene.MENU || (scene==Scene.RINGSIDE && !inBout())) &&
                f!=null && f.sequence!=lastMenuSequence && SystemClock.uptimeMillis()-lastMenuRead>=80;
        boolean changedPage=session!=null && editorPage!=session.career.page &&
                (editorPage!=null || CareerDesk.editingBoxer(session.career.page) && careerRevision!=session.career.revision);
        if(f!=null && (f.sequence!=sequence || forceCapture || deskScene.settling() || checkResult || changedPage || readSettledMenu))sample(f);
        if(scene==Scene.MENU && followingRootMotion(SystemClock.uptimeMillis()))postOnAnimation(tick);
        else postDelayed(tick,scene==Scene.MENU?(session!=null && session.atRoot()?50:80):110);
    }
    private void updateLevel(int i,int v){if(levels[i]!=v){levels[i]=v;drawMeter(i);}}
    private boolean inBout(){return session!=null && session.inLiveBout();}
    private void syncReadings(){
        if(session==null)return;
        if((scene==Scene.LOADING || scene==Scene.TRAINING) && careerPage!=session.career.page)build();
        if(scene==Scene.TRAINING && training!=null && careerRevision!=session.career.revision){
            careerRevision=session.career.revision;training.show(session.career,careerPage);
        }
        BoutAnnouncements.Notice news=session.callout();
        if(news==null && announced!=null){announced=null;announcement.clear();invalidate();}
        if(news!=null && news!=announced){
            announced=news;
            if(SystemClock.uptimeMillis()-news.at<FightCallout.DURATION){
                announcement.start(news.words,news.at);session.calloutShown(news);announceForAccessibility(news.words);invalidate();
            }
        }
        FightBroadcast.Bout latest=session.bout();
        if(latest!=bout){bout=latest;drawBout();showFighters();}
        FightBroadcast.Fighters names=session.fighters();
        if(names!=fighters){fighters=names;drawNames();}
        menuDock.setAlpha(session.cabinetOpen?0f:1f);
        openingSlate.movie(session.introMovie());
        if((scene==Scene.RINGSIDE || scene==Scene.MENU) && (tips.due(SystemClock.uptimeMillis()) || displayedCamp!=session.camp() || careerRevision!=session.career.revision))describeMenu();
        syncDisplayOwnership();
        if(cutman!=null){
            cutman.time(session.recoverySeconds());
            if(SystemClock.uptimeMillis()-lastSeen>1800)cutman.release();
        }
        if(judgePanel!=null && lastCard!=scorecard())drawScorecard();
        FightBroadcast.Card judged=session.judging();
        if(clockFlank!=null && judged!=flankCard){
            flankCard=judged;
            int[] won=judged==null?new int[2]:judged.roundsWon();
            clockFlank.setWins(won[0],won[1]);
        }
        if(scene==Scene.FINAL){
            if(!resultHeading.equals(session.result()+session.finish()))build();
            else {
                String heading=finalHeading();
                if(!heading.equals(hero.title)){hero.title=heading;hero.invalidate();}
            }
        }
    }
    private String finalHeading(){
        BoutAnnouncements.Notice cue=session.callout();
        if(cue!=null && cue.words.equals(session.result()) && session.fightCueVisible())return "Fight result";
        switch(session.result()){
            case "WON!!":return "Victory";
            case "LOST!!":return "Defeat";
            default:return session.result().isEmpty()?"Final bell":session.result();
        }
    }
    private void sample(LiveFrame f){
        CareerDesk.Page activePage=session==null?null:session.career.page;
        boolean inEditor=CareerDesk.editingBoxer(activePage);
        if(editorVisit && !inEditor)notebook.clear();
        editorVisit=inEditor;
        boolean live=inBout();
        CareerDesk.Page editor=inEditor && !live && !session.cabinetOpen?activePage:null;
        int front=session==null?CornerSession.FRONT_OTHER:session.frontPage;
        boolean root=!live && session!=null && session.atRoot();
        boolean playlist=front==CornerSession.FRONT_TRAX;
        Scene next=root || playlist || editor!=null?Scene.MENU:f.scene(live);
        boolean showingMenu=next==Scene.MENU || (next==Scene.RINGSIDE && !live);
        int mask=0;
        long sampled=SystemClock.uptimeMillis();
        if(next==Scene.MENU && !inEditor && !playlist){
            if(forceCapture || sampled>=nextFooterRead){
                cues.readMenu(f);
                nextFooterRead=sampled+(root?220:150);
            }
            mask=cues.mask;
        }else nextFooterRead=0;
        boolean readMenu=showingMenu && editor==null && !playlist &&
                (forceCapture || (f.sequence!=lastMenuSequence && sampled-lastMenuRead>=(root?(followingRootMotion(sampled)?12:45):80)));
        MenuContext.Page page=currentPage;
        int focus=-1;
        if(readMenu){
            page=menuContext.identify(f,root);
            if(root)page=MenuContext.rootSelection(page,currentPage,front);
            if(page!=null && "Career Central".equals(page.title))focus=menuContext.careerRow(f);
        }
        if(!screen.current(f))return;
        boolean editorChanged=editorPage!=editor;
        editorPage=editor;

        if(playlist && currentPage!=MenuContext.PLAYLIST){
            currentPage=MenuContext.PLAYLIST;careerFocus=-1;careerBranch=false;missingPageReads=0;describeMenu();
        }else if(readMenu){
            lastMenuRead=sampled;lastMenuSequence=f.sequence;
            MenuContext.Page previous=currentPage;
            int previousFocus=careerFocus;
            careerFocus=focus;
            if(page!=null)careerBranch="Career Central".equals(page.title) || careerSetup(page);

            if(page!=null){
                currentPage=page;missingPageReads=0;lastRecognizedAt=sampled;
            }else if(currentPage!=null && ++missingPageReads>=3 &&
                    // Title reads can miss on transition/highlight frames; dropping a submenu after two 80 ms misses
                    // flashed the generic "From your corner" card over Play Now and other pages.
                    sampled-lastRecognizedAt>=(MenuContext.isMain(currentPage)?900:700)){
                currentPage=null;
            }
            if(currentPage!=previous || careerFocus!=previousFocus)describeMenu();
        }else if(!showingMenu){tips.leave();currentPage=null;careerFocus=-1;careerBranch=false;missingPageReads=0;}
        long now=SystemClock.uptimeMillis();
        if(f.sequence!=sequence || forceCapture)lastSeen=now;
        sequence=f.sequence;forceCapture=false;
        if(next!=Scene.TREATMENT && cutman!=null)cutman.release();

        FightBroadcast.Card card=session==null?null:session.judging();
        Scene layout=deskScene.layout(next,card!=null && card.round>0 && card.last()<card.round);
        // The Xbox can retain the last judges' card back at Play Now. Only a
        // menu title recognized on this frame can dismiss the finished bout.
        if(session!=null && ((readMenu && page!=null) || (next==Scene.MENU && session.career.page!=null)))session.leaveResults();
        if(session!=null){
            if((next==Scene.MENU && session.introMovie()==0) || (readMenu && page!=null) || inBout())session.reachedMenu();
            if(session.openingSlate())layout=Scene.INTRO;
        }
        if(session!=null && session.showingResult() && next!=Scene.LOADING)layout=Scene.FINAL;
        if(activePage!=null && activePage.is("drill") && (next==Scene.LOADING || next==Scene.RINGSIDE || next==Scene.ROUND))
            layout=Scene.TRAINING;
        if(editor!=null){layout=Scene.MENU;mask=0;}
        boolean sceneChanged=deskScene.observe(layout,mask,now);
        if(sceneChanged || editorChanged){
            boolean buttonsOnly=!editorChanged && scene==Scene.MENU && deskScene.shown==scene && editor==null;
            scene=deskScene.shown;footerMask=deskScene.prompts;
            if(buttonsOnly)buildMenuDock();else build();
        }
        screen.timing.observe(next,session==null?0:session.boutNumber(),card==null?0:card.round,
                card!=null && card.round>0 && card.last()==card.round,layout==Scene.FINAL);
        int route=scene==Scene.MENU && next==scene && !inEditor && mask!=0?BroadcastCues.FOOTER:BroadcastCues.NONE;
        if(route==BroadcastCues.FOOTER)route=(footerMask&mask)==0?BroadcastCues.NONE:route|((footerMask&mask)<<8);
        if((route&255)==BroadcastCues.FOOTER && contract!=null && contract.getVisibility()==VISIBLE &&
                careerPage!=null && careerPage.index==1 && session!=null && careerPage==session.career.page &&
                session.career.selectedOffer()!=null && f.scoutPrompt())route|=BroadcastCues.SCOUT_CARD;
        screen.lowerShows(route,cues.centers);
    }

    @Override protected void dispatchDraw(Canvas canvas){
        super.dispatchDraw(canvas);
        if(announcement.isRocked() && fightPanel.getVisibility()==VISIBLE){
            int top=fightPanel.getBottom();
            if(punchPanel!=null && punchPanel.isShown()){
                punchPanel.getDrawingRect(rockedArea);
                offsetDescendantRectToMyCoords(punchPanel,rockedArea);
                top=Math.max(top,rockedArea.bottom);
            }
            top+=dp(3);
            if(getHeight()-top>=dp(48))rockedArea.set(0,top,getWidth(),getHeight());
            else rockedArea.set(Math.max(getWidth()/2,hero.titleRight+dp(6)),hero.getTop(),getWidth(),hero.getBottom());
        }else rockedArea.setEmpty();
        if(announcement.draw(canvas,getWidth(),getHeight(),headline,SystemClock.uptimeMillis(),rockedArea))postInvalidateOnAnimation();
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        if(cornerArt==null || screen==null)return;
        int h=Math.min(416,cornerArt.getHeight()), w=cornerArt.getWidth();
        float scale=Math.max(getWidth()/(float)w,getHeight()/(float)h);
        if(scale<=0)return;
        int sw=Math.min(w,Math.max(1,Math.round(getWidth()/scale)));
        int sh=Math.min(h,Math.max(1,Math.round(getHeight()/scale)));
        backdropSrc.set((w-sw)/2,(h-sh)/2,(w-sw)/2+sw,(h-sh)/2+sh);
        backdropDst.set(0,0,getWidth(),getHeight());backdropPaint.setAlpha(scene==Scene.ROUND || scene==Scene.PAUSE?34:scene==Scene.FINAL?60:130);
        canvas.drawBitmap(cornerArt,backdropSrc,backdropDst,backdropPaint);
    }

    private final class Hero extends View {
        String title="";boolean career;
        int titleRight;
        final Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);
        final Path stripe=new Path(),cornerRules=new Path();
        long titleStarted;
        void revealTitle(){titleStarted=SystemClock.uptimeMillis();invalidate();}
        Hero(Context context){super(context);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);}
        @Override protected void onSizeChanged(int w,int h,int ow,int oh){
            stripe.reset();stripe.moveTo(0,0);stripe.lineTo(dp(13),0);
            stripe.lineTo(dp(5),h);stripe.lineTo(0,h);stripe.close();
            cornerRules.reset();
            for(int i=0;i<3;i++){
                float x=w-dp(15+i*10);
                cornerRules.moveTo(x+dp(18),0);cornerRules.lineTo(x+dp(21),0);
                cornerRules.lineTo(x,h);cornerRules.lineTo(x-dp(3),h);cornerRules.close();
            }
        }
        @Override protected void onDraw(Canvas c){
            int h=getHeight();c.drawColor(career?0xeb43160e:0xec301913);ink.setColor(career?0xffca4029:GOLD);c.drawPath(stripe,ink);
            float progress=1;
            if(career){
                ink.setColor(0xff81412b);c.drawPath(cornerRules,ink);
                if(titleStarted>0){
                    progress=Math.min(1,(SystemClock.uptimeMillis()-titleStarted)/220f);
                    if(progress<1 && isShown())postInvalidateOnAnimation();
                    else titleStarted=0;
                }
                ink.setTypeface(headline);ink.setTextSize(dp(11));ink.setColor(GameInk.GOLD);
                c.drawText("CAREER",dp(23),dp(13),ink);
            }
            ink.setTypeface(headline);ink.setTextSize(dp(career?24:28));ink.setColor(PAPER);
            String text=title.toUpperCase(java.util.Locale.ROOT);
            float space=Math.max(1,getWidth()-dp(67)),width=ink.measureText(text);
            if(width>space)ink.setTextSize(ink.getTextSize()*space/width);
            float baseline=career?h-dp(6):(h-ink.ascent()-ink.descent())*.5f;
            float left=dp(23)+dp(10)*(1-progress)*(1-progress);
            titleRight=(int)Math.ceil(left+Math.min(width,space));
            c.drawText(text,left,baseline,ink);
        }
    }
}
