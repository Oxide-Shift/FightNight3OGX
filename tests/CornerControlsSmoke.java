package org.oxideshift.corner;

import static org.oxideshift.corner.CornerControls.Key;

public final class CornerControlsSmoke {
    static void check(boolean value){if(!value)throw new AssertionError();}
    public static void main(String[] args){
        FightPad pad=new FightPad();
        String idle=pad.report();
        check(pad.report(Key.X,true,32767,-32768).equals("PAD 00000000ff000000000000000000ff7f0080"));
        check(pad.report(Key.LEFT_SIDE,false,0,0).substring(24,28).equals("0080"));
        check(pad.report(Key.RIGHT_SIDE,false,0,0).substring(24,28).equals("ff7f"));
        check(pad.report().equals(idle));
        System.out.println("Verified relocated button input and manual healing sticks share the physical pad report.");
    }
}
