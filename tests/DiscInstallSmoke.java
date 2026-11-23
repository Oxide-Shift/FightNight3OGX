package org.oxideshift.corner;

import java.io.*;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Arrays;

public class DiscInstallSmoke {
    private static final int START=127, SECTOR=2048;
    private static void entry(ByteBuffer dir,int sector,int size,String name){
        byte[] bytes=name.getBytes(StandardCharsets.US_ASCII);
        dir.putInt(0).putInt(sector).putInt(size).put((byte)0).put((byte)bytes.length).put(bytes);
        dir.position((dir.position()+3)&~3);
    }
    public static void main(String[] args)throws Exception{
        byte[] xbe=Files.readAllBytes(Paths.get(args[0]));
        Path temp=Files.createTempDirectory("disc-range-smoke");
        try{
            int exeSector=66,assetSector=exeSector+(xbe.length+SECTOR-1)/SECTOR;
            byte[] bytes=new byte[(assetSector+1)*SECTOR];
            ByteBuffer disc=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
            disc.position(65536);disc.put("MICROSOFT*XBOX*MEDIA".getBytes(StandardCharsets.US_ASCII)).putInt(64).putInt(56);
            disc.position(64*SECTOR);entry(disc,exeSector,xbe.length,"DEFAULT.XBE");entry(disc,assetSector,3,"hello.txt");
            disc.position(exeSector*SECTOR);disc.put(xbe);disc.position(assetSector*SECTOR);disc.put(new byte[]{4,5,6});
            Path slice=temp.resolve("document");
            try(OutputStream out=Files.newOutputStream(slice)){out.write(new byte[START]);out.write(bytes);out.write(new byte[17]);}
            Path direct=Files.createDirectory(temp.resolve("direct")),failed=Files.createDirectory(temp.resolve("retry"));
            try(FileChannel file=FileChannel.open(slice)){
                String result=GameLibrary.importDisc(file,START,bytes.length,direct,s->{},(exe,status)->{
                    if(!Arrays.equals(Files.readAllBytes(exe),xbe))throw new AssertionError("Partial executable");
                    return "compiled";
                });
                if(!"compiled".equals(result) || Files.exists(direct.resolve("retail/source.iso")) ||
                   !Arrays.equals(new byte[]{4,5,6},Files.readAllBytes(direct.resolve("retail/game/hello.txt"))))throw new AssertionError();
                try{GameLibrary.importDisc(file,START,bytes.length,failed,s->{},(exe,status)->{throw new IOException("test compiler failure");});
                    throw new AssertionError("Compiler error lost");
                }catch(IOException expected){if(!expected.getMessage().equals("test compiler failure"))throw expected;}
                if(!Files.isRegularFile(failed.resolve("retail/game/default.xbe")) || Files.exists(failed.resolve("disc-copy.tmp")))
                    throw new AssertionError("Cannot retry preparation");
            }
            Path pipe=Files.createDirectory(temp.resolve("pipe"));
            GameLibrary.importDisc(new ByteArrayInputStream(bytes),bytes.length,pipe,s->{});
            if(!Arrays.equals(Files.readAllBytes(pipe.resolve("retail/game/default.xbe")),xbe))throw new AssertionError("Stream fallback");
            System.out.println("Direct sliced ISO, streaming fallback and preparation retry passed");
        }finally{GameLibrary.clearStage(temp);}
    }
}
