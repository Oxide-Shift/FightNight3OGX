package org.oxideshift.corner;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.CRC32;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;
import org.tukaani.xz.*;

/**
 * Finds the one Xbox disc image in a 7z archive and streams it out. Reads the archive in place, so a seekable
 * document needs no extra copy. Supports the coders 7-Zip uses for disc images (LZMA, LZMA2, Deflate, Copy and the
 * x86, ARM, ARM64 and Delta filters) chained one after another. BCJ2, PPMd, BZip2 and encrypted archives are refused
 * with an explanation.
 */
final class SevenZip {
    static final byte[] SIGNATURE={'7','z',(byte)0xbc,(byte)0xaf,0x27,0x1c};
    private static final int MAX_HEADER=64<<20, MAX_FILES=100000, MAX_FOLDERS=100000, MAX_CODERS=8, MAX_PROPS=1024;

    static boolean matches(byte[] prefix){
        if(prefix==null || prefix.length<SIGNATURE.length)return false;
        for(int i=0;i<SIGNATURE.length;i++)if(prefix[i]!=SIGNATURE[i])return false;
        return true;
    }

    /** The disc image found in an archive and where its bytes sit inside its folder's decoded output. */
    static final class Entry {
        final String name; final long size; final Long crc;
        final Folder folder; final long skip;
        Entry(String name,long size,Long crc,Folder folder,long skip){
            this.name=name;this.size=size;this.crc=crc;this.folder=folder;this.skip=skip;
        }
    }

    static final class Coder { byte[] id=new byte[0], props=new byte[0]; int inputs=1, outputs=1; }

    static final class Folder {
        Coder[] coders; long[][] binds; int[] packedInputs; long[] unpackSizes; Long crc;
        long packOffset; long packSize; int streams=1;
        long[] streamSizes; Long[] streamCrcs;
        int totalOutputs(){int n=0;for(Coder c:coders)n+=c.outputs;return n;}
        int totalInputs(){int n=0;for(Coder c:coders)n+=c.inputs;return n;}
        int mainOutput()throws IOException{
            for(int out=0;out<unpackSizes.length;out++){
                boolean bound=false;
                for(long[] pair:binds)if(pair[1]==out)bound=true;
                if(!bound)return out;
            }
            throw corrupt();
        }
        long size()throws IOException{return unpackSizes[mainOutput()];}
    }

    private static final class Streams {
        long packPos; long[] packSizes=new long[0]; Folder[] folders=new Folder[0];
    }

    private static final class Header {
        final byte[] b; int p;
        Header(byte[] b){this.b=b;}
        int u8()throws IOException{if(p>=b.length)throw corrupt();return b[p++]&255;}
        long u32()throws IOException{long v=0;for(int i=0;i<4;i++)v|=(long)u8()<<(8*i);return v;}
        long number()throws IOException{
            int first=u8(),mask=0x80;long value=0;
            for(int i=0;i<8;i++){
                if((first&mask)==0){
                    long high=first&(mask-1);
                    return value|(high<<(8*i));
                }
                value|=(long)u8()<<(8*i);
                mask>>>=1;
            }
            return value;
        }
        int count(int limit)throws IOException{
            long n=number();
            if(n<0 || n>limit)throw corrupt();
            return (int)n;
        }
        byte[] bytes(int n)throws IOException{
            if(n<0 || n>b.length-p)throw corrupt();
            byte[] out=Arrays.copyOfRange(b,p,p+n);p+=n;return out;
        }
        void skip(long n)throws IOException{if(n<0 || n>b.length-p)throw corrupt();p+=(int)n;}
        boolean[] bits(int n)throws IOException{
            boolean[] out=new boolean[n];int mask=0,current=0;
            for(int i=0;i<n;i++){
                if(mask==0){current=u8();mask=0x80;}
                out[i]=(current&mask)!=0;mask>>>=1;
            }
            return out;
        }
        boolean[] defined(int n)throws IOException{
            int all=u8();
            if(all!=0){boolean[] out=new boolean[n];Arrays.fill(out,true);return out;}
            return bits(n);
        }
        Long[] digests(int n)throws IOException{
            boolean[] defined=defined(n);Long[] out=new Long[n];
            for(int i=0;i<n;i++)if(defined[i])out[i]=u32();
            return out;
        }
    }

    private SevenZip(){}

