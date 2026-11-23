import java.io.*;
import java.util.*;
import org.oxideshift.corner.*;

public class CornerSmoke {
    public static void main(String[] args) throws Exception {
        try (FileInputStream iso = new FileInputStream(args[0])) {
            DiscImage disc = new DiscImage(iso.getChannel());
            if (disc.count() != 901 || disc.bytes() != 2403279877L) throw new AssertionError("Disc inventory");
            System.out.println("Verified retail XBE, " + disc.count() + " files, " + disc.bytes() + " bytes");
            if (args.length == 6) {
                java.nio.file.Path stage = java.nio.file.Files.createDirectory(new File(args[5]).toPath());
                disc.extract(stage, (n,total)->{});
                System.out.println("Extracted " + disc.count() + " files");
            }
        }
        CornerSession session = new CornerSession(new File(args[4]), args[1], args[2], args[3], args[4], "--corner");
        Thread worker = new Thread(session::run); worker.start();
        long deadline = System.nanoTime() + 45_000_000_000L;
        boolean sawLive = false;
        while (worker.isAlive() && System.nanoTime() < deadline) {
            Map<String,Object> sample = session.snapshot();
            if (sample.get("link").equals("Live")) sawLive = true;
            if (Integer.parseInt((String)sample.get("frames")) >= 2) {
                session.stop();
                break;
            }
            Thread.sleep(5);
        }
        worker.join(3000);
        if (worker.isAlive()) { session.stop(); worker.join(3000); throw new AssertionError("Boot deadline"); }
        Map<String,Object> state = session.snapshot();
        System.out.println(state);
        if (!sawLive || !state.get("link").equals("Closed") || (int)state.get("replies") < 2 ||
            !state.get("bridge").equals("150/150") || !state.get("thread").toString().matches("0x[0-9A-Fa-f]{8}") ||
            Integer.parseInt((String)state.get("frames")) < 2)
            throw new AssertionError("Runtime telemetry");
    }
}
