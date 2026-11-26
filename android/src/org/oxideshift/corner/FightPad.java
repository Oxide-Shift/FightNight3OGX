package org.oxideshift.corner;

import android.view.*;
import java.nio.*;
import java.util.ArrayList;
import java.util.Arrays;

/** Xbox controller packet. Both sticks retain independent signed 16-bit axes. */
final class FightPad {
    private final byte[] state=new byte[18];
    private final ByteBuffer words=ByteBuffer.wrap(state).order(ByteOrder.LITTLE_ENDIAN);
    private final byte[] lastReport=new byte[18];
    private String packet;
    private static final String DIGITS="0123456789abcdef";
    private static final class Source {
        final int id;
        int keys,buttons,hat,leftTrigger,rightTrigger;
        InputDevice motionDevice;
        int motionSource;
        final int[] axes={MotionEvent.AXIS_X,MotionEvent.AXIS_Y,MotionEvent.AXIS_Z,MotionEvent.AXIS_RZ,
                MotionEvent.AXIS_HAT_X,MotionEvent.AXIS_HAT_Y,MotionEvent.AXIS_LTRIGGER,MotionEvent.AXIS_RTRIGGER};
        final float[] flat=new float[8];
        int supported;
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
        boolean changed=packet==null;
        for(int i=0;i<state.length;i++){
            int value=state[i]&255;
            if(touch!=null && touch.index==10 && (i==10||i==11))value=(touch.mask>>((i-10)*8))&255;
            else if(touch!=null && touch.index==i)value|=touch.mask;
            if(treating && i>=14)value=((i<16?rx:ry)>>((i%2)*8))&255;
            if(lastReport[i]!=(byte)value){lastReport[i]=(byte)value;changed=true;}
        }
        if(changed){
            char[] encoded=new char[4+2*lastReport.length];"PAD ".getChars(0,4,encoded,0);
            int pos=4;
            for(byte b:lastReport){encoded[pos++]=DIGITS.charAt((b&255)>>4);encoded[pos++]=DIGITS.charAt(b&15);}
            packet=new String(encoded);
        }
        return packet;
    }
    boolean key(KeyEvent e,boolean down){
        return key(e.getDeviceId(),e.getSource(),e.getKeyCode(),down && !e.isCanceled());
    }
    /** Apply a release even while presses are not accepted, so a held button can never outlive its key-up.
     *  Returns true when the combined state changed. Never creates a device entry. */
    boolean release(int id,int sources,int code){
        boolean known=false;
        for(Source s:devices)if(s.id==id){known=true;break;}
        if(!known)return false;
        byte[] before=state.clone();
        key(id,sources,code,false);
        return !Arrays.equals(before,state);
    }
    /** Motion while presses are not accepted: an axis may move toward rest but never away from it.
     *  Returns true when the combined state changed. */
    boolean releaseMotion(MotionEvent e){
        if(!directionalMotion(e))return false;
        Source pad=null;
        for(Source s:devices)if(s.id==e.getDeviceId()){pad=s;break;}
        if(pad==null)return false;
        byte[] before=state.clone();
        short lx=pad.lx,ly=pad.ly,rx=pad.rx,ry=pad.ry;
        int hat=pad.hat,lt=pad.leftTrigger,rt=pad.rightTrigger;
        motion(e);
        pad.lx=toward(lx,pad.lx);pad.ly=toward(ly,pad.ly);pad.rx=toward(rx,pad.rx);pad.ry=toward(ry,pad.ry);
        pad.hat&=hat;
        pad.leftTrigger=Math.min(lt,pad.leftTrigger);pad.rightTrigger=Math.min(rt,pad.rightTrigger);
        combine();
        return !Arrays.equals(before,state);
    }
    private static short toward(short old,short next){
        if(next==0 || old==0)return 0;
        if((old>0)!=(next>0))return 0;
        return Math.abs((int)next)<=Math.abs((int)old)?next:old;
    }
    boolean key(int id,int sources,int code,boolean down){
        boolean controller=(sources & InputDevice.SOURCE_GAMEPAD)==InputDevice.SOURCE_GAMEPAD ||
                (sources & InputDevice.SOURCE_JOYSTICK)==InputDevice.SOURCE_JOYSTICK;
        boolean direction=code>=KeyEvent.KEYCODE_DPAD_UP && code<=KeyEvent.KEYCODE_DPAD_RIGHT;
        if(!controller && code!=KeyEvent.KEYCODE_BUTTON_B && !(direction && (sources & InputDevice.SOURCE_DPAD)==InputDevice.SOURCE_DPAD))return false;
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
    private static int preferred(InputDevice device,int source,int first,int fallback){
        return device.getMotionRange(first,source)!=null?first:fallback;
    }
    private static void configure(Source pad,InputDevice device,int source){
        pad.motionDevice=device;pad.motionSource=source;
        pad.axes[2]=preferred(device,source,MotionEvent.AXIS_Z,MotionEvent.AXIS_RX);
        pad.axes[3]=preferred(device,source,MotionEvent.AXIS_RZ,MotionEvent.AXIS_RY);
        pad.axes[6]=preferred(device,source,MotionEvent.AXIS_LTRIGGER,MotionEvent.AXIS_BRAKE);
        pad.axes[7]=preferred(device,source,MotionEvent.AXIS_RTRIGGER,MotionEvent.AXIS_GAS);
        pad.supported=0;
        for(int i=0;i<pad.axes.length;i++){
            InputDevice.MotionRange range=device.getMotionRange(pad.axes[i],source);
            if(range!=null){pad.supported|=1<<i;pad.flat[i]=range.getFlat();}
        }
    }
    private static float axis(MotionEvent e,Source pad,int i){
        float value=e.getAxisValue(pad.axes[i]);
        return Math.abs(value)<=pad.flat[i]?0:value;
    }
    private static short stick(float f){return (short)Math.round(Math.min(1,Math.max(-1,f))*(f<0?32768:32767));}
    void axes(int id,float lx,float ly,float rx,float ry,float hatX,float hatY,float lt,float rt){
        applyAxes(source(id),255,lx,ly,rx,ry,hatX,hatY,lt,rt);
    }
    private void applyAxes(Source s,int mask,float lx,float ly,float rx,float ry,float hatX,float hatY,float lt,float rt){
        // Some controllers advertise a zero flat and send a small residual at rest.
        boolean left=lx*lx+ly*ly>.08f*.08f,right=rx*rx+ry*ry>.08f*.08f;
        if((mask&1)!=0)s.lx=left?stick(lx):0;
        if((mask&2)!=0)s.ly=left?stick(-ly):0;
        if((mask&4)!=0)s.rx=right?stick(rx):0;
        if((mask&8)!=0)s.ry=right?stick(-ry):0;
        if((mask&16)!=0)s.hat=(s.hat&3)|(hatX<-.5f?4:0)|(hatX>.5f?8:0);
        if((mask&32)!=0)s.hat=(s.hat&12)|(hatY<-.5f?1:0)|(hatY>.5f?2:0);
        if((mask&64)!=0)s.leftTrigger=Math.round(255*Math.max(0,Math.min(1,lt)));
        if((mask&128)!=0)s.rightTrigger=Math.round(255*Math.max(0,Math.min(1,rt)));
        combine();
    }
    static boolean directionalMotion(MotionEvent event){
        return event.isFromSource(InputDevice.SOURCE_JOYSTICK) || event.isFromSource(InputDevice.SOURCE_DPAD);
    }
    boolean cancelMotion(int id,boolean joystick){
        for(Source pad:devices)if(pad.id==id){
            // Android cancels motion streams independently of key streams.
            // A real disconnect clears the entire device in remove().
            applyAxes(pad,joystick?255:48,0,0,0,0,0,0,0,0);
            return true;
        }
        return false;
    }
    boolean motion(MotionEvent e){
        if(!directionalMotion(e))return false;
        boolean joystick=e.isFromSource(InputDevice.SOURCE_JOYSTICK);
        if(e.getActionMasked()==MotionEvent.ACTION_CANCEL)return cancelMotion(e.getDeviceId(),joystick);
        if(e.getActionMasked()!=MotionEvent.ACTION_MOVE)return false;
        InputDevice device=e.getDevice();
        int id=e.getDeviceId(),bits=e.getSource();
        if(device==null){cancelMotion(id,joystick);return true;}
        Source pad=source(id);
        if(device!=pad.motionDevice || bits!=pad.motionSource)configure(pad,device,bits);
        int supported=pad.supported;
        if(!joystick)supported&=48;
        applyAxes(pad,supported,axis(e,pad,0),axis(e,pad,1),axis(e,pad,2),axis(e,pad,3),
                axis(e,pad,4),axis(e,pad,5),axis(e,pad,6),axis(e,pad,7));
        return true;
    }
}
