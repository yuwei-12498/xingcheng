package com.citytrip.service.application.itinerary;

import com.citytrip.model.vo.PoiSearchResultVO;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class ChatReplacementSessionStore {

    private final ConcurrentMap<String, PendingProposal> pendingProposals = new ConcurrentHashMap<>();

    public void savePendingProposal(PendingProposal proposal) {
        if (proposal == null) {
            return;
        }
        pendingProposals.put(buildKey(proposal.getClientSessionId(), proposal.getProposalToken()), proposal);
    }

    public Optional<PendingProposal> getPendingProposal(String clientSessionId, String proposalToken) {
        if (clientSessionId == null || proposalToken == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(pendingProposals.get(buildKey(clientSessionId, proposalToken)));
    }

    public String nextProposalToken() {
        return UUID.randomUUID().toString();
    }

    private String buildKey(String clientSessionId, String proposalToken) {
        return String.valueOf(clientSessionId) + ':' + proposalToken;
    }

    @Data
    public static class PendingProposal {
        private String clientSessionId;
        private String proposalToken;
        private Long itineraryId;
        private String cityName;
        private String question;
        private String mode;
        private List<Long> targetPoiIds = new ArrayList<>();
        private List<String> targetPoiNames = new ArrayList<>();
        private List<com.citytrip.model.dto.ChatReqDTO.ChatRouteNode> targetNodes = new ArrayList<>();
        private List<PoiSearchResultVO> candidates = new ArrayList<>();
        private int candidateIndex;

        public void setTargetPoiIds(List<Long> targetPoiIds) {
            this.targetPoiIds = targetPoiIds == null ? new ArrayList<>() : new ArrayList<>(targetPoiIds);
        }

        public void setTargetPoiNames(List<String> targetPoiNames) {
            this.targetPoiNames = targetPoiNames == null ? new ArrayList<>() : new ArrayList<>(targetPoiNames);
        }

        public void setTargetNodes(List<com.citytrip.model.dto.ChatReqDTO.ChatRouteNode> targetNodes) {
            this.targetNodes = targetNodes == null ? new ArrayList<>() : new ArrayList<>(targetNodes);
        }

        public void setCandidates(List<PoiSearchResultVO> candidates) {
            this.candidates = candidates == null ? new ArrayList<>() : new ArrayList<>(candidates);
        }
    }
}
