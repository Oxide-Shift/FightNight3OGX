package org.oxideshift.corner;

public final class CutmanStrokeSmoke {
    public static void main(String[] args){
        int[] last=new int[4];
        CutmanStroke stroke=new CutmanStroke((down,x,y)->{
            last[0]=down?1:0;last[1]=x;last[2]=y;last[3]++;
        });
        for(int start:new int[]{4,97,1300}){
            stroke.begin(6,start,125);
            check(last[0]==1 && last[1]==0 && last[2]==0);
            stroke.begin(9,12,9);stroke.move(9,60,100,20);
            check(stroke.finger==6 && stroke.originX==start && last[1]==0);
            stroke.move(6,start+25,125,50);check(last[1]==16384 && last[2]==0);
            stroke.move(6,start-100,125,50);check(last[1]==-32767);
            stroke.move(6,start,25,50);check(last[2]==32767);
            stroke.move(6,start+80,205,50);check(Math.abs(last[1]-23170)<=1 && Math.abs(last[2]+23170)<=1);
            stroke.release();check(last[0]==0 && last[1]==0 && last[2]==0);
            int sends=last[3];stroke.move(6,300,0,1);stroke.release();check(last[3]==sends);
        }
        System.out.println("Touch origins, diagonals, clamping, pointer ownership and neutral release passed.");
    }
    private static void check(boolean condition){if(!condition)throw new AssertionError();}
}
