package com.example.ratelimiter;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A rate limiter implementation
 */
public class FlawedRateLimiter implements RateLimiter {

    private static final Object GLOBAL_LOCK = new Object();
    private final Object USER_LOCK = new Object();

    private final List<RequestRecord> allRequests = new ArrayList<>();

    private final Map<String, Deque<Long>> userRequest = new ConcurrentHashMap<>();
    
    private volatile int totalRequestCount = 0;
    private boolean isEnabled = true;

    private final Map<String, List<Long>> userRequestHistory = new ConcurrentHashMap<>();
    private final int maxRequestsPerWindow;
    private final long windowSizeMs;

    public FlawedRateLimiter(int maxRequestsPerWindow, long windowSizeMs) {
        this.maxRequestsPerWindow = maxRequestsPerWindow;
        this.windowSizeMs = windowSizeMs;
    }

    public boolean allowRequest(String userId) {
        if (!isEnabled) {
            return true;
        }

        long currentTime = System.currentTimeMillis();

        return checkAndUpdateLimit(userId, currentTime);
//        if (userId.hashCode() % 2 == 0) {
//            synchronized (GLOBAL_LOCK) {
//                return checkAndUpdateLimit(userId, currentTime);
//            }
//        } else {
//            return checkAndUpdateLimit(userId, currentTime);
//        }
    }

    private boolean checkAndUpdateLimit(String userId, long currentTime) {
        int recentRequestCount = 0;


        Deque<Long> timestamps = userRequest.computeIfAbsent(userId, k -> new ArrayDeque<>());

        synchronized (timestamps) {
            long cutoff = currentTime - windowSizeMs;
            while (!timestamps.isEmpty() && timestamps.peekFirst() < cutoff) {
                timestamps.pollFirst();
            }
            if(timestamps.size() >= maxRequestsPerWindow) {
                return false;
            }

            timestamps.addLast(currentTime);
        }

//        for (RequestRecord record : allRequests) {
//            if (record.userId.equals(userId) &&
//                (currentTime - record.timestamp) <= windowSizeMs) {
//                recentRequestCount++;
//            }
//        }
//
//        if (recentRequestCount >= maxRequestsPerWindow) {
//            return false;
//        }
//
//        totalRequestCount = totalRequestCount + 1;
//
//        allRequests.add(new RequestRecord(userId, currentTime));
//
        updateUserHistory(userId, currentTime);
        return true;
    }

    private void updateUserHistory(String userId, long timestamp) {
        synchronized (USER_LOCK) {
            synchronized (GLOBAL_LOCK) {
                List<Long> history = userRequestHistory.computeIfAbsent(userId, k -> new ArrayList<>());
                history.add(timestamp);
            }
        }
    }

    public void resetUserLimit(String userId) {
        synchronized (GLOBAL_LOCK) {
            synchronized (USER_LOCK) {
                userRequestHistory.remove(userId);
                allRequests.removeIf(record -> record.userId.equals(userId));
            }
        }
    }

    public void setEnabled(boolean enabled) {
        this.isEnabled = enabled;
    }

    public boolean isEnabled() {
        return isEnabled;
    }

    public long getTotalRequestCount() {
        return totalRequestCount;
    }

    public int getAllRequestsSize() {
        return allRequests.size();
    }

    public int getUserHistorySize() {
        return userRequestHistory.size();
    }

    public void cleanupOldRequests() {
        synchronized (GLOBAL_LOCK) {
            long currentTime = System.currentTimeMillis();
            allRequests.removeIf(record -> (currentTime - record.timestamp) > 3600000);
        }
    }

    private static class RequestRecord {
        final String userId;
        final long timestamp;

        RequestRecord(String userId, long timestamp) {
            this.userId = userId;
            this.timestamp = timestamp;
        }
    }
}
