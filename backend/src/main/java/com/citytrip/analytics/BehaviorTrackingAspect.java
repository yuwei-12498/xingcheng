package com.citytrip.analytics;

import com.citytrip.analytics.command.UserBehaviorTrackCommand;
import com.citytrip.analytics.event.UserBehaviorTrackedEvent;
import com.citytrip.annotation.TrackBehavior;
import com.citytrip.common.AuthConstants;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Aspect
@Component
public class BehaviorTrackingAspect {

    private static final Logger log = LoggerFactory.getLogger(BehaviorTrackingAspect.class);
    private static final String ANALYTICS_REQUEST_ID = "ANALYTICS_REQUEST_ID";

    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final ExpressionParser expressionParser = new SpelExpressionParser();
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    public BehaviorTrackingAspect(ApplicationEventPublisher eventPublisher, ObjectMapper objectMapper) {
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

    @Around("@annotation(trackBehavior)")
    public Object around(ProceedingJoinPoint joinPoint, TrackBehavior trackBehavior) throws Throwable {
        long startMillis = System.currentTimeMillis();
        Object result = null;
        Throwable error = null;
        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable throwable) {
            error = throwable;
            throw throwable;
        } finally {
            try {
                UserBehaviorTrackCommand command = buildCommand(joinPoint, trackBehavior, result, error, startMillis);
                eventPublisher.publishEvent(new UserBehaviorTrackedEvent(command));
            } catch (Exception ex) {
                log.warn("行为埋点发布失败: eventType={}, reason={}", trackBehavior.eventType(), ex.getMessage());
            }
        }
    }

    private UserBehaviorTrackCommand buildCommand(ProceedingJoinPoint joinPoint,
                                                  TrackBehavior trackBehavior,
                                                  Object result,
                                                  Throwable error,
                                                  long startMillis) {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        Object[] args = joinPoint.getArgs();
        HttpServletRequest request = currentRequest();
        StandardEvaluationContext context = buildContext(joinPoint.getTarget(), method, args, result, error, request);

        UserBehaviorTrackCommand command = new UserBehaviorTrackCommand();
        command.setUserId(asLong(resolveUserId(request)));
        command.setSessionId(resolveSessionId(request));
        command.setRequestId(resolveRequestId(request));
        command.setEventType(trackBehavior.eventType());
        command.setEventSource(trackBehavior.eventSource());
        command.setItineraryId(asLong(evaluateExpression(trackBehavior.itineraryIdExpression(), context)));
        command.setPoiId(asLong(evaluateExpression(trackBehavior.poiIdExpression(), context)));
        command.setOptionKey(asString(evaluateExpression(trackBehavior.optionKeyExpression(), context)));
        command.setInteractionWeight(BigDecimal.valueOf(trackBehavior.weight()));
        command.setSuccessFlag(error == null);
        command.setCostMs((int) Math.max(0, System.currentTimeMillis() - startMillis));
        command.setRequestUri(request == null ? null : truncate(request.getRequestURI(), 255));
        command.setHttpMethod(request == null ? null : truncate(request.getMethod(), 16));
        command.setClientIp(request == null ? null : truncate(resolveClientIp(request), 64));
        command.setUserAgent(request == null ? null : truncate(request.getHeader("User-Agent"), 255));
        command.setReferer(request == null ? null : truncate(request.getHeader("Referer"), 255));
        command.setExtraJson(buildExtraJson(evaluateExpression(trackBehavior.extraExpression(), context), error));
        command.setEventTime(LocalDateTime.now());
        return command;
    }

    private StandardEvaluationContext buildContext(Object target,
                                                   Method method,
                                                   Object[] args,
                                                   Object result,
                                                   Throwable error,
                                                   HttpServletRequest request) {
        StandardEvaluationContext context = new StandardEvaluationContext();
        String[] parameterNames = parameterNameDiscoverer.getParameterNames(method);
        if (parameterNames != null) {
            for (int i = 0; i < parameterNames.length; i++) {
                context.setVariable(parameterNames[i], args[i]);
            }
        }
        for (int i = 0; i < args.length; i++) {
            context.setVariable("p" + i, args[i]);
            context.setVariable("a" + i, args[i]);
        }
        context.setVariable("target", target);
        context.setVariable("result", result);
        context.setVariable("request", request);
        context.setVariable("exception", error);
        context.setVariable("userId", resolveUserId(request));
        return context;
    }

    private Object evaluateExpression(String expression, StandardEvaluationContext context) {
        if (!StringUtils.hasText(expression)) {
            return null;
        }
        return expressionParser.parseExpression(expression).getValue(context);
    }

    private Object resolveUserId(HttpServletRequest request) {
        return request == null ? null : request.getAttribute(AuthConstants.LOGIN_USER_ID);
    }

    private HttpServletRequest currentRequest() {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (requestAttributes instanceof ServletRequestAttributes servletRequestAttributes) {
            return servletRequestAttributes.getRequest();
        }
        return null;
    }

    private String resolveRequestId(HttpServletRequest request) {
        if (request == null) {
            return UUID.randomUUID().toString();
        }
        Object existing = request.getAttribute(ANALYTICS_REQUEST_ID);
        if (existing instanceof String existingValue && StringUtils.hasText(existingValue)) {
            return existingValue;
        }
        String headerValue = request.getHeader("X-Request-Id");
        String requestId = StringUtils.hasText(headerValue) ? headerValue.trim() : UUID.randomUUID().toString();
        request.setAttribute(ANALYTICS_REQUEST_ID, requestId);
        return requestId;
    }

    private String resolveSessionId(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String headerSessionId = request.getHeader("X-Session-Id");
        if (StringUtils.hasText(headerSessionId)) {
            return truncate(headerSessionId.trim(), 64);
        }
        String requestedSessionId = request.getRequestedSessionId();
        if (StringUtils.hasText(requestedSessionId)) {
            return truncate(requestedSessionId.trim(), 64);
        }
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (cookie == null || !StringUtils.hasText(cookie.getName()) || !StringUtils.hasText(cookie.getValue())) {
                continue;
            }
            if ("citytrip_session".equalsIgnoreCase(cookie.getName()) || "JSESSIONID".equalsIgnoreCase(cookie.getName())) {
                return truncate(cookie.getValue().trim(), 64);
            }
        }
        return null;
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            return forwardedFor.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(realIp)) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    private String buildExtraJson(Object extraPayload, Throwable error) {
        Map<String, Object> wrapper = new LinkedHashMap<>();
        if (extraPayload != null) {
            wrapper.put("payload", extraPayload);
        }
        if (error != null) {
            wrapper.put("errorClass", error.getClass().getName());
            wrapper.put("errorMessage", error.getMessage());
        }
        if (wrapper.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(wrapper);
        } catch (JsonProcessingException ex) {
            return "{\"error\":\"extra_json_serialize_failed\"}";
        }
    }

    private String truncate(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    private Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Long longValue) {
            return longValue;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = value.toString();
        if (!StringUtils.hasText(text)) {
            return null;
        }
        return Long.parseLong(text.trim());
    }

    private String asString(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString();
        return StringUtils.hasText(text) ? text.trim() : null;
    }
}
