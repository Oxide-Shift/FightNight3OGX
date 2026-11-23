package org.oxideshift.corner;

import java.util.Arrays;
import java.util.Locale;

/** Time between newly displayed game frames during one phase of a bout. */
final class BoutFrameTimes {
    private final long[] intervals=new long[180];
    private final int[] produced=new int[180];
    private long previous;
    private int previousSequence;
    private int count,cursor;
    private boolean active;
    synchronized void clear(){active=false;previous=0;count=cursor=0;}
    synchronized void active(boolean value){
        if(value==active)return;
        active=value;previous=0;
    }
    synchronized void frame(long time){frame(time,previousSequence+1);}
    synchronized void frame(long time,int sequence){
        if(!active)return;
        if(previous!=0 && time>previous){
            long advance=Integer.toUnsignedLong(sequence-previousSequence);
            if(advance==0 || advance>600){previous=time;previousSequence=sequence;return;}
            produced[cursor]=(int)advance;
            intervals[cursor]=time-previous;
            cursor=(cursor+1)%intervals.length;
            count=Math.min(count+1,intervals.length);
        }
        previous=time;previousSequence=sequence;
    }
    synchronized String reading(){
        if(count<12)return "";
        long[] data=Arrays.copyOf(intervals,count);
        Arrays.sort(data);long total=0,frames=0;
        for(int i=0;i<count;i++)frames+=produced[i];
        for(long interval:data)total+=interval;
        return String.format(Locale.ROOT,"%.1f FPS displayed  ·  %.1f FPS produced  ·  %.0f ms (95th percentile)",
                count*1e9/total,frames*1e9/total,data[(count*95-1)/100]/1e6);
    }
}
