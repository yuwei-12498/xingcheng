package com.citytrip.service.impl;

import com.citytrip.common.BadRequestException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Locale;
import java.util.function.Supplier;

@Service
public class EmailVerificationCodeService {

    static final Duration REGISTER_CODE_TTL = Duration.ofMinutes(5);
    static final String REGISTER_CODE_KEY_PREFIX = "auth:register-code:";
    static final String PASSWORD_RESET_CODE_KEY_PREFIX = "auth:password-reset-code:";

    private final JavaMailSender mailSender;
    private final StringRedisTemplate redisTemplate;
    private final String fromAddress;
    private final Supplier<String> codeSupplier;

    @Autowired
    public EmailVerificationCodeService(JavaMailSender mailSender,
                                        StringRedisTemplate redisTemplate,
                                        @Value("${app.auth.mail.from:noreply@050923.xyz}") String fromAddress) {
        this(mailSender, redisTemplate, fromAddress, secureSixDigitSupplier());
    }

    EmailVerificationCodeService(JavaMailSender mailSender,
                                 StringRedisTemplate redisTemplate,
                                 String fromAddress,
                                 Supplier<String> codeSupplier) {
        this.mailSender = mailSender;
        this.redisTemplate = redisTemplate;
        this.fromAddress = fromAddress;
        this.codeSupplier = codeSupplier;
    }

    public void sendRegisterCode(String email) {
        String normalizedEmail = normalizeEmail(email);
        String code = codeSupplier.get();
        redisTemplate.opsForValue().set(registerCodeKey(normalizedEmail), code, REGISTER_CODE_TTL);
        sendCodeMail(normalizedEmail, code, "行城有数注册验证码", "注册行城有数账号");
    }

    public void sendPasswordResetCode(String email) {
        String normalizedEmail = normalizeEmail(email);
        String code = codeSupplier.get();
        redisTemplate.opsForValue().set(passwordResetCodeKey(normalizedEmail), code, REGISTER_CODE_TTL);
        sendCodeMail(normalizedEmail, code, "行城有数找回密码验证码", "找回或重置行城有数账号密码");
    }

    public void verifyAndConsume(String email, String submittedCode) {
        String normalizedEmail = normalizeEmail(email);
        verifyAndConsumeKey(registerCodeKey(normalizedEmail), submittedCode);
    }

    public void verifyPasswordResetAndConsume(String email, String submittedCode) {
        String normalizedEmail = normalizeEmail(email);
        verifyAndConsumeKey(passwordResetCodeKey(normalizedEmail), submittedCode);
    }

    private void verifyAndConsumeKey(String key, String submittedCode) {
        String normalizedCode = submittedCode == null ? "" : submittedCode.trim();
        String expectedCode = redisTemplate.opsForValue().get(key);
        if (!StringUtils.hasText(expectedCode) || !expectedCode.equals(normalizedCode)) {
            throw new BadRequestException("验证码错误或已过期");
        }
        redisTemplate.delete(key);
    }

    private void sendCodeMail(String email, String code, String subject, String sceneText) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(email);
            helper.setSubject(subject);
            helper.setText("""
                    您正在%s。

                    验证码：%s

                    该验证码 5 分钟内有效，请勿转发给他人。
                    """.formatted(sceneText, code), false);
            mailSender.send(message);
        } catch (MessagingException | MailException ex) {
            throw new BadRequestException("验证码邮件发送失败，请稍后重试");
        }
    }

    private static Supplier<String> secureSixDigitSupplier() {
        SecureRandom random = new SecureRandom();
        return () -> String.format("%06d", random.nextInt(1_000_000));
    }

    static String normalizeEmail(String email) {
        String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(normalized)) {
            throw new BadRequestException("邮箱不能为空");
        }
        return normalized;
    }

    static String registerCodeKey(String normalizedEmail) {
        return REGISTER_CODE_KEY_PREFIX + normalizedEmail;
    }

    static String passwordResetCodeKey(String normalizedEmail) {
        return PASSWORD_RESET_CODE_KEY_PREFIX + normalizedEmail;
    }
}
