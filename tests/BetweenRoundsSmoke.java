package org.oxideshift.corner;

import java.util.List;

/** Between-round notes use only real meters and punch counts, at most two, and none without a human corner. */
public final class BetweenRoundsSmoke {
    private static void check(boolean ok,String what){if(!ok)throw new AssertionError(what);}
    public static void main(String[] args){
        FightBroadcast.Bout ended=FightBroadcast.Bout.parse("3 0 61 35 80 72 40 120 30 50");
        FightBroadcast.Bout live=FightBroadcast.Bout.parse("3 42 61 35 80 72 40 120 30 50");
        check(BetweenRounds.upcoming(ended)==4 && BetweenRounds.upcoming(live)==3 && BetweenRounds.upcoming(null)==0,"upcoming round");
        List<String> blue=BetweenRounds.tips(ended.levels,ended.punches,0);
        check(blue.size()==2 && blue.get(0).contains("stamina is 35%") && blue.get(1).contains("landing 33% (40 of 120)"),"blue tips "+blue);
        List<String> red=BetweenRounds.tips(ended.levels,ended.punches,1);
        check(red.size()==1 && red.get(0).contains("Their stamina is 35%"),"red tips "+red);
        check(BetweenRounds.tips(ended.levels,ended.punches,-1).isEmpty(),"no human corner, no tips");
        check(BetweenRounds.tips(new int[]{90,90,90,90},new int[]{10,15,5,10},0).isEmpty(),"healthy bout, no tips");
        // Round 1 to blue on all cards, round 2 to red 2-1, round 3 split 1-1 with one even card: no winner.
        FightBroadcast.Card card=FightBroadcast.Card.parse("3 1,10,9,10,9,10,9 2,9,10,10,9,9,10 3,10,9,9,10,10,10");
        int[] won=card.roundsWon();
        check(won[0]==1 && won[1]==1,"rounds won "+won[0]+"-"+won[1]);
        System.out.println("Between-round notes use real meters and punch counts only; rounds won follow the judges' majority");
    }
}
