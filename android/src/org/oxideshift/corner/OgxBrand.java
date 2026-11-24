package org.oxideshift.corner;

import java.io.*;
import java.nio.*;
import java.util.Arrays;

/** Compose the same title texture for the retail renderer and both cabinet screens. */
final class OgxBrand {
    private static final int WIDTH=256, TITLE_HEIGHT=192;
    static final int GLYPH_SIZE=96;
    // Transparent BC2 block. Its unused color bytes identify an already composed title.
    private static final byte[] SIGNATURE={'O','G','X','5','8'};
    private static final String RETAIL="6916ed16397a4b58d4020ea8d69fb166eff32cf75e1970cb8cf8ef7ba89f14ee";
    private static final String FIRST_TAG="0034cb7982c70b079de2ed4a1944d30b3815a6d9658b2b625274413fc2e9d238";
    private static final String SLOPED_TAG="d816799a62d582859b0c7dd894a9e71dccb41dca97cee531a2451e0f3b79de86";

    static void apply(byte[] bank,int at,byte[] glyphs)throws IOException{
        if(at<0 || at>bank.length-WIDTH*WIDTH || glyphs.length!=2*GLYPH_SIZE*GLYPH_SIZE)
            throw new IOException("Invalid title texture");
        int end=at+WIDTH*WIDTH;
        if(Arrays.equals(Arrays.copyOfRange(bank,end-SIGNATURE.length,end),SIGNATURE))return;
        String digest=RetailMenu.digest(Arrays.copyOfRange(bank,at,end));
        if(RETAIL.equals(digest)){
            fitTitle(bank,at);
        }else if(SLOPED_TAG.equals(digest)){
            // Alpha 56 placed ink in five block rows of the fitted title's empty right margin.
            int[] firstEmptyBlock={172,148,136,104,92};
            for(int row=0;row<firstEmptyBlock.length;row++){
                int start=at+(172+row*4)*WIDTH;
                Arrays.fill(bank,start+firstEmptyBlock[row]*4,start+4*WIDTH,(byte)0);
            }
        }else if(!FIRST_TAG.equals(digest)){
            throw new IOException("Unrecognized title texture");
        }
        Arrays.fill(bank,at+WIDTH*TITLE_HEIGHT,end,(byte)0);
        int[] title=TraxArt.bc2(bank,at,WIDTH,WIDTH);
        int[] front=new int[WIDTH*WIDTH],shadow=new int[WIDTH*WIDTH];
        drawMark(title,glyphs,front,shadow);
        int[] block=new int[16],palette=new int[4];
        ByteBuffer dest=ByteBuffer.wrap(bank).order(ByteOrder.LITTLE_ENDIAN);
        for(int y=112;y<WIDTH;y+=4)for(int x=0;x<WIDTH;x+=4){
            boolean modified=false;
            for(int k=0;k<16;k++){
                int i=(y+k/4)*WIDTH+x+k%4;
                int color=title[i];
                if(shadow[i]!=0)color=over(color,shadow[i]<<24);
                if(front[i]!=0)color=over(front[i],color);
                block[k]=color;
                modified|=color!=title[i];
            }
            if(modified){dest.position(at+y*WIDTH+x*4);encode(dest,block,palette);}
        }
        System.arraycopy(SIGNATURE,0,bank,end-SIGNATURE.length,SIGNATURE.length);
    }

    private static void drawMark(int[] title,byte[] glyphs,int[] front,int[] shadow){
        for(int x=38;x<=190;x++){
            for(int y=168;y<WIDTH;y++){
                double top=199-(x-39)*0.181, height=58-(x-39)*0.087;
                double v=(y+.5-top)/height;
                if(v<0 || v>=1)continue;
                int alpha=coverage(title,glyphs,x+.5,v);
                if(alpha==0)continue;
                front[y*WIDTH+x]=alpha<<24 | gold(x,v);
                // Project the silhouette toward the same rear corner as the NIGHT row.
                for(int step=0;step<=53;step++){
                    int rear=(y-step)*WIDTH+x-(step*36+26)/53;
                    shadow[rear]=Math.max(shadow[rear],alpha);
                }
            }
        }
    }

