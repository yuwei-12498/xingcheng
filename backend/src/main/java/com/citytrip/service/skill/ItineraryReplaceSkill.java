package com.citytrip.service.skill;

import com.citytrip.model.dto.ChatReqDTO;
import com.citytrip.model.vo.ChatSkillPayloadVO;
import com.citytrip.service.application.itinerary.ChatReplacementWorkflowService;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Order(15)
public class ItineraryReplaceSkill implements ChatSkillHandler {
    private final ChatReplacementWorkflowService workflowService;

    public ItineraryReplaceSkill(ChatReplacementWorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    @Override
    public String skillName() {
        return "itinerary_replace";
    }

    @Override
    public boolean supports(ChatReqDTO req) {
        if (req != null && req.getAction() != null && StringUtils.hasText(req.getAction().getType())) {
            return "regenerate_replacement".equalsIgnoreCase(req.getAction().getType());
        }
        if (req == null
                || req.getContext() == null
                || req.getContext().getItinerary() == null
                || req.getContext().getItinerary().getNodes() == null
                || req.getContext().getItinerary().getNodes().isEmpty()) {
            return false;
        }
        String question = req.getQuestion();
        if (!StringUtils.hasText(question)) {
            return false;
        }
        String normalized = question.trim();
        return normalized.contains("\u6362\u6210")
                || normalized.contains("\u66ff\u6362")
                || normalized.startsWith("\u6211\u60f3\u53bb")
                || normalized.startsWith("\u60f3\u53bb")
                || normalized.startsWith("\u6211\u60f3\u901b")
                || normalized.startsWith("\u60f3\u901b")
                || normalized.startsWith("\u53bb")
                || normalized.startsWith("\u770b\u770b")
                || normalized.startsWith("\u6253\u5361");
    }

    @Override
    public ChatSkillPayloadVO execute(ChatReqDTO req) {
        return workflowService.handle(req);
    }
}
