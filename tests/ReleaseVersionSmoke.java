package org.oxideshift.corner;

final class ReleaseVersionSmoke {
    public static void main(String[] args){
        String[] order={"v0.4.0-alpha.9","0.4.0-alpha.22","v0.4.0-alpha.23","0.4.0-alpha.78","0.4.0-alpha.78.1","0.4.0-alpha.78.3","0.4.0-alpha.79","0.4.0-alpha.79.5","0.4.0-alpha.80","0.4.0-alpha.80.1","0.4.0-beta.1",
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
        // Plain scheme from Alpha 84: three numbers so installed updaters accept it, sorting above every older tag.
        String[] plain={"0.4.0-alpha.83","v84.0.0","84.1.0","84.2.0","85.0.0"};
        for(int i=1;i<plain.length;i++)
            if(ReleaseVersion.parse(plain[i-1]).compareTo(ReleaseVersion.parse(plain[i]))>=0)
                throw new AssertionError(plain[i]);
        String[][] names={{"v84.0.0","Alpha 84"},{"84.1.0","Alpha 84.1"},{"85.0.0","Alpha 85"},{"0.4.0-alpha.83","Alpha 83"},
                {"0.4.0-alpha.82.1","Alpha 82.1"},{"0.4.0-beta.1","0.4.0-beta.1"},{"1.0.0","1.0.0"}};
        for(String[] n:names)
            if(!ReleaseVersion.parse(n[0]).display().equals(n[1]))throw new AssertionError(n[0]+" -> "+ReleaseVersion.parse(n[0]).display());
        if(!ReleaseVersion.display("dev-build").equals("dev-build") || !ReleaseVersion.parse("v84.0.0").asset().equals("FightNight3OGX-84.0.0.apk"))
            throw new AssertionError("raw fallback and plain asset name");
        System.out.println("Release tag order, display names and Thor APK filename passed");
    }
}
