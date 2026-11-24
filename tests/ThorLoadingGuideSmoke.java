package org.oxideshift.corner;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Run on private copies of a fresh and a previously prepared disc archive. */
public final class ThorLoadingGuideSmoke {
    private static final String[] VARIANTS={"CM","CR","HH","PN","Training","Tutorial"};
    private static byte[] member(Path game,String stem,String extension)throws Exception{
        return TraxArt.textureBank(game.toFile(),"overlay.viv","screengroups/load/"+stem+".big",
                stem+"."+extension,2*1024*1024);
    }
    private static String string(ByteBuffer b,int at){
        if(at==0)return "";int end=at;while(b.get(end)!=0)end++;
        return new String(b.array(),at,end-at,StandardCharsets.US_ASCII);
    }
    private static int[] items(ByteBuffer b,int frame){
        int[] result=new int[b.getInt(frame)];
        for(int i=0;i<result.length;i++)result[i]=b.getInt(b.getInt(frame+4)+4*i);
        return result;
    }
    private static void check(boolean pass,String message){if(!pass)throw new AssertionError(message);}

    private static byte[] selectorBank(Path game,String stem)throws Exception{
        return TraxArt.textureBank(game.toFile(),"custom.viv","controls/"+stem+".big",stem+".xsh",2*1024*1024);
    }

    public static void main(String[] args)throws Exception{
        Path source=Paths.get(args[0]),work=Paths.get(args[1]);
        Path archive=work.resolve("GUI/overlay.viv");Files.createDirectories(archive.getParent());
        Files.copy(source.resolve("GUI/overlay.viv"),archive);
        byte[] artwork=Files.readAllBytes(Paths.get(args[2]));
        RetailMenu.install(work,new byte[OgxBrand.GLYPH_SIZE*OgxBrand.GLYPH_SIZE*2]);
        RetailMenu.installThorGuide(work,artwork);
        String installed=RetailMenu.digest(Files.readAllBytes(archive));
        RetailMenu.installThorGuide(work,artwork);
        check(installed.equals(RetailMenu.digest(Files.readAllBytes(archive))),"Guide reinstalled twice");
        // With a selector fixture, exercise both a fresh install and a second launch.
        if(args.length==5){
            Path custom=archive.resolveSibling("custom.viv");
            Files.copy(Paths.get(args[3]),custom);
            byte[] selector=Files.readAllBytes(Paths.get(args[4]));
            String[] stems={"cstCtrlParts","cstCtrlPartsBe"};
            int[] offsets={2800,8560};
            byte[][] before=new byte[stems.length][];
            for(int i=0;i<stems.length;i++)before[i]=selectorBank(work,stems[i]);
            RetailMenu.installThorSelector(work,selector);
            String digest=RetailMenu.digest(Files.readAllBytes(custom));
            RetailMenu.installThorSelector(work,selector);
            check(digest.equals(RetailMenu.digest(Files.readAllBytes(custom))),"Selector reinstalled twice");
            for(int i=0;i<stems.length;i++){
                System.arraycopy(selector,0,before[i],offsets[i]+16,selector.length);
                check(Arrays.equals(before[i],selectorBank(work,stems[i])),"Unexpected selector bank change in "+stems[i]);
            }
        }
        for(String variant:VARIANTS){
            String stem="lp-"+variant+"-ContConfig";
            check(Arrays.equals(member(source,stem,"const"),member(work,stem,"const")),"Changed preset constants");
            check(Arrays.equals(member(source,stem,"o"),member(work,stem,"o")),"Changed loading geometry");
            ByteBuffer b=ByteBuffer.wrap(member(work,stem,"apt")).order(ByteOrder.LITTLE_ENDIAN);
            int root=b.getInt(44),table=b.getInt(root+24),controller=0;
            for(int item:items(b,b.getInt(root+12)))if(b.getInt(item)==3 && string(b,b.getInt(item+52)).equals("controller"))
                controller=b.getInt(table+4*b.getInt(item+12));
            check(controller!=0,"Missing controller "+variant);
            Set<String> fields=new HashSet<>(),labels=new HashSet<>();
            Map<Integer,String> buttonAt=new HashMap<>();
            Map<String,Integer> actionRow=new HashMap<>();
            Map<String,Integer> shoulderRow=new HashMap<>();
            for(int item:items(b,b.getInt(controller+12)+8)){
                if(b.getInt(item)!=3)continue;
                String name=string(b,b.getInt(item+52));int field=b.getInt(table+4*b.getInt(item+12));
                int x=(int)b.getFloat(item+32),y=(int)b.getFloat(item+36);
                if(!name.isEmpty()){
                    fields.add(name);
                    actionRow.put(name,y);
                }
                else{
                    check(b.getInt(field)==2,"Stock controller or leader lines on "+variant);
                    String label=string(b,b.getInt(field+52));
                    labels.add(label);
                    if(label.length()==1 && y>=400)buttonAt.put(y*1000+x,label);
                    if(label.endsWith("BUMPER") || label.endsWith("TRIGGER"))shoulderRow.put(label,y);
                }
                if(name.equals("multiDpadMaps")){
                    int[] dirs=items(b,b.getInt(field+12));check(dirs.length==4,"Directional prompt count");
                    for(int dir:dirs)labels.add(string(b,b.getInt(b.getInt(table+4*b.getInt(dir+12))+52)));
                }
            }
            check(fields.equals(new HashSet<>(Arrays.asList("lanTxt","ranTxt","strTxt","aTxt","bTxt","xTxt","yTxt",
                    "blkTxt","whtTxt","l1Txt","r1Txt","dpadTxt","dupTxt","ddownTxt","dleftTxt","drightTxt",
                    "singleDpadMaps","multiDpadMaps"))),"Lost live fields on "+variant);
            check(labels.containsAll(Arrays.asList("L STICK","R STICK","START","A","B","X","Y","L1 BUMPER","R1 BUMPER","L2 TRIGGER",
                    "R2 TRIGGER","UP","DOWN","LEFT","RIGHT","FIGHT CONTROLS","THOR STYLE","XBOX STYLE")),"Missing Thor prompts");
            String[] shoulders={"L1 BUMPER","R1 BUMPER","L2 TRIGGER","R2 TRIGGER"};
            String[] xboxSlots={"whtTxt","blkTxt","l1Txt","r1Txt"};
            for(int i=0;i<shoulders.length;i++)
                check(shoulderRow.get(shoulders[i]).equals(actionRow.get(xboxSlots[i])),"Mispaired "+shoulders[i]);
            check(buttonAt.size()==8,"Missing face button legends");
            for(int i=0;i<2;i++){
                int x=i==0?248:434;
                String buttons=buttonAt.get(407000+x)+buttonAt.get(422000+x+16)+
                        buttonAt.get(437000+x)+buttonAt.get(422000+x-16);
                check(buttons.equals(i==0?"XABY":"YBAX"),"Wrong physical face button order");
            }
            ByteBuffer bank=ByteBuffer.wrap(member(work,stem,"xsh")).order(ByteOrder.LITTLE_ENDIAN);
            int at=bank.getInt(20)+16;
            check(Arrays.equals(artwork,Arrays.copyOfRange(bank.array(),at,at+artwork.length)),"Wrong backdrop");
        }
        System.out.println("Verified all six imported loading movies, live fields, Thor labels and idempotent install.");
    }
}
