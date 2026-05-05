package com.citytrip.service.application.itinerary;

import com.citytrip.assembler.ItineraryComparisonAssembler;
import com.citytrip.common.BadRequestException;
import com.citytrip.model.dto.ChatReqDTO;
import com.citytrip.model.dto.GenerateReqDTO;
import com.citytrip.model.entity.Poi;
import com.citytrip.model.vo.ItineraryNodeVO;
import com.citytrip.model.vo.ItineraryVO;
import com.citytrip.model.vo.PoiSearchResultVO;
import com.citytrip.service.domain.ai.ItineraryAiDecorationService;
import com.citytrip.service.impl.ItineraryRouteOptimizer;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class ItineraryReplacementExecutionService {

    private final ItineraryRouteOptimizer routeOptimizer;
    private final ItineraryComparisonAssembler itineraryComparisonAssembler;
    private final ItineraryAiDecorationService itineraryAiDecorationService;
    private final SavedItineraryCommandService savedItineraryCommandService;

    public ItineraryReplacementExecutionService(ItineraryRouteOptimizer routeOptimizer,
                                               ItineraryComparisonAssembler itineraryComparisonAssembler,
                                               ItineraryAiDecorationService itineraryAiDecorationService,
                                               SavedItineraryCommandService savedItineraryCommandService) {
        this.routeOptimizer = routeOptimizer;
        this.itineraryComparisonAssembler = itineraryComparisonAssembler;
        this.itineraryAiDecorationService = itineraryAiDecorationService;
        this.savedItineraryCommandService = savedItineraryCommandService;
    }

    public ItineraryVO execute(Long userId,
                               Long itineraryId,
                               ChatReplacementSessionStore.PendingProposal proposal,
                               List<ChatReqDTO.ChatRouteNode> currentNodes,
                               GenerateReqDTO originalReq) {
        if (proposal == null) {
            throw new BadRequestException("\u7f3a\u5c11\u53ef\u6267\u884c\u7684\u66ff\u6362\u65b9\u6848\u3002");
        }
        List<ChatReqDTO.ChatRouteNode> safeNodes = currentNodes == null ? Collections.emptyList() : currentNodes;
        if (safeNodes.isEmpty()) {
            throw new BadRequestException("\u5f53\u524d\u6ca1\u6709\u53ef\u66ff\u6362\u7684\u884c\u7a0b\u8282\u70b9\u3002");
        }
        List<PoiSearchResultVO> candidates = proposal.getCandidates() == null ? Collections.emptyList() : proposal.getCandidates();
        if (candidates.isEmpty()) {
            throw new BadRequestException("\u5f53\u524d\u66ff\u6362\u65b9\u6848\u6ca1\u6709\u53ef\u7528\u7684\u76ee\u6807\u5730\u70b9\u3002");
        }

        GenerateReqDTO normalized = routeOptimizer.normalizeRequest(originalReq);
        List<Poi> currentPois = materializeCurrentPois(safeNodes, normalized);
        List<Poi> rewritten = new ArrayList<>();
        for (Poi poi : currentPois) {
            if (shouldReplace(poi, proposal)) {
                continue;
            }
            rewritten.add(poi);
        }
        rewritten.add(materializeCandidate(candidates.get(Math.max(0, Math.min(proposal.getCandidateIndex(), candidates.size() - 1))), normalized));

        List<Poi> prepared = routeOptimizer.prepareCandidates(rewritten, normalized, false);
        ItineraryRouteOptimizer.RouteOption best = routeOptimizer.bestRoute(prepared, normalized, prepared.size());
        if (best == null || best.path() == null || best.path().isEmpty()) {
            throw new BadRequestException("\u5f53\u524d\u66ff\u6362\u65b9\u6848\u65e0\u6cd5\u751f\u6210\u53ef\u6267\u884c\u884c\u7a0b\u3002");
        }

        ItineraryVO itinerary = itineraryComparisonAssembler.buildComparedItinerary(
                List.of(best),
                normalized,
                reasonMap(safeNodes),
                null,
                Collections.emptySet()
        );
        itinerary.setRecommendReason("\u5df2\u6309\u4f60\u7684\u8981\u6c42\u5b8c\u6210\u66ff\u6362\uff0c\u65b0\u8def\u7ebf\u4f1a\u56f4\u7ed5\u5f53\u524d\u8282\u594f\u91cd\u65b0\u6574\u7406\u3002");
        itineraryAiDecorationService.applyWarmTips(itinerary, normalized);
        return savedItineraryCommandService.save(userId, itineraryId, normalized, itinerary);
    }

    private boolean shouldReplace(Poi poi, ChatReplacementSessionStore.PendingProposal proposal) {
        if (poi == null || proposal == null) {
            return false;
        }
        if (poi.getId() != null && proposal.getTargetPoiIds() != null && proposal.getTargetPoiIds().contains(poi.getId())) {
            return true;
        }
        return StringUtils.hasText(poi.getName())
                && proposal.getTargetPoiNames() != null
                && proposal.getTargetPoiNames().stream().anyMatch(name -> Objects.equals(name, poi.getName()));
    }

    private List<Poi> materializeCurrentPois(List<ChatReqDTO.ChatRouteNode> nodes, GenerateReqDTO req) {
        List<Poi> pois = new ArrayList<>();
        for (ChatReqDTO.ChatRouteNode node : nodes) {
            if (node == null || !StringUtils.hasText(node.getPoiName())) {
                continue;
            }
            Poi poi = new Poi();
            poi.setId(node.getPoiId());
            poi.setCityCode(req == null ? null : req.getCityCode());
            poi.setCityName(req == null ? null : req.getCityName());
            poi.setName(node.getPoiName().trim());
            poi.setCategory(node.getCategory());
            poi.setDistrict(node.getDistrict());
            poi.setLatitude(node.getLatitude());
            poi.setLongitude(node.getLongitude());
            poi.setOpenTime(LocalTime.of(9, 0));
            poi.setCloseTime(LocalTime.of(21, 0));
            poi.setAvgCost(BigDecimal.ZERO);
            poi.setStayDuration(90);
            poi.setIndoor(0);
            poi.setNightAvailable(0);
            poi.setRainFriendly(0);
            poi.setWalkingLevel("medium");
            poi.setTags(node.getCategory());
            poi.setSuitableFor("");
            poi.setDescription(node.getPoiName());
            poi.setPriorityScore(new BigDecimal("3.0"));
            poi.setCrowdPenalty(BigDecimal.ZERO);
            poi.setSourceType(StringUtils.hasText(node.getSourceType()) ? node.getSourceType() : "local");
            pois.add(poi);
        }
        return pois;
    }

    private Poi materializeCandidate(PoiSearchResultVO result, GenerateReqDTO req) {
        Poi poi = new Poi();
        poi.setId(buildTemporaryPoiId(result));
        poi.setCityCode(req == null ? null : req.getCityCode());
        poi.setCityName(StringUtils.hasText(result.cityName()) ? result.cityName() : (req == null ? null : req.getCityName()));
        poi.setName(result.name());
        poi.setCategory(result.category());
        poi.setDistrict(null);
        poi.setAddress(result.address());
        poi.setLatitude(result.latitude());
        poi.setLongitude(result.longitude());
        poi.setOpenTime(LocalTime.of(9, 0));
        poi.setCloseTime(LocalTime.of(21, 0));
        poi.setAvgCost(BigDecimal.valueOf(60));
        poi.setStayDuration(90);
        poi.setIndoor(0);
        poi.setNightAvailable(0);
        poi.setRainFriendly(0);
        poi.setWalkingLevel("medium");
        poi.setTags(result.category());
        poi.setSuitableFor("");
        poi.setDescription(result.name());
        poi.setPriorityScore(new BigDecimal("3.2"));
        poi.setCrowdPenalty(new BigDecimal("0.2"));
        poi.setSourceType(StringUtils.hasText(result.source()) ? "external" : "local");
        return poi;
    }

    private long buildTemporaryPoiId(PoiSearchResultVO result) {
        String seed = (result.name() == null ? "" : result.name())
                + "|"
                + (result.latitude() == null ? "" : result.latitude().toPlainString())
                + "|"
                + (result.longitude() == null ? "" : result.longitude().toPlainString());
        long hash = Integer.toUnsignedLong(seed.hashCode());
        return -(20_000_000L + hash);
    }

    private Map<Long, String> reasonMap(List<ChatReqDTO.ChatRouteNode> nodes) {
        Map<Long, String> map = new HashMap<>();
        for (ChatReqDTO.ChatRouteNode node : nodes) {
            if (node != null && node.getPoiId() != null && StringUtils.hasText(node.getPoiName())) {
                map.put(node.getPoiId(), node.getPoiName());
            }
        }
        return map;
    }
}
