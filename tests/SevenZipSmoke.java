package org.oxideshift.corner;

import java.io.*;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.CRC32;
import org.tukaani.xz.*;

/** Builds 7z archives around a synthetic Xbox disc and installs them through both import paths. */
public class SevenZipSmoke {
    private static final int SECTOR=2048, START=311;

    // --- Minimal 7z writer: enough of the format to exercise every path the reader takes. ---
    static final class Member { final String name; final byte[] data; final boolean dir;
        Member(String name,byte[] data){this(name,data,false);} Member(String name,byte[] data,boolean dir){this.name=name;this.data=data;this.dir=dir;} }
    static final class Folder { final String method; final List<Member> members=new ArrayList<>();
        Folder(String method,Member... m){this.method=method;members.addAll(Arrays.asList(m));} }

    private static void number(ByteArrayOutputStream out,long value){
        int first=0,mask=0x80,i;
        for(i=0;i<8;i++){
            if(value<(1L<<(7*(i+1)))){first|=(int)(value>>>(8*i));break;}
            first|=mask;mask>>>=1;
        }
        out.write(first);
        for(;i>0;i--){out.write((int)value);value>>>=8;}
    }
    private static void u32(ByteArrayOutputStream out,long v){for(int i=0;i<4;i++)out.write((int)(v>>>(8*i)));}
    private static long crc(byte[] b){CRC32 c=new CRC32();c.update(b);return c.getValue();}

    /** Returns {packed bytes, folder record (coders, binds and unpack sizes)}. */
    private static byte[][] encode(String method,byte[] raw)throws IOException{
        ByteArrayOutputStream packed=new ByteArrayOutputStream(),coders=new ByteArrayOutputStream();
        LZMA2Options options=new LZMA2Options();options.setDictSize(1<<20);
        switch(method){
            case "copy":
                packed.write(raw);
                coders.write(1);coders.write(0x01);coders.write(0x00);
                number(coders,raw.length);break;
            case "ppmd":
                packed.write(raw);
                coders.write(1);coders.write(0x03);coders.write(new byte[]{3,4,1});
                number(coders,raw.length);break;
            case "lzma2": {
                FinishableOutputStream out=options.getOutputStream(new FinishableWrapperOutputStream(packed));
                out.write(raw);out.finish();
                coders.write(1);coders.write(0x21);coders.write(0x21);number(coders,1);coders.write(16);
                number(coders,raw.length);break;
            }
            case "lzma": {
                LZMAOutputStream out=new LZMAOutputStream(packed,options,false);
                out.write(raw);out.finish();
                coders.write(1);coders.write(0x23);coders.write(new byte[]{3,1,1});number(coders,5);
                coders.write(out.getProps());u32(coders,1<<20);
                number(coders,raw.length);break;
            }
            case "bcj+lzma2": {
                FinishableOutputStream lz=options.getOutputStream(new FinishableWrapperOutputStream(packed));
                FinishableOutputStream bcj=new X86Options().getOutputStream(lz);
                bcj.write(raw);bcj.finish();
                // Coder 0 (BCJ) is the main output; it reads coder 1's (LZMA2) output. Coder 1 reads the packed stream.
                coders.write(2);
                coders.write(0x04);coders.write(new byte[]{3,3,1,3});
                coders.write(0x21);coders.write(0x21);number(coders,1);coders.write(16);
                number(coders,0);number(coders,1);
                number(coders,raw.length);number(coders,raw.length);break;
            }
            default: throw new AssertionError(method);
        }
        return new byte[][]{packed.toByteArray(),coders.toByteArray()};
    }

