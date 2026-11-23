package org.oxideshift.corner;

import java.nio.ByteBuffer;

/** A clean lower strip from the same retail menu, before EA Trax covers it. */
final class MenuStrip {
    final int width, height, firstRow, rows;
    final ByteBuffer pixels;
    private final int[] anchors=new int[15];

    MenuStrip(LiveFrame frame){
        width=frame.width;
        height=frame.height;
        firstRow=height*380/480;
        rows=(height*449+479)/480-firstRow;
        ByteBuffer source=frame.pixels.duplicate();
        source.position(8+4*width*firstRow);
        source.limit(source.position()+4*width*rows);
        pixels=ByteBuffer.allocateDirect(source.remaining());
        pixels.put(source).flip();
        for(int i=0;i<anchors.length;i++)anchors[i]=anchor(frame,i);
    }
    private static int anchor(LiveFrame f,int i){
        return f.at(75+(i%5)*123,82+(i/5)*129);
    }
    boolean matches(LiveFrame frame){
        if(frame==null || width!=frame.width || height!=frame.height)return false;
        int similar=0;
        for(int i=0;i<anchors.length;i++){
            int a=anchors[i],b=anchor(frame,i);
            int diff=Math.abs(LiveFrame.red(a)-LiveFrame.red(b))+
                Math.abs(LiveFrame.green(a)-LiveFrame.green(b))+
                Math.abs(LiveFrame.blue(a)-LiveFrame.blue(b));
            if(diff<55)similar++;
        }
        return similar>=12;
    }
}
