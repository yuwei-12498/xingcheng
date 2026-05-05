package com.citytrip.config;

import com.citytrip.annotation.AdminRequired;
import com.citytrip.common.ApiErrorResponse;
import com.citytrip.common.AuthConstants;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

@Component
public class AdminInterceptor implements HandlerInterceptor {

    private final ObjectMapper objectMapper;

    public AdminInterceptor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        AdminRequired adminRequired = handlerMethod.getMethodAnnotation(AdminRequired.class);
        if (adminRequired == null) {
            adminRequired = handlerMethod.getBeanType().getAnnotation(AdminRequired.class);
            if (adminRequired == null) {
                return true;
            }
        }

        Integer role = (Integer) request.getAttribute(AuthConstants.LOGIN_USER_ROLE);
        if (role != null && role == 1) {
            return true;
        }

        ApiErrorResponse body = new ApiErrorResponse(
                HttpServletResponse.SC_FORBIDDEN,
                "您没有权限访问管理员接口",
                request.getRequestURI()
        );

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(body));
        return false;
    }
}
