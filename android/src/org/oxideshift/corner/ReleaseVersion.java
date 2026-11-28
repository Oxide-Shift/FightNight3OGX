package org.oxideshift.corner;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Versions accepted from this project's Android release tags. */
final class ReleaseVersion implements Comparable<ReleaseVersion> {
    private static final Pattern TAG=Pattern.compile("v?(\\d{1,8})\\.(\\d{1,8})\\.(\\d{1,8})(?:-(alpha|beta|rc)\\.(\\d{1,8})(?:\\.(\\d{1,3}))?)?");
    final String name;
    private final int[] parts;
    private ReleaseVersion(String name,int[] parts){this.name=name;this.parts=parts;}

    static ReleaseVersion parse(String tag){
        if(tag==null)return null;
        Matcher m=TAG.matcher(tag);
        if(!m.matches())return null;
        String stage=m.group(4);
        int order=stage==null?3:"alpha".equals(stage)?0:"beta".equals(stage)?1:2;
        return new ReleaseVersion(tag.startsWith("v")?tag.substring(1):tag,new int[]{
                Integer.parseInt(m.group(1)),Integer.parseInt(m.group(2)),Integer.parseInt(m.group(3)),
                order,m.group(5)==null?0:Integer.parseInt(m.group(5)),m.group(6)==null?-1:Integer.parseInt(m.group(6))});
    }
    String asset(){return "FightNight3OGX-"+name+".apk";}
    /** First release tagged in the plain scheme: v84.0.0 is "Alpha 84", v84.1.0 its first hotfix "Alpha 84.1".
     *  Installed apps up to Alpha 83 only accept three-number tags, and 84.0.0 sorts above every 0.4.0-alpha.NN. */
    static final int PLAIN_SCHEME=84;
    /** The name players see: "Alpha 84", "Alpha 84.1"; older tags read "Alpha 83", "Alpha 82.1". */
    String display(){
        if(parts[3]==0)return "Alpha "+parts[4]+(parts[5]>=0?"."+parts[5]:"");
        if(parts[3]==3 && parts[0]>=PLAIN_SCHEME)
            return "Alpha "+parts[0]+(parts[1]>0||parts[2]>0?"."+parts[1]:"")+(parts[2]>0?"."+parts[2]:"");
        return name;
    }
    /** Display name for an installed versionName, falling back to the raw text. */
    static String display(String versionName){
        ReleaseVersion v=parse(versionName);
        return v==null?versionName:v.display();
    }
    @Override public int compareTo(ReleaseVersion other){
        for(int i=0;i<parts.length;i++){
            int result=Integer.compare(parts[i],other.parts[i]);
            if(result!=0)return result;
        }
        return 0;
    }
}
