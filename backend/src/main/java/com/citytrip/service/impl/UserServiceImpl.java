package com.citytrip.service.impl;

import com.citytrip.common.BadRequestException;
import com.citytrip.common.UnauthorizedException;
import com.citytrip.mapper.UserMapper;
import com.citytrip.model.dto.LoginReqDTO;
import com.citytrip.model.dto.RegisterReqDTO;
import com.citytrip.model.dto.ResetPasswordReqDTO;
import com.citytrip.model.entity.User;
import com.citytrip.model.vo.UserSessionVO;
import com.citytrip.service.UserService;
import com.citytrip.util.JwtUtil;
import com.citytrip.util.PasswordUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Service
public class UserServiceImpl implements UserService {

    private static final int MAX_DAILY_PASSWORD_FAILURES = 5;
    private static final String LOGIN_FAILURE_KEY_PREFIX = "auth:login-fail:";
    private static final ZoneId LOGIN_LIMIT_ZONE = ZoneId.of("Asia/Shanghai");

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final EmailVerificationCodeService emailVerificationCodeService;
    private final StringRedisTemplate redisTemplate;

    @Autowired
    public UserServiceImpl(UserMapper userMapper,
                           JwtUtil jwtUtil,
                           EmailVerificationCodeService emailVerificationCodeService,
                           StringRedisTemplate redisTemplate) {
        this.userMapper = userMapper;
        this.jwtUtil = jwtUtil;
        this.emailVerificationCodeService = emailVerificationCodeService;
        this.redisTemplate = redisTemplate;
    }

    UserServiceImpl(UserMapper userMapper,
                    JwtUtil jwtUtil,
                    EmailVerificationCodeService emailVerificationCodeService) {
        this(userMapper, jwtUtil, emailVerificationCodeService, null);
    }

    @Override
    public void sendRegisterCode(String email) {
        emailVerificationCodeService.sendRegisterCode(EmailVerificationCodeService.normalizeEmail(email));
    }

    @Override
    public void sendPasswordResetCode(String email) {
        String normalizedEmail = EmailVerificationCodeService.normalizeEmail(email);
        User existingEmail = userMapper.selectByEmail(normalizedEmail);
        if (existingEmail == null) {
            throw new BadRequestException("该邮箱尚未注册");
        }
        if (existingEmail.getStatus() != null && existingEmail.getStatus() == 0) {
            throw new BadRequestException("抱歉，该账号已被冻结，无法找回密码");
        }
        emailVerificationCodeService.sendPasswordResetCode(normalizedEmail);
    }

    @Override
    public UserSessionVO register(RegisterReqDTO req) {
        String username = normalize(req == null ? null : req.getUsername());
        String nickname = normalize(req == null ? null : req.getNickname());
        String password = req == null ? null : req.getPassword();
        String email = EmailVerificationCodeService.normalizeEmail(req == null ? null : req.getEmail());
        String emailCode = normalize(req == null ? null : req.getEmailCode());

        validateRegister(username, nickname, password, email, emailCode);

        User existing = userMapper.selectByUsername(username);
        if (existing != null) {
            throw new BadRequestException("用户名已存在");
        }
        User existingEmail = userMapper.selectByEmail(email);
        if (existingEmail != null) {
            throw new BadRequestException("邮箱已被注册");
        }

        emailVerificationCodeService.verifyAndConsume(email, emailCode);

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setNickname(nickname);
        user.setPasswordSalt(PasswordUtils.bcryptStorageMarker());
        user.setPasswordHash(PasswordUtils.hashPassword(password));
        user.setRole(0);
        user.setStatus(1);
        userMapper.insert(user);
        return toSessionVO(user);
    }

    @Override
    public UserSessionVO login(LoginReqDTO req) {
        String username = normalize(req == null ? null : req.getUsername());
        String password = req == null ? null : req.getPassword();

        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            throw new BadRequestException("用户名和密码不能为空");
        }

        assertLoginNotLocked(username);

        User user = userMapper.selectByUsername(username);
        if (user == null) {
            recordPasswordFailure(username);
            throw new UnauthorizedException("用户名或密码错误");
        }

