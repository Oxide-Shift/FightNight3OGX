package org.oxideshift.corner;

import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;

/** An Xbox profile can own more than one container; title settings travel with each profile. */
final class ProfileSaves {
    final String folder;
    private long lastSaved;
    private final Set<String> containers=new HashSet<>();
    private ProfileSaves(String folder){this.folder=folder;}

    boolean includes(String area,Path relative,boolean directory){
        if(!area.equals("UDATA") || relative.toString().isEmpty())return true;
        return relative.getNameCount()==1 && !directory ||
                containers.contains(relative.getName(0).toString());
    }
    private static String label(Path meta)throws IOException{
        if(Files.size(meta)>8192)return "";
        byte[] bytes=Files.readAllBytes(meta);
        Charset charset=StandardCharsets.UTF_8;
        if(bytes.length>=2){
            if(bytes[0]==(byte)0xfe && bytes[1]==(byte)0xff)charset=StandardCharsets.UTF_16BE;
            else if(bytes[0]==(byte)0xff && bytes[1]==(byte)0xfe || bytes[1]==0)charset=StandardCharsets.UTF_16LE;
        }
        for(String line:new String(bytes,charset).replace("\ufeff","").split("[\r\n]")){
            int split=line.indexOf('=');
            if(split>=0 && line.substring(0,split).trim().equalsIgnoreCase("Name"))return line.substring(split+1).trim();
        }
        return "";
    }
    private static String folderName(String label,String id){
        String safe=label.replaceAll("[\\p{Cntrl}\\\\/:*?\"<>|]","_").replaceFirst("[. ]+$","");
        if(safe.isEmpty())return id;
        if(!safe.equals(label) || safe.length()>90 || safe.matches("(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(\\..*)?"))
            return safe.substring(0,Math.min(80,safe.length()))+"-"+id;
        return safe;
    }
    static List<ProfileSaves> list(Path slot)throws IOException{
        Path udata=GameLibrary.childIgnoreCase(slot,"UDATA");
        Path title=udata==null?null:GameLibrary.childIgnoreCase(udata,"45410086");
        Map<String,ProfileSaves> profiles=new TreeMap<>();
        Set<String> shared=new HashSet<>();
        if(title!=null)try(DirectoryStream<Path> slots=Files.newDirectoryStream(title)){
            for(Path dir:slots){
                if(!Files.isDirectory(dir,LinkOption.NOFOLLOW_LINKS))continue;
                String id=dir.getFileName().toString(),label="",payload="";long last=0;
                try(DirectoryStream<Path> files=Files.newDirectoryStream(dir)){
                    for(Path file:files){
                        if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))continue;
                        String name=file.getFileName().toString();
                        if(name.equalsIgnoreCase("SaveMeta.xbx"))label=label(file);
                        else if(!name.toLowerCase(Locale.ROOT).endsWith(".xbx") && Files.size(file)>0){
                            last=Math.max(last,Files.getLastModifiedTime(file).toMillis());
                            if(payload.isEmpty() || name.compareTo(payload)<0)payload=name;
                        }
                    }
                }
                if(payload.isEmpty())continue;
                if(label.isEmpty())label=payload;
                if(label.equalsIgnoreCase("EA SPORTS FN 3") || id.equalsIgnoreCase("C0E485A19522")){
                    shared.add(id);continue;
                }
                String name=folderName(label,id);
                ProfileSaves profile=profiles.computeIfAbsent(name,ProfileSaves::new);
                profile.containers.add(id);profile.lastSaved=Math.max(profile.lastSaved,last);
            }
        }
        for(ProfileSaves profile:profiles.values())profile.containers.addAll(shared);
        List<ProfileSaves> sorted=new ArrayList<>(profiles.values());
        sorted.sort(Comparator.comparingLong(p->p.lastSaved));
        return sorted;
    }
}
