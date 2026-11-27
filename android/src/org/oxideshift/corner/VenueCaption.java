package org.oxideshift.corner;

/** Names the venue highlighted on Play Now's venue select from the top screen's own caption. The game keeps the
 *  choice inside its menu script, out of reach of the runtime, but the caption it prints (white capitals on the
 *  orange name plate) has a different shape for every venue: the number of letters in each word. The reader counts
 *  letter columns and word gaps in that band and only answers on an exact match. */
final class VenueCaption {
    /** The venues as the poster names them, with the letter count of each word of the game's caption (a registered
     *  mark after a name may add one more mark to the last word). */
    static final String[] NAMES={"The Warehouse","Madison Square Garden","Windy City Boxing Gym","State Palace Theatre",
            "Staples Center","Aragon Ballroom","Everlast Boxing Center"};
    static final int[][] LETTERS={{3,9},{7,6,6},{5,4,6,3},{5,6,7},{7,6},{6,8},{8,6,6}};
    /** Caption band in the game's 640x480 interface coordinates (the caption starts after the arrows), measured on the
     *  Thor. A word space is at least 7 interface units; letter gaps stay under 6.3. */
    static final int LEFT=200,RIGHT=533,TOP=358,BOTTOM=380;
    /** Word space and narrowest letter in interface units; thinner marks are the film-scratch overlay. */
    static final float SPACE=7f,LETTER=1.8f,GAP=1.2f;
    private VenueCaption(){}

    /** The venue for a letters-per-word pattern, or null. Pure, for tests. */
    static String match(int[] words){
        if(words==null || words.length==0)return null;
        for(int v=0;v<NAMES.length;v++){
            int[] want=LETTERS[v];
            // A trademark sign (MADISON SQUARE GARDEN™, STAPLES CENTER™; seen on the Thor) reads as one or two more
            // marks on the last word (the superscript T and M), or, when it sits a little apart, as a word of its own.
            boolean apart=words.length==want.length+1 && words[words.length-1]<=2;
            if(want.length!=words.length && !apart)continue;
            boolean same=true;
            for(int i=0;i<want.length && same;i++){
                boolean last=i==want.length-1;
                int extra=words[i]-want[i];
                same=extra==0 || (last && !apart && (extra==1 || extra==2));
            }
            if(same)return NAMES[v];
        }
        return null;
    }
    /** Letters per word from text columns (true where the caption has white ink), splitting words at gaps of at least
     *  `space` columns. Pure, for tests. */
    static int[] words(boolean[] ink,int space){return words(ink,space,1,1);}
    /** As above, ignoring ink runs narrower than `minLetter` columns (they count as background) and bridging gaps
     *  narrower than `minGap` (a diagonal letter such as W or N can thin out mid-letter). */
    static int[] words(boolean[] ink,int space,int minLetter,int minGap){
        // Drop thin runs first so a scratch neither adds a letter nor splits a gap.
        boolean[] clean=ink.clone();
        for(int i=0;i<clean.length;){
            if(!clean[i]){i++;continue;}
            int j=i;while(j<clean.length && clean[j])j++;
            if(j-i<minLetter)for(int k=i;k<j;k++)clean[k]=false;
            i=j;
        }
        for(int i=0;i<clean.length;){
            if(clean[i]){i++;continue;}
            int j=i;while(j<clean.length && !clean[j])j++;
            if(i>0 && j<clean.length && j-i<minGap)for(int k=i;k<j;k++)clean[k]=true;
            i=j;
        }
        java.util.List<Integer> out=new java.util.ArrayList<>();
        int letters=0,gap=0;boolean inLetter=false,started=false;
        for(boolean on:clean){
            if(on){
                if(!inLetter){
                    if(started && gap>=space){out.add(letters);letters=0;}
                    letters++;inLetter=true;started=true;
                }
                gap=0;
            }else{inLetter=false;gap++;}
        }
        if(started)out.add(letters);
        int[] r=new int[out.size()];for(int i=0;i<r.length;i++)r[i]=out.get(i);
        return r;
    }
    static boolean white(int c){return LiveFrame.red(c)>190 && LiveFrame.green(c)>190 && LiveFrame.blue(c)>170;}
    /** The venue on screen, or null when the caption cannot be read as one of the seven. */
    static String read(LiveFrame f){
        if(f==null || f.width<320 || f.height<240)return null;
        // Scan real pixel columns: interface units are too coarse to keep letters apart at 1080p.
        int x0=f.sourceX(LEFT)*f.width/640,x1=Math.min(f.width,f.sourceX(RIGHT)*f.width/640);
        int y0=TOP*f.height/480,y1=Math.min(f.height,BOTTOM*f.height/480);
        float perUnit=(f.sourceX(RIGHT)-f.sourceX(LEFT))*f.width/640f/(RIGHT-LEFT);
        if(x1-x0<16 || y1<=y0 || f.pixels.capacity()<8+4L*f.width*f.height)return null;
        boolean[] ink=new boolean[x1-x0];
        for(int x=x0;x<x1;x++){
            int n=0;
            for(int y=y0;y<y1 && n<2;y++)if(white(f.pixels.getInt(8+4*(y*f.width+x))))n++;
            ink[x-x0]=n>=2;
        }
        return match(words(ink,Math.max(3,Math.round(SPACE*perUnit)),Math.max(2,Math.round(LETTER*perUnit)),Math.max(2,Math.round(GAP*perUnit))));
    }
}
