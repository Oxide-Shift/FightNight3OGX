package org.oxideshift.corner;

/** Locate the retail editor's printed input cues in logical 640 by 480 coordinates. */
final class BoxerEditor {
    static boolean handles(CareerDesk.Page page){
        return page!=null && page.is("cac") && page.index>=1 && page.index<=8;
    }
    static boolean bright(int pixel){
        return Math.min(LiveFrame.red(pixel),Math.min(LiveFrame.green(pixel),LiveFrame.blue(pixel)))>=180;
    }
    private static boolean brightNear(LiveFrame f,int x,int y){
        return bright(f.at(x,y)) || bright(f.at(x+1,y)) || bright(f.at(x-1,y));
    }
    static boolean showing(CareerDesk.Page page,LiveFrame f,BroadcastCues cues){
        if(!handles(page) || (cues.mask&8)==0)return false;
        // Dialogs retain the old footer on a darkened backdrop. Require lit icon ink.
        int help=cues.centers[3];
        boolean lit=false;
        for(int x=help-4;x<=help+4;x+=2)if(brightNear(f,x,426)){lit=true;break;}
        if(!lit)return false;
        if(page.index==1)return (cues.mask&3)!=0 || cues.trackRight>0;
        // Start is a silver play triangle one caption to the left of Y Help.
        int start=startCenter(help);
        return brightNear(f,start,427) || brightNear(f,start-2,428) || cues.trackRight>start-12;
    }
    static int startCenter(int help){return help-114;}
    static boolean shoulders(LiveFrame f){
        return brightNear(f,64,148) && brightNear(f,124,148) &&
                LiveFrame.red(f.at(87,148))<45 && LiveFrame.red(f.at(120,155))<60;
    }
    private static boolean orange(int c){
        int r=LiveFrame.red(c),g=LiveFrame.green(c);
        return r>135 && r>g+56 && g>48 && g>LiveFrame.blue(c)+34;
    }
    private static boolean green(int c){
        int g=LiveFrame.green(c);
        return g>85 && g>LiveFrame.red(c)+15 && g>LiveFrame.blue(c)+38;
    }
    private static boolean border(int c){
        return LiveFrame.red(c)>230 && LiveFrame.green(c)>136 && LiveFrame.green(c)<195 && LiveFrame.blue(c)<44;
    }
    boolean dialog(LiveFrame f){
        // Both gold edges belong to the game's modal, not the dimmed editor underneath.
        for(int y=205;y<=265;y+=30)if(!border(f.at(122,y)) || !border(f.at(518,y)))return false;
        for(int y=175;y<319;y++)for(int x=185;x<=320;x++){
            if(green(f.at(x-5,y)) && green(f.at(x+4,y)) && brightNear(f,x,y) &&
                    (green(f.at(x,y-5)) || green(f.at(x,y-6)))){
                left=x-12;right=x+12;top=y-11;bottom=y+13;return true;
            }
        }
        return false;
    }
    // Store the small ink rectangle only. The selected value, highlight and boxer stay in place.
    int left,right,top,bottom,closingArrow;
    boolean select;
    boolean field(LiveFrame f,int page){
        select=false;closingArrow=0;
        int first=page==1?96:169;
        for(int y=first;y<405;y++){
            for(int x=69;x<=73;x++){
                if(green(f.at(x-6,y)) && green(f.at(x+5,y)) && brightNear(f,x,y) &&
                        (green(f.at(x,y-7)) || green(f.at(x,y-6)))){
                    select=true;left=x-14;right=x+17;top=y-7;bottom=y+15;
                    if(page==1)choiceArrows(f,y);
                    return true;
                }
            }
            // The form uses a leading arrow pair. Ratings puts it in the percentage column.
            int center=page==4?213:68;
            for(int x=center-2;x<=center+2;x++){
                if(orange(f.at(x-10,y)) && orange(f.at(x+10,y)) &&
                        orange(f.at(x-6,y-4)) && orange(f.at(x+5,y-4)) &&
                        orange(f.at(x-6,y+4)) && orange(f.at(x+5,y+4)) &&
                        !orange(f.at(x,y)) && !orange(f.at(x,y-4)) &&
                        !orange(f.at(x-10,y-4)) && !orange(f.at(x+10,y-4)) &&
                        !orange(f.at(x-4,y)) && !orange(f.at(x+4,y))){
                    left=x-15;right=x+18;top=y-8;bottom=y+10;return true;
                }
            }
        }
        return false;
    }
    private static boolean chevron(LiveFrame f,int tip,int y,int inward){
        return orange(f.at(tip,y)) && orange(f.at(tip+5*inward,y-3)) &&
                orange(f.at(tip+5*inward,y+3)) && !orange(f.at(tip,y-4)) &&
                !orange(f.at(tip,y+4)) && !orange(f.at(tip+6*inward,y)) &&
                !orange(f.at(tip+5*inward,y-7)) && !orange(f.at(tip+5*inward,y+7));
    }
    private void choiceArrows(LiveFrame f,int fieldY){
        // On the chooser, the two arrows enclose a variable-length boxer name.
        for(int y=fieldY-2;y<=fieldY+6;y++)for(int x=91;x<=96;x++){
            if(!chevron(f,x,y,1))continue;
            for(int end=x+25;end<520;end++)if(chevron(f,end,y,-1)){
                right=x+12;closingArrow=end;return;
            }
        }
    }
}
