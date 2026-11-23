package org.oxideshift.corner;

/** Measures the position, length and darkness of transient scratches in the current Xbox scanout. */
final class MenuFilm {
    private static final int COLS=316,ROWS=28;
    private byte[] previous=new byte[ROWS*COLS],current=new byte[ROWS*COLS];
    private int sequence,width,height;
    private boolean ready,wide;
    final float[] lines=new float[COLS*4];
    final int[] opacity=new int[COLS];
    int used;

    boolean clear(){
        boolean changed=used!=0;used=0;ready=false;return changed;
    }
    boolean observe(LiveFrame f){
        if(ready && sequence==f.sequence)return false;
        boolean compare=ready && width==f.width && height==f.height && wide==f.wide;
        boolean had=used!=0;used=0;sequence=f.sequence;width=f.width;height=f.height;wide=f.wide;
        int stable=0;
        for(int row=0;row<ROWS;row++)for(int col=0;col<COLS;col++){
            int i=row*COLS+col,c=f.at(4+2*col,68+12*row);
            int light=(2*LiveFrame.red(c)+3*LiveFrame.green(c)+LiveFrame.blue(c))/6;
            current[i]=(byte)light;
            if(Math.abs(light-(previous[i]&255))<4)stable++;
        }
        if(compare && stable>COLS*ROWS*2/3){
            for(int x=2;x<COLS-2;x++){
                int hits=0,first=ROWS,last=0,contrast=0,background=0;
                for(int y=0;y<ROWS;y++){
                    int i=y*COLS+x;
                    int left=current[i-2]&255,right=current[i+2]&255,center=current[i]&255;
                    int side=Math.min(left,right),depth=Math.min(side,previous[i]&255)-center;
                    if(depth<4 || Math.abs(left-(previous[i-2]&255))>3 ||
                            Math.abs(right-(previous[i+2]&255))>3)continue;
                    first=Math.min(first,y);last=y;hits++;contrast+=depth;background+=side;
                }
                if(hits<6 || last-first<12)continue;
                // A scratch disappears in the black parts of the upper artwork. Join the
                // visible sections so its silhouette on the lower artwork remains continuous.
                float pos=(4+x*2)/640f;
                opacity[used/4]=Math.min(72,Math.max(5,255*contrast/Math.max(1,background)));
                lines[used++]=pos;lines[used++]=first/(float)ROWS;
                lines[used++]=pos;lines[used++]=(last+1)/(float)ROWS;
            }
        }
        byte[] swap=previous;previous=current;current=swap;ready=true;
        return had || used!=0;
    }
}
