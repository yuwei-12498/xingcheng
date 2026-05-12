package com.citytrip.service;

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
import com.citytrip.model.vo.CommunityCommentVO;
import com.citytrip.model.vo.CommunityItineraryDetailVO;
import com.citytrip.model.vo.CommunityItineraryPageVO;
import com.citytrip.model.vo.ItinerarySummaryVO;
import com.citytrip.model.vo.ItineraryVO;

import java.util.List;

public interface ItineraryService {
    ItineraryVO generateUserItinerary(Long userId, GenerateReqDTO req);

    ItineraryVO replaceNode(Long userId, Long itineraryId, Long targetPoiId, ReplaceReqDTO req);

    ReplanRespDTO replan(Long userId, Long itineraryId, ReplanReqDTO req);

    ItineraryVO getLatestItinerary(Long userId);

    ItineraryVO getItinerary(Long userId, Long itineraryId);

    default ItineraryVO calculateSegmentTravel(Long userId, Long itineraryId, Integer segmentIndex) {
        return calculateSegmentTravel(userId, itineraryId, segmentIndex, null);
    }

    ItineraryVO calculateSegmentTravel(Long userId, Long itineraryId, Integer segmentIndex, SegmentTravelReqDTO req);

    List<ItinerarySummaryVO> listItineraries(Long userId, boolean favoriteOnly, Integer limit);

    List<ItinerarySummaryVO> listProfileItineraries(Long userId, String type, Integer limit);

    CommunityItineraryPageVO listCommunityItineraries(int page, int size);

    CommunityItineraryPageVO listCommunityItineraries(int page, int size, String sort, String keyword, String theme, Long currentUserId);

    CommunityItineraryDetailVO getCommunityItinerary(Long itineraryId, Long currentUserId);

    List<CommunityCommentVO> listCommunityComments(Long itineraryId, Long currentUserId);

    CommunityCommentVO addCommunityComment(Long userId, Long itineraryId, CommunityCommentReqDTO req);

    CommunityItineraryDetailVO likeCommunityItinerary(Long userId, Long itineraryId);

    CommunityItineraryDetailVO unlikeCommunityItinerary(Long userId, Long itineraryId);

    CommunityItineraryDetailVO pinCommunityComment(Long userId, Long itineraryId, Long commentId);

    void deleteCommunityPost(Long userId, Long itineraryId);

    ItineraryVO selectOption(Long userId, Long itineraryId, OptionSelectReqDTO req);

    ItineraryVO favoriteItinerary(Long userId, Long itineraryId, FavoriteReqDTO req);

    ItineraryVO saveCommunityItinerary(Long userId, SaveItineraryReqDTO req);

    void unfavoriteItinerary(Long userId, Long itineraryId);

    ItineraryVO updatePublicStatus(Long userId, Long itineraryId, PublicStatusReqDTO req);
}
