package com.citytrip.config;

import com.citytrip.annotation.AdminRequired;
import com.citytrip.annotation.LoginRequired;
import com.citytrip.common.ApiErrorResponse;
import com.citytrip.common.AuthConstants;
import com.citytrip.mapper.UserMapper;
import com.citytrip.model.entity.User;
import com.citytrip.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final ObjectMapper objectMapper;
    private final JwtUtil jwtUtil;
    private final UserMapper userMapper;

    public AuthInterceptor(ObjectMapper objectMapper, JwtUtil jwtUtil, UserMapper userMapper) {
        this.objectMapper = objectMapper;
        this.jwtUtil = jwtUtil;
        this.userMapper = userMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        LoginRequired loginRequired = handlerMethod.getMethodAnnotation(LoginRequired.class);
        if (loginRequired == null) {
            loginRequired = handlerMethod.getBeanType().getAnnotation(LoginRequired.class);
        }

        AdminRequired adminRequired = handlerMethod.getMethodAnnotation(AdminRequired.class);
        if (adminRequired == null) {
            adminRequired = handlerMethod.getBeanType().getAnnotation(AdminRequired.class);
        }

        boolean requiresAuth = loginRequired != null || adminRequired != null;

        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
            Claims claims = jwtUtil.parseToken(token);
            User user = resolveActiveUser(claims);
            if (user != null) {
                request.setAttribute(AuthConstants.LOGIN_USER_ID, user.getId());
                request.setAttribute(AuthConstants.LOGIN_USER_ROLE, user.getRole());
                return true;
            }
        }

        if (!requiresAuth) {
            return true;
        }

        ApiErrorResponse body = new ApiErrorResponse(
                HttpServletResponse.SC_UNAUTHORIZED,
                "请先登录",
                request.getRequestURI()
        );

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(body));
        return false;
    }

    private User resolveActiveUser(Claims claims) {
        if (claims == null) {
            return null;
        }

        Long userId = claims.get("userId", Long.class);
        if (userId == null) {
            return null;
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            return null;
        }

        if (user.getStatus() != null && user.getStatus() == 0) {
            return null;
        }
        return user;
    }
}
