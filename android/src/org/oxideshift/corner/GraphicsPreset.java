package org.oxideshift.corner;

/** The five stored quality values are shared by the launcher and installed games. */
enum GraphicsPreset {
    LOW(0,1,1), MEDIUM(1,1,2), HIGH(2,2,4), HIGH_NO_MSAA(2,1,4), MEDIUM_MSAA(1,2,2);

    final int tier,samples,anisotropy;
    GraphicsPreset(int tier,int samples,int anisotropy){
        this.tier=tier;this.samples=samples;this.anisotropy=anisotropy;
    }
    static GraphicsPreset stored(int value){
        return value>=0 && value<values().length?values()[value]:MEDIUM;
    }
    static GraphicsPreset select(int tier,boolean smoothEdges){
        if(tier==0)return LOW;
        if(tier==2)return smoothEdges?HIGH:HIGH_NO_MSAA;
        return smoothEdges?MEDIUM_MSAA:MEDIUM;
    }
}
