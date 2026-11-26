package org.oxideshift.corner;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import java.io.File;
import java.io.IOException;

/** The lower half of the selected fight bill. All photo plates come from the player's disc. */
final class RootIllustration extends View {
    static Bitmap[] load(File game,Bitmap[] training,Bitmap[] mainMenu){
        Bitmap[] plates=new Bitmap[9];
        plates[0]=read(game,"mainmenu","24",512,256);
        plates[1]=read(game,"rivals","2",256,128);
        plates[2]=read(game,"cac","141",128,128);
        plates[3]=training!=null && training.length>4?training[4]:null;
        plates[4]=read(game,"cac","144",128,128);
        plates[5]=read(game,"trophycase","9",128,128);
        plates[7]=read(game,"options","62",128,128);
        plates[8]=mainMenu!=null && mainMenu.length>0?mainMenu[0]:null;
        return plates;
    }
    private static Bitmap read(File game,String group,String id,int w,int h){
        try{
            int[][] pixels=TraxArt.screenTextures(game,group,new String[]{id},new int[]{w},new int[]{h});
            return Bitmap.createBitmap(pixels[0],w,h,Bitmap.Config.ARGB_8888);
        }catch(IOException unavailable){return null;}
    }
    private Bitmap[] plates;
    private int row=-1;
    private final Plate plate=new Plate();
    RootIllustration(Context c){
        super(c);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);setVisibility(GONE);
    }
    void setPlates(Bitmap[] value){if(plates!=value){plates=value;invalidate();}}
    void select(MenuContext.Page page){
        int next=-1;
        for(int i=0;i<8;i++)if(page!=null && MenuContext.root(i)==page){next=i;break;}
        if(row==next)return;
        row=next;setVisibility(next<0?GONE:VISIBLE);invalidate();
    }
    @Override protected void onDraw(Canvas canvas){
        if(row<0)return;
        Bitmap image=plates==null?null:plates[row];
        plate.draw(canvas,getWidth(),getHeight(),row,image,plates==null?null:plates[8]);
    }

    static final class Plate {
        private static final int[] INKS={0xff974021,0xff76553b,0xff86422a,0xff9f3426,0xff663e30,0xff866735,0xff8b3827,0xff4e5b54};
        private final Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        private final Path field=new Path();
        private final RectF position=new RectF();
        void draw(Canvas c,int w,int h,int row,Bitmap image,Bitmap ring){
            if(w<=0 || h<=0 || row<0 || row>=INKS.length || (image==null && row!=6))return;
            c.save();c.clipRect(0,0,w,h);
            // The unprinted margin above the cut keeps the note and illustration distinct.
            field.reset();field.moveTo(0,h*.18f);field.lineTo(w,h*.035f);field.lineTo(w,h);field.lineTo(0,h);field.close();
            c.clipPath(field);c.drawColor(INKS[row]);
            if(ring!=null){
                float scale=Math.max(w/(float)ring.getWidth(),h/(float)ring.getHeight());
                float rw=ring.getWidth()*scale,rh=ring.getHeight()*scale;
                position.set((w-rw)*.5f,(h-rh)*.5f,(w+rw)*.5f,(h+rh)*.5f);
                ink.setColor(0x85ffffff);c.drawBitmap(ring,null,position,ink);
            }
            ink.setColor(0x39261916);
            for(int i=0;i<3;i++){
                float y=h*(.48f+.17f*i);c.drawRect(0,y,w,y+Math.max(1,h*.013f),ink);
            }
            if(row==6){
                float cx=w*.66f,cy=h*.64f,r=Math.min(w*.42f,h*.61f);
                ink.setColor(0xff191917);c.drawCircle(cx,cy,r,ink);
                ink.setStyle(Paint.Style.STROKE);ink.setStrokeWidth(Math.max(1,r*.007f));
                ink.setColor(0xff55504a);
                for(int i=0;i<13;i++)c.drawCircle(cx,cy,r*(.40f+i*.046f),ink);
                ink.setStyle(Paint.Style.FILL);ink.setColor(0xffdfb86f);c.drawCircle(cx,cy,r*.31f,ink);
                ink.setColor(0xff211d18);c.drawCircle(cx,cy,Math.max(1,r*.037f),ink);
            }else{
                float scale=Math.min(w*(row<2?.98f:.87f)/image.getWidth(),h*(row<2?1.1f:.82f)/image.getHeight());
                float iw=image.getWidth()*scale,ih=image.getHeight()*scale;
                float x=(w-iw)*(row<2?.5f:.66f);
                float bottom=row<2?h:h*.96f;
                position.set(x,bottom-ih,x+iw,bottom);ink.setColor(0xffffffff);c.drawBitmap(image,null,position,ink);
            }
            c.restore();
        }
    }
}
