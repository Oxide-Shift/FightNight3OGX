package org.oxideshift.corner;

import android.content.Context;
import android.view.Display;
import android.view.InputDevice;

/** Which of the six drawn devices (Thor, RP Duo, RP6, Odin 3 Portal, phone, tablet) is closest to the one the game
 *  runs on, for the corner-select icon and the loading controls guide. Cosmetic only: nothing about how the game runs
 *  depends on it. Capabilities decide first (a second built-in screen and its shape, a built-in gamepad, screen
 *  size); the maker's name only separates the single-screen handhelds, and a wrong guess just shows other art. */
final class DeviceMatch {
    private DeviceMatch(){}

    /** Pure decision, for tests. secondAspect is the second display's long/short side ratio (0 when there is none). */
    static ThorLoadingGuide.Device pick(int displays,float secondAspect,boolean builtInPad,int smallestWidthDp,String maker,String model){
        String name=((maker==null?"":maker)+" "+(model==null?"":model)).toLowerCase(java.util.Locale.ROOT);
        if(displays>=2 && builtInPad){
            if(name.contains("thor"))return ThorLoadingGuide.Device.THOR;
            if(name.contains("duo"))return ThorLoadingGuide.Device.DUO;
            // The Thor's lower screen is near-square (1240x1080); the RP Duo's is 4:3.
            return secondAspect>=1.25f?ThorLoadingGuide.Device.DUO:ThorLoadingGuide.Device.THOR;
        }
        if(builtInPad){
            if(name.contains("retroid"))return ThorLoadingGuide.Device.RP6;
            if(name.contains("ayn") || name.contains("odin"))return ThorLoadingGuide.Device.ODIN;
            return smallestWidthDp>=400?ThorLoadingGuide.Device.ODIN:ThorLoadingGuide.Device.RP6;
        }
        return smallestWidthDp>=600?ThorLoadingGuide.Device.TABLET:ThorLoadingGuide.Device.PHONE;
    }

    static ThorLoadingGuide.Device detect(Context c){
        int displays=1;float aspect=0f;
        try{
            android.hardware.display.DisplayManager manager=(android.hardware.display.DisplayManager)c.getSystemService(Context.DISPLAY_SERVICE);
            Display[] all=manager==null?new Display[0]:manager.getDisplays();
            displays=Math.max(1,all.length);
            for(Display d:all){
                if(d.getDisplayId()==Display.DEFAULT_DISPLAY)continue;
                Display.Mode mode=d.getMode();
                int a=Math.max(mode.getPhysicalWidth(),mode.getPhysicalHeight()),b=Math.min(mode.getPhysicalWidth(),mode.getPhysicalHeight());
                if(b>0){aspect=a/(float)b;break;}
            }
        }catch(RuntimeException ignored){}
        boolean internal=false,any=false;
        for(int id:InputDevice.getDeviceIds()){
            InputDevice d=InputDevice.getDevice(id);
            if(d==null)continue;
            if((d.getSources()&InputDevice.SOURCE_GAMEPAD)!=InputDevice.SOURCE_GAMEPAD)continue;
            any=true;
            if(android.os.Build.VERSION.SDK_INT<29 || !d.isExternal())internal=true;
        }
        int sw=c.getResources().getConfiguration().smallestScreenWidthDp;
        return pick(displays,aspect,builtInPad(internal,any,android.os.Build.MANUFACTURER),sw,android.os.Build.MANUFACTURER,android.os.Build.MODEL);
    }
    /** Handheld makers wire their controls over internal USB, which Android reports as external (the Thor's
     *  "Odin Controller" does), so on their devices any gamepad counts as built in. Elsewhere only internal ones do. */
    static boolean builtInPad(boolean internalGamepad,boolean anyGamepad,String maker){
        if(internalGamepad)return true;
        String m=maker==null?"":maker.toLowerCase(java.util.Locale.ROOT);
        return anyGamepad && (m.contains("ayn") || m.contains("retroid") || m.contains("ayaneo") || m.contains("anbernic") || m.contains("odin"));
    }
}
