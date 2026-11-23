package org.oxideshift.corner;

import java.util.ArrayDeque;
import java.util.EnumSet;
import java.util.function.Consumer;
import java.util.function.Predicate;
import static org.oxideshift.corner.Achievements.Category.*;

/** Device-wide medals. Award names are persistent keys across upgrades. */
final class Achievements {
    enum Category {
        CLASSICS("Xbox 360"), FINISHES("Finishes"), RINGCRAFT("Ringcraft"), JUDGES("Scorecards"),
        GYM("Gym"), CAREER("Career"), TROPHIES("Trophies");
        final String title;
        Category(String title){this.title=title;}
    }

    enum Award {
        BURGER_KING("Burger King","Win the BK Invitational in career.",100,3),
        DODGE("Dodge","Take the win at the Dodge career event.",100,4),
        EVERLAST("Everlast","Take the win at the Everlast career event.",100,2),
        UNDER_ARMOUR("Under Armour","Take the win at the Under Armour career event.",100,9),
        ESPN_WNF("ESPN Wednesday Night Fights","Win on a Wednesday Night Fights career card.",150,7),
        ESPN_FNF("ESPN Friday Night Fights","Win on a Friday Night Fights career card.",150,6),
        ESPN_PPV("ESPN Pay-Per-View","Win a televised ESPN pay-per-view in career.",150,8),
        EA_SPORTS("EA SPORTS","Take the win at an EA SPORTS career event.",150,5),

        FIRST_WIN(FINISHES,"Hand raised","Win a bout."),
        STOPPAGE(FINISHES,"Beat the count","Win by KO or TKO."),
        DEEP_WATER(FINISHES,"Deep water","Win in the fourth round or later."),
        FIRST_ROUND_KO(FINISHES,"Short night","Stop your opponent in round one."),
        LATE_STOPPAGE(FINISHES,"The long con","Win by stoppage in round six or later."),
        CLEAN_KO(FINISHES,"Count to ten","Win by knockout."),
        TECHNICAL_KO(FINISHES,"Wave it off","Win by technical knockout."),
        COMEBACK(FINISHES,"Back from the canvas","Win a bout after suffering a knockdown."),
        TRADED_DOWNS(FINISHES,"Last one standing","Win a bout in which both fighters scored knockdowns."),
        LATE_WIN(FINISHES,"Championship rounds","Win in round ten or later."),

        LAND_25(RINGCRAFT,"Finding the range","Land 25 punches in one bout."),
        LAND_150(RINGCRAFT,"Volume puncher","Land 150 punches in one bout."),
        HAYMAKERS(RINGCRAFT,"Heavy artillery","Land five haymakers in one bout."),
        ACCURATE(RINGCRAFT,"Pick your shots","Reach 60% accuracy with at least 50 punches thrown."),
        SHARPSHOOTER(RINGCRAFT,"Thread the needle","Reach 75% accuracy with at least 40 punches thrown."),
        OUTLAND(RINGCRAFT,"Set the pace","Build a lead of 30 landed punches in a bout."),
        ELUSIVE(RINGCRAFT,"Make them miss","Hold your opponent to 25% accuracy or less after at least 40 attempts."),
        STAGGER(RINGCRAFT,"Rubber legs","Stagger your opponent."),
        TWO_DOWNS(RINGCRAFT,"Return trip","Score two knockdowns in one bout."),

        UNANIMOUS_ROUND(JUDGES,"Three of a kind","Win one round on all three cards."),
        SPLIT_ROUND(JUDGES,"Two to one","Win a round with two judges while the third scores it for your opponent."),
        BIG_ROUND(JUDGES,"Ten-eight","Win a round by two or more points on all three cards."),
        WIDE_ROUND(JUDGES,"Daylight","Win a round by three or more points on a judge's card."),
        THREE_STRAIGHT(JUDGES,"String them together","Win three consecutive rounds with at least two judges."),
        SIX_ROUNDS(JUDGES,"Bank the rounds","Win six rounds with at least two judges in a single bout."),
        ANSWER_BACK(JUDGES,"Answer the bell","Lose a round on all three cards, then win the next on all three."),
        EIGHT_SCORED(JUDGES,"Eight in the books","Finish eight scored rounds in one bout."),
        CONSENSUS(JUDGES,"Clear favorite","Lead all three cards after at least four scored rounds."),
        TURN_CARDS(JUDGES,"Turn the tables","Trail all three cards after at least two rounds, then take the lead on all three."),

