package org.oxideshift.corner;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Between-round notes for the lower display. Every line comes from the retail meters and punch counts; nothing is invented. */
final class BetweenRounds {
    private BetweenRounds(){}

    /** The round about to start: the finished round + 1 once its clock reads 0:00, otherwise the current round. */
    static int upcoming(FightBroadcast.Bout bout){
        if(bout==null || bout.round<=0)return 0;
        return bout.seconds==0?bout.round+1:bout.round;
    }

    /**
     * At most two rule-based tips for the human corner. levels = blue health, blue stamina, red health, red stamina;
     * punches = blue landed, blue thrown, red landed, red thrown. side 0 blue, 1 red, -1 unknown (no tips).
     */
    static List<String> tips(int[] levels,int[] punches,int side){
        List<String> tips=new ArrayList<>(2);
        if(levels==null || levels.length<4 || (side!=0 && side!=1))return tips;
        int health=levels[side*2],stamina=levels[side*2+1],theirHealth=levels[(1-side)*2],theirStamina=levels[(1-side)*2+1];
        if(stamina>=0 && stamina<40)
            tips.add(String.format(Locale.ROOT,"Your stamina is %d%%. Throw fewer, sharper punches and let it recover.",stamina));
        if(punches!=null && punches.length>=4){
            int landed=punches[side*2],thrown=punches[side*2+1];
            if(thrown>=20 && landed*100<thrown*45 && tips.size()<2)
                tips.add(String.format(Locale.ROOT,"You are landing %d%% (%d of %d). Short combinations land more often.",landed*100/thrown,landed,thrown));
        }
        if(health>=0 && theirHealth>=0 && theirHealth-health>=15 && tips.size()<2)
            tips.add(String.format(Locale.ROOT,"Your health trails %d%% to %d%%. Keep your guard up early in the round.",health,theirHealth));
        if(theirStamina>=0 && theirStamina<40 && tips.size()<2)
            tips.add(String.format(Locale.ROOT,"Their stamina is %d%%. Pressure them while it is low.",theirStamina));
        return tips;
    }
}
