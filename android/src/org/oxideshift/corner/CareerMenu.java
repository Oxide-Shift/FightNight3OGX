package org.oxideshift.corner;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/** Adds FIGHT RECORD to the retail Career Central menu, on the player's own disc files, the same way the main menu
 *  gained ACHIEVEMENTS and MUSIC: the menu's program asks the runtime for an extension screen and the app opens the
 *  page over the game (FNR3/1 RECORD OPEN, see fight_data.c). The movie keeps its exact length: code past the
 *  original end is not loaded (Thor QA 2026-11-27: a first version that appended code left Career Central inert).
 *
 *  careercentral.apt (EA APT actions, US disc):
 *   - InitCCentral adds one career-state item (Sign Contract / Train / Fight / New Career), then $4907, $4906,
 *     $1205, $2300. The state chain ends in an unreachable addElement('UH-OH') at 0x18e48 that falls through to
 *     0x18e55. The four "item added" branches now go to 0x18e48 instead, and 'UH-OH' reads FIGHT RECORD, so it
 *     becomes item 1.
 *   - mainEventHandler's ADVANCE switch tests the index (register 0) against 0-4. With the new item, ==1 goes to
 *     new code, ==2/3/4 to the old 1/2/3 cases, and the default (now 5) to the old Options case.
 *   - The new code sits where two debug traces of the hype frames were (0x19d60, 28 bytes): a branch over it keeps
 *     that path, and inside it assigns m_sCurrentScreenId = "fnr3/record" on a temporary object, then continues
 *     at the switch end 0x19cb0.
 *  careercentral.const: the dictionary entries for 'UH-OH' and two CAC trace strings are re-pointed to "FIGHT
 *  RECORD", "m_sCurrentScreenId" and "fnr3/record". State is recognised by content hash; overlay stamps untouched. */
final class CareerMenu {
    /** True once this session's Career Central carries the FIGHT RECORD row (six rows instead of five). */
    static volatile boolean active;
    static final String GROUP="screengroups/careercentral/careercentral.big";
    static final String RETAIL_APT="13e6a70dd0f69d5bc892f1d09a4f9737fc994f497502dbcabe722537ef352a2f";
    static final String RETAIL_CONST="debafb00cd3f8a1405441281d42bac5bbd1d3770a8e633c490581e0cfe0375f0";
    /** The first, appended-code version (QA builds only); repaired back to retail before patching. */
    static final String V1_APT="fc5ffc46fb88ce573240f2caffd9ee9e74d6f04c013942e67707a294bd7b658f";
    /** The same-length entry without the panel layout move (QA builds 2026-11-27); gains the layout on install. */
    static final String V2_APT="759ea169e55cc3a25df743bd4b4874c20a8e2e32c1584ed6f4cbc7ac62619896";
    static final String PATCHED_APT="b7835ed012810afc9ffbec0566431530aeed6dfcba2bdfb1ae27c476c1fc568e";
    static final String LABEL="FIGHT RECORD",KEY="m_sCurrentScreenId",SCREEN="fnr3/record";
    private static final int APT_LENGTH=0x1bd5f,CONST_LENGTH=8596,POOL=0x1a748;
    private static final int UHOH=0x18e48,AFTER_STATE=0x18e55;
    private static final int[] STATE_BRANCHES={0x18da9,0x18dd5,0x18e19,0x18e41};
    // Switch branches (opcode addresses) and their retail and new targets.
    private static final int[] SWITCH={0x1982a,0x1983f,0x19853,0x19867,0x1986c};
    private static final int CODE=0x19d60,CODE_END=0x19d7c,SWITCH_END=0x19cb0;
    private static final int[] RETAIL_TARGETS={0x19a78,0x19ac4,0x19b10,0x19c80,SWITCH_END};
    private static final int[] NEW_TARGETS={CODE+8,0x19a78,0x19ac4,0x19b10,0x19c80};
    private static final int LABEL_SLOT=80,KEY_SLOT=197,SCREEN_SLOT=198;
    private static final byte[] UHOH_ADD={(byte)0xa2,0x50,0x5a,(byte)0xae,0x01,(byte)0xaf,0x02,(byte)0xaf,0x31,(byte)0xaf,0x49,(byte)0xb2,0x4a};
    private static final String[] RETAIL_TEXT={"UH-OH","< CAC > - CCentralScreen - CAC_CAREER_CHAMP_EDIT","< CAC > - CCentralScreen - CAC_CAREER_LEGEND_EDIT"};
    // V1 repair data.
    private static final int V1_ADD_AT=0x18eda,V1_ADD_END=0x18ee7,V1_DEFAULT=0x1986c;
    private static final byte[] ADD_2300={(byte)0xa2,0x5a,0x5a,(byte)0xae,0x01,(byte)0xaf,0x02,(byte)0xaf,0x31,(byte)0xaf,0x49,(byte)0xb2,0x4a};
    private static final int[] V1_SLOTS={185,197,198};
    private static final String[] V1_TEXT={"UH-OH: Shouldn't ever hit here.  Remove when we are sure of that",RETAIL_TEXT[1],RETAIL_TEXT[2]};

