package org.oxideshift.corner;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

public final class HakuSaveSmoke {
    private static final String SLOT="123F0F3C1212";
    private static final byte[] META=("\ufeffTitleName=EA SPORTS FN 3\r\n").getBytes(StandardCharsets.UTF_16LE);
    private static final byte[] PROFILE={23,8,91,4};
    private static void put(ZipOutputStream out,String name,byte[] data)throws IOException{
        out.putNextEntry(new ZipEntry(name));out.write(data);out.closeEntry();
    }
    private static byte[] archive(String prefix,boolean sibling)throws IOException{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(ZipOutputStream z=new ZipOutputStream(bytes)){
            put(z,"hakuX.properties","kind=saves\n".getBytes(StandardCharsets.UTF_8));
            if(!prefix.isEmpty())put(z,prefix,new byte[0]);
            put(z,prefix+"TitleMeta.xbx",META);
            put(z,prefix+SLOT+"/",new byte[0]);
            put(z,prefix+SLOT+"/NAMEAS",PROFILE);
            if(sibling)put(z,"UDATA/abcdef01/DEADBEEF0102/NAMEAS",new byte[]{12});
        }
        return bytes.toByteArray();
    }
    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("hakux-save-test");
        try{
            String[] layouts={"UDATA/45410086/","Fight Night saves/E/udata/45410086/",
                    "hdd/Partition1/UDATA/45410086/","45410086/","My boxer/",""};
            Path first=null;
            for(String prefix:layouts){
                byte[] bytes=archive(prefix,true);
                Path picked=root.resolve("Fight Night saves.zip");Files.write(picked,bytes);
                try(ZipFile zip=new ZipFile(picked.toFile())){
                    if(!GameLibrary.hasFightSave(zip))throw new AssertionError("Folder discovery: "+prefix);
                }
                Path imported=root.resolve("save-slots").resolve(GameLibrary.importSave(new ByteArrayInputStream(bytes),root));
                Path profile=imported.resolve("UDATA/45410086/"+SLOT+"/NAMEAS");
                if(!Arrays.equals(PROFILE,Files.readAllBytes(profile))||Files.exists(imported.resolve("UDATA/abcdef01")))throw new AssertionError(prefix);
                if(first==null)first=profile;
            }
            Path active=first.getParent().getParent().getParent().getParent();
            Path titleData=active.resolve("TDATA/45410086/data.bin");
            Files.createDirectories(titleData.getParent());Files.write(titleData,new byte[]{3,2,1});
            Files.createDirectories(active.resolve("UDATA/abcdef01"));Files.write(active.resolve("UDATA/abcdef01/other"),PROFILE);
            ByteArrayOutputStream exported=new ByteArrayOutputStream();GameLibrary.exportSave(active,exported);
            Set<String> awards=new TreeSet<>(Arrays.asList("SPLIT_ROUND","BIG_ROUND"));
            String fingerprint=GameLibrary.saveFingerprint(active,awards);
            if(fingerprint.isEmpty() || fingerprint.equals(GameLibrary.saveFingerprint(active,Collections.emptySet())))throw new AssertionError("Awards were not tracked");
            ByteArrayOutputStream backup=new ByteArrayOutputStream();GameLibrary.exportSave(active,backup,awards);
            Set<String> restored=new HashSet<>();
            String restoredSlot=GameLibrary.importSave(new ByteArrayInputStream(backup.toByteArray()),root,restored::addAll);
            if(!restored.equals(awards) || !Arrays.equals(PROFILE,Files.readAllBytes(root.resolve("save-slots").resolve(restoredSlot)
                    .resolve("UDATA/45410086/"+SLOT+"/NAMEAS"))))throw new AssertionError("Career and achievements backup round trip");
            Files.write(titleData,new byte[]{3,2,1,0});
            if(fingerprint.equals(GameLibrary.saveFingerprint(active,awards)))throw new AssertionError("Save change was missed");
            Path portable=root.resolve("portable.zip");Files.write(portable,exported.toByteArray());
            try(ZipFile z=new ZipFile(portable.toFile())){
                if(z.getEntry("hakuX.properties")!=null || z.getEntry("UDATA/abcdef01/other")!=null ||
                   z.getEntry("UDATA/45410086/"+SLOT+"/NAMEAS")==null || z.getEntry("TDATA/45410086/data.bin")==null)
                    throw new AssertionError("Portable save layout");
            }
            Path roundTrip=root.resolve("save-slots").resolve(GameLibrary.importSave(new ByteArrayInputStream(exported.toByteArray()),root));
            if(!Arrays.equals(PROFILE,Files.readAllBytes(roundTrip.resolve("UDATA/45410086/"+SLOT+"/NAMEAS"))) ||
               !Arrays.equals(META,Files.readAllBytes(roundTrip.resolve("UDATA/45410086/TitleMeta.xbx"))) ||
               !Arrays.equals(new byte[]{3,2,1},Files.readAllBytes(roundTrip.resolve("TDATA/45410086/data.bin"))))throw new AssertionError("Export round trip");
            try{GameLibrary.exportSave(root.resolve("empty"),new ByteArrayOutputStream());throw new AssertionError("Empty export");}
            catch(IOException expected){}
            if(args.length==1)Files.copy(portable,Paths.get(args[0]),StandardCopyOption.REPLACE_EXISTING);
            for(String bad:new String[]{"Other/UDATA/abcdef01/","hdd/Partition2/UDATA/45410086/","../UDATA/45410086/"}){
                try{GameLibrary.importSave(new ByteArrayInputStream(archive(bad,false)),root);throw new AssertionError(bad);}
                catch(IOException expected){}
            }
            if(!Arrays.equals(PROFILE,Files.readAllBytes(first)))throw new AssertionError("Original save changed");
            System.out.println("Export marker, game discovery, six ZIP layouts, unrelated titles and failed import isolation passed.");
        }finally{GameLibrary.clearStage(root);}
    }
}
