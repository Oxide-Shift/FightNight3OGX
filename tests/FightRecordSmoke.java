package org.oxideshift.corner;

import java.util.HashMap;
import java.util.Map;

/** Fight record: books, punch types with the duplicated damage lines, results, streaks and quits. */
public final class FightRecordSmoke {
    static void check(boolean pass,String message){if(!pass)throw new AssertionError(message);}
    static Map<String,Long> data=new HashMap<>();
    static FightRecord record(){
        return new FightRecord(new FightRecord.Store(){
            public long get(String key){return data.getOrDefault(key,0L);}
            public void put(String key,long value){data.put(key,value);}
        });
    }
    static void punch(FightRecord r,int puncher,String area,String zone,String attack){
        // The retail routine logs each landed punch twice (overall, then body damage).
        r.damage("FNR3/1 DAMAGE "+puncher+" DAMAGE_AREA_"+area+" "+zone+" 3.10 "+attack);
        r.damage("FNR3/1 DAMAGE "+puncher+" DAMAGE_AREA_"+area+" "+zone+" 0.00 "+attack);
    }
    public static void main(String[] args){
        FightRecord r=record();
        // Career bout, player is red (side 1): KO win in round 3.
        r.begin(true);
        r.playerStats(1,0,0,0);
        punch(r,1,"LEFT_JAW","HEAD","ATTACK_TYPE_HOOK_L");
        punch(r,1,"LEFT_JAW","HEAD","ATTACK_TYPE_HOOK_L");   // a second identical punch still counts
        punch(r,1,"RIGHT_BODY","BODY","ATTACK_TYPE_UPPERCUT_R");
        punch(r,1,"NOSE","HEAD","ATTACK_TYPE_STRAIGHT_L");
        punch(r,0,"NOSE","HEAD","ATTACK_TYPE_STRAIGHT_R");   // opponent's punch: not the player's
        r.stagger();
        r.playerStats(1,2,1,6);
        r.punches(new int[]{30,80,95,140},3);
        r.callout("KO");r.callout("WON");
        r.callout("WON");                                      // repeats never count twice
        for(boolean career:new boolean[]{true,false}){
            check(r.get(career,FightRecord.Stat.BOUTS)==1 && r.get(career,FightRecord.Stat.WINS)==1,"bout and win");
            check(r.get(career,FightRecord.Stat.KO_WINS)==1 && r.get(career,FightRecord.Stat.FASTEST_KO)==3,"KO round");
            check(r.get(career,FightRecord.Stat.LANDED)==95 && r.get(career,FightRecord.Stat.THROWN)==140,"punch totals");
            check(r.get(career,FightRecord.Stat.ABSORBED)==30,"absorbed");
            check(r.get(career,FightRecord.Stat.HOOK_L)==2 && r.get(career,FightRecord.Stat.UPPER_R)==1 && r.get(career,FightRecord.Stat.JAB)==1,"types");
            check(r.get(career,FightRecord.Stat.STRAIGHT)==0,"opponent's straight not counted");
            check(r.get(career,FightRecord.Stat.HEAD)==3 && r.get(career,FightRecord.Stat.BODY)==1,"head/body");
            check(r.get(career,FightRecord.Stat.AT_LEFT_JAW)==2 && r.get(career,FightRecord.Stat.AT_RIGHT_BODY)==1
                    && r.get(career,FightRecord.Stat.AT_NOSE)==1 && r.get(career,FightRecord.Stat.AT_MOUTH)==0,"areas");
            check(r.get(career,FightRecord.Stat.KNOCKDOWNS)==2 && r.get(career,FightRecord.Stat.KNOCKED_DOWN)==1,"downs");
            check(r.get(career,FightRecord.Stat.HAYMAKERS)==6 && r.get(career,FightRecord.Stat.STAGGERS)==1,"haymakers, staggers");
            check(r.get(career,FightRecord.Stat.STREAK)==1 && r.get(career,FightRecord.Stat.BEST_STREAK)==1,"streak");
            check(r.get(career,FightRecord.Stat.BEST_LANDED)==95 && r.get(career,FightRecord.Stat.ROUNDS)==3,"bests and rounds");
        }
        // Play Now decision loss: only the all-bouts book moves; the streak resets there.
        r.begin(false);r.playerStats(0,0,0,0);r.punches(new int[]{40,90,50,70},12);
        r.callout("FINAL");r.callout("LOST");
        check(r.get(true,FightRecord.Stat.BOUTS)==1 && r.get(false,FightRecord.Stat.BOUTS)==2,"books");
        check(r.get(false,FightRecord.Stat.LOSSES)==1 && r.get(false,FightRecord.Stat.STOPPED)==0,"decision loss");
        check(r.get(false,FightRecord.Stat.STREAK)==0 && r.get(false,FightRecord.Stat.BEST_STREAK)==1 && r.get(true,FightRecord.Stat.STREAK)==1,"streaks");
        check(r.get(false,FightRecord.Stat.BEST_LANDED)==95 && r.get(false,FightRecord.Stat.ROUNDS)==15,"best kept, rounds summed");
        // A thrown-in towel (no result) is never recorded; a final bell with no winner is a draw at the next bout.
        r.begin(true);r.playerStats(1,0,0,0);r.punches(new int[]{1,2,3,4},1);
        r.begin(true);r.playerStats(1,0,0,0);r.punches(new int[]{20,40,22,41},10);r.callout("FINAL");
        check(r.get(true,FightRecord.Stat.BOUTS)==1,"quit and pending draw not yet counted");
        r.begin(false);
        check(r.get(true,FightRecord.Stat.BOUTS)==2 && r.get(true,FightRecord.Stat.DRAWS)==1,"draw settled at next bout");
        // AI-only bouts (no player side) never count; a later stopped loss counts as stopped.
        long before=r.get(false,FightRecord.Stat.BOUTS);
        r.begin(false);r.callout("WON");
        check(r.get(false,FightRecord.Stat.BOUTS)==before,"no player side");
        r.begin(false);r.playerStats(0,0,3,0);r.callout("TKO");r.callout("LOST");
        check(r.get(false,FightRecord.Stat.STOPPED)==1 && r.get(false,FightRecord.Stat.KNOCKED_DOWN)==4,"stopped");
        // Bad lines are ignored.
        r.begin(false);
        check(!r.damage("FNR3/1 DAMAGE 3 DAMAGE_AREA_NOSE HEAD 1.0 ATTACK_TYPE_HOOK_L") && !r.damage("FNR3/1 DAMAGE 0 NOSE HEAD 1 X")
                && !r.damage("FNR3/1 DAMAGE 0 DAMAGE_AREA_NOSE HEAD x ATTACK_TYPE_HOOK_L"),"malformed");
        check(FightRecord.percent(1,3)==33 && FightRecord.percent(5,0)==-1,"percent");
        System.out.println("Fight record: books, punch types, results, streaks, quits and draws passed");
    }
}
