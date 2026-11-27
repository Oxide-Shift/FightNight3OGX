package org.oxideshift.corner;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

/** Run on a private copy of a disc's overlay.viv: Career Central gains FIGHT RECORD once, idempotently, and nothing
 *  else in the archive changes. Args: source game dir (with GUI/overlay.viv), scratch dir. */
public final class CareerMenuSmoke {
    static void check(boolean pass,String message){if(!pass)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        Path source=Paths.get(args[0]),work=Paths.get(args[1]);
        Path archive=work.resolve("GUI/overlay.viv");Files.createDirectories(archive.getParent());
        Files.copy(source.resolve("GUI/overlay.viv"),archive);
        byte[] before=Files.readAllBytes(archive);
        check(CareerMenu.install(work),"install");
        byte[] once=Files.readAllBytes(archive);
        byte[] group=TraxArt.inflate(RetailMenu.member(once,CareerMenu.GROUP),4*1024*1024);
        byte[] apt=RetailMenu.member(group,"careercentral.apt"),pool=RetailMenu.member(group,"careercentral.const");
        check(RetailMenu.digest(apt).equals(CareerMenu.PATCHED_APT),"patched program hash");
        // The label and screen id are in the dictionary.
        String text=new String(pool,java.nio.charset.StandardCharsets.US_ASCII);
        check(text.contains("FIGHT RECORD\0") && text.contains("m_sCurrentScreenId\0") && text.contains("fnr3/record\0"),"dictionary strings");
        // Same length; the state items fall into the FIGHT RECORD element; the switch is re-aimed; new code in place.
        ByteBuffer b=ByteBuffer.wrap(apt).order(ByteOrder.LITTLE_ENDIAN);
        check(apt.length==0x1bd5f,"program length unchanged");
        for(int at:new int[]{0x18da9,0x18dd5,0x18e19,0x18e41}){int f=(at+4)&~3;check(f+4+b.getInt(f)==0x18e48,"state branch "+Integer.toHexString(at));}
        int[] sw={0x1982a,0x1983f,0x19853,0x19867,0x1986c},to={0x19d68,0x19a78,0x19ac4,0x19b10,0x19c80};
        for(int i=0;i<sw.length;i++){int f=(sw[i]+4)&~3;check(f+4+b.getInt(f)==to[i],"switch "+i);}
        check((apt[0x19d60]&255)==0x99 && (apt[0x19d6e]&255)==0x4f && 0x19d74+b.getInt(0x19d70)==0x19cb0,"screen request code");
        // The popularity block moved below the sixth row; Cash still inside the panel.
        check(Math.abs(b.getFloat(0x18114+36)-162.25f)<.01f && Math.abs(b.getFloat(0x18094+36)-311.4f)<.01f,"panel layout");
        // A second install changes nothing.
        check(CareerMenu.install(work),"second install");
        check(Arrays.equals(once,Files.readAllBytes(archive)),"idempotent");
        // Every other screen group is untouched.
        for(String other:new String[]{"screengroups/mainmenu/mainmenu.big","screengroups/careersummary/careersummary.big"})
            check(Arrays.equals(RetailMenu.member(before,other),RetailMenu.member(once,other)),"changed "+other);
        System.out.println("Career Central gains FIGHT RECORD once; program, dictionary and other groups verified");
    }
}
