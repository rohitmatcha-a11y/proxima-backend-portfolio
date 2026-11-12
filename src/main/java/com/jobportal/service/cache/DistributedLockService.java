package com.jobportal.service.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;


@Slf4j
@Service
@RequiredArgsConstructor
public class DistributedLockService {

    private final RedissonClient redissonClient;
    private final CacheManager cacheManager;

    @Value("${app.lock.wait-ms:500}")
    private long waitTimeMs;

    @Value("${app.lock.lease-ms:10000}")
    private long leaseTimeMs;



    @SuppressWarnings("unchecked")
    public <T> T getCachedOrLoad(String cacheName, String cacheKey, Supplier<T> loader) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            log.warn("Cache '{}' not found — loading directly", cacheName);
            return loader.get();
        }

        // Fast path — cache hit, no lock needed
        Cache.ValueWrapper cached = cache.get(cacheKey);
        if (cached != null) {
            return (T) cached.get();
        }

        // Cache miss — acquire lock to prevent stampede
        RLock lock = redissonClient.getLock("dlock:cache:" + cacheName + ":" + cacheKey);
        try {
            lock.lock(leaseTimeMs, TimeUnit.MILLISECONDS);

            // Double-check after acquiring lock — another thread may have populated it
            cached = cache.get(cacheKey);
            if (cached != null) {
                log.debug("Cache populated by another thread in cache='{}'", cacheName);
                return (T) cached.get();
            }

            // Still a miss — load and populate cache
            T value = loader.get();
            if (value != null) {
                cache.put(cacheKey, value);
            }
            return value;

        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    public RLock tryAcquireScraperLock(String lockKey) {
        RLock lock = redissonClient.getLock("dlock:scraper:" + lockKey);
        try {
            boolean acquired = lock.tryLock(0, TimeUnit.MILLISECONDS);
            if (acquired) {
                log.debug("Scraper lock acquired");
                return lock;
            } else {
                log.warn("Scraper lock not acquired — scraper is already running");
                return null;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    public void releaseScraperLock(RLock lock) {
        if (lock != null && lock.isHeldByCurrentThread()) {
            lock.unlock();
            log.debug("Scraper lock released");
        }
    }

    /**
     * Administrative escape hatch for a scraper that has been explicitly stopped.
     * Redisson locks are thread-owned, so a different HTTP request cannot use the
     * normal unlock path after interrupting the original worker thread.
     */
    public boolean forceReleaseScraperLock(String lockKey) {
        RLock lock = redissonClient.getLock("dlock:scraper:" + lockKey);
        if (!lock.isLocked()) return false;
        boolean released = lock.forceUnlock();
        if (released) log.info("Force-released scraper lock");
        return released;
    }


}
