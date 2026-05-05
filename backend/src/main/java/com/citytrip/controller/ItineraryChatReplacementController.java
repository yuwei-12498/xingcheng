package com.citytrip.controller;

import com.citytrip.annotation.LoginRequired;
import com.citytrip.common.AuthConstants;
import com.citytrip.model.dto.ChatReplacementApplyReqDTO;
import com.citytrip.model.dto.ChatReplacementRestoreReqDTO;
import com.citytrip.model.vo.ItineraryVO;
import com.citytrip.service.application.itinerary.ChatReplacementApplyUseCase;
import com.citytrip.service.application.itinerary.ChatReplacementRestoreUseCase;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/itineraries")
public class ItineraryChatReplacementController {

    private final ChatReplacementApplyUseCase chatReplacementApplyUseCase;
    private final ChatReplacementRestoreUseCase chatReplacementRestoreUseCase;

    public ItineraryChatReplacementController(ChatReplacementApplyUseCase chatReplacementApplyUseCase,
                                              ChatReplacementRestoreUseCase chatReplacementRestoreUseCase) {
        this.chatReplacementApplyUseCase = chatReplacementApplyUseCase;
        this.chatReplacementRestoreUseCase = chatReplacementRestoreUseCase;
    }

    @LoginRequired
    @PostMapping("/{id}/chat-replacements/apply")
    public ItineraryVO apply(@PathVariable("id") Long itineraryId,
                             @Valid @RequestBody ChatReplacementApplyReqDTO req,
                             HttpServletRequest request) {
        return chatReplacementApplyUseCase.apply(currentUserId(request), itineraryId, req);
    }

    @LoginRequired
    @PostMapping("/{id}/chat-replacements/restore")
    public ItineraryVO restore(@PathVariable("id") Long itineraryId,
                               @Valid @RequestBody ChatReplacementRestoreReqDTO req,
                               HttpServletRequest request) {
        return chatReplacementRestoreUseCase.restore(currentUserId(request), itineraryId, req);
    }

    private Long currentUserId(HttpServletRequest request) {
        return request == null ? null : (Long) request.getAttribute(AuthConstants.LOGIN_USER_ID);
    }
}
