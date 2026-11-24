package org.oxideshift.corner;

/** Recognize the printed caption before moving a controller prompt. The samples are
 * contrast pairs in the lettering. No scanout pixels are copied into the button. */
final class FooterLegend {
    static final String[] LABELS={"Select","Back","Ratings","Help","Retire"};
    static final int[] RIGHT={88,71,107,70,83};
    private static final int[][] STROKES={
        {25,3,22,3,30,12,33,12,35,6,32,6,37,5,40,5,46,4,43,4,48,4,51,4,56,11,60,11,60,2,60,5,65,4,62,4,72,5,75,5,77,2,74,2,80,5,84,5},
        {24,2,24,0,27,7,31,7,29,12,32,12,35,13,32,13,36,12,33,12,40,6,43,6,47,13,44,13,54,5,57,5,60,7,57,7,62,7,66,7},
        {24,2,21,2,30,7,34,7,37,13,34,13,42,4,45,4,52,5,56,5,56,2,56,5,63,4,66,4,72,9,76,9,80,7,84,7,86,5,89,5,91,5,88,5,98,12,101,12},
        {25,5,28,5,30,4,33,4,32,4,35,4,37,5,41,5,39,5,42,5,46,5,43,5,48,4,51,4,56,12,60,12,57,11,61,11,61,8,65,8},
        {21,2,17,2,25,9,29,9,26,7,29,7,33,5,36,5,34,3,38,3,41,4,38,4,45,5,49,5,46,3,50,3,53,7,57,7,54,5,57,5,60,6,57,6,64,7,68,7,66,5,69,5,72,6,68,6,74,7,78,7},
    };
    static boolean matches(LiveFrame frame,int center,int key){
        int[] points=STROKES[key];
        int pairs=points.length/4,required=(3*pairs+3)/4;
        for(int dy=0;dy<=2;dy++){
            int y=420+(dy==2?-1:dy);
            for(int dx=0;dx<7;dx++){
                int x=center+((dx&1)==0?dx/2:-(1+dx/2));
                if(x+RIGHT[key]>=640)continue;
                int votes=0,misses=0;
                for(int i=0;i<points.length;i+=4){
                    int light=frame.at(x+points[i],y+points[i+1]);
                    int dark=frame.at(x+points[i+2],y+points[i+3]);
                    int red=LiveFrame.red(light);
                    if(red>LiveFrame.green(light)*2 && red-LiveFrame.red(dark)>32){
                        if(++votes>=required)return true;
                    }else if(++misses>pairs-required)break;
                }
            }
        }
        return false;
    }
    private FooterLegend(){}
}
