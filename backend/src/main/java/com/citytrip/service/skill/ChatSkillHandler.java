package com.citytrip.service.skill;

import com.citytrip.model.dto.ChatReqDTO;
import com.citytrip.model.vo.ChatSkillPayloadVO;

public interface ChatSkillHandler {

    String skillName();

    boolean supports(ChatReqDTO req);

    ChatSkillPayloadVO execute(ChatReqDTO req);
}