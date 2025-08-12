package com.example.ratelimiter;

/**
 * Example usage of the FlawedRateLimiter to demonstrate its behavior
 */
public class ExampleUsage {
    
    public static void main(String[] args) throws InterruptedException {
        // Create a rate limiter: 3 requests per 2 seconds
        FlawedRateLimiter rateLimiter = new FlawedRateLimiter(3, 2000);
        
        System.out.println("=== FlawedRateLimiter Example Usage ===");
        System.out.println("Rate limit: 3 requests per 2 seconds");
        System.out.println();
        
        // Test basic rate limiting
        System.out.println("--- Basic Rate Limiting ---");
        for (int i = 1; i <= 5; i++) {
            boolean allowed = rateLimiter.allowRequest("user1");
            System.out.println("Request " + i + " for user1: " + (allowed ? "ALLOWED" : "DENIED"));
        }
        
        System.out.println("\nWaiting 2.5 seconds for window reset...");
        Thread.sleep(2500);
        
        // After window reset
        System.out.println("\n--- After Window Reset ---");
        boolean allowed = rateLimiter.allowRequest("user1");
        System.out.println("Request after reset for user1: " + (allowed ? "ALLOWED" : "DENIED"));
        
        // Test multiple users
        System.out.println("\n--- Multiple Users ---");
        for (int i = 1; i <= 3; i++) {
            boolean allowedUser2 = rateLimiter.allowRequest("user2");
            boolean allowedUser3 = rateLimiter.allowRequest("user3");
            System.out.println("Request " + i + " - user2: " + (allowedUser2 ? "ALLOWED" : "DENIED") + 
                             ", user3: " + (allowedUser3 ? "ALLOWED" : "DENIED"));
        }
        
        // Test disabling
        System.out.println("\n--- Disabled Rate Limiter ---");
        rateLimiter.setEnabled(false);
        for (int i = 1; i <= 5; i++) {
            boolean allowedDisabled = rateLimiter.allowRequest("user1");
            System.out.println("Request " + i + " when disabled: " + (allowedDisabled ? "ALLOWED" : "DENIED"));
        }
        
        System.out.println("\n=== End of Example ===");
    }
} 