package org.oxideshift.corner;

import android.util.AtomicFile;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** One process-wide owner under app files/, serialized off the UI thread.
 * This file is disposable diagnostic state, never authority to delete assets. */
final class BootJournal {
    private final AtomicFile file;
    BootJournal(File filesDir) { file = new AtomicFile(new File(filesDir, "launch-attempt.json")); }

    synchronized void record(String run, String kit, String mode, String stage,
                             boolean retryUsed, boolean userDataTouched) throws IOException, JSONException {
        JSONObject row = new JSONObject().put("v", 1).put("run", run).put("kit", kit)
                .put("mode", mode).put("stage", stage).put("retryUsed", retryUsed)
                .put("userDataTouched", userDataTouched);
        byte[] data = row.toString().getBytes(StandardCharsets.UTF_8);
        FileOutputStream stream = file.startWrite();
        try {
            stream.write(data);
            file.finishWrite(stream);
        } catch (IOException | RuntimeException e) {
            file.failWrite(stream);
            throw e;
        }
    }

    synchronized JSONObject previous() throws IOException {
        try {
            byte[] data = file.readFully();
            JSONObject row = new JSONObject(new String(data, StandardCharsets.UTF_8));
            if (row.optInt("v") == 1 && row.has("run") && row.has("stage") &&
                    row.has("retryUsed") && row.has("userDataTouched")) return row;
            return null;
        } catch (FileNotFoundException | JSONException e) {
            return null;
        }
    }
}

