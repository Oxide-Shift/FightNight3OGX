package org.oxideshift.corner;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.widget.LinearLayout;
import android.widget.TextView;

/** "Press the button you want": waits for one physical control from any controller and reports it in
 *  PadMap's physical numbering. Captures key presses, the hat D-pad and analog triggers. Sticks are ignored.
 *  Ends on a capture, after {@link #SECONDS} seconds, on the system Back key, or on Cancel (touch). */
final class ButtonCapture {
    interface Result {void physical(int physical);}
    static final int SECONDS=6;
    /** An axis counts past PRESS, and only again after it has dropped below REST. */
    private static final float REST=.3f, PRESS=.6f;

    /** Dialog body: prompt, swap note, countdown (child 2) and a touch-only Cancel (child 3). */
    static LinearLayout content(android.content.Context owner,String xbox){
        float density=owner.getResources().getDisplayMetrics().density;
        LinearLayout body=new LinearLayout(owner);body.setOrientation(LinearLayout.VERTICAL);
        int pad=Math.round(22*density);body.setPadding(pad,Math.round(8*density),pad,Math.round(10*density));
        TextView prompt=new TextView(owner);prompt.setTextSize(17);prompt.setTextColor(GameInk.TEXT);
        prompt.setText("Press the button you want for "+xbox+".");body.addView(prompt);
        TextView detail=new TextView(owner);detail.setTextSize(14);detail.setTextColor(GameInk.MUTED);
        detail.setPadding(0,Math.round(8*density),0,0);
        detail.setText("Whatever that button did moves to the one "+xbox+" used, so the two swap.");body.addView(detail);
        TextView timer=new TextView(owner);timer.setTextSize(14);timer.setTextColor(GameInk.GOLD);
        timer.setPadding(0,Math.round(10*density),0,0);timer.setText("Closing in "+SECONDS+" s");body.addView(timer);
        TextView cancel=new TextView(owner);cancel.setText("Cancel");cancel.setTextSize(16);cancel.setTextColor(GameInk.GOLD);
        cancel.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);cancel.setMinHeight(Math.round(48*density));
        cancel.setFocusable(false);body.addView(cancel);
        return body;
    }

    static void show(Activity owner,String xbox,Result result){
        LinearLayout body=content(owner,xbox);
        TextView timer=(TextView)body.getChildAt(2),cancel=(TextView)body.getChildAt(3);
        // The body holds focus so joystick motion (hat, triggers) reaches it rather than a button.
        body.setFocusable(true);body.setFocusableInTouchMode(true);

        AlertDialog dialog=new GameDialog(owner).setTitle("Assign "+xbox).setView(body).create();
        dialog.setCanceledOnTouchOutside(true);
        Handler main=new Handler(Looper.getMainLooper());
        long shown=SystemClock.uptimeMillis();
        boolean[] done={false};
        // Hat, left trigger, right trigger. Axes only report changes, so the first report after opening is
        // usually the press itself: start at rest (Thor, 2026-11-26: a rest-first rule never saw the D-pad or L2).
        boolean[] rest={true,true,true};
        int[] heldKey={0};               // captured key whose release the dialog still swallows
        Runnable[] tick=new Runnable[1];
        Runnable close=()->{if(dialog.isShowing())dialog.dismiss();};
        java.util.function.IntConsumer end=physical->{
            if(done[0])return;
            done[0]=true;main.removeCallbacks(tick[0]);
            if(physical>=0)result.physical(physical);
            // A captured key closes on its own release, so that release (e.g. B sent as Back) cannot reach
            // Fight Setup underneath. The delay covers a release that never arrives.
            if(heldKey[0]!=0){timer.setText("");main.postDelayed(close,600);}
            else close.run();
        };
        tick[0]=()->{
            long left=SECONDS-(SystemClock.uptimeMillis()-shown)/1000;
            if(left<=0){end.accept(-1);return;}
            timer.setText("Closing in "+left+" s");
            main.postDelayed(tick[0],250);
        };
        cancel.setOnClickListener(v->end.accept(-1));
        dialog.setOnDismissListener(d->end.accept(-1));
        dialog.setOnKeyListener((d,code,event)->{
            if(done[0]){
                if(code==heldKey[0] && event.getAction()==KeyEvent.ACTION_UP){main.removeCallbacks(close);close.run();}
                return true;
            }
            boolean controller=event.isFromSource(InputDevice.SOURCE_GAMEPAD) || event.isFromSource(InputDevice.SOURCE_JOYSTICK);
            int physical;
            if(code==KeyEvent.KEYCODE_BACK){
                // Same rule as the game: a pad without its own B sends B as Back. Any other Back cancels.
                if(!controller || CornerActivity.hasButtonB(event.getDevice())){
                    if(event.getAction()==KeyEvent.ACTION_UP)end.accept(-1);
                    return true;
                }
                physical=9;
            }else physical=PadMap.physicalKey(code);
            if(physical<0)return false;
            // Ignore the release (and repeats) of the press that opened this dialog.
            if(event.getAction()==KeyEvent.ACTION_DOWN && event.getRepeatCount()==0 && event.getDownTime()>=shown){
                heldKey[0]=code;end.accept(physical);
            }
            return true;
        });
        body.setOnGenericMotionListener((v,event)->{
            if(!FightPad.directionalMotion(event) || event.getActionMasked()!=MotionEvent.ACTION_MOVE)return false;
            float x=event.getAxisValue(MotionEvent.AXIS_HAT_X),y=event.getAxisValue(MotionEvent.AXIS_HAT_Y);
            float lt=Math.max(event.getAxisValue(MotionEvent.AXIS_LTRIGGER),event.getAxisValue(MotionEvent.AXIS_BRAKE));
            float rt=Math.max(event.getAxisValue(MotionEvent.AXIS_RTRIGGER),event.getAxisValue(MotionEvent.AXIS_GAS));
            if(Math.abs(x)<REST && Math.abs(y)<REST)rest[0]=true;
            else if(rest[0] && (Math.abs(x)>PRESS || Math.abs(y)>PRESS)){
                end.accept(Math.abs(y)>=Math.abs(x)?(y<0?0:1):(x<0?2:3));return true;
            }
            if(lt<REST)rest[1]=true;else if(rest[1] && lt>PRESS){end.accept(PadMap.LT);return true;}
            if(rt<REST)rest[2]=true;else if(rest[2] && rt>PRESS){end.accept(PadMap.RT);return true;}
            return true;
        });
        dialog.show();
        body.requestFocus();
        tick[0].run();
    }
}