    /** Locates the single .iso/.xiso member. Throws a reportable failure for anything this reader cannot use. */
    static Entry find(FileChannel channel,long origin,long length)throws IOException{
        if(length<32)throw truncated();
        byte[] start=read(channel,origin,length,0,32);
        if(!matches(start))throw corrupt();
        if(start[6]!=0)throw unsupported("a newer 7z format version");
        ByteBuffer le=ByteBuffer.wrap(start).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        long startCrc=Integer.toUnsignedLong(le.getInt(8));
        if(crc(start,12,20)!=startCrc)throw damaged();
        long nextOffset=le.getLong(12),nextSize=le.getLong(20),nextCrc=Integer.toUnsignedLong(le.getInt(28));
        if(nextSize==0)throw contents("This 7z archive is empty. Choose an archive containing one .iso or .xiso file.");
        if(nextOffset<0 || nextSize<0 || nextSize>MAX_HEADER || nextOffset>length-32-nextSize)throw truncated();
        byte[] raw=read(channel,origin,length,32+nextOffset,(int)nextSize);
        if(crc(raw,0,raw.length)!=nextCrc)throw damaged();
        Header header=new Header(raw);
        for(int depth=0;;depth++){
            int id=header.u8();
            if(id==0x01)break;
            if(id!=0x17 || depth>=4)throw corrupt();
            Streams packed=new Streams();
            readStreams(header,packed);
            if(packed.folders.length==0)throw corrupt();
            Folder f=packed.folders[0];
            long size=f.size();
            if(size>MAX_HEADER)throw corrupt();
            byte[] decoded=new byte[(int)size];
            try(InputStream in=decode(channel,origin,length,f)){
                new DataInputStream(in).readFully(decoded);
            }
            if(f.crc!=null && crc(decoded,0,decoded.length)!=f.crc)throw damaged();
            header=new Header(decoded);
        }
        Streams main=new Streams();
        String[] names=null;boolean[] emptyStream=null,directory=null;int files=0;
        for(int id;(id=header.u8())!=0x00;){
            if(id==0x02){
                for(int type;(type=header.u8())!=0;)header.skip(header.number());
            }else if(id==0x03){
                readStreams(header,new Streams());
            }else if(id==0x04){
                readStreams(header,main);
            }else if(id==0x05){
                files=header.count(MAX_FILES);
                names=new String[files];emptyStream=new boolean[files];directory=new boolean[files];
                for(int type;(type=header.u8())!=0x00;){
                    long size=header.number();
                    if(size<0 || size>header.b.length-header.p)throw corrupt();
                    int end=header.p+(int)size;
                    if(type==0x0E){
                        emptyStream=header.bits(files);
                    }else if(type==0x11){
                        if(header.u8()!=0)throw corrupt();
                        for(int i=0;i<files;i++){
                            int from=header.p;
                            while(true){
                                if(header.p+1>=end)throw corrupt();
                                if(header.b[header.p]==0 && header.b[header.p+1]==0)break;
                                header.p+=2;
                            }
                            names[i]=new String(header.b,from,header.p-from,StandardCharsets.UTF_16LE);
                            header.p+=2;
                        }
                    }else if(type==0x15){
                        boolean[] defined=header.defined(files);
                        if(header.u8()!=0)throw corrupt();
                        for(int i=0;i<files;i++)if(defined[i])directory[i]=(header.u32()&0x10)!=0;
                    }
                    if(header.p>end)throw corrupt();
                    header.p=end;
                }
            }else throw corrupt();
        }
        if(names==null)throw contents("This 7z contains no Xbox ISO. Choose an archive containing one .iso or .xiso file.");
        // Map each file that owns a stream to its folder and to its byte offset in that folder's output.
        Entry disc=null;
        int folder=0,inFolder=0;long folderOffset=0;
        for(int i=0;i<files;i++){
            if(emptyStream[i])continue;
            while(folder<main.folders.length && main.folders[folder].streams==0)folder++;
            if(folder>=main.folders.length)throw corrupt();
            Folder f=main.folders[folder];
            long size=f.streamSizes[inFolder];
            String name=names[i]==null?"":names[i];
            String lower=name.toLowerCase(Locale.ROOT);
            if(!directory[i] && (lower.endsWith(".iso") || lower.endsWith(".xiso"))){
                if(disc!=null)throw contents("This 7z contains multiple disc images. Extract it and select one complete Xbox ISO.");
                disc=new Entry(name,size,f.streamCrcs[inFolder],f,folderOffset);
            }
            folderOffset+=size;
            if(++inFolder>=f.streams){folder++;inFolder=0;folderOffset=0;}
        }
        if(disc==null){
            for(int i=0;i<files;i++){
                String lower=names[i]==null?"":names[i].toLowerCase(Locale.ROOT);
                if(lower.endsWith(".7z") || lower.endsWith(".zip") || lower.endsWith(".rar"))
                    throw contents("This 7z holds another archive ("+names[i]+"). Extract it first, then select the Xbox ISO inside.");
            }
            throw contents("This 7z contains no Xbox ISO. Choose an archive containing one .iso or .xiso file.");
        }
        return disc;
    }

