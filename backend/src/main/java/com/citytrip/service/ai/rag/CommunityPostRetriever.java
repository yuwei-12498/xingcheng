package com.citytrip.service.ai.rag;

import com.citytrip.model.vo.CommunityItineraryPageVO;
import com.citytrip.model.vo.CommunityItineraryVO;
import com.citytrip.service.ai.model.AiExecutionContext;
import com.citytrip.service.application.community.CommunityItineraryQueryService;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

public class CommunityPostRetriever implements ContextRetriever {
    private static final int DEFAULT_LIMIT = 3;
    private static final List<String> COMMUNITY_HINTS = List.of(
            "攻略", "社区", "帖子", "路线", "行程", "拍照", "打卡"
    );

    private final CommunityItineraryQueryService communityItineraryQueryService;

    public CommunityPostRetriever() {
        this(null);
    }

    public CommunityPostRetriever(CommunityItineraryQueryService communityItineraryQueryService) {
        this.communityItineraryQueryService = communityItineraryQueryService;
    }

    @Override
    public List<RetrievalDocument> retrieve(AiExecutionContext context) {
        if (communityItineraryQueryService == null || context == null || !StringUtils.hasText(context.getUserInput())) {
            return List.of();
        }
        String question = context.getUserInput().trim();
        if (!shouldRetrieve(question)) {
            return List.of();
        }
        try {
            CommunityItineraryPageVO page = communityItineraryQueryService.listPublic(
                    1,
                    DEFAULT_LIMIT,
                    "latest",
                    question,
                    null,
                    context.getUserId()
            );
            if (page == null || page.getRecords() == null || page.getRecords().isEmpty()) {
                return List.of();
            }
            List<RetrievalDocument> documents = new ArrayList<>();
            for (CommunityItineraryVO item : page.getRecords()) {
                String content = summarize(item);
                if (!StringUtils.hasText(content)) {
                    continue;
                }
                documents.add(new RetrievalDocument(resolveSource(item), content));
                if (documents.size() >= DEFAULT_LIMIT) {
                    break;
                }
            }
            return documents;
        } catch (RuntimeException ex) {
            return List.of();
        }
    }

    private boolean shouldRetrieve(String question) {
        for (String hint : COMMUNITY_HINTS) {
            if (question.contains(hint)) {
                return true;
            }
        }
        return false;
    }

    private String summarize(CommunityItineraryVO item) {
        if (item == null) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        append(parts, "title", item.getTitle());
        append(parts, "shareNote", item.getShareNote());
        append(parts, "routeSummary", item.getRouteSummary());
        if (item.getThemes() != null && !item.getThemes().isEmpty()) {
            append(parts, "themes", String.join("/", item.getThemes()));
        }
        if (item.getLikeCount() != null) {
            parts.add("likes=" + item.getLikeCount());
        }
        if (item.getCommentCount() != null) {
            parts.add("comments=" + item.getCommentCount());
        }
        if (Boolean.TRUE.equals(item.getLiked())) {
            parts.add("liked=true");
        }
        return String.join(" | ", parts);
    }

    private String resolveSource(CommunityItineraryVO item) {
        return item != null && Boolean.TRUE.equals(item.getLiked()) ? "community-post-liked" : "community-post";
    }

    private void append(List<String> parts, String label, String value) {
        if (StringUtils.hasText(value)) {
            parts.add(label + "=" + value.trim());
        }
    }
}
