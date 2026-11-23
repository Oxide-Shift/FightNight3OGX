package org.oxideshift.corner;

import android.graphics.Typeface;
import java.io.File;

/** The two Xbox UI fonts are available after the player's disc import. */
final class DiscFonts {
    static final DiscFonts FALLBACK = new DiscFonts(
            Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC),
            Typeface.create("sans-serif-condensed", Typeface.NORMAL),null,null);
    final Typeface title,body,stencil,brush;

    private DiscFonts(Typeface title,Typeface body,Typeface stencil,Typeface brush) {
        this.title = title;
        this.body = body;
        this.stencil=stencil==null?title:stencil;
        this.brush=brush==null?this.stencil:brush;
    }

    static DiscFonts load(File game) {
        return new DiscFonts(read(game, "05_title.ttf", FALLBACK.title),
                read(game, "05_body.ttf", FALLBACK.body),
                read(game,"gunplay.ttf",null),read(game,"eraser.ttf",null));
    }

    static File stencilFile(File game) {
        for (String name : new String[]{"gunplay.ttf","05_title.ttf"}) {
            for (String folder : FOLDERS) {
                File file=new File(new File(game,folder),name);
                if(file.isFile() && file.length()>0 && file.length()<=512*1024)return file;
            }
        }
        return null;
    }
    private static final String[] FOLDERS={"GUI", "gui", "FEAPT", "feapt"};

    private static Typeface read(File game, String name, Typeface fallback) {
        for (String folder : FOLDERS) {
            File file = new File(new File(game, folder), name);
            if (!file.isFile()) continue;
            try {
                return Typeface.createFromFile(file);
            } catch (RuntimeException ignored) {
                // Try the copy used by the other front-end package.
            }
        }
        return fallback;
    }
}
