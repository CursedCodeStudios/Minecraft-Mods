package dev.frydae.watcher;

/** Starts a fresh ten-second wait whenever the client becomes idle. */
public final class ReconnectDelay {
    private static final long RETRY_MILLIS = 10_000;
    private long nextAttempt = -1;

    public boolean ready(long now, boolean idle) {
        if (!idle) { nextAttempt = -1; return false; }
        if (nextAttempt < 0) nextAttempt = now + RETRY_MILLIS;
        if (now < nextAttempt) return false;
        nextAttempt = now + RETRY_MILLIS;
        return true;
    }
}
