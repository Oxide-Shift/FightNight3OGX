package org.oxideshift.corner;

/** Play Now selection report: both sides' rows, names, record columns and malformed packets. Synthetic values only. */
public final class PlayNowPickSmoke {
    static void check(boolean pass,String message){if(!pass)throw new AssertionError(message);}
    static String row(int wins,int losses,int draws,int kos){
        StringBuilder b=new StringBuilder();
        for(int c=0;c<40;c++){
            int v=c==31?wins:c==32?losses:c==33?draws:c==34?kos:c;
            if(c>0)b.append(',');b.append(v);
        }
        return b.toString();
    }
    public static void main(String[] args){
        // "TEST ONE" / "TEST TWO" in byte hex.
        String blue="54455354204F4E45",red="544553542054574F";
        PlayNowPick p=PlayNowPick.parse("7 "+blue+" "+row(20,3,1,15)+" 12 "+red+" "+row(9,9,0,2)+" m,ffffffff,0,7,c");
        check(p!=null && p.has(0) && p.has(1),"parsed both sides");
        check(p.id[0]==7 && p.id[1]==12 && "TEST ONE".equals(p.name[0]) && "TEST TWO".equals(p.name[1]),"ids and names");
        check(p.wins(0)==20 && p.losses(0)==3 && p.draws(0)==1 && p.knockouts(0)==15,"record columns");
        check(p.knockoutRate(0)==75 && p.knockoutRate(1)==22,"knockout rate");
        check(p.cell(0,11)==11 && p.cell(0,200)==-1,"cells and bounds");
        check(p.manager.length==4 && p.manager[0]==0xffffffffL && p.manager[3]==12,"manager words");
        PlayNowPick half=PlayNowPick.parse("- - - 12 "+red+" "+row(1,0,0,1)+" m");
        check(half!=null && !half.has(0) && half.has(1) && half.wins(0)==-1,"one side missing");
        check(PlayNowPick.parse("7 "+blue)==null && PlayNowPick.parse("x "+blue+" 1 2 "+red+" 3 m")==null
                && PlayNowPick.parse("7 ZZ 1 2 "+red+" 3 m")!=null,"malformed packets");
        String words=",ffffffff,ffffffff,ffff,0,1,1,0,a,e,a,e";
        PlayNowPick blueSide=PlayNowPick.parse("- - - - - - m"+words+",0,ffffffff,ffffffff,ffffffff,0");
        PlayNowPick middle=PlayNowPick.parse("- - - - - - m"+words+",ffffffff,ffffffff,ffffffff,ffffffff,0");
        PlayNowPick redSide=PlayNowPick.parse("- - - - - - m"+words+",ffffffff,0,ffffffff,ffffffff,0");
        check(blueSide.padIn(0)==0 && blueSide.padIn(1)==-1 && middle.padIn(0)==-1 && middle.padIn(1)==-1
                && redSide.padIn(1)==0 && redSide.padIn(0)==-1 && p.padIn(0)==-1,"corner slots");
        check(p.sameBoxers(PlayNowPick.parse("7 "+blue+" 1 12 "+red+" 2 m")) && !p.sameBoxers(half),"same boxers");
        System.out.println("Play Now selection: sides, names, records, bounds and malformed packets passed");
    }
}
