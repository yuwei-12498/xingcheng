package com.citytrip.service.skill;

import com.citytrip.model.dto.ChatReqDTO;
import com.citytrip.model.vo.ChatSkillPayloadVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

@Service
public class SkillRouterService {
    private static final Logger log = LoggerFactory.getLogger(SkillRouterService.class);
    private final List<ChatSkillHandler> handlers;

    public SkillRouterService(List<ChatSkillHandler> handlers) {
        this.handlers = handlers == null ? List.of() : List.copyOf(handlers);
    }

    public Optional<ChatSkillPayloadVO> route(ChatReqDTO req) {
        if (req == null || (!StringUtils.hasText(req.getQuestion()) && (req.getAction() == null || !StringUtils.hasText(req.getAction().getType())))) {
            log.info("Skill router skipped because question/action is empty.");
            return Optional.empty();
        }
        String question = req.getQuestion() == null ? "" : req.getQuestion().trim();
        log.info("Skill router evaluating request. question='{}', handlerCount={}", question, handlers.size());
        for (ChatSkillHandler handler : handlers) {
            boolean supports = handler.supports(req);
            log.info("Skill router handler check. handler={}, supports={}", handler.skillName(), supports);
            if (supports) {
                ChatSkillPayloadVO payload = handler.execute(req);
                log.info("Skill router matched handler={}. payloadStatus={}, resultCount={}",
                        handler.skillName(),
                        payload == null ? "null" : payload.getStatus(),
                        payload == null || payload.getResults() == null ? 0 : payload.getResults().size());
                return payload == null ? Optional.empty() : Optional.of(payload);
            }
        }
        log.info("Skill router found no matching handler. question='{}'", question);
        return Optional.empty();
    }
}
