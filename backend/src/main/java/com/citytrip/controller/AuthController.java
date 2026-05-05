package com.citytrip.controller;

import com.citytrip.annotation.LoginRequired;
import com.citytrip.common.AuthConstants;
import com.citytrip.common.UnauthorizedException;
import com.citytrip.model.dto.LoginReqDTO;
import com.citytrip.model.dto.RegisterReqDTO;
import com.citytrip.model.dto.ResetPasswordReqDTO;
import com.citytrip.model.dto.SendCodeReqDTO;
import com.citytrip.model.vo.UserSessionVO;
import com.citytrip.service.UserService;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping({"/auth/register", "/users"})
    public ResponseEntity<UserSessionVO> register(@Valid @RequestBody RegisterReqDTO req) {
        UserSessionVO vo = userService.register(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(vo);
    }

    @PostMapping("/users/send-code")
    public ResponseEntity<Void> sendCode(@Valid @RequestBody SendCodeReqDTO req) {
        userService.sendRegisterCode(req.getEmail().trim().toLowerCase(java.util.Locale.ROOT));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/auth/password/send-code")
    public ResponseEntity<Void> sendPasswordResetCode(@Valid @RequestBody SendCodeReqDTO req) {
        userService.sendPasswordResetCode(req.getEmail().trim().toLowerCase(java.util.Locale.ROOT));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/auth/password/reset")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordReqDTO req) {
        userService.resetPassword(req);
        return ResponseEntity.noContent().build();
    }

    @PostMapping({"/auth/login", "/sessions"})
    public UserSessionVO login(@Valid @RequestBody LoginReqDTO req) {
        return userService.login(req);
    }

    @DeleteMapping({"/auth/logout", "/sessions/current"})
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent().build();
    }

    @LoginRequired
    @GetMapping({"/auth/me", "/users/me"})
    public UserSessionVO me(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute(AuthConstants.LOGIN_USER_ID);
        UserSessionVO vo = userService.getSessionUser(userId);
        if (vo == null) {
            throw new UnauthorizedException("登录状态已失效，请重新登录");
        }
        return vo;
    }
}
