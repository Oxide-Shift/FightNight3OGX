package org.oxideshift.corner;

/** Phase math for the two lower-screen cues (Notification-Motion study pp. 4 and 6), adapted from the study's
 *  reference sampler. Units are milliseconds and dp. sample() only computes a pose; it never schedules anything.
 *  TRAX: the badge catches at the left, the sleeve opens rightward, title then artist resolve, a still 6 s read,
 *  then the words fade, the sleeve folds back and the badge slides away (6.6 s in all).
 *  AWARD: the medal strikes at the right (90% -> 103% -> 100%), the receipt opens leftward, title and points then
 *  the deed, a little over 3 s of stillness, then the receipt retracts into the medal (3.58 s in all). */
final class CueMotion {
    enum Kind { TRAX, AWARD }
    static final class Pose {
        float field,emblem,name,detail,emblemX,emblemScale,wordsX;
        boolean visible;
    }
    /** End of the reading hold, after which only the exit runs. */
    static long holdEnd(Kind kind){return kind==Kind.TRAX?6250:3400;}
    static long end(Kind kind){return kind==Kind.TRAX?6600:3580;}
    /** Entry is complete (everything after this, until holdEnd, is still). */
    static long entryEnd(Kind kind){return kind==Kind.TRAX?250:270;}
    private static float ramp(long ms,int a,int b){return Math.max(0f,Math.min(1f,(ms-a)/(float)(b-a)));}
    private static float out(float t){return 1f-(1f-t)*(1f-t)*(1f-t);}
    private static float in(float t){return t*t;}

    static void sample(Kind kind,long ms,boolean reduced,Pose p){
        p.visible=ms>=0 && ms<end(kind);
        p.emblemX=p.wordsX=0f;
        p.emblemScale=1f;
        p.field=p.emblem=p.name=p.detail=p.visible?1f:0f;
        if(!p.visible || reduced)return;
        if(kind==Kind.TRAX){
            float lead=out(ramp(ms,0,100));
            float withdraw=in(ramp(ms,6420,6600));
            p.emblem=lead*(1f-withdraw);
            p.emblemX=-22f*(1f-lead)-16f*withdraw;
            p.field=out(ramp(ms,35,220))*(1f-in(ramp(ms,6250,6550)));
            p.name=out(ramp(ms,85,210))*(1f-ramp(ms,6250,6380));
            p.detail=out(ramp(ms,125,250))*(1f-ramp(ms,6250,6350));
            p.wordsX=-8f*(1f-out(ramp(ms,85,210)));
        }else{
            float strike=out(ramp(ms,0,100));
            float settle=out(ramp(ms,100,180));
            float lift=in(ramp(ms,3450,3580));
            p.emblemScale=.90f+.13f*strike-.03f*settle;
            p.emblem=strike*(1f-lift);
            p.emblemX=8f*(1f-strike)+8f*lift;
            p.field=out(ramp(ms,25,190))*(1f-in(ramp(ms,3400,3560)));
            p.name=out(ramp(ms,65,185))*(1f-ramp(ms,3400,3510));
            p.detail=out(ramp(ms,135,270))*(1f-ramp(ms,3400,3480));
            p.wordsX=6f*(1f-out(ramp(ms,65,185)));
        }
    }
    /** Milliseconds until the next frame must be drawn: every frame during entry and exit, one wake at the hold's end. */
    static long nextWake(Kind kind,long ms){
        if(ms<entryEnd(kind) || ms>=holdEnd(kind))return 0;
        return holdEnd(kind)-ms;
    }
    private CueMotion(){}
}