    /** Splits a folder record into its coder part and its unpack-size part at the marker the caller knows. */
    static byte[] archive(boolean encodedHeader,Folder... folders)throws IOException{
        ByteArrayOutputStream body=new ByteArrayOutputStream();
        List<byte[]> packs=new ArrayList<>();List<byte[]> records=new ArrayList<>();List<Long> unpack=new ArrayList<>();
        List<Member> files=new ArrayList<>();
        for(Folder f:folders){
            ByteArrayOutputStream raw=new ByteArrayOutputStream();
            for(Member m:f.members)raw.write(m.data);
            byte[][] e=encode(f.method,raw.toByteArray());
            packs.add(e[0]);records.add(e[1]);body.write(e[0]);
            files.addAll(f.members);
        }
        ByteArrayOutputStream h=new ByteArrayOutputStream();
        h.write(0x01);h.write(0x04);
        streams(h,0,packs,records,folders);
        h.write(0x05);number(h,files.size()+1);
        // A directory entry with no stream checks the empty-stream mapping.
        ByteArrayOutputStream empty=new ByteArrayOutputStream();
        boolean[] bits=new boolean[files.size()+1];bits[0]=true;
        int current=0;for(int i=0;i<bits.length;i++){if(bits[i])current|=0x80>>(i%8);if(i%8==7||i==bits.length-1){empty.write(current);current=0;}}
        h.write(0x0E);number(h,empty.size());h.write(empty.toByteArray());
        ByteArrayOutputStream names=new ByteArrayOutputStream();names.write(0);
        for(String n:concat("docs",files)){names.write(n.getBytes(StandardCharsets.UTF_16LE));names.write(0);names.write(0);}
        h.write(0x11);number(h,names.size());h.write(names.toByteArray());
        ByteArrayOutputStream attrs=new ByteArrayOutputStream();attrs.write(1);attrs.write(0);
        u32(attrs,0x10);for(int i=0;i<files.size();i++)u32(attrs,0x20);
        h.write(0x15);number(h,attrs.size());h.write(attrs.toByteArray());
        h.write(0x00);h.write(0x00);
        byte[] header=h.toByteArray();
        if(encodedHeader){
            long at=body.size();
            byte[][] e=encode("lzma2",header);
            body.write(e[0]);
            ByteArrayOutputStream wrapped=new ByteArrayOutputStream();
            wrapped.write(0x17);
            wrapped.write(0x06);number(wrapped,at);number(wrapped,1);wrapped.write(0x09);number(wrapped,e[0].length);wrapped.write(0x00);
            wrapped.write(0x07);wrapped.write(0x0B);number(wrapped,1);wrapped.write(0);
            byte[] rec=e[1];wrapped.write(rec,0,rec.length-sizeLength(header.length));
            wrapped.write(0x0C);number(wrapped,header.length);
            wrapped.write(0x0A);wrapped.write(1);u32(wrapped,crc(header));wrapped.write(0x00);
            wrapped.write(0x00);
            header=wrapped.toByteArray();
        }
        ByteBuffer start=ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN);
        start.put(SevenZip.SIGNATURE).put((byte)0).put((byte)4).putInt(0).putLong(body.size()).putLong(header.length).putInt((int)crc(header));
        byte[] s=start.array();
        CRC32 c=new CRC32();c.update(s,12,20);ByteBuffer.wrap(s).order(ByteOrder.LITTLE_ENDIAN).putInt(8,(int)c.getValue());
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        out.write(s);out.write(body.toByteArray());out.write(header);
        return out.toByteArray();
    }
    private static List<String> concat(String first,List<Member> files){List<String> n=new ArrayList<>();n.add(first);for(Member m:files)n.add(m.name);return n;}
    private static int sizeLength(long v){ByteArrayOutputStream b=new ByteArrayOutputStream();number(b,v);return b.size();}

    private static void streams(ByteArrayOutputStream h,long packPos,List<byte[]> packs,List<byte[]> records,Folder[] folders)throws IOException{
        h.write(0x06);number(h,packPos);number(h,packs.size());
        h.write(0x09);for(byte[] p:packs)number(h,p.length);h.write(0x00);
        h.write(0x07);h.write(0x0B);number(h,folders.length);h.write(0);
        List<byte[]> sizes=new ArrayList<>();
        for(int i=0;i<folders.length;i++){
            byte[] rec=records.get(i);
            long total=0;for(Member m:folders[i].members)total+=m.data.length;
            int tail=sizeLength(total)*(folders[i].method.equals("bcj+lzma2")?2:1);
            h.write(rec,0,rec.length-tail);
            sizes.add(Arrays.copyOfRange(rec,rec.length-tail,rec.length));
        }
        h.write(0x0C);for(byte[] s:sizes)h.write(s);
        h.write(0x00);
        h.write(0x08);h.write(0x0D);for(Folder f:folders)number(h,f.members.size());
        h.write(0x09);for(Folder f:folders)for(int i=0;i<f.members.size()-1;i++)number(h,f.members.get(i).data.length);
        h.write(0x0A);h.write(1);for(Folder f:folders)for(Member m:f.members)u32(h,crc(m.data));
        h.write(0x00);
        h.write(0x00);
    }

    // --- Synthetic disc, laid out like DiscInstallSmoke's. ---
    private static void entry(ByteBuffer dir,int sector,int size,String name){
        byte[] bytes=name.getBytes(StandardCharsets.US_ASCII);
        dir.putInt(0).putInt(sector).putInt(size).put((byte)0).put((byte)bytes.length).put(bytes);
        dir.position((dir.position()+3)&~3);
    }
    private static byte[] disc(byte[] xbe){
        int exeSector=66,assetSector=exeSector+(xbe.length+SECTOR-1)/SECTOR;
        byte[] bytes=new byte[(assetSector+1)*SECTOR];
        ByteBuffer disc=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        disc.position(65536);disc.put("MICROSOFT*XBOX*MEDIA".getBytes(StandardCharsets.US_ASCII)).putInt(64).putInt(56);
        disc.position(64*SECTOR);entry(disc,exeSector,xbe.length,"DEFAULT.XBE");entry(disc,assetSector,3,"hello.txt");
        int start=disc.position();
        entry(disc,63,28,"GUI");disc.put(start+12,(byte)16);
        disc.putInt(65536+24,disc.position()-64*SECTOR);
        disc.position(63*SECTOR);entry(disc,65,64,"overlay.viv");
        disc.position(65*SECTOR);
        disc.put("BIGF".getBytes(StandardCharsets.US_ASCII)).putInt(64);
        disc.order(ByteOrder.BIG_ENDIAN).putInt(1).putInt(26).putInt(32).putInt(1).put((byte)'a').put((byte)0);
        disc.order(ByteOrder.LITTLE_ENDIAN);
        disc.position(exeSector*SECTOR);disc.put(xbe);disc.position(assetSector*SECTOR);disc.put(new byte[]{4,5,6});
        return bytes;
    }

    private static void installed(Path root,byte[] xbe,String label)throws Exception{
        if(!Arrays.equals(xbe,Files.readAllBytes(root.resolve("retail/game/default.xbe"))) ||
           !Arrays.equals(new byte[]{4,5,6},Files.readAllBytes(root.resolve("retail/game/hello.txt"))))
            throw new AssertionError(label+": wrong install");
        if(!"7z".equals(DiscFacts.installed(root).values.getProperty("container")))throw new AssertionError(label+": container fact");
        if(Files.exists(root.resolve("disc-copy.tmp")))throw new AssertionError(label+": stage left behind");
    }

    private static void viaChannel(Path temp,String label,byte[] archive,byte[] xbe)throws Exception{
        Path document=temp.resolve(label+".document"),root=Files.createDirectory(temp.resolve(label+"-channel"));
        try(OutputStream out=Files.newOutputStream(document)){out.write(new byte[START]);out.write(archive);out.write(new byte[29]);}
        try(FileChannel file=FileChannel.open(document)){
            DiscImport attempt=new DiscImport(s->{});
            GameLibrary.importDisc(file,START,archive.length,root,attempt,null);
            if(!"7z".equals(attempt.format))throw new AssertionError(label+": reported format "+attempt.format);
        }
        installed(root,xbe,label+" (channel)");
    }
    private static void viaStream(Path temp,String label,byte[] archive,byte[] xbe)throws Exception{
        Path root=Files.createDirectory(temp.resolve(label+"-stream"));
        GameLibrary.importDisc(new ByteArrayInputStream(archive),archive.length,root,s->{});
        installed(root,xbe,label+" (stream)");
    }
    private static void refuse(Path temp,String label,byte[] archive,int code,String text)throws Exception{
        Path document=temp.resolve(label+".bad"),root=Files.createDirectory(temp.resolve(label+"-refused"));
        Files.write(document,archive);
        DiscImport attempt=new DiscImport(s->{});
        try(FileChannel file=FileChannel.open(document)){
            GameLibrary.importDisc(file,0,archive.length,root,attempt,null);
            throw new AssertionError(label+": accepted");
        }catch(DiscImport.Failure failure){
            if(failure.reason.code!=code || !failure.getMessage().contains(text) || !"7z".equals(failure.format==null?attempt.format:failure.format))
                throw new AssertionError(label+": got "+failure.reason+" "+failure.getMessage());
        }
        if(Files.exists(root.resolve("retail")) || Files.exists(root.resolve("disc-copy.tmp")))throw new AssertionError(label+": failed import left files");
    }

    public static void main(String[] args)throws Exception{
        byte[] xbe=Files.readAllBytes(Paths.get(args[0]));
        byte[] iso=disc(xbe);
        byte[] readme="Fight Night Round 3 (USA)\r\n".getBytes(StandardCharsets.US_ASCII);
        Path temp=Files.createTempDirectory("seven-zip-smoke");
        try{
            for(String method:new String[]{"lzma2","lzma","copy","bcj+lzma2"}){
                byte[] a=archive(false,new Folder(method,new Member("Fight Night Round 3 (USA).iso",iso)));
                viaChannel(temp,method,a,xbe);
            }
            // 7-Zip's default output: a compressed header and a solid block with the disc between other files.
            byte[] solid=archive(true,new Folder("lzma2",new Member("readme.txt",readme),new Member("docs/Fight Night Round 3.xiso",iso),
                    new Member("Fight Night.nfo",readme)));
            viaChannel(temp,"solid",solid,xbe);
            viaStream(temp,"solid",solid,xbe);
            // Two folders: the disc is in the second.
            viaChannel(temp,"folders",archive(false,new Folder("copy",new Member("cover.jpg",readme)),
                    new Folder("lzma2",new Member("FN3.ISO",iso))),xbe);

            refuse(temp,"two-discs",archive(false,new Folder("lzma2",new Member("a.iso",iso),new Member("b.iso",iso))),-111,"multiple");
            refuse(temp,"no-disc",archive(false,new Folder("lzma2",new Member("readme.txt",readme))),-111,"no Xbox ISO");
            refuse(temp,"nested",archive(false,new Folder("copy",new Member("Fight Night Round 3.7z",readme))),-111,"another archive");
            refuse(temp,"ppmd",archive(false,new Folder("ppmd",new Member("fn3.iso",iso))),-101,"PPMd");
            byte[] damaged=archive(false,new Folder("copy",new Member("fn3.iso",iso)));
            damaged[32+70000]^=0x55;
            refuse(temp,"damaged",damaged,-112,"damaged");
            byte[] lz=archive(false,new Folder("lzma2",new Member("fn3.iso",iso)));
            refuse(temp,"truncated",Arrays.copyOf(lz,lz.length/2),-109,"ended");
            byte[] header=archive(true,new Folder("lzma2",new Member("fn3.iso",iso)));
            header[header.length-3]^=0x01;
            refuse(temp,"header",header,-112,"damaged");

            if(SevenZip.dictionary(0xFFFFFFFFL,5000)!=5000 || SevenZip.dictionary(1<<26,-1)!=1<<26 || SevenZip.dictionary(0xFFFFFFFFL,-1)<0)
                throw new AssertionError("Dictionary sizing");
            System.out.println("7z import: LZMA, LZMA2, Copy, BCJ chain, solid blocks, encoded header and refusals passed");
        }finally{GameLibrary.clearStage(temp);}
    }
}
