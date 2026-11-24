package org.oxideshift.corner;

import java.io.File;
import java.util.Map;

public class LinkSmoke {
    public static void main(String[] args) throws Exception {
        File peer = new File(args[0]).getAbsoluteFile();
        for(int i=0;i<2;i++) {
            CornerSession s = new CornerSession(()->System.nanoTime()/1_000_000,peer.getParentFile(),peer.getPath());
            Thread worker = new Thread(s::run); worker.start();
            try {
                long deadline = System.nanoTime()+5_000_000_000L;
                while((int)s.snapshot().get("replies")<6 && System.nanoTime()<deadline) Thread.sleep(20);
                Map<String,Object> live=s.snapshot();
                if (!live.get("link").equals("Live") || !live.get("phase").equals("Link check") ||
                    (int)live.get("replies")<6 || !live.get("bridge").equals("—")) throw new AssertionError(live);
                System.out.println(live);
            } finally {s.stop();worker.join(2500);}
            if(worker.isAlive() || !s.snapshot().get("link").equals("Closed"))throw new AssertionError("Disconnect");
        }
    }
}
