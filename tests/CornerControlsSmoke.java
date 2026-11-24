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
        for(Key key:new Key[]{Key.LT,Key.RT,Key.LB,Key.RB,Key.R3,Key.START}){
            int code=key==Key.LT?8:key==Key.RT?9:key==Key.LB?7:key==Key.RB?6:0;
            int start=4+2*code;
            check(pad.report(key,false,0,0).substring(start,start+2).equals(
                    key==Key.R3?"80":key==Key.START?"10":"ff"));
        }
        check(pad.report().equals(idle));
        System.out.println("Verified relocated button input and manual healing sticks share the physical pad report.");
    }
}
