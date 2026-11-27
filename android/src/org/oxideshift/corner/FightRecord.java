package org.oxideshift.corner;

/** Lifetime fight record for the player's boxer, built from the game's own fight events and kept in two books:
 *  career bouts and every bout (career plus Play Now). A bout counts once its result is known; a fight thrown in
 *  or quit before the result is never recorded. Pure apart from the Store, so it can be tested off device.
 *
 *  Sources (all already on the session pipe): PLAYERSTATS (player's side, knockdowns scored and suffered,
 *  haymakers), BOUT (cumulative landed/thrown for both corners), DAMAGE (one landed punch: who threw it, head or
 *  body, attack type; the retail routine logs each punch twice), CALLOUT STAGGER/KO/TKO/WON/LOST/FINAL, and the
 *  rounds reached. */
final class FightRecord {
    interface Store { long get(String key); void put(String key,long value); }

    /** Every stat the page shows, in book order. */
    enum Stat {
        BOUTS,WINS,LOSSES,DRAWS,KO_WINS,TKO_WINS,STOPPED,ROUNDS,
        THROWN,LANDED,ABSORBED,OPP_THROWN,HEAD,BODY,
        /** Landed by target area, in DamageMap.Tally.AREAS order. */
        AT_LEFT_EYE,AT_RIGHT_EYE,AT_NOSE,AT_MOUTH,AT_LEFT_JAW,AT_RIGHT_JAW,AT_LEFT_BODY,AT_RIGHT_BODY,
        JAB,STRAIGHT,HOOK_L,HOOK_R,UPPER_L,UPPER_R,SIGNATURE,ILLEGAL,
        KNOCKDOWNS,KNOCKED_DOWN,HAYMAKERS,STAGGERS,
        BEST_LANDED,BEST_KNOCKDOWNS,FASTEST_KO,STREAK,BEST_STREAK
    }
    static final String CAREER="career.",ALL="all.";
    /** Retail attack types (xbe table 0x548018) in Stat order JAB..ILLEGAL. */
    static final String[] ATTACKS={"ATTACK_TYPE_STRAIGHT_L","ATTACK_TYPE_STRAIGHT_R","ATTACK_TYPE_HOOK_L","ATTACK_TYPE_HOOK_R",
            "ATTACK_TYPE_UPPERCUT_L","ATTACK_TYPE_UPPERCUT_R","ATTACK_TYPE_SIGNATURE","ATTACK_TYPE_ILLEGAL"};

    private final Store store;
    // The bout in progress.
    private boolean active,career;
    private int side=-1,downs,suffered,haymakers,staggers,round;
    private int[] punches;               // blue landed, blue thrown, red landed, red thrown
    private final int[][] attacks=new int[2][ATTACKS.length];
    private final int[][] zones=new int[2][2]; // [puncher][head, body]
    private final int[][] areas=new int[2][8];  // [puncher][DamageMap.Tally.AREAS]
    static final Stat[] AREA_STATS={Stat.AT_LEFT_EYE,Stat.AT_RIGHT_EYE,Stat.AT_NOSE,Stat.AT_MOUTH,Stat.AT_LEFT_JAW,Stat.AT_RIGHT_JAW,Stat.AT_LEFT_BODY,Stat.AT_RIGHT_BODY};
    private String result="",finish="",lastDamage="";
    private boolean skippedDamage,committed;

    FightRecord(Store store){this.store=store;}

    long get(boolean careerBook,Stat s){return store.get((careerBook?CAREER:ALL)+s.name().toLowerCase(java.util.Locale.ROOT));}

