package org.oxideshift.corner;

import java.util.ArrayDeque;
import java.util.regex.Pattern;

/**
 * The last game log lines, scrubbed for crash reports. Keeps the runtime's own messages (renderer, audio, milestones,
 * timing) and removes anything that could identify a person or their files: storage paths, content URIs, e-mail-like
 * text and non-printable characters. Bounded: 40 lines of at most 200 characters, 6,000 characters in total.
 */
final class DiagnosticTail {
    static final int LINES=40, LINE_CHARS=200, TOTAL_CHARS=6000;
    private static final Pattern APP=Pattern.compile("/data/(?:user/\\d+|data|user_de/\\d+)/[A-Za-z0-9._]+[^\\s]*");
    private static final Pattern STORAGE=Pattern.compile("(?:/storage|/sdcard|/mnt)/[^\\s]*");
    private static final Pattern URI=Pattern.compile("content://[^\\s]*");
    private static final Pattern MAIL=Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private final ArrayDeque<String> lines=new ArrayDeque<>();

    static String scrub(String raw){
        if(raw==null)return "";
        String s=raw.replaceAll("[^\\x20-\\x7e]"," ");
        s=APP.matcher(s).replaceAll("<app>");
        s=STORAGE.matcher(s).replaceAll("<storage>");
        s=URI.matcher(s).replaceAll("<uri>");
        s=MAIL.matcher(s).replaceAll("<email>");
        s=s.trim();
        return s.length()>LINE_CHARS?s.substring(0,LINE_CHARS):s;
    }
    synchronized void add(String raw){
        String s=scrub(raw);
        if(s.isEmpty())return;
        if(lines.size()==LINES)lines.removeFirst();
        lines.addLast(s);
    }
    /** Newest lines last, trimmed from the oldest end to fit TOTAL_CHARS. */
    synchronized String text(){
        StringBuilder out=new StringBuilder();
        for(String s:lines)out.append(s).append('\n');
        while(out.length()>TOTAL_CHARS){
            int cut=out.indexOf("\n");
            if(cut<0){out.setLength(TOTAL_CHARS);break;}
            out.delete(0,cut+1);
        }
        return out.toString();
    }
    synchronized int size(){return lines.size();}
}
