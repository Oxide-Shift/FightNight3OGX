package org.oxideshift.corner;

import android.graphics.Bitmap;
import java.io.File;
import java.io.IOException;

/** Original front-end backgrounds for the three career desks, loaded once after import. */
final class CareerArt {
    final Bitmap training,contract,store;
    CareerArt(File game){
        training=read(game,"train","18",256,128);
        contract=read(game,"mcon","93",256,256);
        store=read(game,"fightstore","1",512,256);
    }
    private static Bitmap read(File game,String group,String sprite,int width,int height){
        try{
            int[][] image=TraxArt.screenTextures(game,group,new String[]{sprite},new int[]{width},new int[]{height});
            return Bitmap.createBitmap(image[0],width,height,Bitmap.Config.ARGB_8888);
        }catch(IOException missing){return null;}
    }
}
