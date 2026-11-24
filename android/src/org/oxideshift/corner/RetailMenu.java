package org.oxideshift.corner;

import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.Arrays;

/** Adds a selectable row to the imported Xbox front end. Only the app's extracted copy is edited. */
final class RetailMenu {
    private static final String GROUP="screengroups/mainmenu/mainmenu.big";
    private static final byte[] LEGACY=ascii("FNR3-ACHIEVEMENTS-1"), PRE_BRAND=ascii("FNR3-ACHIEVEMENTS-2"), FIRST_BRAND=ascii("FNR3-ACHIEVEMENTS-3"), SLOPED_BRAND=ascii("FNR3-ACHIEVEMENTS-4"), PRE_PAUSE=ascii("FNR3-ACHIEVEMENTS-5"), VERSION=ascii("FNR3-ACHIEVEMENTS-6");
    private static final int ALIGN=128;

    private static byte[] ascii(String s){return s.getBytes(StandardCharsets.US_ASCII);}
    private static ByteBuffer little(byte[] data){return ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);}
    private static int aligned(int n){return (n+ALIGN-1)&-ALIGN;}
    private static int entry(byte[] data,String name)throws IOException{
        ByteBuffer b=ByteBuffer.wrap(data);
        if(data.length<16 || b.getInt(0)!=0x42494746)throw new IOException("Invalid front-end archive");
        int count=b.getInt(8),end=b.getInt(12),p=16;
        if(count<=0 || end>data.length || end<16)throw new IOException("Invalid front-end directory");
        for(int i=0;i<count && p<=end-9;i++){
            int start=p+8,next=start;
            while(next<end && data[next]!=0)next++;
            if(next==end)break;
            if(name.equals(new String(data,start,next-start,StandardCharsets.US_ASCII)))return p;
            p=next+1;
        }
        throw new IOException("Missing "+name);
    }
    private static byte[] member(byte[] data,String name)throws IOException{
        int index=entry(data,name);ByteBuffer b=ByteBuffer.wrap(data);
        int offset=b.getInt(index),size=b.getInt(index+4);
        if(offset<0 || size<1 || (long)offset+size>data.length)throw new IOException("Invalid "+name);
        return Arrays.copyOfRange(data,offset,offset+size);
    }
    // Preserve each other member's sector alignment, including the Xbox texture bank.
    private static byte[] replace(byte[] data,String name,byte[] replacement)throws IOException{
        int e=entry(data,name);ByteBuffer src=ByteBuffer.wrap(data);
        int offset=src.getInt(e),oldSize=src.getInt(e+4);
        if(offset<0 || oldSize<1 || (long)offset+oldSize>data.length)throw new IOException("Invalid "+name);
        int growth=aligned(Math.max(0,replacement.length-oldSize));
        byte[] out=Arrays.copyOf(data,data.length+growth);
        System.arraycopy(data,offset+oldSize,out,offset+oldSize+growth,data.length-offset-oldSize);
        System.arraycopy(replacement,0,out,offset,replacement.length);
        ByteBuffer b=ByteBuffer.wrap(out);little(out).putInt(4,out.length);
        b.putInt(e+4,replacement.length);
        for(int i=0,p=16;i<src.getInt(8);i++){
            if(src.getInt(p)>offset)b.putInt(p,src.getInt(p)+growth);
            p+=8;while(data[p++]!=0){}
        }
        return out;
    }
    static String digest(byte[] data){
        try{
            byte[] sum=MessageDigest.getInstance("SHA-256").digest(data);
            StringBuilder hex=new StringBuilder(64);
            for(byte b:sum){hex.append(Character.forDigit((b>>>4)&15,16));hex.append(Character.forDigit(b&15,16));}
            return hex.toString();
        }catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
    }
    static void verify(byte[] data,String expected)throws IOException{
        if(!digest(data).equals(expected))throw new IOException("Unrecognized disc asset revision");
    }
    private static byte[] constants(byte[] data,boolean legacy)throws IOException{
        verify(data,legacy?"0dfcda4b1bffbe8fea0ffc2db3e0a2d3e25b716aa61dc5642285d04ebc91e00a":"dc26cee7d1afbc8f0016551c7c582eed21f068415f1c93801cda548ac3e9602a");
        ByteArrayOutputStream out=new ByteArrayOutputStream();out.write(data);
        ByteBuffer table=little(data);
        for(int slot:new int[]{278,281,282,310,311,312}){
            int pointer=table.getInt(28)+slot*8+4,start=table.getInt(pointer),end=start;
            while(data[end]!=0)end++;
            String original=new String(data,start,end-start,StandardCharsets.US_ASCII);
            int split=original.lastIndexOf(',');
            table.putInt(pointer,out.size());
            // The last two entries are diagnostic strings for Xenon-only sign-in prompts.
            String value=slot==310?"m_sCurrentScreenId":slot==311?"fnr3/achievements":slot==312?"fnr3/music":
                    original.substring(0,split)+(slot==278?(legacy?",MUSIC":",ACHIEVEMENTS,MUSIC"):(legacy?",~DNA~":",~DNA~,~DNA~"))+original.substring(split);
            out.write(ascii(value));
            out.write(0);
        }
        byte[] result=out.toByteArray();System.arraycopy(data,0,result,0,data.length);return result;
    }
    private static void jump(ByteBuffer code,int opcode,int target){
        code.put((byte)opcode);code.position((code.position()+3)&~3);
        code.putInt(target-code.position()-4);
    }
    private static byte[] actions(byte[] data,boolean legacy)throws IOException{
        verify(data,legacy?"78a590c7e640c412866c7458d7d3b763f5e2752807eb16db7640ca3dcaa15246":"15283ba1c487856314ca40f1b87597f364e42328bc461e223b9db1e8c21324dc");
        ByteBuffer code=little(data);
        // The SELECT handler has already pushed the carousel index from register 4.
        // Dispatch from the unused Xenon sign-in block in this Xbox revision.
        code.position(0xcfdc);jump(code,0x99,0xd02c);
        while(code.position()<0xcfe8)code.put((byte)0x04);
        code.position(0xd02c);code.put((byte)0x4c).put((byte)0xb5).put((byte)4).put((byte)0x49);
        code.put((byte)0x12);jump(code,0x9d,0xd040);
        code.put((byte)0x17);jump(code,0x99,0xcfe8); // Stock Create Champ path.
        code.position(0xd040);code.put((byte)0x4c).put((byte)0xb5).put((byte)5).put((byte)0x49).put((byte)0x12);
        jump(code,0x9d,0xd060);code.put((byte)0x17);
        // Assign the extension screen ID on a temporary object, leaving the menu stack intact.
        code.put((byte)0x59).put((byte)0x43);
        code.put((byte)0xa3).putShort((short)303).put((byte)0xa3).putShort((short)304);
        code.put((byte)0x4f).put((byte)0x76).put((byte)0x3e);
        code.position(0xd060);code.put((byte)0xb5).put((byte)6).put((byte)0x49).put((byte)0x12);
        jump(code,0x9d,0xd0b0);
        code.put((byte)0x59).put((byte)0x43);
        code.put((byte)0xa3).putShort((short)303).put((byte)0xa3).putShort((short)305);
        code.put((byte)0x4f).put((byte)0x76).put((byte)0x3e);
        while(code.position()<0xd082)code.put((byte)0x04);
        return data;
    }
    static synchronized void installThorGuide(Path game,byte[] artwork)throws IOException{
        if(artwork.length!=ThorLoadingGuide.ART_BYTES)throw new IOException("Invalid Thor guide backdrop");
        Path archive=game.resolve("GUI/overlay.viv");
        if(!Files.isRegularFile(archive))archive=game.resolve("gui/overlay.viv");
        byte[] stamp=ascii("FNR3-THOR-CONTROLS-4");
        boolean priorGuide,v3;
        try(RandomAccessFile file=new RandomAccessFile(archive.toFile(),"r")){
            if(file.length()>48L*1024*1024 || file.length()<1024)throw new IOException("Unrecognized controller archive");
            byte[] tail=new byte[stamp.length];file.seek(file.length()-VERSION.length-stamp.length);file.readFully(tail);
            if(Arrays.equals(tail,stamp))return;
            v3=Arrays.equals(tail,ascii("FNR3-THOR-CONTROLS-3"));
            priorGuide=v3 || Arrays.equals(tail,ascii("FNR3-THOR-CONTROLS-1")) || Arrays.equals(tail,ascii("FNR3-THOR-CONTROLS-2"));
        }
        byte[] outer=Files.readAllBytes(archive);
        if(!Arrays.equals(Arrays.copyOfRange(outer,outer.length-VERSION.length,outer.length),VERSION))
            throw new IOException("Install the retail menu first");
        for(String variant:new String[]{"CM","CR","HH","PN","Training","Tutorial"}){
            String stem="lp-"+variant+"-ContConfig",group="screengroups/load/"+stem+".big",bankName=stem+".xsh";
            byte[] screen=TraxArt.inflate(member(outer,group),2*1024*1024);
            byte[] bank=TraxArt.inflate(member(screen,bankName),2*1024*1024);
            ByteBuffer header=little(bank);
            int count=header.getInt(8),matches=0;
            if(header.getInt(0)!=0x58504853 || count!=5)throw new IOException("Unrecognized controller artwork");
            for(int i=0;i<count;i++){
                String id=new String(bank,16+8*i,4,StandardCharsets.US_ASCII).trim();
                if(!"1".equals(id))continue;
                int at=header.getInt(20+i*8);
                if(at<56 || at>bank.length-artwork.length-16 || bank[at]!=0x61 ||
                   header.getShort(at+4)!=512 || header.getShort(at+6)!=256)throw new IOException("Invalid loading backdrop");
                System.arraycopy(artwork,0,bank,at+16,artwork.length);matches++;
            }
            if(matches!=1)throw new IOException("Missing loading backdrop");
            String layoutName=stem+".apt";
            byte[] source=TraxArt.inflate(member(screen,layoutName),2*1024*1024);
            byte[] layout=v3?ThorLoadingGuide.upgradeV3(source,variant):ThorLoadingGuide.compose(source,variant);
            outer=replace(outer,group,replace(replace(screen,layoutName,layout),bankName,bank));
        }
        byte[] result=Arrays.copyOf(outer,outer.length+(priorGuide?0:stamp.length));
        System.arraycopy(stamp,0,result,result.length-VERSION.length-stamp.length,stamp.length);
        System.arraycopy(VERSION,0,result,result.length-VERSION.length,VERSION.length);
        little(result).putInt(4,result.length);
        writeArchive(archive,result);
    }
    private static void writeArchive(Path archive,byte[] result)throws IOException{
        Path temp=Files.createTempFile(archive.getParent(),"thor-art-",".tmp");
        try{
            try(FileOutputStream out=new FileOutputStream(temp.toFile())){out.write(result);out.getFD().sync();}
            Files.move(temp,archive,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        }finally{Files.deleteIfExists(temp);}
    }
    static synchronized void installThorSelector(Path game,byte[] artwork)throws IOException{
        if(artwork.length!=128*64)throw new IOException("Invalid Thor selector artwork");
        Path archive=game.resolve("GUI/custom.viv");
        if(!Files.isRegularFile(archive))archive=game.resolve("gui/custom.viv");
        byte[] stamp=ascii("FNR3-THOR-SELECT-1");
        try(RandomAccessFile file=new RandomAccessFile(archive.toFile(),"r")){
            if(file.length()<1024 || file.length()>48L*1024*1024)throw new IOException("Unrecognized selector archive");
            byte[] tail=new byte[stamp.length];file.seek(file.length()-tail.length);file.readFully(tail);
            if(Arrays.equals(tail,stamp))return;
        }
        byte[] outer=Files.readAllBytes(archive);
        // The pre-fight and pause menus import distinct controllerXBox symbols.
        byte[] updated=replaceSelector(outer,"cstCtrlParts",2800,
                "1461dd7a2ee8f44f37f9981b2cf97eddf6003d783af5b1649f97bbd3d741fb6b",artwork);
        updated=replaceSelector(updated,"cstCtrlPartsBe",8560,
                "5973254d9ea1a534f64088cbaf17d30069359398c724e7a93f24e3d4c9e459c3",artwork);
        byte[] result=Arrays.copyOf(updated,updated.length+stamp.length);
        System.arraycopy(stamp,0,result,updated.length,stamp.length);
        little(result).putInt(4,result.length);
        writeArchive(archive,result);
    }
    private static byte[] replaceSelector(byte[] outer,String stem,int offset,String expected,byte[] artwork)throws IOException{
        String parts="controls/"+stem+".big",texture=stem+".xsh";
        byte[] group=TraxArt.inflate(member(outer,parts),2*1024*1024);
        byte[] bank=TraxArt.inflate(member(group,texture),2*1024*1024);
        verify(bank,expected);
        System.arraycopy(artwork,0,bank,offset+16,artwork.length);
        return replace(outer,parts,replace(group,texture,bank));
    }
    static synchronized void install(Path game,byte[] tag)throws IOException{
        Path archive=game.resolve("GUI/overlay.viv");
        if(!Files.isRegularFile(archive))archive=game.resolve("gui/overlay.viv");
        try(RandomAccessFile f=new RandomAccessFile(archive.toFile(),"r")){
            if(f.length()<VERSION.length || f.length()>48L*1024*1024)throw new IOException("Unrecognized overlay archive");
            byte[] tail=new byte[VERSION.length];f.seek(f.length()-tail.length);f.readFully(tail);
            if(Arrays.equals(tail,VERSION))return;
        }
        byte[] outer=Files.readAllBytes(archive);
        boolean legacy=Arrays.equals(Arrays.copyOfRange(outer,outer.length-LEGACY.length,outer.length),LEGACY);
        byte[] stage=Arrays.copyOfRange(outer,outer.length-VERSION.length,outer.length);
        boolean current=Arrays.equals(stage,PRE_BRAND) || Arrays.equals(stage,FIRST_BRAND) || Arrays.equals(stage,SLOPED_BRAND) || Arrays.equals(stage,PRE_PAUSE);
        if(legacy || current){
            outer=Arrays.copyOf(outer,outer.length-LEGACY.length);
            little(outer).putInt(4,outer.length);
        }else verify(outer,"e6dfe436df6e50f788b792f69ea2bdfef6026aa636357a0ab1aeac186b923647");
        byte[] group=TraxArt.inflate(member(outer,GROUP),2*1024*1024);
        if(!current){
            byte[] apt=actions(member(group,"mainmenu.apt"),legacy),pool=constants(member(group,"mainmenu.const"),legacy);
            group=replace(replace(group,"mainmenu.apt",apt),"mainmenu.const",pool);
        }
        byte[] bank=TraxArt.inflate(member(group,"mainmenu.xsh"),2*1024*1024);
        ByteBuffer header=little(bank);
        int matches=0,mainTitle=0;
        for(int i=0;i<header.getInt(8);i++){
            int pos=16+8*i;
            if(!"16".equals(new String(bank,pos,4,StandardCharsets.US_ASCII).trim()))continue;
            mainTitle=header.getInt(pos+4)+16;
            OgxBrand.apply(bank,mainTitle,tag);matches++;
        }
        if(matches!=1)throw new IOException("Missing retail title");
        byte[] result=replace(outer,GROUP,replace(group,"mainmenu.xsh",bank));
        String pauseGroup="screengroups/pausemenu/pausemenu.big";
        byte[] pause=TraxArt.inflate(member(result,pauseGroup),2*1024*1024);
        byte[] textures=TraxArt.inflate(member(pause,"pausemenu.xsh"),2*1024*1024);
        ByteBuffer directory=little(textures);
        int found=0;
        for(int i=0;i<directory.getInt(8);i++){
            int pos=16+8*i;
            if(!"2".equals(new String(textures,pos,4,StandardCharsets.US_ASCII).trim()))continue;
            int at=directory.getInt(pos+4);
            if(at<0 || at>textures.length-16 || textures[at]!=0x61 ||
                    directory.getShort(at+4)!=512 || directory.getShort(at+6)!=128)
                throw new IOException("Invalid pause title texture");
            OgxBrand.applyPause(textures,at+16,bank,mainTitle,tag);found++;
        }
        if(found!=1)throw new IOException("Missing pause title");
        result=replace(result,pauseGroup,replace(pause,"pausemenu.xsh",textures));
        little(result).putInt(4,result.length+VERSION.length);
        Path temp=Files.createTempFile(archive.getParent(),"menu-",".tmp");
        try{
            try(FileOutputStream stream=new FileOutputStream(temp.toFile())){
                stream.write(result);stream.write(VERSION);stream.getFD().sync();
            }
            Files.move(temp,archive,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        }finally{Files.deleteIfExists(temp);}
    }
}
