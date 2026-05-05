package com.citytrip.config;

import com.citytrip.mapper.UserMapper;
import com.citytrip.model.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminAccountBootstrapRunnerTest {

    @Test
    void rejectsBlankBootstrapPasswordWhenExplicitlyEnabled() {
        UserMapper userMapper = mock(UserMapper.class);
        AdminAccountBootstrapRunner runner = buildRunner(userMapper, "admin", "", false);

        assertThatThrownBy(() -> runner.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_BOOTSTRAP_ADMIN_PASSWORD");

        verify(userMapper, never()).insert(any(User.class));
    }

    @Test
    void rejectsUnsafeHistoricalDefaultPassword() {
        UserMapper userMapper = mock(UserMapper.class);
        AdminAccountBootstrapRunner runner = buildRunner(userMapper, "admin", "Admin123456", false);

        assertThatThrownBy(() -> runner.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unsafe");

        verify(userMapper, never()).insert(any(User.class));
    }

    @Test
    void createsAdminWhenStrongPasswordIsExplicitlyConfigured() {
        UserMapper userMapper = mock(UserMapper.class);
        when(userMapper.selectByUsername("admin")).thenReturn(null);
        AdminAccountBootstrapRunner runner = buildRunner(userMapper, "admin", "A-strong-admin-password-2026", false);

        runner.run(null);

        verify(userMapper).insert(any(User.class));
    }

    private AdminAccountBootstrapRunner buildRunner(UserMapper userMapper,
                                                    String username,
                                                    String password,
                                                    boolean syncPassword) {
        AdminAccountBootstrapRunner runner = new AdminAccountBootstrapRunner(userMapper);
        ReflectionTestUtils.setField(runner, "adminUsername", username);
        ReflectionTestUtils.setField(runner, "adminPassword", password);
        ReflectionTestUtils.setField(runner, "adminNickname", "系统管理员");
        ReflectionTestUtils.setField(runner, "syncPassword", syncPassword);
        return runner;
    }
}
