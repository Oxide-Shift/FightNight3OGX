package org.oxideshift.corner;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Iterator;

/** Only the writer thread waits on the native pipe. Android dispatch and both
 * host output readers must stay available while the child is slow to read. */
final class CornerCommands {
    private final ArrayDeque<String> pending=new ArrayDeque<>();
    private boolean closed;

    synchronized void offer(String command){
        if(closed)return;
        if(command.startsWith("PAD ")){
            // Full snapshots. Under backpressure, retain button transitions first.
            String last=pending.peekLast();
            if(pending.size()>=4 && last!=null && last.regionMatches(0,command,0,24))pending.removeLast();
            if(pending.size()>=64){
                for(Iterator<String> it=pending.iterator();it.hasNext();){
                    if(it.next().startsWith("PAD "))it.remove();
                }
            }
        }else if(command.startsWith("TRAX ")){
            for(Iterator<String> it=pending.iterator();it.hasNext();){
                if(it.next().startsWith("TRAX "))it.remove();
            }
        }
        pending.addLast(command);notifyAll();
    }
    synchronized void close(){closed=true;pending.clear();notifyAll();}
    private synchronized String next() throws InterruptedException{
        while(!closed && pending.isEmpty())wait();
        return closed?null:pending.removeFirst();
    }
    void writeTo(BufferedWriter output)throws IOException,InterruptedException{
        try(BufferedWriter stream=output){
            for(String command;(command=next())!=null;){
                stream.write(command);stream.newLine();stream.flush();
            }
        }
    }
}
