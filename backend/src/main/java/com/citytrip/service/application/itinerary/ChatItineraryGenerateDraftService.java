package com.citytrip.service.application.itinerary;

import com.citytrip.model.dto.ChatReqDTO;
import com.citytrip.model.dto.GenerateReqDTO;
import com.citytrip.model.dto.SmartFillReqDTO;
import com.citytrip.model.vo.ChatSkillPayloadVO;
import com.citytrip.model.vo.SmartFillVO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class ChatItineraryGenerateDraftService {

    private static final String SKILL_NAME = "itinerary_generate";
    private static final String WORKFLOW_TYPE = "itinerary_generate";
    private static final String SOURCE = "result_page_conversation";
    private static final int MAX_SMART_FILL_TEXT_CHARS = 1000;
    private static final Pattern ISO_DATE_PATTERN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");

    private final SmartFillUseCase smartFillUseCase;

    public ChatItineraryGenerateDraftService(SmartFillUseCase smartFillUseCase) {
        this.smartFillUseCase = smartFillUseCase;
    }

    public ChatSkillPayloadVO buildDraft(ChatReqDTO req) {
        GenerateReqDTO base = req == null || req.getContext() == null ? null : req.getContext().getOriginalReq();
        if (!hasBaseRequiredFields(base)) {
            return clarificationPayload();
        }

        SmartFillReqDTO smartFillReq = new SmartFillReqDTO();
        smartFillReq.setText(buildExtractionText(req, base));
        SmartFillVO parsed = smartFillUseCase.parse(smartFillReq);

        GenerateReqDTO draft = mergeDraft(base, parsed);
        ChatSkillPayloadVO payload = basePayload();
        payload.setStatus("proposal_ready");
        payload.setWorkflowState("proposal_ready");
        payload.setGenerateDraft(draft);
        payload.setGenerateSummary(resolveGenerateSummary(parsed, draft));
        payload.setFallbackMessage("我把这次对话整理成新的路线生成草稿，确认后可以直接生成路线。");
        payload.setActions(List.of(
                action("confirm_itinerary_generate", "生成路线", "primary"),
                action("continue_itinerary_generate", "继续补充", "secondary")
        ));
        payload.setEvidence(List.of(
                "source=result-page conversation",
                "recentMessages=" + (req == null || req.getRecentMessages() == null ? 0 : req.getRecentMessages().size()),
                "baseOriginalReq=present"
        ));
        return payload;
    }

    private ChatSkillPayloadVO clarificationPayload() {
        ChatSkillPayloadVO payload = basePayload();
        payload.setStatus("clarification_required");
        payload.setWorkflowState("clarification_required");
        payload.setGenerateDraft(null);
        payload.setGenerateSummary(List.of());
        payload.setFallbackMessage("当前路线缺少基础参数，请先补充城市、日期和起止时间后，我再帮你把对话整理成生成路线草稿。");
        payload.setActions(List.of(action("continue_itinerary_generate", "继续补充", "secondary")));
        payload.setEvidence(List.of("baseOriginalReq=missing_or_incomplete"));
        return payload;
    }

    private ChatSkillPayloadVO basePayload() {
        ChatSkillPayloadVO payload = new ChatSkillPayloadVO();
        payload.setSkillName(SKILL_NAME);
        payload.setIntent(WORKFLOW_TYPE);
        payload.setMessageType("workflow");
        payload.setWorkflowType(WORKFLOW_TYPE);
        payload.setSource(SOURCE);
        return payload;
    }

    private boolean hasBaseRequiredFields(GenerateReqDTO base) {
        return base != null
                && (StringUtils.hasText(base.getCityName()) || StringUtils.hasText(base.getCityCode()))
                && StringUtils.hasText(base.getTripDate())
                && StringUtils.hasText(base.getStartTime())
                && StringUtils.hasText(base.getEndTime());
    }

    private String buildExtractionText(ChatReqDTO req, GenerateReqDTO base) {
        StringBuilder builder = new StringBuilder();
        builder.append("基础路线参数：")
                .append("城市=").append(nullToBlank(base.getCityName()))
                .append("，cityCode=").append(nullToBlank(base.getCityCode()))
                .append("，日期=").append(nullToBlank(base.getTripDate()))
                .append("，天数=").append(base.getTripDays())
                .append("，时间=").append(nullToBlank(base.getStartTime())).append("-").append(nullToBlank(base.getEndTime()))
                .append("，预算等级=").append(nullToBlank(base.getBudgetLevel()))
                .append("，总预算=").append(base.getTotalBudget())
                .append("，主题=").append(join(base.getThemes()))
                .append("，必去=").append(join(base.getMustVisitPoiNames()))
                .append("，出发地=").append(nullToBlank(base.getDeparturePlaceName()))
                .append("\n");

        builder.append("最近对话：");
        if (req != null && req.getRecentMessages() != null) {
            for (ChatReqDTO.ChatMessage message : req.getRecentMessages()) {
                if (message == null || !StringUtils.hasText(message.getContent())) {
                    continue;
                }
                builder.append("\n")
                        .append(nullToBlank(message.getRole()))
                        .append(": ")
                        .append(message.getContent().trim());
            }
        }
        builder.append("\n当前问题：")
                .append(req == null ? "" : nullToBlank(req.getQuestion()));

        String text = builder.toString();
        if (text.length() <= MAX_SMART_FILL_TEXT_CHARS) {
            return text;
        }
        return text.substring(text.length() - MAX_SMART_FILL_TEXT_CHARS);
    }

    private GenerateReqDTO mergeDraft(GenerateReqDTO base, SmartFillVO parsed) {
        GenerateReqDTO draft = copyBase(base);
        if (parsed == null) {
            return draft;
        }
        if (StringUtils.hasText(parsed.getCityName())) {
            draft.setCityName(parsed.getCityName().trim());
        }
        if (parsed.getTripDays() != null) {
            draft.setTripDays(parsed.getTripDays());
        }
        if (isIsoDate(parsed.getTripDate())) {
            draft.setTripDate(parsed.getTripDate().trim());
        }
        if (StringUtils.hasText(parsed.getStartTime())) {
            draft.setStartTime(parsed.getStartTime().trim());
        }
        if (StringUtils.hasText(parsed.getEndTime())) {
            draft.setEndTime(parsed.getEndTime().trim());
        }
        if (StringUtils.hasText(parsed.getBudgetLevel())) {
            draft.setBudgetLevel(parsed.getBudgetLevel().trim());
        }
        if (parsed.getIsRainy() != null) {
            draft.setIsRainy(parsed.getIsRainy());
        }
        if (parsed.getIsNight() != null) {
            draft.setIsNight(parsed.getIsNight());
        }
        if (StringUtils.hasText(parsed.getWalkingLevel())) {
            draft.setWalkingLevel(parsed.getWalkingLevel().trim());
        }
        if (StringUtils.hasText(parsed.getCompanionType())) {
            draft.setCompanionType(parsed.getCompanionType().trim());
        }
        draft.setThemes(mergeDistinct(base.getThemes(), parsed.getThemes()));
        draft.setMustVisitPoiNames(mergeDistinct(base.getMustVisitPoiNames(), parsed.getMustVisitPoiNames()));
        if (StringUtils.hasText(parsed.getDepartureText())) {
            draft.setDeparturePlaceName(parsed.getDepartureText().trim());
        }
        if (parsed.getDepartureLatitude() != null) {
            draft.setDepartureLatitude(parsed.getDepartureLatitude());
        }
        if (parsed.getDepartureLongitude() != null) {
            draft.setDepartureLongitude(parsed.getDepartureLongitude());
        }
        return draft;
    }

    private GenerateReqDTO copyBase(GenerateReqDTO base) {
        GenerateReqDTO copy = new GenerateReqDTO();
        copy.setCityName(base.getCityName());
        copy.setCityCode(base.getCityCode());
        copy.setTripDays(base.getTripDays());
        copy.setTripDate(base.getTripDate());
        copy.setTotalBudget(base.getTotalBudget());
        copy.setBudgetLevel(base.getBudgetLevel());
        copy.setThemes(copyList(base.getThemes()));
        copy.setIsRainy(base.getIsRainy());
        copy.setIsNight(base.getIsNight());
        copy.setWalkingLevel(base.getWalkingLevel());
        copy.setCompanionType(base.getCompanionType());
        copy.setStartTime(base.getStartTime());
        copy.setEndTime(base.getEndTime());
        copy.setMustVisitPoiNames(copyList(base.getMustVisitPoiNames()));
        copy.setDeparturePlaceName(base.getDeparturePlaceName());
        copy.setDepartureLatitude(base.getDepartureLatitude());
        copy.setDepartureLongitude(base.getDepartureLongitude());
        return copy;
    }

    private List<String> mergeDistinct(List<String> base, List<String> parsed) {
        Set<String> values = new LinkedHashSet<>();
        addAllTrimmed(values, base);
        addAllTrimmed(values, parsed);
        return new ArrayList<>(values);
    }

    private void addAllTrimmed(Set<String> values, List<String> source) {
        if (source == null) {
            return;
        }
        for (String item : source) {
            if (StringUtils.hasText(item)) {
                values.add(item.trim());
            }
        }
    }

    private List<String> resolveGenerateSummary(SmartFillVO parsed, GenerateReqDTO draft) {
        Set<String> summary = new LinkedHashSet<>();
        if (parsed != null) {
            addAllTrimmed(summary, parsed.getSummary());
        }
        if (summary.isEmpty() && draft.getMustVisitPoiNames() != null && !draft.getMustVisitPoiNames().isEmpty()) {
            summary.add("必去：" + String.join("、", draft.getMustVisitPoiNames()));
        }
        if (summary.isEmpty() && draft.getThemes() != null && !draft.getThemes().isEmpty()) {
            summary.add("主题：" + String.join("、", draft.getThemes()));
        }
        return new ArrayList<>(summary);
    }

    private boolean isIsoDate(String value) {
        return StringUtils.hasText(value) && ISO_DATE_PATTERN.matcher(value.trim()).matches();
    }

    private ChatSkillPayloadVO.ActionItem action(String key, String label, String style) {
        ChatSkillPayloadVO.ActionItem action = new ChatSkillPayloadVO.ActionItem();
        action.setKey(key);
        action.setLabel(label);
        action.setStyle(style);
        return action;
    }

    private List<String> copyList(List<String> source) {
        return source == null ? null : new ArrayList<>(source);
    }

    private String join(List<String> items) {
        return items == null || items.isEmpty() ? "" : String.join("、", items);
    }

    private String nullToBlank(String value) {
        return value == null ? "" : value;
    }
}
