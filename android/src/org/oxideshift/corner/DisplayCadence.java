package org.oxideshift.corner;

/** Presentation deadlines on the display's vsync clock. Guest timing stays at NTSC. */
final class DisplayCadence {
    static final int[] TARGETS={30,45,60};
    private final long period;
    private long next;

    DisplayCadence(int fps){period=Math.round(1_000_000_000.0/fps);}

    static int legacyIndex(int pacing){return pacing==1?1:2;}
    static int target(int index){return index>=0 && index<TARGETS.length?TARGETS[index]:60;}

    // An empty vsync must not reserve the next slot ahead of an arriving frame.
    boolean due(long frameTime,boolean pending){return pending && due(frameTime);}
    boolean due(long frameTime){
        if(next!=0 && frameTime+1_000_000L<next)return false;
        // Keep phase when the display rate is not a multiple of the requested FPS.
        // After a stall, skip past missed deadlines without a burst of renders.
        if(next==0 || frameTime-next>=period)next=frameTime+period;
        else next+=period;
        return true;
    }
    void reset(){next=0;}
    static boolean compatible(float hz,int fps){
        int pulses=Math.round(hz/fps);
        return pulses>=1 && Math.abs(hz-fps*pulses)<.6f;
    }
}
