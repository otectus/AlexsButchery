package com.otectus.alexsbutchery.compat;

import com.otectus.alexsbutchery.AlexsButchery;

/** Bounded debug output for {@code compat.debugLogging}: at most 20 lines a minute, then one summary of what was skipped. */
public final class CompatLog {
    private static final int PER_WINDOW = 20;
    private static final long WINDOW_MS = 60_000L;
    private static long windowStart;
    private static int written;
    private static int skipped;

    public static synchronized void debug(String message) {
        long now = System.currentTimeMillis();
        if (now - windowStart > WINDOW_MS) {
            if (skipped > 0) AlexsButchery.LOGGER.info("Compatibility debug: {} more lines skipped in the last minute", skipped);
            windowStart = now;
            written = 0;
            skipped = 0;
        }
        if (written < PER_WINDOW) {
            written++;
            AlexsButchery.LOGGER.info("Compatibility debug: {}", message);
        } else skipped++;
    }

    private CompatLog() {}
}
