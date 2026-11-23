package org.oxideshift.corner;

import java.io.*;
import java.nio.*;
import java.util.Arrays;

/** Compose the same title texture for the retail renderer and both cabinet screens. */
final class OgxBrand {
    private static final int WIDTH=256, TITLE_HEIGHT=192, TAG_BYTES=256*64;
    static byte[] tag(InputStream source)throws IOException{
        try(DataInputStream in=new DataInputStream(source)){
            byte[] bytes=new byte[TAG_BYTES];in.readFully(bytes);
            if(in.read()!=-1)throw new IOException("Invalid OGX mark");
            RetailMenu.verify(bytes,"08b475fa8e87f99b3d39dfe575d5f26d5b90fee9085fe7876546ef522a636ecf");
            return bytes;
        }
    }
    static void apply(byte[] bank,int at,byte[] tag)throws IOException{
        if(tag.length!=TAG_BYTES || at<0 || at>bank.length-WIDTH*WIDTH)throw new IOException("Invalid title texture");
        if(Arrays.equals(tag,Arrays.copyOfRange(bank,at+WIDTH*TITLE_HEIGHT,at+WIDTH*WIDTH)))return;
        RetailMenu.verify(Arrays.copyOfRange(bank,at,at+WIDTH*WIDTH),"6916ed16397a4b58d4020ea8d69fb166eff32cf75e1970cb8cf8ef7ba89f14ee");
        // Resample the disc's lettering into the space above the OGX baseline.
        int[] original=TraxArt.bc2(bank,at,WIDTH,WIDTH),block=new int[16],palette=new int[4];
        ByteBuffer dest=ByteBuffer.wrap(bank).order(ByteOrder.LITTLE_ENDIAN);dest.position(at);
        for(int y=0;y<TITLE_HEIGHT;y+=4)for(int x=0;x<WIDTH;x+=4){
            for(int k=0;k<16;k++){
                int sy=(2+16*(y+k/4))/12,weight=(2+16*(y+k/4))%12;
                int column=x+k%4;
                block[k]=blend(original[Math.min(255,sy)*WIDTH+column],original[Math.min(255,sy+1)*WIDTH+column],weight);
            }
            encode(dest,block,palette);
        }
        dest.put(tag);
    }
    private static int blend(int a,int b,int w){
        int out=0;
        for(int shift=0;shift<=24;shift+=8)out|=((((a>>>shift)&255)*(12-w)+((b>>>shift)&255)*w+6)/12)<<shift;
        return out;
    }
    private static int rgb565(int argb){return (argb>>>8&0xf800)|(argb>>>5&0x7e0)|(argb>>>3&31);}
    private static void encode(ByteBuffer out,int[] pixels,int[] palette){
        long alpha=0;int min=766,max=-1,low=0,high=0;
        for(int i=0;i<16;i++){
            int p=pixels[i],opacity=(p>>>24);alpha|=(long)((opacity+8)/17)<<(4*i);
            if(opacity<9)continue;
            int light=(p>>>16&255)+(p>>>8&255)+(p&255);
            if(light<min){min=light;low=rgb565(p);}
            if(light>max){max=light;high=rgb565(p);}
        }
        palette[0]=TraxArt.rgb(high);palette[1]=TraxArt.rgb(low);
        palette[2]=TraxArt.mix(palette[0],palette[1]);palette[3]=TraxArt.mix(palette[1],palette[0]);
        int indices=0;
        for(int i=0;i<16;i++){
            int best=0,distance=Integer.MAX_VALUE,p=pixels[i];
            for(int n=0;n<4;n++){
                int c=palette[n],r=(p>>>16&255)-(c>>>16&255),g=(p>>>8&255)-(c>>>8&255),b=(p&255)-(c&255);
                int error=r*r+g*g+b*b;
                if(error<distance){distance=error;best=n;}
            }
            indices|=best<<(2*i);
        }
        out.putLong(alpha).putShort((short)high).putShort((short)low).putInt(indices);
    }
}
