package com.citytrip.controller;

import com.citytrip.annotation.LoginRequired;
import com.citytrip.annotation.TrackBehavior;
import com.citytrip.common.AnalyticsEventTypes;
import com.citytrip.common.AuthConstants;
import com.citytrip.model.dto.CommunityCommentReqDTO;
import com.citytrip.model.dto.FavoriteReqDTO;
import com.citytrip.model.dto.GenerateReqDTO;
import com.citytrip.model.dto.OptionSelectReqDTO;
import com.citytrip.model.dto.PublicStatusReqDTO;
import com.citytrip.model.dto.ReplaceReqDTO;
import com.citytrip.model.dto.ReplanReqDTO;
import com.citytrip.model.dto.ReplanRespDTO;
import com.citytrip.model.dto.SaveItineraryReqDTO;
import com.citytrip.model.dto.SegmentTravelReqDTO;
import com.citytrip.model.dto.SmartFillReqDTO;
import com.citytrip.model.vo.CommunityCommentVO;
import com.citytrip.model.vo.CommunityItineraryDetailVO;
import com.citytrip.model.vo.CommunityItineraryPageVO;
import com.citytrip.model.vo.ItinerarySummaryVO;
import com.citytrip.model.vo.ItineraryVO;
import com.citytrip.model.vo.SmartFillVO;
import com.citytrip.service.ItineraryService;
import com.citytrip.service.application.itinerary.SmartFillUseCase;
import com.citytrip.service.guard.AiRequestGuard;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/itineraries")
public class ItineraryController {

    private static final Logger log = LoggerFactory.getLogger(ItineraryController.class);

    private final ItineraryService itineraryService;
    private final SmartFillUseCase smartFillUseCase;
    private final AiRequestGuard aiRequestGuard;

    public ItineraryController(ItineraryService itineraryService,
                               SmartFillUseCase smartFillUseCase,
                               AiRequestGuard aiRequestGuard) {
        this.itineraryService = itineraryService;
        this.smartFillUseCase = smartFillUseCase;
        this.aiRequestGuard = aiRequestGuard;
    }

    @TrackBehavior(
            eventType = AnalyticsEventTypes.PLAN_SUBMIT,
            itineraryIdExpression = "#result.body.id",
            extraExpression = "#p0",
            weight = 1.0D
    )
    @PostMapping
    public ResponseEntity<ItineraryVO> createItinerary(@Valid @RequestBody GenerateReqDTO req, HttpServletRequest request) {
        ItineraryVO vo = aiRequestGuard.call("generate", guardSubject(request),
                () -> itineraryService.generateUserItinerary(currentUserId(request), req));
        return ResponseEntity.status(HttpStatus.CREATED).body(vo);
    }

    @LoginRequired
    @TrackBehavior(
            eventType = AnalyticsEventTypes.PLAN_SUBMIT,
            itineraryIdExpression = "#result.body.id",
            extraExpression = "#p0",
            weight = 1.0D
    )
    @PostMapping("/generate")
    public ResponseEntity<ItineraryVO> generateItinerary(@Valid @RequestBody GenerateReqDTO req,
                                                         HttpServletRequest request) {
        Long userId = currentUserId(request);
        log.info("planner generate request received, userId={}, cityName={}, tripDays={}, budgetLevel={}, totalBudget={}, themes={}, tripDate={}, startTime={}, endTime={}",
                userId,
                req == null ? null : req.getCityName(),
                req == null ? null : req.getTripDays(),
                req == null ? null : req.getBudgetLevel(),
                req == null ? null : req.getTotalBudget(),
                req == null ? null : req.getThemes(),
                req == null ? null : req.getTripDate(),
                req == null ? null : req.getStartTime(),
                req == null ? null : req.getEndTime());

        ItineraryVO vo = aiRequestGuard.call("generate", guardSubject(request),
                () -> itineraryService.generateUserItinerary(userId, req));

        log.info("planner generate request completed, userId={}, itineraryId={}, nodeCount={}, optionCount={}, totalDuration={}, totalCost={}",
                userId,
                vo == null ? null : vo.getId(),
                vo == null || vo.getNodes() == null ? 0 : vo.getNodes().size(),
                vo == null || vo.getOptions() == null ? 0 : vo.getOptions().size(),
                vo == null ? null : vo.getTotalDuration(),
                vo == null ? null : vo.getTotalCost());

        return ResponseEntity.ok(vo);
    }

