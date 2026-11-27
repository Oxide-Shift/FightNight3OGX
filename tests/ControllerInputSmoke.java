package org.oxideshift.corner;
import android.view.InputDevice;
import android.view.KeyEvent;
import java.util.Arrays;

public class ControllerInputSmoke {
    static void check(boolean b){if(!b)throw new AssertionError();}
    static int value(String report,int n){return Integer.parseInt(report.substring(4+2*n,6+2*n),16);}
    public static void main(String[] args){
        FightPad idlePad=new FightPad();
        if(idlePad.report()!=idlePad.report())throw new AssertionError("Duplicate input rebuilt the wire packet");

        FightPad pad=new FightPad();String neutral=pad.report();
        int gamepad=InputDevice.SOURCE_GAMEPAD;
        pad.axes(7,0,0,0,0,-1,1,0,0);
        check(value(pad.report(),0)==6 && pad.report().substring(24).equals(neutral.substring(24)));
        pad.axes(7,.75f,-.5f,-1,0,1,0,0,.8f);
        pad.key(7,gamepad,KeyEvent.KEYCODE_BUTTON_X,true);
        String held=pad.report();
        check(pad.cancelMotion(7,false) && value(pad.report(),0)==0);
        check(pad.report().substring(6).equals(held.substring(6)));
        check(pad.cancelMotion(7,true) && value(pad.report(),4)==255);
        check(pad.report().substring(16).equals(neutral.substring(16)));
        check(pad.remove(7) && pad.report().equals(neutral));
        // A guard (right trigger key) release applies without a matching accepted press path, exactly once.
        pad.key(9,gamepad,KeyEvent.KEYCODE_BUTTON_R2,true);
        check(value(pad.report(),9)==255);
        check(!pad.release(10,gamepad,KeyEvent.KEYCODE_BUTTON_R2) && value(pad.report(),9)==255);
        check(pad.release(9,gamepad,KeyEvent.KEYCODE_BUTTON_R2) && pad.report().equals(neutral));
        check(!pad.release(9,gamepad,KeyEvent.KEYCODE_BUTTON_R2));
        check(!pad.release(9,gamepad,KeyEvent.KEYCODE_VOLUME_UP));
        check(pad.remove(9) && pad.report().equals(neutral));
        for(int i=0;i<10000;i++){
            pad.axes(1,1,-1,0,0,-1,0,.5f,0);
            pad.axes(2,0,0,.01f,.01f,0,0,0,0);
            pad.key(1,gamepad,KeyEvent.KEYCODE_DPAD_LEFT,true);
            check(pad.key(2,gamepad,KeyEvent.KEYCODE_BUTTON_L2,true));
            check(value(pad.report(),8)==255 && value(pad.report(),11)==127);
            pad.remove(2);check(value(pad.report(),8)==128);
            pad.axes(1,0,0,0,0,0,0,0,0);check(value(pad.report(),0)==4);
            pad.key(1,gamepad,KeyEvent.KEYCODE_DPAD_LEFT,false);check(pad.report().equals(neutral));
            pad.axes(1,.04f,.04f,0,0,0,0,0,0);check(pad.report().equals(neutral));
            check(value(pad.report(null,true,-32768,32767),15)==128);
            pad.key(1,gamepad,KeyEvent.KEYCODE_BUTTON_Y,true);check(value(pad.report(),5)==255);
            pad.clear();check(pad.report().equals(neutral));
        }
        check(!pad.key(9,InputDevice.SOURCE_KEYBOARD,KeyEvent.KEYCODE_BUTTON_Y,true));
        for(int[] b:new int[][]{{gamepad,KeyEvent.KEYCODE_BUTTON_B},{InputDevice.SOURCE_KEYBOARD,KeyEvent.KEYCODE_BUTTON_B}}){
            check(pad.key(9,b[0],b[1],true) && value(pad.report(),3)==255);
            check(pad.key(9,b[0],b[1],false) && pad.report().equals(neutral));
        }
        for(int source:new int[]{gamepad,InputDevice.SOURCE_KEYBOARD,InputDevice.SOURCE_JOYSTICK}){
            check(!pad.key(9,source,KeyEvent.KEYCODE_BACK,true));
            check(!pad.key(9,source,KeyEvent.KEYCODE_BACK,false));
            check(neutral.equals(pad.report()));
        }
        check(value(pad.report(CornerControls.Key.BACK,false,0,0),0)==32);
        for(int level=0;level<=3;level++){
            check(PadRumble.amplitude(65535,level)==new int[]{0,89,166,255}[level]);
            for(int n=1;n<=10;n++){
                long[] pattern=PadRumble.countTiming(n,level,true);
                check(pattern.length==(n>=8?3:1) && pattern[0]==66);
                if(n>=8)check(pattern[1]>0 && pattern[2]==pattern[0]);
            }
        }
        check(PadRumble.countTiming(8,1,false)[0]<PadRumble.countTiming(8,2,false)[0]);
        remap(gamepad);
        System.out.println("10000 controller disconnect/reconnect cycles, all count haptic patterns and button remapping");
    }
    static boolean permutation(PadMap map){
        boolean[] seen=new boolean[PadMap.SLOTS];
        for(int p=0;p<PadMap.SLOTS;p++){int s=map.slot(p);if(seen[s])return false;seen[s]=true;}
        return true;
    }
    static void remap(int gamepad){
        // Stored maps: anything damaged or partial reads as the default wiring.
        check(PadMap.decode(null).isStandard() && PadMap.decode("").isStandard());
        for(String bad:new String[]{"1,2,3","0,0,2,3,4,5,6,7,8,9,10,11,12,13,14,15","0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,16",
                "a,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15","0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,0"})
            check(PadMap.decode(bad).isStandard());
        check(PadMap.standard().encode().equals("0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15"));
        // Default wiring matches every key FightPad has always accepted.
        int[] keys={KeyEvent.KEYCODE_DPAD_UP,KeyEvent.KEYCODE_DPAD_DOWN,KeyEvent.KEYCODE_DPAD_LEFT,KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.KEYCODE_BUTTON_START,KeyEvent.KEYCODE_BUTTON_SELECT,KeyEvent.KEYCODE_BUTTON_THUMBL,KeyEvent.KEYCODE_BUTTON_THUMBR,
                KeyEvent.KEYCODE_BUTTON_A,KeyEvent.KEYCODE_BUTTON_B,KeyEvent.KEYCODE_BUTTON_X,KeyEvent.KEYCODE_BUTTON_Y,
                KeyEvent.KEYCODE_BUTTON_R1,KeyEvent.KEYCODE_BUTTON_L1,KeyEvent.KEYCODE_BUTTON_L2,KeyEvent.KEYCODE_BUTTON_R2};
        FightPad pad=new FightPad();String neutral=pad.report();
        for(int p=0;p<16;p++){
            check(PadMap.physicalKey(keys[p])==p);
            check(pad.key(3,gamepad,keys[p],true));
            String r=pad.report();
            check(p<8?value(r,0)==1<<p:value(r,p-6)==255);
            check(pad.key(3,gamepad,keys[p],false) && pad.report().equals(neutral));
        }
        // Assigning swaps: D-pad up now presses Y, and the old Y button takes over D-pad up. Always a permutation.
        PadMap map=PadMap.standard().assign(11,0);
        check(map.slot(0)==11 && map.slot(11)==0 && map.physical(11)==0 && map.physical(0)==11 && permutation(map));
        check(PadMap.decode(map.encode()).encode().equals(map.encode()));
        java.util.Random random=new java.util.Random(81);
        PadMap walk=PadMap.standard();
        for(int i=0;i<5000;i++){
            int xbox=random.nextInt(16),physical=random.nextInt(16);
            walk=walk.assign(xbox,physical);
            check(walk.slot(physical)==xbox && permutation(walk) && PadMap.decode(walk.encode()).encode().equals(walk.encode()));
        }
        check(PadMap.standard().assign(-1,3)==PadMap.standard() && PadMap.standard().assign(3,16)==PadMap.standard());
        // Live: a remapped D-pad press reaches the game as Y; a held control stays down across a map change and
        // still releases exactly once afterwards (nothing stuck).
        check(!pad.map(map) && pad.map()==map);   // nothing held, so nothing to resend
        pad.key(4,gamepad,KeyEvent.KEYCODE_DPAD_UP,true);
        check(value(pad.report(),0)==0 && value(pad.report(),5)==255);
        pad.key(4,gamepad,KeyEvent.KEYCODE_BUTTON_Y,true);check(value(pad.report(),0)==1);
        // Both swapped controls held: the packet is the same under either map.
        check(!pad.map(PadMap.standard()) && pad.map().isStandard());
        check(value(pad.report(),0)==1 && value(pad.report(),5)==255);
        check(pad.release(4,gamepad,KeyEvent.KEYCODE_BUTTON_Y) && value(pad.report(),5)==0);
        check(pad.map(map) && value(pad.report(),0)==0 && value(pad.report(),5)==255);
        pad.key(4,gamepad,KeyEvent.KEYCODE_BUTTON_Y,true);check(!pad.map(PadMap.standard()));
        check(pad.release(4,gamepad,KeyEvent.KEYCODE_DPAD_UP) && pad.release(4,gamepad,KeyEvent.KEYCODE_BUTTON_Y));
        check(pad.report().equals(neutral));
        check(!pad.map(PadMap.standard()));
        // Hat directions remap like keys.
        pad.map(map);pad.axes(5,0,0,0,0,0,-1,0,0);check(value(pad.report(),0)==0 && value(pad.report(),5)==255);
        pad.axes(5,0,0,0,0,0,0,0,0);check(pad.report().equals(neutral));
        // Triggers: swapping LT and RT keeps pressure; a trigger sent to A presses past half travel only.
        pad.map(PadMap.standard().assign(PadMap.LT,PadMap.RT));
        pad.axes(5,0,0,0,0,0,0,.3f,0);check(value(pad.report(),8)==0 && value(pad.report(),9)==77);
        pad.axes(5,0,0,0,0,0,0,0,0);
        pad.map(PadMap.standard().assign(8,PadMap.LT));
        pad.axes(5,0,0,0,0,0,0,.3f,0);check(value(pad.report(),2)==0 && value(pad.report(),8)==0);
        pad.axes(5,0,0,0,0,0,0,.7f,0);check(value(pad.report(),2)==255 && value(pad.report(),8)==0);
        pad.key(5,gamepad,KeyEvent.KEYCODE_BUTTON_A,true);check(value(pad.report(),8)==255);
        pad.clear();check(pad.report().equals(neutral));
        // Sticks are never remapped.
        pad.axes(6,1,0,0,-1,0,0,0,0);check(value(pad.report(),11)==127 && value(pad.report(),17)==127);
        pad.clear();pad.map(PadMap.standard());
    }
}
