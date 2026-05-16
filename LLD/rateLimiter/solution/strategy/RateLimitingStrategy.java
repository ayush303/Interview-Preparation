package LLD.rateLimiter.solution.strategy;

public interface RateLimitingStrategy {
    boolean allowRequest(String userId);
}
