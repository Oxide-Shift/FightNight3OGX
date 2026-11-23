package org.oxideshift.corner;

final class ReleaseVersionSmoke {
    public static void main(String[] args){
        String[] order={"v0.4.0-alpha.9","0.4.0-alpha.22","v0.4.0-alpha.23","0.4.0-beta.1",
                "0.4.0-rc.1","v0.4.0","0.4.1-alpha.1","1.0.0"};
        for(int i=1;i<order.length;i++)
            if(ReleaseVersion.parse(order[i-1]).compareTo(ReleaseVersion.parse(order[i]))>=0)
                throw new AssertionError(order[i]);
        for(String bad:new String[]{"","v1.2","../1.2.3","1.2.3-alpha","1.0.0-rc.99999999999999"})
            if(ReleaseVersion.parse(bad)!=null)throw new AssertionError(bad);
        ReleaseVersion version=ReleaseVersion.parse("v0.4.0-alpha.23");
        if(version.compareTo(ReleaseVersion.parse("0.4.0-alpha.23"))!=0 ||
                !version.asset().equals("FightNight3OGX-0.4.0-alpha.23.apk"))
            throw new AssertionError("Thor release asset");
        System.out.println("Release tag order and Thor APK filename passed");
    }
}
