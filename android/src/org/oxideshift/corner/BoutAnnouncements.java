package org.oxideshift.corner;

/** Orders the original fight events on the lower display. Time is monotonic uptime. */
final class BoutAnnouncements {
    static final long DURATION=1600;
    static final class Notice {
        final String words;
        final long at;
        final int priority;
        boolean isCount(){return priority==2;}
        Notice(String words,long at,int priority){this.words=words;this.at=at;this.priority=priority;}
    }
    private Notice current, result;
    private int lastCount;
    private boolean finished;
    synchronized void reset(){current=result=null;lastCount=0;finished=false;}
    synchronized Notice current(long now){
        if(result!=null && now>=result.at)return result;
        return current;
    }
    synchronized boolean ownsDisplay(long now){
        Notice active=current(now);
        return lastCount!=0 || (active!=null && now>=active.at && now-active.at<DURATION);
    }
    synchronized boolean count(int n,long now){
        if(n==0){
            lastCount=0;
            if(current!=null && current.priority==2)current=null;
            return false;
        }
        if(finished || n<1 || n>10 || n==lastCount)return false;
        lastCount=n;
        current=new Notice(Integer.toString(n),now,2);
        return true;
    }
    synchronized boolean event(String token,long now){
        String words;
        int priority;
        switch(token){
            case "HAYMAKER":words="HAYMAKER!!";priority=0;break;
            case "STAGGER":words="STAGGER!";priority=1;break;
            case "ROCKED":words="ROCKED!!";priority=1;break;
            case "DOWN":words="KNOCK DOWN!!";priority=3;break;
            case "GETUP":words="GET UP!!";priority=3;break;
            case "KO":words="KNOCKOUT!!";priority=4;break;
            case "TKO":words="TKO!!";priority=4;break;
            case "WON":case "LOST":
                if(result!=null)return false;
                finished=true;lastCount=0;
                result=new Notice(token+"!!",current!=null && current.priority==4?
                        Math.max(now,current.at+DURATION):now,5);
                return true;
            default:return false;
        }
        if(finished && priority!=4)return false;
        if(lastCount!=0 && priority<2)return false;
        if(current!=null && now-current.at<DURATION && current.priority>priority)return false;
        if(current!=null && "ROCKED!!".equals(current.words) && now-current.at<DURATION && "STAGGER".equals(token))return false;
        if(priority==4){
            if(current!=null && current.priority==4)return false;
            finished=true;
            if(result!=null)result=new Notice(result.words,now+DURATION,5);
        }
        if(priority>=3)lastCount=0;
        current=new Notice(words,now,priority);
        return true;
    }
}
