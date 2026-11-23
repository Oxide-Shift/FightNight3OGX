package org.oxideshift.corner;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** The five BC2 sprites of the original stdTrax widget, read from the imported disc. */
final class TraxArt {
    static final int[] WIDTH={64,8,16,64,8};
    static final String[] IDS={"1","10","13","16","3"};
    static int[][] load(File game) throws IOException {
        byte[] xsh=textureBank(game,"custom.viv","controls/stdTrax.big","stdTrax.xsh",262144);
        if(xsh.length<56 || le(xsh,0)!=0x58504853 || le(xsh,8)!=5)throw invalid();
        int[][] result=new int[5][];
        for(int i=0;i<5;i++){
            if(!new String(xsh,16+i*8,4,StandardCharsets.US_ASCII).trim().equals(IDS[i]))throw invalid();
            int p=le(xsh,20+i*8),w=WIDTH[i];
            if(p<56 || p>xsh.length-16-w*64 || xsh[p]!=0x61 || u16(xsh,p+4)!=w || u16(xsh,p+6)!=64)throw invalid();
            result[i]=bc2(xsh,p+16,w,64);
        }
        return result;
    }
    static final int[] CAMP_WIDTH={256,128,128,128,128};
    static int[][] training(File game)throws IOException{
        return screenTextures(game,"train",new String[]{"58","74","72","76","78"},CAMP_WIDTH,CAMP_WIDTH);
    }
    static final int[] MENU_WIDTH={512,256,256}, MENU_HEIGHT={256,256,256};
    static int[][] mainMenu(File game)throws IOException{
        return screenTextures(game,"mainmenu",new String[]{"1","16","26"},MENU_WIDTH,MENU_HEIGHT);
    }
    static int[][] screenTextures(File game,String screen,String[] ids,int[] widths,int[] heights)throws IOException{
        byte[] xsh=textureBank(game,"overlay.viv","screengroups/"+screen+"/"+screen+".big",screen+".xsh",2*1024*1024);
        int[][] result=new int[ids.length][];
        if(xsh.length<16 || le(xsh,0)!=0x58504853)throw invalid();
        int n=le(xsh,8);if(n<=0 || n>256 || xsh.length<16+n*8)throw invalid();
        for(int i=0;i<n;i++){
            String id=new String(xsh,16+i*8,4,StandardCharsets.US_ASCII).trim();
            for(int j=0;j<ids.length;j++)if(id.equals(ids[j])){
                int off=le(xsh,20+i*8),w=widths[j],h=heights[j];
                if(off<16+n*8 || off>xsh.length-16-w*h || xsh[off]!=0x61 ||
                        u16(xsh,off+4)!=w || u16(xsh,off+6)!=h)throw invalid();
                result[j]=bc2(xsh,off+16,w,h);
            }
        }
        for(int[] image:result)if(image==null)throw invalid();
        return result;
    }
    private static byte[] textureBank(File game,String archiveName,String group,String bank,int limit)throws IOException{
        File archive=new File(game,"GUI/"+archiveName);
        if(!archive.isFile())archive=new File(game,"gui/"+archiveName);
        byte[] widget;
        try(RandomAccessFile f=new RandomAccessFile(archive,"r")){
            byte[] header=new byte[(int)Math.min(f.length(),32768)];f.readFully(header);
            int[] entry=find(header,f.length(),group,limit);
            f.seek(entry[0]);widget=new byte[entry[1]];f.readFully(widget);
        }
        widget=inflate(widget,limit);int[] entry=find(widget,widget.length,bank,limit);
        return inflate(Arrays.copyOfRange(widget,entry[0],entry[0]+entry[1]),limit);
    }
    private static IOException invalid(){return new IOException("Invalid Trax artwork");}
    private static int u16(byte[] b,int i){return (b[i]&255)|(b[i+1]&255)<<8;}
    private static int le(byte[] b,int i){return u16(b,i)|u16(b,i+2)<<16;}
    private static int be(byte[] b,int i){return Integer.reverseBytes(le(b,i));}
    private static int[] find(byte[] b,long length,String name,int limit)throws IOException{
        if(b.length<16 || b[0]!='B'||b[1]!='I'||b[2]!='G')throw invalid();
        int count=be(b,8),p=16;
        for(int i=0;i<count && p<=b.length-9;i++){
            int off=be(b,p),size=be(b,p+4),start=p+8;p=start;
            while(p<b.length && b[p]!=0)p++;
            if(p==b.length)break;
            String key=new String(b,start,p-start,StandardCharsets.US_ASCII);p++;
            if(key.equalsIgnoreCase(name)||key.toLowerCase(java.util.Locale.ROOT).endsWith("/"+name.toLowerCase(java.util.Locale.ROOT))){
                if(off<0 || size<=0 || size>limit || (long)off+size>length)throw invalid();
                return new int[]{off,size};
            }
        }
        throw invalid();
    }
    static byte[] inflate(byte[] b,int limit)throws IOException{
        if(b.length<5 || (b[0]&255)!=16 || (b[1]&255)!=251)return b;
        int size=(b[2]&255)<<16|(b[3]&255)<<8|(b[4]&255);
        if(size<=0 || size>limit)throw invalid();
        byte[] out=new byte[size];int i=5,j=0;
        while(i<b.length){
            int a=b[i++]&255,p,n=0,back=0;
            if(a<128){if(i>=b.length)throw invalid();p=a&3;n=((a>>2)&7)+3;back=((a&96)<<3)+(b[i++]&255)+1;}
            else if(a<192){if(i+2>b.length)throw invalid();int c=b[i++]&255;p=c>>6;n=(a&63)+4;back=((c&63)<<8)+(b[i++]&255)+1;}
            else if(a<224){if(i+3>b.length)throw invalid();p=a&3;back=((a&16)<<12)+((b[i++]&255)<<8)+(b[i++]&255)+1;n=((a&12)<<6)+(b[i++]&255)+5;}
            else if(a<252)p=((a&31)<<2)+4;
            else p=a&3;
            if(i+p>b.length || j+p+n>size)throw invalid();
            System.arraycopy(b,i,out,j,p);i+=p;j+=p;
            if(n>0 && back>j)throw invalid();
            for(int k=0;k<n;k++){out[j]=out[j-back];j++;}
            if(a>=252 && j==size)return out;
        }
        throw invalid();
    }
    private static int rgb(int v){
        int r=(v>>11)&31,g=(v>>5)&63,b=v&31;
        return ((r<<3|r>>2)<<16)|((g<<2|g>>4)<<8)|(b<<3|b>>2);
    }
    private static int mix(int a,int b){
        int r=((a>>16)*2+(b>>16))/3,g=(((a>>8)&255)*2+((b>>8)&255))/3;
        return r<<16|g<<8|((a&255)*2+(b&255))/3;
    }
    private static int[] bc2(byte[] bytes,int p,int width,int height){
        int[] out=new int[width*height],colors=new int[4];
        for(int y=0;y<height;y+=4)for(int x=0;x<width;x+=4,p+=16){
            colors[0]=rgb(u16(bytes,p+8));colors[1]=rgb(u16(bytes,p+10));
            colors[2]=mix(colors[0],colors[1]);colors[3]=mix(colors[1],colors[0]);
            int selectors=le(bytes,p+12);
            for(int k=0;k<16;k++){
                int alpha=(bytes[p+k/2]>>((k&1)*4))&15;
                out[(y+k/4)*width+x+k%4]=(alpha*17<<24)|colors[(selectors>>>(2*k))&3];
            }
        }
        return out;
    }
}
