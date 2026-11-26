package org.oxideshift.corner;

/** One foreground startup attempt. The owner persists mode and recoveryUsed before spawn.
 * Call from one serialized session executor. All callbacks carry the child generation. */
final class LaunchRecovery {
    enum Mode { VULKAN, VULKAN_GENERAL, SOFTWARE }
    enum Failure { UNSUPPORTED_GPU, GPU_MEMORY, SHADER, DEVICE_LOST, NO_FRAME,
                   NATIVE_UNKNOWN, BAD_MODULE, WRONG_DISC, USER_CANCEL }
    enum Action { IGNORE, WAIT, REQUEST_STOP, RETRY, EXPLAIN, PLAYING }
    private long generation;
    private long lastObservation, idleMillis;
    private long guestFrame = -1, presentedGuestFrame = -1;
    private boolean active, shown, paused, terminal, recoveryUsed, stopRequested;
    private Mode mode;

    LaunchRecovery(Mode initial, boolean recoveryUsed) {
        this.mode = initial;
        this.recoveryUsed = recoveryUsed;
    }
    Mode mode() { return mode; }
    boolean recoveryUsed() { return recoveryUsed; }

    long begin(long now) {
        if (active) throw new IllegalStateException("Previous child still owns session");
        generation++;
        active = true;
        paused = terminal = shown = stopRequested = false;
        guestFrame = presentedGuestFrame = -1;
        idleMillis = 0;
        lastObservation = now;
        return generation;
    }
    private void tick(long now) {
        if (!paused) idleMillis += Math.max(0, now - lastObservation);
        lastObservation = Math.max(now, lastObservation);
    }
    void pause(long id, long now, boolean value) {
        if (!active || id != generation) return;
        tick(now);
        paused = value;
    }
    Action sample(long id, long now, long guest, long presented, boolean gameSurface) {
        if (!active || id != generation || terminal || stopRequested) return Action.IGNORE;
        tick(now);
        if (paused) return Action.WAIT;
        // Renderer must publish the guest sequence actually drawn on the game surface.
        // Guest progress alone cannot indefinitely postpone a missing presentation.
        boolean advanced = guest > guestFrame;
        guestFrame = Math.max(guestFrame, guest);
        if (gameSurface && presented >= 0 && presented <= guestFrame && presented > presentedGuestFrame) {
            if (presentedGuestFrame >= 0 && (advanced || guestFrame > presentedGuestFrame)) shown = true;
            presentedGuestFrame = presented;
            idleMillis = 0;
        }
        if (shown) return Action.PLAYING;
        if (idleMillis < 30000) return Action.WAIT;
        stopRequested = true;
        return Action.REQUEST_STOP;
    }
    Action exited(long id, Failure failure) {
        if (!active || id != generation) return Action.IGNORE;
        active = false;
        terminal = true;
        // Only a completed process exit/reap can authorize another child.
        if (shown || recoveryUsed || mode == Mode.SOFTWARE) return Action.EXPLAIN;
        Mode next;
        switch (failure) {
            case UNSUPPORTED_GPU: next = Mode.SOFTWARE; break;
            case GPU_MEMORY: case SHADER: case DEVICE_LOST: case NO_FRAME:
                if (mode != Mode.VULKAN) return Action.EXPLAIN;
                next = Mode.VULKAN_GENERAL; break;
            default: return Action.EXPLAIN;
        }
        recoveryUsed = true;
        mode = next;
        return Action.RETRY;
    }
}
