package com.citytrip.service.skill;

import com.citytrip.model.dto.ChatReqDTO;
import com.citytrip.model.vo.ChatSkillPayloadVO;
import com.citytrip.service.application.itinerary.ChatItineraryGenerateDraftService;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@Order(8)
public class ItineraryGenerateFromChatSkill implements ChatSkillHandler {

    private static final List<String> EXPLICIT_GENERATE_INTENTS = List.of(
            "生成路线",
            "生成一条",
            "规划路线",
            "生成行程",
            "重新生成",
            "换一条路线",
            "来一条路线"
    );
    private static final List<String> CONFIRMATION_PREFIXES = List.of(
            "按刚才",
            "按这个",
            "就这样",
            "照这个",
            "用这个偏好"
    );
    private static final List<String> CONFIRMATION_ACTION_TERMS = List.of(
            "生成",
            "规划",
            "安排"
    );
    private static final List<String> REPLACE_INTENTS = List.of(
            "换成",
            "替换"
    );
    private static final List<String> EDIT_INTENTS = List.of(
            "删除",
            "去掉",
            "移除",
            "少玩",
            "多玩",
            "少待",
            "多待",
            "延长",
            "缩短",
            "加一个",
            "加入",
            "加上",
            "加到",
            "加在",
            "新增",
            "添加",
            "补上",
            "安排",
            "放到",
            "放在",
            "想去",
            "要去",
            "想逛",
            "想看",
            "打卡",
            "顺便去",
            "带上",
            "带我去",
            "一定要去",
            "必须去",
            "多一站",
            "插入"
    );
    private static final List<String> TIME_ANCHORS = List.of(
            "开始",
            "结束",
            "出发"
    );
    private static final List<String> TIME_EDIT_TERMS = List.of(
            "改",
            "调整"
    );

    private final ChatItineraryGenerateDraftService draftService;

    public ItineraryGenerateFromChatSkill(ChatItineraryGenerateDraftService draftService) {
        this.draftService = draftService;
    }

    @Override
    public String skillName() {
        return "itinerary_generate";
    }

    @Override
    public boolean supports(ChatReqDTO req) {
        if (req == null || req.getContext() == null || !StringUtils.hasText(req.getQuestion())) {
            return false;
        }
        if (req.getAction() != null && StringUtils.hasText(req.getAction().getType())) {
            return false;
        }
        String pageType = req.getContext().getPageType();
        if (!StringUtils.hasText(pageType) || !"result".equalsIgnoreCase(pageType.trim())) {
            return false;
        }
        String question = req.getQuestion().trim();
        String normalizedQuestion = question.replaceAll("\\s+", "");
        if (isReplaceOrEditIntent(normalizedQuestion)) {
            return false;
        }
        return containsAny(question, EXPLICIT_GENERATE_INTENTS)
                || (containsAny(question, CONFIRMATION_PREFIXES)
                && containsAny(question, CONFIRMATION_ACTION_TERMS));
    }

    @Override
    public ChatSkillPayloadVO execute(ChatReqDTO req) {
        return draftService.buildDraft(req);
    }

    private boolean containsAny(String text, List<String> keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private boolean isReplaceOrEditIntent(String question) {
        if (containsAny(question, REPLACE_INTENTS) || containsAny(question, EDIT_INTENTS)) {
            return true;
        }
        boolean mentionsTimeField = containsAny(question, TIME_ANCHORS);
        if (!mentionsTimeField) {
            return false;
        }
        return containsAny(question, TIME_EDIT_TERMS) || question.matches(".*\\d{1,2}:\\d{2}.*");
    }
}
