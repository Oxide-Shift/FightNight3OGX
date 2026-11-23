package org.oxideshift.corner;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

public final class ReleaseAssetSmoke {
    private static final String DIGEST="sha256:ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
    private static void check(boolean condition){if(!condition)throw new AssertionError();}
    public static void main(String[] args)throws Exception{
        ReleaseAsset asset=ReleaseAsset.parse("v0.4.0-alpha.26",3,DIGEST);
        check(asset!=null && asset.path().equals("/apk/FightNight3OGX-0.4.0-alpha.26.apk"));
        check(ReleaseAsset.parse("v0.4.0/elsewhere",3,DIGEST)==null);
        check(ReleaseAsset.parse("v1.0.0",0,DIGEST)==null);
        check(ReleaseAsset.parse("v1.0.0",268435457L,DIGEST)==null);
        check(ReleaseAsset.parse("v1.0.0",3,"sha256:bad")==null);
        byte[] good={'a','b','c'};int[] percent={0};
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        asset.copyVerified(new ByteArrayInputStream(good),out,n->percent[0]=n);
        check(Arrays.equals(good,out.toByteArray()) && percent[0]==100);
        for(byte[] bad:new byte[][]{{'a','b'},{'a','b','c','d'},{'a','b','d'}}){
            try{asset.copyVerified(new ByteArrayInputStream(bad),null,n->{});throw new AssertionError();}
            catch(IOException expected){}
        }
        System.out.println("Release asset checks passed");
    }
}
