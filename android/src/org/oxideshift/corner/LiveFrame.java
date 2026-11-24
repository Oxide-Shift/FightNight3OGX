package org.oxideshift.corner;

import java.nio.ByteBuffer;

/** Coordinates are measured in the original Xbox 640 by 480 scanout. */
final class LiveFrame {
    final ByteBuffer pixels;
    final int width,height,sequence;
    final boolean wide;
    LiveFrame(ByteBuffer pixels,int width,int height,int sequence){
        this(pixels,width,height,sequence,false);
    }
    LiveFrame(ByteBuffer pixels,int width,int height,int sequence,boolean wide){
        this.wide=wide;
        this.pixels=pixels;this.width=width;this.height=height;this.sequence=sequence;
    }
    // The retail 16:9 mode centres 4:3 interface artwork in the scanout.
    int sourceX(int logicalX){return wide?80+(logicalX*3+2)/4:logicalX;}
    int at(int x,int y){
        int offset=8+4*((y*height/480)*width+sourceX(x)*width/640);
        return pixels.getInt(offset)|0xff000000;
    }
    static int red(int c){return (c>>16)&255;}
    static int green(int c){return (c>>8)&255;}
    static int blue(int c){return c&255;}
    // On a contract, Scout is printed inside the opponent's column underneath the record.
    // Confirm both blue rims and the bright lettering. A confirmation dialog dims them.
    boolean scoutPrompt(){
        for(int x=77;x<=89;x+=12){
            int c=at(x,360);
            if(blue(c)-red(c)<50 || blue(c)-green(c)<30)return false;
        }
        return whiteInk(105,352) && whiteInk(116,352) && whiteInk(130,356) && whiteInk(149,352);
    }
    private boolean whiteInk(int x,int y){
        int c=at(x,y);
        return Math.min(red(c),Math.min(green(c),blue(c)))>155;
    }
    private int litRow(int y){
        int active=0;
        for(int x=16;x<640;x+=24){
            int c=at(x,y);
            if(Math.max(red(c),Math.max(green(c),blue(c)))>30)active++;
        }
        return active;
    }
    // A dark fade offers no evidence either way. Keep the previous crop.
    int matte(){
        if(litRow(52)+litRow(427)+litRow(12)+litRow(467)>4)return 0;
        return litRow(68)+litRow(412)>8?1:-1;
    }
    private int colorCount(int x1,int y1,int x2,int y2,int color){
        int n=0;
        for(int y=y1;y<y2;y+=2)for(int x=x1;x<x2;x+=2){
            int c=at(x,y),r=red(c),g=green(c),b=blue(c);
            if(color==0?r>85&&r>2*g&&r>2*b:
               color==1?r>74&&g>35&&r>g*1.15&&g>b*1.6:
               g>72&&g>r*1.1&&g>b*1.5)n++;
        }
        return n;
    }
    enum Scene { INTRO, LOADING, TRAINING, MENU, PAUSE, ROUND, CORNER, TREATMENT, RINGSIDE, TOTALS, FINAL }
    private boolean nearColor(int x,int y,int r,int g,int b){
        int c=at(x,y);
        return Math.abs(red(c)-r)<16 && Math.abs(green(c)-g)<13 && Math.abs(blue(c)-b)<13;
    }
    private boolean totals(){
        // Both columns of the retail statistics sheet have a burnt-red border,
        // a rust nameplate, alternating dark rows and a near-black footer.
        int matches=0;
        for(int x=74;x<=564;x+=98){
            if(nearColor(x,437,142,58,38))matches++;
            if(nearColor(x,151,87,35,12))matches++;
            if(nearColor(x,425,20,8,4))matches++;
        }
        if(matches<15 || colorCount(505,96,560,125,1)<=260)return false;
        int figures=0;
        for(int y=192;y<390;y+=4)for(int x=429;x<564;x+=4){
            int c=at(x,y);
            if(Math.min(red(c),Math.min(green(c),blue(c)))>150)figures++;
        }
        return figures>55;
    }
    int pauseRow(){
        if(colorCount(260,96,348,125,1)<460 || colorCount(500,96,562,125,1)<330)return -1;
        for(int row=0;row<5;row++){
            int y=196+34*row;
            if(colorCount(87,y-9,108,y+9,2)>8 &&
               colorCount(121,y-6,192,y+8,1)>8)return row;
        }
        return -1;
    }
    private boolean loadingArtwork(){
        int samples=0;
        for(int y=70;y<=400;y+=110){
            for(int x=55;x<=590;x+=97){
                if(nearColor(x,y,255,138,0) || nearColor(x,y,160,22,20))samples++;
            }
        }
        return samples>=17;
    }
    private boolean fightMeters(){
        int blue=0,red=0;
        for(int x=62;x<=176;x+=19){
            int b=at(x,390),r=at(639-x,390);
            if(blue(b)>35 && blue(b)>2*red(b) && green(b)>2*red(b) && blue(b)>green(b))blue++;
            if(red(r)>42 && red(r)>2*green(r) && red(r)>2*blue(r))red++;
        }
        return blue>=5 && red>=5;
    }
    Scene scene(){return scene(true);}
    Scene scene(boolean inBout){
        if(loadingArtwork())return Scene.LOADING;
        if(inBout && fightMeters() && colorCount(276,385,333,399,0)>35 &&
                colorCount(277,400,332,415,1)>32)return Scene.ROUND;
        if(inBout && pauseRow()>=0)return Scene.PAUSE;
        if(BroadcastCues.hasMenuLogo(this) || (!inBout && BroadcastCues.hasUnbadgedFooter(this)))return Scene.MENU;
        if(!inBout)return Scene.RINGSIDE;
        if(totals())return Scene.TOTALS;
        if(colorCount(106,369,149,402,1)>95 && colorCount(157,365,178,384,2)>7
                && colorCount(351,365,370,383,1)>8 && cutmanClock())return Scene.CORNER;
        if(colorCount(106,369,149,402,1)>95 && colorCount(157,364,222,384,1)>20
                && colorCount(378,362,444,386,1)>18 && colorCount(181,390,203,410,1)>9
                && cutmanClock())return Scene.TREATMENT;
        return Scene.RINGSIDE;
    }
    private boolean cutmanClock(){
        // The recovery clock is its own small opaque panel amid the cutman's controls.
        int dark=0;
        for(int x=104;x<151;x+=4)for(int y=363;y<408;y+=4){
            int c=at(x,y);
            if(red(c)<62 && green(c)<57 && blue(c)<50)dark++;
        }
        return dark>=12;
    }
}
