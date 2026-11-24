package org.oxideshift.corner;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import java.io.IOException;
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

    /** Rasterize the O and X outlines from the imported stencil font at a common scale. */
    static byte[] ogxStencil(File game)throws IOException {
        Typeface face=read(game,"gunplay.ttf",null);
        if(face==null)throw new IOException("Missing Gunplay stencil font");
        int size=OgxBrand.GLYPH_SIZE;
        byte[] masks=new byte[2*size*size];
        Bitmap bitmap=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);
        try{
            Canvas canvas=new Canvas(bitmap);
            Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(0xffffffff);paint.setTypeface(face);paint.setTextSize(128);
            int[] pixels=new int[size*size];
            for(int i=0;i<2;i++){
                Path glyph=new Path();
                paint.getTextPath("OX",i,i+1,0,0,glyph);
                RectF bounds=new RectF();
                glyph.computeBounds(bounds,true);
                if(bounds.isEmpty())throw new IOException("Missing OGX glyph");
                Matrix fit=new Matrix();
                fit.setRectToRect(bounds,new RectF(1,1,size-1,size-1),Matrix.ScaleToFit.FILL);
                glyph.transform(fit);
                bitmap.eraseColor(0);canvas.drawPath(glyph,paint);
                bitmap.getPixels(pixels,0,size,0,0,size,size);
                for(int p=0;p<pixels.length;p++)masks[i*pixels.length+p]=(byte)(pixels[p]>>>24);
            }
        }finally{bitmap.recycle();}
        return masks;
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
