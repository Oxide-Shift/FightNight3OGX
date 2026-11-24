package org.oxideshift.corner;

/** Finds the retail menu's controller ribbon and EA Trax card in guest scanout coordinates. */
final class BroadcastCues {
    static final int NONE=0, FOOTER=1, TRACK=2, CUTMAN=3, MANUAL=4;
    static final int RETIRE=4, ALL_PROMPTS=31;
    static final int SCOUT_CARD=1<<16;
    static final CornerControls.Key[] KEYS={CornerControls.Key.A,CornerControls.Key.B,CornerControls.Key.X,CornerControls.Key.Y,CornerControls.Key.X};
    final int[] centers=new int[KEYS.length];
    int mask,trackRight;
    private static boolean gold(int c){return LiveFrame.red(c)>179 && LiveFrame.green(c)>95 && LiveFrame.blue(c)<80;}
    static boolean hasMenuLogo(LiveFrame f){
        int white=0,red=0,trim=0;
        for(int x=57;x<=65;x+=2){
            for(int dy=-2;dy<=2;dy+=2){
                int a=f.at(x,400+dy),b=f.at(x,413+dy);
                if(LiveFrame.green(a)>185 && LiveFrame.blue(a)>160)white++;
                if(LiveFrame.red(b)>135 && LiveFrame.red(b)>2*LiveFrame.green(b))red++;
                if(gold(f.at(x,435+dy)))trim++;
            }
        }
        return white>=6 && red>=2 && trim>=8;
    }
    boolean hasTrackRail(LiveFrame frame){
        return gold(frame.at(94,389)) && gold(frame.at(94,440));
    }
    private static boolean hue(int c,int key){
        int r=LiveFrame.red(c),g=LiveFrame.green(c),b=LiveFrame.blue(c);
        switch(key){
            case 0:return g>100 && g>r+19 && g>b+45;
            case 1:return r>115 && r>g+53 && r>b+46;
            case 2:case RETIRE:return b>130 && b>g+24 && b>r+45;
            default:return r>145 && g>110 && r>b+75 && g>b+45;
        }
    }
    private static int glyph(LiveFrame f,int start,int key){
        for(int x=start;x<600;x+=2){
            // At 900 lines a resampled rim can fall between two logical rows.
            if(!(hue(f.at(x,420),key) || hue(f.at(x,421),key) || hue(f.at(x,422),key)))continue;
            if(!(hue(f.at(x-4,429),key) || hue(f.at(x-4,428),key) || hue(f.at(x-4,430),key)))continue;
            int bright=0;
            for(int y=421;y<=430;y+=2)for(int dx=-5;dx<=5;dx+=2){
                int c=f.at(x+dx,y);
                if(Math.min(LiveFrame.red(c),Math.min(LiveFrame.green(c),LiveFrame.blue(c)))>158)bright++;
            }
            if(bright>=3 && FooterLegend.matches(f,x,key))return x;
        }
        return -1;
    }
    static boolean hasUnbadgedFooter(LiveFrame frame){
        for(int key=0;key<4;key++){
            if(key==2)continue;
            int center=glyph(frame,94,key);
            if(center>=0)return true;
        }
        return false;
    }
    void read(LiveFrame f){read(f,true);}
    void read(LiveFrame f,boolean checkBareFooter){
        if(!hasMenuLogo(f) && !(checkBareFooter && hasUnbadgedFooter(f))){mask=trackRight=0;return;}
        readMenu(f);
    }
    void readMenu(LiveFrame f){
        mask=trackRight=0;
        int rails=0;
        for(int x=91;x<630;x+=4){
            int c=f.at(x,396);
            boolean silver=Math.abs(LiveFrame.red(c)-LiveFrame.blue(c))<20 && LiveFrame.green(c)>125;
            if(gold(f.at(x,389)) && gold(f.at(x,440))){
                if(silver)rails++;
                if(rails>=3)trackRight=x+16;
            }else if(rails>=3)break;
        }
        if(trackRight!=0)trackRight=Math.min(640,trackRight);
        // The song banner can cover Select and Back while leaving Y Help exposed.
        int start=Math.max(94,trackRight);
        for(int i=0;i<RETIRE;i++){
            centers[i]=glyph(f,start,i);
            if(centers[i]>=0){mask|=1<<i;start=centers[i]+34;}
        }
        // Career prints X Retire to the left of B Back. It shares the physical
        // X button with Ratings, but has its own caption and mask bit.
        centers[RETIRE]=(mask&4)==0?glyph(f,Math.max(94,trackRight),RETIRE):-1;
        if(centers[RETIRE]>=0)mask|=1<<RETIRE;
    }
    static int visibleAt(LiveFrame frame,int requested,int[] centers){
        int visible=0;
        for(int key=0;key<KEYS.length;key++){
            if((requested&(1<<key))==0)continue;
            int x=centers[key];
            if(x>=0 && (hue(frame.at(x,421),key) || hue(frame.at(x,420),key) || hue(frame.at(x,422),key)) &&
                    FooterLegend.matches(frame,x,key))visible|=1<<key;
        }
        return visible;
    }
    int knownPrompts(LiveFrame frame,int requested){
        int confirmed=0;
        for(int i=0;i<KEYS.length;i++)
            if((requested & mask & (1<<i))!=0 && FooterLegend.matches(frame,centers[i],i))confirmed|=1<<i;
        return confirmed;
    }
    int route(LiveFrame.Scene scene){
        if(scene==LiveFrame.Scene.MENU && mask!=0)return FOOTER;
        if(trackRight>0)return TRACK;
        if(scene==LiveFrame.Scene.CORNER)return CUTMAN;
        if(scene==LiveFrame.Scene.TREATMENT)return MANUAL;
        return NONE;
    }
}