        FIRST_DRILL(GYM,"Gym regular","Complete a career training session."),
        BREAKTHROUGH(GYM,"Breakthrough","Gain at least 5% in one attribute in a single session."),
        BAG_DRILL(GYM,"Bag work","Finish a career heavy bag drill."),
        DUMMY_DRILL(GYM,"Put it together","Finish a career combo dummy drill."),
        WEIGHT_DRILL(GYM,"Iron work","Finish a career weightlifting drill."),
        POWER_GAIN(GYM,"Power shift","Gain at least 3% Power in one session."),
        SPEED_GAIN(GYM,"Fast hands","Gain at least 3% Speed in one session."),
        STAMINA_GAIN(GYM,"Roadwork","Gain at least 3% Stamina in one session."),
        THREE_GAINS(GYM,"All-rounder","Improve three attributes in a single session."),
        STRONG_SESSION(GYM,"Made it count","Earn at least 12 percentage points of positive gains in one session."),

        FIVE_WINS(CAREER,"On the card","Record five wins with one career boxer."),
        TEN_WINS(CAREER,"Double figures","Record ten wins with one career boxer."),
        TWENTY_FIVE_WINS(CAREER,"Main event","Record 25 wins with one career boxer."),
        TEN_KOS(CAREER,"Heavy hands","Record ten knockouts with one career boxer."),
        TWENTY_FIVE_KOS(CAREER,"Lights out","Record 25 knockouts with one career boxer."),
        UNBEATEN(CAREER,"Unblemished","Reach ten career wins with no losses."),
        KO_RATIO(CAREER,"Knockout artist","Reach 20 career wins with at least 75% won by knockout."),
        VETERAN(CAREER,"Ring veteran","Complete 30 bouts with one career boxer."),
        STEP_UP(CAREER,"Step up","Have a contracted opponent with at least ten more career wins than you."),

        FIRST_TROPHY(TROPHIES,"On the mantel","Earn a career trophy and visit the trophy room."),
        FOUR_TROPHIES(TROPHIES,"Top shelf","Visit the trophy room with four trophies."),
        HALF_CASE(TROPHIES,"Half the hardware","Visit the trophy room with seven trophies."),
        FULL_CASE(TROPHIES,"Full house","Fill all fourteen spots in the career trophy room.");

        final Category category;
        final String title,goal;
        final int points,careerEventId;
        Award(Category category,String title,String goal){this(category,title,goal,0,0);}
        Award(String title,String goal,int points,int careerEventId){this(CLASSICS,title,goal,points,careerEventId);}
        Award(Category category,String title,String goal,int points,int careerEventId){
            this.category=category;this.title=title;this.goal=goal;this.points=points;this.careerEventId=careerEventId;
        }
    }

    private final EnumSet<Award> earned=EnumSet.noneOf(Award.class);
    private final ArrayDeque<Award> pending=new ArrayDeque<>();
    private final Consumer<String> save;
    private int side=-1,scoredDowns,sufferedDowns;
    private FightBroadcast.Card card;
    private FightBroadcast.Bout bout;

