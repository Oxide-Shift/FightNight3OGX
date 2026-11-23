package org.oxideshift.corner;

import java.text.NumberFormat;
import java.util.Locale;

/** The front end writes a different screen-stack ID for pages sharing a movie. */
final class CareerDesk {
    static final class Page {
        final String group,title,note,detail;
        final int index;
        Page(String group,int index,String title,String note,String detail){
            this.group=group;this.index=index;this.title=title;this.note=note;this.detail=detail;
        }
        boolean is(String key){return group.equals(key);}
    }
    private static Page p(String group,int n,String title,String note,String detail){
        return new Page(group,n,title,note,detail);
    }
    static final Page[] PAGES={
        p("careercentral",1,"Career Central","Your record follows you between contracts.","Open Fight to see the current offers or prepare for a signed bout."),
        p("careercentral",2,"Save your career","Check the profile and slot before saving.","You can also export a backup from Fight Setup after leaving the game."),
        p("mcon",1,"Contract desk","Browse an offer to bring the opponent into your corner.","Compare power, speed and stamina before signing."),
        p("mcon",2,"Scouting report","Study the opponent on this offer.","Look for the rating that changes how you want to open the first round."),
        p("drill",1,"Heavy bag","Set your feet before the first punch.","Plant your feet. Turn through each punch."),
        p("drill",2,"Combo dummy","Read the target before you throw.","Match the target. Reset between combinations."),
        p("drill",3,"Weightlifting","Find a rhythm you can repeat.","Time each rep to the cue."),
        p("drill",4,"Sparring","Give the round one purpose.","Land the jab and step out of counter range."),
        p("train",1,"Training results","Check which attributes moved after the session.","Use the next session to address a weakness the last fight exposed."),
        p("train",2,"Training floor","Choose your session in the game.","For sparring, pick one goal for the round. Practice landing a jab and leaving range before the return punch."),
        p("train",3,"Hire a trainer","Browse trainers to compare their cost with your available cash.","Look at the attributes this trainer develops before committing the fee."),
        p("fightstore",1,"Fight store","Browse the gear to check your budget.","Compare the item's effect with the rating you want to improve."),
        p("fightstore",2,"Purchase desk","Review this item's price before you confirm it.","Check what will be left for a trainer after the purchase."),
        p("fightstore",3,"Gear preview","Inspect the selected gear on your fighter.","Browse another item to compare its look and effect."),
        p("trophycase",1,"Trophy case","Select a trophy to see its place in your collection.","Pause on a trophy to read its name and description upstairs."),
        p("rankings",1,"Rankings","Check who sits above you in the division.","Use the rankings alongside the contract list when you choose the next opponent."),
        p("careersummary",1,"Career recap","Review the career you built.","Visit the trophy case to revisit the awards behind the record."),
        p("records",1,"Record book","Browse the career records.","Compare a long career with a shorter run before judging the knockout total."),
        p("records",2,"Hall of Fame","Browse the fighters in the Hall of Fame.","Look at wins and knockout wins together when comparing careers."),
        p("cac",1,"Edit boxer","Choose which part of your boxer to edit.","Review your changes before returning to camp."),
        p("cac",2,"Ring identity","Set the name and hometown that follow your boxer.","Check the spoken name before leaving this page."),
        p("cac",3,"Boxing style","Pick a stance you are comfortable defending from.","An orthodox boxer leads with the left. A southpaw leads with the right."),
        p("cac",4,"Fighter ratings","Find the attribute you want to build around.","Compare power with stamina when planning how long to stay in an exchange."),
        p("cac",5,"Boxer appearance","Browse your boxer's appearance options.","Check the face from more than one angle."),
        p("cac",6,"Physique","Review your boxer's build.","Try your jab at two distances in your first session with this build."),
        p("cac",7,"Head shape","Preview the head shape as you adjust it.","Check the profile as well as the front view."),
        p("cac",8,"Facial features","Work on one feature at a time.","Compare the brow, nose and jaw from the same angle."),
        p("cac",9,"Start a career","Choose a created boxer or a returning legend.","A rebuilt legend starts a new career record."),
        p("cac",10,"Choose a legend","Browse legends in the selected weight class.","Pick a stance and reach you enjoy using across a full career."),
        p("options",1,"Career options","Choose the settings you want to adjust.","Use the controller page to check a binding before your next bout."),
        p("options",2,"Gameplay settings","Check round length and difficulty.","Short rounds leave less time to win back a close score."),
        p("options",3,"Corner audio","Balance speech, music and the crowd.","Turn the crowd down to bring the coach forward between rounds."),
        p("options",4,"Controller setup","Review the active control layout.","Check your block and body modifier before returning to the ring."),
        p("options",5,"EA Trax","Choose which tracks are in rotation.","Listen to a track while you decide whether to keep it in the rotation."),
        p("options",6,"Profile settings","Review the active profile.","Check the displayed name before saving a change."),
        p("options",7,"Saved settings","Choose the settings slot you want to use.","Check the destination before confirming an overwrite.")
    };
    volatile Page page;
    volatile long revision;
    private volatile Offer offer;
    Offer selectedOffer(){return offer;}
    private volatile Budget shop,trainer;
    private volatile int trophy=-1,caseMask=-1;
    private volatile int[] actual,estimate;
    int[] training(boolean forecast){return forecast?estimate:actual;}
    Budget trainerBudget(){return trainer;}
    Budget storeBudget(){return shop;}
    int trophies(){return caseMask;}
    int selectedTrophy(){return trophy;}
    private volatile int selectedDrill;
    Page selectedDrill(){return find("drill",selectedDrill);}
    private static int integer(String s,int min,int max){
        int value=Integer.parseInt(s);
        if(value<min || value>max)throw new NumberFormatException();
        return value;
    }
    static String money(int n){return NumberFormat.getCurrencyInstance(Locale.US).format(n).replace(".00","");}
    static final class Budget {
        final int cost,cash;
        Budget(int price,int balance){cost=price;cash=balance;}
        String reading(){
            if(cost<0)return cash>=0?"Available cash  "+money(cash):"";
            String price="Price  "+money(cost);
            if(cash<0)return price;
            return price+"     Wallet  "+money(cash)+"\n"+(cash>=cost?"After purchase  "+money(cash-cost):"Short by  "+money(cost-cash));
        }
    }
    static final class Offer {
        final String name;
        final int purse;
        final int[] ratings;
        final String ownRecord,rivalRecord;
        final FightBroadcast.Record own,rival;
        Offer(String name,int purse,int[] ratings){this(name,purse,ratings,"-","-");}
        Offer(String name,int purse,int[] ratings,String ownRecord,String rivalRecord){
            this.name=name;this.purse=purse;this.ratings=ratings;this.ownRecord=ownRecord;this.rivalRecord=rivalRecord;
            own=FightBroadcast.Record.parse(ownRecord);rival=FightBroadcast.Record.parse(rivalRecord);
        }
        String experience(){
            if(rival==null)return "";
            if(own!=null && Math.abs(rival.bouts()-own.bouts())>=3){
                int gap=rival.bouts()-own.bouts();
                return (gap>0?"They have ":"You have ")+Math.abs(gap)+" more career bouts.";
            }
            if(rival.wins>=3)return rival.knockouts+" of their "+rival.wins+" wins came by knockout.";
            return "";
        }
        String grades(){
            String[] labels={"Power","Speed","Stamina"};
            StringBuilder s=new StringBuilder("Your boxer  /  "+name);
            for(int i=0;i<3;i++)if(ratings[i]>=0 && ratings[i+3]>=0)
                s.append("\n").append(labels[i]).append("    ").append(ratings[i]).append("  /  ").append(ratings[i+3]);
            int ahead=0,behind=0,known=0;
            for(int i=0;i<3;i++)if(ratings[i]>=0 && ratings[i+3]>=0){
                known++;int difference=ratings[i]-ratings[i+3];
                if(difference>=5)ahead++;
                if(difference<=-5)behind++;
            }
            if(known==3)s.append("\n").append(ahead>=2?"Matchup lean   Your corner":behind>=2?"Matchup lean   Opponent":"Matchup lean   Even on ratings");
            return s.toString();
        }
        String plan(){
            int index=-1,gap=0;
            for(int i=0;i<3;i++){
                if(ratings[i]<0 || ratings[i+3]<0)continue;
                int d=ratings[i]-ratings[i+3];
                if(index<0 || Math.abs(d)>Math.abs(gap)){index=i;gap=d;}
            }
            if(index<0)return "Open with a jab and see how this opponent responds before committing the rear hand.";
            if(Math.abs(gap)<5)return "The available ratings are close. Use the first round to find the distance where your jab lands cleanly.";
            String[][] advice={
                {"Your power leads. Draw the jab and counter.","They have the power edge. Land two and step out."},
                {"Your hands are quicker. Jab and step away.","They have the speed edge. Block first and counter."},
                {"Your stamina leads. Work the body early.","They have the stamina edge. Punch in short bursts."}
            };
            return advice[index][gap>0?0:1];
        }
    }
    static Page find(String group,int n){
        for(Page p:PAGES)if(p.index==n && p.is(group))return p;
        return null;
    }
    void accept(String event,String payload){
        Page previousPage=page;
        Offer previousOffer=offer;
        Budget previousShop=shop,previousTrainer=trainer;
        int[] previousActual=actual,previousEstimate=estimate;
        int previousTrophy=trophy,previousMask=caseMask,previousDrill=selectedDrill;
        String[] v=payload.split(" ",-1);
        try{
            if(event.equals("CAREERPAGE")){
                Page next=v.length==2?find(v[0],integer(v[1],1,10)):null;
                if(next==null || !next.is("mcon"))offer=null;
                if(next==null || !next.is("fightstore"))shop=null;
                if(next==null || !(next.is("drill") || next.is("train")))selectedDrill=0;
                if(next!=null && next.is("drill")){
                    selectedDrill=next.index;
                    trainer=null;actual=null;
                    if(next!=page && (page==null || !page.is("train") || page.index!=2))estimate=null;
                }else if(next==null || !next.is("train")){trainer=null;actual=null;estimate=null;}
                else if(next!=page && next.index==1)estimate=null;
                if(next==null || !next.is("trophycase")){trophy=-1;caseMask=-1;}
                page=next;
            }else if(event.equals("OFFER")){
                if(v.length!=8 && v.length!=10){offer=null;return;}
                String name=FightBroadcast.Fighters.name(v[0]);
                if(name==null){offer=null;return;}
                int[] ratings=new int[6];
                for(int i=0;i<6;i++)ratings[i]=integer(v[i+2],-1,100);
                int purse=integer(v[1],-1,Integer.MAX_VALUE);
                String own=v.length==10?v[8]:"-",rival=v.length==10?v[9]:"-";
                Offer previous=offer;
                if(previous==null || !name.equals(previous.name) || purse!=previous.purse ||
                        !own.equals(previous.ownRecord) || !rival.equals(previous.rivalRecord) ||
                        !java.util.Arrays.equals(previous.ratings,ratings))offer=new Offer(name,purse,ratings,own,rival);
            }else if(event.equals("TRAINCHOICE")){
                selectedDrill=integer(payload,0,4);
            }else if(event.equals("TRAINSTATS")){
                if(v.length==0 || !(v[0].equals("0") || v[0].equals("1")))return;
                boolean forecast=v[0].equals("1");
                if(v.length!=9){if(forecast)estimate=null;else actual=null;return;}
                int[] stats=new int[8];
                for(int i=0;i<stats.length;i++)stats[i]=integer(v[i+1],-100,100);
                if(forecast){if(!java.util.Arrays.equals(estimate,stats))estimate=stats;}
                else if(!java.util.Arrays.equals(actual,stats))actual=stats;
            }else if(event.equals("SHOP") || event.equals("TRAINER")){
                boolean shopping=event.equals("SHOP");
                Budget budget=shopping?shop:trainer;
                if(v.length!=2)budget=null;
                else{
                    int cost=integer(v[0],-1,Integer.MAX_VALUE),cash=integer(v[1],-1,Integer.MAX_VALUE);
                    if(budget==null || budget.cost!=cost || budget.cash!=cash)budget=new Budget(cost,cash);
                }
                if(shopping)shop=budget;else trainer=budget;
            }else if(event.equals("TROPHY"))trophy=integer(payload,-1,13);
            else if(event.equals("CASE"))caseMask=integer(payload,0,16383);
        }catch(NumberFormatException ignored){
            if(event.equals("TRAINSTATS") && v.length>0){if(v[0].equals("1"))estimate=null;else if(v[0].equals("0"))actual=null;}
            if(event.equals("OFFER"))offer=null;
            if(event.equals("TRAINCHOICE"))selectedDrill=0;
            if(event.equals("SHOP"))shop=null;
            if(event.equals("TRAINER"))trainer=null;
            if(event.equals("TROPHY"))trophy=-1;
            if(event.equals("CASE"))caseMask=-1;
            if(event.equals("CAREERPAGE"))accept("CAREERPAGE","-");
        }finally{
            if(previousPage!=page || previousOffer!=offer || previousShop!=shop || previousTrainer!=trainer ||
                    previousActual!=actual || previousEstimate!=estimate || previousTrophy!=trophy || previousMask!=caseMask || previousDrill!=selectedDrill)revision++;
        }
    }
    String text(Page p,boolean details){
        if(p.is("mcon")){
            Offer current=offer;
            if(current==null)return details?p.detail:p.note;
            return details?current.grades():current.name+(current.purse<0?"":"    "+money(current.purse)+" purse")+"\n"+current.plan();
        }
        if(!details){
            Budget budget=p.is("fightstore")?shop:p.is("train") && p.index==3?trainer:null;
            if(budget!=null && !budget.reading().isEmpty())return budget.reading();
            if(p.is("trophycase") && caseMask>=0){
                String current=trophy<0?"":"\nTrophy "+(trophy+1)+"   "+((caseMask&(1<<trophy))!=0?"Earned":"Locked");
                return Integer.bitCount(caseMask)+" of 14 trophies earned"+current;
            }
        }
        return details?p.detail:p.note;
    }
}
