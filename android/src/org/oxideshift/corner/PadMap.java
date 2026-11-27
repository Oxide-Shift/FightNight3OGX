package org.oxideshift.corner;

import android.view.KeyEvent;
import java.util.Arrays;

/** Player button remap: which physical control presses which Xbox control.
 *  Slots 0-7 are the packet's first byte (D-pad up/down/left/right, Start, Back, L3, R3); slots 8-15 are the
 *  pressure buttons A, B, X, Y, Black, White, LT, RT. The default sends every control to its own slot.
 *  The map is always a permutation: assigning a control swaps it with the one that held that slot, so no Xbox
 *  control can ever be left without a button (B and Start stay reachable whatever the player does). */
final class PadMap {
    static final int SLOTS=16, LT=14, RT=15;
    /** Xbox controls in the order the Fight Setup page lists them. */
    static final int[] ORDER={8,9,10,11,12,13,14,15,0,1,2,3,4,5,6,7};
    static final String[] XBOX={"D-pad up","D-pad down","D-pad left","D-pad right","Start","Back","Left stick click",
            "Right stick click","A","B","X","Y","Black","White","Left trigger","Right trigger"};
    static final String[] PHYSICAL={"D-pad up","D-pad down","D-pad left","D-pad right","Start","Select","L3","R3",
            "A","B","X","Y","R1","L1","L2","R2"};
    private static final PadMap IDENTITY=new PadMap(identity());
    private final int[] slot;

    private PadMap(int[] slot){this.slot=slot;}
    private static int[] identity(){int[] s=new int[SLOTS];for(int i=0;i<SLOTS;i++)s[i]=i;return s;}
    static PadMap standard(){return IDENTITY;}
    boolean isStandard(){return Arrays.equals(slot,IDENTITY.slot);}
    /** Xbox slot that physical control {@code physical} presses. */
    int slot(int physical){return slot[physical];}
    /** Physical control that presses Xbox slot {@code xbox}. */
    int physical(int xbox){for(int i=0;i<SLOTS;i++)if(slot[i]==xbox)return i;return xbox;}
    /** Assign {@code physical} to {@code xbox}; whatever pressed {@code xbox} before takes over its old slot. */
    PadMap assign(int xbox,int physical){
        if(xbox<0 || xbox>=SLOTS || physical<0 || physical>=SLOTS)return this;
        int[] next=slot.clone();int holder=physical(xbox);
        next[holder]=slot[physical];next[physical]=xbox;
        return new PadMap(next);
    }
    /** Stored as sixteen comma-separated slot numbers. */
    String encode(){
        StringBuilder b=new StringBuilder();
        for(int i=0;i<SLOTS;i++){if(i>0)b.append(',');b.append(slot[i]);}
        return b.toString();
    }
    /** Anything that is not a full permutation (missing, damaged, from a newer build) reads as the default. */
    static PadMap decode(String stored){
        if(stored==null || stored.isEmpty())return IDENTITY;
        String[] parts=stored.split(",");
        if(parts.length!=SLOTS)return IDENTITY;
        int[] s=new int[SLOTS];boolean[] seen=new boolean[SLOTS];
        try{
            for(int i=0;i<SLOTS;i++){
                int v=Integer.parseInt(parts[i].trim());
                if(v<0 || v>=SLOTS || seen[v])return IDENTITY;
                seen[v]=true;s[i]=v;
            }
        }catch(NumberFormatException e){return IDENTITY;}
        PadMap map=new PadMap(s);
        return map.isStandard()?IDENTITY:map;
    }
    /** Physical control for an Android controller key, or -1. Matches FightPad's default wiring. */
    static int physicalKey(int code){
        switch(code){
            case KeyEvent.KEYCODE_DPAD_UP:return 0;case KeyEvent.KEYCODE_DPAD_DOWN:return 1;
            case KeyEvent.KEYCODE_DPAD_LEFT:return 2;case KeyEvent.KEYCODE_DPAD_RIGHT:return 3;
            case KeyEvent.KEYCODE_BUTTON_START:return 4;case KeyEvent.KEYCODE_BUTTON_SELECT:return 5;
            case KeyEvent.KEYCODE_BUTTON_THUMBL:return 6;case KeyEvent.KEYCODE_BUTTON_THUMBR:return 7;
            case KeyEvent.KEYCODE_BUTTON_A:return 8;case KeyEvent.KEYCODE_BUTTON_B:return 9;
            case KeyEvent.KEYCODE_BUTTON_X:return 10;case KeyEvent.KEYCODE_BUTTON_Y:return 11;
            case KeyEvent.KEYCODE_BUTTON_R1:return 12;case KeyEvent.KEYCODE_BUTTON_L1:return 13;
            case KeyEvent.KEYCODE_BUTTON_L2:return 14;case KeyEvent.KEYCODE_BUTTON_R2:return 15;
            default:return -1;
        }
    }
}
