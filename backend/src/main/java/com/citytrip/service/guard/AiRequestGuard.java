package com.citytrip.service.guard;

import com.citytrip.common.SystemBusyException;
import com.citytrip.config.AiRequestGuardProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

@Component
public class AiRequestGuard {

    private final AiRequestGuardProperties properties;
    private final Clock clock;
    private final Map<String, Semaphore> semaphores = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> lastAcceptedAt = new ConcurrentHashMap<>();

    @Autowired
    public AiRequestGuard(AiRequestGuardProperties properties) {
        this(properties, Clock.systemUTC());
    }

    AiRequestGuard(AiRequestGuardProperties properties, Clock clock) {
        this.properties = properties == null ? new AiRequestGuardProperties() : properties;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    public <T> T call(String sceneName, String subject, Supplier<T> supplier) {
        try (GuardPermit ignored = acquire(sceneName, subject)) {
            return supplier.get();
        }
    }

    public GuardPermit acquire(String sceneName, String subject) {
        AiRequestGuardProperties.SceneGuardProperties scene = properties.resolve(sceneName);
        String normalizedScene = normalizeScene(sceneName);
        if (!properties.isEnabled() || !scene.isEnabled()) {
            return GuardPermit.noop();
        }

        String normalizedSubject = normalizeSubject(subject);
        enforceCooldown(scene, normalizedScene, normalizedSubject);

        Semaphore semaphore = semaphores.computeIfAbsent(normalizedScene,
                ignored -> new Semaphore(scene.safeMaxConcurrent()));
        boolean acquired = tryAcquire(semaphore, scene.safeAcquireTimeoutMs(), normalizedScene);
        if (!acquired) {
            throw busy(normalizedScene);
        }

        try {
            markAccepted(scene, normalizedScene, normalizedSubject);
            return new GuardPermit(semaphore);
        } catch (RuntimeException ex) {
            semaphore.release();
            throw ex;
        }
    }

    private void enforceCooldown(AiRequestGuardProperties.SceneGuardProperties scene,
                                 String normalizedScene,
                                 String normalizedSubject) {
        long cooldownMs = scene.safeCooldownMs();
        if (cooldownMs <= 0) {
            return;
        }
        long now = clock.millis();
        AtomicLong last = lastAcceptedAt.computeIfAbsent(cooldownKey(normalizedScene, normalizedSubject), ignored -> new AtomicLong(0));
        long previous = last.get();
        if (previous > 0 && now - previous < cooldownMs) {
            throw new SystemBusyException("AI request too frequent for " + normalizedScene + ", please retry later");
        }
    }

    private void markAccepted(AiRequestGuardProperties.SceneGuardProperties scene,
                              String normalizedScene,
                              String normalizedSubject) {
        long cooldownMs = scene.safeCooldownMs();
        if (cooldownMs <= 0) {
            return;
        }
        long now = clock.millis();
        AtomicLong last = lastAcceptedAt.computeIfAbsent(cooldownKey(normalizedScene, normalizedSubject), ignored -> new AtomicLong(0));
        long previous = last.get();
        if (previous > 0 && now - previous < cooldownMs) {
            throw new SystemBusyException("AI request too frequent for " + normalizedScene + ", please retry later");
        }
        last.set(now);
    }

    private boolean tryAcquire(Semaphore semaphore, long acquireTimeoutMs, String normalizedScene) {
        try {
            if (acquireTimeoutMs <= 0) {
                return semaphore.tryAcquire();
            }
            return semaphore.tryAcquire(acquireTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new SystemBusyException("AI request guard interrupted for " + normalizedScene);
        }
    }

    private String normalizeScene(String sceneName) {
        return StringUtils.hasText(sceneName) ? sceneName.trim().toLowerCase(Locale.ROOT) : "chat";
    }

    private String normalizeSubject(String subject) {
        return StringUtils.hasText(subject) ? subject.trim() : "anonymous";
    }

    private String cooldownKey(String normalizedScene, String normalizedSubject) {
        return normalizedScene + ":" + normalizedSubject;
    }

    private SystemBusyException busy(String normalizedScene) {
        return new SystemBusyException("AI request capacity is full for " + normalizedScene + ", please retry later");
    }

    public static class GuardPermit implements AutoCloseable {
        private static final GuardPermit NOOP = new GuardPermit(null);
        private final Semaphore semaphore;
        private boolean closed;

        private GuardPermit(Semaphore semaphore) {
            this.semaphore = semaphore;
        }

        static GuardPermit noop() {
            return NOOP;
        }

        @Override
        public void close() {
            if (!closed && semaphore != null) {
                semaphore.release();
            }
            closed = true;
        }
    }
}
