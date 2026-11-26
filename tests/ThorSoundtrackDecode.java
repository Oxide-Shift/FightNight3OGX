package org.oxideshift.corner;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.res.AssetManager;
import android.os.Bundle;
import java.util.Arrays;
import java.util.HashSet;

/** Independent offline instrumentation. Read the installed game's assets without launching it. */
public final class ThorSoundtrackDecode extends Instrumentation {
    private String gamePackage;
    @Override public void onCreate(Bundle args){
        super.onCreate(args);
        gamePackage=args.getString("gamePackage","org.oxideshift.corner");
        start();
    }
    @Override public void onStart(){
        Bundle result=new Bundle();
        try{
            AssetManager assets=getContext().createPackageContext(gamePackage,0).getAssets();
            HashSet<String> expected=new HashSet<>();
            for(String title:ExpandedTracks.TITLES)expected.add(title+".mp3");
            if(!expected.equals(new HashSet<>(Arrays.asList(assets.list("expanded")))))
                throw new AssertionError("Installed roster differs from manifest");
            short[] pcm=new short[DiscTrax.MAX_FRAMES*2];
            for(String title:ExpandedTracks.TITLES){
                long total=0;
                int peak=0;
                try(ExpandedAudio track=new ExpandedAudio(assets,title)){
                    int frames;
                    while((frames=track.read(pcm))>0){
                        for(int i=0;i<frames*2;i++)peak=Math.max(peak,Math.abs((int)pcm[i]));
                        total+=frames;
                    }
                }
                if(total<DiscTrax.RATE*60L || peak==0)throw new AssertionError(title+" ended early or was silent");
                result.putString(title,total+" stereo frames, peak "+peak);
            }
            finish(Activity.RESULT_OK,result);
        }catch(Throwable failure){
            result.putString("failure",failure.toString());
            finish(Activity.RESULT_CANCELED,result);
        }
    }
}
