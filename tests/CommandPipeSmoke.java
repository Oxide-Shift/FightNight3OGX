package org.oxideshift.corner;

import java.io.*;
import java.util.concurrent.*;

public class CommandPipeSmoke {
    public static void main(String[] args)throws Exception{
        CornerCommands pipe=new CornerCommands();
        CountDownLatch blocked=new CountDownLatch(1),unblock=new CountDownLatch(1),done=new CountDownLatch(1);
        StringWriter captured=new StringWriter();
        Writer slow=new Writer(){
            public void write(char[] data,int offset,int length)throws IOException{
                blocked.countDown();
                try{if(!unblock.await(5,TimeUnit.SECONDS))throw new IOException("Producer stalled");}
                catch(InterruptedException e){throw new IOException(e);}
                captured.write(data,offset,length);
                if(captured.toString().contains("PING 7"))done.countDown();
            }
            public void flush(){} public void close(){}
        };
        Thread writer=new Thread(()->{
            try{pipe.writeTo(new BufferedWriter(slow));}catch(Exception e){throw new AssertionError(e);}
        });writer.setDaemon(true);writer.start();
        pipe.offer("RUN");
        if(!blocked.await(2,TimeUnit.SECONDS))throw new AssertionError("No write");
        String neutral="PAD 000000000000000000000000000000000000";
        for(int i=0;i<50000;i++){
            pipe.offer(i%2==0?"PAD 0000ff000000000000000000000000000000":neutral);
            if(i%500==0)pipe.offer("TRAX 1");
        }
        pipe.offer(neutral);pipe.offer("PING 7");unblock.countDown();
        if(!done.await(3,TimeUnit.SECONDS))throw new AssertionError("Lost heartbeat");
        pipe.close();writer.join(2000);
        String[] lines=captured.toString().split("\\R");
        if(writer.isAlive() || lines.length>68 || !lines[0].equals("RUN")
                || !lines[lines.length-2].equals(neutral))throw new AssertionError("Stuck pad or unbounded backlog");
        System.out.println("50,000 input changes with a stalled host pipe: dispatcher remained available and neutral state recovered");
    }
}
