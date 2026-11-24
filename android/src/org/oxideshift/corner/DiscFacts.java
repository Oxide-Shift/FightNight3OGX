package org.oxideshift.corner;

import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.*;
import java.util.*;

/** Small import receipt stored beside the game. Fields are added only after the corresponding observation. */
final class DiscFacts {
    static final String RECEIPT="disc-facts.properties";
    static final String[] KEYS={"origin","container","layout","selected_bytes","image_bytes","partition_offset","files",
            "payload_bytes","title","region","revision","media_mask","disc_number","source_xbe_bytes","source_xbe_sha256",
            "installed_xbe_bytes","installed_xbe_sha256","xbe_selection","disc_files_sha256","selected_sha1","image_sha1"};
    final Properties values=new Properties();
    void put(String key,Object value){values.setProperty(key,String.valueOf(value));}
    static String u32(int value){return String.format(Locale.ROOT,"%08x",value);}
    static MessageDigest digest(){
        try{return MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException e){throw new AssertionError(e);}
    }
    static String hex(byte[] data){
        char[] out=new char[data.length*2];
        String digits="0123456789abcdef";
        for(int i=0;i<data.length;i++){out[i*2]=digits.charAt((data[i]&255)>>>4);out[i*2+1]=digits.charAt(data[i]&15);}
        return new String(out);
    }
    void certificate(ByteBuffer cert){
        put("title",u32(cert.getInt(8)));put("media_mask",u32(cert.getInt(0x9c)));put("region",u32(cert.getInt(0xa0)));
        put("disc_number",Integer.toUnsignedLong(cert.getInt(0xa8)));put("revision",Integer.toUnsignedLong(cert.getInt(0xac)));
    }
    // Hash sorted, lowercase ASCII relative path + NUL + binary SHA-256 of original file bytes.
    static String fileSet(SortedMap<String,byte[]> files){
        MessageDigest sha=digest();
        for(Map.Entry<String,byte[]> file:files.entrySet()){
            sha.update(file.getKey().getBytes(StandardCharsets.US_ASCII));sha.update((byte)0);sha.update(file.getValue());
        }
        return hex(sha.digest());
    }
    void write(Path stage)throws IOException{
        try(OutputStream out=Files.newOutputStream(stage.resolve(RECEIPT),StandardOpenOption.CREATE_NEW)){values.store(out,null);}
    }
    static DiscFacts installed(Path root){
        DiscFacts facts=new DiscFacts();
        Path retail=root.resolve("retail");
        Path xbe=retail.resolve("game/default.xbe");
        if(!Files.isRegularFile(xbe))xbe=root.resolve("game/default.xbe");
        else {
            Path receipt=retail.resolve(RECEIPT);
            try{
                if(Files.isRegularFile(receipt) && Files.size(receipt)<=4096){
                    try(InputStream in=Files.newInputStream(receipt)){facts.values.load(in);}
                    if("import".equals(facts.values.getProperty("origin")))return facts;
                }
            }catch(IOException | IllegalArgumentException ignored){}
            facts.values.clear();
        }
        if(!Files.isRegularFile(xbe))return facts;
        facts.put("origin","installed_only");
        try{
            long size=Files.size(xbe);facts.put("installed_xbe_bytes",size);
            if(size<0x11c || size>64L*1024*1024)return facts;
            try(RandomAccessFile file=new RandomAccessFile(xbe.toFile(),"r")){
                byte[] header=new byte[0x11c];file.readFully(header);
                ByteBuffer b=ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
                long cert=Integer.toUnsignedLong(b.getInt(0x118))-Integer.toUnsignedLong(b.getInt(0x104));
                long headers=Integer.toUnsignedLong(b.getInt(0x108));
                if(b.getInt(0)==0x48454258 && headers<=size && cert>=header.length && cert<=headers-0xb0){
                    file.seek(cert);byte[] data=new byte[0xb0];file.readFully(data);
                    ByteBuffer c=ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
                    if(Integer.toUnsignedLong(c.getInt(0))>=data.length && Integer.toUnsignedLong(c.getInt(0))<=headers-cert)facts.certificate(c);
                }
                file.seek(0);MessageDigest sha=digest();byte[] block=new byte[65536];
                for(int n;(n=file.read(block))!=-1;)sha.update(block,0,n);
                facts.put("installed_xbe_sha256",hex(sha.digest()));
            }
        }catch(IOException ignored){}
        return facts;
    }
    /** Player-requested scan of an older installation. Never traverses links or reads sibling saves. */
    static DiscFacts checkInstalledFiles(Path root,String expectedXbe,java.util.function.BooleanSupplier cancelled)throws IOException{
        synchronized(GameLibrary.class){
            Path current=root.resolve("retail/game");
            final Path game=Files.isRegularFile(current.resolve("default.xbe"))?current:root.resolve("game");
            SortedMap<String,byte[]> hashes=new TreeMap<>();
            byte[] block=new byte[256*1024];long[] total={0};
            MessageDigest sha=digest();
            Files.walkFileTree(game,EnumSet.noneOf(FileVisitOption.class),34,new SimpleFileVisitor<Path>(){
                @Override public FileVisitResult visitFile(Path path,BasicFileAttributes attr)throws IOException{
                    if(!attr.isRegularFile() || attr.size()>(8L<<30)-total[0] || hashes.size()>=25000)
                        throw new IOException("Unexpected installed disc contents");
                    String key=game.relativize(path).toString().replace(File.separatorChar,'/').toLowerCase(Locale.ROOT);
                    if(!StandardCharsets.US_ASCII.newEncoder().canEncode(key) || hashes.containsKey(key))throw new IOException("Invalid disc filename");
                    long read=0;
                    try(InputStream in=Files.newInputStream(path,LinkOption.NOFOLLOW_LINKS)){
                        for(int n;(n=in.read(block))!=-1;){
                            if(cancelled.getAsBoolean())throw new InterruptedIOException();
                            read+=n;if(read>attr.size())throw new IOException("Game files changed");
                            sha.update(block,0,n);
                        }
                    }
                    if(read!=attr.size())throw new IOException("Game files changed");
                    hashes.put(key,sha.digest());total[0]+=read;
                    return FileVisitResult.CONTINUE;
                }
            });
            byte[] exe=hashes.get("default.xbe");
            if(exe==null || !hex(exe).equals(expectedXbe))throw new IOException("Installed executable changed");
            DiscFacts result=new DiscFacts();
            result.put("files",hashes.size());result.put("payload_bytes",total[0]);result.put("disc_files_sha256",fileSet(hashes));
            return result;
        }
    }
}