    /** The decoded member, positioned at its first byte and ending after its last. */
    static InputStream open(FileChannel channel,long origin,long length,Entry entry)throws IOException{
        InputStream in=decode(channel,origin,length,entry.folder);
        try{
            byte[] scratch=new byte[1<<16];
            for(long left=entry.skip;left>0;){
                int n=in.read(scratch,0,(int)Math.min(scratch.length,left));
                if(n<0)throw new EOFException("The 7z archive ended early");
                left-=n;
            }
        }catch(IOException|RuntimeException e){in.close();throw e;}
        return new Bounded(in,entry.size);
    }

    private static void readStreams(Header h,Streams s)throws IOException{
        for(int id;(id=h.u8())!=0x00;){
            if(id==0x06){
                s.packPos=h.number();
                int n=h.count(MAX_FOLDERS);
                s.packSizes=new long[n];
                for(int sub;(sub=h.u8())!=0x00;){
                    if(sub==0x09)for(int i=0;i<n;i++)s.packSizes[i]=h.number();
                    else if(sub==0x0A)h.digests(n);
                    else throw corrupt();
                }
            }else if(id==0x07){
                if(h.u8()!=0x0B)throw corrupt();
                int n=h.count(MAX_FOLDERS);
                if(h.u8()!=0)throw corrupt();
                s.folders=new Folder[n];
                for(int i=0;i<n;i++)s.folders[i]=readFolder(h);
                if(h.u8()!=0x0C)throw corrupt();
                for(Folder f:s.folders){
                    f.unpackSizes=new long[f.totalOutputs()];
                    for(int i=0;i<f.unpackSizes.length;i++){
                        f.unpackSizes[i]=h.number();
                        if(f.unpackSizes[i]<0)throw corrupt();
                    }
                }
                for(int sub;(sub=h.u8())!=0x00;){
                    if(sub!=0x0A)throw corrupt();
                    Long[] crcs=h.digests(n);
                    for(int i=0;i<n;i++)s.folders[i].crc=crcs[i];
                }
                // Assign each folder its packed bytes in archive order.
                long offset=s.packPos;int pack=0;
                for(Folder f:s.folders){
                    if(f.packedInputs.length!=1)throw unsupported("BCJ2 or another multi-stream filter");
                    if(pack>=s.packSizes.length)throw corrupt();
                    f.packOffset=offset;f.packSize=s.packSizes[pack];
                    for(int k=0;k<f.packedInputs.length;k++)offset+=s.packSizes[pack++];
                }
                for(Folder f:s.folders){f.streamSizes=new long[]{f.size()};f.streamCrcs=new Long[]{f.crc};}
            }else if(id==0x08){
                readSubStreams(h,s);
            }else throw corrupt();
        }
    }

    private static void readSubStreams(Header h,Streams s)throws IOException{
        Folder[] folders=s.folders;
        for(Folder f:folders)f.streams=1;
        int id=h.u8();
        if(id==0x0D){
            for(Folder f:folders)f.streams=h.count(MAX_FILES);
            id=h.u8();
        }
        for(Folder f:folders){f.streamSizes=new long[f.streams];f.streamCrcs=new Long[f.streams];}
        boolean sized=id==0x09;
        for(Folder f:folders){
            if(f.streams==0)continue;
            long sum=0;
            for(int i=0;i<f.streams-1;i++){
                if(!sized)throw corrupt();
                long size=h.number();
                if(size<0 || (sum+=size)<0)throw corrupt();
                f.streamSizes[i]=size;
            }
            long last=f.size()-sum;
            if(last<0)throw corrupt();
            f.streamSizes[f.streams-1]=last;
        }
        if(sized)id=h.u8();
        int unknown=0;
        for(Folder f:folders)if(!(f.streams==1 && f.crc!=null))unknown+=f.streams;
        for(Folder f:folders)if(f.streams==1 && f.crc!=null)f.streamCrcs[0]=f.crc;
        while(id!=0x00){
            if(id!=0x0A)throw corrupt();
            Long[] crcs=h.digests(unknown);int k=0;
            for(Folder f:folders){
                if(f.streams==1 && f.crc!=null)continue;
                for(int i=0;i<f.streams;i++)f.streamCrcs[i]=crcs[k++];
            }
            id=h.u8();
        }
    }

