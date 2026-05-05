package com.citytrip.service.application.itinerary;

import com.citytrip.common.BadRequestException;
import com.citytrip.model.dto.ChatReplacementRestoreReqDTO;
import com.citytrip.model.dto.GenerateReqDTO;
import com.citytrip.model.vo.ItineraryVO;
import org.springframework.stereotype.Service;

@Service
public class ChatReplacementRestoreUseCase {

    private final SavedItineraryCommandService savedItineraryCommandService;

    public ChatReplacementRestoreUseCase(SavedItineraryCommandService savedItineraryCommandService) {
        this.savedItineraryCommandService = savedItineraryCommandService;
    }

    public ItineraryVO restore(Long userId, Long itineraryId, ChatReplacementRestoreReqDTO req) {
        if (req == null || req.getItinerarySnapshot() == null) {
            throw new BadRequestException("\u7f3a\u5c11\u8981\u6062\u590d\u7684\u884c\u7a0b\u5feb\u7167\u3002");
        }
        ItineraryVO snapshot = req.getItinerarySnapshot();
        if (snapshot.getId() != null && !snapshot.getId().equals(itineraryId)) {
            throw new BadRequestException("\u884c\u7a0b\u5feb\u7167\u4e0e\u5f53\u524d\u884c\u7a0b\u4e0d\u5339\u914d\u3002");
        }
        snapshot.setId(itineraryId);
        GenerateReqDTO originalReq = snapshot.getOriginalReq() == null ? new GenerateReqDTO() : snapshot.getOriginalReq();
        return savedItineraryCommandService.save(userId, itineraryId, originalReq, snapshot);
    }
}
