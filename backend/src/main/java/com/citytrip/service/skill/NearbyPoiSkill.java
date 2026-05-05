package com.citytrip.service.skill;

import com.citytrip.model.dto.ChatReqDTO;
import com.citytrip.model.vo.ChatSkillPayloadVO;
import com.citytrip.service.PoiService;
import com.citytrip.service.geo.GeoSearchService;
import com.citytrip.service.geo.PlaceDisambiguationService;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Order(30)
public class NearbyPoiSkill extends AbstractGeoSkill {

    private final GeoSearchService geoSearchService;
    private final PoiService poiService;
    private final PlaceDisambiguationService placeDisambiguationService;

    public NearbyPoiSkill(GeoSearchService geoSearchService,
                          PoiService poiService,
                          PlaceDisambiguationService placeDisambiguationService) {
        this.geoSearchService = geoSearchService;
        this.poiService = poiService;
        this.placeDisambiguationService = placeDisambiguationService;
    }

    @Override
    public String skillName() {
        return "nearby_poi";
    }

    @Override
    public boolean supports(ChatReqDTO req) {
        String question = questionOf(req);
        return containsAny(question, "附近", "周边", "旁边")
                && containsAny(question, "景点", "好玩", "逛", "玩");
    }

    @Override
    public ChatSkillPayloadVO execute(ChatReqDTO req) {
        String city = cityOf(req);
        String anchor = extractAnchorKeyword(questionOf(req));
        AnchorResolution anchorResolution = resolveAnchor(anchor, city, poiService, placeDisambiguationService);
        if (anchorResolution.clarificationRequired()) {
            return buildClarificationPayload(skillName(), "nearby_poi", city, anchorResolution.anchor(), "景点", 5, 1500,
                    anchorResolution.source(), anchorResolution.clarificationQuestion());
        }

        List<ChatSkillPayloadVO.ResultItem> items = anchorResolution.center() == null
                ? List.of()
                : fromGeoCandidates(geoSearchService.searchNearby(anchorResolution.center(), city, "景点", 1500, 5));
        String source = items.isEmpty() ? "local-db" : items.get(0).getSource();
        String fallback = items.isEmpty()
                ? "我暂时没找到附近景点结果，你可以换一个地标或缩小范围。"
                : "我先把实时查到的附近景点列给你，例如" + items.get(0).getName() + "。";
        return buildPayload(skillName(), "nearby_poi", city, anchor, "景点", 5, 1500, items, source, fallback);
    }
}