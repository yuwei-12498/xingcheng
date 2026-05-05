package com.citytrip.config;

import com.citytrip.mapper.UserMapper;
import com.citytrip.model.entity.User;
import com.citytrip.util.PasswordUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@ConditionalOnProperty(name = "app.auth.bootstrap-admin.enabled", havingValue = "true")
public class AdminAccountBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminAccountBootstrapRunner.class);
    private static final String UNSAFE_HISTORICAL_DEFAULT_PASSWORD = "Admin123456";

    private final UserMapper userMapper;

    @Value("${app.auth.bootstrap-admin.username:admin}")
    private String adminUsername;

    @Value("${app.auth.bootstrap-admin.password:}")
    private String adminPassword;

    @Value("${app.auth.bootstrap-admin.nickname:系统管理员}")
    private String adminNickname;

    @Value("${app.auth.bootstrap-admin.sync-password:false}")
    private boolean syncPassword;

    public AdminAccountBootstrapRunner(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        String username = adminUsername == null ? null : adminUsername.trim();
        String password = adminPassword == null ? null : adminPassword.trim();
        String nickname = adminNickname == null ? null : adminNickname.trim();

        if (!StringUtils.hasText(username)) {
            throw new IllegalStateException("Bootstrap admin is enabled but APP_BOOTSTRAP_ADMIN_USERNAME is empty.");
        }
        if (!StringUtils.hasText(password)) {
            throw new IllegalStateException("Bootstrap admin is enabled but APP_BOOTSTRAP_ADMIN_PASSWORD is empty.");
        }
        if (UNSAFE_HISTORICAL_DEFAULT_PASSWORD.equals(password)) {
            throw new IllegalStateException("Bootstrap admin password is unsafe. Set APP_BOOTSTRAP_ADMIN_PASSWORD to a strong unique value.");
        }

        User existing = userMapper.selectByUsername(username);
        if (existing == null) {
            User admin = new User();
            admin.setUsername(username);
            admin.setNickname(StringUtils.hasText(nickname) ? nickname : "系统管理员");
            admin.setPasswordSalt(PasswordUtils.bcryptStorageMarker());
            admin.setPasswordHash(PasswordUtils.hashPassword(password));
            admin.setRole(1);
            admin.setStatus(1);
            userMapper.insert(admin);
            log.info("Bootstrap admin account created. username={}", username);
            return;
        }

        boolean changed = false;
        if (!Integer.valueOf(1).equals(existing.getRole())) {
            existing.setRole(1);
            changed = true;
        }
        if (!Integer.valueOf(1).equals(existing.getStatus())) {
            existing.setStatus(1);
            changed = true;
        }
        if (StringUtils.hasText(nickname) && !nickname.equals(existing.getNickname())) {
            existing.setNickname(nickname);
            changed = true;
        }
        if (syncPassword && !PasswordUtils.matchesPassword(password, existing.getPasswordHash(), existing.getPasswordSalt())) {
            existing.setPasswordSalt(PasswordUtils.bcryptStorageMarker());
            existing.setPasswordHash(PasswordUtils.hashPassword(password));
            changed = true;
        }
        if (changed) {
            userMapper.updateById(existing);
            log.info("Bootstrap admin account synchronized. username={}", username);
        } else {
            log.info("Bootstrap admin account already ready. username={}", username);
        }
    }
}