    private CareerMenu(){}

    /** Patch the player's overlay once. Returns true when the menu has the entry (already or now). */
    static synchronized boolean install(Path game)throws IOException{
        Path archive=TraxArt.guiFile(game.toFile(),"overlay.viv").toPath();
        byte[] outer=Files.readAllBytes(archive);
        byte[] group=TraxArt.inflate(RetailMenu.member(outer,GROUP),4*1024*1024);
        byte[] apt=RetailMenu.member(group,"careercentral.apt"),pool=RetailMenu.member(group,"careercentral.const");
        String aptHash=RetailMenu.digest(apt);
        if(aptHash.equals(PATCHED_APT))return true;
        if(aptHash.equals(V2_APT)){
            // Entry already there (first device builds): only the panel layout moves.
            byte[] moved=layout(apt);
            group=RetailMenu.replace(group,"careercentral.apt",moved);
            RetailMenu.writeArchive(archive,RetailMenu.replace(outer,GROUP,group));
            return true;
        }
        if(aptHash.equals(V1_APT)){byte[][] retail=repairV1(apt,pool);apt=retail[0];pool=retail[1];aptHash=RetailMenu.digest(apt);}
        if(!aptHash.equals(RETAIL_APT) || !RetailMenu.digest(pool).equals(RETAIL_CONST))
            throw new IOException("Unrecognized career menu revision");
        byte[][] patched=patch(apt,pool);
        group=RetailMenu.replace(RetailMenu.replace(group,"careercentral.apt",patched[0]),"careercentral.const",patched[1]);
        RetailMenu.writeArchive(archive,RetailMenu.replace(outer,GROUP,group));
        return true;
    }

