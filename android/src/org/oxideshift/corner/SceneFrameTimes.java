package org.oxideshift.corner;

/** Keeps ring, round-break and post-fight uploads on separate clocks. */
final class SceneFrameTimes {
    final BoutFrameTimes ring=new BoutFrameTimes(), corner=new BoutFrameTimes(), finish=new BoutFrameTimes();
    private BoutFrameTimes current;
    private int boutNumber, cornerRound;
    private long finishStart;
    private boolean finishCaptured;

    synchronized void observe(LiveFrame.Scene scene,int bout,int round,boolean scored,boolean result){
        if(bout!=boutNumber){
            suspend();ring.clear();corner.clear();finish.clear();
            boutNumber=bout;cornerRound=0;finishStart=0;finishCaptured=false;
        }
        BoutFrameTimes next=null;
        switch(scene){
        case RINGSIDE: case CORNER: case TREATMENT: case ROUND: case FINAL:
            if(result){if(!finishCaptured)next=finish;}
            else if(scene==LiveFrame.Scene.ROUND)next=ring;
            else if(round>0 && (scored || scene==LiveFrame.Scene.CORNER || scene==LiveFrame.Scene.TREATMENT))next=corner;
            break;
        default: break;
        }
        if(next==corner && round!=cornerRound){corner.clear();cornerRound=round;if(current==corner)current=null;}
        if(next==current)return;
        suspend();current=next;
        if(current!=null)current.active(true);
    }
    synchronized void suspend(){
        if(current!=null)current.active(false);
        current=null;
    }
    synchronized void frame(long time,int sequence){
        if(current==null)return;
        if(current==finish){
            if(finishStart==0)finishStart=time;
            if(time-finishStart>=8_000_000_000L){finishCaptured=true;suspend();return;}
        }
        current.frame(time,sequence);
    }
}
