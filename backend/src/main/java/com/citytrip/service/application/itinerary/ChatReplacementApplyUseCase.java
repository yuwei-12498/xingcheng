package com.citytrip.service.application.itinerary;

import com.citytrip.common.BadRequestException;
import com.citytrip.model.dto.ChatReplacementApplyReqDTO;
import com.citytrip.model.vo.ItineraryVO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ChatReplacementApplyUseCase {

    private final ChatReplacementSessionStore sessionStore;
    private final ItineraryReplacementExecutionService executionService;

    public ChatReplacementApplyUseCase(ChatReplacementSessionStore sessionStore,
                                       ItineraryReplacementExecutionService executionService) {
        this.sessionStore = sessionStore;
        this.executionService = executionService;
    }

    public ItineraryVO apply(Long userId, Long itineraryId, ChatReplacementApplyReqDTO req) {
        if (req == null || !StringUtils.hasText(req.getClientSessionId()) || !StringUtils.hasText(req.getProposalToken())) {
            throw new BadRequestException("\u7f3a\u5c11\u66ff\u6362\u65b9\u6848\u6807\u8bc6\u3002");
        }
        ChatReplacementSessionStore.PendingProposal proposal = sessionStore
                .getPendingProposal(req.getClientSessionId(), req.getProposalToken())
                .orElseThrow(() -> new BadRequestException("\u5f53\u524d\u66ff\u6362\u65b9\u6848\u5df2\u5931\u6548\uff0c\u8bf7\u91cd\u65b0\u751f\u6210\u3002"));
        return executionService.execute(userId, itineraryId, proposal, req.getCurrentNodes(), req.getOriginalReq());
    }
}
