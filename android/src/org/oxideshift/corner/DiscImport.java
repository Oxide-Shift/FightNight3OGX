package org.oxideshift.corner;

import java.io.*;
import java.nio.file.AccessDeniedException;
import java.util.Locale;
import java.util.zip.ZipException;

/** Only these enumerated reasons and bounded disc metadata are eligible for a report. */
final class DiscImport implements GameLibrary.Status {
    enum Reason {
        ARCHIVE(-101), COMPRESSED(-102), SPLIT(-103), FILESYSTEM(-104), WRONG_TITLE(-105),
        REVISION(-106), MISSING_XBE(-107), INVALID_XBE(-108), TRUNCATED(-109), INVALID_TREE(-110),
        ZIP_CONTENTS(-111), ZIP_INTEGRITY(-112), SPACE(-113), ACCESS(-114), COMPILER(-115),
        SIZE(-116), INSTALLED(-117), READ(-118), WRONG_PLATFORM(-119), MENU_ASSET(-120);
        final int code;
        Reason(int code){this.code=code;}
    }
    static final class Failure extends IOException {
        final Reason reason;
        String format, title, region, revision, hash;
        Failure(Reason reason,String message){super(message);this.reason=reason;}
        Failure format(String kind){format=kind;return this;}
        Failure xbe(int game,int mask,int version,String sha){
            title=String.format(Locale.ROOT,"%08x",game);
            region=String.format(Locale.ROOT,"%08x",mask);
            revision=Integer.toUnsignedString(version);hash=sha;return this;
        }
    }
    private final GameLibrary.Status display;
    String stage="open", format="unknown";
    private boolean zipContainer;
    long bytes=-1;
    DiscFacts facts=new DiscFacts();
    @Override public void discFacts(DiscFacts observed){facts=observed;}
    DiscImport(GameLibrary.Status display){this.display=display;}
    @Override public void update(String text){display.update(text);}
    @Override public void stage(String value){stage=value;}
    @Override public void format(String value){
        if(!zipContainer)format=value;
        if(value.equals("zip"))zipContainer=true;
    }
    void filenameHint(String name){
        if(name==null)return;
        String lower=name.toLowerCase(Locale.ROOT);
        if(lower.matches(".*(?:\\.\\d+\\.(?:iso|cci|cso)|\\.iso\\.\\d+)$")){format="split";return;}
        for(String ext:new String[]{"7z","rar","zip","chd","cci","cso","zar","xiso","iso","xbe","xex","img"})
            if(lower.endsWith("."+ext)){format=ext;return;}
    }
    Failure explain(Exception e){
        if(e instanceof Failure){
            Failure failure=(Failure)e;
            if(failure.reason==Reason.FILESYSTEM || failure.reason==Reason.TRUNCATED){
                if(format.equals("split"))return new Failure(Reason.SPLIT,"This looks like part of a split disc. Join all parts into one Xbox ISO, then select it.");
                if(format.equals("zar"))return new Failure(Reason.COMPRESSED,"Convert this ZAR game archive to a single Xbox XISO, then select the result.");
                if(format.equals("xbe") || format.equals("xex"))return new Failure(Reason.FILESYSTEM,"You selected an executable. Choose the complete original Xbox disc image with its game assets.");
            }
            return failure;
        }
        if(e instanceof SecurityException || e instanceof FileNotFoundException || e instanceof AccessDeniedException)
            return new Failure(Reason.ACCESS,"Android could not open the selected file. Copy it to local storage and select it again.");
        if(e instanceof EOFException)return new Failure(Reason.TRUNCATED,"The image ended before all of its data could be read. Check that the copy or download finished.");
        if(e instanceof ZipException)return new Failure(Reason.ZIP_INTEGRITY,"The ZIP could not be opened or unpacked. Test the archive, or extract the ISO and select it directly.");
        if(e.getMessage()!=null && (e.getMessage().contains("ENOSPC") || e.getMessage().toLowerCase(Locale.ROOT).contains("no space left")))
            return new Failure(Reason.SPACE,"The device ran out of storage. Free some space and try again.");
        if(stage.equals("prepare"))return new Failure(Reason.COMPILER,"Game preparation stopped after the disc was verified. Tap Prepare game to retry.");
        return new Failure(Reason.READ,"Android could not finish reading or installing the disc. Try a complete local copy and check available storage.");
    }
}
