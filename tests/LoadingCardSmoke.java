package org.oxideshift.corner;

/** Loading card timing: bounded swing, a kick at each shot, tips that cycle and fade at the edges only. */
public final class LoadingCardSmoke {
    static void check(boolean pass,String message){if(!pass)throw new AssertionError(message);}
    public static void main(String[] args){
        float most=0;
        for(long t=0;t<60000;t+=7){
            float a=LoadingCard.swing(t);
            check(!Float.isNaN(a) && Math.abs(a)<20f,"swing out of range at "+t+": "+a);
            most=Math.max(most,Math.abs(a));
        }
        check(most>8f,"no visible kick from the shots");
        // Just before a shot the bag only sways; just after, it swings past the sway.
        check(Math.abs(LoadingCard.swing(LoadingCard.HIT_MS-1))<=4.01f,"kick before the shot");
        float after=0;for(long t=1;t<400;t++)after=Math.max(after,Math.abs(LoadingCard.swing(LoadingCard.HIT_MS+t)));
        check(after>6f,"shot did not move the bag");
        // Tips: every one shown in order, wrapping, with full opacity mid-tip and fades only at the edges.
        int n=LoadingCard.TIPS.length;
        for(int first=0;first<n;first++)for(int k=0;k<n*2;k++)
            check(LoadingCard.tip(k*LoadingCard.TIP_MS+10,first)==(first+k)%n,"tip order");
        check(LoadingCard.tipAlpha(0)==0f && LoadingCard.tipAlpha(LoadingCard.TIP_MS/2)==1f,"tip fade");
        for(long t=0;t<LoadingCard.TIP_MS*3;t+=13){float a=LoadingCard.tipAlpha(t);check(a>=0f && a<=1f,"alpha range");}
        for(String tip:LoadingCard.TIPS)check(tip.length()<=80 && !tip.isEmpty(),"tip too long for two lines: "+tip);
        System.out.println("Loading card swing, shots and "+n+" tips passed");
    }
}
