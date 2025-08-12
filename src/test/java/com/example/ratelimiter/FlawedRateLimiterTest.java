package com.example.ratelimiter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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

}