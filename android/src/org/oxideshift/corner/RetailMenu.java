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
    private static final byte[] VERSION=ascii("FNR3-ACHIEVEMENTS-1");
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
    private static void verify(byte[] data,String expected)throws IOException{
        try{
            byte[] sum=MessageDigest.getInstance("SHA-256").digest(data);
            for(int i=0;i<sum.length;i++)
                if((sum[i]&255)!=Integer.parseInt(expected.substring(i*2,i*2+2),16))
                    throw new IOException("Unrecognized main menu revision");
        }catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
    }
    private static byte[] constants(byte[] data)throws IOException{
        verify(data,"dc26cee7d1afbc8f0016551c7c582eed21f068415f1c93801cda548ac3e9602a");
        ByteArrayOutputStream out=new ByteArrayOutputStream();out.write(data);
        ByteBuffer table=little(data);
        for(int slot:new int[]{278,281,282,310,311}){
            int pointer=table.getInt(28)+slot*8+4,start=table.getInt(pointer),end=start;
            while(data[end]!=0)end++;
            String original=new String(data,start,end-start,StandardCharsets.US_ASCII);
            int split=original.lastIndexOf(',');
            table.putInt(pointer,out.size());
            // The last two entries are diagnostic strings for Xenon-only sign-in prompts.
            String value=slot==310?"m_sCurrentScreenId":slot==311?"fnr3/achievements":
                    original.substring(0,split)+(slot==278?",ACHIEVEMENTS":",~DNA~")+original.substring(split);
            out.write(ascii(value));
            out.write(0);
        }
        byte[] result=out.toByteArray();System.arraycopy(data,0,result,0,data.length);return result;
    }
    private static void jump(ByteBuffer code,int opcode,int target){
        code.put((byte)opcode);code.position((code.position()+3)&~3);
        code.putInt(target-code.position()-4);
    }
    private static byte[] actions(byte[] data)throws IOException{
        verify(data,"15283ba1c487856314ca40f1b87597f364e42328bc461e223b9db1e8c21324dc");
        ByteBuffer code=little(data);
        // The SELECT handler has already pushed the carousel index from register 4.
        // Dispatch from the unused Xenon sign-in block in this Xbox revision.
        code.position(0xcfdc);jump(code,0x99,0xd02c);
        while(code.position()<0xcfe8)code.put((byte)0x04);
        code.position(0xd02c);code.put((byte)0x4c).put((byte)0xb5).put((byte)4).put((byte)0x49);
        code.put((byte)0x12);jump(code,0x9d,0xd040);
        code.put((byte)0x17);jump(code,0x99,0xcfe8); // Stock Create Champ path.
        code.position(0xd040);code.put((byte)0xb5).put((byte)5).put((byte)0x49).put((byte)0x12);
        jump(code,0x9d,0xd0b0); // Stock menu.select() for all other rows.
        // Assign the extension screen ID on a temporary object, leaving the menu stack intact.
        code.put((byte)0x59).put((byte)0x43);
        code.put((byte)0xa3).putShort((short)303).put((byte)0xa3).putShort((short)304);
        code.put((byte)0x4f).put((byte)0x76).put((byte)0x3e);
        while(code.position()<0xd082)code.put((byte)0x04);
        return data;
    }
    static synchronized void install(Path game)throws IOException{
        Path archive=game.resolve("GUI/overlay.viv");
        if(!Files.isRegularFile(archive))archive=game.resolve("gui/overlay.viv");
        try(RandomAccessFile f=new RandomAccessFile(archive.toFile(),"r")){
            if(f.length()<VERSION.length || f.length()>48L*1024*1024)throw new IOException("Unrecognized overlay archive");
            byte[] tail=new byte[VERSION.length];f.seek(f.length()-tail.length);f.readFully(tail);
            if(Arrays.equals(tail,VERSION))return;
        }
        byte[] outer=Files.readAllBytes(archive);
        verify(outer,"e6dfe436df6e50f788b792f69ea2bdfef6026aa636357a0ab1aeac186b923647");
        byte[] group=TraxArt.inflate(member(outer,GROUP),2*1024*1024);
        byte[] apt=actions(member(group,"mainmenu.apt")),pool=constants(member(group,"mainmenu.const"));
        group=replace(replace(group,"mainmenu.apt",apt),"mainmenu.const",pool);
        byte[] result=replace(outer,GROUP,group);
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
