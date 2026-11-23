package org.oxideshift.corner;

import java.io.File;

final class CalloutRumbleSmoke {
    private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    public static void main(String[] args)throws Exception{
        long[] now={10000};
        CornerSession s=new CornerSession(()->now[0],new File("."));
        int[] pulse={0},count={0},motors={0};
        s.motors(new CornerSession.Motors(){
            public void changed(int left,int right,int ms){motors[0]++;}
            public void count(int n){count[0]++;}
            public void callout(){pulse[0]++;}
        });
        s.lowerCallouts=true;
        for(String word:new String[]{"HAYMAKER","STAGGER","DOWN","GETUP","KO","TKO","WON","LOST"}){
            s.line("FNR3/1 BOUTBEGIN");int before=pulse[0];s.line("FNR3/1 CALLOUT "+word);
            check(pulse[0]==before,"event arrived before visible popup");
            s.calloutShown(s.callout());s.calloutShown(s.callout());
            check(pulse[0]==before+1,word+" should pulse once");
        }
        check(motors[0]==0,"old single buzz must not accompany a displayed callout");
        s.line("FNR3/1 BOUTBEGIN");s.line("FNR3/1 COUNT 3");s.calloutShown(s.callout());
        check(pulse[0]==8 && count[0]==1,"referee count beat");
        s.line("FNR3/1 CALLOUT KO");s.line("FNR3/1 CALLOUT WON");s.calloutShown(s.callout());
        now[0]+=1599;s.calloutShown(s.callout());check(pulse[0]==9,"KO first");
        now[0]++;s.calloutShown(s.callout());check(pulse[0]==10,"queued result");
        s.line("FNR3/1 BOUTBEGIN");s.line("FNR3/1 CALLOUT STAGGER");now[0]+=2000;s.calloutShown(s.callout());
        check(pulse[0]==10,"expired popup");
        s.lowerCallouts=false;s.line("FNR3/1 CALLOUT DOWN");check(motors[0]==1,"single-display fallback");
        System.out.println("All eight callouts buzz once on presentation, including queued results. Referee beats preserved.");
    }
}
