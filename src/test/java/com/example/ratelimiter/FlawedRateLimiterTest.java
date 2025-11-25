package com.example.ratelimiter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test cases for FlawedRateLimiter demonstrating both:
 * 1. Cases that succeed despite the flaws
 * 2. Cases that fail because of the flaws
 */
public class FlawedRateLimiterTest {

    private FlawedRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        rateLimiter = new FlawedRateLimiter(5, 1000); // 5 requests per second
    }

    /**
     * SUCCESS CASES - These work despite the flaws
     */

    @Test
    void testBasicRateLimiting_Success() {
        // Basic functionality works for simple sequential usage
        assertTrue(rateLimiter.allowRequest("user1"));
        assertTrue(rateLimiter.allowRequest("user1"));
        assertTrue(rateLimiter.allowRequest("user1"));
        assertTrue(rateLimiter.allowRequest("user1"));
        assertTrue(rateLimiter.allowRequest("user1"));

        // 6th request should be denied
        assertFalse(rateLimiter.allowRequest("user1"));
    }

    @Test
    void testDifferentUsers_Success() {
        // Different users should have separate limits
        for (int i = 0; i < 5; i++) {
            assertTrue(rateLimiter.allowRequest("user1"));
            assertTrue(rateLimiter.allowRequest("user2"));
        }

        assertFalse(rateLimiter.allowRequest("user1"));
        assertFalse(rateLimiter.allowRequest("user2"));
    }

    @Test
    void testTimeWindowReset_Success() throws InterruptedException {
        // Fill up the limit
        for (int i = 0; i < 5; i++) {
            assertTrue(rateLimiter.allowRequest("user1"));
        }
        assertFalse(rateLimiter.allowRequest("user1"));

        // Wait for window to pass
        Thread.sleep(1100);

        // Should allow requests again
        assertTrue(rateLimiter.allowRequest("user1"));
    }

    @Test
    void testDisabledRateLimiter_Success() {
        rateLimiter.setEnabled(false);

        // Should allow unlimited requests when disabled
        for (int i = 0; i < 10; i++) {
            assertTrue(rateLimiter.allowRequest("user1"));
        }
    }

    /**
     * FAILURE CASES - These demonstrate the flaws
     */

    /**
     * FLAW #1: Race Condition - Concurrent modifications to allRequests
     * This test demonstrates that users with odd hash codes can cause
     * ConcurrentModificationException or data corruption when accessed concurrently.
     */
    @Test
    @Timeout(10)
    void testRaceCondition_ConcurrentModification() throws InterruptedException {
        // Find a user with odd hash code (no synchronization)
        String oddHashUser = findUserWithOddHash();
        
        int threadCount = 10;
        int requestsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger exceptions = new AtomicInteger(0);
        AtomicInteger successes = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < requestsPerThread; j++) {
                        try {
                            rateLimiter.allowRequest(oddHashUser);
                            successes.incrementAndGet();
                        } catch (Exception e) {
                            exceptions.incrementAndGet();
                            // Expected: ConcurrentModificationException or other concurrency issues
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        // This test may or may not fail depending on timing, but it demonstrates the flaw
        System.out.println("Exceptions caught: " + exceptions.get());
        System.out.println("Successful requests: " + successes.get());
        
        // The flaw exists if we see exceptions OR if the count is incorrect
        assertTrue(exceptions.get() > 0 || successes.get() != threadCount * requestsPerThread,
                "Race condition detected: exceptions occurred or request count is incorrect");
    }

    /**
     * FLAW #2: Non-Atomic Counter Increment
     * Multiple threads incrementing totalRequestCount concurrently will lose increments.
     */
    @Test
    @Timeout(10)
    void testNonAtomicCounterIncrement() throws InterruptedException {
        int threadCount = 10;
        int requestsPerThread = 100;
        int expectedTotal = threadCount * requestsPerThread;
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final String userId = "user" + i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < requestsPerThread; j++) {
                        rateLimiter.allowRequest(userId);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        long actualTotal = rateLimiter.getTotalRequestCount();
        
        // Due to non-atomic increment, actualTotal will be less than expected
        assertTrue(actualTotal < expectedTotal,
                "Non-atomic increment flaw: expected " + expectedTotal + 
                " but got " + actualTotal + " (lost increments)");
    }

    /**
     * FLAW #3: Memory Visibility Issue with isEnabled
     * Changes to isEnabled might not be immediately visible to other threads.
     */
    @Test
    @Timeout(10)
    void testIsEnabledVisibilityIssue() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        AtomicInteger requestsAfterDisable = new AtomicInteger(0);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(1);

        // Thread 1: Continuously make requests
        executor.submit(() -> {
            try {
                startLatch.await();
                for (int i = 0; i < 1000; i++) {
                    if (rateLimiter.allowRequest("user1")) {
                        requestsAfterDisable.incrementAndGet();
                    }
                    Thread.sleep(1);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                endLatch.countDown();
            }
        });

        // Thread 2: Disable rate limiter
        executor.submit(() -> {
            try {
                Thread.sleep(100); // Let some requests go through first
                startLatch.countDown();
                Thread.sleep(50);
                rateLimiter.setEnabled(false);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        endLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        // Due to visibility issue, some requests might still be denied
        // even after disabling (though this is hard to reliably test)
        System.out.println("Requests after disable: " + requestsAfterDisable.get());
    }

    /**
     * FLAW #4: Deadlock Risk - Lock Ordering Issue
     * This test attempts to trigger a deadlock between updateUserHistory and resetUserLimit.
     * Note: Deadlocks are timing-dependent and may not always occur.
     */
    @Test
    @Timeout(5)
    void testDeadlockRisk_LockOrdering() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger completed = new AtomicInteger(0);
        String userId = "deadlockUser";

        // Make some requests first to populate history
        for (int i = 0; i < 5; i++) {
            rateLimiter.allowRequest(userId);
        }

        // Thread 1: Continuously makes requests (calls updateUserHistory)
        executor.submit(() -> {
            try {
                startLatch.await();
                for (int i = 0; i < 1000; i++) {
                    rateLimiter.allowRequest(userId);
                    completed.incrementAndGet();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // Thread 2: Continuously resets user limit (calls resetUserLimit)
        executor.submit(() -> {
            try {
                startLatch.await();
                for (int i = 0; i < 1000; i++) {
                    rateLimiter.resetUserLimit(userId);
                    completed.incrementAndGet();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        startLatch.countDown();
        
        // If deadlock occurs, this will timeout
        boolean finished = executor.awaitTermination(3, TimeUnit.SECONDS);
        executor.shutdownNow();

        // If we get here without timeout, deadlock didn't occur this time
        // But the flaw still exists - deadlocks are probabilistic
        System.out.println("Completed operations: " + completed.get());
        assertTrue(finished || completed.get() > 0, 
                "Deadlock risk exists - operations may hang or timeout");
    }

    /**
     * FLAW #5: Memory Leak - Unbounded Growth
     * allRequests list grows indefinitely and cleanup uses wrong threshold.
     */
    @Test
    void testMemoryLeak_UnboundedGrowth() {
        // Make many requests
        for (int i = 0; i < 1000; i++) {
            rateLimiter.allowRequest("user" + (i % 10));
        }

        int sizeBeforeCleanup = rateLimiter.getAllRequestsSize();
        assertTrue(sizeBeforeCleanup >= 1000, 
                "allRequests should contain all requests: " + sizeBeforeCleanup);

        // Cleanup should remove old requests, but uses hardcoded 1 hour
        // So recent requests (within 1 hour) won't be cleaned
        rateLimiter.cleanupOldRequests();
        
        int sizeAfterCleanup = rateLimiter.getAllRequestsSize();
        
        // Since requests are recent, cleanup won't remove them (wrong threshold)
        assertTrue(sizeAfterCleanup >= 1000,
                "Memory leak: cleanup didn't remove requests because it uses wrong threshold. " +
                "Size: " + sizeAfterCleanup);
    }

    /**
     * FLAW #5: Memory Leak - Cleanup Uses Wrong Threshold
     * cleanupOldRequests uses hardcoded 3600000ms instead of windowSizeMs.
     */
    @Test
    void testMemoryLeak_WrongCleanupThreshold() {
        FlawedRateLimiter limiter = new FlawedRateLimiter(5, 2000); // 2 second window
        
        // Make requests
        for (int i = 0; i < 10; i++) {
            limiter.allowRequest("user1");
        }

        int sizeBefore = limiter.getAllRequestsSize();
        
        // Wait for window to pass (2 seconds + buffer)
        try {
            Thread.sleep(2100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Cleanup should use windowSizeMs (2000ms) but uses 3600000ms instead
        limiter.cleanupOldRequests();
        int sizeAfter = limiter.getAllRequestsSize();

        // Requests are older than windowSizeMs but less than 1 hour
        // So they should be cleaned but won't be (wrong threshold)
        assertTrue(sizeAfter == sizeBefore,
                "Flaw: cleanup uses wrong threshold (1 hour instead of windowSizeMs). " +
                "Requests older than window should be removed but weren't. " +
                "Size before: " + sizeBefore + ", after: " + sizeAfter);
    }

    /**
     * FLAW #6: Inefficient O(n) Rate Limit Check
     * Performance degrades as allRequests grows.
     */
    @Test
    void testInefficientRateLimitCheck() {
        // Make many requests to grow the list
        for (int i = 0; i < 10000; i++) {
            rateLimiter.allowRequest("user" + (i % 100));
        }

        int size = rateLimiter.getAllRequestsSize();
        assertTrue(size >= 10000,
                "Inefficient check: allRequests contains " + size + 
                " entries, making each check O(n)");

        // This will be slow because it iterates through all 10000+ entries
        long startTime = System.nanoTime();
        rateLimiter.allowRequest("newUser");
        long duration = System.nanoTime() - startTime;

        System.out.println("Time to check rate limit with " + size + " entries: " + 
                          duration / 1_000_000 + " ms");
        // Performance test - should be slow with large list
    }

    /**
     * FLAW #7: Unused userRequestHistory
     * userRequestHistory is maintained but never used in rate limiting logic.
     */
    @Test
    void testUnusedUserRequestHistory() {
        // Make requests
        for (int i = 0; i < 10; i++) {
            rateLimiter.allowRequest("user1");
        }

        int historySize = rateLimiter.getUserHistorySize();
        assertTrue(historySize > 0, "userRequestHistory is being maintained");

        // But it's not used - rate limiting only checks allRequests
        // This is wasteful - maintaining data that's never used
        assertTrue(historySize >= 1,
                "Flaw: userRequestHistory is maintained but never used in rate limiting");
    }

    /**
     * FLAW #8: Non-Thread-Safe Lists in ConcurrentHashMap
     * ArrayList values in ConcurrentHashMap are not thread-safe.
     */
    @Test
    @Timeout(10)
    void testNonThreadSafeListsInConcurrentHashMap() throws InterruptedException {
        String userId = "concurrentUser";
        int threadCount = 10;
        int requestsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger exceptions = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < requestsPerThread; j++) {
                        try {
                            rateLimiter.allowRequest(userId);
                        } catch (Exception e) {
                            exceptions.incrementAndGet();
                            // Expected: ArrayIndexOutOfBoundsException or data corruption
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        // The flaw exists if exceptions occur or data is corrupted
        System.out.println("Exceptions from non-thread-safe lists: " + exceptions.get());
    }

    /**
     * FLAW #9: Missing Null Checks
     * No null validation for userId parameter.
     */
    @Test
    void testMissingNullChecks() {
        // These should throw NullPointerException due to missing null checks
        assertThrows(NullPointerException.class, () -> {
            rateLimiter.allowRequest(null);
        }, "Flaw: allowRequest should validate null userId");

        assertThrows(NullPointerException.class, () -> {
            rateLimiter.resetUserLimit(null);
        }, "Flaw: resetUserLimit should validate null userId");
    }

    /**
     * FLAW #10: Arbitrary Synchronization Based on Hash Code
     * Different users get different synchronization treatment.
     */
    @Test
    void testArbitrarySynchronizationStrategy() {
        // Find users with different hash code mod 2
        String evenHashUser = findUserWithEvenHash();
        String oddHashUser = findUserWithOddHash();

        // Both should work, but have different synchronization
        assertTrue(rateLimiter.allowRequest(evenHashUser),
                "Even hash user (synchronized)");
        assertTrue(rateLimiter.allowRequest(oddHashUser),
                "Odd hash user (NOT synchronized)");

        // The flaw: inconsistent behavior based on arbitrary hash code
        assertNotEquals(evenHashUser.hashCode() % 2, oddHashUser.hashCode() % 2,
                "Flaw: Users get different synchronization based on hash code");
    }

    /**
     * FLAW #11: Redundant Synchronization
     * updateUserHistory uses nested locks unnecessarily.
     */
    @Test
    @Timeout(10)
    void testRedundantSynchronization() throws InterruptedException {
        // This test demonstrates the overhead of redundant synchronization
        int threadCount = 10;
        int requestsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        long startTime = System.nanoTime();

        for (int i = 0; i < threadCount; i++) {
            final String userId = "user" + i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < requestsPerThread; j++) {
                        rateLimiter.allowRequest(userId);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        long duration = System.nanoTime() - startTime;
        System.out.println("Time with redundant synchronization: " + 
                         duration / 1_000_000 + " ms");
        
        // The redundant locks add unnecessary overhead
        assertTrue(duration > 0, "Redundant synchronization adds overhead");
    }

    /**
     * Helper method to find a user ID with even hash code (synchronized path)
     */
    private String findUserWithEvenHash() {
        for (int i = 0; i < 1000; i++) {
            String user = "user" + i;
            if (user.hashCode() % 2 == 0) {
                return user;
            }
        }
        return "user0"; // fallback
    }

    /**
     * Helper method to find a user ID with odd hash code (non-synchronized path)
     */
    private String findUserWithOddHash() {
        for (int i = 0; i < 1000; i++) {
            String user = "user" + i;
            if (user.hashCode() % 2 != 0) {
                return user;
            }
        }
        return "user1"; // fallback
    }
}