    private static Folder readFolder(Header h)throws IOException{
        Folder f=new Folder();
        int n=h.count(MAX_CODERS);
        if(n==0)throw corrupt();
        f.coders=new Coder[n];
        for(int i=0;i<n;i++){
            Coder c=new Coder();
            int flags=h.u8();
            if((flags&0x80)!=0)throw unsupported("an alternative coder method");
            c.id=h.bytes(flags&0x0F);
            if((flags&0x10)!=0){c.inputs=h.count(MAX_CODERS);c.outputs=h.count(MAX_CODERS);}
            if((flags&0x20)!=0)c.props=h.bytes(h.count(MAX_PROPS));
            f.coders[i]=c;
        }
        int outputs=f.totalOutputs(),inputs=f.totalInputs();
        if(outputs==0)throw corrupt();
        f.binds=new long[outputs-1][];
        for(int i=0;i<f.binds.length;i++)f.binds[i]=new long[]{h.number(),h.number()};
        int packed=inputs-f.binds.length;
        if(packed<1)throw corrupt();
        f.packedInputs=new int[packed];
        if(packed==1){
            f.packedInputs[0]=-1;
            for(int in=0;in<inputs;in++){
                boolean bound=false;
                for(long[] pair:f.binds)if(pair[0]==in)bound=true;
                if(!bound){f.packedInputs[0]=in;break;}
            }
            if(f.packedInputs[0]<0)throw corrupt();
        }else for(int i=0;i<packed;i++)f.packedInputs[i]=h.count(MAX_CODERS);
        return f;
    }

    /** Builds the coder chain from the packed stream to the folder's main output. */
    private static InputStream decode(FileChannel channel,long origin,long length,Folder f)throws IOException{
        for(Coder c:f.coders)if(c.inputs!=1 || c.outputs!=1)throw unsupported("BCJ2 or another multi-stream filter");
        if(f.packOffset<0 || f.packSize<0 || f.packOffset>length-32-f.packSize)throw truncated();
        InputStream stream=new BufferedInputStream(new Slice(channel,origin+32+f.packOffset,f.packSize),1<<20);
        int current=f.packedInputs[0],main=f.mainOutput();
        try{
            stream=coder(f.coders[current],stream,f.unpackSizes[current]);
            for(int steps=0;current!=main;steps++){
                if(steps>f.coders.length)throw corrupt();
                int next=-1;
                for(long[] pair:f.binds)if(pair[1]==current)next=(int)pair[0];
                if(next<0 || next>=f.coders.length)throw corrupt();
                current=next;
                stream=coder(f.coders[current],stream,f.unpackSizes[current]);
            }
        }catch(IOException|RuntimeException e){stream.close();throw e;}
        return stream;
    }

    private static InputStream coder(Coder c,InputStream in,long size)throws IOException{
        String id=hex(c.id);
        switch(id){
            case "00": return in;
            case "030101": {
                if(c.props.length<5)throw corrupt();
                long dict=(c.props[1]&255L)|(c.props[2]&255L)<<8|(c.props[3]&255L)<<16|(c.props[4]&255L)<<24;
                return new LZMAInputStream(in,size,c.props[0],dictionary(dict,size));
            }
            case "21": {
                if(c.props.length<1)throw corrupt();
                int bits=c.props[0]&255;
                if(bits>40)throw corrupt();
                long dict=bits==40?0xFFFFFFFFL:(2L|(bits&1))<<(bits/2+11);
                return new LZMA2InputStream(in,dictionary(dict,size));
            }
            case "040108": return new InflaterInputStream(in,new Inflater(true),1<<16);
            case "03030103": case "04": { X86Options o=new X86Options(); if(c.props.length>=4)o.setStartOffset(start(c)); return o.getInputStream(in); }
            case "03030501": case "07": { ARMOptions o=new ARMOptions(); if(c.props.length>=4)o.setStartOffset(start(c)); return o.getInputStream(in); }
            case "03030701": case "08": { ARMThumbOptions o=new ARMThumbOptions(); if(c.props.length>=4)o.setStartOffset(start(c)); return o.getInputStream(in); }
            case "0a": { ARM64Options o=new ARM64Options(); if(c.props.length>=4)o.setStartOffset(start(c)); return o.getInputStream(in); }
            case "03": return new DeltaOptions(c.props.length>0?(c.props[0]&255)+1:1).getInputStream(in);
            case "030401": throw unsupported("PPMd compression");
            case "040202": throw unsupported("BZip2 compression");
            case "0303011b": throw unsupported("the BCJ2 filter");
            case "06f10701": throw new DiscImport.Failure(DiscImport.Reason.ARCHIVE,
                    "This 7z archive is password-protected. Extract it on a computer, then select the Xbox ISO inside.").format("7z");
            default: throw unsupported("compression method "+id);
        }
    }

