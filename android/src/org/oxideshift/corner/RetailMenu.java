package org.oxideshift.corner;

import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.Arrays;

/** Adds a selectable row to the imported Xbox front end. Only the app's extracted copy is edited. */
final class RetailMenu {
    private static final String GROUP="screengroups/mainmenu/mainmenu.big";
    private static final byte[] LEGACY=ascii("FNR3-ACHIEVEMENTS-1"), PRE_BRAND=ascii("FNR3-ACHIEVEMENTS-2"), FIRST_BRAND=ascii("FNR3-ACHIEVEMENTS-3"), SLOPED_BRAND=ascii("FNR3-ACHIEVEMENTS-4"), PRE_PAUSE=ascii("FNR3-ACHIEVEMENTS-5"), VERSION=ascii("FNR3-ACHIEVEMENTS-6");
    private static final byte[] MUSIC_ROUTE=ascii("FNR3-ACHIEVEMENTS-7");
    private static final byte[] NAVIGATION=ascii("FNR3-ACHIEVEMENTS-8");
    private static final byte[] MUSIC_CHILD=ascii("FNR3-ACHIEVEMENTS-9");
    // Fixed-width revision markers also locate the adjacent Thor guide marker.
    private static final byte[] STARTUP_FIXED=ascii("FNR3-ACHIEVEMENTS-A");
    private static final byte[] EXPLICIT_PLAYLIST=ascii("FNR3-ACHIEVEMENTS-B");
    private static final int ALIGN=128;

