package org.oxideshift.corner;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Paths;

public final class FightBroadcastSmoke {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void meters(LiveFrame frame, int[] expected) {
        for (int i = 0; i < expected.length; i++) {
            int value = FightBroadcast.gauge(frame, i / 2, (i & 1) != 0);
            check(value == expected[i], "Meter " + i + " expected " + expected[i] + ", got " + value);
        }
    }

    public static void main(String[] args) throws Exception {
        FightBroadcast.Card fresh = FightBroadcast.Card.parse("1");
        check(fresh != null && fresh.last() == -1, "No scores before the bell");
        FightBroadcast.Card official = FightBroadcast.Card.parse("2 1,9,10,9,10,9,10 2,10,8,9,9,10,9");
        check(official.last() == 2 && official.round == 2, "Two posted rounds");
        check(official.total(0,0) == 19 && official.total(0,1) == 18, "First judge");
        check(official.total(1,0) == 18 && official.total(1,1) == 19, "Second judge");
        check(official.total(2,0) == 19 && official.total(2,1) == 19, "Third judge");
        for (String bad : new String[]{"", " ", "x", "17", "0 1,10,9,10,9,10,9", "2 0,10,9,10,9,10,9", "2 1,10,8", "2 1,11,9,9,9,10,9",
                "2 1,10,9,10,9,10,9 1,10,9,10,9,10,9"}) {
            check(FightBroadcast.Card.parse(bad) == null, "Invalid snapshot: " + bad);
        }
        check(FightBroadcast.Card.parse("0").last() == -1, "Scorecard reset");
        check("Never Gonna Get It".equals(FightBroadcast.song("SEANBIG1").title), "EA Trax archive entry");
        check(FightBroadcast.song("UNKNOWN") == null, "Unrecognised track");

        CareerDesk scout=new CareerDesk();scout.accept("CAREERPAGE","mcon 1");
        scout.accept("OFFER","536D697468 1000 71 68 72 64 69 71 4,1,0,2 9,2,0,6");
        CareerDesk.Offer selected=scout.selectedOffer();
        check(selected.own.wins==4 && selected.rival.knockouts==6 && selected.experience().contains("6 more"),"Highlighted records");
        scout.accept("OFFER","536D697468 1000 71 68 72 64 69 71 - -");
        check(scout.selectedOffer().rival==null && scout.selectedOffer().experience().isEmpty(),"Unknown records clear the old offer");
        check(scout.selectedOffer().terms==null && scout.selectedOffer().purseText().equals("$1,000"),"Purse before contract text");
        scout.accept("CONTRACT","24372C343030 24352C303030 57696E206279204B4F");
        CareerDesk.Offer termed=scout.selectedOffer();
        check(termed.terms.bonus.equals("$5,000") && termed.terms.objective.equals("Win by KO") && termed.purseText().equals("$7,400"),"Contract text");
        check("$7,400".equals(CareerDesk.Terms.text("2424372C343030")) &&
              "$5,000".equals(CareerDesk.Terms.text("2424352C303030")),"A doubled currency sign shows once");
        scout.accept("CONTRACT","24372C343030 24352C303030 57696E206279204B4F");
        check(scout.selectedOffer()==termed,"Repeated contract text keeps the offer");
        scout.accept("OFFER","536D697468 1000 71 68 72 64 69 71 - -");
        check(scout.selectedOffer()==termed,"Same offer keeps its contract text");
        scout.accept("CONTRACT","24372C343030 - -");
        check(!scout.selectedOffer().terms.hasBonus(),"No-bonus contract");
        for(String bad:new String[]{"2 - -","0A - -","- -","ZZ - -"}){
            CareerDesk.Offer before=scout.selectedOffer();scout.accept("CONTRACT",bad);
            check(scout.selectedOffer()==before,"Invalid contract text ignored: "+bad);
        }
        check(ContractReport.lead(72,84).equals("Opp. +12") && ContractReport.lead(81,75).equals("You +6") &&
              ContractReport.lead(68,69).equals("Close") && ContractReport.lead(70,70).equals("Even") &&
              ContractReport.lead(76,-1).equals("Unrated") && ContractReport.lead(-1,60).equals("Unrated"),"Tale of the tape captions");
        check(TrainingSheet.fill(5)==.5f && TrainingSheet.fill(-2)==-.2f && TrainingSheet.fill(-12)==-1f && TrainingSheet.fill(0)==0f &&
              TrainingSheet.beyond(11) && TrainingSheet.beyond(-11) && !TrainingSheet.beyond(10),"Training deltas share one locked scale");
        int[] drill={5,-2,-1,2,0,0,0,1},flat={0,0,0,0,0,0,0,0};
        check(CampPoster.best(drill)==0 && CampPoster.worst(drill)==1 && CampPoster.best(flat)==-1 && CampPoster.worst(flat)==-1,"Camp poster gain and cost");
        check(CampPoster.summary(drill,false).equals("Forecast. Best gain Power +5%. Biggest cost Speed -2%."),"Camp poster description");
        check(CampPoster.reach(0)==.5f && CampPoster.reach(10)==1f && CampPoster.reach(-10)==0f && CampPoster.reach(25)==1f,"Radar on the locked scale");
        check(CampPoster.stamp(new CareerDesk.Budget(2500,1800)).equals("SHORT $700") && CampPoster.stamp(new CareerDesk.Budget(0,1800)).equals("NO FEE") &&
              CampPoster.stamp(new CareerDesk.Budget(900,1800)).equals("IN BUDGET") && CampPoster.stamp(new CareerDesk.Budget(-1,1800)).isEmpty(),"Trainer stamp");
        check(CampPoster.stamp(new CareerDesk.Budget(0,3450),true).equals("FREE") && CampPoster.stamp(new CareerDesk.Budget(4200,3450),true).equals("SHORT $750") &&
              CampPoster.stamp(new CareerDesk.Budget(1200,3450),true).equals("IN BUDGET"),"Store stamp");
        check(CampPoster.share(new CareerDesk.Budget(2500,1800))==139 && CampPoster.share(new CareerDesk.Budget(0,1800))==-1 &&
              CampPoster.share(new CareerDesk.Budget(900,0))==-1,"Fee share of camp cash");
        CueMotion.Pose pose=new CueMotion.Pose();
        CueMotion.sample(CueMotion.Kind.TRAX,-1,false,pose);check(!pose.visible,"Trax cue before playback");
        CueMotion.sample(CueMotion.Kind.TRAX,0,false,pose);check(pose.visible && pose.emblem==0f && pose.field==0f && pose.emblemX==-22f,"Trax cue starts clear");
        CueMotion.sample(CueMotion.Kind.TRAX,3000,false,pose);check(pose.emblem==1f && pose.field==1f && pose.name==1f && pose.detail==1f && pose.wordsX==0f,"Trax cue holds still");
        CueMotion.sample(CueMotion.Kind.TRAX,6600,false,pose);check(!pose.visible,"Trax cue removed at 6.6 s");
        CueMotion.sample(CueMotion.Kind.TRAX,100,true,pose);check(pose.field==1f && pose.emblem==1f,"Reduced motion binds the composed cue");
        CueMotion.sample(CueMotion.Kind.AWARD,100,false,pose);check(Math.abs(pose.emblemScale-1.03f)<1e-4f,"Award medal impression at 103%");
        CueMotion.sample(CueMotion.Kind.AWARD,200,false,pose);check(Math.abs(pose.emblemScale-1f)<1e-4f,"Award medal settles at 100%");
        CueMotion.sample(CueMotion.Kind.AWARD,0,false,pose);check(pose.visible && pose.emblem==0f && pose.field==0f && Math.abs(pose.emblemScale-.90f)<1e-4f,"Award cue starts from a 90% medal");
        CueMotion.sample(CueMotion.Kind.AWARD,1500,false,pose);check(pose.emblem==1f && pose.field==1f && pose.name==1f && pose.detail==1f && pose.wordsX==0f && pose.emblemX==0f,"Award cue holds still");
        CueMotion.sample(CueMotion.Kind.AWARD,3480,false,pose);check(pose.detail==0f && pose.name>0f && pose.field>pose.name && pose.emblem>.9f,"Award exit: deed, then title, receipt, medal last");
        CueMotion.sample(CueMotion.Kind.AWARD,3580,false,pose);check(!pose.visible,"Award cue clear at 3.58 s");
        DamageMap.Tally dm=new DamageMap.Tally();
        check(dm.line("FNR3/1 DAMAGE 0 DAMAGE_AREA_LEFT_EYE HEAD 3.50") && dm.line("FNR3/1 DAMAGE 1 DAMAGE_AREA_RIGHT_BODY BODY 2.25 ATTACK_TYPE_HOOK_L"),"Damage lines parse");
        check(!dm.line("FNR3/1 DAMAGE 0 ATTACK_TYPE_HOOK_L HEAD 1.00"),"An attack type is not a damage area");
        check(!dm.line("FNR3/1 DAMAGE 2 DAMAGE_AREA_NOSE HEAD 1") && !dm.line("FNR3/1 DAMAGE 0 SOMEWHERE HEAD 1") && !dm.line("FNR3/1 DAMAGE 0 DAMAGE_AREA_NOSE HEAD x"),"Bad damage lines are ignored");
        check(dm.head(1)==3.5f && dm.body(0)==2.25f && dm.head(0)==0f && dm.peak()==3.5f,"Damage lands on the boxer who was hit");
        dm.clear();check(dm.peak()==0f,"Damage resets for a new bout");
        // Each punch arrives as a pair (overall, then body); only the first counts.
        check(dm.line("FNR3/1 DAMAGE 0 DAMAGE_AREA_LEFT_BODY BODY 1.52 ATTACK_TYPE_STRAIGHT_L") &&
              !dm.line("FNR3/1 DAMAGE 0 DAMAGE_AREA_LEFT_BODY BODY 2.00 ATTACK_TYPE_STRAIGHT_L") &&
              dm.line("FNR3/1 DAMAGE 0 DAMAGE_AREA_LEFT_BODY BODY 1.52 ATTACK_TYPE_STRAIGHT_L"),"Paired damage lines count once");
        check(Math.abs(dm.body(1)-3.04f)<1e-4f,"Two punches, not four");
        check(dm.level(1,6,100)==0f,"Full health is all green");
        check(Math.abs(dm.level(1,6,40)-.6f)<1e-4f,"The most-hit area shows the boxer's health loss");
        dm.line("FNR3/1 DAMAGE 0 DAMAGE_AREA_NOSE HEAD 0.76 ATTACK_TYPE_HOOK_L");
        check(Math.abs(dm.level(1,2,40)-.6f*(float)Math.sqrt(.25f))<1e-4f && dm.level(1,0,40)==0f && dm.worst(1)==6,"Other areas scale by their share; untouched stays green");
        check(DamageMap.heat(.95f)==0xffff2a14 && DamageMap.heat(0f)==0xff3fae4a,"Healthy starts green; red only at the top");
        TransitionMirror.Tracker tr=new TransitionMirror.Tracker();tr.setup(141,119);
        tr.observe(10,0);check(tr.active() && Math.abs(tr.position(500)-40f)<.01f,"Mirror locks and runs at 60 fps");
        check(tr.position(3000)==119f,"Mirror holds at the screen change");
        tr.observe(80,3000);check(tr.position(3000)==119f,"Covered-phase ties do not pull the hold back");
        tr.observe(124,3100);check(Math.abs(tr.position(3100)-124f)<.01f,"Mirror follows the top out of the hold");
        check(tr.position(3400)<0 && !tr.active(),"Mirror finishes with the movie");
        tr.observe(20,0);for(int i=1;i<=5;i++)tr.observe(-1,i*16);
        check(tr.active() && tr.position(80)>=120f,"Lost reads let the bars pull away");
        tr.clear();tr.observe(5,0);tr.observe(6,33);tr.observe(21,533);
        check(Math.abs(tr.position(1033)-36f)<.6f,"Mirror adopts a 30 fps movie rate");
        tr.clear();tr.observe(139,0);check(!tr.active(),"No lock on the movie's last frames");
        check(CueMotion.nextWake(CueMotion.Kind.TRAX,100)==0 && CueMotion.nextWake(CueMotion.Kind.TRAX,1000)==5250 &&
              CueMotion.nextWake(CueMotion.Kind.TRAX,6300)==0 && CueMotion.nextWake(CueMotion.Kind.AWARD,300)==3100,"Cue frames during motion, one wake during the hold");
        check(MenuArt.TabMeter.share(4,8)==.5f && MenuArt.TabMeter.share(0,0)==0f && MenuArt.TabMeter.share(9,8)==1f &&
              MenuArt.Switch.blend(0xff000000,0xffffffff,.5f)==0xff808080 && MenuArt.Switch.blend(0xff102030,0xff405060,0f)==0xff102030,"Menu art progress and switch colours");
        check(MenuStage.percent(35,100)==35 && MenuStage.percent(0,0)==0 && MenuStage.percent(2,3)==67,"Menu stage progress percentage");
        check(OptionsSheet.handles(CareerDesk.find("options",3)) && !OptionsSheet.handles(CareerDesk.find("train",2)) && !OptionsSheet.handles(null) &&
              OptionsSheet.LABELS.length==7 && CareerDesk.find("options",7)!=null,"Options sheet covers the seven option routes");
        check(TrophyCabinet.count(4241).equals("4 / 14 earned") && TrophyCabinet.count(0).equals("0 / 14 earned") &&
              TrophyCabinet.count(16383).equals("14 / 14 earned") && TrophyCabinet.count(-1).isEmpty(),"Trophy count only from a valid mask");
        check(TrophyCabinet.caption(4241,6).equals("Trophy 07  ·  Locked") && TrophyCabinet.caption(4241,0).equals("Trophy 01  ·  Earned") &&
              TrophyCabinet.caption(-1,2).isEmpty() && TrophyCabinet.caption(4241,-1).isEmpty() && TrophyCabinet.caption(4241,14).isEmpty(),"Trophy caption never invents a name");
        check(BoxerEditorDesk.nextUnreviewed(0)==2 && BoxerEditorDesk.nextUnreviewed((1<<2)|(1<<3))==4 &&
              BoxerEditorDesk.nextUnreviewed(0x1fc)==-1 && BoxerEditorDesk.nextUnreviewed(0x1fc&~(1<<8))==8,"Notebook next unchecked tab");
        check(SheetMotion.dollars("$12,500")==12500 && SheetMotion.dollars("$700")==700 && SheetMotion.dollars("Title shot")==-1 &&
              SheetMotion.dollars("$1,2345")==-1 && SheetMotion.dollars("")==-1 && SheetMotion.percent(5).equals("+5%") &&
              SheetMotion.percent(-2).equals("-2%") && SheetMotion.percent(0).equals("0%"),"Rolling numbers parse only plain amounts");
        scout.accept("OFFER","4A6F6E6573 2000 71 68 72 64 69 71 - -");
        check(scout.selectedOffer().terms==null,"A different offer drops the previous contract text");
        LiveFrame empty = new LiveFrame(ByteBuffer.allocate(8 + 4*640*480).order(ByteOrder.LITTLE_ENDIAN),
                640, 480, 1, true);
        meters(empty, new int[]{-1,-1,-1,-1});
        // EA Trax handoff: the Music page stays until the retail orange wipe covers most of the game image.
        check(!AchievementDisplay.wipeCovers(empty),"Black frame is not the retail wipe");
        ByteBuffer wipe=ByteBuffer.allocate(8+4*640*480).order(ByteOrder.LITTLE_ENDIAN);
        for(int i=0;i<640*480;i++)wipe.putInt(8+4*i,(i/640)%40<26?0xf09a20:0xb52a1c);
        check(AchievementDisplay.wipeCovers(new LiveFrame(wipe,640,480,2,true)),"Orange bars cover the frame");
        for(int i=0;i<640*480;i++)wipe.putInt(8+4*i,(i/640)%40<14?0xf09a20:0xb52a1c);
        check(!AchievementDisplay.wipeCovers(new LiveFrame(wipe,640,480,3,true)),"A partial sweep keeps the Music page");
        for (String path : args) {
            ByteBuffer buf = ByteBuffer.wrap(Files.readAllBytes(Paths.get(path))).order(ByteOrder.LITTLE_ENDIAN);
            LiveFrame frame = new LiveFrame(buf, buf.getInt(0), buf.getInt(4), 1, true);
            check(frame.scene() == LiveFrame.Scene.ROUND, "Expected the Warehouse round");
            int[] levels = new int[4];
            for (int i=0;i<4;i++) levels[i]=FightBroadcast.gauge(frame,i/2,(i&1)!=0);
            System.out.println(frame.height + "p: " + java.util.Arrays.toString(levels));
            meters(frame, new int[]{100, 91, 77, 97});
        }
        System.out.println("Fight broadcast checks passed");
    }
}
