package com.citytrip.controller;

import com.citytrip.annotation.LoginRequired;
import com.citytrip.common.AuthConstants;
import com.citytrip.model.entity.UserCustomPoi;
import com.citytrip.service.application.itinerary.UserCustomPoiRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/custom-pois")
public class UserCustomPoiController {

    private final UserCustomPoiRepository userCustomPoiRepository;

    public UserCustomPoiController(UserCustomPoiRepository userCustomPoiRepository) {
        this.userCustomPoiRepository = userCustomPoiRepository;
    }

    @LoginRequired
    @GetMapping
    public List<UserCustomPoi> list(HttpServletRequest request) {
        Long userId = request == null ? null : (Long) request.getAttribute(AuthConstants.LOGIN_USER_ID);
        return userCustomPoiRepository.listOwned(userId);
    }
}