    private static byte[] ascii(String s){return s.getBytes(StandardCharsets.US_ASCII);}
    private static ByteBuffer little(byte[] data){return ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);}
    private static int aligned(int n){return (n+ALIGN-1)&-ALIGN;}
    private static int entry(byte[] data,String name)throws IOException{
        ByteBuffer b=ByteBuffer.wrap(data);
        if(data.length<16 || b.getInt(0)!=0x42494746)throw new IOException("Invalid front-end archive");
        int count=b.getInt(8),end=b.getInt(12),p=16;
        if(count<=0 || end>data.length || end<16)throw new IOException("Invalid front-end directory");
        for(int i=0;i<count && p<=end-9;i++){
            int start=p+8,next=start;
            while(next<end && data[next]!=0)next++;
            if(next==end)break;
            if(name.equals(new String(data,start,next-start,StandardCharsets.US_ASCII)))return p;
            p=next+1;
        }
        throw new IOException("Missing "+name);
    }
    private static byte[] member(byte[] data,String name)throws IOException{
        int index=entry(data,name);ByteBuffer b=ByteBuffer.wrap(data);
        int offset=b.getInt(index),size=b.getInt(index+4);
        if(offset<0 || size<1 || (long)offset+size>data.length)throw new IOException("Invalid "+name);
        return Arrays.copyOfRange(data,offset,offset+size);
    }
    // Preserve each other member's sector alignment, including the Xbox texture bank.
    private static byte[] replace(byte[] data,String name,byte[] replacement)throws IOException{
        int e=entry(data,name);ByteBuffer src=ByteBuffer.wrap(data);
        int offset=src.getInt(e),oldSize=src.getInt(e+4);
        if(offset<0 || oldSize<1 || (long)offset+oldSize>data.length)throw new IOException("Invalid "+name);
        int growth=aligned(Math.max(0,replacement.length-oldSize));
        byte[] out=Arrays.copyOf(data,data.length+growth);
        System.arraycopy(data,offset+oldSize,out,offset+oldSize+growth,data.length-offset-oldSize);
        System.arraycopy(replacement,0,out,offset,replacement.length);
        ByteBuffer b=ByteBuffer.wrap(out);
        if(out.length!=data.length)little(out).putInt(4,out.length);
        b.putInt(e+4,replacement.length);
        for(int i=0,p=16;i<src.getInt(8);i++){
            if(src.getInt(p)>offset)b.putInt(p,src.getInt(p)+growth);
            p+=8;while(data[p++]!=0){}
        }
        return out;
    }
    static String digest(byte[] data){
        try{
            byte[] sum=MessageDigest.getInstance("SHA-256").digest(data);
            StringBuilder hex=new StringBuilder(64);
            for(byte b:sum){hex.append(Character.forDigit((b>>>4)&15,16));hex.append(Character.forDigit(b&15,16));}
            return hex.toString();
        }catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
    }
    static void verify(byte[] data,String expected)throws IOException{
        if(!digest(data).equals(expected))throw new IOException("Unrecognized disc asset revision");
    }
    private static byte[] constants(byte[] data,boolean legacy)throws IOException{
        verify(data,legacy?"0dfcda4b1bffbe8fea0ffc2db3e0a2d3e25b716aa61dc5642285d04ebc91e00a":"dc26cee7d1afbc8f0016551c7c582eed21f068415f1c93801cda548ac3e9602a");
        ByteArrayOutputStream out=new ByteArrayOutputStream();out.write(data);
        ByteBuffer table=little(data);
        for(int slot:new int[]{278,281,282,310,311,312}){
            int pointer=table.getInt(28)+slot*8+4,start=table.getInt(pointer),end=start;
            while(data[end]!=0)end++;
            String original=new String(data,start,end-start,StandardCharsets.US_ASCII);
            int split=original.lastIndexOf(',');
            table.putInt(pointer,out.size());
            // The last two entries are diagnostic strings for Xenon-only sign-in prompts.
            String value=slot==310?"m_sCurrentScreenId":slot==311?"fnr3/achievements":slot==312?"fnr3/music":
                    original.substring(0,split)+(slot==278?(legacy?",MUSIC":",ACHIEVEMENTS,MUSIC"):(legacy?",~DNA~":",~DNA~,~DNA~"))+original.substring(split);
            out.write(ascii(value));
            out.write(0);
        }
        byte[] result=out.toByteArray();System.arraycopy(data,0,result,0,data.length);return result;
    }
    private static void jump(ByteBuffer code,int opcode,int target){
        code.put((byte)opcode);code.position((code.position()+3)&~3);
        code.putInt(target-code.position()-4);
    }
    private static byte[] actions(byte[] data,boolean legacy)throws IOException{
        verify(data,legacy?"78a590c7e640c412866c7458d7d3b763f5e2752807eb16db7640ca3dcaa15246":"15283ba1c487856314ca40f1b87597f364e42328bc461e223b9db1e8c21324dc");
        ByteBuffer code=little(data);
        // The SELECT handler has already pushed the carousel index from register 4.
        // Dispatch from the unused Xenon sign-in block in this Xbox revision.
        code.position(0xcfdc);jump(code,0x99,0xd02c);
        while(code.position()<0xcfe8)code.put((byte)0x04);
        code.position(0xd02c);code.put((byte)0x4c).put((byte)0xb5).put((byte)4).put((byte)0x49);
        code.put((byte)0x12);jump(code,0x9d,0xd040);
        code.put((byte)0x17);jump(code,0x99,0xcfe8); // Stock Create Champ path.
        code.position(0xd040);code.put((byte)0x4c).put((byte)0xb5).put((byte)5).put((byte)0x49).put((byte)0x12);
        jump(code,0x9d,0xd060);code.put((byte)0x17);
        // Assign the extension screen ID on a temporary object, leaving the menu stack intact.
        code.put((byte)0x59).put((byte)0x43);
        code.put((byte)0xa3).putShort((short)303).put((byte)0xa3).putShort((short)304);
        code.put((byte)0x4f).put((byte)0x76).put((byte)0x3e);
        code.position(0xd060);code.put((byte)0xb5).put((byte)6).put((byte)0x49).put((byte)0x12);
        jump(code,0x9d,0xd0b0);
        code.put((byte)0x59).put((byte)0x43);
        code.put((byte)0xa3).putShort((short)303).put((byte)0xa3).putShort((short)305);
        code.put((byte)0x4f).put((byte)0x76).put((byte)0x3e);
        while(code.position()<0xd082)code.put((byte)0x04);
        return data;
    }
    static synchronized void installThorGuide(Path game,byte[] artwork)throws IOException{
        if(artwork.length!=ThorLoadingGuide.ART_BYTES)throw new IOException("Invalid Thor guide backdrop");
        Path archive=game.resolve("GUI/overlay.viv");
        if(!Files.isRegularFile(archive))archive=game.resolve("gui/overlay.viv");
        byte[] stamp=ascii("FNR3-THOR-CONTROLS-4");
        boolean priorGuide,v3;
        try(RandomAccessFile file=new RandomAccessFile(archive.toFile(),"r")){
            if(file.length()>48L*1024*1024 || file.length()<1024)throw new IOException("Unrecognized controller archive");
            byte[] tail=new byte[stamp.length];file.seek(file.length()-VERSION.length-stamp.length);file.readFully(tail);
            if(Arrays.equals(tail,stamp))return;
            v3=Arrays.equals(tail,ascii("FNR3-THOR-CONTROLS-3"));
            priorGuide=v3 || Arrays.equals(tail,ascii("FNR3-THOR-CONTROLS-1")) || Arrays.equals(tail,ascii("FNR3-THOR-CONTROLS-2"));
        }
        byte[] outer=Files.readAllBytes(archive);
        byte[] menuStamp=Arrays.copyOfRange(outer,outer.length-VERSION.length,outer.length);
        if(!Arrays.equals(menuStamp,VERSION) && !Arrays.equals(menuStamp,MUSIC_ROUTE) && !Arrays.equals(menuStamp,NAVIGATION) && !Arrays.equals(menuStamp,MUSIC_CHILD) && !Arrays.equals(menuStamp,STARTUP_FIXED) && !Arrays.equals(menuStamp,EXPLICIT_PLAYLIST))
            throw new IOException("Install the retail menu first");
        for(String variant:new String[]{"CM","CR","HH","PN","Training","Tutorial"}){
            String stem="lp-"+variant+"-ContConfig",group="screengroups/load/"+stem+".big",bankName=stem+".xsh";
            byte[] screen=TraxArt.inflate(member(outer,group),2*1024*1024);
            byte[] bank=TraxArt.inflate(member(screen,bankName),2*1024*1024);
            ByteBuffer header=little(bank);
            int count=header.getInt(8),matches=0;
            if(header.getInt(0)!=0x58504853 || count!=5)throw new IOException("Unrecognized controller artwork");
            for(int i=0;i<count;i++){
                String id=new String(bank,16+8*i,4,StandardCharsets.US_ASCII).trim();
                if(!"1".equals(id))continue;
                int at=header.getInt(20+i*8);
                if(at<56 || at>bank.length-artwork.length-16 || bank[at]!=0x61 ||
                   header.getShort(at+4)!=512 || header.getShort(at+6)!=256)throw new IOException("Invalid loading backdrop");
                System.arraycopy(artwork,0,bank,at+16,artwork.length);matches++;
            }
            if(matches!=1)throw new IOException("Missing loading backdrop");
            String layoutName=stem+".apt";
            byte[] source=TraxArt.inflate(member(screen,layoutName),2*1024*1024);
            byte[] layout=v3?ThorLoadingGuide.upgradeV3(source,variant):ThorLoadingGuide.compose(source,variant);
            outer=replace(outer,group,replace(replace(screen,layoutName,layout),bankName,bank));
        }
        byte[] result=Arrays.copyOf(outer,outer.length+(priorGuide?0:stamp.length));
        System.arraycopy(stamp,0,result,result.length-VERSION.length-stamp.length,stamp.length);
        System.arraycopy(menuStamp,0,result,result.length-VERSION.length,VERSION.length);
        little(result).putInt(4,result.length);
        writeArchive(archive,result);
    }
    private static void writeArchive(Path archive,byte[] result)throws IOException{
        Path temp=Files.createTempFile(archive.getParent(),"thor-art-",".tmp");
        try{
            try(FileOutputStream out=new FileOutputStream(temp.toFile())){out.write(result);out.getFD().sync();}
            Files.move(temp,archive,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        }finally{Files.deleteIfExists(temp);}
    }
    static synchronized void installThorSelector(Path game,byte[] artwork)throws IOException{
        if(artwork.length!=128*64)throw new IOException("Invalid Thor selector artwork");
        Path archive=game.resolve("GUI/custom.viv");
        if(!Files.isRegularFile(archive))archive=game.resolve("gui/custom.viv");
        byte[] stamp=ascii("FNR3-THOR-SELECT-1");
        try(RandomAccessFile file=new RandomAccessFile(archive.toFile(),"r")){
            if(file.length()<1024 || file.length()>48L*1024*1024)throw new IOException("Unrecognized selector archive");
            byte[] tail=new byte[stamp.length];file.seek(file.length()-tail.length);file.readFully(tail);
            if(Arrays.equals(tail,stamp))return;
        }
        byte[] outer=Files.readAllBytes(archive);
        // The pre-fight and pause menus import distinct controllerXBox symbols.
        byte[] updated=replaceSelector(outer,"cstCtrlParts",2800,
                "1461dd7a2ee8f44f37f9981b2cf97eddf6003d783af5b1649f97bbd3d741fb6b",artwork);
        updated=replaceSelector(updated,"cstCtrlPartsBe",8560,
                "5973254d9ea1a534f64088cbaf17d30069359398c724e7a93f24e3d4c9e459c3",artwork);
        byte[] result=Arrays.copyOf(updated,updated.length+stamp.length);
        System.arraycopy(stamp,0,result,updated.length,stamp.length);
        little(result).putInt(4,result.length);
        writeArchive(archive,result);
    }
    private static byte[] replaceSelector(byte[] outer,String stem,int offset,String expected,byte[] artwork)throws IOException{
        String parts="controls/"+stem+".big",texture=stem+".xsh";
        byte[] group=TraxArt.inflate(member(outer,parts),2*1024*1024);
        byte[] bank=TraxArt.inflate(member(group,texture),2*1024*1024);
        verify(bank,expected);
        System.arraycopy(artwork,0,bank,offset+16,artwork.length);
        return replace(outer,parts,replace(group,texture,bank));
    }
    static synchronized void install(Path game,byte[] tag)throws IOException{
        Path archive=game.resolve("GUI/overlay.viv");
        if(!Files.isRegularFile(archive))archive=game.resolve("gui/overlay.viv");
        try(RandomAccessFile f=new RandomAccessFile(archive.toFile(),"r")){
            if(f.length()<VERSION.length || f.length()>48L*1024*1024)throw new IOException("Unrecognized overlay archive");
            byte[] tail=new byte[VERSION.length];f.seek(f.length()-tail.length);f.readFully(tail);
            if(Arrays.equals(tail,EXPLICIT_PLAYLIST))return;
        }
        byte[] outer=Files.readAllBytes(archive);
        byte[] installed=Arrays.copyOfRange(outer,outer.length-VERSION.length,outer.length);
        if(Arrays.equals(installed,MUSIC_CHILD) || Arrays.equals(installed,STARTUP_FIXED)){
            byte[] packed=member(outer,GROUP),group=TraxArt.inflate(packed,2*1024*1024);
            byte[] updated=group;
            if(Arrays.equals(installed,MUSIC_CHILD)){
                byte[] apt=member(group,"mainmenu.apt");
                repairStartup(apt);
                updated=replace(group,"mainmenu.apt",apt);
            }
            writeArchive(archive,storeMainMenu(outer,packed,group,updated));
            return;
        }
        if(Arrays.equals(installed,NAVIGATION)){writeArchive(archive,musicChild(outer));return;}
        if(Arrays.equals(installed,MUSIC_ROUTE) || Arrays.equals(installed,VERSION)){
            writeArchive(archive,musicChild(navigation(Arrays.equals(installed,VERSION)?musicRoute(outer):outer)));return;
        }
        boolean legacy=Arrays.equals(Arrays.copyOfRange(outer,outer.length-LEGACY.length,outer.length),LEGACY);
        byte[] stage=Arrays.copyOfRange(outer,outer.length-VERSION.length,outer.length);
        boolean current=Arrays.equals(stage,PRE_BRAND) || Arrays.equals(stage,FIRST_BRAND) || Arrays.equals(stage,SLOPED_BRAND) || Arrays.equals(stage,PRE_PAUSE);
        if(legacy || current){
            outer=Arrays.copyOf(outer,outer.length-LEGACY.length);
            little(outer).putInt(4,outer.length);
        }else verify(outer,"e6dfe436df6e50f788b792f69ea2bdfef6026aa636357a0ab1aeac186b923647");
        byte[] group=TraxArt.inflate(member(outer,GROUP),2*1024*1024);
        if(!current){
            byte[] apt=actions(member(group,"mainmenu.apt"),legacy),pool=constants(member(group,"mainmenu.const"),legacy);
            group=replace(replace(group,"mainmenu.apt",apt),"mainmenu.const",pool);
        }
        byte[] bank=TraxArt.inflate(member(group,"mainmenu.xsh"),2*1024*1024);
        ByteBuffer header=little(bank);
        int matches=0,mainTitle=0;
        for(int i=0;i<header.getInt(8);i++){
            int pos=16+8*i;
            if(!"16".equals(new String(bank,pos,4,StandardCharsets.US_ASCII).trim()))continue;
            mainTitle=header.getInt(pos+4)+16;
            OgxBrand.apply(bank,mainTitle,tag);matches++;
        }
        if(matches!=1)throw new IOException("Missing retail title");
        byte[] result=replace(outer,GROUP,replace(group,"mainmenu.xsh",bank));
        String pauseGroup="screengroups/pausemenu/pausemenu.big";
        byte[] pause=TraxArt.inflate(member(result,pauseGroup),2*1024*1024);
        byte[] textures=TraxArt.inflate(member(pause,"pausemenu.xsh"),2*1024*1024);
        ByteBuffer directory=little(textures);
        int found=0;
        for(int i=0;i<directory.getInt(8);i++){
            int pos=16+8*i;
            if(!"2".equals(new String(textures,pos,4,StandardCharsets.US_ASCII).trim()))continue;
            int at=directory.getInt(pos+4);
            if(at<0 || at>textures.length-16 || textures[at]!=0x61 ||
                    directory.getShort(at+4)!=512 || directory.getShort(at+6)!=128)
                throw new IOException("Invalid pause title texture");
            OgxBrand.applyPause(textures,at+16,bank,mainTitle,tag);found++;
        }
        if(found!=1)throw new IOException("Missing pause title");
        result=replace(result,pauseGroup,replace(pause,"pausemenu.xsh",textures));
        writeArchive(archive,musicChild(navigation(musicRoute(Arrays.copyOf(result,result.length+VERSION.length)))));
    }

    private static byte[] musicRoute(byte[] outer)throws IOException{
        byte[] group=TraxArt.inflate(member(outer,GROUP),2*1024*1024);
        byte[] pool=member(group,"mainmenu.const"),apt=member(group,"mainmenu.apt");
        ByteBuffer table=little(pool);
        int start=table.getInt(table.getInt(28)+8*84+4);
        byte[] old=ascii("startscreen called: ");
        if(start<0 || start+old.length>=pool.length || !Arrays.equals(old,Arrays.copyOfRange(pool,start,start+old.length)))
            throw new IOException("Unexpected main menu constants");
        // Reuse a trace string so every retail pointer and member extent stays in place.
        Arrays.fill(pool,start,start+old.length,(byte)0);
        byte[] name=ascii("OPTIONSEXTRAX");System.arraycopy(name,0,pool,start,name.length);
        byte[] signal={(byte)0xa3,49,1,0x4f,0x76,0x3e}; // dictionary entry 305 = fnr3/music
        int end=-1;
        for(int pos=0xd060;pos+signal.length<=0xd082;pos++)
            if(Arrays.equals(Arrays.copyOfRange(apt,pos,pos+signal.length),signal)){end=pos+4;break;}
        if(end<0)throw new IOException("Missing Music selection handler");
        // The same SCREEN lookup and gSM.changeMainScreen call used by Options.
        byte[] open={(byte)0xae,(byte)229,(byte)0xaf,77,0x5a,(byte)0xae,(byte)231,(byte)0xb2,(byte)232,0x76,0x3e};
        System.arraycopy(open,0,apt,end,open.length);
        outer=replace(outer,GROUP,replace(replace(group,"mainmenu.apt",apt),"mainmenu.const",pool));
        String optionsName="screengroups/options/options.big";
        byte[] packed=member(outer,optionsName);
        byte[] options=TraxArt.inflate(packed,2*1024*1024);
        byte[] menu=member(options,"options.apt");
        verify(menu,"51ffd421a73c65925e23348c4a2de0d5879d1be28934b6d58f2276158e1abfc7");
        // Skip the third addElement call and shift every following selection to its original handler.
        Arrays.fill(menu,0xe7c2,0xe7cf,(byte)0x04);
        Arrays.fill(menu,0xea94,0xeaa8,(byte)0x04);
        ByteBuffer code=little(menu);code.position(0xe7c2);jump(code,0x99,0xe7cf);
        code.position(0xea94);jump(code,0x99,0xeaa8);
        for(int operand:new int[]{0xeab5,0xeac9,0xeadd,0xeaf1,0xeb05})menu[operand]--;
        byte[] updated=replace(options,"options.apt",menu);
        byte[] result=replace(outer,optionsName,options==packed?updated:repack(packed,updated));
        System.arraycopy(MUSIC_ROUTE,0,result,result.length-MUSIC_ROUTE.length,MUSIC_ROUTE.length);
        little(result).putInt(4,result.length);return result;
    }

    private static byte[] musicChild(byte[] outer)throws IOException{
        byte[] packed=member(outer,GROUP),group=TraxArt.inflate(packed,2*1024*1024);
        byte[] apt=member(group,"mainmenu.apt"),pool=member(group,"mainmenu.const");
        verify(apt,"5df8292a36610671e3e1131b173e0c4dbc1de7f5bfbb58cc80415b4bffae3e6d");
        // Slot 312 is Music's extension string in both the current and legacy imports.
        ByteBuffer table=little(pool);
        int offset=table.getInt(table.getInt(28)+312*8+4);
        byte[] signal=ascii("fnr3/music");
        if(offset<0 || offset+signal.length>=pool.length || pool[offset+signal.length]!=0 ||
                !Arrays.equals(signal,Arrays.copyOfRange(pool,offset,offset+signal.length)))
            throw new IOException("Unexpected Music action");
        System.arraycopy(ascii("fnr3/trax?"),0,pool,offset,signal.length);
        ByteBuffer code=little(apt);
        code.position(0xd06c);jump(code,0x99,0xce70);
        // The remaining original-Xbox-skipped sign-in block is inside the same input handler.
        code.position(0xce70);
        code.put((byte)0x59).put((byte)0x43).put((byte)0x4c); // temporary object, duplicate
        code.put((byte)0xa3).putShort((short)303).put((byte)0xa3).putShort((short)305).put((byte)0x4f);
        code.put((byte)0xa3).putShort((short)303).put((byte)0x4e);
        code.put((byte)0xa3).putShort((short)305).put((byte)0x49);
        jump(code,0x9d,0xd075); // Only an explicit EA Trax request opens the stock playlist.
        code.put((byte)0x76).put((byte)0x3e);
        repairStartup(apt);
        byte[] updated=replace(replace(group,"mainmenu.apt",apt),"mainmenu.const",pool);
        return storeMainMenu(outer,packed,group,updated);
    }

    private static void repairStartup(byte[] apt)throws IOException{
        verify(apt,"6d547eb7c01d531cfdf02054f228c932b59a47030aae27946d4b603a962c82d9");
        // Restore the retail menu.setup arguments on both new installs and upgrades.
        // The lower display reads the visible initial row. D-pad changes still
        // publish getSelection() from the two scroll handlers.
        ByteBuffer code=little(apt);
        int rowArgument=code.getInt(0xcb6c);
        byte[] selection=Arrays.copyOfRange(apt,0xcb55,0xcb5d);
        Arrays.fill(apt,0xcb4f,0xcb78,(byte)0);
        code.position(0xcb4f);
        pushMenuArgument(code,rowArgument-4);
        code.put(selection);
        pushMenuArgument(code,rowArgument);
        pushMenuArgument(code,rowArgument+4);
        // The retail loader walks even unreachable actions to relocate later operands.
        // These gaps follow unconditional jumps or returns. Fill them with single-byte
        // actions so zero bytes from overwritten operands cannot end the walk early.
        Arrays.fill(apt,0xce6c,0xce70,(byte)0x04);
        Arrays.fill(apt,0xce8a,0xce8c,(byte)0x04);
        Arrays.fill(apt,0xd058,0xd060,(byte)0x04);
    }

    private static void pushMenuArgument(ByteBuffer code,int tableEntry){
        code.put((byte)0x96);
        code.position((code.position()+3)&~3);
        code.putInt(1).putInt(tableEntry);
    }

    private static byte[] storeMainMenu(byte[] outer,byte[] packed,byte[] group,byte[] updated)throws IOException{
        byte[] apt=member(updated,"mainmenu.apt"),pool=member(updated,"mainmenu.const");
        verify(apt,"0f7364997724cba7100e9cb9d269bd7942699c8016a946e315c10279ddc38611");
        ByteBuffer table=little(pool);
        int offset=table.getInt(table.getInt(28)+312*8+4);
        byte[] old=ascii("fnr3/trax?");
        if(offset<0 || offset>=pool.length-old.length || pool[offset+old.length]!=0 ||
                !Arrays.equals(old,Arrays.copyOfRange(pool,offset,offset+old.length)))
            throw new IOException("Unexpected EA Trax menu marker");
        System.arraycopy(ascii("fnr3/open?"),0,pool,offset,old.length);
        // An ordinary Select stores this member and returns to the carousel.
        // Only the Open row arms the native hook to skip that store once.
        apt[0xce82]=(byte)0xb8; // EA branch-if-false to the retail playlist
        updated=replace(replace(updated,"mainmenu.apt",apt),"mainmenu.const",pool);
        byte[] result=replace(outer,GROUP,packed==group?updated:repack(packed,updated));
        System.arraycopy(EXPLICIT_PLAYLIST,0,result,result.length-EXPLICIT_PLAYLIST.length,EXPLICIT_PLAYLIST.length);
        return result;
    }

    private static byte[] navigation(byte[] outer)throws IOException{
        outer=patchNavigation(outer,"mainmenu","fcbc7ae286895fa8e4ffd58ad9e9bd585dc6fd532e1112428eee7368d78856f4");
        // B takes the same autosave path as selecting the Options Exit row.
        outer=patchNavigation(outer,"options","575442f079ed7a8e9d4b51dea6fd4ea8a0a947f2078024378ec46fe5fefda095",0xe9e4,0xedec);
        // All editor tabs share Start's existing Accept Changes confirmation.
        outer=patchNavigation(outer,"cac","05d67ac6f5afbee52474cbe004fec1b65930e836e45aa20ce365e862396575e6",0x12668,0x12738);
        // Leave Select Controller without applying a pending reassignment.
        outer=patchNavigation(outer,"pausemenu","638e8413f7950b99ce9ded98a14689762670023e7983bebac4089d613a4f3c43",0x9dc8,0x9f7b);
        outer=patchNavigation(outer,"train","893e69818ce7f3105971530ad5c4c1a0ab0913fef50306a6a19dd0c8c2233066",0x94e0,0x9574);
        // This screen's Advance handler commits a value before going back. Cancel skips the commit.
        outer=patchNavigation(outer,"mcon","2edf8ab0d2bedad79c2c6ec53de1e2e28c56ccb3b50e2b925cddbf9decc3f916",0x63d4,0x645c);
        System.arraycopy(NAVIGATION,0,outer,outer.length-NAVIGATION.length,NAVIGATION.length);
        return outer;
    }

    private static byte[] patchNavigation(byte[] outer,String group,String hash,int... branches)throws IOException{
        String name="screengroups/"+group+"/"+group+".big",script=group+".apt";
        byte[] packed=member(outer,name), unpacked=TraxArt.inflate(packed,2*1024*1024);
        byte[] data=member(unpacked,script);
        verify(data,hash);
        ByteBuffer code=little(data);
        for(int i=0;i<branches.length;i+=2)code.putInt(branches[i],branches[i+1]-branches[i]-4);
        if(group.equals("mainmenu"))publishHover(data,code);
        byte[] updated=replace(unpacked,script,data);
        return replace(outer,name,packed==unpacked?updated:repack(packed,updated));
    }

    private static void publishHover(byte[] data,ByteBuffer code){
        // Preserve the arguments to menu.setup and report the actual restored row on entry.
        byte[] selected=Arrays.copyOfRange(data,0xcb58,0xcb60); // .mainSelection.num
        int rowArguments=code.getInt(0xcb68); // consecutive retail constants for registers 3 and 4
        code.position(0xcb4f);
        code.put((byte)0x59).put((byte)0x43).put((byte)0xa3).putShort((short)303);
        code.put((byte)0x56).put(selected).put((byte)0x4f);
        code.put((byte)0x56).put(selected);
        // initGuiMainMenu preloads this in register 1. A stock multi-value PUSH
        // supplies the original two registers in order as the remaining setup arguments.
        code.put((byte)0x96);code.position((code.position()+3)&~3);
        code.putInt(2).putInt(rowArguments).put((byte)0xb5).put((byte)3);
        jump(code,0x99,0xcb7a);
        // Both retail scroll handlers now read menu.getSelection() after the move.
        // On original Xbox the platform test skips this Xenon sign-in diagnostic block.
        for(int pos:new int[]{0xcd05,0xcd19}){code.position(pos);jump(code,0x99,0xce50);}
        code.position(0xce50);
        code.put((byte)0x59).put((byte)0x43).put((byte)0xa3).putShort((short)303);
        code.put(data,0xcd50,14).put((byte)0x4f);
        jump(code,0x99,0xd114);
    }

    // Preserve the retail RefPack matches wherever the changed script still permits them.
    // A match crossing an edit becomes literals until the next reusable match.
    private static byte[] repack(byte[] packed,byte[] updated)throws IOException{
        ByteArrayOutputStream out=new ByteArrayOutputStream(packed.length);
        out.write(packed,0,5);
        int pos=0,literals=0;
        for(int i=5;i<packed.length;){
            int a=packed[i++]&255,b=0,c=0,d=0,prefix,length=0,distance=0;
            if(a<128){
                b=packed[i++]&255;prefix=a&3;length=((a>>2)&7)+3;distance=((a&96)<<3)+b+1;
            }else if(a<192){
                b=packed[i++]&255;c=packed[i++]&255;prefix=b>>6;length=(a&63)+4;distance=((b&63)<<8)+c+1;
            }else if(a<224){
                b=packed[i++]&255;c=packed[i++]&255;d=packed[i++]&255;prefix=a&3;
                length=((a&12)<<6)+d+5;distance=((a&16)<<12)+(b<<8)+c+1;
            }else prefix=a<252?((a&31)<<2)+4:a&3;
            i+=prefix;pos+=prefix;
            if(a>=224 && a<252)continue;
            int k=0;
            while(k<length && updated[pos+k]==updated[pos+k-distance])k++;
            if(k!=length){pos+=length;continue;}
            while(pos-literals>=4){
                int n=Math.min(112,(pos-literals)&~3);
                out.write(0xe0+n/4-1);out.write(updated,literals,n);literals+=n;
            }
            int tail=pos-literals;
            if(a>=252){
                out.write(0xfc|tail);out.write(updated,literals,tail);
                byte[] result=out.toByteArray();
                if(pos!=updated.length || !Arrays.equals(updated,TraxArt.inflate(result,updated.length)))
                    throw new IOException("Options archive failed verification");
                return result;
            }
            if(a<128){out.write((a&~3)|tail);out.write(b);}
            else if(a<192){out.write(a);out.write((b&63)|(tail<<6));out.write(c);}
            else{out.write((a&~3)|tail);out.write(b);out.write(c);out.write(d);}
            out.write(updated,literals,tail);pos+=length;literals=pos;
        }
        throw new IOException("Incomplete Options archive");
    }
}
