package org.oxideshift.corner;

import android.view.*;
import java.nio.*;
import java.util.ArrayList;
import java.util.Arrays;

/** Xbox controller packet. Both sticks retain independent signed 16-bit axes. */
final class FightPad {
    private final byte[] state=new byte[18];
    private final ByteBuffer words=ByteBuffer.wrap(state).order(ByteOrder.LITTLE_ENDIAN);
    private static final String DIGITS="0123456789abcdef";
    private static final class Source {
        final int id;
        int keys,buttons,hat,leftTrigger,rightTrigger;
        short lx,ly,rx,ry;
        Source(int id){this.id=id;}
    }
    private final ArrayList<Source> devices=new ArrayList<>();
    private Source source(int id){
        for(int i=0;i<devices.size();i++)if(devices.get(i).id==id)return devices.get(i);
        Source source=new Source(id);devices.add(source);return source;
    }
    boolean remove(int id){
        for(int i=0;i<devices.size();i++)if(devices.get(i).id==id){
            devices.remove(i);combine();return true;
        }
        return false;
    }
    void clear(){devices.clear();Arrays.fill(state,(byte)0);}
    private static long magnitude(short x,short y){return (long)x*x+(long)y*y;}
    private void combine(){
        Arrays.fill(state,(byte)0);
        long left=0,right=0;
        for(int i=0;i<devices.size();i++){
            Source s=devices.get(i);
            state[0]|=(byte)(s.keys|s.hat);
            for(int n=0;n<8;n++)if((s.buttons & (1<<n))!=0)state[2+n]=(byte)255;
            state[8]=(byte)Math.max(state[8]&255,s.leftTrigger);
            state[9]=(byte)Math.max(state[9]&255,s.rightTrigger);
            long l=magnitude(s.lx,s.ly),r=magnitude(s.rx,s.ry);
            if(l>left){left=l;words.putShort(10,s.lx);words.putShort(12,s.ly);}
            if(r>right){right=r;words.putShort(14,s.rx);words.putShort(16,s.ry);}
        }
    }
    boolean navigating(){return (state[0]&15)!=0 || Math.abs((int)words.getShort(12))>16000;}
    String report(){return report(null,false,0,0);}
    String report(CornerControls.Key touch,boolean treating,int rx,int ry){
        StringBuilder packet=new StringBuilder("PAD ");
        for(int i=0;i<state.length;i++){
            int value=state[i]&255;
            if(touch!=null && touch.index==10 && (i==10||i==11))value=(touch.mask>>((i-10)*8))&255;
            else if(touch!=null && touch.index==i)value|=touch.mask;
            if(treating && i>=14)value=((i<16?rx:ry)>>((i%2)*8))&255;
            packet.append(DIGITS.charAt(value>>4)).append(DIGITS.charAt(value&15));
        }
        return packet.toString();
    }
    boolean key(KeyEvent e,boolean down){
        return key(e.getDeviceId(),e.getSource(),e.getKeyCode(),down && !e.isCanceled());
    }
    boolean key(int id,int sources,int code,boolean down){
        boolean controller=(sources & InputDevice.SOURCE_GAMEPAD)==InputDevice.SOURCE_GAMEPAD ||
                (sources & InputDevice.SOURCE_JOYSTICK)==InputDevice.SOURCE_JOYSTICK;
        boolean direction=code>=KeyEvent.KEYCODE_DPAD_UP && code<=KeyEvent.KEYCODE_DPAD_RIGHT;
        if(!controller && !(direction && (sources & InputDevice.SOURCE_DPAD)==InputDevice.SOURCE_DPAD))return false;
        int mask=0,index=-1;
        switch(code){
            case KeyEvent.KEYCODE_DPAD_UP:mask=1;break;case KeyEvent.KEYCODE_DPAD_DOWN:mask=2;break;
            case KeyEvent.KEYCODE_DPAD_LEFT:mask=4;break;case KeyEvent.KEYCODE_DPAD_RIGHT:mask=8;break;
            case KeyEvent.KEYCODE_BUTTON_START:mask=16;break;case KeyEvent.KEYCODE_BUTTON_SELECT:mask=32;break;
            case KeyEvent.KEYCODE_BUTTON_THUMBL:mask=64;break;case KeyEvent.KEYCODE_BUTTON_THUMBR:mask=128;break;
            case KeyEvent.KEYCODE_BUTTON_A:index=2;break;case KeyEvent.KEYCODE_BUTTON_B:index=3;break;
            case KeyEvent.KEYCODE_BUTTON_X:index=4;break;case KeyEvent.KEYCODE_BUTTON_Y:index=5;break;
            case KeyEvent.KEYCODE_BUTTON_R1:index=6;break;case KeyEvent.KEYCODE_BUTTON_L1:index=7;break;
            case KeyEvent.KEYCODE_BUTTON_L2:index=8;break;case KeyEvent.KEYCODE_BUTTON_R2:index=9;break;
            default:return false;
        }
        Source s=source(id);
        if(index>=0){
            int bit=1<<(index-2);
            s.buttons=down?s.buttons|bit:s.buttons&~bit;
        }else s.keys=down?s.keys|mask:s.keys&~mask;
        combine();
        return true;
    }
    private static float axis(MotionEvent e,int id){
        InputDevice d=e.getDevice();if(d==null)return 0;
        InputDevice.MotionRange range=d.getMotionRange(id,e.getSource());
        float v=e.getAxisValue(id);return range!=null&&Math.abs(v)<=range.getFlat()?0:v;
    }
    private static boolean has(MotionEvent e,int id){return e.getDevice()!=null&&e.getDevice().getMotionRange(id,e.getSource())!=null;}
    private static short stick(float f){return (short)Math.round(Math.min(1,Math.max(-1,f))*(f<0?32768:32767));}
    void axes(int id,float lx,float ly,float rx,float ry,float hatX,float hatY,float lt,float rt){
        Source s=source(id);
        // Some controllers advertise a zero flat and send a small residual at rest.
        boolean left=lx*lx+ly*ly>.08f*.08f,right=rx*rx+ry*ry>.08f*.08f;
        s.lx=left?stick(lx):0;s.ly=left?stick(-ly):0;
        s.rx=right?stick(rx):0;s.ry=right?stick(-ry):0;
        s.hat=(hatY<-.5f?1:0)|(hatY>.5f?2:0)|(hatX<-.5f?4:0)|(hatX>.5f?8:0);
        s.leftTrigger=Math.round(255*Math.max(0,Math.min(1,lt)));
        s.rightTrigger=Math.round(255*Math.max(0,Math.min(1,rt)));
        combine();
    }
    boolean motion(MotionEvent e){
        if(!e.isFromSource(InputDevice.SOURCE_JOYSTICK))return false;
        if(e.getActionMasked()==MotionEvent.ACTION_CANCEL)return remove(e.getDeviceId());
        if(e.getActionMasked()!=MotionEvent.ACTION_MOVE)return false;
        int rx=has(e,MotionEvent.AXIS_Z)?MotionEvent.AXIS_Z:MotionEvent.AXIS_RX;
        int ry=has(e,MotionEvent.AXIS_RZ)?MotionEvent.AXIS_RZ:MotionEvent.AXIS_RY;
        int lt=has(e,MotionEvent.AXIS_LTRIGGER)?MotionEvent.AXIS_LTRIGGER:MotionEvent.AXIS_BRAKE;
        int rt=has(e,MotionEvent.AXIS_RTRIGGER)?MotionEvent.AXIS_RTRIGGER:MotionEvent.AXIS_GAS;
        axes(e.getDeviceId(),axis(e,MotionEvent.AXIS_X),axis(e,MotionEvent.AXIS_Y),
                axis(e,rx),axis(e,ry),axis(e,MotionEvent.AXIS_HAT_X),axis(e,MotionEvent.AXIS_HAT_Y),
                axis(e,lt),axis(e,rt));
        return true;
    }
}