    /** {apt, const} with the entry added. Pure; validates every byte it relies on. Same apt length. */
    static byte[][] patch(byte[] apt,byte[] pool)throws IOException{
        if(apt.length!=APT_LENGTH || !Arrays.equals(Arrays.copyOfRange(apt,UHOH,UHOH+UHOH_ADD.length),UHOH_ADD))
            throw new IOException("Unexpected career menu program");
        byte[] out=apt.clone();ByteBuffer code=RetailMenu.little(out);
        for(int at:STATE_BRANCHES)retarget(code,0x99,at,AFTER_STATE,UHOH);
        for(int i=0;i<SWITCH.length;i++)retarget(code,i==SWITCH.length-1?0x99:0x9d,SWITCH[i],RETAIL_TARGETS[i],NEW_TARGETS[i]);
        // The hype-frame traces become a branch over the new code, then the code itself.
        if(out[CODE]!=(byte)0xa2 || out[CODE_END-1]!=0x26)throw new IOException("Unexpected career trace block");
        out[CODE]=(byte)0x99;out[CODE+1]=out[CODE+2]=out[CODE+3]=0;code.putInt(CODE+4,CODE_END-(CODE+8));
        int p=CODE+8;
        byte[] assign={0x59,0x43,(byte)0xa2,(byte)KEY_SLOT,(byte)0xa2,(byte)SCREEN_SLOT,0x4f,(byte)0x99};
        System.arraycopy(assign,0,out,p,assign.length);p+=assign.length;   // 0x99 lands at 0x19d6f
        if(p%4!=0)throw new IOException("Career code alignment");
        code.putInt(p,SWITCH_END-(p+4));p+=4;
        while(p<CODE_END)out[p++]=0x04;
        byte[] dictionary=repoint(apt,pool,new int[]{LABEL_SLOT,KEY_SLOT,SCREEN_SLOT},RETAIL_TEXT,new String[]{LABEL,KEY,SCREEN});
        return new byte[][]{layout(out),dictionary};
    }
    /** Placement records in CcMainControls (offset of the record; y is the float at +36) and the y each moves to.
     *  With six menu rows the sixth (Options) sat on the POPULARITY label: the popularity block (its six background
     *  shapes, label, belt bar and UNRANKED title) moves down 18 units and the info lines below close up from 22 to
     *  19 units apart so Cash still sits inside the panel. Retail values are checked first. */
    private static final int[] LAYOUT_RECORDS={0x17e14,0x17e54,0x17e94,0x17ed4,0x17f14,0x17f54,0x17f94,0x18114,0x18154,
            0x18194,0x17fd4,0x18014,0x18054,0x180d4,0x18094};
    private static final float[] LAYOUT_FROM={169.2f,187.3f,147.15f,169.2f,169.25f,169.25f,185.15f,144.25f,173f,
            217.4f,239.4f,261.4f,283.4f,283.4f,305.4f};
    private static final float[] LAYOUT_TO={187.2f,205.3f,165.15f,187.2f,187.25f,187.25f,203.15f,162.25f,191f,
            235.4f,254.4f,273.4f,292.4f,292.4f,311.4f};
    static byte[] layout(byte[] apt)throws IOException{
        byte[] out=apt.clone();ByteBuffer b=RetailMenu.little(out);
        for(int i=0;i<LAYOUT_RECORDS.length;i++){
            int at=LAYOUT_RECORDS[i]+36;
            if(b.getInt(LAYOUT_RECORDS[i])!=3 || Math.abs(b.getFloat(at)-LAYOUT_FROM[i])>.01f)
                throw new IOException("Unexpected career panel layout");
            b.putFloat(at,LAYOUT_TO[i]);
        }
        return out;
    }
    private static void retarget(ByteBuffer code,int opcode,int at,int expected,int target)throws IOException{
        int field=(at+4)&~3;
        if((code.get(at)&255)!=opcode || field+4+code.getInt(field)!=expected)throw new IOException("Unexpected branch at "+Integer.toHexString(at));
        code.putInt(field,target-(field+4));
    }
    /** Re-point dictionary entries (by this function's pool slot) to new strings appended to the dictionary. */
    private static byte[] repoint(byte[] apt,byte[] pool,int[] slots,String[] expected,String[] values)throws IOException{
        ByteBuffer program=RetailMenu.little(apt),table=RetailMenu.little(pool);
        int entries=table.getInt(28);
        java.io.ByteArrayOutputStream grown=new java.io.ByteArrayOutputStream();grown.write(pool,0,pool.length);
        int[] pointers=new int[slots.length],offsets=new int[slots.length];
        for(int k=0;k<slots.length;k++){
            int entry=entries+8*program.getInt(POOL+4*slots[k]);
            if(table.getInt(entry)!=1)throw new IOException("Unexpected career dictionary entry");
            int start=table.getInt(entry+4),end=start;while(pool[end]!=0)end++;
            if(!expected[k].equals(new String(pool,start,end-start,StandardCharsets.US_ASCII)))throw new IOException("Unexpected career dictionary text");
            pointers[k]=entry+4;
            while(grown.size()%4!=0)grown.write(0);
            offsets[k]=grown.size();
            byte[] text=values[k].getBytes(StandardCharsets.US_ASCII);grown.write(text,0,text.length);grown.write(0);
        }
        byte[] out=grown.toByteArray();
        for(int k=0;k<slots.length;k++)RetailMenu.little(out).putInt(pointers[k],offsets[k]);
        return out;
    }
    /** Undo the appended-code version exactly: original bytes, length and dictionary pointers. */
    static byte[][] repairV1(byte[] apt,byte[] pool)throws IOException{
        byte[] out=Arrays.copyOf(apt,APT_LENGTH);
        System.arraycopy(ADD_2300,0,out,V1_ADD_AT,ADD_2300.length);
        RetailMenu.little(out).putInt(V1_DEFAULT+4,SWITCH_END-(V1_DEFAULT+8));
        byte[] dictionary=Arrays.copyOf(pool,CONST_LENGTH);
        ByteBuffer program=RetailMenu.little(out),table=RetailMenu.little(dictionary);
        int entries=table.getInt(28);
        String text=new String(dictionary,StandardCharsets.ISO_8859_1);
        for(int k=0;k<V1_SLOTS.length;k++){
            int at=text.indexOf(V1_TEXT[k]+"\0");
            if(at<0)throw new IOException("Career dictionary text missing");
            table.putInt(entries+8*program.getInt(POOL+4*V1_SLOTS[k])+4,at);
        }
        return new byte[][]{out,dictionary};
    }
}
