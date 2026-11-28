package org.oxideshift.corner;

import android.content.SharedPreferences;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageManager;

/** Android reports the GPU driver's Vulkan level as a system feature before the game starts.
 *  Crash reports to Alpha 83: every "no usable Vulkan" exit came from a Vulkan 1.1 driver. From Alpha 84 the
 *  renderer runs on Vulkan 1.1 and 1.2 drivers through its compatibility path (native/fight_vk_compat.h,
 *  experimental: verified on a PC under emulation only). Drivers below 1.1 start on Software. */
final class VulkanSupport {
    static final int MINIMUM=(1<<22)|(1<<12);    // VK_MAKE_API_VERSION(0,1,1,0): compatibility path
    static final int FULL=(1<<22)|(3<<12);       // VK_MAKE_API_VERSION(0,1,3,0): the renderer's native level
    static final int RENDERER_VULKAN=0, RENDERER_SOFTWARE=1;
    private VulkanSupport(){}

    /** The highest Vulkan version Android reports for this device's driver, or 0 when it reports none. */
    static int systemVersion(PackageManager pm){
        int best=0;
        FeatureInfo[] features=pm==null?null:pm.getSystemAvailableFeatures();
        if(features!=null)for(FeatureInfo f:features)
            if(PackageManager.FEATURE_VULKAN_HARDWARE_VERSION.equals(f.name))best=Math.max(best,f.version);
        return best;
    }
    /** True only when Android names a version and it is below 1.1. No report at all is left to the renderer's probe. */
    static boolean tooOld(int version){return version>0 && Integer.compareUnsigned(version,MINIMUM)<0;}
    /** Vulkan 1.1 or 1.2: the renderer uses its compatibility path. */
    static boolean compatibility(int version){
        return version>0 && Integer.compareUnsigned(version,MINIMUM)>=0 && Integer.compareUnsigned(version,FULL)<0;
    }
    static String label(int version){return (version>>>22)+"."+((version>>>12)&0x3ff);}

    /** Once per install (Alpha 84): a driver below 1.1 starts on Software; a 1.1/1.2 driver that was left on
     *  Software (after the old "Vulkan unavailable" message) gets one try of the compatibility path, and the
     *  launcher switches back to Software by itself if that can't start. The player's later choices stand.
     *  Returns true when it changed the renderer. */
    static boolean chooseDefault(SharedPreferences prefs,int version){
        if(prefs.getBoolean("rendererLevelChecked",false))return false;
        SharedPreferences.Editor edit=prefs.edit().putBoolean("rendererLevelChecked",true);
        int current=prefs.getInt("renderer",RENDERER_VULKAN);
        int choice=tooOld(version)?RENDERER_SOFTWARE:compatibility(version) && current==RENDERER_SOFTWARE?RENDERER_VULKAN:current;
        boolean switched=choice!=current;
        if(switched)edit.putInt("renderer",choice);
        edit.apply();
        return switched;
    }
    /** The line under Graphics quality for this device, or null when there is nothing to say. */
    static String notice(int version,boolean software){
        if(tooOld(version))return "This device's graphics driver supports Vulkan "+label(version)+"; the Vulkan renderer needs 1.1 or newer, so the game uses Software (480p).";
        if(compatibility(version) && !software)return "Vulkan "+label(version)+" driver · compatibility path (experimental)";
        return null;
    }
}
