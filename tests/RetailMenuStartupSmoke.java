package org.oxideshift.corner;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.*;
import java.util.Arrays;

/** Exercise the same linear walk that the retail loader uses before the title screen. */
public final class RetailMenuStartupSmoke {
    private static int following(int at,int op){
        int operand=(at+4)&~3;
        switch(op){
            case 0x8e:return operand+32; // Function2 header in the startup action block
            case 0x96:case 0x88:return operand+8; // PUSH and constant dictionary
            case 0x99:case 0x9d:case 0x87:case 0xb8:return operand+4;
            case 0xa3:return at+3;
            case 0xb5:case 0xb9:case 0xa2:case 0xae:case 0xaf:
            case 0xb0:case 0xb1:case 0xb2:case 0xb3:case 0xb6:return at+2;
            case 0xb4:case 0xb7:case 0x77:return at+5;
            default:return at+1;
        }
    }
    public static void main(String[] args)throws Exception{
        Path game=Paths.get(args[1]),overlay=game.resolve("GUI/overlay.viv");
        Files.createDirectories(overlay.getParent());
        Files.copy(Paths.get(args[0]),overlay);
        byte[] tag=new byte[2*OgxBrand.GLYPH_SIZE*OgxBrand.GLYPH_SIZE];
        RetailMenu.install(game,tag);
        byte[] menu=TraxArt.textureBank(game.toFile(),"overlay.viv","screengroups/mainmenu/mainmenu.big","mainmenu.apt",2*1024*1024);
        // The original instruction following the patched dispatch pushes one constant.
        // All preceding action bytes must decode even when normal gameplay jumps over them.
        int target=0xd115,at=0xcb4f;
        while(at<target){
            int op=menu[at]&255;
            if(op==0)throw new AssertionError("Premature end of actions at "+Integer.toHexString(at));
            at=following(at,op);
        }
        ByteBuffer words=ByteBuffer.wrap(menu).order(ByteOrder.LITTLE_ENDIAN);
        if(menu[0xce54]!=0)throw new AssertionError("Carousel skipped displacement before reaching the selected row");
        if(at!=target || (menu[at]&255)!=0x96 || words.getInt(0xd118)!=1 || words.getInt(0xd11c)!=0xed14)
            throw new AssertionError("Startup constant operand was skipped");
        // The title and profile picker also work with a broken carousel. Check the
        // retail setup call itself so every install path preserves the visible rows.
        byte[] setup=Arrays.copyOfRange(menu,0xcb4f,0xcb86);
        if(!RetailMenu.digest(setup).equals("86b11b5140b43fea74eb98d31fa344a6d2d473796386225728dae90e7a9c3f22"))
            throw new AssertionError("Retail carousel setup changed");
        // Music must return immediately after signaling its parent page. The
        // independent, app-only SELECT action is the sole shortcut to EA Trax.
        byte[] music={(byte)0x59,0x43,(byte)0xa3,0x2f,1,(byte)0xa3,0x31,1,0x4f,0x76,0x3e};
        if(!Arrays.equals(music,Arrays.copyOfRange(menu,0xce70,0xce70+music.length)) ||
                (menu[0xd0e8]&255)!=0x99 || 0xd0f0+words.getInt(0xd0ec)!=0xd075)
            throw new AssertionError("Music parent and playlist must have separate input handlers");
        byte[] pool=TraxArt.textureBank(game.toFile(),"overlay.viv","screengroups/mainmenu/mainmenu.big","mainmenu.const",2*1024*1024);
        ByteBuffer constants=ByteBuffer.wrap(pool).order(ByteOrder.LITTLE_ENDIAN);
        int indices=words.getInt(0xaf04),count=words.getInt(0xaf00);
        int last=words.getInt(indices+(count-1)*4);
        int record=constants.getInt(28)+last*8, label=constants.getInt(record+4);
        if(last!=constants.getInt(24)-1 || constants.getInt(record)!=1 ||
                !Arrays.equals(Arrays.copyOfRange(pool,label,label+6),new byte[]{'t','i','m','e','r',0}))
            throw new AssertionError("Root carousel timer did not resolve to its own property");
        byte[] first=Files.readAllBytes(overlay);RetailMenu.install(game,tag);
        if(!Arrays.equals(first,Files.readAllBytes(overlay)))throw new AssertionError("Repeated repair modified the archive");
        System.out.println("Startup script reaches the title constant after install and repeat launch");
    }
}