    Achievements(Predicate<String> saved,Consumer<String> save){
        this.save=save;
        for(Award a:Award.values())if(saved.test(a.name()))earned.add(a);
    }
    synchronized boolean earned(Award a){return earned.contains(a);}
    synchronized int count(){return earned.size();}
    synchronized Award nextRibbon(){return pending.pollFirst();}
    private synchronized void unlock(Award a){
        if(earned.add(a)){save.accept(a.name());pending.addLast(a);}
    }
    synchronized int classicPoints(){
        int total=0;
        for(Award a:earned)total+=a.points;
        return total;
    }
    void careerEventWin(int id){
        for(Award a:Award.values())if(a.category==CLASSICS && a.careerEventId==id){unlock(a);return;}
    }
    void beginBout(){
        side=-1;scoredDowns=sufferedDowns=0;card=null;bout=null;
    }
    void playerStats(int player,int scored,int suffered,int haymakers){
        boolean changed=side!=player;
        side=player;scoredDowns=scored;sufferedDowns=suffered;
        if(side<0)return;
        if(scored>=2)unlock(Award.TWO_DOWNS);
        if(haymakers>=5)unlock(Award.HAYMAKERS);
        if(changed){punches(bout);scores(card);}
    }
    void punches(FightBroadcast.Bout data){
        bout=data;
        if(side<0 || data==null || data.punches==null)return;
        int[] p=data.punches;int own=2*side,other=2*(1-side);
        int landed=p[own],thrown=p[own+1];
        if(landed>=25)unlock(Award.LAND_25);
        if(landed>=150)unlock(Award.LAND_150);
        if(thrown>=50 && landed*100>=thrown*60)unlock(Award.ACCURATE);
        if(thrown>=40 && landed*100>=thrown*75)unlock(Award.SHARPSHOOTER);
        if(landed-p[other]>=30)unlock(Award.OUTLAND);
        if(p[other+1]>=40 && p[other]*4<=p[other+1])unlock(Award.ELUSIVE);
    }
    void scores(FightBroadcast.Card data){
        card=data;
        if(side<0 || card==null || card.last()<1)return;
        int[] margins=new int[3];
        int wins=0,streak=0;
        boolean lostAll=false,trailed=false;
        // Reconstruct from complete rounds. Repeated packets never advance a streak.
        for(int r=1;r<card.rows.length && card.rows[r]!=null;r++){
            int[] row=card.rows[r];int ahead=0,behind=0,wide=0,lead=0,trail=0;
            for(int j=0;j<3;j++){
                int margin=row[j*2+side]-row[j*2+1-side];
                margins[j]+=margin;
                if(margin>0)ahead++;
                if(margin<0)behind++;
                if(margin>=2)wide++;
                if(margin>=3)unlock(Award.WIDE_ROUND);
                if(margins[j]>0)lead++;
                if(margins[j]<0)trail++;
            }
            if(ahead==3)unlock(Award.UNANIMOUS_ROUND);
            if(ahead==2 && behind==1)unlock(Award.SPLIT_ROUND);
            if(wide==3)unlock(Award.BIG_ROUND);
            if(lostAll && ahead==3)unlock(Award.ANSWER_BACK);
            lostAll=behind==3;
            if(ahead>=2){wins++;streak++;}else streak=0;
            if(streak>=3)unlock(Award.THREE_STRAIGHT);
            if(wins>=6)unlock(Award.SIX_ROUNDS);
            if(r>=8)unlock(Award.EIGHT_SCORED);
            if(r>=4 && lead==3)unlock(Award.CONSENSUS);
            if(trailed && lead==3)unlock(Award.TURN_CARDS);
            if(r>=2 && trail==3)trailed=true;
        }
    }
    void stagger(){unlock(Award.STAGGER);}
    void result(String result,String finish,int round){
        if(!"WON!!".equals(result))return;
        unlock(Award.FIRST_WIN);
        boolean ko="KO".equals(finish),tko="TKO".equals(finish);
        if(ko || tko){
            unlock(Award.STOPPAGE);
            if(round==1)unlock(Award.FIRST_ROUND_KO);
            if(round>=6)unlock(Award.LATE_STOPPAGE);
            unlock(ko?Award.CLEAN_KO:Award.TECHNICAL_KO);
        }
        if(round>=4)unlock(Award.DEEP_WATER);
        if(round>=10)unlock(Award.LATE_WIN);
        if(side>=0 && sufferedDowns>0){
            unlock(Award.COMEBACK);
            if(scoredDowns>0)unlock(Award.TRADED_DOWNS);
        }
    }
    void training(int[] changes,int drill){
        if(changes==null || changes.length!=8)return;
        unlock(Award.FIRST_DRILL);
        if(drill==1)unlock(Award.BAG_DRILL);
        if(drill==2)unlock(Award.DUMMY_DRILL);
        if(drill==3)unlock(Award.WEIGHT_DRILL);
        int gains=0,total=0;
        for(int delta:changes){
            if(delta>=5)unlock(Award.BREAKTHROUGH);
            if(delta>0){gains++;total+=delta;}
        }
        if(changes[0]>=3)unlock(Award.POWER_GAIN);
        if(changes[1]>=3)unlock(Award.SPEED_GAIN);
        if(changes[3]>=3)unlock(Award.STAMINA_GAIN);
        if(gains>=3)unlock(Award.THREE_GAINS);
        if(total>=12)unlock(Award.STRONG_SESSION);
    }
    void record(FightBroadcast.Camp camp){
        if(camp==null)return;
        FightBroadcast.Record r=camp.boxer;
        if(r.wins>=5)unlock(Award.FIVE_WINS);
        if(r.wins>=10)unlock(Award.TEN_WINS);
        if(r.wins>=25)unlock(Award.TWENTY_FIVE_WINS);
        if(r.knockouts>=10)unlock(Award.TEN_KOS);
        if(r.knockouts>=25)unlock(Award.TWENTY_FIVE_KOS);
        if(r.wins>=10 && r.losses==0)unlock(Award.UNBEATEN);
        if(r.wins>=20 && r.knockouts*4>=r.wins*3)unlock(Award.KO_RATIO);
        if(r.bouts()>=30)unlock(Award.VETERAN);
        if(camp.opponent==null)return;
        if(camp.opponent.wins>=r.wins+10)unlock(Award.STEP_UP);
    }
    void trophies(int mask){
        if(mask<0 || mask>0x3fff)return;
        int total=Integer.bitCount(mask);
        if(total>=1)unlock(Award.FIRST_TROPHY);
        if(total>=4)unlock(Award.FOUR_TROPHIES);
        if(total>=7)unlock(Award.HALF_CASE);
        if(total==14)unlock(Award.FULL_CASE);
    }
}
