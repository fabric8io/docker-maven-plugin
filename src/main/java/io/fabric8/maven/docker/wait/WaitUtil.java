package io.fabric8.maven.docker.wait;

import java.util.Arrays;
import java.util.concurrent.*;


/**
 * @author roland
 * @since 18.10.14
 */
public class WaitUtil {

    // how long to wait at max when doing a http ping
    private static final long DEFAULT_MAX_WAIT = 10 * 1000L;

    // How long to wait between pings
    private static final long WAIT_RETRY_WAIT = 500;


    private WaitUtil() {}

    public static long wait(int wait, Callable<Void> callable) throws ExecutionException, WaitTimeoutException {
        long now = System.currentTimeMillis();
        if (wait > 0) {
            try {
                FutureTask<Void> task = new FutureTask<>(callable);
                task.run();

                task.get(wait, TimeUnit.SECONDS);
            } catch (@SuppressWarnings("unused") InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (@SuppressWarnings("unused") TimeoutException e) {
                throw new WaitTimeoutException("timed out waiting for execution to complete: " + e, delta(now));
            }
        }
        return delta(now);
    }

    public static long wait(int maxWait, WaitChecker ... checkers) throws WaitTimeoutException {
        return wait(maxWait, Arrays.asList(checkers));
    }

    public static long wait(int maxWait, Iterable<WaitChecker> checkers) throws WaitTimeoutException {
        long max = maxWait > 0 ? maxWait : DEFAULT_MAX_WAIT;
        long now = System.currentTimeMillis();
        try {
            do {
                if (check(checkers)) {
                    return delta(now);
                }
                sleep(WAIT_RETRY_WAIT);
            } while (delta(now) < max);

            throw new WaitTimeoutException("No checker finished successfully", delta(now));

        } finally {
            cleanup(checkers);
        }
    }

    private static boolean check(Iterable<WaitChecker> checkers) {
        for (WaitChecker checker : checkers) {
            if (checker.check()) {
                return true;
            }
        }
        return false;
    }

    // Give checkers a possibility to clean up
    private static void cleanup(Iterable<WaitChecker> checkers) {
        for (WaitChecker checker : checkers) {
            checker.cleanUp();
        }
    }

    /**
     * Sleep a bit
     *
     * @param millis how long to sleep in milliseconds
     */
    public static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            // ...
            Thread.currentThread().interrupt();
        }
    }

    private static long delta(long now) {
        return System.currentTimeMillis() - now;
    }


}
