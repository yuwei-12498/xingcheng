package com.citytrip.service.impl;

import com.citytrip.model.vo.CommunityItineraryPageVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.lang.Nullable;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.function.Supplier;

@Slf4j
@Service
public class CommunityItineraryCacheService {

    private static final String CACHE_KEY_PREFIX = "community:itineraries:";
    private static final String CACHE_VERSION_KEY = CACHE_KEY_PREFIX + "version";
    private static final Object CACHE_INVALIDATION_RESOURCE = new Object();
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);

    private final boolean redisEnabled;
    private final RedisTemplate<String, Object> redisTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public CommunityItineraryCacheService(@Value("${app.redis.enabled:false}") boolean redisEnabled,
                                          @Nullable RedisTemplate<String, Object> redisTemplate,
                                          @Nullable StringRedisTemplate stringRedisTemplate,
                                          ObjectMapper objectMapper) {
        this.redisEnabled = redisEnabled;
        this.redisTemplate = redisTemplate;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    public CommunityItineraryPageVO getCommunityPage(int page, int size, Supplier<CommunityItineraryPageVO> loader) {
        return getCommunityPage(page, size, null, null, null, loader);
    }

    public CommunityItineraryPageVO getCommunityPage(int page,
                                                     int size,
                                                     String sort,
                                                     String keyword,
                                                     String theme,
                                                     Supplier<CommunityItineraryPageVO> loader) {
        if (!isRedisAvailable()) {
            return loadWithoutCache(page, size, loader);
        }

        String cacheKey = buildCacheKey(page, size, sort, keyword, theme);
        try {
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                return objectMapper.convertValue(cached, new TypeReference<>() {
                });
            }
        } catch (DataAccessException ex) {
            log.warn("Redis unavailable, fallback to MySQL for key={}", cacheKey);
            return loadWithoutCache(page, size, loader);
        } catch (Exception ex) {
            log.warn("Failed to read community cache, fallback to MySQL for key={}", cacheKey, ex);
            return loadWithoutCache(page, size, loader);
        }

        CommunityItineraryPageVO pageData = loadWithoutCache(page, size, loader);

        try {
            redisTemplate.opsForValue().set(cacheKey, pageData, CACHE_TTL);
        } catch (DataAccessException ex) {
            log.warn("Redis unavailable, skip caching for key={}", cacheKey);
        } catch (Exception ex) {
            log.warn("Failed to write community cache for key={}", cacheKey, ex);
        }
        return pageData;
    }

    public void markCommunityPageCacheDirty() {
        if (!isRedisAvailable()) {
            return;
        }

        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            if (TransactionSynchronizationManager.hasResource(CACHE_INVALIDATION_RESOURCE)) {
                return;
            }

            TransactionSynchronizationManager.bindResource(CACHE_INVALIDATION_RESOURCE, Boolean.TRUE);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    bumpCacheVersion();
                }

                @Override
                public void afterCompletion(int status) {
                    if (TransactionSynchronizationManager.hasResource(CACHE_INVALIDATION_RESOURCE)) {
                        TransactionSynchronizationManager.unbindResource(CACHE_INVALIDATION_RESOURCE);
                    }
                }
            });
            return;
        }

        bumpCacheVersion();
    }

    private boolean isRedisAvailable() {
        return redisEnabled && redisTemplate != null && stringRedisTemplate != null;
    }

    private CommunityItineraryPageVO loadWithoutCache(int page, int size, Supplier<CommunityItineraryPageVO> loader) {
        CommunityItineraryPageVO pageData = loader.get();
        if (pageData == null) {
            return emptyPage(page, size);
        }
        return pageData;
    }

    private CommunityItineraryPageVO emptyPage(int page, int size) {
        CommunityItineraryPageVO result = new CommunityItineraryPageVO();
        result.setPage(page);
        result.setSize(size);
        result.setTotal(0L);
        return result;
    }

    private String buildCacheKey(int page, int size, String sort, String keyword, String theme) {
        return CACHE_KEY_PREFIX
                + resolveCacheVersion()
                + ":" + page
                + ":" + size
                + ":" + normalizeKeyPart(sort)
                + ":" + normalizeKeyPart(keyword)
                + ":" + normalizeKeyPart(theme);
    }

    private String normalizeKeyPart(String value) {
        if (!StringUtils.hasText(value)) {
            return "-";
        }
        return value.trim()
                .toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9\\u4e00-\\u9fa5_-]+", "_");
    }

    private String resolveCacheVersion() {
        try {
            String version = stringRedisTemplate.opsForValue().get(CACHE_VERSION_KEY);
            return StringUtils.hasText(version) ? version.trim() : "0";
        } catch (DataAccessException ex) {
            log.warn("Redis unavailable, fallback to default cache version");
            return "0";
        } catch (Exception ex) {
            log.warn("Failed to resolve community cache version, fallback to default version", ex);
            return "0";
        }
    }

    private void bumpCacheVersion() {
        try {
            stringRedisTemplate.opsForValue().increment(CACHE_VERSION_KEY);
        } catch (DataAccessException ex) {
            log.warn("Redis unavailable, skip community cache version bump");
        } catch (Exception ex) {
            log.warn("Failed to bump community cache version", ex);
        }
    }
}
