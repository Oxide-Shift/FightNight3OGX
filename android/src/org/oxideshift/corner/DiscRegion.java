package org.oxideshift.corner;

import android.content.Context;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/** Which release of the disc is installed, and which signed build kit prepares it. */
final class DiscRegion {
    /** Europe/PAL release EA13401E (English/French). The US executable is DiscImage.HASH. */
    static final String PAL_HASH = "349ba807b93bd4ddef251b0129ef7970b5da1840ef794b4a2fa735e90c58394a";
    static final int PAL_BYTES = 5705728;
    static final String US_KIT = "disc-kit", PAL_KIT = "disc-kit-pal";

    private DiscRegion() {}

    /** The installed executables differ in size, so the length alone tells the two supported releases apart. */
    static boolean pal(File xbe) {
        return xbe != null && xbe.length() == PAL_BYTES;
    }

    /** Asset name (without .zip/.id) of the kit for this installed executable. */
    static String kit(File xbe, boolean palBundled) {
        return palBundled && pal(xbe) ? PAL_KIT : US_KIT;
    }

    static boolean palBundled(Context context) {
        try (InputStream in = context.getAssets().open(PAL_KIT + ".id")) {
            return in.read() >= 0;
        } catch (IOException e) {
            return false;
        }
    }
}
