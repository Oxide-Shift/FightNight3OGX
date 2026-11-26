package org.oxideshift.corner;

/** Immutable snapshots from the retail judges and the soundtrack archive. */
final class FightBroadcast {
    static final class Card {
        final int round;
        final int[][] rows;
        Card(int round,int[][] rows){this.round=round;this.rows=rows;}

        static Card parse(String payload){
            if(payload==null || payload.isEmpty())return null;
            String[] fields=payload.split(" ");
            if(fields.length==0)return null;
            try{
                int round=Integer.parseInt(fields[0]);
                if(round<0 || round>15 || fields.length>16)return null;
                int[][] rows=new int[15][];
                for(int i=1;i<fields.length;i++){
                    String[] values=fields[i].split(",");
                    if(values.length!=7)return null;
                    int slot=Integer.parseInt(values[0]);
                    if(slot<1 || slot>round || slot>=15 || rows[slot]!=null)return null;
                    int[] points=new int[6];
                    for(int j=0;j<6;j++){
                        points[j]=Integer.parseInt(values[j+1]);
                        if(points[j]<1 || points[j]>10)return null;
                    }
                    rows[slot]=points;
                }
                return new Card(round,rows);
            }catch(NumberFormatException err){return null;}
        }
        int last(){for(int i=rows.length-1;i>=0;i--)if(rows[i]!=null)return i;return -1;}
        int total(int judge,int side){int value=0;for(int[] row:rows)if(row!=null)value+=row[judge*2+side];return value;}
        /** Filed rounds won {blue, red}: a round goes to the fighter at least two of the three judges scored higher. */
        int[] roundsWon(){
            int[] won=new int[2];
            for(int[] row:rows){
                if(row==null)continue;
                int blue=0,red=0;
                for(int judge=0;judge<3;judge++){
                    if(row[judge*2]>row[judge*2+1])blue++;
                    else if(row[judge*2+1]>row[judge*2])red++;
                }
                if(blue>=2)won[0]++;else if(red>=2)won[1]++;
            }
            return won;
        }
    }

    static final class Bout {
        final int round, seconds;
        final int[] levels, punches;
        Bout(int round,int seconds,int[] levels,int[] punches){
            this.round=round;this.seconds=seconds;this.levels=levels;this.punches=punches;
        }
        static Bout parse(String message){
            String[] parts=message.split(" ");
            if(parts.length!=6 && parts.length!=10)return null;
            try {
                int round=Integer.parseInt(parts[0]),seconds=Integer.parseInt(parts[1]);
                if(round<1 || round>15 || seconds<0 || seconds>3600)return null;
                int[] levels=new int[4];
                for(int i=0;i<4;i++){
                    levels[i]=Integer.parseInt(parts[i+2]);
                    if(levels[i]<0 || levels[i]>100)return null;
                }
                int[] punches=null;
                if(parts.length==10){
                    punches=new int[4];
                    for(int i=0;i<4;i++){
                        int value=Integer.parseInt(parts[6+i]);
                        if(value<0 || value>100000)return null;
                        punches[i]=value;
                    }
                    if(punches[0]>punches[1] || punches[2]>punches[3])return null;
                }
                return new Bout(round,seconds,levels,punches);
            }catch(NumberFormatException err){return null;}
        }
        static String time(int seconds){
            int s=seconds%60;
            return seconds/60+":"+(s<10?"0":"")+s;
        }
    }

    static final class Record {
        final int wins,losses,draws,knockouts;
        Record(int w,int l,int d,int k){wins=w;losses=l;draws=d;knockouts=k;}
        int bouts(){return wins+losses+draws;}
        static Record parse(String value){
            String[] fields=value.split(",",-1);
            if(fields.length!=4)return null;
            int[] counts=new int[4];
            try{
                for(int i=0;i<4;i++){
                    counts[i]=Integer.parseInt(fields[i]);
                    if(counts[i]<0 || counts[i]>32767)return null;
                }
                if(counts[3]>counts[0])return null;
                return new Record(counts[0],counts[1],counts[2],counts[3]);
            }catch(NumberFormatException error){return null;}
        }
    }
    static final class Camp {
        final Record boxer,opponent;
        final String purse,odds;
        Camp(Record boxer,Record opponent,int amount){
            this.boxer=boxer;this.opponent=opponent;
            purse=opponent==null || amount<0?"":"Fight purse $"+java.text.NumberFormat.getIntegerInstance(java.util.Locale.US).format(amount);
            if(opponent==null || boxer.bouts()==0 || opponent.bouts()==0){odds="";return;}
            // Count draws as half a win. Two prior wins and losses temper small records.
            double yours=(boxer.wins+boxer.draws*.5+2)/(boxer.losses+boxer.draws*.5+2);
            double theirs=(opponent.wins+opponent.draws*.5+2)/(opponent.losses+opponent.draws*.5+2);
            int pct=Math.max(1,Math.min(99,(int)Math.round(100*yours/(yours+theirs))));
            odds="Record estimate   You "+pct+"%  /  Opp. "+(100-pct)+"%";
        }
        static Camp parse(String payload){
            String[] sides=payload.split(" ",-1);
            if(sides.length!=2 && sides.length!=3)return null;
            Record boxer=Record.parse(sides[0]);
            int amount=-1;
            // Older hosts sent a bare number containing the career wallet.
            if(sides.length==3 && sides[2].startsWith("purse="))
                try{amount=Integer.parseInt(sides[2].substring(6));}catch(NumberFormatException ignored){}
            return boxer==null?null:new Camp(boxer,Record.parse(sides[1]),amount);
        }
        private static String line(Record r){
            String record=r.wins+"W  "+r.losses+"L  "+r.draws+"D";
            if(r.bouts()==0)return record+"   /   Debut";
            return record+"   /   "+r.knockouts+" KO";
        }
        String notes(){
            String notes="Your record   "+line(boxer);
            if(boxer.wins>0)notes+="\n"+Math.round(100f*boxer.knockouts/boxer.wins)+"% of your wins by KO";
            if(opponent!=null){
                notes+="\nContract opponent   "+line(opponent);
                if(opponent.wins>0)notes+="\n"+Math.round(100f*opponent.knockouts/opponent.wins)+"% of opponent wins by KO";
            }
            if(!purse.isEmpty())notes+="\n"+purse;
            if(!odds.isEmpty())notes+="\n"+odds;
            return notes;
        }
    }

