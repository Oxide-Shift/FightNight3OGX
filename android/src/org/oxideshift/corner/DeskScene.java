package org.oxideshift.corner;

/** A brief fade or a single missing footer must not replace the touch layout. */
final class DeskScene {
    LiveFrame.Scene shown;
    int prompts;
    private LiveFrame.Scene candidate;
    private int candidateMask;
    private long since;

    boolean observe(LiveFrame.Scene scene,int mask,long now){
        if(scene!=LiveFrame.Scene.MENU)mask=0;
        if(scene!=candidate || mask!=candidateMask){
            candidate=scene;candidateMask=mask;since=now;
        }
        if(shown==scene && prompts==mask)return false;
        long settle=140;
        if(shown==null)settle=0;
        else if(scene==LiveFrame.Scene.RINGSIDE)settle=shown==LiveFrame.Scene.ROUND?1500:700;
        else if(scene==shown && mask==0)settle=440;
        if(now-since<settle)return false;
        shown=scene;prompts=mask;return true;
    }
    LiveFrame.Scene layout(LiveFrame.Scene observed,boolean unscoredRound){
        if(observed==LiveFrame.Scene.CORNER)return LiveFrame.Scene.RINGSIDE;
        if(observed==LiveFrame.Scene.RINGSIDE && shown==LiveFrame.Scene.ROUND && unscoredRound)
            return LiveFrame.Scene.ROUND;
        return observed;
    }
    boolean settling(){return candidate!=shown || candidateMask!=prompts;}
    void reset(){shown=candidate=null;prompts=candidateMask=0;since=0;}
}
