package org.oxideshift.corner;

/** One touch command is merged with the physical Xbox pad. */
final class CornerControls {
    enum Key {
        UP(0,1), DOWN(0,2), LEFT(0,4), RIGHT(0,8), START(0,16),
        A(2,255), B(3,255), X(4,255), Y(5,255), LEFT_SIDE(10,0x8000), RIGHT_SIDE(10,0x7fff);
        final int index,mask;
        Key(int index,int mask){this.index=index;this.mask=mask;}
    }
    interface Output { boolean press(Key key); }
    private CornerControls(){}
}
