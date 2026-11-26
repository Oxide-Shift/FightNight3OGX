package org.oxideshift.corner;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/** Shell-only private build entry point. Exercise the same actions as the launcher button. */
public final class UsbStartupCheck extends CornerActivity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private String runId;
    private boolean started, waitingForBuild;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        runId = getIntent().getStringExtra("run_id");
        if (runId == null || !runId.matches("[0-9a-f]{32}")) {
            finish();
            return;
        }
        if (state != null) {
            started = state.getBoolean("usbStarted");
            waitingForBuild = state.getBoolean("usbBuilding");
        }
    }

    @Override protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        String id = intent.getStringExtra("run_id");
        if (id == null || !id.matches("[0-9a-f]{32}") || id.equals(runId)) return;
        setIntent(intent);
        runId = id;
        // A new shell invocation may attach to the same foreground Activity.
        // Keep an existing compiler job and acknowledge it under the new run ID.
        if (waitingForBuild) Log.i("FightNightUsbQA", runId + " PREPARING");
        else started = false;
    }

    @Override protected void onPostResume() {
        super.onPostResume();
        if (isFinishing()) return;
        if (waitingForBuild) {
            handler.removeCallbacks(checkBuild);
            handler.post(checkBuild);
        } else if (!started) {
            started = true;
            dispatch();
        }
    }

    private void dispatch() {
        LaunchAction action = launch();
        Log.i("FightNightUsbQA", runId + " " + action.name());
        if (action == LaunchAction.PREPARING) {
            waitingForBuild = true;
            handler.postDelayed(checkBuild, 500);
        }
    }

    private final Runnable checkBuild = new Runnable() {
        @Override public void run() {
            if (preparingImport()) {
                handler.postDelayed(this, 500);
                return;
            }
            waitingForBuild = false;
            if (needsGamePreparation()) {
                Log.i("FightNightUsbQA", runId + " PREPARE_FAILED");
                return;
            }
            dispatch();
        }
    };

    @Override protected void onPause() {
        handler.removeCallbacks(checkBuild);
        super.onPause();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putBoolean("usbStarted", started);
        state.putBoolean("usbBuilding", waitingForBuild);
    }

    @Override protected void onDestroy() {
        handler.removeCallbacks(checkBuild);
        super.onDestroy();
    }
}
