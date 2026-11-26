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
        System.out.println("10000 controller disconnect/reconnect cycles and all count haptic patterns");
    }
}