    /** A new bout begins; careerBout says whether it was entered from the career hub. */
    synchronized void begin(boolean careerBout){
        finishPending();
        active=true;career=careerBout;side=-1;downs=suffered=haymakers=staggers=round=0;punches=null;
        for(int[] a:attacks)java.util.Arrays.fill(a,0);
        for(int[] z:zones)java.util.Arrays.fill(z,0);
        for(int[] a:areas)java.util.Arrays.fill(a,0);
        result="";finish="";lastDamage="";skippedDamage=false;committed=false;
    }
    synchronized void playerStats(int player,int scored,int against,int hay){
        if(!active || player<0 || player>1)return;
        side=player;downs=scored;suffered=against;haymakers=hay;
    }
    synchronized void punches(int[] totals,int boutRound){
        if(!active || totals==null || totals.length!=4)return;
        punches=totals.clone();round=Math.max(round,boutRound);
    }
    synchronized void round(int boutRound){if(active)round=Math.max(round,boutRound);}
    /** "FNR3/1 DAMAGE <puncher> <area> <HEAD|BODY> <amount> [attack]"; consecutive duplicates are one punch. */
    synchronized boolean damage(String line){
        if(!active || !line.startsWith("FNR3/1 DAMAGE "))return false;
        String[] p=line.substring(14).split(" ");
        if(p.length!=5)return false;
        int puncher;
        try{puncher=Integer.parseInt(p[0]);Float.parseFloat(p[3]);}catch(NumberFormatException e){return false;}
        if(puncher<0 || puncher>1 || !p[1].startsWith("DAMAGE_AREA_"))return false;
        String key=p[0]+" "+p[1]+" "+p[4];
        if(key.equals(lastDamage) && !skippedDamage){skippedDamage=true;return false;}
        skippedDamage=false;lastDamage=key;
        zones[puncher][p[2].equals("BODY")?1:0]++;
        int area=DamageMap.Tally.area(p[1]);if(area>=0)areas[puncher][area]++;
        for(int i=0;i<ATTACKS.length;i++)if(ATTACKS[i].equals(p[4])){attacks[puncher][i]++;break;}
        return true;
    }
    synchronized void stagger(){if(active)staggers++;}
    /** CALLOUT WON, LOST, FINAL, KO or TKO. WON and LOST settle the bout; FINAL alone is a draw once the next
     *  bout starts or the session ends without a winner. */
    synchronized void callout(String event){
        if(!active)return;
        if(event.equals("KO") || event.equals("TKO"))finish=event;
        else if(event.equals("WON") || event.equals("LOST")){result=event;commit();}
        else if(event.equals("FINAL") && result.isEmpty())result="FINAL";
    }
    /** Session or bout boundary: a bout that reached its final bell without a winner is a draw. */
    synchronized void finishPending(){
        if(active && !committed && result.equals("FINAL"))commit();
    }

    private void commit(){
        if(committed || side<0)return;
        committed=true;
        for(String book:career?new String[]{CAREER,ALL}:new String[]{ALL})commit(book);
    }
    private long read(String book,Stat s){return store.get(book+s.name().toLowerCase(java.util.Locale.ROOT));}
    private void add(String book,Stat s,long n){if(n!=0)store.put(book+s.name().toLowerCase(java.util.Locale.ROOT),read(book,s)+n);}
    private void set(String book,Stat s,long n){store.put(book+s.name().toLowerCase(java.util.Locale.ROOT),n);}
    private void commit(String book){
        boolean won=result.equals("WON"),lost=result.equals("LOST");
        add(book,Stat.BOUTS,1);
        add(book,won?Stat.WINS:lost?Stat.LOSSES:Stat.DRAWS,1);
        if(won && finish.equals("KO"))add(book,Stat.KO_WINS,1);
        if(won && finish.equals("TKO"))add(book,Stat.TKO_WINS,1);
        if(lost && !finish.isEmpty())add(book,Stat.STOPPED,1);
        add(book,Stat.ROUNDS,round);
        int other=1-side,landed=0;
        if(punches!=null){
            landed=punches[2*side];
            add(book,Stat.LANDED,landed);add(book,Stat.THROWN,punches[2*side+1]);
            add(book,Stat.ABSORBED,punches[2*other]);add(book,Stat.OPP_THROWN,punches[2*other+1]);
        }
        add(book,Stat.HEAD,zones[side][0]);add(book,Stat.BODY,zones[side][1]);
        for(int i=0;i<AREA_STATS.length;i++)add(book,AREA_STATS[i],areas[side][i]);
        Stat[] kinds={Stat.JAB,Stat.STRAIGHT,Stat.HOOK_L,Stat.HOOK_R,Stat.UPPER_L,Stat.UPPER_R,Stat.SIGNATURE,Stat.ILLEGAL};
        for(int i=0;i<kinds.length;i++)add(book,kinds[i],attacks[side][i]);
        add(book,Stat.KNOCKDOWNS,downs);add(book,Stat.KNOCKED_DOWN,suffered);
        add(book,Stat.HAYMAKERS,haymakers);add(book,Stat.STAGGERS,staggers);
        if(landed>read(book,Stat.BEST_LANDED))set(book,Stat.BEST_LANDED,landed);
        if(downs>read(book,Stat.BEST_KNOCKDOWNS))set(book,Stat.BEST_KNOCKDOWNS,downs);
        if(won && !finish.isEmpty() && round>0 && (read(book,Stat.FASTEST_KO)==0 || round<read(book,Stat.FASTEST_KO)))
            set(book,Stat.FASTEST_KO,round);
        long streak=won?read(book,Stat.STREAK)+1:0;
        set(book,Stat.STREAK,streak);
        if(streak>read(book,Stat.BEST_STREAK))set(book,Stat.BEST_STREAK,streak);
    }
    /** Whole-number percentage, or -1 when there is nothing to divide. */
    static int percent(long part,long whole){return whole<=0?-1:(int)Math.round(part*100.0/whole);}
}
