package com.citytrip.controller;

import com.citytrip.annotation.LoginRequired;
import com.citytrip.common.AuthConstants;
import com.citytrip.model.dto.ItineraryEditApplyReqDTO;
import com.citytrip.model.dto.ItineraryEditRestoreReqDTO;
import com.citytrip.model.vo.ItineraryEditVersionVO;
import com.citytrip.model.vo.ItineraryVO;
import com.citytrip.service.application.itinerary.ItineraryEditApplyUseCase;
import com.citytrip.service.application.itinerary.ItineraryEditRestoreUseCase;
import com.citytrip.service.application.itinerary.ItineraryEditVersionQueryUseCase;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/itineraries")
public class ItineraryEditController {

    private final ItineraryEditApplyUseCase itineraryEditApplyUseCase;
    private final ItineraryEditRestoreUseCase itineraryEditRestoreUseCase;
    private final ItineraryEditVersionQueryUseCase itineraryEditVersionQueryUseCase;

    public ItineraryEditController(ItineraryEditApplyUseCase itineraryEditApplyUseCase,
                                   ItineraryEditRestoreUseCase itineraryEditRestoreUseCase,
                                   ItineraryEditVersionQueryUseCase itineraryEditVersionQueryUseCase) {
        this.itineraryEditApplyUseCase = itineraryEditApplyUseCase;
        this.itineraryEditRestoreUseCase = itineraryEditRestoreUseCase;
        this.itineraryEditVersionQueryUseCase = itineraryEditVersionQueryUseCase;
    }

    @LoginRequired
    @PostMapping("/{id}/edits/apply")
    public ItineraryVO apply(@PathVariable("id") Long itineraryId,
                             @Valid @RequestBody ItineraryEditApplyReqDTO req,
                             HttpServletRequest request) {
        return itineraryEditApplyUseCase.apply(currentUserId(request), itineraryId, req);
    }

    @LoginRequired
    @PostMapping("/{id}/edits/restore")
    public ItineraryVO restore(@PathVariable("id") Long itineraryId,
                               @Valid @RequestBody ItineraryEditRestoreReqDTO req,
                               HttpServletRequest request) {
        return itineraryEditRestoreUseCase.restore(currentUserId(request), itineraryId, req);
    }

    @LoginRequired
    @GetMapping("/{id}/edit-versions")
    public List<ItineraryEditVersionVO> listVersions(@PathVariable("id") Long itineraryId,
                                                     HttpServletRequest request) {
        return itineraryEditVersionQueryUseCase.list(currentUserId(request), itineraryId);
    }

    private Long currentUserId(HttpServletRequest request) {
        return request == null ? null : (Long) request.getAttribute(AuthConstants.LOGIN_USER_ID);
    }
}
