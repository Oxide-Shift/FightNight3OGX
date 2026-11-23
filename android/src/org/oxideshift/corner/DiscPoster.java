package org.oxideshift.corner;

import java.io.*;
import java.nio.*;

/** Read the two indexed Xbox texture tiles in the retail pre-fight illustration. */
final class DiscPoster {
    static final int WIDTH=640, HEIGHT=480;
    static int[] read(File game) throws IOException {
        return read(game,"xpreload.bin");
    }
    static int[] read(File game,String name) throws IOException {
        File art=new File(game,"FE/ART/"+name);
        long size=art.length();
        if(size<1024 || size>512*1024)throw new IOException("Unavailable disc poster");
        byte[] bytes=new byte[(int)size];
        try(DataInputStream in=new DataInputStream(new FileInputStream(art))){in.readFully(bytes);}
        ByteBuffer data=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        if(data.getInt(0)!=0x58504853 || data.getInt(4)!=size || data.getInt(8)<2)
            throw new IOException("Unknown poster format");
        int[] pixels=new int[WIDTH*HEIGHT];
        tile(data,data.getInt(20),512,0,pixels);
        tile(data,data.getInt(28),128,512,pixels);
        return pixels;
    }
    private static void tile(ByteBuffer bytes,int base,int width,int left,int[] dest) throws IOException {
        if(base<32 || base>bytes.capacity()-16)throw new IOException("Invalid poster tile");
        int descriptor=bytes.getInt(base), palette=base+(descriptor>>>8);
        if((descriptor&255)!=0x7b || bytes.getShort(base+4)!=width || bytes.getShort(base+6)!=512 ||
           palette<base+16+width*512 || palette>bytes.capacity()-1040 || bytes.get(palette)!=0x2a)
            throw new IOException("Invalid indexed texture");
        int[] colors=new int[256], column=new int[width], row=new int[HEIGHT];
        for(int i=0;i<256;i++)colors[i]=0xff000000|(bytes.getInt(palette+16+4*i)&0xffffff);
        // Rectangular Morton order interleaves only dimensions with a bit remaining.
        int outBit=1;
        for(int bit=1;bit<512;bit<<=1){
            if(bit<width){for(int x=0;x<width;x++)if((x&bit)!=0)column[x]|=outBit;outBit<<=1;}
            for(int y=0;y<HEIGHT;y++)if((y&bit)!=0)row[y]|=outBit;
            outBit<<=1;
        }
        for(int y=0;y<HEIGHT;y++)for(int x=0;x<width;x++)
            dest[y*WIDTH+left+x]=colors[bytes.get(base+16+(column[x]|row[y]))&255];
    }
}
