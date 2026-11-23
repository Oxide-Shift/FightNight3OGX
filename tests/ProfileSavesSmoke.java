package org.oxideshift.corner;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

public final class ProfileSavesSmoke {
    private static Path container(Path slot,String key,String profile,String data)throws IOException{
        Path folder=Files.createDirectories(slot.resolve("UDATA/45410086").resolve(key));
        Files.write(folder.resolve("SaveMeta.xbx"),("\ufeffName="+profile+"\r\n").getBytes(StandardCharsets.UTF_16LE));
        return Files.write(folder.resolve("boxer"),data.getBytes(StandardCharsets.UTF_8));
    }
    public static void main(String[] args)throws Exception{
        Path tmp=Files.createTempDirectory("fn3-profile-saves");
        try{
            Path slot=tmp.resolve("saves");
            Path matt=container(slot,"000000000001","Matt","career");
            container(slot,"000000000002","lorm","other career");
            container(slot,"000000000003","Matt","second");
            container(slot,"C0E485A19522","EA SPORTS FN 3","shared");
            Path league=slot.resolve("TDATA/45410086/rules");Files.createDirectories(league.getParent());Files.write(league,new byte[]{1});
            List<ProfileSaves> list=ProfileSaves.list(slot);list.sort(Comparator.comparing(p->p.folder));
            if(list.size()!=2 || !list.get(0).folder.equals("Matt") || !list.get(1).folder.equals("lorm"))throw new AssertionError();
            Set<String> awards=Collections.singleton("BIG_ROUND");
            String other=GameLibrary.saveFingerprint(slot,awards,list.get(1));
            String before=GameLibrary.saveFingerprint(slot,awards,list.get(0));
            Files.write(matt,new byte[]{9});
            if(!other.equals(GameLibrary.saveFingerprint(slot,awards,list.get(1))) ||
                    before.equals(GameLibrary.saveFingerprint(slot,awards,list.get(0))))throw new AssertionError("Profile isolation");
            ByteArrayOutputStream zip=new ByteArrayOutputStream();GameLibrary.exportSave(slot,zip,awards,list.get(0));
            Set<String> restored=new HashSet<>();
            String id=GameLibrary.importSave(new ByteArrayInputStream(zip.toByteArray()),tmp,restored::addAll,true);
            Path selected=tmp.resolve("save-slots").resolve(id);
            if(ProfileSaves.list(selected).size()!=1 || !ProfileSaves.list(selected).get(0).folder.equals("Matt") ||
                    !Files.exists(selected.resolve("UDATA/45410086/000000000003/boxer")) ||
                    Files.exists(selected.resolve("UDATA/45410086/000000000002")) ||
                    !Files.exists(selected.resolve("UDATA/45410086/C0E485A19522/boxer")) ||
                    !Files.exists(selected.resolve("TDATA/45410086/rules")) || !restored.equals(awards))throw new AssertionError("Restore contents");
            container(slot,"000000000004","Renée","accent");container(slot,"000000000005","../Other","unusable path");
            for(ProfileSaves profile:ProfileSaves.list(slot))if(profile.folder.contains("/") || profile.folder.equals(".."))throw new AssertionError();
            if(ProfileSaves.list(slot).stream().noneMatch(p->p.folder.equals("Renée")))throw new AssertionError();
            System.out.println("Separate Matt and lorm careers, same-name slots, metadata spelling, shared data and single-profile restore passed.");
        }finally{GameLibrary.clearStage(tmp);}
    }
}
