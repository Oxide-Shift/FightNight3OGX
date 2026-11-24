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
        if(at!=target || (menu[at]&255)!=0x96 || words.getInt(0xd118)!=1 || words.getInt(0xd11c)!=0xed14)
            throw new AssertionError("Startup constant operand was skipped");
        // The title and profile picker also work with a broken carousel. Check the
        // retail setup call itself so every install path preserves the visible rows.
        byte[] setup=Arrays.copyOfRange(menu,0xcb4f,0xcb86);
        if(!RetailMenu.digest(setup).equals("86b11b5140b43fea74eb98d31fa344a6d2d473796386225728dae90e7a9c3f22"))
            throw new AssertionError("Retail carousel setup changed");
        byte[] first=Files.readAllBytes(overlay);RetailMenu.install(game,tag);
        if(!Arrays.equals(first,Files.readAllBytes(overlay)))throw new AssertionError("Repeated repair modified the archive");
        System.out.println("Startup script reaches the title constant after install and repeat launch");
    }
}
