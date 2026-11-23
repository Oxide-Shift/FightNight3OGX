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
        BURGER_KING("Burger King","Win the BK Invitational in career.","The BK Invitational is yours.",100,3),
        DODGE("Dodge","Take the win at the Dodge career event.","You took the decision or stoppage at the Dodge event.",100,4),
        EVERLAST("Everlast","Take the win at the Everlast career event.","You won the Everlast career event.",100,2),
        UNDER_ARMOUR("Under Armour","Take the win at the Under Armour career event.","You won the Under Armour career event.",100,9),
        ESPN_WNF("ESPN Wednesday Night Fights","Win on a Wednesday Night Fights career card.","You won under the Wednesday Night Fights lights.",150,7),
        ESPN_FNF("ESPN Friday Night Fights","Win on a Friday Night Fights career card.","You took the W on Friday Night Fights.",150,6),
        ESPN_PPV("ESPN Pay-Per-View","Win a televised ESPN pay-per-view in career.","You delivered a win on ESPN pay-per-view.",150,8),
        EA_SPORTS("EA SPORTS","Take the win at an EA SPORTS career event.","You won the EA SPORTS career event.",150,5),

        FIRST_WIN(FINISHES,"Hand raised","Win a bout.","Your hand went up for the first time.",10),
        STOPPAGE(FINISHES,"Beat the count","Win by KO or TKO.","You finished the fight by KO or TKO.",15),
        DEEP_WATER(FINISHES,"Deep water","Win in the fourth round or later.","You took the win in round four or later.",20),
        FIRST_ROUND_KO(FINISHES,"Short night","Stop your opponent in round one.","You closed the show in the opening round.",35),
        LATE_STOPPAGE(FINISHES,"The long con","Win by stoppage in round six or later.","You got the stoppage in round six or later.",50),
        CLEAN_KO(FINISHES,"Count to ten","Win by knockout.","You won by knockout.",25),
        TECHNICAL_KO(FINISHES,"Wave it off","Win by technical knockout.","The referee waved it off. You won by TKO.",30),
        COMEBACK(FINISHES,"Back from the canvas","Win a bout after suffering a knockdown.","You got off the canvas and came back to win.",45),
        TRADED_DOWNS(FINISHES,"Last one standing","Win a bout in which both fighters scored knockdowns.","Both fighters hit the canvas. You got the win.",50),
        LATE_WIN(FINISHES,"Championship rounds","Win in round ten or later.","You won in round ten or later.",60),

        LAND_25(RINGCRAFT,"Finding the range","Land 25 punches in one bout.","You landed 25 punches in one fight.",10),
        LAND_150(RINGCRAFT,"Volume puncher","Land 150 punches in one bout.","You landed 150 punches in one fight.",40),
        HAYMAKERS(RINGCRAFT,"Heavy artillery","Land five haymakers in one bout.","Five haymakers landed in one fight.",30),
        ACCURATE(RINGCRAFT,"Pick your shots","Reach 60% accuracy with at least 50 punches thrown.","You connected on 60% or better across at least 50 attempts.",30),
        SHARPSHOOTER(RINGCRAFT,"Thread the needle","Reach 75% accuracy with at least 40 punches thrown.","You hit 75% accuracy after at least 40 attempts.",40),
        OUTLAND(RINGCRAFT,"Set the pace","Build a lead of 30 landed punches in a bout.","You pulled ahead by 30 landed punches.",25),
        ELUSIVE(RINGCRAFT,"Make them miss","Hold your opponent to 25% accuracy or less after at least 40 attempts.","Your opponent landed one in four or fewer over at least 40 attempts.",40),
        STAGGER(RINGCRAFT,"Rubber legs","Stagger your opponent.","You staggered your opponent.",15),
        TWO_DOWNS(RINGCRAFT,"Return trip","Score two knockdowns in one bout.","You put your opponent down twice in one fight.",35),

        UNANIMOUS_ROUND(JUDGES,"Three of a kind","Win one round on all three cards.","All three judges gave you the round.",15),
        SPLIT_ROUND(JUDGES,"Two to one","Win a round with two judges while the third scores it for your opponent.","Two judges gave you the round. The third went the other way.",20),
        BIG_ROUND(JUDGES,"Ten-eight","Win a round by two or more points on all three cards.","You won a round by two or more on every card.",35),
        WIDE_ROUND(JUDGES,"Daylight","Win a round by three or more points on a judge's card.","You took a round by three or more on one judge's card.",45),
        THREE_STRAIGHT(JUDGES,"String them together","Win three consecutive rounds with at least two judges.","You won three rounds in a row with at least two judges.",30),
        SIX_ROUNDS(JUDGES,"Bank the rounds","Win six rounds with at least two judges in a single bout.","You put six rounds in the bank with at least two judges.",45),
        ANSWER_BACK(JUDGES,"Answer the bell","Lose a round on all three cards, then win the next on all three.","All three judges had you losing one round and winning the next.",40),
        EIGHT_SCORED(JUDGES,"Eight in the books","Finish eight scored rounds in one bout.","Eight scored rounds are in the books.",35),
        CONSENSUS(JUDGES,"Clear favorite","Lead all three cards after at least four scored rounds.","You led all three cards after at least four scored rounds.",40),
        TURN_CARDS(JUDGES,"Turn the tables","Trail all three cards after at least two rounds, then take the lead on all three.","You turned a deficit on all three cards into a lead on all three.",60),

        FIRST_DRILL(GYM,"Gym regular","Complete a career training session.","You finished a career training session.",10),
        BREAKTHROUGH(GYM,"Breakthrough","Gain at least 5% in one attribute in a single session.","One attribute climbed at least five points in a single session.",25),
        BAG_DRILL(GYM,"Bag work","Finish a career heavy bag drill.","You finished the heavy bag drill.",15),
        DUMMY_DRILL(GYM,"Put it together","Finish a career combo dummy drill.","You finished the combo dummy drill.",15),
        WEIGHT_DRILL(GYM,"Iron work","Finish a career weightlifting drill.","You finished the weightlifting drill.",15),
        POWER_GAIN(GYM,"Power shift","Gain at least 3% Power in one session.","You added at least three points of Power in one session.",20),
        SPEED_GAIN(GYM,"Fast hands","Gain at least 3% Speed in one session.","You added at least three points of Speed in one session.",20),
        STAMINA_GAIN(GYM,"Roadwork","Gain at least 3% Stamina in one session.","You added at least three points of Stamina in one session.",20),
        THREE_GAINS(GYM,"All-rounder","Improve three attributes in a single session.","Three attributes improved in one session.",25),
        STRONG_SESSION(GYM,"Made it count","Earn at least 12 percentage points of positive gains in one session.","You picked up at least twelve positive training points in one session.",40),

        FIVE_WINS(CAREER,"On the card","Record five wins with one career boxer.","Your career boxer has five wins.",20),
        TEN_WINS(CAREER,"Double figures","Record ten wins with one career boxer.","Your career boxer hit double figures with ten wins.",35),
        TWENTY_FIVE_WINS(CAREER,"Main event","Record 25 wins with one career boxer.","Twenty-five wins are on one boxer's record.",55),
        TEN_KOS(CAREER,"Heavy hands","Record ten knockouts with one career boxer.","Your career boxer recorded ten knockouts.",35),
        TWENTY_FIVE_KOS(CAREER,"Lights out","Record 25 knockouts with one career boxer.","Your career boxer recorded 25 knockouts.",60),
        UNBEATEN(CAREER,"Unblemished","Reach ten career wins with no losses.","Ten wins and still no losses on your record.",50),
        KO_RATIO(CAREER,"Knockout artist","Reach 20 career wins with at least 75% won by knockout.","You reached 20 wins with at least three in four by knockout.",55),
        VETERAN(CAREER,"Ring veteran","Complete 30 bouts with one career boxer.","You went through 30 career bouts with one boxer.",45),
        STEP_UP(CAREER,"Step up","Have a contracted opponent with at least ten more career wins than you.","You signed to face someone with at least ten more wins.",25),

        FIRST_TROPHY(TROPHIES,"On the mantel","Earn a career trophy and visit the trophy room.","You visited the trophy room with your first piece of hardware.",15),
        FOUR_TROPHIES(TROPHIES,"Top shelf","Visit the trophy room with four trophies.","You brought at least four trophies to the case.",30),
        HALF_CASE(TROPHIES,"Half the hardware","Visit the trophy room with seven trophies.","At least seven trophies are on your shelves.",50),
        FULL_CASE(TROPHIES,"Full house","Fill all fourteen spots in the career trophy room.","All fourteen spots in the trophy room are filled.",100),

        SECOND_WIND(FINISHES,"Second wind","Win after being knocked down at least twice.","You hit the canvas twice and still got your hand raised.",75),
        ELEVENTH_HOUR(FINISHES,"Eleventh hour","Stop your opponent in round eleven or later.","You stopped your opponent in round eleven or later.",90),
        THUNDERCLAP(RINGCRAFT,"Thunderclap","Land twelve haymakers in one bout.","Twelve haymakers landed in one fight.",50),
        LAST_ROPE(FINISHES,"Off the ropes","Win by stoppage after suffering two knockdowns.","You got up from two knockdowns and won by stoppage.",100),
        FINAL_BELL(FINISHES,"When the bell tolls","Win a bout that reaches the twelfth round.","You won a fight that reached the twelfth round.",80),
        LATE_SURGE(FINISHES,"Still standing","Win in round eight or later after being knocked down.","You got up from a knockdown and won in round eight or later.",70),
        EMPTY_RING(FINISHES,"The last man out","Knock out your opponent in round nine or later.","You scored a knockout in round nine or later.",80),
        THREE_TRIPS(RINGCRAFT,"Canvas inspector","Score three knockdowns in one bout.","You dropped your opponent three times in one bout.",65),
        DOUBLE_CENTURY(RINGCRAFT,"Double century","Land 200 punches in one bout.","You landed 200 punches in one fight.",65),
        MARATHON_HANDS(RINGCRAFT,"Punch the clock","Throw 500 punches in one bout.","You let your hands go 500 times in one fight.",75),
        SNIPER(RINGCRAFT,"Dead center","Reach 70% accuracy after throwing at least 150 punches.","You shot 70% or better over at least 150 attempts.",85),
        SLIP_SCHOOL(RINGCRAFT,"Slip school","Keep your opponent at 25% accuracy or lower through at least 150 attempts.","Your opponent landed one in four or fewer over at least 150 attempts.",85),
        LAP_THE_FIELD(RINGCRAFT,"A lap ahead","Land 200 punches and lead by at least 100 in one bout.","You landed 200 and opened a 100-punch lead.",70),
        LEATHER_RAIN(RINGCRAFT,"Leather rain","Land 300 punches in one bout.","Three hundred punches found their mark in one fight.",100),
        HEAT_SEEKER(RINGCRAFT,"Straight to work","Land at least 100 punches with 80% accuracy or better.","You landed 100 or more at 80% accuracy or better.",85),
        IRON_FIST(RINGCRAFT,"The anvil","Land twenty haymakers in one bout.","Twenty haymakers landed in a single fight.",90),
        SIX_CLEAN(JUDGES,"Clean sweep","Win six consecutive rounds unanimously.","Six straight rounds. All three judges agreed.",75),
        TEN_ON_PAPER(JUDGES,"Ten in ink","Complete ten scored rounds in one bout.","Ten scored rounds are on the books.",50),
        BALANCE_TIPPED(JUDGES,"Tilt the scales","Build a lead of six points on every card.","You opened a six-point lead on every card.",65),
        EIGHT_STRAIGHT(JUDGES,"Cover to cover","Win eight consecutive rounds with at least two judges.","You strung together eight rounds with at least two judges.",85),
        TWELVE_SCORED(JUDGES,"The full hearing","Complete all twelve scored rounds.","You went all twelve scored rounds.",80),
        JUDGES_CHAIR(JUDGES,"Own the room","Win ten rounds with at least two judges in one bout.","You took ten rounds with at least two judges.",90),
        SEALED(JUDGES,"Signed and sealed","Win nine rounds unanimously in one bout.","Nine rounds went your way on every card.",85),
        ALL_TWELVE(JUDGES,"Every last box","Win twelve rounds on all three scorecards.","Twelve rounds. Three cards. Every round yours.",125),
        ROAD_VET(CAREER,"The road warrior","Complete 50 bouts with one career boxer.","You fought 50 career bouts with one boxer.",60),
        LOCKER(CAREER,"Permanent locker","Build one career with 25 knockout wins and 15 other wins.","You built a record with 25 knockout wins and 15 other wins.",85),
        FORTY_WINS(CAREER,"Marquee name","Win 40 bouts with one career boxer.","You earned your fortieth career win.",65),
        FIFTY_WINS(CAREER,"Under the lights","Win 50 bouts with one career boxer.","Fifty wins are on one boxer's record.",85),
        TOP_BILLING(CAREER,"Living legend","Reach 40 career wins with 30 knockouts and at most two losses.","You reached 40 wins and 30 knockouts with at most two losses.",125),
        FIFTY_KO(CAREER,"Hang up the gloves","Record 50 knockouts with one career boxer.","Your career boxer recorded 50 knockouts.",100),
        TEN_TROPHIES(TROPHIES,"The silverware","Visit the trophy room with at least ten trophies.","You visited the trophy room with ten pieces of hardware.",60),
        TWELVE_TROPHIES(TROPHIES,"Room for two","Visit the trophy room with at least twelve trophies.","At least twelve trophies are on display.",75),
        THIRTY_FIVE_KO(CAREER,"Speed bag to sandman","Record 35 knockouts with one career boxer.","Your career boxer recorded 35 knockouts.",70),
        TWENTY_FIVE_CLEAN(CAREER,"Perfect record","Reach 25 career wins with no losses or draws.","You made it to 25 wins without a loss or draw.",85),
        KO_FACTORY(CAREER,"The knockout factory","Reach 40 career wins with at least 90% won by knockout.","You reached 40 wins with at least 90% by knockout.",100),
        FIFTY_CLEAN(CAREER,"Zero on the ledger","Reach 50 career wins with no losses or draws.","You went 50 wins without a loss or draw.",125),
        DISTANCE(CAREER,"Go the distance","Win 20 career fights without a knockout.","You recorded 20 career wins without a knockout.",75),
        CAMP_COMPLETE(TROPHIES,"Camp graduate","Earn every medal on the Gym shelf.","You earned every medal on the Gym shelf.",125),
        THIRTY_DECISIONS(CAREER,"Leave it to the judges","Win 30 career fights without a knockout.","You recorded 30 career wins without a knockout.",100),
        CENTURION(TROPHIES,"The centurion","Earn all 99 other achievements.","You earned every other achievement in FightNight3OGX.",150);

        final Category category;
        final String title,goal,deed;
        final int points,careerEventId;
        Award(Category category,String title,String goal,String deed,int points){this(category,title,goal,deed,points,0);}
        Award(String title,String goal,String deed,int points,int careerEventId){this(CLASSICS,title,goal,deed,points,careerEventId);}
        Award(Category category,String title,String goal,String deed,int points,int careerEventId){
            this.category=category;this.title=title;this.goal=goal;this.deed=deed;this.points=points;this.careerEventId=careerEventId;
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
        completeShelves();
    }
    synchronized boolean earned(Award a){return earned.contains(a);}
    synchronized int count(){return earned.size();}
    synchronized Award nextRibbon(){return pending.pollFirst();}
    private synchronized void unlock(Award a){
        if(earned.add(a)){
            save.accept(a.name());pending.addLast(a);
            if(a.category==GYM || earned.size()==Award.values().length-1)completeShelves();
        }
    }
    private void completeShelves(){
        if(!earned.contains(Award.CAMP_COMPLETE)){
            boolean complete=true;
            for(Award a:Award.values())if(a.category==GYM && !earned.contains(a)){complete=false;break;}
            if(complete)unlock(Award.CAMP_COMPLETE);
        }
        if(earned.size()==Award.values().length-1 && !earned.contains(Award.CENTURION))unlock(Award.CENTURION);
    }
    synchronized int classicPoints(){
        int total=0;
        for(Award a:earned)if(a.category==CLASSICS)total+=a.points;
        return total;
    }
    synchronized int fightScore(){
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
        if(haymakers>=12)unlock(Award.THUNDERCLAP);
        if(scored>=3)unlock(Award.THREE_TRIPS);
        if(haymakers>=20)unlock(Award.IRON_FIST);
        if(scored>=2)unlock(Award.TWO_DOWNS);
        if(haymakers>=5)unlock(Award.HAYMAKERS);
        if(changed){punches(bout);scores(card);}
    }
    void punches(FightBroadcast.Bout data){
        bout=data;
        if(side<0 || data==null || data.punches==null)return;
        int[] p=data.punches;int own=2*side,other=2*(1-side);
        int landed=p[own],thrown=p[own+1];
        if(landed>=200)unlock(Award.DOUBLE_CENTURY);
        if(thrown>=500)unlock(Award.MARATHON_HANDS);
        if(thrown>=150 && landed*10>=thrown*7)unlock(Award.SNIPER);
        if(p[other+1]>=150 && p[other]*4<=p[other+1])unlock(Award.SLIP_SCHOOL);
        if(landed-p[other]>=100 && landed>=200)unlock(Award.LAP_THE_FIELD);
        if(landed>=300)unlock(Award.LEATHER_RAIN);
        if(landed>=100 && landed*5>=thrown*4)unlock(Award.HEAT_SEEKER);
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
        int wins=0,streak=0,cleanStreak=0,cleanWins=0;
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
            if(ahead==3){cleanStreak++;cleanWins++;}else cleanStreak=0;
            if(cleanStreak>=6)unlock(Award.SIX_CLEAN);
            if(r>=10)unlock(Award.TEN_ON_PAPER);
            if(Math.min(margins[0],Math.min(margins[1],margins[2]))>=6)unlock(Award.BALANCE_TIPPED);
            if(streak>=8)unlock(Award.EIGHT_STRAIGHT);
            if(r>=12)unlock(Award.TWELVE_SCORED);
            if(wins>=10)unlock(Award.JUDGES_CHAIR);
            if(cleanWins>=9)unlock(Award.SEALED);
            if(cleanStreak>=12)unlock(Award.ALL_TWELVE);
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
        if(sufferedDowns>=2 && side>=0)unlock(Award.SECOND_WIND);
        if((ko || tko) && round>=11)unlock(Award.ELEVENTH_HOUR);
        if((ko || tko) && side>=0 && sufferedDowns>=2)unlock(Award.LAST_ROPE);
        if(round>=12)unlock(Award.FINAL_BELL);
        if(side>=0 && sufferedDowns>=1 && round>=8)unlock(Award.LATE_SURGE);
        if(ko && round>=9)unlock(Award.EMPTY_RING);
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
        if(r.bouts()>=50)unlock(Award.ROAD_VET);
        if(r.knockouts>=25 && r.wins-r.knockouts>=15)unlock(Award.LOCKER);
        if(r.wins>=40)unlock(Award.FORTY_WINS);
        if(r.wins>=50)unlock(Award.FIFTY_WINS);
        if(r.wins>=40 && r.knockouts>=30 && r.losses<=2)unlock(Award.TOP_BILLING);
        if(r.knockouts>=50)unlock(Award.FIFTY_KO);
        if(r.knockouts>=35)unlock(Award.THIRTY_FIVE_KO);
        if(r.wins>=25 && r.losses==0 && r.draws==0)unlock(Award.TWENTY_FIVE_CLEAN);
        if(r.wins>=40 && r.knockouts*10>=r.wins*9)unlock(Award.KO_FACTORY);
        if(r.wins>=50 && r.losses==0 && r.draws==0)unlock(Award.FIFTY_CLEAN);
        if(r.wins-r.knockouts>=20)unlock(Award.DISTANCE);
        if(r.wins-r.knockouts>=30)unlock(Award.THIRTY_DECISIONS);
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
        if(total>=10)unlock(Award.TEN_TROPHIES);
        if(total>=12)unlock(Award.TWELVE_TROPHIES);
        if(total>=1)unlock(Award.FIRST_TROPHY);
        if(total>=4)unlock(Award.FOUR_TROPHIES);
        if(total>=7)unlock(Award.HALF_CASE);
        if(total==14)unlock(Award.FULL_CASE);
    }
}
