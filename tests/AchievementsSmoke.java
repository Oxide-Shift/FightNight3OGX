package org.oxideshift.corner;

import java.io.File;
import java.util.*;
import static org.oxideshift.corner.Achievements.Award.*;

/** Use production host messages so unlocks, migration and corner selection are tested together. */
final class AchievementsSmoke {
    private final Set<String> saved=new HashSet<>();
    private final Achievements a=new Achievements(saved::contains,key->{if(!saved.add(key))throw new AssertionError("Duplicate save "+key);});
    private final CornerSession s=new CornerSession(()->0L,new File("."));
    private AchievementsSmoke(){s.achievements=a;}
    private void send(String... events)throws Exception{
        for(String e:events)s.line("FNR3/1 "+e);
    }
    private void ribbons(Achievements.Award... expected){
        Set<Achievements.Award> left=new HashSet<>(Arrays.asList(expected));
        for(Achievements.Award next;(next=a.nextRibbon())!=null;)
            if(!left.remove(next))throw new AssertionError("Unexpected or duplicate ribbon "+next);
        if(!left.isEmpty())throw new AssertionError("Missing ribbons "+left);
    }
    private void fight()throws Exception{
        send("CALLOUT WON","BOUTBEGIN","CALLOUT KO","CALLOUT LOST","BOUTBEGIN","CALLOUT FINAL");ribbons();
        send("BOUTBEGIN","BOUT 1 50 100 100 100 100 1 45 150 200","PLAYERSTATS broken");ribbons();
        send("PLAYERSTATS 0 0 0 0");ribbons(); // Player has only one landed punch.
        send("PLAYERSTATS -","BOUTBEGIN","PLAYERSTATS 1 0 0 0","BOUT 1 40 90 80 70 80 10 40 24 39");ribbons(ELUSIVE);
        send("BOUT 1 39 90 80 70 80 10 40 30 40");ribbons(LAND_25,SHARPSHOOTER);
        send("BOUT 1 25 90 80 70 80 10 40 40 50");ribbons(ACCURATE,OUTLAND);
        send("BOUT 1 10 90 80 70 80 10 40 75 120","BOUT 2 15 90 80 70 80 10 40 150 240");ribbons(LAND_150);
        send("CALLOUT HAYMAKER","PLAYERSTATS 1 1 0 4");ribbons(); // Opponent haymakers don't count.
        send("PLAYERSTATS 1 2 1 5","PLAYERSTATS 1 2 1 5","CALLOUT STAGGER");ribbons(HAYMAKERS,TWO_DOWNS,STAGGER);
        send("CARD 10","CALLOUT TKO","CALLOUT WON","CALLOUT WON");
        ribbons(FIRST_WIN,STOPPAGE,DEEP_WATER,LATE_STOPPAGE,TECHNICAL_KO,COMEBACK,TRADED_DOWNS,LATE_WIN);
        send("BOUTBEGIN","CARD 1","CALLOUT WON","CALLOUT KO","CALLOUT KO");ribbons(FIRST_ROUND_KO,CLEAN_KO);
    }
    private void judges()throws Exception{
        send("BOUTBEGIN","PLAYERSTATS 0 0 0 0","CARD 2 2,10,7,10,7,10,7");ribbons(); // Missing opening round.
        send("BOUTBEGIN","PLAYERSTATS 1 0 0 0");
        String loss=",10,9,10,9,10,9",big=",7,10,8,10,8,10",win=",9,10,9,10,9,10",split=",9,10,10,9,9,10";
        String rows=" 1"+loss+" 2"+loss;
        send("CARD 2"+rows,"CARD 2"+rows);ribbons();
        rows+=" 3"+big;send("CARD 3"+rows);ribbons(UNANIMOUS_ROUND,WIDE_ROUND,BIG_ROUND,ANSWER_BACK);
        rows+=" 4"+win+" 5"+win;send("CARD 5"+rows);ribbons(THREE_STRAIGHT,TURN_CARDS,CONSENSUS);
        rows+=" 6"+split;send("CARD 6"+rows);ribbons(SPLIT_ROUND);
        rows+=" 7"+win+" 8"+win;send("CARD 8"+rows,"CARD 8"+rows);ribbons(SIX_ROUNDS,EIGHT_SCORED);
    }
    private void gym()throws Exception{
        send("CAREERPAGE train 2","TRAINSTATS 1 9 9 9 9 9 9 9 9","CAREERPAGE drill 1","CAREERPAGE train 2",
             "CAREERPAGE train 1","TRAINSTATS 0 -");ribbons();
        send("TRAINSTATS 0 0 0 0 0 0 0 0 0");ribbons(FIRST_DRILL);
        send("CAREERPAGE drill 1","CAREERPAGE train 1","TRAINSTATS 0 5 3 1 3 3 3 0 0");
        ribbons(BAG_DRILL,BREAKTHROUGH,POWER_GAIN,SPEED_GAIN,STAMINA_GAIN,THREE_GAINS,STRONG_SESSION);
        for(int drill:new int[]{2,3}){
            send("CAREERPAGE drill "+drill,"CAREERPAGE train 1","TRAINSTATS 0 -1 -1 -1 -1 0 0 0 0");
            ribbons(drill==2?DUMMY_DRILL:WEIGHT_DRILL);
        }
    }
    private void career()throws Exception{
        send("CAMP - -","CAMP 4,1,0,4 -","CAMP 4,1,0,4 - purse=100000");ribbons();
        send("CAMP 5,1,0,4 14,1,0,2 purse=4999");ribbons(FIVE_WINS);
        send("CAMP 10,0,0,10 20,1,0,2 purse=5000");ribbons(TEN_WINS,UNBEATEN,TEN_KOS,STEP_UP);
        send("CAMP 20,5,0,15 20,1,0,2 purse=25000");ribbons(KO_RATIO);
        send("CAMP 25,5,0,25 -");ribbons(TWENTY_FIVE_WINS,TWENTY_FIVE_KOS,VETERAN);
        send("CAMP 40,5,0,25 20,1,0,2 purse=100000");ribbons();
        send("CASE 16383","CAREERPAGE -","CAREERPAGE trophycase 1","CASE 0");ribbons();
        send("CASE 1","CASE 15","CASE 127","CASE 1023","CASE 16383","CASE 16383");
        ribbons(FIRST_TROPHY,FOUR_TROPHIES,HALF_CASE,FULL_CASE);
    }
    private void classics()throws Exception{
        Achievements.Award[] retailOrder={EVERLAST,BURGER_KING,DODGE,EA_SPORTS,ESPN_FNF,ESPN_WNF,ESPN_PPV,UNDER_ARMOUR};
        send("BOUTBEGIN","EVENTWIN 2","CALLOUT LOST","EVENTWIN 2","BOUTBEGIN","CALLOUT FINAL","EVENTWIN 3");ribbons();
        for(int i=0;i<retailOrder.length;i++){
            String event="EVENTWIN "+(i+2);
            send("BOUTBEGIN",event,"CALLOUT WON","EVENTWIN -1","EVENTWIN 0","EVENTWIN 10",event,event);
            ribbons(retailOrder[i]);
        }
        if(a.classicPoints()!=1000)throw new AssertionError("Classic points");
    }
    public static void main(String[] args)throws Exception{
        Set<String> legacy=new HashSet<>(Arrays.asList("FIRST_WIN","STOPPAGE","DEEP_WATER","FIRST_DRILL","BREAKTHROUGH",
                "FIVE_WINS","TEN_KOS","FIRST_TROPHY","FULL_CASE"));
        Achievements upgraded=new Achievements(legacy::contains,legacy::add);
        if(upgraded.count()!=9 || upgraded.nextRibbon()!=null)throw new AssertionError("Upgrade from alpha.34");
        AchievementsSmoke test=new AchievementsSmoke();test.fight();test.judges();test.gym();test.career();test.classics();
        if(test.a.count()!=60 || Achievements.Award.values().length!=60)throw new AssertionError("Catalog reachability");
        Achievements restored=new Achievements(test.saved::contains,k->{throw new AssertionError("Duplicate save");});
        if(restored.classicPoints()!=1000 || restored.count()!=60 || restored.nextRibbon()!=null)throw new AssertionError("Restored cabinet");
        System.out.println("All 60 medals earned via host events. Both corners, negative cases, repeated packets and alpha.34 upgrades passed.");
    }
}