    static final class Fighters {
        final String blue,red;
        final Record[] records;
        Fighters(String blue,String red,Record[] records){this.blue=blue;this.red=red;this.records=records;}
        static Fighters parse(String packet){
            String[] parts=packet.split(" ");
            if(parts.length!=2 && parts.length!=4)return null;
            String blue=name(parts[0]),red=name(parts[1]);
            Record[] records=new Record[2];
            if(parts.length==4){
                records[0]=Record.parse(parts[2]);records[1]=Record.parse(parts[3]);
            }
            return blue==null || red==null?null:new Fighters(blue,red,records);
        }
        static String name(String hex){
            if(hex.isEmpty() || hex.length()>126 || (hex.length()&1)!=0)return null;
            StringBuilder text=new StringBuilder(hex.length()/2);
            for(int i=0;i<hex.length();i+=2){
                int a=Character.digit(hex.charAt(i),16),b=Character.digit(hex.charAt(i+1),16);
                int ch=a*16+b;
                if(a<0 || b<0 || ch<32 || (ch>=127 && ch<160))return null;
                text.append((char)ch);
            }
            return text.toString();
        }
    }
    static final class Song {
        final String title,artist;
        final long started;
        private Song(String title,String artist,long started){this.title=title;this.artist=artist;this.started=started;}
        Song at(long now){return new Song(title,artist,now);}
        static Song parse(String payload){return parse(payload,0);}
        static Song parse(String payload,long now){
            int split=payload.indexOf(' ');
            if(split<1)return null;
            String title=text(payload.substring(0,split)),artist=text(payload.substring(split+1));
            return title==null || artist==null?null:new Song(title,artist,now);
        }
        private static String text(String hex){
            if(hex.isEmpty() || hex.length()>512 || hex.length()%4!=0)return null;
            StringBuilder out=new StringBuilder(hex.length()/4);
            for(int i=0;i<hex.length();i+=4){
                int value=0;
                for(int j=0;j<4;j++){
                    int digit=Character.digit(hex.charAt(i+j),16);
                    if(digit<0)return null;
                    value=value*16+digit;
                }
                if(value<32)return null;
                out.append((char)value);
            }
            return out.toString();
        }

    }
    static final String[] EXPANDED=ExpandedTracks.TITLES;
    static Song song(String stem){
        if(ExpandedTracks.index(stem)>=0)return new Song(stem,ExpandedTracks.ARTIST,0);
        switch(stem){
        case "SEANBIG1":return new Song("Never Gonna Get It","Sean Biggs feat. Akon & Topic",0);
        case "ATMOSPH1":return new Song("The Arrival","Atmosphere",0);
        case "BRASCO":return new Song("Uh-Oh","Brasco",0);
        case "CONSEQ1":return new Song("Night Night","Consequence",0);
        case "DILATEDP":return new Song("Back Again","Dilated Peoples",0);
        case "DIPSET":return new Song("The Best Out","Dipset",0);
        case "KRAYTWIN":return new Song("Round One","Kray Twinz feat. D&G",0);
        case "LTDA":return new Song("Knock Em Down","L.T.D. A.",0);
        case "OBIETRIC":return new Song("Wanna Know","Obie Trice",0);
        case "ROCC":return new Song("Don’t Stop","Roc C",0);
        case "SLIC":return new Song("Crook Dancin’","Slic One",0);
        case "YOUNGROS":return new Song("Itz Nothin’","Young Roscoe",0);
        default:return null;
        }
    }

    /* Read the solid interior of the retail HUD. The darker remainder uses the
     * same hue as the fill. Reject frames whose meter background does not match. */
    static int gauge(LiveFrame f,int side,boolean stamina){
        int lit=0,recognised=0;
        for(int i=0;i<132;i++){
            int x=side==0?58+i:581-i;
            int color=f.at(x,stamina?408:390);
            int r=LiveFrame.red(color),g=LiveFrame.green(color),b=LiveFrame.blue(color);
            boolean valid=stamina?r>30 && g>28 && Math.abs(r-g)<22 && g>b*2:
                side==0?b>35 && b>r*2 && g>r*2 && b>g:
                r>42 && r>g*2 && r>b*2;
            if(valid){recognised++;if(stamina?g>=96:side==0?b>=115:r>=110)lit++;}
        }
        return recognised>=120?(lit*100+66)/132:-1;
    }
    private FightBroadcast(){}
}
