package LLD.rateLimiter.solution.strategy;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class FixedWindowStrategy implements RateLimitingStrategy {
    private final int maxRequests;
    private final long windowSizeInMillis;
    private final Map<String, UserRequestInfo> userRequestMap = new ConcurrentHashMap<>();

    public FixedWindowStrategy(int maxRequests, long windowSizeInSeconds) {
        this.maxRequests = maxRequests;
        this.windowSizeInMillis = windowSizeInSeconds * 1000;
    }

    @Override
    public boolean allowRequest(String userId) {
        long currentTime = System.currentTimeMillis();
        userRequestMap.putIfAbsent(userId, new UserRequestInfo(currentTime));

        UserRequestInfo requestInfo = userRequestMap.get(userId);

        synchronized (requestInfo) {
            if (currentTime - requestInfo.windowStart >= windowSizeInMillis) {
                requestInfo.reset(currentTime);
            }

            if (requestInfo.requestCount.get() < maxRequests) {
                requestInfo.requestCount.incrementAndGet();
                return true;
            } else {
                return false;
            }
        }
    }

    private static class UserRequestInfo {
        long windowStart;
        AtomicInteger requestCount;

        UserRequestInfo(long startTime) {
            this.windowStart = startTime;
            this.requestCount = new AtomicInteger(0);
        }

        void reset(long newStart) {
            this.windowStart = newStart;
            this.requestCount.set(0);
        }
    }
}
