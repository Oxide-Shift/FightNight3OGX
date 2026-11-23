package org.oxideshift.corner;

import android.content.Context;
import android.os.*;
import android.view.InputDevice;

/** Xbox motor state belongs to the active controller and the foreground bout. */
final class PadRumble {
    private final Context context;
    private int device=-1;
    private Vibrator vibrator;
    private VibratorManager manager;
    PadRumble(Context context){this.context=context;}
    void use(int id){if(id!=device){stop();device=id;}}
    void forget(int id){if(id==device)use(-1);}
    private final Handler handler=new Handler(Looper.getMainLooper());
    private long beatUntil;
    private int heldLeft,heldRight;
    private final Runnable resume=new Runnable(){public void run(){
        beatUntil=0;
        if((heldLeft|heldRight)!=0)play(heldLeft,heldRight,0,heldLevel);
    }};
    private int heldLevel;
    void stop(){
        handler.removeCallbacks(resume);heldLeft=heldRight=0;beatUntil=0;cancelMotors();
    }
    private void cancelMotors(){
        if(manager!=null)manager.cancel();
        if(vibrator!=null)vibrator.cancel();
        manager=null;vibrator=null;
    }
    static int amplitude(int motor,int level){
        int cap=level==1?89:level==2?166:level==3?255:0;
        return (Math.max(0,Math.min(65535,motor))*cap+65534)/65535;
    }
    private static VibrationEffect effect(Vibrator motor,int amplitude,int durationMs,int level){
        if(durationMs<0){
            long[] timing=countTiming(durationMs==-2?8:1,level,motor.hasAmplitudeControl());
            int strength=motor.hasAmplitudeControl()?amplitude:VibrationEffect.DEFAULT_AMPLITUDE;
            return timing.length==1?VibrationEffect.createOneShot(timing[0],strength):
                    VibrationEffect.createWaveform(timing,new int[]{strength,0,strength},-1);
        }
        if(!motor.hasAmplitudeControl() && level<3){
            // Fixed-strength motors can express the setting through pulse length.
            int on=level==1?35:65;
            if(durationMs>0)return VibrationEffect.createOneShot(Math.max(1,durationMs*on/100),VibrationEffect.DEFAULT_AMPLITUDE);
            return VibrationEffect.createWaveform(new long[]{on,100-on},
                    new int[]{VibrationEffect.DEFAULT_AMPLITUDE,0},0);
        }
        if(durationMs>0)return VibrationEffect.createOneShot(durationMs,amplitude);
        return VibrationEffect.createWaveform(new long[]{1000},new int[]{amplitude},0);
    }
    static long[] countTiming(int count,int level,boolean variable){
        int pulse=variable || level==3 ? 66 : level==2 ? 43 : 24;
        return count>=8 ? new long[]{pulse,105-pulse,pulse} : new long[]{pulse};
    }
    void count(int n,int level){
        if(n>=1 && n<=10)beat(n>=8,level);
    }
    void callout(int level){beat(true,level);}
    private void beat(boolean twice,int level){
        if(level<=0)return;
        int window=twice?171:66;
        handler.removeCallbacks(resume);
        beatUntil=SystemClock.uptimeMillis()+window;
        play(48000,36000,twice?-2:-1,level);
        handler.postAtTime(resume,beatUntil);
    }
    void apply(int left,int right,int durationMs,int level){
        if(durationMs==0){heldLeft=left;heldRight=right;heldLevel=level;}
        if(beatUntil>SystemClock.uptimeMillis())return;
        play(left,right,durationMs,level);
    }
    @SuppressWarnings("deprecation")
    private void play(int left,int right,int durationMs,int level){
        cancelMotors();
        int a=amplitude(left,level),b=amplitude(right,level);
        if((a|b)==0)return;
        InputDevice pad=InputDevice.getDevice(device);
        if(pad!=null && Build.VERSION.SDK_INT>=31){
            VibratorManager vm=pad.getVibratorManager();int[] ids=vm.getVibratorIds();
            if(ids.length>=2){
                CombinedVibration.ParallelCombination pair=CombinedVibration.startParallel();
                if(a>0)pair.addVibrator(ids[0],effect(vm.getVibrator(ids[0]),a,durationMs,level));
                if(b>0)pair.addVibrator(ids[1],effect(vm.getVibrator(ids[1]),b,durationMs,level));
                manager=vm;vm.vibrate(pair.combine());return;
            }
        }
        Vibrator v=pad==null?null:pad.getVibrator();
        if(v==null || !v.hasVibrator())v=context.getSystemService(Vibrator.class);
        if(v!=null && v.hasVibrator()){vibrator=v;v.vibrate(effect(v,Math.max(a,b),durationMs,level));}
    }
}
