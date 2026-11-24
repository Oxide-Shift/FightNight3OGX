package org.oxideshift.corner;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Paths;

public final class FightBroadcastSmoke {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void meters(LiveFrame frame, int[] expected) {
        for (int i = 0; i < expected.length; i++) {
            int value = FightBroadcast.gauge(frame, i / 2, (i & 1) != 0);
            check(value == expected[i], "Meter " + i + " expected " + expected[i] + ", got " + value);
        }
    }

    public static void main(String[] args) throws Exception {
        FightBroadcast.Card fresh = FightBroadcast.Card.parse("1");
        check(fresh != null && fresh.last() == -1, "No scores before the bell");
        FightBroadcast.Card official = FightBroadcast.Card.parse("2 1,9,10,9,10,9,10 2,10,8,9,9,10,9");
        check(official.last() == 2 && official.round == 2, "Two posted rounds");
        check(official.total(0,0) == 19 && official.total(0,1) == 18, "First judge");
        check(official.total(1,0) == 18 && official.total(1,1) == 19, "Second judge");
        check(official.total(2,0) == 19 && official.total(2,1) == 19, "Third judge");
        for (String bad : new String[]{"", " ", "x", "17", "0 1,10,9,10,9,10,9", "2 0,10,9,10,9,10,9", "2 1,10,8", "2 1,11,9,9,9,10,9",
                "2 1,10,9,10,9,10,9 1,10,9,10,9,10,9"}) {
            check(FightBroadcast.Card.parse(bad) == null, "Invalid snapshot: " + bad);
        }
        check(FightBroadcast.Card.parse("0").last() == -1, "Scorecard reset");
        check("Never Gonna Get It".equals(FightBroadcast.song("SEANBIG1").title), "EA Trax archive entry");
        check(FightBroadcast.song("UNKNOWN") == null, "Unrecognised track");

        CareerDesk scout=new CareerDesk();scout.accept("CAREERPAGE","mcon 1");
        scout.accept("OFFER","536D697468 1000 71 68 72 64 69 71 4,1,0,2 9,2,0,6");
        CareerDesk.Offer selected=scout.selectedOffer();
        check(selected.own.wins==4 && selected.rival.knockouts==6 && selected.experience().contains("6 more"),"Highlighted records");
        scout.accept("OFFER","536D697468 1000 71 68 72 64 69 71 - -");
        check(scout.selectedOffer().rival==null && scout.selectedOffer().experience().isEmpty(),"Unknown records clear the old offer");
        LiveFrame empty = new LiveFrame(ByteBuffer.allocate(8 + 4*640*480).order(ByteOrder.LITTLE_ENDIAN),
                640, 480, 1, true);
        meters(empty, new int[]{-1,-1,-1,-1});
        for (String path : args) {
            ByteBuffer buf = ByteBuffer.wrap(Files.readAllBytes(Paths.get(path))).order(ByteOrder.LITTLE_ENDIAN);
            LiveFrame frame = new LiveFrame(buf, buf.getInt(0), buf.getInt(4), 1, true);
            check(frame.scene() == LiveFrame.Scene.ROUND, "Expected the Warehouse round");
            int[] levels = new int[4];
            for (int i=0;i<4;i++) levels[i]=FightBroadcast.gauge(frame,i/2,(i&1)!=0);
            System.out.println(frame.height + "p: " + java.util.Arrays.toString(levels));
            meters(frame, new int[]{100, 91, 77, 97});
        }
        System.out.println("Fight broadcast checks passed");
    }
}
