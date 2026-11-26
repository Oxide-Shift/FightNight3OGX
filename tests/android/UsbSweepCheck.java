package org.oxideshift.corner;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import org.json.JSONObject;
import java.io.File;
import java.lang.ref.WeakReference;

/** Fixed tests against this separately installed app's own data and normal launcher. */
public final class UsbSweepCheck extends CornerActivity {
    private static WeakReference<UsbSweepCheck> live = new WeakReference<>(null);
    private final MenuContext menus = new MenuContext();
    private String outcome = "READY", lastRequest = "";
    private int treatmentMoves, treatmentReleases;

    @Override boolean treatmentStick(boolean down,int x,int y) {
        if (!super.treatmentStick(down,x,y)) return false;
        if (!down) treatmentReleases++;
        else if (x!=0 || y!=0) treatmentMoves++;
        return true;
    }

    private void inspectTreatment(JSONObject state) throws Exception {
        android.view.View panel=lowerContent();
        if (panel==null || !panel.isShown() || panel.getDisplay()==null) return;
        java.util.ArrayList<android.view.View> matches=new java.util.ArrayList<>();
        panel.findViewsWithText(matches,"Manual treatment pad.",android.view.View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION);
        if (matches.size()!=1) return;
        android.view.View pad=matches.get(0);
        android.graphics.Rect visible=new android.graphics.Rect();
        if (!pad.isShown() || !pad.getLocalVisibleRect(visible) || visible.width()<32 || visible.height()<32) return;
        int[] xy=new int[2];
        pad.getLocationOnScreen(xy);
        int x=xy[0]+visible.left, y=xy[1]+visible.top;
        state.put("treatmentPad",new org.json.JSONArray(new int[]{panel.getDisplay().getDisplayId(),
                x+visible.width()/3,y+visible.height()/2,x+2*visible.width()/3,y+visible.height()/3}));
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (!getPackageName().equals("org.oxideshift.corner.usbqa")) { finish(); return; }
        live = new WeakReference<>(this);
        if (state != null) lastRequest = state.getString("sweepRequest", "");
    }
    @Override protected void onPostResume() {
        super.onPostResume();
        if (!isFinishing()) runRequest(getIntent());
    }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        runRequest(intent);
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putString("sweepRequest", lastRequest);
    }
    @Override protected void onDestroy() {
        if (live.get() == this) live.clear();
        super.onDestroy();
    }
    private void runRequest(Intent intent) {
        String id = intent.getStringExtra("run_id"), phase = intent.getStringExtra("phase");
        if (id == null || !id.matches("[0-9a-f]{32}") || id.equals(lastRequest)) return;
        lastRequest = id;
        if ("game".equals(phase)) {
            outcome = launch().name();
        } else if (("primary".equals(phase) || "automatic".equals(phase) ||
                "ordinary".equals(phase) || "shared".equals(phase)) && !preparingImport() && currentGame()==null) {
            FightMenu menu=launcherMenu();
            boolean display="primary".equals(phase) || "automatic".equals(phase);
            android.widget.Spinner picker=display?menu.displayRoute:menu.renderer;
            int target="primary".equals(phase)?1:"shared".equals(phase)?3:0;
            outcome="ROUTE_SELECTING";
            menu.openAdvancedGraphics();
            picker.post(()->{
                if (!id.equals(lastRequest))return;
                if (!picker.isShown()) {outcome="SETTING_HIDDEN";return;}
                picker.setSelection(target);
                outcome="ROUTE_SELECTED";
            });
        } else if ("high1080".equals(phase) && !preparingImport() && currentGame()==null) {
            FightMenu menu=launcherMenu();
            menu.quality.getChildAt(2).performClick();
            menu.pacing.getChildAt(2).performClick();
            menu.resolution.setSelection(2);
            outcome="GRAPHICS_SELECTED";
        } else if ("disc".equals(phase) || "save".equals(phase)) {
            boolean disc = phase.equals("disc");
            File source = new File(getFilesDir(), disc ? "usb-disc.iso" : "usb-save.zip");
            if (!source.isFile() || preparingImport()) { outcome = "SOURCE_UNAVAILABLE"; return; }
            onActivityResult(disc ? DISC : SAVE, RESULT_OK, new Intent().setData(Uri.fromFile(source)));
            outcome = disc ? "IMPORT_DISC" : "IMPORT_SAVE";
        } else outcome = "READY";
    }
    private JSONObject snapshot() throws Exception {
        JSONObject j = new JSONObject();
        j.put("deviceElapsedNs",android.os.SystemClock.elapsedRealtimeNanos());
        j.put("deviceMonotonicNs",System.nanoTime());
        FightMenu menu=launcherMenu();
        j.put("setupVisible",menu.isShown() && menu.isLaidOut());
        j.put("displaySelection",menu.displayRoute.getSelectedItemPosition());
        j.put("rendererSelection",menu.renderer.getSelectedItemPosition());
        android.content.SharedPreferences stored=getSharedPreferences("fight-setup",MODE_PRIVATE);
        j.put("savedDisplaySelection",stored.getInt("displayRoute",0));
        j.put("savedRendererSelection",stored.getInt("renderer",0));
        j.put("quality",menu.qualityValue()).put("targetFps",DisplayCadence.target(menu.pacing.index()));
        int selection=menu.resolution.getSelectedItemPosition();
        if (selection>=0 && selection<FightMenu.HEIGHTS.length) j.put("requestedHeight",FightMenu.HEIGHTS[selection]);
        j.put("focused",hasWindowFocus());
        j.put("treatmentMoves",treatmentMoves).put("treatmentReleases",treatmentReleases);
        j.put("request", lastRequest).put("outcome", outcome).put("busy", preparingImport())
            .put("prepared", !preparingImport() && (currentGame()!=null || !needsGamePreparation())).put("setup", setupStatus());
        android.view.View companion=lowerContent();
        if(companion!=null && companion.isShown() && companion.isAttachedToWindow() && companion.getDisplay()!=null)
            j.put("companionDisplay",companion.getDisplay().getDisplayId());
        CornerSession game = currentGame();
        if (game == null) return j;
        j.put("renderHeight",game.presentationHeight());
        j.put("active", game.active()).put("root", game.atRoot()).put("front", game.frontPage)
            .put("cabinet", game.cabinetOpen).put("trax", game.browsingTrax).put("intro", game.introMovie())
            .put("result", game.result()).put("finish", game.finish()).put("career", String.valueOf(game.career.page));
        java.util.Map<String,Object> stats = game.snapshot();
        BoutAnnouncements.Notice notice=game.callout();
        if(notice!=null)j.put("fightNotice",notice.words).put("noticeUptimeMs",notice.at);
        if(notice!=null && "ROCKED!!".equals(notice.words))inspectRocked(j);
        for (String key : new String[]{"link","renderer","audio","frames"}) j.put(key,stats.get(key));
        LiveFrame f = currentFrame();
        if (f != null) {
            MenuContext.Page page = menus.identify(f, game.atRoot());
            j.put("serial",f.sequence).put("scene",f.scene(game.inLiveBout()).name());
            if ("TREATMENT".equals(j.optString("scene"))) inspectTreatment(j);
            if (page != null) j.put("menu",page.title);
        }
        FightBroadcast.Bout bout = game.bout();
        if (bout != null) {
            j.put("round",bout.round).put("clock",bout.seconds);
            if (bout.punches != null) j.put("punches",new org.json.JSONArray(bout.punches));
        }
        return j;
    }
    /** Report the ROCKED spray lane and the protected fighter/punch panels in RingsideView coordinates. */
    private void inspectRocked(JSONObject j) throws Exception {
        android.view.View panel=lowerContent();
        if(!(panel instanceof android.view.ViewGroup) || ((android.view.ViewGroup)panel).getChildCount()==0)return;
        android.view.View child=((android.view.ViewGroup)panel).getChildAt(0);
        if(!(child instanceof RingsideView) || !child.isShown())return;
        RingsideView view=(RingsideView)child;
        java.lang.reflect.Field lane=RingsideView.class.getDeclaredField("rockedArea");lane.setAccessible(true);
        android.graphics.Rect area=(android.graphics.Rect)lane.get(view);
        j.put("lowerWidth",view.getWidth()).put("rockedLane",bounds(area));
        for(String[] f:new String[][]{{"fightPanel","fightPanel"},{"punchPanel","meterPanel"}}){
            java.lang.reflect.Field field=RingsideView.class.getDeclaredField(f[0]);field.setAccessible(true);
            android.view.View v=(android.view.View)field.get(view);
            if(v==null || !v.isShown())continue;
            android.graphics.Rect r=new android.graphics.Rect();v.getDrawingRect(r);view.offsetDescendantRectToMyCoords(v,r);
            j.put(f[1],bounds(r));
        }
    }
    private static org.json.JSONArray bounds(android.graphics.Rect r){
        return new org.json.JSONArray().put(r.left).put(r.top).put(r.right).put(r.bottom);
    }
    // Some retail Android images omit the shell uinput executable. Replay a short,
    // fixed stick gesture through the Activity with the handheld's actual axis map.
    private boolean sampleSticks(boolean neutral, boolean cross) {
        for (int id : android.view.InputDevice.getDeviceIds()) {
            android.view.InputDevice pad = android.view.InputDevice.getDevice(id);
            int source = android.view.InputDevice.SOURCE_JOYSTICK;
            if (pad == null || pad.isVirtual() || !pad.supportsSource(source)) continue;
            int rx = pad.getMotionRange(android.view.MotionEvent.AXIS_Z,source) == null ?
                    android.view.MotionEvent.AXIS_RX : android.view.MotionEvent.AXIS_Z;
            int ry = pad.getMotionRange(android.view.MotionEvent.AXIS_RZ,source) == null ?
                    android.view.MotionEvent.AXIS_RY : android.view.MotionEvent.AXIS_RZ;
            if (pad.getMotionRange(rx,source)==null || pad.getMotionRange(ry,source)==null) continue;
            android.view.MotionEvent.PointerCoords c=new android.view.MotionEvent.PointerCoords();
            c.setAxisValue(android.view.MotionEvent.AXIS_Y,neutral?0:-0.8f);
            c.setAxisValue(rx,neutral?0:cross?0.9f:0);
            c.setAxisValue(ry,neutral?0:cross?0:-0.95f);
            android.view.MotionEvent.PointerProperties prop=new android.view.MotionEvent.PointerProperties();
            prop.id=0; prop.toolType=android.view.MotionEvent.TOOL_TYPE_UNKNOWN;
            long now=android.os.SystemClock.uptimeMillis();
            android.view.MotionEvent event=android.view.MotionEvent.obtain(now,now,
                    android.view.MotionEvent.ACTION_MOVE,1,new android.view.MotionEvent.PointerProperties[]{prop},
                    new android.view.MotionEvent.PointerCoords[]{c},0,0,1,1,id,0,source,0);
            try {return dispatchGenericMotionEvent(event);} finally {event.recycle();}
        }
        return false;
    }
    private boolean punchSequence() {
        if (!hasWindowFocus() || currentGame()==null || !currentGame().inLiveBout() || !sampleSticks(false,false)) return false;
        android.os.Handler h=new android.os.Handler(android.os.Looper.getMainLooper());
        h.postDelayed(()->sampleSticks(true,false),180);
        h.postDelayed(()->{if(hasWindowFocus())sampleSticks(false,true);},300);
        h.postDelayed(()->sampleSticks(true,false),480);
        return true;
    }
    public static final class Inspect extends BroadcastReceiver {
        @Override public void onReceive(Context context,Intent intent) {
            UsbSweepCheck activity = live.get();
            try {
                if ("isolated.STICK_COMBO".equals(intent.getAction())) {
                    setResultData(activity!=null && activity.punchSequence()?"GESTURE":"UNAVAILABLE");
                    return;
                }
                String data = activity == null ? "{}" : activity.snapshot().toString();
                setResultData(android.util.Base64.encodeToString(data.getBytes("UTF-8"),android.util.Base64.NO_WRAP));
            } catch (Exception ex) {
                android.util.Log.e("FightNightUsbQA","Unable to inspect isolated activity",ex);
                setResultCode(1);
            }
        }
    }
}
