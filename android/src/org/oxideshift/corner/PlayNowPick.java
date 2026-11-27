package org.oxideshift.corner;

/** The two boxers highlighted on the Play Now select screens, from the runtime's SELECTION report: each side's roster
 *  row ID, name and every 16-bit cell of its roster row, plus the selection manager's neighbouring words. Pure data so
 *  it can be tested off device. */
final class PlayNowPick {
    /** Retail roster columns (signed 16-bit): wins, losses, draws and knockout wins. */
    static final int WINS=31,LOSSES=32,DRAWS=33,KOS=34;
    final int[] id={-1,-1};
    final String[] name={null,null};
    final int[][] cells={null,null};
    final long[] manager;

    private PlayNowPick(long[] manager){this.manager=manager;}

    /** Parse the payload after "FNR3/1 SELECTION ". Returns null when it is malformed. */
    static PlayNowPick parse(String payload){
        String[] p=payload.trim().split(" ");
        if(p.length!=7 || !p[6].startsWith("m"))return null;
        String[] words=p[6].length()>2?p[6].substring(2).split(","):new String[0];
        long[] manager=new long[words.length];
        try{
            for(int i=0;i<words.length;i++)manager[i]=Long.parseLong(words[i],16);
            PlayNowPick pick=new PlayNowPick(manager);
            for(int side=0;side<2;side++){
                String idText=p[side*3],hex=p[side*3+1],values=p[side*3+2];
                if(idText.equals("-"))continue;
                pick.id[side]=Integer.parseInt(idText);
                pick.name[side]=FightBroadcast.Fighters.name(hex);
                String[] v=values.split(",");
                int[] row=new int[v.length];
                for(int i=0;i<v.length;i++)row[i]=Integer.parseInt(v[i]);
                pick.cells[side]=row;
            }
            return pick;
        }catch(NumberFormatException e){return null;}
    }
    boolean has(int side){return cells[side]!=null && name[side]!=null;}
    /** A roster cell, or -1 when the row is short or missing. */
    int cell(int side,int column){int[] row=cells[side];return row==null || column<0 || column>=row.length?-1:row[column];}
    int wins(int side){return cell(side,WINS);}
    int losses(int side){return cell(side,LOSSES);}
    int draws(int side){return cell(side,DRAWS);}
    int knockouts(int side){return cell(side,KOS);}
    /** Knockout wins as a share of wins, or -1. */
    int knockoutRate(int side){int w=wins(side),k=knockouts(side);return w<=0 || k<0?-1:Math.round(100f*k/w);}
    /** Manager words 11 and 12 (0x59dcbc, 0x59dcc0) hold the pad in the blue and red slots on corner select, or
     *  0xffffffff for an empty slot (Thor, kit-82l). */
    static final int BLUE_SLOT=11,RED_SLOT=12;
    static final long EMPTY=0xffffffffL;
    /** The pad in a corner (0 blue, 1 red), or -1 when that corner is the computer's. */
    int padIn(int side){int at=side==0?BLUE_SLOT:RED_SLOT;return manager.length>at && manager[at]!=EMPTY && manager[at]<4?(int)manager[at]:-1;}
    boolean sameBoxers(PlayNowPick other){return other!=null && other.id[0]==id[0] && other.id[1]==id[1];}
}