        if (!PasswordUtils.matchesPassword(password, user.getPasswordHash(), user.getPasswordSalt())) {
            recordPasswordFailure(username);
            throw new UnauthorizedException("用户名或密码错误");
        }

        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new UnauthorizedException("抱歉，您的账号已被冻结");
        }

        if (PasswordUtils.needsRehash(user.getPasswordHash())) {
            user.setPasswordHash(PasswordUtils.hashPassword(password));
            user.setPasswordSalt(PasswordUtils.bcryptStorageMarker());
            userMapper.updateById(user);
        }

        clearPasswordFailures(username);
        return toSessionVO(user);
    }

    @Override
    public void resetPassword(ResetPasswordReqDTO req) {
        String email = EmailVerificationCodeService.normalizeEmail(req == null ? null : req.getEmail());
        String emailCode = normalize(req == null ? null : req.getEmailCode());
        String password = req == null ? null : req.getPassword();
        validatePasswordReset(email, emailCode, password);

        User user = userMapper.selectByEmail(email);
        if (user == null) {
            throw new BadRequestException("该邮箱尚未注册");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BadRequestException("抱歉，该账号已被冻结，无法找回密码");
        }

        emailVerificationCodeService.verifyPasswordResetAndConsume(email, emailCode);
        user.setPasswordHash(PasswordUtils.hashPassword(password));
        user.setPasswordSalt(PasswordUtils.bcryptStorageMarker());
        userMapper.updateById(user);
        clearPasswordFailures(user.getUsername());
    }

    @Override
    public UserSessionVO getSessionUser(Long userId) {
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
        return toSessionVO(user);
    }

    private void validateRegister(String username, String nickname, String password, String email, String emailCode) {
        if (!StringUtils.hasText(username)) {
            throw new BadRequestException("用户名不能为空");
        }
        if (!StringUtils.hasText(email)) {
            throw new BadRequestException("邮箱不能为空");
        }
        if (!StringUtils.hasText(emailCode)) {
            throw new BadRequestException("验证码不能为空");
        }
        if (!StringUtils.hasText(nickname)) {
            throw new BadRequestException("昵称不能为空");
        }
        if (!StringUtils.hasText(password)) {
            throw new BadRequestException("密码不能为空");
        }
        if (username.length() < 4 || username.length() > 20) {
            throw new BadRequestException("用户名长度需在 4 到 20 位之间");
        }
        if (email.length() > 128 || !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new BadRequestException("邮箱格式不正确");
        }
        if (!emailCode.matches("^\\d{6}$")) {
            throw new BadRequestException("验证码格式不正确");
        }
        if (!username.matches("^[A-Za-z0-9_]+$")) {
            throw new BadRequestException("用户名仅支持字母、数字和下划线");
        }
        if (nickname.length() < 2 || nickname.length() > 20) {
            throw new BadRequestException("昵称长度需在 2 到 20 位之间");
        }
        if (password.length() < 6 || password.length() > 20) {
            throw new BadRequestException("密码长度需在 6 到 20 位之间");
        }
    }

    private void validatePasswordReset(String email, String emailCode, String password) {
        if (!StringUtils.hasText(email)) {
            throw new BadRequestException("邮箱不能为空");
        }
        if (email.length() > 128 || !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new BadRequestException("邮箱格式不正确");
        }
        if (!StringUtils.hasText(emailCode)) {
            throw new BadRequestException("验证码不能为空");
        }
        if (!emailCode.matches("^\\d{6}$")) {
            throw new BadRequestException("验证码格式不正确");
        }
        if (!StringUtils.hasText(password)) {
            throw new BadRequestException("密码不能为空");
        }
        if (password.length() < 6 || password.length() > 20) {
            throw new BadRequestException("密码长度需在 6 到 20 位之间");
        }
    }

    private void assertLoginNotLocked(String username) {
        if (redisTemplate == null || !StringUtils.hasText(username)) {
            return;
        }
        String value = redisTemplate.opsForValue().get(loginFailureKey(username));
        int failures = parseFailureCount(value);
        if (failures >= MAX_DAILY_PASSWORD_FAILURES) {
            throw new UnauthorizedException("今日密码错误次数已达 5 次，请明天再试或通过找回密码重置");
        }
    }

    private void recordPasswordFailure(String username) {
        if (redisTemplate == null || !StringUtils.hasText(username)) {
            return;
        }
        String key = loginFailureKey(username);
        Long failures = redisTemplate.opsForValue().increment(key);
        redisTemplate.expire(key, secondsUntilTomorrow());
        if (failures != null && failures >= MAX_DAILY_PASSWORD_FAILURES) {
            throw new UnauthorizedException("今日密码错误次数已达 5 次，请明天再试或通过找回密码重置");
        }
    }

    private void clearPasswordFailures(String username) {
        if (redisTemplate == null || !StringUtils.hasText(username)) {
            return;
        }
        redisTemplate.delete(loginFailureKey(username));
    }

    private String loginFailureKey(String username) {
        LocalDate today = LocalDate.now(LOGIN_LIMIT_ZONE);
        return LOGIN_FAILURE_KEY_PREFIX + today + ":" + username.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private Duration secondsUntilTomorrow() {
        ZonedDateTime now = ZonedDateTime.now(LOGIN_LIMIT_ZONE);
        ZonedDateTime tomorrow = now.toLocalDate().plusDays(1).atStartOfDay(LOGIN_LIMIT_ZONE);
        return Duration.between(now, tomorrow).plusMinutes(1);
    }

    private int parseFailureCount(String value) {
        if (!StringUtils.hasText(value)) {
            return 0;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private UserSessionVO toSessionVO(User user) {
        UserSessionVO vo = new UserSessionVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setRole(user.getRole() == null ? 0 : user.getRole());
        vo.setToken(jwtUtil.generateToken(vo.getId(), vo.getRole()));
        return vo;
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }
}
