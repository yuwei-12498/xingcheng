package com.citytrip.service.impl;

import com.citytrip.model.dto.CommunityCommentReqDTO;
import com.citytrip.model.dto.FavoriteReqDTO;
import com.citytrip.model.dto.GenerateReqDTO;
import com.citytrip.model.dto.OptionSelectReqDTO;
import com.citytrip.model.dto.PublicStatusReqDTO;
import com.citytrip.model.dto.ReplaceReqDTO;
import com.citytrip.model.dto.ReplanReqDTO;
import com.citytrip.model.dto.ReplanRespDTO;
import com.citytrip.model.dto.SaveItineraryReqDTO;
import com.citytrip.model.vo.CommunityCommentVO;
import com.citytrip.model.vo.CommunityItineraryDetailVO;
import com.citytrip.model.vo.CommunityItineraryPageVO;
import com.citytrip.model.vo.ItinerarySummaryVO;
import com.citytrip.model.vo.ItineraryVO;
import com.citytrip.service.ItineraryService;
import com.citytrip.service.application.community.CommunityInteractionService;
import com.citytrip.service.application.community.CommunityItineraryQueryService;
import com.citytrip.service.application.itinerary.GenerateItineraryUseCase;
import com.citytrip.service.application.itinerary.ItinerarySegmentTravelUseCase;
import com.citytrip.service.application.itinerary.ItineraryQueryService;
import com.citytrip.service.application.itinerary.ReplacePoiUseCase;
import com.citytrip.service.application.itinerary.ReplanItineraryUseCase;
import com.citytrip.service.application.itinerary.SavedItineraryCommandService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ItineraryServiceImpl implements ItineraryService {

    private final GenerateItineraryUseCase generateItineraryUseCase;
    private final ReplacePoiUseCase replacePoiUseCase;
    private final ReplanItineraryUseCase replanItineraryUseCase;
    private final ItinerarySegmentTravelUseCase itinerarySegmentTravelUseCase;
    private final ItineraryQueryService itineraryQueryService;
    private final SavedItineraryCommandService savedItineraryCommandService;
    private final CommunityItineraryQueryService communityItineraryQueryService;
    private final CommunityInteractionService communityInteractionService;

    public ItineraryServiceImpl(GenerateItineraryUseCase generateItineraryUseCase,
                                ReplacePoiUseCase replacePoiUseCase,
                                ReplanItineraryUseCase replanItineraryUseCase,
                                ItinerarySegmentTravelUseCase itinerarySegmentTravelUseCase,
                                ItineraryQueryService itineraryQueryService,
                                SavedItineraryCommandService savedItineraryCommandService,
                                CommunityItineraryQueryService communityItineraryQueryService,
                                CommunityInteractionService communityInteractionService) {
        this.generateItineraryUseCase = generateItineraryUseCase;
        this.replacePoiUseCase = replacePoiUseCase;
        this.replanItineraryUseCase = replanItineraryUseCase;
        this.itinerarySegmentTravelUseCase = itinerarySegmentTravelUseCase;
        this.itineraryQueryService = itineraryQueryService;
        this.savedItineraryCommandService = savedItineraryCommandService;
        this.communityItineraryQueryService = communityItineraryQueryService;
        this.communityInteractionService = communityInteractionService;
    }

    @Override
    public ItineraryVO generateUserItinerary(Long userId, GenerateReqDTO req) {
        return generateItineraryUseCase.generate(userId, req);
    }

    @Override
    public ItineraryVO replaceNode(Long userId, Long itineraryId, Long targetPoiId, ReplaceReqDTO req) {
        return replacePoiUseCase.replace(userId, itineraryId, targetPoiId, req);
    }

    @Override
    public ReplanRespDTO replan(Long userId, Long itineraryId, ReplanReqDTO req) {
        return replanItineraryUseCase.replan(userId, itineraryId, req);
    }

    @Override
    public ItineraryVO getLatestItinerary(Long userId) {
        return itineraryQueryService.getLatest(userId);
    }

    @Override
    public ItineraryVO getItinerary(Long userId, Long itineraryId) {
        return itineraryQueryService.get(userId, itineraryId);
    }

    @Override
    public ItineraryVO calculateSegmentTravel(Long userId, Long itineraryId, Integer segmentIndex) {
        return itinerarySegmentTravelUseCase.calculate(userId, itineraryId, segmentIndex);
    }

    @Override
    public List<ItinerarySummaryVO> listItineraries(Long userId, boolean favoriteOnly, Integer limit) {
        return itineraryQueryService.list(userId, favoriteOnly, limit);
    }

    @Override
    public List<ItinerarySummaryVO> listProfileItineraries(Long userId, String type, Integer limit) {
        return itineraryQueryService.listProfile(userId, type, limit);
    }

    @Override
    public CommunityItineraryPageVO listCommunityItineraries(int page, int size) {
        return communityItineraryQueryService.listPublic(page, size);
    }

    @Override
    public CommunityItineraryPageVO listCommunityItineraries(int page, int size, String sort, String keyword, String theme, Long currentUserId) {
        return communityItineraryQueryService.listPublic(page, size, sort, keyword, theme, currentUserId);
    }

    @Override
    public CommunityItineraryDetailVO getCommunityItinerary(Long itineraryId, Long currentUserId) {
        return communityItineraryQueryService.getPublicDetail(itineraryId, currentUserId);
    }

    @Override
    public List<CommunityCommentVO> listCommunityComments(Long itineraryId, Long currentUserId) {
        return communityInteractionService.listComments(itineraryId, currentUserId);
    }

    @Override
    public CommunityCommentVO addCommunityComment(Long userId, Long itineraryId, CommunityCommentReqDTO req) {
        return communityInteractionService.addComment(userId, itineraryId, req);
    }

    @Override
    public CommunityItineraryDetailVO likeCommunityItinerary(Long userId, Long itineraryId) {
        return communityInteractionService.like(userId, itineraryId);
    }

    @Override
    public CommunityItineraryDetailVO unlikeCommunityItinerary(Long userId, Long itineraryId) {
        return communityInteractionService.unlike(userId, itineraryId);
    }

    @Override
    public CommunityItineraryDetailVO pinCommunityComment(Long userId, Long itineraryId, Long commentId) {
        return communityInteractionService.pinComment(userId, itineraryId, commentId);
    }

    @Override
    public void deleteCommunityPost(Long userId, Long itineraryId) {
        communityInteractionService.deletePost(userId, itineraryId);
    }

    @Override
    public ItineraryVO selectOption(Long userId, Long itineraryId, OptionSelectReqDTO req) {
        return savedItineraryCommandService.selectOption(userId, itineraryId, req);
    }

    @Override
    public ItineraryVO favoriteItinerary(Long userId, Long itineraryId, FavoriteReqDTO req) {
        return savedItineraryCommandService.favorite(userId, itineraryId, req);
    }

    @Override
    public ItineraryVO saveCommunityItinerary(Long userId, SaveItineraryReqDTO req) {
        return savedItineraryCommandService.saveFromPublic(userId, req);
    }

    @Override
    public void unfavoriteItinerary(Long userId, Long itineraryId) {
        savedItineraryCommandService.unfavorite(userId, itineraryId);
    }

    @Override
    public ItineraryVO updatePublicStatus(Long userId, Long itineraryId, PublicStatusReqDTO req) {
        return savedItineraryCommandService.updatePublicStatus(userId, itineraryId, req);
    }
}