    static void applyPause(byte[] bank,int at,byte[] main,int mainAt,byte[] glyphs)throws IOException{
        final int width=512,height=128;
        if(at<0 || at>bank.length-width*height)throw new IOException("Invalid pause banner");
        RetailMenu.verify(Arrays.copyOfRange(bank,at,at+width*height),
                "ca7c5c53bc4784f5b7190e2ec511ded3b20909beb0e5f84183aa5de4ff1c5870");
        int[] title=TraxArt.bc2(main,mainAt,WIDTH,WIDTH);
        int[] front=new int[WIDTH*WIDTH],shadow=new int[WIDTH*WIDTH];
        drawMark(title,glyphs,front,shadow);
        int[] banner=TraxArt.bc2(bank,at,width,height),block=new int[16],palette=new int[4];
        ByteBuffer dest=ByteBuffer.wrap(bank).order(ByteOrder.LITTLE_ENDIAN);
        // The transparent band alongside the title sits above the pause-menu text.
        for(int y=0;y<64;y+=4)for(int x=172;x<256;x+=4){
            boolean changed=false;
            for(int k=0;k<16;k++){
                int dx=x+k%4,dy=y+k/4,color=banner[dy*width+dx];
                int sx=(dx-172)*7/3,sy=110+dy*7/3;
                if(sy>=WIDTH){block[k]=color;continue;}
                int index=sy*WIDTH+sx;
                int shaded=shadow[index]==0?color:over(color,shadow[index]<<24);
                block[k]=front[index]==0?shaded:over(front[index],shaded);
                changed|=block[k]!=color;
            }
            if(changed){dest.position(at+y*width+x*4);encode(dest,block,palette);}
        }
    }

    private static int coverage(int[] title,byte[] masks,double x,double v){
        if(x>=87 && x<132){
            // Sample the printed G itself, including its squared crossbar, spur and stencil bridges.
            double sx=128+(x-87)*44/45;
            double top=114.5-(sx-130)*.04, bottom=176.5-(sx-130)*.18;
            double sy=top+v*(bottom-top);
            int ix=(int)sx,iy=(int)sy,p=iy*WIDTH+ix;
            return bilinear(ink(title[p]),ink(title[p+1]),ink(title[p+WIDTH]),ink(title[p+WIDTH+1]),sx-ix,sy-iy);
        }
        boolean xGlyph=x>=137 && x<190;
        if(!xGlyph && (x<39 || x>=82))return 0;
        double u=xGlyph?(x-137)/53:(x-39)/43;
        double sx=1+u*(GLYPH_SIZE-2)-.5,sy=1+v*(GLYPH_SIZE-2)-.5;
        int ix=(int)sx,iy=(int)sy,p=(xGlyph?GLYPH_SIZE*GLYPH_SIZE:0)+iy*GLYPH_SIZE+ix;
        return bilinear(masks[p]&255,masks[p+1]&255,masks[p+GLYPH_SIZE]&255,masks[p+GLYPH_SIZE+1]&255,sx-ix,sy-iy);
    }
    private static int ink(int pixel){
        return (pixel>>>24)*(pixel>>>16&255)/255;
    }
    private static int bilinear(int a,int b,int c,int d,double x,double y){
        return (int)Math.round((a*(1-x)+b*x)*(1-y)+(c*(1-x)+d*x)*y);
    }
    private static int gold(int x,double v){
        // The title's light peaks over the center and falls into amber down the right-hand strokes.
        double left=Math.max(0,(94-x)/55.0),right=Math.max(0,(x-94)/96.0);
        int r=(int)Math.round(255-22*right*right*(.35+.65*v));
        int g=(int)Math.round(199-14*left-45*right-27*v);
        int b=(int)Math.round(24-8*Math.max(right,v));
        return r<<16|g<<8|b;
    }
    private static int over(int foreground,int background){
        int fa=foreground>>>24,ba=background>>>24;
        if(fa==255 || ba==0)return foreground;
        if(fa==0)return background;
        int backWeight=ba*(255-fa),weight=fa*255+backWeight;
        int pixel=((weight+127)/255)<<24;
        for(int shift=0;shift<=16;shift+=8){
            int f=foreground>>>shift&255,b=background>>>shift&255;
            pixel|=((f*fa*255+b*backWeight+weight/2)/weight)<<shift;
        }
        return pixel;
    }
    private static void fitTitle(byte[] bank,int at){
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
