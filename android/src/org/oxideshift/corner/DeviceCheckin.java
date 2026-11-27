package org.oxideshift.corner;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONObject;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * Anonymous player count. While the app is open it tells the project server, every ten minutes, that this install is
 * here: a random install ID made on first use (not tied to the device, account or player), the app version, the
 * Android API level, the coarse device type the corner art already uses, the display count and whether a game is
 * running. Nothing else: no model name, no location, no saves. The server keeps only first-seen, last-seen and
 * those fields per ID. Players turn it off in Fight Setup ("usageCount"); the QA package has no network permission.
 */
final class DeviceCheckin {
    static final long INTERVAL_MS=10*60*1000L;
    static final String SETTING="usageCount";
    private final Context app;
    private final SharedPreferences settings,store;
    private final String version;
    private final int versionCode;
    private final BooleanSupplier playing;
    private final Handler main=new Handler(Looper.getMainLooper());
    private boolean running;
    private volatile boolean sending;
    private final Runnable beat=new Runnable(){@Override public void run(){
        if(!running)return;
        send();
        main.postDelayed(this,INTERVAL_MS);
    }};

    DeviceCheckin(Activity activity,SharedPreferences settings,BooleanSupplier playing){
        app=activity.getApplicationContext();this.settings=settings;this.playing=playing;
        store=app.getSharedPreferences("usage-count",Context.MODE_PRIVATE);
        String name="";int code=0;
        try{
            android.content.pm.PackageInfo info=app.getPackageManager().getPackageInfo(app.getPackageName(),0);
            name=info.versionName;code=info.versionCode;
        }catch(android.content.pm.PackageManager.NameNotFoundException ignored){}
        version=name;versionCode=code;
    }

    static boolean enabled(SharedPreferences settings){return settings.getBoolean(SETTING,true);}

    /** Foreground only: Activity.onStart starts the beat, onStop ends it. */
    void start(){if(running)return;running=true;main.post(beat);}
    void stop(){running=false;main.removeCallbacks(beat);}

    private String installId(){
        String id=store.getString("id",null);
        if(id==null){id=UUID.randomUUID().toString();store.edit().putString("id",id).apply();}
        return id;
    }

    static JSONObject body(String id,String version,int versionCode,int android,String device,int displays,boolean playing)
            throws org.json.JSONException{
        return new JSONObject().put("id",id).put("version",version).put("version_code",versionCode).put("android",android)
                .put("device",device).put("displays",displays).put("playing",playing);
    }

    private void send(){
        if(!enabled(settings) || sending || version.isEmpty() || versionCode<=0)return;
        final boolean inGame=playing.getAsBoolean();
        sending=true;
        new Thread(()->{
            try{
                int displays=1;
                android.hardware.display.DisplayManager manager=app.getSystemService(android.hardware.display.DisplayManager.class);
                if(manager!=null)displays=Math.max(1,Math.min(16,manager.getDisplays().length));
                String device=DeviceMatch.detect(app).name().toLowerCase(java.util.Locale.ROOT);
                byte[] bytes=body(installId(),version,versionCode,Build.VERSION.SDK_INT,device,displays,inGame)
                        .toString().getBytes(StandardCharsets.UTF_8);
                HttpURLConnection conn=CrashReports.openInbox(app,"v1/checkin");
                try{
                    conn.setRequestMethod("POST");conn.setDoOutput(true);
                    conn.setRequestProperty("Content-Type","application/json");conn.setFixedLengthStreamingMode(bytes.length);
                    try(OutputStream out=conn.getOutputStream()){out.write(bytes);}
                    conn.getResponseCode();
                }finally{conn.disconnect();}
            }catch(Exception ignored){
                // Silent by design: offline, no network permission (QA package) or an older server.
            }finally{sending=false;}
        },"usage-checkin").start();
    }
}
