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
    private static final int PANEL=0xff302720,RULE=0xff514239, RED=0xff932f23, BLUE=0xff345672;
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
    private final BoxerEditorDesk.References editorRefs=new BoxerEditorDesk.References();
    private final BoxerEditor editorField=new BoxerEditor();
    private boolean editorDialog;
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
    private String resultHeading="";
    private CutmanDesk cutman;
    private final DeskScene deskScene=new DeskScene();
    private final BroadcastCues cues=new BroadcastCues();
    private int footerMask;
    private final LinearLayout fightPanel;
    private TextView roundLabel,roundValue;
    private FightBroadcast.Bout bout;
    private final Paint backdropPaint=new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Rect backdropSrc=new Rect(),backdropDst=new Rect();
    private final Runnable tick=this::refreshFrame;
    private Scene scene;
    private GameScreen screen;
    private int sequence;
    private long lastSeen;
    private boolean activeView;
    private CornerSession session;
    private FightBroadcast.Card lastCard;
    private LinearLayout judgePanel,punchPanel;
    private final TextView[] numbers=new TextView[4];
    private final ProgressBar[] meters=new ProgressBar[4];
    private final int[] levels={-1,-1,-1,-1};
    private final MenuFilm film=new MenuFilm();
    private final Paint filmInk=new Paint();
    private long lastFilmFrame;
    private boolean filmRunning;
    private int filmSequence;
    private final Runnable filmTick=new Runnable(){
        @Override public void run(){
            if(!filmRunning)return;
            if(!activeView || !isAttachedToWindow() || screen==null || session==null || session.cabinetOpen || inBout()){
                setFilmRunning(false);return;
            }
            long now=SystemClock.uptimeMillis();
            LiveFrame frame=screen.latestFrame();
            if(frame!=null && frame.sequence!=filmSequence){
                filmSequence=frame.sequence;lastFilmFrame=now;
                boolean changed=film.observe(frame);
                if(!screen.current(frame))clearFilm();
                else if(changed)invalidate();
            }else if(now-lastFilmFrame>=45)clearFilm();
            postOnAnimation(this);
        }
    };
    private void setFilmRunning(boolean run){
        if(filmRunning==run)return;
        filmRunning=run;removeCallbacks(filmTick);clearFilm();filmSequence=0;
        if(run)postOnAnimation(filmTick);
    }
    private void clearFilm(){if(film.clear())invalidate();}
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
    private void label(String s){
        TextView v=type(s,15,GOLD);v.setTypeface(headline);v.setLetterSpacing(.045f);v.setPadding(dp(2),dp(6),0,dp(3));content.addView(v);
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
        meter(card,index*2,"Health",index==0?0xff6faedb:0xffe98170);
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
        LinearLayout clock=new LinearLayout(getContext());clock.setGravity(Gravity.CENTER_VERTICAL);
        fightPanel.addView(clock);
        roundLabel=type("",14,GOLD);roundLabel.setTypeface(headline);clock.addView(roundLabel,cell(dp(36)));
        roundValue=type("",29,PAPER);roundValue.setTypeface(headline);roundValue.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);
        clock.addView(roundValue,cell(dp(36)));
        LinearLayout pair=new LinearLayout(getContext());fightPanel.addView(pair);
        fighter(pair,"BLUE CORNER",BLUE,0);fighter(pair,"RED CORNER",RED,1);
        drawBout();
        showFighters();
    }
    private void showFighters(){
        boolean show=screen!=null && bout!=null &&
            (scene==Scene.ROUND || scene==Scene.PAUSE || scene==Scene.TOTALS ||
             (scene==Scene.RINGSIDE && (bout.seconds>0 && session!=null && session.judging()!=null &&
                 session.judging().last()<bout.round)));
        fightPanel.setVisibility(show?VISIBLE:GONE);
    }
    private void drawBout(){
        if(bout==null)return;
        String round="ROUND "+bout.round;
        if(!round.contentEquals(roundLabel.getText()))roundLabel.setText(round);
        String time=FightBroadcast.Bout.time(bout.seconds);
        if(!time.contentEquals(roundValue.getText()))roundValue.setText(time);
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
            TextView v=type(values[i],heading?15:20,heading?GOLD:PAPER);
            v.setGravity((i==0?Gravity.START:Gravity.CENTER_HORIZONTAL)|Gravity.CENTER_VERTICAL);
            int height=dp(heading?25:31);
            if(!heading && i>0){
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
        if(last<0){
            judgePanel.addView(type("Awaiting the judges’ first round scores",15,PAPER));
            return;
        }
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
    private void addScorecard(){
        addPunches();
        label("OFFICIAL SCORECARD");
        judgePanel=new LinearLayout(getContext());judgePanel.setOrientation(VERTICAL);
        judgePanel.setPadding(dp(8),dp(5),dp(8),dp(7));judgePanel.setBackground(field(PANEL,RULE));
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
        session=owner;editorPage=null;editorVisit=false;editorRefs.clear();careerBranch=false;careerFocus=-1;announced=null;announcement.clear();
        if(screen!=null){screen.lowerShows(BroadcastCues.NONE);screen.timing.suspend();}
        setFilmRunning(false);deskScene.reset();currentPage=null;missingPageReads=0;tips.leave();displayedCamp=null;lastMenuRead=0;screen=next;sequence=0;lastSeen=0;scene=null;footerMask=0;
        lastCard=null;bout=null;fighters=null;drawNames();java.util.Arrays.fill(levels,-1);
        boolean opening=next!=null && owner!=null && owner.openingSlate();
        if(opening){scene=Scene.INTRO;deskScene.observe(scene,0,SystemClock.uptimeMillis());}
        animate().cancel();setTranslationX(0);setAlpha(1);
        build();schedule();

    }
    void releaseInput(){if(cutman!=null)cutman.release();}
    void activate(boolean value){
        activeView=value;syncDisplayOwnership();
        if(!value){setFilmRunning(false);if(cutman!=null)cutman.release();if(screen!=null){screen.timing.suspend();screen.lowerShows(BroadcastCues.NONE);}}
        else forceCapture=true;schedule();
    }
    private void schedule(){
        removeCallbacks(tick);
        if(activeView && isAttachedToWindow() && screen!=null)post(tick);
    }
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();syncDisplayOwnership();schedule();}
    @Override protected void onDetachedFromWindow(){
        if(session!=null){session.lowerCallouts=false;}
        setFilmRunning(false);removeCallbacks(tick);
        if(screen!=null){screen.timing.suspend();screen.lowerShows(BroadcastCues.NONE);}
        super.onDetachedFromWindow();
    }
    private void build(){
        if(editorDesk!=null){desk.removeView(editorDesk);editorDesk=null;}
        scroll.setVisibility(VISIBLE);
        if(cutman!=null){cutman.release();removeView(cutman);cutman=null;}
        menuDock.removeAllViews();menuDock.setVisibility(scene==Scene.MENU && screen!=null?VISIBLE:GONE);
        content.removeAllViews();scroll.scrollTo(0,0);menuHint=null;menuLead=null;scouting=null;contract=null;training=null;ledger=null;judgePanel=null;punchPanel=null;java.util.Arrays.fill(punchCounts,null);
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
            editorDesk=new BoxerEditorDesk(getContext(),headline,editorPage,editorRefs,this::editorCommand);
            editorDesk.camp(session.camp());
            if(editorDialog)editorDesk.showDialog();
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
            hero.title=end?(session.result().isEmpty()?"Final bell":session.result()):"Fight totals";
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
            if(menu || !inBout()){
                scouting=new CareerScouting(getContext(),headline);
                LayoutParams sheet=new LayoutParams(-1,-2);sheet.setMargins(dp(3),dp(5),dp(3),dp(4));
                content.addView(scouting,sheet);
                contract=new ContractReport(getContext(),usedFonts==null?DiscFonts.FALLBACK:usedFonts,
                        forward->browseOffer(forward));
                contract.setArt(careerArt);
                contract.setOnClickListener(v->{
                    if(activeOfferFrame()!=null){pad.press(Key.X);}
                });
                content.addView(contract,new LayoutParams(-1,-2));
                training=trainingSheet();
                content.addView(training,new LayoutParams(-1,-2));
                ledger=new CampLedger(getContext(),usedFonts==null?DiscFonts.FALLBACK:usedFonts);
                ledger.setArt(careerArt);
                content.addView(ledger,new LayoutParams(-1,-2));
                careerGuide=false;
                menuLead=type("",23,PAPER);
                menuLead.setPadding(dp(10),dp(14),dp(10),dp(2));
                menuLead.setVisibility(GONE);content.addView(menuLead,new LayoutParams(-1,-2));
                menuHint=type("",18,GameInk.MUTED);
                menuHint.setTypeface(headline);
                menuHint.setMaxWidth(dp(570));menuHint.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
                menuHint.setLineSpacing(dp(2),1f);menuHint.setPadding(dp(10),dp(8),dp(10),dp(8));
                content.addView(menuHint,new LayoutParams(-1,-2));
                describeMenu();
            }
            if(menu){
                LinearLayout keys=null;
                for(int i=0;i<BroadcastCues.KEYS.length;i++){
                    if((footerMask&(1<<i))==0)continue;
                    if(keys==null){
                        keys=new LinearLayout(getContext());keys.setBaselineAligned(false);
                        menuDock.addView(keys,new LayoutParams(-1,-2));
                    }
                    guestButton(keys,i);
                }

            }
        }
        showFighters();sizeHero();hero.setContentDescription(hero.title);hero.invalidate();
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
        if(contract!=null)contract.show(page,offers?desk.selectedOffer():null);
        boolean atTraining=TrainingSheet.handles(page);
        boolean shop=page!=null && page.is("fightstore");
        Drawable wall=shop?shopWall:offers?contractWall:atTraining?gymWall:null;
        if(this.desk.getBackground()!=wall)this.desk.setBackground(wall);
        if(training!=null)training.show(desk,page);
        boolean atLedger=ledger!=null && ledger.show(desk,page);
        menuHint.setVisibility(offers || atTraining || (atLedger && !careerDetails)?GONE:VISIBLE);
        hero.setVisibility(offers || atTraining || shop?GONE:VISIBLE);rule.setVisibility(hero.getVisibility());
        if(scouting!=null){
            scouting.show(central || (deeper && !dedicated)?camp:null);
            menuDock.setVisibility(scene==Scene.MENU && footerMask!=0?VISIBLE:GONE);
        }
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
    private boolean editorCommand(Key key){
        if(editorDesk==null || !activeView || !isShown() || session==null || session.cabinetOpen ||
                session.career.page!=editorDesk.page || screen==null)return false;
        LiveFrame frame=screen.latestFrame();if(frame==null)return false;
        if(editorDesk.dialog)return (key==Key.A || key==Key.UP || key==Key.DOWN) &&
                editorField.dialog(frame) && screen.current(frame) && pad.press(key);
        BroadcastCues fresh=new BroadcastCues();fresh.read(frame);
        if(!BoxerEditor.showing(editorDesk.page,frame,fresh) || !screen.current(frame))return false;
        if(key==Key.Y || (key==Key.B && editorDesk.help)){
            editorDesk.show(!editorDesk.help);return true;
        }
        return pad.press(key);
    }
    boolean editorHelpKey(int code){
        if(editorDesk==null || editorDesk.dialog)return false;
        if(code==KeyEvent.KEYCODE_BUTTON_Y)return editorCommand(Key.Y);
        return code==KeyEvent.KEYCODE_BUTTON_B && editorDesk.help && editorCommand(Key.B);
    }
    private void refreshFrame(){
        if(!activeView||!isAttachedToWindow()||screen==null)return;
        syncReadings();
        LiveFrame f=screen.latestFrame();
        if(f==null && scene==null && session!=null && session.openingSlate()){scene=Scene.INTRO;build();}
        // A menu can stop publishing frames before its prompts finish settling.
        boolean checkResult=session!=null && session.showingResult() && SystemClock.uptimeMillis()-lastMenuRead>=400;
        boolean readSettledMenu=(scene==Scene.MENU || (scene==Scene.RINGSIDE && !inBout())) &&
                SystemClock.uptimeMillis()-lastMenuRead>=220;
        boolean changedPage=session!=null && editorPage!=session.career.page &&
                (editorPage!=null || BoxerEditor.handles(session.career.page) && careerRevision!=session.career.revision);
        if(f!=null && (f.sequence!=sequence || forceCapture || deskScene.settling() || checkResult || changedPage || readSettledMenu))sample(f);
        postDelayed(tick,110);
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
        if(scene==Scene.FINAL && !resultHeading.equals(session.result()+session.finish()))build();
    }
    private void sample(LiveFrame f){
        Scene next=f.scene(inBout());
        cues.read(f,!inBout());
        int mask=next==Scene.MENU?cues.knownPrompts(f,BroadcastCues.ALL_PROMPTS):0;

        long sampled=SystemClock.uptimeMillis();
        boolean showingMenu=next==Scene.MENU || (next==Scene.RINGSIDE && !inBout());
        boolean readMenu=showingMenu && (forceCapture || sampled-lastMenuRead>=150);
        MenuContext.Page page=currentPage;
        int focus=-1;
        if(readMenu){
            page=menuContext.identify(f);
            if(page!=null && "Career Central".equals(page.title))focus=menuContext.careerRow(f);
        }
        if(!screen.current(f)){setFilmRunning(false);return;}
        CareerDesk.Page activePage=session==null?null:session.career.page;
        boolean inEditor=BoxerEditor.handles(activePage);
        if(editorVisit && !inEditor)editorRefs.clear();
        editorVisit=inEditor;
        boolean editing=showingMenu && !inBout() && session!=null && !session.cabinetOpen && BoxerEditor.handles(activePage);
        boolean form=editing && BoxerEditor.showing(activePage,f,cues);
        boolean dialog=editing && !form && editorField.dialog(f);
        CareerDesk.Page editor=form || dialog?activePage:null;
        boolean editorChanged=editorPage!=editor || editorDialog!=dialog;
        editorPage=editor;editorDialog=dialog;
        setFilmRunning(editor==null && showingMenu && !inBout() && session!=null && !session.cabinetOpen);

        if(readMenu){
            lastMenuRead=sampled;
            careerFocus=focus;
            if(page!=null)careerBranch="Career Central".equals(page.title) || careerSetup(page);

            if(page!=null){
                currentPage=page;missingPageReads=0;
            }else if(currentPage!=null && ++missingPageReads>=2){
                currentPage=null;
            }
            describeMenu();
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
        if(editor!=null){layout=Scene.MENU;mask=8;}
        boolean sceneChanged=deskScene.observe(layout,mask,now);
        if(sceneChanged || editorChanged){scene=deskScene.shown;footerMask=deskScene.prompts;build();}
        if(editorDesk!=null)editorDesk.observe(f);
        if(editorDesk!=null && !editorDesk.dialog && editorDesk.page.index==2)
            editorDesk.nameField(editorField.field(f,2) && editorField.select);
        screen.timing.observe(next,session==null?0:session.boutNumber(),card==null?0:card.round,
                card!=null && card.round>0 && card.last()==card.round,layout==Scene.FINAL);
        int route=scene==next?cues.route(next):BroadcastCues.NONE;
        if(route==BroadcastCues.FOOTER)route=(footerMask&mask)==0?BroadcastCues.NONE:route|((footerMask&mask)<<8);
        if(route==BroadcastCues.TRACK || route==BroadcastCues.CUTMAN || route==BroadcastCues.MANUAL)route=BroadcastCues.NONE;
        if((route&255)==BroadcastCues.FOOTER && contract!=null && contract.getVisibility()==VISIBLE &&
                careerPage!=null && careerPage.index==1 && session!=null && careerPage==session.career.page &&
                session.career.selectedOffer()!=null && f.scoutPrompt())route|=BroadcastCues.SCOUT_CARD;
        if(BoxerEditor.handles(activePage))route=editorDesk!=null && editorDesk.page==activePage?
                (editorDesk.dialog?BroadcastCues.EDITOR_DIALOG:BroadcastCues.EDITOR)|(activePage.index<<8):BroadcastCues.NONE;
        screen.lowerShows(route);
    }

    @Override protected void dispatchDraw(Canvas canvas){
        super.dispatchDraw(canvas);
        if(film.used>0 && (session==null || !session.cabinetOpen)){
            int w=getWidth(),h=getHeight();
            filmInk.setStrokeWidth(Math.max(1,w/640f));
            for(int j=0;j<film.used;j+=4){
                filmInk.setColor(film.opacity[j/4]<<24);
                canvas.drawLine(w*film.lines[j],h*film.lines[j+1],w*film.lines[j+2],h*film.lines[j+3],filmInk);
            }
        }
        if(announcement.draw(canvas,getWidth(),getHeight(),headline,SystemClock.uptimeMillis()))postInvalidateOnAnimation();
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
        backdropDst.set(0,0,getWidth(),getHeight());backdropPaint.setAlpha(154);
        canvas.drawBitmap(cornerArt,backdropSrc,backdropDst,backdropPaint);
    }

    private final class Hero extends View {
        String title="";boolean career;
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
            c.drawText(text,dp(23)+dp(10)*(1-progress)*(1-progress),baseline,ink);
        }
    }
}
