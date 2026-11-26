package org.oxideshift.corner;

/** Crash-report log tail: private paths, URIs and e-mail-like text never leave the device; size stays bounded. */
public final class DiagnosticTailSmoke {
    private static void check(boolean ok,String what){if(!ok)throw new AssertionError(what);}
    public static void main(String[] args){
        String s=DiagnosticTail.scrub("open /data/user/0/org.oxideshift.corner/files/retail/game/default.xbe failed");
        check(s.equals("open <app> failed"),"app path: "+s);
        check(DiagnosticTail.scrub("disc /storage/emulated/0/Download/My Games/fn3.iso").startsWith("disc <storage>"),"storage path");
        check(DiagnosticTail.scrub("uri content://com.android.providers.downloads/document/12").equals("uri <uri>"),"content uri");
        check(DiagnosticTail.scrub("contact player.name@example.com now").equals("contact <email> now"),"email");
        check(DiagnosticTail.scrub("café \u0000bell").equals("caf   bell"),"non-printable become spaces");
        check(DiagnosticTail.scrub(new String(new char[500]).replace('\0','x')).length()==DiagnosticTail.LINE_CHARS,"line cap");
        DiagnosticTail tail=new DiagnosticTail();
        for(int i=0;i<100;i++)tail.add("line "+i+" "+new String(new char[190]).replace('\0','y'));
        check(tail.size()==DiagnosticTail.LINES,"line count");
        String text=tail.text();
        check(text.length()<=DiagnosticTail.TOTAL_CHARS && text.endsWith("\n") && text.contains("line 99 "),"bounded newest tail");
        check(!text.contains("line 59 "),"oldest lines dropped");
        tail.add("   ");
        check(tail.size()==DiagnosticTail.LINES,"blank lines ignored");
        System.out.println("Crash log tail removes private paths and stays bounded");
    }
}
