package com.citytrip.controller;

import com.citytrip.common.AuthConstants;
import com.citytrip.model.vo.CommunityItineraryDetailVO;
import com.citytrip.model.vo.CommunityItineraryPageVO;
import com.citytrip.service.ItineraryService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/community")
public class CommunityController {

    private final ItineraryService itineraryService;

    public CommunityController(ItineraryService itineraryService) {
        this.itineraryService = itineraryService;
    }

    @GetMapping("/itineraries")
    public CommunityItineraryPageVO listCommunityItineraries(
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "size", defaultValue = "12") Integer size,
            @RequestParam(value = "sort", defaultValue = "latest") String sort,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "theme", required = false) String theme,
            HttpServletRequest request) {
        return itineraryService.listCommunityItineraries(
                page == null ? 1 : page,
                size == null ? 12 : size,
                sort,
                keyword,
                theme,
                currentUserId(request)
        );
    }

    @GetMapping("/itineraries/{id}")
    public CommunityItineraryDetailVO getCommunityItinerary(@PathVariable("id") Long id,
                                                            HttpServletRequest request) {
        return itineraryService.getCommunityItinerary(id, currentUserId(request));
    }

    private Long currentUserId(HttpServletRequest request) {
        return request == null ? null : (Long) request.getAttribute(AuthConstants.LOGIN_USER_ID);
    }
}
