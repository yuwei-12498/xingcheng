package com.citytrip.service.impl;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Duration;
import java.util.Properties;
import jakarta.mail.Session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailVerificationCodeServiceTest {

    @Test
    void sendRegisterCodeStoresSixDigitCodeInRedisForFiveMinutesAndSendsMail() throws Exception {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(mailSender.createMimeMessage()).thenReturn(message);

        EmailVerificationCodeService service = new EmailVerificationCodeService(
                mailSender,
                redisTemplate,
                "noreply@050923.xyz",
                () -> "123456"
        );

        service.sendRegisterCode(" USER@Example.COM ");

        verify(valueOperations).set(
                "auth:register-code:user@example.com",
                "123456",
                Duration.ofMinutes(5)
        );
        verify(mailSender).send(message);

        assertThat(message.getFrom()[0].toString()).contains("noreply@050923.xyz");
        assertThat(message.getAllRecipients()[0].toString()).isEqualTo("user@example.com");
        assertThat(message.getSubject()).contains("验证码");
        assertThat(message.getContent().toString()).contains("123456");
    }

    @Test
    void verifyAndConsumeDeletesCodeOnlyWhenRedisCodeMatches() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("auth:register-code:user@example.com")).thenReturn("654321");

        EmailVerificationCodeService service = new EmailVerificationCodeService(
                mailSender,
                redisTemplate,
                "noreply@050923.xyz",
                () -> "000000"
        );

        service.verifyAndConsume("USER@example.com", " 654321 ");

        verify(redisTemplate).delete("auth:register-code:user@example.com");
    }

    @Test
    void sendPasswordResetCodeUsesSeparateRedisKeyAndSubject() throws Exception {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(mailSender.createMimeMessage()).thenReturn(message);

        EmailVerificationCodeService service = new EmailVerificationCodeService(
                mailSender,
                redisTemplate,
                "noreply@050923.xyz",
                () -> "112233"
        );

        service.sendPasswordResetCode("USER@example.COM");

        verify(valueOperations).set(
                "auth:password-reset-code:user@example.com",
                "112233",
                Duration.ofMinutes(5)
        );
        verify(mailSender).send(message);
        assertThat(message.getSubject()).contains("找回密码");
        assertThat(message.getContent().toString()).contains("112233");
    }

    @Test
    void verifyPasswordResetAndConsumeDeletesPasswordResetCode() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("auth:password-reset-code:user@example.com")).thenReturn("112233");

        EmailVerificationCodeService service = new EmailVerificationCodeService(
                mailSender,
                redisTemplate,
                "noreply@050923.xyz",
                () -> "000000"
        );

        service.verifyPasswordResetAndConsume("USER@example.com", " 112233 ");

        verify(redisTemplate).delete("auth:password-reset-code:user@example.com");
    }
}
