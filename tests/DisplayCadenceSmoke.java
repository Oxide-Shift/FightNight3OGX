package org.oxideshift.corner;

public final class DisplayCadenceSmoke {
    public static void main(String[] args){
        if(DisplayCadence.target(DisplayCadence.legacyIndex(0))!=60 ||
           DisplayCadence.target(DisplayCadence.legacyIndex(1))!=45 ||
           DisplayCadence.target(0)!=30 || DisplayCadence.target(99)!=60)
            throw new AssertionError("Saved frame target");
        for(int fps:new int[]{30,45,60})for(int hz:new int[]{60,80,90,120,144,180}){
            DisplayCadence cadence=new DisplayCadence(fps);
            int count=0,last=-1;
            for(int i=0;i<hz*10;i++){
                if(!cadence.due(1_000_000_000L+(long)i*1_000_000_000L/hz))continue;
                if(last>=0 && (i-last<hz/fps || i-last>(hz+fps-1)/fps))throw new AssertionError("Uneven cadence");
                last=i;count++;
            }
            if(count!=fps*10 || DisplayCadence.compatible(hz,fps)!=(hz%fps==0))
                throw new AssertionError("Rate at "+hz+" Hz, target "+fps+", frames "+count);
            if(!cadence.due(20_000_000_000L) || cadence.due(20_005_000_000L))throw new AssertionError("Late pulse");
            cadence.reset();
            if(!cadence.due(20_006_000_000L))throw new AssertionError("Resume");
        }
        DisplayCadence idle=new DisplayCadence(60);
        long tick=1_000_000_000L;
        if(!idle.due(tick,true) || idle.due(tick+16_666_667L,false) ||
                !idle.due(tick+25_000_000L,true) || idle.due(tick+26_000_000L,true))
            throw new AssertionError("An empty refresh delayed a ready frame");
        int[] tiers={0,1,2,2,1}, samples={1,1,2,1,2}, filtering={1,2,4,4,2};
        for(int i=0;i<tiers.length;i++){
            GraphicsPreset preset=GraphicsPreset.stored(i);
            if(preset.tier!=tiers[i] || preset.samples!=samples[i] || preset.anisotropy!=filtering[i] ||
                    GraphicsPreset.select(preset.tier,preset.samples==2)!=preset)
                throw new AssertionError("Saved graphics preset "+i);
        }
        if(GraphicsPreset.stored(-1)!=GraphicsPreset.MEDIUM || GraphicsPreset.select(0,true)!=GraphicsPreset.LOW)
            throw new AssertionError("Unsupported quality value");
        SceneFrameTimes timing=new SceneFrameTimes();
        long t=1_000_000_000L;
        int seq=0;
        timing.observe(LiveFrame.Scene.ROUND,1,1,false,false);
        for(int i=0;i<32;i++)timing.frame(t+=16_666_667L,++seq);
        timing.observe(LiveFrame.Scene.RINGSIDE,1,1,true,false);
        for(int i=0;i<32;i++)timing.frame(t+=33_333_333L,seq+=2);
        timing.observe(LiveFrame.Scene.PAUSE,1,1,true,false);
        timing.frame(t+=3_000_000_000L,++seq);
        if(!timing.ring.reading().contains("60.0 FPS displayed") ||
           !timing.corner.reading().contains("30.0 FPS displayed") ||
           !timing.corner.reading().contains("60.0 FPS produced"))throw new AssertionError("Separated frame timing");
        timing.observe(LiveFrame.Scene.RINGSIDE,1,1,true,true);
        for(int i=0;i<330;i++)timing.frame(t+=25_000_000L,++seq);
        String finish=timing.finish.reading();
        for(int i=0;i<400;i++){timing.observe(LiveFrame.Scene.RINGSIDE,1,1,true,true);timing.frame(t+=50_000_000L,++seq);}
        if(!finish.contains("40.0 FPS displayed") || !timing.finish.reading().equals(finish))
            throw new AssertionError("Post-fight sample must survive the results screen");
        timing.observe(LiveFrame.Scene.ROUND,2,1,false,false);
        if(!timing.finish.reading().isEmpty() || !timing.corner.reading().isEmpty() || !timing.ring.reading().isEmpty())
            throw new AssertionError("Previous bout timings retained");
        if(!DisplayCadence.compatible(89.91f,45) || DisplayCadence.compatible(119.88f,45))throw new AssertionError("Fractional refresh");
    }
}
