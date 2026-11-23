package org.oxideshift.corner;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.function.IntConsumer;
import org.json.JSONArray;
import org.json.JSONObject;

/** The exact signed APK advertised by this repository's release feed. */
final class ReleaseAsset {
    static final String REPO="Oxide-Shift/FightNight3OGX";
    final ReleaseVersion version;
    final String tag,digest;
    final long bytes;
    private ReleaseAsset(ReleaseVersion version,String tag,long bytes,String digest){
        this.version=version;this.tag=tag;this.bytes=bytes;this.digest=digest;
    }
    String path(){return "/apk/"+version.asset();}
    String url(){return "https://github.com/"+REPO+"/releases/download/"+tag+"/"+version.asset();}
    static ReleaseAsset parse(String tag,long size,String digest){
        ReleaseVersion version=ReleaseVersion.parse(tag);
        if(version==null || size<=0 || size>256L*1024*1024 || digest==null ||
                !digest.matches("sha256:[0-9a-fA-F]{64}"))return null;
        return new ReleaseAsset(version,tag,size,digest.toLowerCase(Locale.ROOT));
    }
    static ReleaseAsset newest(JSONArray rows,ReleaseVersion current){
        ReleaseAsset best=null;
        for(int i=0;i<rows.length();i++){
            JSONObject row=rows.optJSONObject(i);
            if(row==null || row.optBoolean("draft"))continue;
            String tag=row.optString("tag_name");
            JSONArray assets=row.optJSONArray("assets");
            if(assets==null)continue;
            for(int j=0;j<assets.length();j++){
                JSONObject a=assets.optJSONObject(j);
                if(a==null || !"uploaded".equals(a.optString("state")))continue;
                ReleaseAsset file=parse(tag,a.optLong("size"),a.optString("digest"));
                if(file!=null && file.version.compareTo(current)>0 && (best==null || file.version.compareTo(best.version)>0)
                        && file.version.asset().equals(a.optString("name")) && file.url().equals(a.optString("browser_download_url")))best=file;
            }
        }
        return best;
    }
    void copyVerified(InputStream in,OutputStream out,IntConsumer progress) throws Exception{
        MessageDigest hash=MessageDigest.getInstance("SHA-256");
        long received=0;
        byte[] buf=new byte[65536];
        int previous=-1;
        for(int n;(n=in.read(buf))!=-1;){
            received+=n;
            if(received>bytes)throw new IOException("Unexpected APK size");
            if(out!=null)out.write(buf,0,n);
            hash.update(buf,0,n);
            int percent=(int)(received*100/bytes);
            if(percent!=previous){progress.accept(percent);previous=percent;}
        }
        StringBuilder actual=new StringBuilder("sha256:");
        for(byte b:hash.digest())actual.append(String.format(Locale.ROOT,"%02x",b&255));
        if(received!=bytes || !digest.contentEquals(actual))throw new IOException("APK does not match the release");
    }
}
