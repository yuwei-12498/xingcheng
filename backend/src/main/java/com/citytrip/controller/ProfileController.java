package com.citytrip.controller;

import com.citytrip.annotation.LoginRequired;
import com.citytrip.common.AuthConstants;
import com.citytrip.model.vo.ItinerarySummaryVO;
import com.citytrip.service.ItineraryService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/user")
public class ProfileController {

    private final ItineraryService itineraryService;

    public ProfileController(ItineraryService itineraryService) {
        this.itineraryService = itineraryService;
    }

    @LoginRequired
    @GetMapping("/itineraries")
    public List<ItinerarySummaryVO> listUserItineraries(
            @RequestParam(value = "type", defaultValue = "generated") String type,
            @RequestParam(value = "limit", required = false) Integer limit,
            HttpServletRequest request) {
        return itineraryService.listProfileItineraries(currentUserId(request), type, limit);
    }

    private Long currentUserId(HttpServletRequest request) {
        return request == null ? null : (Long) request.getAttribute(AuthConstants.LOGIN_USER_ID);
    }
}
