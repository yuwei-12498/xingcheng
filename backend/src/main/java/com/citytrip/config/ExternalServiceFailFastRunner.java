package com.citytrip.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Component
@ConditionalOnProperty(name = "app.external-services.require-enabled", havingValue = "true")
public class ExternalServiceFailFastRunner implements ApplicationRunner {

    private final boolean redisEnabled;
    private final GeoSearchProperties geoSearchProperties;
    private final AmapProperties amapProperties;
    private final LlmProperties llmProperties;
    private final StringRedisTemplate stringRedisTemplate;
    private final String mailPassword;
    private final String authMailFrom;

    public ExternalServiceFailFastRunner(@Value("${app.redis.enabled:false}") boolean redisEnabled,
                                         GeoSearchProperties geoSearchProperties,
                                         AmapProperties amapProperties,
                                         LlmProperties llmProperties,
                                         @Nullable StringRedisTemplate stringRedisTemplate,
                                         @Value("${spring.mail.password:}") String mailPassword,
                                         @Value("${app.auth.mail.from:}") String authMailFrom) {
        this.redisEnabled = redisEnabled;
        this.geoSearchProperties = geoSearchProperties;
        this.amapProperties = amapProperties;
        this.llmProperties = llmProperties;
        this.stringRedisTemplate = stringRedisTemplate;
        this.mailPassword = mailPassword;
        this.authMailFrom = authMailFrom;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> issues = new ArrayList<>();
        verifyRedis(issues);
        verifyGeo(issues);
        verifyAmap(issues);
        verifyLlm(issues);
        verifyMail(issues);
        if (!issues.isEmpty()) {
            throw new IllegalStateException("Required external services are not ready: " + String.join("; ", issues));
        }
    }

    private void verifyRedis(List<String> issues) {
        if (!redisEnabled) {
            issues.add("app.redis.enabled=false");
            return;
        }
        if (stringRedisTemplate == null || stringRedisTemplate.getConnectionFactory() == null) {
            issues.add("RedisTemplate is unavailable");
            return;
        }
        try (RedisConnection connection = stringRedisTemplate.getConnectionFactory().getConnection()) {
            String pong = connection.ping();
            if (!"PONG".equalsIgnoreCase(pong)) {
                issues.add("Redis ping returned " + pong);
            }
        } catch (Exception ex) {
            issues.add("Redis ping failed: " + ex.getMessage());
        }
    }

    private void verifyGeo(List<String> issues) {
        if (geoSearchProperties == null || !geoSearchProperties.isEnabled()) {
            issues.add("app.geo.enabled=false");
            return;
        }
        if (!StringUtils.hasText(geoSearchProperties.getBaseUrl())) {
            issues.add("app.geo.base-url is empty");
        }
        if (!StringUtils.hasText(geoSearchProperties.getApiKey())) {
            issues.add("app.geo.api-key is empty");
        }
    }

    private void verifyAmap(List<String> issues) {
        if (amapProperties == null || !amapProperties.isEnabled()) {
            issues.add("app.amap.enabled=false");
            return;
        }
        if (!StringUtils.hasText(amapProperties.getBaseUrl())) {
            issues.add("app.amap.base-url is empty");
        }
        if (!StringUtils.hasText(amapProperties.getApiKey())) {
            issues.add("app.amap.api-key is empty");
        }
    }

    private void verifyLlm(List<String> issues) {
        if (llmProperties == null || !llmProperties.canTryRealText()) {
            List<String> llmIssues = llmProperties == null
                    ? List.of("llm config is missing")
                    : llmProperties.getRealTextConfigIssues();
            issues.add("LLM real text config is unavailable: " + String.join(",", llmIssues));
        }
        if (llmProperties != null && llmProperties.isFallbackToMock()) {
            issues.add("llm.fallback-to-mock=true");
        }
    }

    private void verifyMail(List<String> issues) {
        if (!StringUtils.hasText(mailPassword)) {
            issues.add("RESEND_API_KEY/spring.mail.password is empty");
        }
        if (!StringUtils.hasText(authMailFrom)) {
            issues.add("app.auth.mail.from is empty");
        }
    }
}
