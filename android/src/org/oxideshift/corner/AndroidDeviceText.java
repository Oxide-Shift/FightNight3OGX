package org.oxideshift.corner;

import java.io.IOException;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Updates English storage prompts in the supported imported disc revision. */
final class AndroidDeviceText {
    static void install(Path game)throws IOException{
        Path dir=game.resolve("LOCALE");
        if(!Files.isDirectory(dir))dir=game.resolve("locale");
        update(dir,"ENGLISH.loc",
                "ad6541f2eccf06192a5e2efa4cef0e77a924b5244e50fbade4378025909bec1d",
                "83f05a24f45a71b56fb5d419cbd518ede1a29858e78dac75d08a5e2d70d1f8b2",
                new int[]{452,1284,1846,2394,2864},new int[]{184,112,102,106,28},
                new String[]{"Free %1 more block(s) on your Android device to save.",
                        "Free space on your Android device to save.",
                        "Saving %1. Keep your Android device powered on.",
                        "Deleting %1. Keep your Android device powered on.",
                        "Device storage"});
        update(dir,"xloc_fe.eus",
                "53f5afc2c5a50b50029134360b81b924a9e1c110d539c6293919d73f5b65860a",
                "5a8f9445e975c7d840d039a42a59c0c484583dd2a34eb2b9c99700f49d29bb02",
                new int[]{64280,64762},new int[]{478,386},
                new String[]{"STORAGE SLOT %1S IS UNAVAILABLE ON YOUR ANDROID DEVICE. DISABLE AUTOSAVE AND CONTINUE WITHOUT SAVING?",
                        "STORAGE SLOT %1D IS UNAVAILABLE ON YOUR ANDROID DEVICE. CONTINUE WITHOUT SAVING? AUTOSAVE WILL BE DISABLED."});
    }
    private static void update(Path dir,String name,String original,String updated,int[] offsets,int[] lengths,String[] prompts)throws IOException{
        Path file=dir.resolve(name);
        if(!Files.isRegularFile(file))file=dir.resolve(name.toLowerCase(java.util.Locale.ROOT));
        if(!Files.isRegularFile(file))return;
        if(Files.size(file)>300000)throw new IOException("Unknown locale revision");
        byte[] bytes=Files.readAllBytes(file);
        String hash=RetailMenu.digest(bytes);
        if(hash.equals(updated))return;
        if(!hash.equals(original))throw new IOException("Unknown locale revision: "+name);
        for(int i=0;i<prompts.length;i++){
            byte[] text=prompts[i].getBytes(StandardCharsets.UTF_16LE);
            Arrays.fill(bytes,offsets[i],offsets[i]+lengths[i],(byte)0);
            System.arraycopy(text,0,bytes,offsets[i],text.length);
        }
        RetailMenu.verify(bytes,updated);
        Path temp=Files.createTempFile(dir,"device-text-",".tmp");
        try{
            Files.write(temp,bytes);
            Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        }finally{Files.deleteIfExists(temp);}
    }
}