    @PostMapping("/smart-fill")
    public SmartFillVO smartFill(@Valid @RequestBody SmartFillReqDTO req, HttpServletRequest request) {
        return aiRequestGuard.call("smart-fill", guardSubject(request), () -> smartFillUseCase.parse(req));
    }

    @LoginRequired
    @GetMapping
    public List<ItinerarySummaryVO> listItineraries(@RequestParam(value = "favorite", defaultValue = "false") boolean favorite,
                                                    @RequestParam(value = "limit", required = false) Integer limit,
                                                    HttpServletRequest request) {
        return itineraryService.listItineraries(currentUserId(request), favorite, limit);
    }

    @GetMapping("/community")
    public CommunityItineraryPageVO listCommunityItineraries(@RequestParam(value = "page", defaultValue = "1") Integer page,
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

    @GetMapping("/community/{id}")
    public CommunityItineraryDetailVO getCommunityItinerary(@PathVariable("id") Long id,
                                                            HttpServletRequest request) {
        return itineraryService.getCommunityItinerary(id, currentUserId(request));
    }

    @GetMapping("/community/{id}/comments")
    public List<CommunityCommentVO> listCommunityComments(@PathVariable("id") Long id,
                                                          HttpServletRequest request) {
        return itineraryService.listCommunityComments(id, currentUserId(request));
    }

    @LoginRequired
    @TrackBehavior(
            eventType = AnalyticsEventTypes.COMMUNITY_COMMENT,
            itineraryIdExpression = "#p0",
            extraExpression = "#p1",
            weight = 2.5D
    )
    @PostMapping("/community/{id}/comments")
    public ResponseEntity<CommunityCommentVO> addCommunityComment(@PathVariable("id") Long id,
                                                                  @Valid @RequestBody CommunityCommentReqDTO req,
                                                                  HttpServletRequest request) {
        CommunityCommentVO comment = itineraryService.addCommunityComment(currentUserId(request), id, req);
        return ResponseEntity.status(HttpStatus.CREATED).body(comment);
    }

    @LoginRequired
    @PatchMapping("/community/{id}/comments/{commentId}/pin")
    public CommunityItineraryDetailVO pinCommunityComment(@PathVariable("id") Long id,
                                                          @PathVariable("commentId") Long commentId,
                                                          HttpServletRequest request) {
        return itineraryService.pinCommunityComment(currentUserId(request), id, commentId);
    }

    @LoginRequired
    @DeleteMapping("/community/{id}")
    public ResponseEntity<Void> deleteCommunityPost(@PathVariable("id") Long id,
                                                    HttpServletRequest request) {
        itineraryService.deleteCommunityPost(currentUserId(request), id);
        return ResponseEntity.noContent().build();
    }

    @LoginRequired
    @TrackBehavior(
            eventType = AnalyticsEventTypes.COMMUNITY_LIKE,
            itineraryIdExpression = "#p0",
            weight = 2.5D
    )
    @PostMapping("/community/{id}/like")
    public CommunityItineraryDetailVO likeCommunityItinerary(@PathVariable("id") Long id,
                                                             HttpServletRequest request) {
        return itineraryService.likeCommunityItinerary(currentUserId(request), id);
    }

    @LoginRequired
    @DeleteMapping("/community/{id}/like")
    public CommunityItineraryDetailVO unlikeCommunityItinerary(@PathVariable("id") Long id,
                                                               HttpServletRequest request) {
        return itineraryService.unlikeCommunityItinerary(currentUserId(request), id);
    }

    @LoginRequired
    @PostMapping("/save")
    public ResponseEntity<ItineraryVO> saveCommunityItinerary(@Valid @RequestBody SaveItineraryReqDTO req,
                                                              HttpServletRequest request) {
        ItineraryVO vo = itineraryService.saveCommunityItinerary(currentUserId(request), req);
        return ResponseEntity.status(HttpStatus.CREATED).body(vo);
    }

    @LoginRequired
    @GetMapping("/{id}")
    public ItineraryVO getItinerary(@PathVariable("id") Long id, HttpServletRequest request) {
        return itineraryService.getItinerary(currentUserId(request), id);
    }

    @LoginRequired
    @PostMapping("/{id}/segments/{segmentIndex}/travel")
    public ItineraryVO calculateSegmentTravel(@PathVariable("id") Long id,
                                              @PathVariable("segmentIndex") Integer segmentIndex,
                                              @RequestBody(required = false) SegmentTravelReqDTO req,
                                              HttpServletRequest request) {
        return itineraryService.calculateSegmentTravel(currentUserId(request), id, segmentIndex, req);
    }

    @LoginRequired
    @TrackBehavior(
            eventType = AnalyticsEventTypes.REPLAN_CLICK,
            itineraryIdExpression = "#p0",
            extraExpression = "#p1",
            weight = 1.5D
    )
    @PatchMapping("/{id}/replan")
    public ReplanRespDTO replanItinerary(@PathVariable("id") Long id,
                                         @Valid @RequestBody ReplanReqDTO req,
                                         HttpServletRequest request) {
        return itineraryService.replan(currentUserId(request), id, req);
    }

    @LoginRequired
    @TrackBehavior(
            eventType = AnalyticsEventTypes.POI_REPLACE,
            itineraryIdExpression = "#p0",
            poiIdExpression = "#p1",
            extraExpression = "#p2",
            weight = 1.5D
    )
    @PatchMapping("/{id}/nodes/{poiId}/replacement")
    public ItineraryVO replacePoi(@PathVariable("id") Long id,
                                  @PathVariable("poiId") Long poiId,
                                  @Valid @RequestBody ReplaceReqDTO req,
                                  HttpServletRequest request) {
        return itineraryService.replaceNode(currentUserId(request), id, poiId, req);
    }

    @LoginRequired
    @TrackBehavior(
            eventType = AnalyticsEventTypes.OPTION_SELECT,
            itineraryIdExpression = "#p0",
            optionKeyExpression = "#p1.selectedOptionKey",
            extraExpression = "#p1",
            weight = 2.0D
    )
    @PostMapping("/{id}/selection")
    public ItineraryVO selectOption(@PathVariable("id") Long id,
                                    @Valid @RequestBody OptionSelectReqDTO req,
                                    HttpServletRequest request) {
        return itineraryService.selectOption(currentUserId(request), id, req);
    }

    @LoginRequired
    @TrackBehavior(
            eventType = AnalyticsEventTypes.FAVORITE_ADD,
            itineraryIdExpression = "#p0",
            optionKeyExpression = "#p1?.selectedOptionKey",
            extraExpression = "#p1",
            weight = 3.0D
    )
    @PutMapping("/{id}/favorite")
    public ItineraryVO favoriteItinerary(@PathVariable("id") Long id,
                                         @Valid @RequestBody(required = false) FavoriteReqDTO req,
                                         HttpServletRequest request) {
        return itineraryService.favoriteItinerary(currentUserId(request), id, req);
    }

    @LoginRequired
    @TrackBehavior(
            eventType = AnalyticsEventTypes.FAVORITE_REMOVE,
            itineraryIdExpression = "#p0",
            weight = 1.5D
    )
    @DeleteMapping("/{id}/favorite")
    public ResponseEntity<Void> unfavoriteItinerary(@PathVariable("id") Long id, HttpServletRequest request) {
        itineraryService.unfavoriteItinerary(currentUserId(request), id);
        return ResponseEntity.noContent().build();
    }

    @LoginRequired
    @TrackBehavior(
            eventType = AnalyticsEventTypes.PUBLIC_STATUS_UPDATE,
            itineraryIdExpression = "#p0",
            optionKeyExpression = "#p1?.selectedOptionKey",
            extraExpression = "#p1",
            weight = 3.5D
    )
    @PatchMapping("/{id}/public")
    public ItineraryVO updatePublicStatus(@PathVariable("id") Long id,
                                          @Valid @RequestBody(required = false) PublicStatusReqDTO req,
                                          HttpServletRequest request) {
        return itineraryService.updatePublicStatus(currentUserId(request), id, req);
    }

    private Long currentUserId(HttpServletRequest request) {
        return request == null ? null : (Long) request.getAttribute(AuthConstants.LOGIN_USER_ID);
    }

    private String guardSubject(HttpServletRequest request) {
        Long userId = currentUserId(request);
        if (userId != null) {
            return "user:" + userId;
        }
        String remoteAddr = request == null ? null : request.getRemoteAddr();
        return remoteAddr == null || remoteAddr.trim().isEmpty() ? "anonymous" : "anon:" + remoteAddr.trim();
    }
}
