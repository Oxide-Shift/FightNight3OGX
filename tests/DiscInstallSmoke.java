package org.oxideshift.corner;

import java.io.*;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Arrays;
import java.util.zip.*;

public class DiscInstallSmoke {
    private static final int START=127, SECTOR=2048;
    private static void entry(ByteBuffer dir,int sector,int size,String name){
        byte[] bytes=name.getBytes(StandardCharsets.US_ASCII);
        dir.putInt(0).putInt(sector).putInt(size).put((byte)0).put((byte)bytes.length).put(bytes);
        dir.position((dir.position()+3)&~3);
    }
    private static void menuEntry(ByteBuffer disc){
        int start=disc.position();
        entry(disc,63,28,"GUI");disc.put(start+12,(byte)16);
        disc.putInt(65536+24,disc.position()-64*SECTOR);
        disc.position(63*SECTOR);entry(disc,65,64,"overlay.viv");
        disc.position(65*SECTOR);
        disc.put("BIGF".getBytes(StandardCharsets.US_ASCII)).putInt(64);
        disc.order(ByteOrder.BIG_ENDIAN).putInt(1).putInt(26).putInt(32).putInt(1).put((byte)'a').put((byte)0);
        disc.order(ByteOrder.LITTLE_ENDIAN);
    }
    private static void reject(Path root,byte[] image,String... messages)throws Exception{
        try{
            GameLibrary.importDisc(new ByteArrayInputStream(image),image.length,root,s->{});
            throw new AssertionError("Unexpectedly accepted an incompatible disc");
        }catch(IOException e){
            for(String part:messages)if(!e.getMessage().contains(part))throw new AssertionError(e);
        }
        if(Files.exists(root.resolve("retail")) || Files.exists(root.resolve("disc-copy.tmp")))
            throw new AssertionError("Failed import blocked a retry");
    }
    private static byte[] zip(byte[] image,String... members)throws IOException{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(ZipOutputStream out=new ZipOutputStream(bytes)){
            for(String name:members){
                out.putNextEntry(new ZipEntry(name));
                if(name.toLowerCase(java.util.Locale.ROOT).endsWith(".iso"))out.write(image);
                out.closeEntry();
            }
        }
        return bytes.toByteArray();
    }
    private static void fact(DiscFacts facts,String key,String expected){
        if(!java.util.Objects.equals(expected,facts.values.getProperty(key)))throw new AssertionError(key+" expected "+expected+" got "+facts.values.getProperty(key));
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
            menuEntry(disc);
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
            Path module=temp.resolve("installed.so"),candidate=temp.resolve("candidate.so");
            ByteBuffer elf=ByteBuffer.allocate(128).order(ByteOrder.LITTLE_ENDIAN);
            elf.putInt(0,0x464c457f).put(4,(byte)2).put(5,(byte)1).putShort(16,(short)3).putShort(18,(short)183);
            elf.putLong(32,64).putShort(54,(short)56).putShort(56,(short)1);
            elf.putInt(64,1).putInt(68,5).putLong(72,120).putLong(96,8).putLong(104,8);
            Files.write(module,elf.array());
            if(!DiscBuild.arm64Module(module.toFile()))throw new AssertionError("Valid prepared module refused");
            Files.write(module,Arrays.copyOf(elf.array(),126));
            if(DiscBuild.arm64Module(module.toFile()))throw new AssertionError("Truncated executable segment accepted");
            elf.putShort(18,(short)62);Files.write(module,elf.array());
            if(DiscBuild.arm64Module(module.toFile()))throw new AssertionError("Wrong prepared ABI accepted");
            Files.write(module,new byte[]{1});
            Files.write(candidate,new byte[]{2});
            try{DiscBuild.verifyAndPublish(candidate.toFile(),module.toFile(),f->false);throw new AssertionError("Bad module accepted");}
            catch(IOException expected){}
            if(Files.exists(candidate) || Files.readAllBytes(module)[0]!=1)throw new AssertionError("Failed build displaced installed game");
            Files.write(candidate,new byte[]{3});
            DiscBuild.verifyAndPublish(candidate.toFile(),module.toFile(),f->Files.readAllBytes(f.toPath())[0]==3);
            if(Files.exists(candidate) || Files.readAllBytes(module)[0]!=3)throw new AssertionError("Verified replacement missing");
            if(!DiscBuild.verifyPayload(module.toFile()))throw new AssertionError("Published module receipt missing");
            Files.write(module,new byte[]{4});
            if(DiscBuild.verifyPayload(module.toFile()))throw new AssertionError("Same-length mutation reached the game child");
            if(Files.exists(module.resolveSibling("installed.so.sha256")))throw new AssertionError("Damaged receipt survived");
            Path pipe=Files.createDirectory(temp.resolve("pipe"));
            GameLibrary.importDisc(new ByteArrayInputStream(bytes),bytes.length,pipe,s->{});
            if(Files.exists(pipe.resolve("retail/source.iso")) || !Arrays.equals(Files.readAllBytes(pipe.resolve("retail/game/default.xbe")),xbe))throw new AssertionError("Stream fallback");
            int exe=exeSector*SECTOR;
            int cert=exe+disc.getInt(exe+0x118)-disc.getInt(exe+0x104);
            byte[] packed=zip(bytes,"docs/readme.txt","My Disc/BOXER.XISO.ISO");
            Path archiveInstall=Files.createDirectory(temp.resolve("zip-stream"));
            GameLibrary.importDisc(new ByteArrayInputStream(packed),-1,archiveInstall,s->{});
            Path archiveRetail=archiveInstall.resolve("retail");
            DiscFacts directFacts=DiscFacts.installed(direct),zippedFacts=DiscFacts.installed(archiveInstall);
            fact(directFacts,"container","raw");fact(directFacts,"layout","xiso");fact(directFacts,"selected_sha1",null);
            fact(zippedFacts,"container","zip");fact(zippedFacts,"layout","xiso");fact(zippedFacts,"selected_bytes",String.valueOf(packed.length));
            fact(zippedFacts,"selected_sha1",DiscFacts.hex(java.security.MessageDigest.getInstance("SHA-1").digest(packed)));
            fact(zippedFacts,"image_sha1",DiscFacts.hex(java.security.MessageDigest.getInstance("SHA-1").digest(bytes)));
            fact(directFacts,"source_xbe_sha256",DiscImage.HASH);fact(directFacts,"installed_xbe_sha256",DiscImage.HASH);
            fact(zippedFacts,"disc_files_sha256",directFacts.values.getProperty("disc_files_sha256"));
            Path legacy=Files.createDirectories(temp.resolve("legacy/game"));
            Files.copy(direct.resolve("retail/game/default.xbe"),legacy.resolve("default.xbe"));
            DiscFacts upgraded=DiscFacts.installed(legacy.getParent());
            fact(upgraded,"origin","installed_only");fact(upgraded,"title","45410086");fact(upgraded,"installed_xbe_sha256",DiscImage.HASH);
            for(String key:new String[]{"source_xbe_sha256","container","disc_files_sha256","selected_bytes"})fact(upgraded,key,null);
            DiscFacts installedFiles=DiscFacts.checkInstalledFiles(direct,DiscImage.HASH,()->false);
            fact(installedFiles,"disc_files_sha256",directFacts.values.getProperty("disc_files_sha256"));
            fact(installedFiles,"files",directFacts.values.getProperty("files"));
            try{DiscFacts.checkInstalledFiles(direct,DiscImage.HASH,()->true);throw new AssertionError("ignored cancel");}
            catch(InterruptedIOException expected){}
            try{DiscFacts.checkInstalledFiles(direct,"old",()->false);throw new AssertionError("reported changed installation");}
            catch(IOException expected){}
            if(Files.exists(archiveRetail.resolve("source.zip")) || Files.exists(archiveRetail.resolve("source.iso")) ||
                    !Arrays.equals(xbe,Files.readAllBytes(archiveRetail.resolve("game/default.xbe"))))throw new AssertionError("ZIP stream");
            Path zipSlice=temp.resolve("zip-document");
            try(OutputStream out=Files.newOutputStream(zipSlice)){out.write(new byte[START]);out.write(packed);out.write(new byte[23]);}
            Path selectedZip=Files.createDirectory(temp.resolve("zip-slice"));
            try(FileChannel file=FileChannel.open(zipSlice)){
                file.position(7);
                GameLibrary.importDisc(file,START,packed.length,selectedZip,s->{},null);
                if(file.position()!=7 || !Arrays.equals(new byte[]{4,5,6},Files.readAllBytes(selectedZip.resolve("retail/game/hello.txt"))))
                    throw new AssertionError("ZIP provider slice");
            }
            Path rejectedZip=Files.createDirectory(temp.resolve("zip-retry"));
            byte[] incomplete=bytes.clone();incomplete[63*SECTOR+14]=(byte)'x';
            reject(rejectedZip,incomplete,"GUI/overlay.viv","missing");
            byte[] truncatedMenu=bytes.clone();ByteBuffer.wrap(truncatedMenu).order(ByteOrder.LITTLE_ENDIAN).putInt(63*SECTOR+8,32);
            reject(rejectedZip,truncatedMenu,"GUI/overlay.viv","incomplete");
            Path lowSpace=Files.createDirectory(temp.resolve("first-compile-space"));
            DiscImport diagnosis=new DiscImport(message->{});
            try{GameLibrary.importDisc(new ByteArrayInputStream(bytes),bytes.length,lowSpace,diagnosis,
                    (gameExe,status)->{throw new DiscImport.Failure(DiscImport.Reason.SPACE,"Compilation ran out of internal storage");});
                throw new AssertionError("Fresh compilation failure lost");}
            catch(DiscImport.Failure failure){
                if(diagnosis.explain(failure).reason!=DiscImport.Reason.SPACE || !"prepare".equals(diagnosis.stage)
                        || !Files.isRegularFile(lowSpace.resolve("retail/game/default.xbe")))
                    throw new AssertionError("Fresh install lost the compiler reason, stage, or verified disc");
            }
            Path saved=Files.createDirectory(pipe.resolve("saves")).resolve("career");
            Files.write(saved,new byte[]{77});
            Path overlay=pipe.resolve("retail/game/GUI/overlay.viv");
            Path installedGame=pipe.resolve("retail/game");
            if(GameLibrary.needsDiscRepair(installedGame))throw new AssertionError("Healthy disc offered repair");
            Files.delete(overlay);
            if(!GameLibrary.needsDiscRepair(installedGame))throw new AssertionError("Repair action hidden for missing menu");
            try{GameLibrary.importDisc(new ByteArrayInputStream(incomplete),incomplete.length,pipe,s->{});throw new AssertionError();}
            catch(DiscImport.Failure failure){if(failure.reason!=DiscImport.Reason.MENU_ASSET)throw failure;}
            if(!Files.exists(pipe.resolve("retail/game/default.xbe")))throw new AssertionError("Failed repair removed existing game");
            try{GameLibrary.importDisc(new ByteArrayInputStream(bytes),bytes.length,pipe,s->{},
                    (gameExe,status)->{throw new IOException("repair compiler failed");});throw new AssertionError();}
            catch(IOException expected){if(!"repair compiler failed".equals(expected.getMessage()))throw expected;}
            if(Files.exists(overlay) || Files.readAllBytes(saved)[0]!=77)throw new AssertionError("Failed build displaced repair target");
            Files.createDirectories(pipe.resolve("disc-copy.tmp/game"));
            Files.write(pipe.resolve("disc-copy.tmp/source.iso"),new byte[]{42});
            GameLibrary.clearInterruptedImport(pipe);
            if(Files.exists(pipe.resolve("disc-copy.tmp")) || !Files.exists(saved))throw new AssertionError("Orphan staging cleanup");
            Files.move(pipe.resolve("retail"),pipe.resolve("disc-repair.previous"));
            GameLibrary.recoverDisc(pipe);
            GameLibrary.importDisc(new ByteArrayInputStream(bytes),bytes.length,pipe,s->{});
            if(Files.size(overlay)!=64 || Files.readAllBytes(saved)[0]!=77 || Files.exists(pipe.resolve("disc-repair.previous")))throw new AssertionError("Disc repair");
            if(GameLibrary.needsDiscRepair(installedGame))throw new AssertionError("Repair action persisted after successful repair");
            reject(rejectedZip,zip(bytes,"one.iso","two.iso"),"multiple disc images");
            reject(rejectedZip,zip(bytes,"readme.txt"),"no Xbox ISO");
            byte[] wrongCrc=packed.clone();
            for(int pos=wrongCrc.length-46;pos>=0;pos--){
                if(ByteBuffer.wrap(wrongCrc).order(ByteOrder.LITTLE_ENDIAN).getInt(pos)==0x02014b50){
                    wrongCrc[pos+16]^=1;break;
                }
            }
            reject(rejectedZip,wrongCrc,"integrity");
            reject(rejectedZip,zip(new byte[100000],"wrong.iso"),"No Xbox disc filesystem");
            byte[] playstation=new byte[100000];
            System.arraycopy("\u0001CD001\u0001\u0000PLAYSTATION".getBytes(StandardCharsets.US_ASCII),0,playstation,32768,19);
            reject(rejectedZip,playstation,"PlayStation");
            byte[] otherPlatform=bytes.clone();
            System.arraycopy("DEFAULT.XEX".getBytes(StandardCharsets.US_ASCII),0,otherPlatform,64*SECTOR+14,11);
            reject(rejectedZip,otherPlatform,"Xbox 360","default.xex");
            byte[] xgd3=new byte[0x2080000+otherPlatform.length];
            System.arraycopy(otherPlatform,0,xgd3,0x2080000,otherPlatform.length);
            reject(rejectedZip,xgd3,"Xbox 360","default.xex");
            // A refused executable must always carry its SHA-256 into Copy details.
            byte[] otherTitle=bytes.clone();
            ByteBuffer.wrap(otherTitle).order(ByteOrder.LITTLE_ENDIAN).putInt(cert+8,0x4d530004);
            try{
                GameLibrary.importDisc(new ByteArrayInputStream(otherTitle),otherTitle.length,rejectedZip,s->{});
                throw new AssertionError("Accepted another Xbox title");
            }catch(DiscImport.Failure e){
                if(!e.getMessage().startsWith("Different game") || e.hash==null || !e.hash.matches("[0-9a-f]{64}"))
                    throw new AssertionError("Wrong-title refusal without label or default.xbe SHA-256: "+e.getMessage()+" / "+e.hash);
            }
            byte[] renamed=bytes.clone();
            Arrays.fill(renamed,cert+12,cert+0x5c,(byte)0);
            byte[] label="Fight Night Round 3".getBytes(StandardCharsets.UTF_16LE);
            System.arraycopy(label,0,renamed,cert+12,label.length);
            Path renamedInstall=Files.createDirectory(temp.resolve("renamed"));
            GameLibrary.importDisc(new ByteArrayInputStream(renamed),renamed.length,renamedInstall,s->{});
            DiscFacts restored=DiscFacts.installed(renamedInstall);
            fact(restored,"xbe_selection","certificate_restored");fact(restored,"installed_xbe_sha256",DiscImage.HASH);
            if(restored.values.getProperty("source_xbe_sha256").equals(DiscImage.HASH)
                    || restored.values.getProperty("disc_files_sha256").equals(directFacts.values.getProperty("disc_files_sha256")))
                throw new AssertionError("Original disc identity lost during certificate restoration");
            if(!Arrays.equals(xbe,Files.readAllBytes(renamedInstall.resolve("retail/game/default.xbe"))))throw new AssertionError("Dashboard title restore");
            renamed[exe+5000]^=1;
            reject(rejectedZip,renamed,"SHA-256");
            disc.putInt(64*SECTOR+8,xbe.length-1824);
            Path trimmed=Files.createDirectory(temp.resolve("trimmed"));
            GameLibrary.importDisc(new ByteArrayInputStream(bytes),bytes.length,trimmed,s->{});
            if(!Arrays.equals(xbe,Files.readAllBytes(trimmed.resolve("retail/game/default.xbe"))))
                throw new AssertionError("Compiler input lost retail alignment");
            disc.putInt(cert+0x9c,0x80000001);
            disc.putInt(cert+0xa0,7);
            Path patched=Files.createDirectory(temp.resolve("flags"));
            GameLibrary.importDisc(new ByteArrayInputStream(bytes),bytes.length,patched,s->{},(path,status)->{
                if(!Arrays.equals(xbe,Files.readAllBytes(path)))throw new AssertionError("Compiler saw a modified XBE");
                return "compiled";
            });
            if(disc.getInt(cert+0xa0)!=7)throw new AssertionError("Modified input");
            Path unknown=Files.createDirectory(temp.resolve("unknown"));
            disc.putInt(64*SECTOR+8,xbe.length-1825);
            reject(unknown,bytes,"SHA-256");
            disc.putInt(64*SECTOR+8,xbe.length);
            disc.putInt(cert+0xa0,4);
            disc.putInt(cert+0xac,1);
            byte[] digest=java.security.MessageDigest.getInstance("SHA-256").digest(Arrays.copyOfRange(bytes,exe,exe+xbe.length));
            String hash=String.format(java.util.Locale.ROOT,"%064x",new java.math.BigInteger(1,digest));
            reject(unknown,bytes,"PAL","45410086","XBE version 1",hash);
            disc.putInt(64*SECTOR+8,xbe.length-1);
            reject(unknown,bytes,"PAL","SHA-256");
            disc.putInt(64*SECTOR+8,xbe.length);
            disc.putInt(cert+8,0x45410055);
            reject(unknown,bytes,"45410055");
            disc.putInt(exe+0x118,0xffff0000);
            reject(unknown,bytes,"certificate offset");
            disc.position(exe);disc.put(xbe);bytes[exe+xbe.length-1]^=1;
            reject(unknown,bytes,"USA/Canada","SHA-256");
            int backupSector=assetSector+1;
            byte[] backedUp=Arrays.copyOf(bytes,backupSector*SECTOR+xbe.length);
            ByteBuffer backupDisc=ByteBuffer.wrap(backedUp).order(ByteOrder.LITTLE_ENDIAN);
            backupDisc.position(64*SECTOR);
            entry(backupDisc,exeSector,xbe.length,"DEFAULT.XBE");
            entry(backupDisc,assetSector,3,"hello.txt");
            entry(backupDisc,backupSector,xbe.length,"Default.XBE_orig");
            menuEntry(backupDisc);
            System.arraycopy(xbe,0,backedUp,backupSector*SECTOR,xbe.length);
            Path original=Files.createDirectory(temp.resolve("original-backup"));
            GameLibrary.importDisc(new ByteArrayInputStream(backedUp),backedUp.length,original,s->{});
            fact(DiscFacts.installed(original),"xbe_selection","backup");
            if(!Arrays.equals(xbe,Files.readAllBytes(original.resolve("retail/game/default.xbe"))))
                throw new AssertionError("Original backup was not restored");
            backupDisc.putInt(cert+8,0x45410055);
            reject(unknown,backedUp,"45410055");
            backupDisc.putInt(cert+8,0x45410086);
            backedUp[backedUp.length-1]^=1;
            reject(unknown,backedUp,"SHA-256");
            byte[] archive={0x37,0x7a,(byte)0xbc,(byte)0xaf,0x27,0x1c};
            DiscImport attempt=new DiscImport(s->{});
            attempt.filenameHint("private directory/Fight Night.1.iso");
            if(!attempt.format.equals("split") || attempt.explain(new DiscImport.Failure(DiscImport.Reason.FILESYSTEM,"")).reason!=DiscImport.Reason.SPLIT)
                throw new AssertionError("Split image guidance");
            if(attempt.explain(new FileNotFoundException("content://private-user-file")).getMessage().contains("private-user-file"))
                throw new AssertionError("Provider URI in diagnostic");
            try{DiscImage.checkContainer(archive);throw new AssertionError();}
            catch(DiscImport.Failure failure){if(failure.reason.code!=-101 || !"7z".equals(failure.format))throw new AssertionError();}
            try{GameLibrary.importDisc(new ByteArrayInputStream(renamed),renamed.length,rejectedZip,attempt);throw new AssertionError();}
            catch(DiscImport.Failure failure){
                if(failure.reason.code!=-106 || !"45410086".equals(failure.title) || !"00000001".equals(failure.region)
                        || !"2".equals(failure.revision) || failure.hash.length()!=64 || !"verify".equals(attempt.stage) || !"xiso".equals(attempt.format))
                    throw new AssertionError("Missing structured verification diagnostics");
            }
            reject(unknown,archive,"7z archive","Extract");
            Path wrapped=Files.write(temp.resolve("archive"),archive);
            try(FileChannel file=FileChannel.open(wrapped)){
                try{new DiscImage(file);throw new AssertionError("Accepted compressed archive");}
                catch(IOException expected){if(!expected.getMessage().contains("7z archive"))throw expected;}
            }
            System.out.println("Disc import, compiler handoff, certificate variants, diagnostics and retry passed");
        }finally{GameLibrary.clearStage(temp);}
    }
}
