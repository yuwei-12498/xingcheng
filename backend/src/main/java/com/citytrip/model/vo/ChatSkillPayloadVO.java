package com.citytrip.model.vo;

import com.citytrip.model.dto.GenerateReqDTO;
import com.citytrip.model.dto.ItineraryEditOperationDTO;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class ChatSkillPayloadVO {
    private String skillName;
    private String status;
    private String intent;
    private String messageType;
    private String workflowType;
    private String workflowState;
    private String clientSessionId;
    private String proposalToken;
    private String versionToken;
    private Query query = new Query();
    private String city;
    private String source;
    private List<String> evidence = new ArrayList<>();
    private String fallbackMessage;
    private List<ResultItem> results = new ArrayList<>();
    private ProposalSummary proposalSummary;
    private ReplacementProposal replacementProposal;
    private ItineraryEditDraft itineraryEditDraft;
    private GenerateReqDTO generateDraft;
    private List<String> generateSummary = new ArrayList<>();
    private List<ClarificationOption> clarificationOptions = new ArrayList<>();
    private List<ActionItem> actions = new ArrayList<>();

    public void setQuery(Query query) {
        this.query = query == null ? new Query() : query;
    }

    public void setEvidence(List<String> evidence) {
        this.evidence = evidence == null ? new ArrayList<>() : evidence;
    }

    public void setResults(List<ResultItem> results) {
        this.results = results == null ? new ArrayList<>() : results;
    }

    public void setClarificationOptions(List<ClarificationOption> clarificationOptions) {
        this.clarificationOptions = clarificationOptions == null ? new ArrayList<>() : clarificationOptions;
    }

    public void setActions(List<ActionItem> actions) {
        this.actions = actions == null ? new ArrayList<>() : actions;
    }

    public void setGenerateSummary(List<String> generateSummary) {
        this.generateSummary = generateSummary == null ? new ArrayList<>() : generateSummary;
    }

    @Data
    public static class Query {
        private String keyword;
        private String anchor;
        private String category;
        private Integer radiusMeters;
        private Integer limit;
    }

    @Data
    public static class ResultItem {
        private String name;
        private String address;
        private String category;
        private BigDecimal latitude;
        private BigDecimal longitude;
        private String cityName;
        private String source;
        private Double distanceMeters;
    }

    @Data
    public static class ClarificationOption {
        private String key;
        private String label;
        private String value;
    }

    @Data
    public static class ActionItem {
        private String key;
        private String label;
        private String style;
        private String value;
    }

    @Data
    public static class ProposalSummary {
        private String title;
        private String detail;
    }

    @Data
    public static class ReplacementProposal {
        private String mode;
        private List<Long> targetPoiIds = new ArrayList<>();
        private List<String> targetPoiNames = new ArrayList<>();
        private List<String> replacementPoiNames = new ArrayList<>();

        public void setTargetPoiIds(List<Long> targetPoiIds) {
            this.targetPoiIds = targetPoiIds == null ? new ArrayList<>() : targetPoiIds;
        }

        public void setTargetPoiNames(List<String> targetPoiNames) {
            this.targetPoiNames = targetPoiNames == null ? new ArrayList<>() : targetPoiNames;
        }

        public void setReplacementPoiNames(List<String> replacementPoiNames) {
            this.replacementPoiNames = replacementPoiNames == null ? new ArrayList<>() : replacementPoiNames;
        }
    }

    @Data
    public static class ItineraryEditDraft {
        private String summary;
        private List<ItineraryEditOperationDTO> operations = new ArrayList<>();

        public void setOperations(List<ItineraryEditOperationDTO> operations) {
            this.operations = operations == null ? new ArrayList<>() : new ArrayList<>(operations);
        }
    }
}
