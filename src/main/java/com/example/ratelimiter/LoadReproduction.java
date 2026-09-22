package com.example.ratelimiter;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Runs the rate limiter under sustained concurrent load and prints raw
 * counters. This is a manual reproduction tool, not a unit test - it is not
 * run by `mvn test` and is not held to the determinism constraints that
 * apply to tests you write. See README.md's "Reproducing Concurrency Issues
 * Under Load" section.
 */
public class LoadReproduction {

    private static final int MAX_REQUESTS_PER_WINDOW = 5;
    private static final long WINDOW_SIZE_MS = 2000;
    private static final int TRAFFIC_THREADS = 16;
    private static final int REQUESTS_PER_TRAFFIC_THREAD = 2000;
    private static final long RUN_TIMEOUT_SECONDS = 15;

    private static final List<String> USER_IDS = Arrays.asList(
            "user1", "user2", "user3", "user4", "user5", "user6", "user7", "user8"
    );

    private static final class StopFlag {
        volatile boolean stop = false;
    }

    public static void main(String[] args) throws InterruptedException {
        FlawedRateLimiter limiter = new FlawedRateLimiter(MAX_REQUESTS_PER_WINDOW, WINDOW_SIZE_MS);

        System.out.println("=== Load Reproduction ===");
        System.out.println("Users: " + USER_IDS);
        for (String userId : USER_IDS) {
            System.out.println("  " + userId + " hashCode parity: " + (userId.hashCode() % 2 == 0 ? "even" : "odd"));
        }
        System.out.println();

        AtomicInteger totalAttempts = new AtomicInteger(0);
        AtomicInteger totalAllowed = new AtomicInteger(0);
        AtomicLong resetCalls = new AtomicLong(0);
        AtomicLong toggleCalls = new AtomicLong(0);
        Map<String, AtomicInteger> perUserAllowed = new ConcurrentHashMap<>();
        for (String userId : USER_IDS) {
            perUserAllowed.put(userId, new AtomicInteger(0));
        }

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch trafficDoneLatch = new CountDownLatch(TRAFFIC_THREADS);
        StopFlag backgroundStop = new StopFlag();

        // Daemon threads: if the limiter deadlocks (a synchronized-block wait
        // is not interruptible), the JVM must still be able to exit once
        // main() is done reporting results, instead of hanging forever.
        ExecutorService trafficExecutor = Executors.newFixedThreadPool(TRAFFIC_THREADS, LoadReproduction::newDaemonThread);
        ExecutorService backgroundExecutor = Executors.newFixedThreadPool(2, LoadReproduction::newDaemonThread);

        for (int t = 0; t < TRAFFIC_THREADS; t++) {
            final int threadIndex = t;
            trafficExecutor.submit(() -> {
                await(startLatch);
                try {
                    for (int i = 0; i < REQUESTS_PER_TRAFFIC_THREAD; i++) {
                        String userId = USER_IDS.get((threadIndex + i) % USER_IDS.size());
                        totalAttempts.incrementAndGet();
                        if (limiter.allowRequest(userId)) {
                            totalAllowed.incrementAndGet();
                            perUserAllowed.get(userId).incrementAndGet();
                        }
                    }
                } finally {
                    trafficDoneLatch.countDown();
                }
            });
        }

        backgroundExecutor.submit(() -> {
            await(startLatch);
            while (!backgroundStop.stop) {
                for (String userId : USER_IDS) {
                    limiter.resetUserLimit(userId);
                    resetCalls.incrementAndGet();
                }
                sleepQuietly(20);
            }
        });

        backgroundExecutor.submit(() -> {
            await(startLatch);
            boolean enabled = true;
            while (!backgroundStop.stop) {
                enabled = !enabled;
                limiter.setEnabled(enabled);
                toggleCalls.incrementAndGet();
                sleepQuietly(5);
            }
            limiter.setEnabled(true);
        });

        long start = System.currentTimeMillis();
        startLatch.countDown();

        boolean trafficFinishedInTime = trafficDoneLatch.await(RUN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        long elapsedMs = System.currentTimeMillis() - start;

        backgroundStop.stop = true;
        backgroundExecutor.shutdown();
        boolean backgroundTerminatedInTime = backgroundExecutor.awaitTermination(RUN_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        trafficExecutor.shutdown();
        if (!trafficFinishedInTime) {
            trafficExecutor.shutdownNow();
        }

        System.out.println();
        System.out.println("=== Results ===");
        System.out.println("Elapsed ms: " + elapsedMs);
        System.out.println("Traffic threads finished within timeout: " + trafficFinishedInTime);
        System.out.println("Background threads stopped within timeout: " + backgroundTerminatedInTime);
        System.out.println("Reset calls made: " + resetCalls.get());
        System.out.println("Toggle calls made: " + toggleCalls.get());
        System.out.println();
        System.out.println("Total attempts:            " + totalAttempts.get());
        System.out.println("Total allowed (observed):  " + totalAllowed.get());
        System.out.println("limiter.getTotalRequestCount(): " + limiter.getTotalRequestCount());
        System.out.println("limiter.getAllRequestsSize():   " + limiter.getAllRequestsSize());
        System.out.println("limiter.getUserHistorySize():   " + limiter.getUserHistorySize());
        System.out.println();
        System.out.println("Per-user allowed counts:");
        for (String userId : USER_IDS) {
            System.out.println("  " + userId + ": " + perUserAllowed.get(userId).get());
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static Thread newDaemonThread(Runnable runnable) {
        Thread thread = new Thread(runnable);
        thread.setDaemon(true);
        return thread;
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
