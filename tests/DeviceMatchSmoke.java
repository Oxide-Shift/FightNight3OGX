package org.oxideshift.corner;

import org.oxideshift.corner.ThorLoadingGuide.Device;

/** The drawn device chosen for the corner icon and loading guide: screens and pads decide, names only break ties. */
public final class DeviceMatchSmoke {
    static void check(boolean pass,String message){if(!pass)throw new AssertionError(message);}
    public static void main(String[] args){
        // Dual-screen handhelds: by the second screen's shape, or the name when it says so.
        check(DeviceMatch.pick(2,1240f/1080f,true,540,"AYN","AYN Thor")==Device.THOR,"Thor");
        check(DeviceMatch.pick(2,1240f/1080f,true,540,"Acme","X1")==Device.THOR,"near-square second screen");
        check(DeviceMatch.pick(2,4f/3f,true,540,"Acme","X1")==Device.DUO,"4:3 second screen");
        check(DeviceMatch.pick(2,4f/3f,true,540,"AYN","Thor")==Device.THOR,"name wins on a dual screen");
        // Single-screen handhelds.
        check(DeviceMatch.pick(1,0,true,360,"Retroid","Pocket 6")==Device.RP6,"Retroid");
        check(DeviceMatch.pick(1,0,true,360,"AYN","Odin3")==Device.ODIN,"AYN");
        check(DeviceMatch.pick(1,0,true,420,"Acme","H")==Device.ODIN && DeviceMatch.pick(1,0,true,360,"Acme","H")==Device.RP6,"by size");
        // No built-in pad: phone or tablet by width; an external monitor without a pad is not a dual-screen handheld.
        check(DeviceMatch.pick(1,0,false,411,"Google","Pixel")==Device.PHONE,"phone");
        check(DeviceMatch.pick(1,0,false,800,"Samsung","Tab")==Device.TABLET,"tablet");
        check(DeviceMatch.pick(2,16f/9f,false,411,"Google","Pixel")==Device.PHONE,"phone with a monitor");
        check(DeviceMatch.pick(1,0,false,411,null,null)==Device.PHONE,"no names");
        // The Thor's own controls report as external over internal USB; a phone's Bluetooth pad stays external.
        check(DeviceMatch.builtInPad(false,true,"AYN") && DeviceMatch.builtInPad(false,true,"Retroid") && DeviceMatch.builtInPad(true,true,"Google"),"handheld pads");
        check(!DeviceMatch.builtInPad(false,true,"Google") && !DeviceMatch.builtInPad(false,false,"AYN"),"attached pads");
        for(Device d:Device.values())check(Device.of(d.code)==d && d.selector.endsWith("-selector.dxt3") && d.art.endsWith("-guide.dxt3"),"codes and assets "+d);
        System.out.println("Device match: dual screens, handhelds, phone and tablet passed");
    }
}
