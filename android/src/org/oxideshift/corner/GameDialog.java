package org.oxideshift.corner;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/** The dialog window and its recycled list rows live outside the launcher tree. */
final class GameDialog extends AlertDialog.Builder {
    private final Activity owner;
    GameDialog(Activity owner){super(owner);this.owner=owner;}
    @Override public AlertDialog create(){
        AlertDialog dialog=super.create();
        View decor=dialog.getWindow().getDecorView();
        decor.getViewTreeObserver().addOnGlobalLayoutListener(()->{
            Typeface face=owner instanceof CornerActivity?((CornerActivity)owner).customFace():DiscFonts.FALLBACK.stencil;
            apply(decor,face);
        });
        return dialog;
    }
    private static void apply(View view,Typeface face){
        if(view instanceof TextView){
            TextView text=(TextView)view;
            if(text.getTypeface()!=face)text.setTypeface(face);
        }
        if(view instanceof ViewGroup){
            ViewGroup group=(ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++)apply(group.getChildAt(i),face);
        }
    }
}
