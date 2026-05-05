package com.citytrip.service;

import com.citytrip.model.dto.ChatReqDTO;
import com.citytrip.model.vo.ChatStatusVO;
import com.citytrip.model.vo.ChatVO;
import java.util.function.Consumer;

/**
 * 旅游助手问答服务
 */
public interface ChatService {
    ChatVO answerQuestion(ChatReqDTO req);

    default ChatVO streamAnswer(ChatReqDTO req, Consumer<String> tokenConsumer) {
        ChatVO vo = answerQuestion(req);
        if (vo != null && vo.getAnswer() != null && tokenConsumer != null) {
            tokenConsumer.accept(vo.getAnswer());
        }
        return vo;
    }

    ChatStatusVO getStatus();
}
