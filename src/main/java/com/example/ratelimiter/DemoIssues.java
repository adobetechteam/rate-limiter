package com.example.ratelimiter;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Targeted demos that show observable symptoms of specific flaws.
 * Run with: mvn -q compile && mvn exec:java -Dexec.mainClass="com.example.ratelimiter.DemoIssues"
 *
 * Unlike LoadReproduction (which is a broad concurrent stress tool), each demo
 * here isolates one issue so you can see it clearly before reading the code.
 */
public class DemoIssues {

    private static class ManualClock extends Clock {
        private long millis;

        ManualClock(long millis) {
            this.millis = millis;
        }

        void advance(long ms) {
            this.millis += ms;
        }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return Instant.ofEpochMilli(millis); }
    }

    public static void main(String[] args) throws InterruptedException {
        demoMemoryGrowth();
        demoConcurrencyOverAdmission();
    }

    private static void demoMemoryGrowth() {
        System.out.println("=== Demo 1: Memory Growth ===");
        System.out.println("Simulating 100 time windows of traffic (3 users, 5 req/window).");
        System.out.println();

        ManualClock clock = new ManualClock(0);
        FlawedRateLimiter limiter = new FlawedRateLimiter(5, 1000, clock);

        for (int window = 0; window < 100; window++) {
            for (int i = 0; i < 5; i++) {
                limiter.allowRequest("user" + (i % 3));
            }
            clock.advance(1001);
        }

        System.out.println("  Windows elapsed:       100");
        System.out.println("  allRequests size:      " + limiter.getAllRequestsSize());
        System.out.println("  Only last window matters for rate limiting, yet all records are kept.");
        System.out.println();

        limiter.cleanupOldRequests();
        System.out.println("  After cleanupOldRequests(): " + limiter.getAllRequestsSize());
        System.out.println("  Cleanup uses a hardcoded 1-hour threshold — with a 1-second window,");
        System.out.println("  records pile up long before they're eligible for removal.");
        System.out.println();
    }

    private static void demoConcurrencyOverAdmission() throws InterruptedException {
        System.out.println("=== Demo 4: Concurrency — Over-admission ===");

        int limit = 5;
        int threadCount = 30;
        int trials = 5;
        int fillerUsers = 5000;

        String userId = pickUnsyncedUser();
        System.out.println("  Limit: " + limit + " requests per window");
        System.out.println("  Threads per trial: " + threadCount);
        System.out.println("  User: \"" + userId + "\" (hashCode % 2 = " + (userId.hashCode() % 2) + ", unsynchronized path)");
        System.out.println("  Pre-filled with " + fillerUsers + " other users to slow down the scan.");
        System.out.println();

        int overAdmitCount = 0;
        for (int trial = 1; trial <= trials; trial++) {
            FlawedRateLimiter limiter = new FlawedRateLimiter(limit, 60000);

            for (int i = 0; i < fillerUsers; i++) {
                limiter.allowRequest("filler" + i);
            }

            CountDownLatch ready = new CountDownLatch(threadCount);
            CountDownLatch go = new CountDownLatch(1);
            AtomicInteger allowed = new AtomicInteger(0);
            AtomicInteger errors = new AtomicInteger(0);
            Thread[] threads = new Thread[threadCount];

            for (int i = 0; i < threadCount; i++) {
                threads[i] = new Thread(() -> {
                    ready.countDown();
                    try {
                        go.await();
                        if (limiter.allowRequest(userId)) {
                            allowed.incrementAndGet();
                        }
                    } catch (Exception e) {
                        errors.incrementAndGet();
                    }
                });
                threads[i].setDaemon(true);
                threads[i].start();
            }

            ready.await();
            go.countDown();
            for (Thread t : threads) t.join(5000);

            boolean overLimit = allowed.get() > limit;
            if (overLimit) overAdmitCount++;
            String suffix = overLimit ? "  <-- OVER LIMIT (expected max " + limit + ")" : "";
            if (errors.get() > 0) suffix += "  (" + errors.get() + " threads hit exceptions)";
            System.out.println("  Trial " + trial + ": " + allowed.get() + " allowed" + suffix);
        }

        System.out.println();
        if (overAdmitCount > 0) {
            System.out.println("  " + overAdmitCount + "/" + trials + " trials exceeded the limit.");
            System.out.println("  Multiple threads scan the list simultaneously, all see count < limit,");
            System.out.println("  and all decide to allow — a classic check-then-act race.");
        } else {
            System.out.println("  Race did not manifest this run — try again or increase thread count.");
        }
        System.out.println();
    }

    private static String pickUnsyncedUser() {
        for (int i = 0; i < 100; i++) {
            String candidate = "user" + i;
            if (candidate.hashCode() % 2 != 0) return candidate;
        }
        return "user1";
    }
}
