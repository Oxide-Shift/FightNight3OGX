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
        // Update offer: bold bullet lead-ins become the "what's new" lines; plain notes fall back to the first paragraph.
        String notes="Alpha 83 makes installing easier.\n\n- **7z archives work.** Choose the `.7z`...\n"+
                "- **Clearer message for European discs.** The PAL release...\n* **Anonymous player count.** While...\n\nAPK SHA-256: `x`";
        check(ReleaseAsset.highlights(notes).equals("• 7z archives work\n• Clearer message for European discs\n• Anonymous player count"));
        check(ReleaseAsset.highlights("Just **one** fix for `audio`.\n\nMore text").equals("Just one fix for audio."));
        check(ReleaseAsset.highlights(null).isEmpty() && ReleaseAsset.highlights("").isEmpty());
        StringBuilder many=new StringBuilder();for(int i=0;i<40;i++)many.append("- **Change number ").append(i).append("** detail\n");
        check(ReleaseAsset.highlights(many.toString()).length()<=440);
        ReleaseAsset plain=ReleaseAsset.parse("v84.0.0",100023494L,DIGEST);
        plain.notes=ReleaseAsset.highlights(notes);
        String offer=ReleaseUpdates.offerText(plain);
        check(offer.startsWith("What's new:\n• 7z archives work") && offer.contains("Download 95 MB.") && offer.contains("carry over"));
        System.out.println("Release asset checks passed");
    }
}
