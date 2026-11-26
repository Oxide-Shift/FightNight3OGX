package org.oxideshift.corner;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.PowerManager;
import android.view.Display;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

/** Bounded diagnostic sections shared by the consent preview and both inbox routes. */
final class ReportDetails {
    // Independent reference built from all 901 files on the tested US disc.
    static final String USA_FILES="7c8ad7ead9c2dfd88d8d69fd3e1c066c2b80b53032294b4e64b6416208bbcefd";
    private static void label(JSONObject d,String key,String value)throws JSONException{
        if(value!=null){
            String s=value.replaceAll("[^\\x20-\\x7e]"," ").trim();
            if(!s.isEmpty())d.put(key,s.substring(0,Math.min(64,s.length())));
        }
    }
    static JSONObject capture(Activity activity,DiscFacts facts,Map<String,Object> failure) throws JSONException{
        JSONObject d=new JSONObject(),rom=new JSONObject();
        label(d,"manufacturer",Build.MANUFACTURER);label(d,"model",Build.MODEL);
        label(d,"android_release",Build.VERSION.RELEASE);label(d,"hardware",Build.HARDWARE);
        if(Build.SUPPORTED_ABIS.length>0)label(d,"preferred_abi",Build.SUPPORTED_ABIS[0]);
        if(Build.VERSION.SDK_INT>=31){label(d,"soc_vendor",Build.SOC_MANUFACTURER);label(d,"soc_model",Build.SOC_MODEL);}
        d.put("cpu_cores",Runtime.getRuntime().availableProcessors());
        long pageBytes=android.system.Os.sysconf(android.system.OsConstants._SC_PAGESIZE);
        if(pageBytes>0)d.put("host_page_bytes",pageBytes);
        try{
            ActivityManager am=activity.getSystemService(ActivityManager.class);
            if(am!=null){
                ActivityManager.MemoryInfo mem=new ActivityManager.MemoryInfo();am.getMemoryInfo(mem);
                d.put("total_ram_mib",mem.totalMem>>20);d.put("available_ram_mib",mem.availMem>>20);d.put("low_memory",mem.lowMemory);
            }
            d.put("free_storage_mib",activity.getFilesDir().getUsableSpace()>>20);
        }catch(RuntimeException ignored){}
        try{
            DisplayManager dm=activity.getSystemService(DisplayManager.class);
            if(dm!=null)d.put("presentation_displays",dm.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION).length);
            Display display=dm==null?null:dm.getDisplay(Display.DEFAULT_DISPLAY);
            if(display!=null){
                Display.Mode mode=display.getMode();d.put("display_width",mode.getPhysicalWidth());
                d.put("display_height",mode.getPhysicalHeight());d.put("display_millihz",Math.round(mode.getRefreshRate()*1000));
            }
            PowerManager pm=activity.getSystemService(PowerManager.class);
            if(pm!=null){d.put("thermal_status",pm.getCurrentThermalStatus());d.put("power_save",pm.isPowerSaveMode());}
        }catch(RuntimeException ignored){}
        try{
            for(FeatureInfo f:activity.getPackageManager().getSystemAvailableFeatures())
                if(PackageManager.FEATURE_VULKAN_HARDWARE_VERSION.equals(f.name))d.put("vulkan_api_version",f.version);
        }catch(RuntimeException ignored){}
        // Schema v4 device identity (stripped for older inboxes). Controllers are vendor:product IDs only, never names.
        try{
            d.put("app_version_code",activity.getPackageManager().getPackageInfo(activity.getPackageName(),0).versionCode);
        }catch(PackageManager.NameNotFoundException | RuntimeException ignored){}
        label(d,"firmware_build",Build.DISPLAY);label(d,"security_patch",Build.VERSION.SECURITY_PATCH);
        try{
            DisplayManager dm=activity.getSystemService(DisplayManager.class);
            if(dm!=null){
                d.put("display_count",dm.getDisplays().length);
                Display[] extra=dm.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION);
                if(extra.length>0){
                    Display.Mode m=extra[0].getMode();
                    d.put("lower_display",m.getPhysicalWidth()+"x"+m.getPhysicalHeight()+"@"+Math.round(m.getRefreshRate()*1000));
                }
            }
        }catch(RuntimeException ignored){}
        StringBuilder pads=new StringBuilder();int count=0;
        for(int id:android.view.InputDevice.getDeviceIds()){
            android.view.InputDevice in=android.view.InputDevice.getDevice(id);
            if(in==null || in.isVirtual())continue;
            int s=in.getSources();
            if((s&android.view.InputDevice.SOURCE_GAMEPAD)!=android.view.InputDevice.SOURCE_GAMEPAD &&
                    (s&android.view.InputDevice.SOURCE_JOYSTICK)!=android.view.InputDevice.SOURCE_JOYSTICK)continue;
            if(count++==6)break;
            if(pads.length()>0)pads.append(',');
            pads.append(String.format(Locale.ROOT,"%04x:%04x",in.getVendorId()&0xffff,in.getProductId()&0xffff));
        }
        d.put("controllers",pads.length()==0?"none":pads.toString());
        JSONObject f=new JSONObject(failure);
        try(BufferedReader in=new BufferedReader(new InputStreamReader(activity.getAssets().open("disc-kit.id"),StandardCharsets.US_ASCII))){
            String kit=in.readLine();if(kit!=null && kit.matches("[0-9a-f]{64}"))f.put("build_kit_sha256",kit);
        }catch(IOException | RuntimeException ignored){}
        for(String key:DiscFacts.KEYS){
            String value=facts.values.getProperty(key);
            if(value==null)continue;
            if(key.endsWith("_bytes") || key.equals("partition_offset") || key.equals("files") || key.equals("revision") || key.equals("disc_number")){
                try{rom.put(key,Long.parseLong(value));}catch(NumberFormatException ignored){}
            }else rom.put(key,value);
        }
        return new JSONObject().put("device",d).put("rom",rom).put("failure",f);
    }
    static String preview(JSONObject details)throws JSONException{
        StringBuilder text=new StringBuilder();
        for(String group:new String[]{"failure","rom","device"}){
            JSONObject item=details.getJSONObject(group);
            if(item.length()==0)continue;
            text.append("\n\n").append(group.equals("device")?"Device at report time":group.equals("rom")?"Game disc details":"Failure details");
            if(item.has("disc_files_sha256"))text.append(USA_FILES.equals(item.getString("disc_files_sha256"))?
                    "\nGame files match the tested USA disc.":"installed_only".equals(item.optString("origin"))?
                    "\nInstalled files differ from the original USA disc. In-game menu and control artwork can change this fingerprint.":
                    "\nImported files differ from the tested USA disc.");
            if("installed_only".equals(item.optString("origin")))text.append(item.has("disc_files_sha256")?
                    "\nFingerprint measured from the installed game folder.":"\nExisting installation. The installed executable was checked.");
            List<String> keys=new ArrayList<>();for(Iterator<String> i=item.keys();i.hasNext();)keys.add(i.next());
            Collections.sort(keys);
            for(String key:keys){
                if(key.equals("log_tail"))continue;   // shown as its own section below
                text.append('\n').append(key.replace('_',' ')).append("  ").append(item.get(key));
            }
        }
        JSONObject failure=details.optJSONObject("failure");
        if(failure!=null && failure.has("log_tail"))
            text.append("\n\nLast game log lines (storage paths removed)\n").append(failure.getString("log_tail"));
        return text.toString();
    }
}
