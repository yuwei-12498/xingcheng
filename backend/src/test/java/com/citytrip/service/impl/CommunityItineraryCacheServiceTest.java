package com.citytrip.service.impl;

import com.citytrip.model.vo.CommunityItineraryPageVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommunityItineraryCacheServiceTest {

    @Test
    void fallsBackToLoaderWhenRedisIsDisabled() {
        CommunityItineraryCacheService service = new CommunityItineraryCacheService(
                false,
                null,
                null,
                new ObjectMapper()
        );
        AtomicInteger loadCount = new AtomicInteger();

        CommunityItineraryPageVO result = service.getCommunityPage(1, 12, () -> {
            loadCount.incrementAndGet();
            CommunityItineraryPageVO page = new CommunityItineraryPageVO();
            page.setPage(1);
            page.setSize(12);
            page.setTotal(3L);
            return page;
        });

        assertThat(loadCount).hasValue(1);
        assertThat(result.getPage()).isEqualTo(1);
        assertThat(result.getSize()).isEqualTo(12);
        assertThat(result.getTotal()).isEqualTo(3L);
    }

    @Test
    void fallsBackToLoaderWhenRedisBeanIsMissing() {
        CommunityItineraryCacheService service = new CommunityItineraryCacheService(
                true,
                null,
                null,
                new ObjectMapper()
        );

        CommunityItineraryPageVO result = service.getCommunityPage(2, 8, () -> null);

        assertThat(result.getPage()).isEqualTo(2);
        assertThat(result.getSize()).isEqualTo(8);
        assertThat(result.getTotal()).isZero();
    }

    @Test
    void fallsBackToLoaderWhenRedisReadThrowsDataAccessException() {
        @SuppressWarnings("unchecked")
        ValueOperations<String, Object> redisOps = mock(ValueOperations.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> stringOps = mock(ValueOperations.class);
        @SuppressWarnings("unchecked")
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        StringRedisTemplate stringRedisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.opsForValue()).thenReturn(redisOps);
        when(stringRedisTemplate.opsForValue()).thenReturn(stringOps);
        when(stringOps.get("community:itineraries:version")).thenReturn("7");
        when(redisOps.get(eq("community:itineraries:7:3:10")))
                .thenThrow(new DataAccessResourceFailureException("redis down"));

        CommunityItineraryCacheService service = new CommunityItineraryCacheService(
                true,
                redisTemplate,
                stringRedisTemplate,
                new ObjectMapper()
        );
        AtomicInteger loadCount = new AtomicInteger();

        CommunityItineraryPageVO result = service.getCommunityPage(3, 10, () -> {
            loadCount.incrementAndGet();
            CommunityItineraryPageVO page = new CommunityItineraryPageVO();
            page.setPage(3);
            page.setSize(10);
            page.setTotal(6L);
            return page;
        });

        assertThat(loadCount).hasValue(1);
        assertThat(result.getPage()).isEqualTo(3);
        assertThat(result.getSize()).isEqualTo(10);
        assertThat(result.getTotal()).isEqualTo(6L);
    }

    @Test
    void bumpsVersionInsteadOfScanningKeysWhenCacheIsMarkedDirty() {
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> stringOps = mock(ValueOperations.class);
        TrackingRedisTemplate redisTemplate = new TrackingRedisTemplate();
        TrackingStringRedisTemplate stringRedisTemplate = new TrackingStringRedisTemplate(stringOps);

        CommunityItineraryCacheService service = new CommunityItineraryCacheService(
                true,
                redisTemplate,
                stringRedisTemplate,
                new ObjectMapper()
        );

        service.markCommunityPageCacheDirty();

        verify(stringOps).increment("community:itineraries:version");
        assertThat(redisTemplate.isKeysCalled()).isFalse();
    }

    private static final class TrackingRedisTemplate extends RedisTemplate<String, Object> {
        private boolean keysCalled;

        @Override
        public java.util.Set<String> keys(String pattern) {
            this.keysCalled = true;
            return java.util.Collections.emptySet();
        }

        private boolean isKeysCalled() {
            return keysCalled;
        }
    }

    private static final class TrackingStringRedisTemplate extends StringRedisTemplate {
        private final ValueOperations<String, String> valueOperations;

        private TrackingStringRedisTemplate(ValueOperations<String, String> valueOperations) {
            this.valueOperations = valueOperations;
        }

        @Override
        public ValueOperations<String, String> opsForValue() {
            return valueOperations;
        }
    }
}
