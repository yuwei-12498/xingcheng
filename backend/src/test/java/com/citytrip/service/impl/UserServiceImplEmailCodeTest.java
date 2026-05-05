package com.citytrip.service.impl;

import com.citytrip.mapper.UserMapper;
import com.citytrip.model.dto.RegisterReqDTO;
import com.citytrip.model.dto.ResetPasswordReqDTO;
import com.citytrip.model.entity.User;
import com.citytrip.util.JwtUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceImplEmailCodeTest {

    @Test
    void registerVerifiesEmailCodeBeforeCreatingUserAndStoresEmail() {
        UserMapper userMapper = mock(UserMapper.class);
        JwtUtil jwtUtil = mock(JwtUtil.class);
        EmailVerificationCodeService emailVerificationCodeService = mock(EmailVerificationCodeService.class);
        UserServiceImpl service = new UserServiceImpl(userMapper, jwtUtil, emailVerificationCodeService);

        RegisterReqDTO req = new RegisterReqDTO();
        req.setUsername("tester");
        req.setNickname("测试用户");
        req.setPassword("password123");
        req.setEmail("USER@example.COM");
        req.setEmailCode("123456");

        when(userMapper.selectByUsername("tester")).thenReturn(null);
        when(jwtUtil.generateToken(anyLong(), anyInt())).thenReturn("token");

        service.register(req);

        verify(emailVerificationCodeService).verifyAndConsume("user@example.com", "123456");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(userCaptor.capture());
        assertThat(userCaptor.getValue().getEmail()).isEqualTo("user@example.com");
    }

    @Test
    void sendPasswordResetCodeRequiresRegisteredEmail() {
        UserMapper userMapper = mock(UserMapper.class);
        JwtUtil jwtUtil = mock(JwtUtil.class);
        EmailVerificationCodeService emailVerificationCodeService = mock(EmailVerificationCodeService.class);
        UserServiceImpl service = new UserServiceImpl(userMapper, jwtUtil, emailVerificationCodeService);

        User user = new User();
        user.setEmail("user@example.com");
        user.setStatus(1);
        when(userMapper.selectByEmail("user@example.com")).thenReturn(user);

        service.sendPasswordResetCode(" USER@example.COM ");

        verify(emailVerificationCodeService).sendPasswordResetCode("user@example.com");
    }

    @Test
    void resetPasswordVerifiesCodeAndUpdatesPasswordHash() {
        UserMapper userMapper = mock(UserMapper.class);
        JwtUtil jwtUtil = mock(JwtUtil.class);
        EmailVerificationCodeService emailVerificationCodeService = mock(EmailVerificationCodeService.class);
        UserServiceImpl service = new UserServiceImpl(userMapper, jwtUtil, emailVerificationCodeService);

        User user = new User();
        user.setId(7L);
        user.setUsername("tester");
        user.setEmail("user@example.com");
        user.setStatus(1);
        user.setPasswordHash("$2a$10$old");
        when(userMapper.selectByEmail("user@example.com")).thenReturn(user);

        ResetPasswordReqDTO req = new ResetPasswordReqDTO();
        req.setEmail("USER@example.COM");
        req.setEmailCode("123456");
        req.setPassword("newpass123");

        service.resetPassword(req);

        verify(emailVerificationCodeService).verifyPasswordResetAndConsume("user@example.com", "123456");
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).updateById(userCaptor.capture());
        assertThat(userCaptor.getValue().getPasswordHash()).isNotEqualTo("$2a$10$old");
    }

    @Test
    void loginLocksAfterFivePasswordFailuresInSameDay() {
        UserMapper userMapper = mock(UserMapper.class);
        JwtUtil jwtUtil = mock(JwtUtil.class);
        EmailVerificationCodeService emailVerificationCodeService = mock(EmailVerificationCodeService.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        UserServiceImpl service = new UserServiceImpl(userMapper, jwtUtil, emailVerificationCodeService, redisTemplate);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(org.mockito.ArgumentMatchers.contains("auth:login-fail:"))).thenReturn("4");
        when(valueOperations.increment(org.mockito.ArgumentMatchers.contains("auth:login-fail:"))).thenReturn(5L);
        User user = new User();
        user.setUsername("tester");
        user.setPasswordHash(PasswordTestHash.HASH);
        user.setPasswordSalt(com.citytrip.util.PasswordUtils.bcryptStorageMarker());
        user.setStatus(1);
        when(userMapper.selectByUsername("tester")).thenReturn(user);

        com.citytrip.model.dto.LoginReqDTO req = new com.citytrip.model.dto.LoginReqDTO();
        req.setUsername("tester");
        req.setPassword("wrong-password");

        assertThatThrownBy(() -> service.login(req))
                .hasMessageContaining("今日密码错误次数已达 5 次");
        verify(redisTemplate).expire(org.mockito.ArgumentMatchers.contains("auth:login-fail:"), org.mockito.ArgumentMatchers.any(Duration.class));
        verify(jwtUtil, never()).generateToken(anyLong(), anyInt());
    }

    private static final class PasswordTestHash {
        private static final String HASH = com.citytrip.util.PasswordUtils.hashPassword("right-password");
    }
}
