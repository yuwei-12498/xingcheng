package com.citytrip.service;

import com.citytrip.model.dto.LoginReqDTO;
import com.citytrip.model.dto.RegisterReqDTO;
import com.citytrip.model.dto.ResetPasswordReqDTO;
import com.citytrip.model.vo.UserSessionVO;

public interface UserService {
    void sendRegisterCode(String email);

    void sendPasswordResetCode(String email);

    UserSessionVO register(RegisterReqDTO req);

    UserSessionVO login(LoginReqDTO req);

    void resetPassword(ResetPasswordReqDTO req);

    UserSessionVO getSessionUser(Long userId);
}