    private static int start(Coder c){
        return (c.props[0]&255)|(c.props[1]&255)<<8|(c.props[2]&255)<<16|(c.props[3]&255)<<24;
    }

    /** A dictionary larger than the data it decodes is never referenced, so allocate no more than the data. */
    static int dictionary(long declared,long size){
        long limit=size<0?declared:Math.min(declared,Math.max(size,4096));
        return (int)Math.min(limit,Integer.MAX_VALUE&~15);
    }

    private static byte[] read(FileChannel channel,long origin,long length,long offset,int size)throws IOException{
        if(offset<0 || size<0 || offset>length-size)throw truncated();
        ByteBuffer dst=ByteBuffer.allocate(size);
        while(dst.hasRemaining()){
            int n=channel.read(dst,origin+offset+dst.position());
            if(n<=0)throw truncated();
        }
        return dst.array();
    }

    private static long crc(byte[] data,int from,int size){CRC32 c=new CRC32();c.update(data,from,size);return c.getValue();}
    private static String hex(byte[] id){
        StringBuilder out=new StringBuilder();
        for(byte b:id)out.append(String.format(Locale.ROOT,"%02x",b&255));
        return out.toString();
    }

    static IOException corrupt(){
        return new DiscImport.Failure(DiscImport.Reason.ZIP_INTEGRITY,
                "This 7z archive is damaged or not a complete 7z file. Re-download it, or extract the ISO on a computer and select it directly.").format("7z");
    }
    private static IOException damaged(){return corrupt();}
    private static IOException truncated(){
        return new DiscImport.Failure(DiscImport.Reason.TRUNCATED,
                "The 7z archive ended before all of its data could be read. Check that the copy or download finished.").format("7z");
    }
    private static IOException contents(String message){
        return new DiscImport.Failure(DiscImport.Reason.ZIP_CONTENTS,message).format("7z");
    }
    private static IOException unsupported(String what){
        return new DiscImport.Failure(DiscImport.Reason.ARCHIVE,
                "This 7z archive uses "+what+", which the app can't unpack. Extract it on a computer, or repack the ISO with 7-Zip's normal settings, then select it.").format("7z");
    }

    /** Reads a byte range of the archive without moving the channel's position. */
    private static final class Slice extends InputStream {
        private final FileChannel channel; private long position; private final long end;
        Slice(FileChannel channel,long start,long size){this.channel=channel;this.position=start;this.end=start+size;}
        @Override public int read()throws IOException{byte[] one=new byte[1];int n=read(one,0,1);return n<0?-1:one[0]&255;}
        @Override public int read(byte[] b,int off,int len)throws IOException{
            if(len==0)return 0;
            if(position>=end)return -1;
            int n=channel.read(ByteBuffer.wrap(b,off,(int)Math.min(len,end-position)),position);
            if(n<0)throw new EOFException("The 7z archive ended early");
            position+=n;return n;
        }
    }

    /** Ends the member exactly at its declared size so trailing members of a solid block are never read. */
    private static final class Bounded extends FilterInputStream {
        private long left;
        Bounded(InputStream in,long size){super(in);left=size;}
        @Override public int read()throws IOException{
            if(left<=0)return -1;
            int b=in.read();
            if(b<0)throw new EOFException("The 7z archive ended early");
            left--;return b;
        }
        @Override public int read(byte[] b,int off,int len)throws IOException{
            if(left<=0)return -1;
            int n=in.read(b,off,(int)Math.min(len,left));
            if(n<0)throw new EOFException("The 7z archive ended early");
            left-=n;return n;
        }
        @Override public long skip(long n){return 0;}
        @Override public int available(){return 0;}
    }
}
