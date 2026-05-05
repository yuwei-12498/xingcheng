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
@Order(20)
public class NearbyHotelSkill extends AbstractGeoSkill {

    private final GeoSearchService geoSearchService;
    private final PoiService poiService;
    private final PlaceDisambiguationService placeDisambiguationService;

    public NearbyHotelSkill(GeoSearchService geoSearchService,
                            PoiService poiService,
                            PlaceDisambiguationService placeDisambiguationService) {
        this.geoSearchService = geoSearchService;
        this.poiService = poiService;
        this.placeDisambiguationService = placeDisambiguationService;
    }

    @Override
    public String skillName() {
        return "nearby_hotel";
    }

    @Override
    public boolean supports(ChatReqDTO req) {
        String question = questionOf(req);
        return containsAny(question, "酒店", "住宿", "住哪里")
                && containsAny(question, "附近", "周边", "旁边");
    }

    @Override
    public ChatSkillPayloadVO execute(ChatReqDTO req) {
        String city = cityOf(req);
        String anchor = extractAnchorKeyword(questionOf(req));
        AnchorResolution anchorResolution = resolveAnchor(anchor, city, poiService, placeDisambiguationService);
        if (anchorResolution.clarificationRequired()) {
            return buildClarificationPayload(skillName(), "nearby_hotel", city, anchorResolution.anchor(), "酒店", 5, 1500,
                    anchorResolution.source(), anchorResolution.clarificationQuestion());
        }

        List<ChatSkillPayloadVO.ResultItem> items = anchorResolution.center() == null
                ? List.of()
                : fromGeoCandidates(geoSearchService.searchNearby(anchorResolution.center(), city, "酒店", 1500, 5));
        String source = items.isEmpty() ? "local-db" : items.get(0).getSource();
        String fallback = items.isEmpty()
                ? "我暂时没找到附近酒店结果，你可以换一个地标或缩小范围。"
                : "我先把实时查到的附近酒店列给你，例如" + items.get(0).getName() + "。";
        return buildPayload(skillName(), "nearby_hotel", city, anchor, "酒店", 5, 1500, items, source, fallback);
    }
}