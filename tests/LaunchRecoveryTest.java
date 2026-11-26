package org.oxideshift.corner;
public class LaunchRecoveryTest {
    static void eq(Object actual, Object expected) {
        if (!actual.equals(expected)) throw new AssertionError(actual + " != " + expected);
    }
    public static void main(String[] args) {
        LaunchRecovery p = new LaunchRecovery(LaunchRecovery.Mode.VULKAN, false);
        long a = p.begin(0);
        eq(p.sample(a, 1000, 60, -1, false), LaunchRecovery.Action.WAIT);
        p.pause(a, 2000, true);
        eq(p.sample(a, 80000, 100, 100, true), LaunchRecovery.Action.WAIT);
        p.pause(a, 81000, false);
        eq(p.sample(a, 108999, 900, -1, false), LaunchRecovery.Action.WAIT);
        eq(p.sample(a, 109000, 1000, -1, false), LaunchRecovery.Action.REQUEST_STOP);
        eq(p.exited(a, LaunchRecovery.Failure.NO_FRAME), LaunchRecovery.Action.RETRY);
        eq(p.mode(), LaunchRecovery.Mode.VULKAN_GENERAL);
        long b = p.begin(110000);
        eq(p.exited(a, LaunchRecovery.Failure.SHADER), LaunchRecovery.Action.IGNORE);
        eq(p.sample(b, 110050, 8, 8, true), LaunchRecovery.Action.WAIT);
        eq(p.sample(b, 110080, 9, 8, true), LaunchRecovery.Action.WAIT);
        eq(p.sample(b, 110100, 10, 9, true), LaunchRecovery.Action.PLAYING);
        eq(p.exited(b, LaunchRecovery.Failure.NATIVE_UNKNOWN), LaunchRecovery.Action.EXPLAIN);
        for (LaunchRecovery.Failure f : LaunchRecovery.Failure.values()) {
            LaunchRecovery q = new LaunchRecovery(LaunchRecovery.Mode.VULKAN, false);
            LaunchRecovery.Action action = q.exited(q.begin(0), f);
            if (action == LaunchRecovery.Action.RETRY) {
                eq(q.exited(q.begin(1), f), LaunchRecovery.Action.EXPLAIN);
                eq(q.recoveryUsed(), true);
            }
            if (f == LaunchRecovery.Failure.WRONG_DISC || f == LaunchRecovery.Failure.BAD_MODULE ||
                f == LaunchRecovery.Failure.NATIVE_UNKNOWN || f == LaunchRecovery.Failure.USER_CANCEL)
                eq(action, LaunchRecovery.Action.EXPLAIN);
        }
        System.out.println("Launch recovery, pause, stale callback, frame sequence and retry tests passed");
    }
}
