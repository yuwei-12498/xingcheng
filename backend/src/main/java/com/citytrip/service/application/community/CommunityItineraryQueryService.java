package com.citytrip.service.application.community;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.citytrip.assembler.ItinerarySummaryAssembler;
import com.citytrip.mapper.CommunityCommentMapper;
import com.citytrip.mapper.CommunityLikeMapper;
import com.citytrip.mapper.UserMapper;
import com.citytrip.model.dto.GenerateReqDTO;
import com.citytrip.model.entity.CommunityComment;
import com.citytrip.model.entity.CommunityLike;
import com.citytrip.model.entity.SavedItinerary;
import com.citytrip.model.entity.User;
import com.citytrip.model.vo.CommunityCommentVO;
import com.citytrip.model.vo.CommunityItineraryDetailVO;
import com.citytrip.model.vo.CommunityItineraryPageVO;
import com.citytrip.model.vo.CommunityItineraryVO;
import com.citytrip.model.vo.ItineraryVO;
import com.citytrip.service.impl.CommunityItineraryCacheService;
import com.citytrip.service.persistence.itinerary.SavedItineraryCodec;
import com.citytrip.service.persistence.itinerary.SavedItineraryRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CommunityItineraryQueryService {

    private final SavedItineraryRepository savedItineraryRepository;
    private final CommunityCommentMapper communityCommentMapper;
    private final CommunityLikeMapper communityLikeMapper;
    private final UserMapper userMapper;
    private final SavedItineraryCodec savedItineraryCodec;
    private final ItinerarySummaryAssembler itinerarySummaryAssembler;
    private final CommunityItineraryCacheService communityItineraryCacheService;
    private final CommunitySemanticSearchService communitySemanticSearchService;

    public CommunityItineraryQueryService(SavedItineraryRepository savedItineraryRepository,
                                          CommunityCommentMapper communityCommentMapper,
                                          CommunityLikeMapper communityLikeMapper,
                                          UserMapper userMapper,
                                          SavedItineraryCodec savedItineraryCodec,
                                          ItinerarySummaryAssembler itinerarySummaryAssembler,
                                          CommunityItineraryCacheService communityItineraryCacheService) {
        this(savedItineraryRepository,
                communityCommentMapper,
                communityLikeMapper,
                userMapper,
                savedItineraryCodec,
                itinerarySummaryAssembler,
                communityItineraryCacheService,
                null);
    }

    @Autowired
    public CommunityItineraryQueryService(SavedItineraryRepository savedItineraryRepository,
                                          CommunityCommentMapper communityCommentMapper,
                                          CommunityLikeMapper communityLikeMapper,
                                          UserMapper userMapper,
                                          SavedItineraryCodec savedItineraryCodec,
                                          ItinerarySummaryAssembler itinerarySummaryAssembler,
                                          CommunityItineraryCacheService communityItineraryCacheService,
                                          CommunitySemanticSearchService communitySemanticSearchService) {
        this.savedItineraryRepository = savedItineraryRepository;
        this.communityCommentMapper = communityCommentMapper;
        this.communityLikeMapper = communityLikeMapper;
        this.userMapper = userMapper;
        this.savedItineraryCodec = savedItineraryCodec;
        this.itinerarySummaryAssembler = itinerarySummaryAssembler;
        this.communityItineraryCacheService = communityItineraryCacheService;
        this.communitySemanticSearchService = communitySemanticSearchService;
    }

    public CommunityItineraryPageVO listPublic(int page, int size) {
        return listPublic(page, size, "latest", null, null, null);
    }

    public CommunityItineraryPageVO listPublic(int page,
                                               int size,
                                               String sort,
                                               String keyword,
                                               String theme,
                                               Long currentUserId) {
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(size, 1), 30);
        String normalizedSort = normalizeSort(sort);
        String normalizedKeyword = normalizeKeyword(keyword);
        String normalizedTheme = normalizeKeyword(theme);

        if (currentUserId == null) {
            return communityItineraryCacheService.getCommunityPage(
                    normalizedPage,
                    normalizedSize,
                    normalizedSort,
                    normalizedKeyword,
                    normalizedTheme,
                    () -> buildPublicPage(normalizedPage, normalizedSize, normalizedSort, normalizedKeyword, normalizedTheme, null)
            );
        }
        return buildPublicPage(normalizedPage, normalizedSize, normalizedSort, normalizedKeyword, normalizedTheme, currentUserId);
    }

    private CommunityItineraryPageVO buildPublicPage(int normalizedPage,
                                                     int normalizedSize,
                                                     String normalizedSort,
                                                     String normalizedKeyword,
                                                     String normalizedTheme,
                                                     Long currentUserId) {
        List<SavedItinerary> entities = savedItineraryRepository.listPublicVisible();
        List<Long> itineraryIds = entities.stream().map(SavedItinerary::getId).filter(Objects::nonNull).toList();
        Map<Long, User> userMap = loadUserMap(entities);
        Map<Long, Long> commentCountMap = loadCommentCountMap(itineraryIds);
        Map<Long, Long> likeCountMap = loadLikeCountMap(itineraryIds);

        List<CommunityItineraryVO> summaries = entities.stream()
                .map(entity -> toCommunitySummary(
                        entity,
                        userMap.get(entity.getUserId()),
                        commentCountMap.get(entity.getId()),
                        likeCountMap.get(entity.getId()),
                        currentUserId
                ))
                .filter(Objects::nonNull)
                .toList();

        List<String> availableThemes = summaries.stream()
                .flatMap(item -> item.getThemes().stream())
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();

        List<CommunityItineraryVO> pinnedRecords = summaries.stream()
                .filter(item -> Boolean.TRUE.equals(item.getGlobalPinned()))
                .sorted(Comparator.comparing(CommunityItineraryVO::getGlobalPinnedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(CommunityItineraryVO::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(3)
                .toList();

        List<CommunityItineraryVO> feedRecords = summaries.stream()
                .filter(item -> !Boolean.TRUE.equals(item.getGlobalPinned()))
                .filter(item -> shouldKeepForKeyword(item, normalizedKeyword))
                .filter(item -> matchesTheme(item, normalizedTheme))
                .sorted(buildFeedComparator(normalizedSort))
                .toList();

        if (StringUtils.hasText(normalizedKeyword) && isSemanticKeywordSearchReady()) {
            feedRecords = applySemanticRanking(normalizedKeyword, feedRecords);
        }

        int fromIndex = Math.min((normalizedPage - 1) * normalizedSize, feedRecords.size());
        int toIndex = Math.min(fromIndex + normalizedSize, feedRecords.size());

        CommunityItineraryPageVO result = new CommunityItineraryPageVO();
        result.setPage(normalizedPage);
        result.setSize(normalizedSize);
        result.setSort(normalizedSort);
        result.setAvailableThemes(availableThemes);
        result.setPinnedRecords(pinnedRecords);
        result.setTotal((long) feedRecords.size());
        result.setRecords(feedRecords.subList(fromIndex, toIndex));
        return result;
    }

    public CommunityItineraryDetailVO getPublicDetail(Long itineraryId, Long currentUserId) {
        SavedItinerary entity = savedItineraryRepository.requirePublic(itineraryId);
        User author = loadUserMap(List.of(entity)).get(entity.getUserId());
        User currentUser = currentUserId == null ? null : userMapper.selectById(currentUserId);
        try {
            GenerateReqDTO req = savedItineraryCodec.readRequest(entity);
            ItineraryVO itinerary = savedItineraryCodec.readItinerary(entity);

            CommunityItineraryDetailVO detail = new CommunityItineraryDetailVO();
            detail.setId(entity.getId());
            detail.setAuthorId(entity.getUserId());
            detail.setTitle(itinerarySummaryAssembler.buildTitle(req, itinerary));
            detail.setCityName(req == null ? null : req.getCityName());
            detail.setCoverImageUrl(itinerarySummaryAssembler.resolveCoverImage(itinerary));
            detail.setShareNote(itinerarySummaryAssembler.resolveShareNote(entity, itinerary));
            detail.setAuthorLabel(itinerarySummaryAssembler.resolveAuthorLabel(author));
            detail.setTripDate(req == null ? null : req.getTripDate());
            detail.setStartTime(req == null ? null : req.getStartTime());
            detail.setEndTime(req == null ? null : req.getEndTime());
            detail.setThemes(req == null || req.getThemes() == null ? Collections.emptyList() : req.getThemes());
            detail.setTotalDuration(entity.getTotalDuration());
            detail.setTotalCost(entity.getTotalCost());
            detail.setNodeCount(entity.getNodeCount());
            detail.setRouteSummary(itinerarySummaryAssembler.buildRouteSummary(itinerary));
            detail.setRecommendReason(itinerary == null ? null : itinerary.getRecommendReason());
            detail.setSelectedOptionKey(itinerary == null ? null : itinerary.getSelectedOptionKey());
            detail.setHighlights(itinerarySummaryAssembler.resolveHighlights(itinerary));
            detail.setAlerts(itinerary == null || itinerary.getAlerts() == null ? Collections.emptyList() : itinerary.getAlerts());
            detail.setNodes(itinerary == null || itinerary.getNodes() == null ? Collections.emptyList() : itinerary.getNodes());
            detail.setLikeCount(countLikes(entity.getId()));
            detail.setLiked(isLikedByCurrentUser(entity.getId(), currentUserId));
            detail.setCommentCount(countComments(entity.getId()));
            detail.setGlobalPinned(Integer.valueOf(1).equals(entity.getIsGlobalPinned()));
            detail.setGlobalPinnedAt(entity.getGlobalPinnedAt());
            detail.setPinnedCommentId(entity.getPinnedCommentId());
            detail.setPinnedComment(loadPinnedComment(entity.getId(), entity.getPinnedCommentId(), currentUserId));
            detail.setCanDelete(canDelete(entity, currentUser));
            detail.setCanPinComment(canPinComment(entity, currentUserId));
            detail.setCanManage(isAdmin(currentUser));
            detail.setUpdatedAt(entity.getUpdateTime());
            return detail;
        } catch (JsonProcessingException ex) {
            throw new RuntimeException("Failed to deserialize public itinerary detail", ex);
        }
    }

    private CommunityItineraryVO toCommunitySummary(SavedItinerary entity,
                                                    User author,
                                                    Long commentCount,
                                                    Long likeCount,
                                                    Long currentUserId) {
        try {
            GenerateReqDTO req = savedItineraryCodec.readRequest(entity);
            ItineraryVO itinerary = savedItineraryCodec.readItinerary(entity);
            CommunityItineraryVO summary = itinerarySummaryAssembler.toCommunitySummary(entity, author, req, itinerary, commentCount, likeCount);
            summary.setGlobalPinned(Integer.valueOf(1).equals(entity.getIsGlobalPinned()));
            summary.setGlobalPinnedAt(entity.getGlobalPinnedAt());
            summary.setLiked(isLikedByCurrentUser(entity.getId(), currentUserId));
            return summary;
        } catch (JsonProcessingException ex) {
            throw new RuntimeException("Failed to deserialize public itinerary summary", ex);
        }
    }

    private Map<Long, User> loadUserMap(List<SavedItinerary> entities) {
        Set<Long> userIds = entities.stream()
                .map(SavedItinerary::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return loadUsersByIds(userIds);
    }

    Map<Long, User> loadUsersByIds(Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return userMapper.selectBatchIds(userIds).stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(User::getId, user -> user, (left, right) -> left, LinkedHashMap::new));
    }

    private long countComments(Long itineraryId) {
        try {
            QueryWrapper<CommunityComment> wrapper = new QueryWrapper<>();
            wrapper.eq("itinerary_id", itineraryId);
            return communityCommentMapper.selectCount(wrapper);
        } catch (DataAccessException ex) {
            log.warn("Community comment table unavailable, fallback to zero comments for itineraryId={}", itineraryId, ex);
            return 0L;
        }
    }

    private long countLikes(Long itineraryId) {
        try {
            QueryWrapper<CommunityLike> wrapper = new QueryWrapper<>();
            wrapper.eq("itinerary_id", itineraryId);
            return communityLikeMapper.selectCount(wrapper);
        } catch (DataAccessException ex) {
            log.warn("Community like table unavailable, fallback to zero likes for itineraryId={}", itineraryId, ex);
            return 0L;
        }
    }

    private Map<Long, Long> loadCommentCountMap(List<Long> itineraryIds) {
        if (itineraryIds == null || itineraryIds.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            QueryWrapper<CommunityComment> wrapper = new QueryWrapper<>();
            wrapper.in("itinerary_id", itineraryIds);
            return communityCommentMapper.selectList(wrapper).stream()
                    .filter(item -> item.getItineraryId() != null)
                    .collect(Collectors.groupingBy(CommunityComment::getItineraryId, LinkedHashMap::new, Collectors.counting()));
        } catch (DataAccessException ex) {
            log.warn("Community comment table unavailable, fallback to zero comment counts", ex);
            return Collections.emptyMap();
        }
    }

    private Map<Long, Long> loadLikeCountMap(List<Long> itineraryIds) {
        if (itineraryIds == null || itineraryIds.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            QueryWrapper<CommunityLike> wrapper = new QueryWrapper<>();
            wrapper.in("itinerary_id", itineraryIds);
            return communityLikeMapper.selectList(wrapper).stream()
                    .filter(item -> item.getItineraryId() != null)
                    .collect(Collectors.groupingBy(CommunityLike::getItineraryId, LinkedHashMap::new, Collectors.counting()));
        } catch (DataAccessException ex) {
            log.warn("Community like table unavailable, fallback to zero like counts", ex);
            return Collections.emptyMap();
        }
    }

    private boolean isLikedByCurrentUser(Long itineraryId, Long currentUserId) {
        if (itineraryId == null || currentUserId == null) {
            return false;
        }
        try {
            QueryWrapper<CommunityLike> wrapper = new QueryWrapper<>();
            wrapper.eq("itinerary_id", itineraryId).eq("user_id", currentUserId).last("limit 1");
            return communityLikeMapper.selectOne(wrapper) != null;
        } catch (DataAccessException ex) {
            log.warn("Community like table unavailable, fallback to not-liked state", ex);
            return false;
        }
    }

    private CommunityCommentVO loadPinnedComment(Long itineraryId, Long pinnedCommentId, Long currentUserId) {
        if (pinnedCommentId == null) {
            return null;
        }
        try {
            CommunityComment comment = communityCommentMapper.selectById(pinnedCommentId);
            if (comment == null || !Objects.equals(comment.getItineraryId(), itineraryId) || comment.getParentId() != null) {
                return null;
            }
            User author = comment.getUserId() == null ? null : userMapper.selectById(comment.getUserId());
            return toCommentVO(comment, author, currentUserId, true, false);
        } catch (DataAccessException ex) {
            log.warn("Community comment table unavailable, fallback to no pinned comment for itineraryId={}", itineraryId, ex);
            return null;
        }
    }

    private CommunityCommentVO toCommentVO(CommunityComment comment,
                                           User author,
                                           Long currentUserId,
                                           boolean pinned,
                                           boolean canPin) {
        CommunityCommentVO vo = new CommunityCommentVO();
        vo.setId(comment.getId());
        vo.setItineraryId(comment.getItineraryId());
        vo.setParentId(comment.getParentId());
        vo.setUserId(comment.getUserId());
        vo.setContent(comment.getContent());
        vo.setAuthorLabel(itinerarySummaryAssembler.resolveAuthorLabel(author));
        vo.setCreateTime(comment.getCreateTime());
        vo.setMine(currentUserId != null && Objects.equals(currentUserId, comment.getUserId()));
        vo.setPinned(pinned);
        vo.setCanPin(canPin);
        return vo;
    }

    private boolean matchesKeyword(CommunityItineraryVO item, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return true;
        }
        String normalized = keyword.trim().toLowerCase();
        return containsIgnoreCase(item.getTitle(), normalized)
                || containsIgnoreCase(item.getShareNote(), normalized)
                || containsIgnoreCase(item.getRouteSummary(), normalized)
                || item.getThemes().stream().filter(StringUtils::hasText).anyMatch(theme -> theme.toLowerCase().contains(normalized));
    }

    private boolean matchesTheme(CommunityItineraryVO item, String theme) {
        if (!StringUtils.hasText(theme)) {
            return true;
        }
        return item.getThemes().stream().filter(StringUtils::hasText).anyMatch(value -> value.equalsIgnoreCase(theme.trim()));
    }

    private boolean shouldKeepForKeyword(CommunityItineraryVO item, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return true;
        }
        if (isSemanticKeywordSearchReady()) {
            return true;
        }
        return matchesKeyword(item, keyword);
    }

    private boolean isSemanticKeywordSearchReady() {
        return communitySemanticSearchService != null && communitySemanticSearchService.isSemanticModelReady();
    }

    private Comparator<CommunityItineraryVO> buildFeedComparator(String sort) {
        if ("hot".equals(sort)) {
            return Comparator.comparing(this::interactionScore, Comparator.reverseOrder())
                    .thenComparing(CommunityItineraryVO::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder()));
        }
        return Comparator.comparing(CommunityItineraryVO::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder()));
    }

    private List<CommunityItineraryVO> applySemanticRanking(String keyword, List<CommunityItineraryVO> records) {
        try {
            List<CommunitySemanticSearchService.CommunitySemanticCandidate> candidates = records.stream()
                    .map(this::toSemanticCandidate)
                    .toList();
            Map<Long, Double> scoreMap = communitySemanticSearchService.rank(keyword, candidates).stream()
                    .collect(Collectors.toMap(
                            CommunitySemanticSearchService.ScoredCommunityCandidate::id,
                            CommunitySemanticSearchService.ScoredCommunityCandidate::score
                    ));
            return records.stream()
                    .sorted(Comparator.comparing((CommunityItineraryVO item) -> scoreMap.getOrDefault(item.getId(), -1D)).reversed())
                    .toList();
        } catch (Exception ex) {
            log.warn("Semantic community ranking failed, fallback to original ordering. keyword={}", keyword, ex);
            return records;
        }
    }

    private CommunitySemanticSearchService.CommunitySemanticCandidate toSemanticCandidate(CommunityItineraryVO item) {
        List<String> parts = new java.util.ArrayList<>();
        if (StringUtils.hasText(item.getTitle())) {
            parts.add(item.getTitle().trim());
        }
        if (StringUtils.hasText(item.getShareNote())) {
            parts.add(item.getShareNote().trim());
        }
        if (StringUtils.hasText(item.getRouteSummary())) {
            parts.add(item.getRouteSummary().trim());
        }
        if (item.getThemes() != null && !item.getThemes().isEmpty()) {
            parts.add(String.join(" ", item.getThemes()));
        }
        return new CommunitySemanticSearchService.CommunitySemanticCandidate(item.getId(), String.join("\n", parts));
    }

    private long interactionScore(CommunityItineraryVO item) {
        long comments = item.getCommentCount() == null ? 0L : item.getCommentCount();
        long likes = item.getLikeCount() == null ? 0L : item.getLikeCount();
        return comments * 3 + likes * 2;
    }

    private boolean containsIgnoreCase(String source, String normalizedKeyword) {
        return StringUtils.hasText(source) && source.toLowerCase().contains(normalizedKeyword);
    }

    private String normalizeSort(String sort) {
        return "hot".equalsIgnoreCase(sort) ? "hot" : "latest";
    }

    private String normalizeKeyword(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private boolean canDelete(SavedItinerary entity, User currentUser) {
        return currentUser != null && (Objects.equals(currentUser.getId(), entity.getUserId()) || isAdmin(currentUser));
    }

    private boolean canPinComment(SavedItinerary entity, Long currentUserId) {
        return currentUserId != null && Objects.equals(currentUserId, entity.getUserId());
    }

    private boolean isAdmin(User user) {
        return user != null && Integer.valueOf(1).equals(user.getRole());
    }
}
