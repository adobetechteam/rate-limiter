package com.example.ratelimiter;

/**
 * Interface defining the contract for rate limiting implementations.
 * 
 * A rate limiter controls the number of requests that can be made by users
 * within a specified time window.
 */
public interface RateLimiter {
    
    /**
     * Check if a request should be allowed for the given user.
     * 
     * @param userId the identifier of the user making the request
     * @return true if the request is allowed, false if it should be rejected
     * @throws IllegalArgumentException if userId is null
     */
    boolean allowRequest(String userId);
    
    /**
     * Enable or disable the rate limiter.
     * When disabled, all requests should be allowed.
     * 
     * @param enabled true to enable rate limiting, false to disable
     */
    void setEnabled(boolean enabled);
    
    /**
     * Check if the rate limiter is currently enabled.
     * 
     * @return true if rate limiting is enabled, false if disabled
     */
    boolean isEnabled();
    
    /**
     * Get the total number of requests that have been allowed
     * since the rate limiter was created.
     * 
     * @return the total count of allowed requests
     */
    long getTotalRequestCount();
    
    /**
     * Reset the rate limit for a specific user, allowing them
     * to make requests as if they haven't made any recently.
     * 
     * @param userId the identifier of the user whose limit should be reset
     */
    void resetUserLimit(String userId);
    
    /**
     * Get the current size of internal request storage.
     * This can be used to monitor memory usage and detect potential leaks.
     * 
     * @return the number of requests currently being tracked
     */
    int getAllRequestsSize();
    
    /**
     * Perform cleanup of old request data to free memory.
     * This should remove expired entries and optimize internal storage.
     */
    void cleanupOldRequests();
} 