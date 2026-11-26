package org.oxideshift.corner;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Recompose the Xbox loading movie around its original, live controller bindings. */
final class ThorLoadingGuide {
    static final int ART_BYTES=512*256;
    private static final int PAPER=0xffffe9cd, GOLD=0xffffbd54, COPPER=0xffffa26c;
    // The native slot names are shared by all six load movies. Black and White in
    // the Xbox pad report are R1 and L1 on Android. The analog triggers are R2 and L2.
    private static final String[] SLOTS={"lanTxt","ranTxt","strTxt","aTxt","bTxt","xTxt","yTxt",
            "whtTxt","blkTxt","l1Txt","r1Txt","dpadTxt","dupTxt","ddownTxt","dleftTxt","drightTxt"};
    private static final String[] CAPS={"L STICK","R STICK","START","A","B","X","Y","L1 BUMPER","R1 BUMPER","L2 TRIGGER","R2 TRIGGER","","UP","DOWN","LEFT","RIGHT"};
    private static final int[] ROW={132,169,359,128,157,186,215,276,304,332,360,240,240,268,296,324};
    private final ByteBuffer movie;
    private final int root,stencil,body,textModel;
    private final List<Integer> characters=new ArrayList<>();

    static byte[] compose(byte[] source,String variant)throws IOException{
        String hash=variant.equals("Training")?"0dae8c5e954ebbc02ffc5e9d2811b19c55fd552c9e7c094246ca28bd19c1bc10":
                variant.equals("PN")?"02dc6ef8f671350b31f88958b1145cf48f6fcc44aa76cf0b6edd9d3632caa0d5":
                "97bc8e341def8d6296781aa7568ae6d5a7daa89fb9d855cb74fde07f51e30486";
        RetailMenu.verify(source,hash);
        return new ThorLoadingGuide(source,variant.equals("Training")).layout();
    }
    /** Upgrade the captions appended by the previous guide without rebuilding its movie. */
    static byte[] upgradeV3(byte[] source,String variant)throws IOException{
        String expected=variant.equals("Training")?"9f84f88484c13b93c23f1bdf7874b05193d05ec82281e7a798c4b16646c67c0e":
                variant.equals("PN")?"9d02389889fe18fd8e2cd895ff7ba5f3154cd52b90b8694f8aa2fdb021bda2e8":
                "ee2d260beb3429fbaaa0fb3e876ab66630f64fbc4f55873df347d799bbe7159d";
        RetailMenu.verify(source,expected);
        ByteBuffer b=ByteBuffer.wrap(source).order(ByteOrder.LITTLE_ENDIAN);
        int root=b.getInt(44),table=b.getInt(root+24);
        for(int i=0;i<b.getInt(root+20);i++){
            int field=b.getInt(table+i*4);
            if(field!=0 && b.getInt(field)==2 && b.getInt(field+32)==0xffc9b6f0)b.putInt(field+32,COPPER);
        }
        return source;
    }
    /** The handheld the guide is drawn for. The art keeps the Thor's control positions, so only the title,
     *  the backdrop and the Thor-only face-letter row change. */
    enum Device {
        THOR("AYN THOR",'T',"thor-guide.dxt3"),DUO("RETROID POCKET DUO",'D',"duo-guide.dxt3"),PHONE("PHONE + CONTROLLER",'P',"phone-guide.dxt3");
        final String title;final char code;final String art;
        Device(String title,char code,String art){this.title=title;this.code=code;this.art=art;}
        static Device random(){Device[] all=values();return all[(int)(Math.random()*all.length)];}
    }
    private static boolean title(String label){
        for(Device d:Device.values())if(d.title.equals(label))return true;
        return false;
    }
    /** Point an installed guide at another device: new title, and the "THOR STYLE" row only on the Thor.
     *  Works on any guide this class composed, so the pristine retail movie is not needed. */
    static byte[] retitle(byte[] source,Device device)throws IOException{
        ByteBuffer b=ByteBuffer.allocate(source.length+256).order(ByteOrder.LITTLE_ENDIAN);
        b.put(source);while((b.position()&3)!=0)b.put((byte)0);
        int root=b.getInt(44),table=b.getInt(root+24),count=b.getInt(root+20);
        if(count<=0 || count>100000 || table<0 || table+4L*count>source.length)throw new IOException("Unrecognized controls guide");
        int heading=-1;
        for(int i=0;i<count;i++){
            int field=b.getInt(table+4*i);
            if(field>0 && field<source.length-56 && b.getInt(field)==2 && title(text(b,field))){heading=i;break;}
        }
        // caption() order: title, FIGHT CONTROLS, ANALOG STICKS, D-PAD, FACE BUTTONS, SHOULDERS, THOR STYLE,
        // XBOX STYLE, then the Thor row's four letters and the Xbox row's four.
        if(heading<0 || heading+15>=count)throw new IOException("Controls guide has no device title");
        int thorRow=b.getInt(table+4*(heading+6)),xboxRow=b.getInt(table+4*(heading+7));
        if(!"XBOX STYLE".equals(text(b,xboxRow)))throw new IOException("Unexpected controls guide layout");
        int titleField=b.getInt(table+4*heading);
        b.putInt(titleField+52,append(b,device.title));
        b.putFloat(titleField+16,300);  // room for the longer names
        boolean thor=device==Device.THOR;
        b.putInt(thorRow+52,append(b,thor?"THOR STYLE":""));
        String letters="XABY";
        for(int i=0;i<4;i++){
            int field=b.getInt(table+4*(heading+8+i));
            b.putInt(field+52,append(b,thor?letters.substring(i,i+1):""));
        }
        return Arrays.copyOf(b.array(),b.position());
    }
    private static String text(ByteBuffer b,int field){
        int start=b.getInt(field+52);if(start<=0 || start>=b.position())return "";
        int end=start;while(end<b.position() && b.get(end)!=0)end++;
        return new String(b.array(),start,end-start,StandardCharsets.US_ASCII);
    }
    private static int append(ByteBuffer b,String value){
        int at=b.position();b.put(value.getBytes(StandardCharsets.US_ASCII)).put((byte)0);
        while((b.position()&3)!=0)b.put((byte)0);
        return at;
    }
    private ThorLoadingGuide(byte[] source,boolean training){
        movie=ByteBuffer.allocate(source.length+16384).order(ByteOrder.LITTLE_ENDIAN);
        movie.put(source);align();
        // These movies export one ActionScript class before the character directory.
        root=movie.getInt(44);
        int table=movie.getInt(root+24);
        for(int i=0;i<movie.getInt(root+20);i++)characters.add(movie.getInt(table+4*i));
        stencil=training?5:104;body=training?40:36;
        textModel=characters.get(training?105:101);
    }
    private void align(){while((movie.position()&3)!=0)movie.put((byte)0);}
    private int string(String value){
        int at=movie.position();movie.put(value.getBytes(StandardCharsets.US_ASCII)).put((byte)0);align();return at;
    }
    private String name(int place){
        int start=movie.getInt(place+52);if(start==0)return "";
        int end=start;while(movie.get(end)!=0)end++;
        return new String(movie.array(),start,end-start,StandardCharsets.US_ASCII);
    }
    private List<Integer> items(int frame){
        List<Integer> result=new ArrayList<>();int offset=movie.getInt(frame+4);
        for(int i=0;i<movie.getInt(frame);i++)result.add(movie.getInt(offset+4*i));
        return result;
    }
    private void setItems(int frame,List<Integer> items){
        movie.putInt(frame,items.size());movie.putInt(frame+4,movie.position());
        for(int item:items)movie.putInt(item);
    }
    private void position(int item,int x,int y){
        movie.putInt(item+4,movie.getInt(item+4)|4);
        movie.putFloat(item+16,1).putFloat(item+20,0).putFloat(item+24,0).putFloat(item+28,1);
        movie.putFloat(item+32,x).putFloat(item+36,y);
    }
    private void style(int field,int font,int size,int color,int width,int height){
        movie.putFloat(field+8,-2).putFloat(field+12,-2).putFloat(field+16,width).putFloat(field+20,height);
        movie.putInt(field+24,font).putInt(field+28,0).putInt(field+32,color).putFloat(field+36,size);
    }
    private void caption(List<Integer> list,String text,int x,int y,int width,int font,int size,int color,int depth){
        int label=string(text),field=movie.position();
        movie.put(movie.array(),textModel,60);style(field,font,size,color,width,size*2);
        movie.putInt(field+52,label);
        int id=characters.size();characters.add(field);
        int place=movie.position();movie.position(place+64);
        movie.putInt(place,3).putInt(place+4,6).putInt(place+8,depth).putInt(place+12,id);
        position(place,x,y);movie.putInt(place+40,-1).putInt(place+56,-1);list.add(place);
    }
    private byte[] layout()throws IOException{
        int stage=movie.getInt(root+12),controller=0;
        for(int item:items(stage)){
            if(movie.getInt(item)!=3)continue;
            String name=name(item);
            int id=movie.getInt(item+12),character=characters.get(id);
            if(name.equals("controller")){controller=character;position(item,0,0);}
            else if(name.equals("configtext")){
                position(item,393,49);style(character,stencil,17,GOLD,135,40);
            }else if(name.equals("LoadImageHolder"))position(item,561,394);
            else if(movie.getInt(character)==2){position(item,630,414);style(character,stencil,18,GOLD,104,40);}
        }
        if(controller==0)throw new IOException("Missing controller clip");
        int first=movie.getInt(controller+12),xbox=first+8;
        // The game initially enters the first platform frame, then selects Xbox.
        // Keep its stop action, so no previous platform's depth survives the switch.
        List<Integer> initial=items(first);
        initial.removeIf(item->movie.getInt(item)==3);
        setItems(first,initial);
        List<Integer> contents=new ArrayList<>(),arrows=new ArrayList<>();
        int seen=0,directions=0;
        for(int item:items(xbox)){
            if(movie.getInt(item)!=3){contents.add(item);continue;}
            String name=name(item);
            if(name.isEmpty())continue; // Old pad silhouette and its permanently printed leader lines.
            int character=characters.get(movie.getInt(item+12));
            if(name.endsWith("DpadMaps")){
                int frame=movie.getInt(character+12);
                setItems(frame,new ArrayList<Integer>());position(item,0,0);
                if(name.equals("multiDpadMaps"))directions=frame;
            }else{
                int i=Arrays.asList(SLOTS).indexOf(name);
                if(i<0)throw new IOException("Unknown Xbox control "+name);
                boolean right=i>=3 && i<=10;
                int x=i>=7 && i<=10?552:right?503:i==11?139:206;
                position(item,x,ROW[i]);style(character,body,15,PAPER,(right?731:431)-x,i<2?36:25);
                seen|=1<<i;
            }
            contents.add(item);
        }
        if(seen!=0xffff || directions==0)throw new IOException("Incomplete Xbox controller layout");
        // The game toggles these two clips when the D-pad changes between movement and punches.
        for(int i=0;i<CAPS.length;i++){
            if(CAPS[i].isEmpty())continue;
            boolean right=i>=3 && i<=10,arrow=i>=12;
            boolean shoulder=i>=7 && i<=10;
            caption(arrow?arrows:contents,CAPS[i],right?449:139,ROW[i],shoulder?98:right?50:77,stencil,shoulder?13:16,right?GOLD:COPPER,40+i);
        }
        setItems(directions,arrows);
        caption(contents,"AYN THOR",139,23,188,stencil,15,COPPER,61);
        caption(contents,"FIGHT CONTROLS",138,40,325,stencil,32,PAPER,62);
        caption(contents,"ANALOG STICKS",139,100,220,stencil,16,GOLD,63);
        caption(contents,"D-PAD",139,205,200,stencil,16,GOLD,64);
        caption(contents,"FACE BUTTONS",446,100,230,stencil,16,GOLD,65);
        caption(contents,"SHOULDERS",446,243,230,stencil,16,GOLD,66);
        caption(contents,"THOR STYLE",139,423,104,stencil,14,COPPER,67);
        caption(contents,"XBOX STYLE",325,423,104,stencil,14,GOLD,68);
        faceLayout(contents,"XABY",252,69);
        faceLayout(contents,"YBAX",438,73);
        caption(contents,"L STICK",512,43,47,stencil,11,COPPER,77);
        caption(contents,"D-PAD",512,66,47,stencil,11,COPPER,78);
        caption(contents,"START",678,26,52,stencil,11,GOLD,79);
        caption(contents,"ABXY",678,48,52,stencil,11,GOLD,80);
        caption(contents,"R STICK",678,65,52,stencil,11,GOLD,81);
        setItems(xbox,contents);
        int table=movie.position();for(int address:characters)movie.putInt(address);
        movie.putInt(root+20,characters.size()).putInt(root+24,table);
        return Arrays.copyOf(movie.array(),movie.position());
    }
    private void faceLayout(List<Integer> items,String clockwise,int center,int depth){
        int[] x={0,16,0,-16},y={407,422,437,422};
        for(int i=0;i<4;i++)
            caption(items,clockwise.substring(i,i+1),center+x[i]-4,y[i],17,stencil,13,PAPER,depth+i);
    }
}
