package com.citytrip.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.citytrip.common.BadRequestException;
import com.citytrip.common.NotFoundException;
import com.citytrip.common.SystemBusyException;
import com.citytrip.mapper.CommunityCommentMapper;
import com.citytrip.mapper.CommunityLikeMapper;
import com.citytrip.mapper.SavedItineraryMapper;
import com.citytrip.mapper.UserMapper;
import com.citytrip.model.dto.CommunityCommentReqDTO;
import com.citytrip.model.dto.FavoriteReqDTO;
import com.citytrip.model.dto.GenerateReqDTO;
import com.citytrip.model.dto.OptionSelectReqDTO;
import com.citytrip.model.dto.PublicStatusReqDTO;
import com.citytrip.model.entity.CommunityComment;
import com.citytrip.model.entity.CommunityLike;
import com.citytrip.model.entity.SavedItinerary;
import com.citytrip.model.entity.User;
import com.citytrip.model.vo.CommunityCommentVO;
import com.citytrip.model.vo.CommunityItineraryDetailVO;
import com.citytrip.model.vo.CommunityItineraryPageVO;
import com.citytrip.model.vo.CommunityItineraryVO;
import com.citytrip.model.vo.ItineraryNodeVO;
import com.citytrip.model.vo.ItineraryOptionVO;
import com.citytrip.model.vo.ItinerarySummaryVO;
import com.citytrip.model.vo.ItineraryVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
public class SavedItineraryStore {

    private final SavedItineraryMapper savedItineraryMapper;
    private final CommunityCommentMapper communityCommentMapper;
    private final CommunityLikeMapper communityLikeMapper;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;
    private final CommunityItineraryCacheService communityItineraryCacheService;

    public SavedItineraryStore(SavedItineraryMapper savedItineraryMapper,
                               CommunityCommentMapper communityCommentMapper,
                               CommunityLikeMapper communityLikeMapper,
                               UserMapper userMapper,
                               ObjectMapper objectMapper,
                               CommunityItineraryCacheService communityItineraryCacheService) {
        this.savedItineraryMapper = savedItineraryMapper;
        this.communityCommentMapper = communityCommentMapper;
        this.communityLikeMapper = communityLikeMapper;
        this.userMapper = userMapper;
        this.objectMapper = objectMapper;
        this.communityItineraryCacheService = communityItineraryCacheService;
    }

    public ItineraryVO loadLatest(Long userId) {
        if (userId == null) {
            return null;
        }
        QueryWrapper<SavedItinerary> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId).orderByDesc("update_time").last("limit 1");
        SavedItinerary entity = savedItineraryMapper.selectOne(wrapper);
        return entity == null ? null : deserialize(entity);
    }

    public ItineraryVO load(Long userId, Long itineraryId) {
        return deserialize(requireOwned(userId, itineraryId));
    }

    public List<ItinerarySummaryVO> listSummaries(Long userId, boolean favoriteOnly, Integer limit) {
        if (userId == null) {
            return Collections.emptyList();
        }
        QueryWrapper<SavedItinerary> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId);
        if (favoriteOnly) {
            wrapper.eq("favorited", 1).orderByDesc("favorite_time").orderByDesc("update_time");
        } else {
            wrapper.orderByDesc("update_time");
        }
        if (limit != null && limit > 0) {
            wrapper.last("limit " + limit);
        }

        List<SavedItinerary> entities = savedItineraryMapper.selectList(wrapper);
        List<ItinerarySummaryVO> result = new ArrayList<>(entities.size());
        for (SavedItinerary entity : entities) {
            result.add(toSummary(entity));
        }
        return result;
    }

    public CommunityItineraryPageVO listPublicSummaries(int page, int size) {
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(size, 1), 30);
        int offset = (normalizedPage - 1) * normalizedSize;

        QueryWrapper<SavedItinerary> countWrapper = new QueryWrapper<>();
        countWrapper.eq("is_public", 1);
        long total = savedItineraryMapper.selectCount(countWrapper);

        QueryWrapper<SavedItinerary> wrapper = new QueryWrapper<>();
        wrapper.eq("is_public", 1)
                .orderByDesc("favorite_time")
                .orderByDesc("update_time")
                .last("limit " + offset + "," + normalizedSize);

        List<SavedItinerary> entities = savedItineraryMapper.selectList(wrapper);
        Map<Long, User> userMap = loadUserMap(entities);
        Map<Long, Long> commentCountMap = loadCommentCountMap(entities.stream().map(SavedItinerary::getId).toList());
        Map<Long, Long> likeCountMap = loadLikeCountMap(entities.stream().map(SavedItinerary::getId).toList());

        CommunityItineraryPageVO result = new CommunityItineraryPageVO();
        result.setPage(normalizedPage);
        result.setSize(normalizedSize);
        result.setTotal(total);
        result.setRecords(entities.stream()
                .map(entity -> toCommunitySummary(
                        entity,
                        userMap.get(entity.getUserId()),
                        commentCountMap.get(entity.getId()),
                        likeCountMap.get(entity.getId())
                ))
                .toList());
        return result;
    }

    public CommunityItineraryDetailVO loadPublicDetail(Long itineraryId) {
        return loadPublicDetail(itineraryId, null);
    }

    public CommunityItineraryDetailVO loadPublicDetail(Long itineraryId, Long currentUserId) {
        SavedItinerary entity = requirePublic(itineraryId);
        User author = loadUserMap(List.of(entity)).get(entity.getUserId());
        try {
            GenerateReqDTO req = objectMapper.readValue(entity.getRequestJson(), GenerateReqDTO.class);
            ItineraryVO itinerary = objectMapper.readValue(entity.getItineraryJson(), ItineraryVO.class);

            CommunityItineraryDetailVO detail = new CommunityItineraryDetailVO();
            detail.setId(entity.getId());
            detail.setTitle(buildTitle(req, itinerary));
            detail.setShareNote(resolveShareNote(entity, itinerary));
            detail.setAuthorLabel(resolveAuthorLabel(author));
            detail.setTripDate(req == null ? null : req.getTripDate());
            detail.setStartTime(req == null ? null : req.getStartTime());
            detail.setEndTime(req == null ? null : req.getEndTime());
            detail.setTotalDuration(entity.getTotalDuration());
            detail.setTotalCost(entity.getTotalCost());
            detail.setNodeCount(entity.getNodeCount());
            detail.setRouteSummary(buildRouteSummary(itinerary));
            detail.setHighlights(resolveHighlights(itinerary));
            detail.setNodes(itinerary == null || itinerary.getNodes() == null ? Collections.emptyList() : itinerary.getNodes());
            detail.setLikeCount(countLikes(entity.getId()));
            detail.setLiked(isLikedByCurrentUser(entity.getId(), currentUserId));
            detail.setCommentCount(countComments(entity.getId()));
            detail.setUpdatedAt(entity.getUpdateTime());
            return detail;
        } catch (JsonProcessingException ex) {
            throw new RuntimeException("鏃犳硶璇诲彇绀惧尯璺嚎璇︽儏", ex);
        }
    }

    public List<CommunityCommentVO> listCommunityComments(Long itineraryId, Long currentUserId) {
        SavedItinerary entity = requirePublic(itineraryId);
        QueryWrapper<CommunityComment> wrapper = new QueryWrapper<>();
        wrapper.eq("itinerary_id", entity.getId()).orderByAsc("create_time").orderByAsc("id");
        List<CommunityComment> comments = communityCommentMapper.selectList(wrapper);
        Set<Long> userIds = comments.stream()
                .map(CommunityComment::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, User> userMap = loadUsersByIds(userIds);
        Map<Long, List<CommunityCommentVO>> repliesMap = comments.stream()
                .filter(comment -> comment.getParentId() != null)
                .map(comment -> toCommunityComment(comment, userMap.get(comment.getUserId()), currentUserId))
                .collect(Collectors.groupingBy(CommunityCommentVO::getParentId, LinkedHashMap::new, Collectors.toList()));

        return comments.stream()
                .filter(comment -> comment.getParentId() == null)
                .map(comment -> {
                    CommunityCommentVO root = toCommunityComment(comment, userMap.get(comment.getUserId()), currentUserId);
                    root.setReplies(repliesMap.getOrDefault(root.getId(), Collections.emptyList()));
                    return root;
                })
                .toList();
    }

    @Transactional
    public CommunityCommentVO addCommunityComment(Long userId, Long itineraryId, CommunityCommentReqDTO req) {
        if (userId == null) {
            throw new BadRequestException("璇峰厛鐧诲綍鍚庡啀鍙戣〃璇勮");
        }
        SavedItinerary entity = requirePublic(itineraryId);
        String content = normalizeCommentContent(req == null ? null : req.getContent());
        if (!StringUtils.hasText(content)) {
            throw new BadRequestException("璇勮鍐呭涓嶈兘涓虹┖");
        }
        Long parentId = req == null ? null : req.getParentId();
        if (parentId != null) {
            CommunityComment parent = communityCommentMapper.selectById(parentId);
            if (parent == null || !Objects.equals(parent.getItineraryId(), entity.getId())) {
                throw new BadRequestException("鍥炲鐨勮瘎璁轰笉瀛樺湪");
            }
            if (parent.getParentId() != null) {
                throw new BadRequestException("当前仅支持回复一级评论");
            }
        }

        CommunityComment comment = new CommunityComment();
        comment.setItineraryId(entity.getId());
        comment.setUserId(userId);
        comment.setParentId(parentId);
        comment.setContent(content);
        communityCommentMapper.insert(comment);
        comment = communityCommentMapper.selectById(comment.getId());
        communityItineraryCacheService.markCommunityPageCacheDirty();

        User author = userMapper.selectById(userId);
        return toCommunityComment(comment, author, userId);
    }

    @Transactional
    public CommunityItineraryDetailVO likeCommunityItinerary(Long userId, Long itineraryId) {
        if (userId == null) {
            throw new BadRequestException("璇峰厛鐧诲綍鍚庡啀鐐硅禐");
        }
        SavedItinerary entity = requirePublic(itineraryId);
        try {
            CommunityLike like = new CommunityLike();
            like.setItineraryId(entity.getId());
            like.setUserId(userId);
            communityLikeMapper.insert(like);
        } catch (DuplicateKeyException ignore) {
            // Idempotent request.
        }
        communityItineraryCacheService.markCommunityPageCacheDirty();
        return loadPublicDetail(entity.getId(), userId);
    }

    @Transactional
    public CommunityItineraryDetailVO unlikeCommunityItinerary(Long userId, Long itineraryId) {
        if (userId == null) {
            throw new BadRequestException("璇峰厛鐧诲綍鍚庡啀鍙栨秷鐐硅禐");
        }
        SavedItinerary entity = requirePublic(itineraryId);
        QueryWrapper<CommunityLike> wrapper = new QueryWrapper<>();
        wrapper.eq("itinerary_id", entity.getId()).eq("user_id", userId);
        communityLikeMapper.delete(wrapper);
        communityItineraryCacheService.markCommunityPageCacheDirty();
        return loadPublicDetail(entity.getId(), userId);
    }

    @Transactional
    public ItineraryVO favorite(Long userId, Long itineraryId, FavoriteReqDTO req) {
        SavedItinerary entity = requireOwnedForUpdate(userId, itineraryId);
        ItineraryVO itinerary = deserialize(entity);
        ItineraryVO selected = selectOptionInPlace(itinerary, req == null ? null : req.getSelectedOptionKey());
        String customTitle = normalizeTitle(req == null ? null : req.getTitle());

        selected.setCustomTitle(customTitle);
        entity.setItineraryJson(writeJson(selected));
        entity.setNodeCount(selected.getNodes() == null ? 0 : selected.getNodes().size());
        entity.setTotalDuration(selected.getTotalDuration());
        entity.setTotalCost(selected.getTotalCost());
        entity.setRouteSignature(signature(selected));
        entity.setCustomTitle(customTitle);
        entity.setFavorited(1);
        entity.setFavoriteTime(LocalDateTime.now());
        savedItineraryMapper.updateById(entity);
        evictCommunityCacheIfPublic(entity);

        selected.setId(entity.getId());
        selected.setCustomTitle(entity.getCustomTitle());
        selected.setShareNote(entity.getShareNote());
        selected.setFavorited(true);
        selected.setFavoriteTime(entity.getFavoriteTime());
        selected.setIsPublic(entity.getIsPublic() != null && entity.getIsPublic() == 1);
        selected.setLastSavedAt(entity.getUpdateTime() == null ? LocalDateTime.now() : entity.getUpdateTime());
        return selected;
    }

    @Transactional
    public ItineraryVO selectOption(Long userId, Long itineraryId, OptionSelectReqDTO req) {
        SavedItinerary entity = requireOwnedForUpdate(userId, itineraryId);
        ItineraryVO itinerary = deserialize(entity);
        ItineraryVO selected = selectOptionInPlace(itinerary, req == null ? null : req.getSelectedOptionKey());

        entity.setItineraryJson(writeJson(selected));
        entity.setNodeCount(selected.getNodes() == null ? 0 : selected.getNodes().size());
        entity.setTotalDuration(selected.getTotalDuration());
        entity.setTotalCost(selected.getTotalCost());
        entity.setRouteSignature(signature(selected));
        savedItineraryMapper.updateById(entity);
        evictCommunityCacheIfPublic(entity);

        selected.setId(entity.getId());
        selected.setCustomTitle(entity.getCustomTitle());
        selected.setShareNote(entity.getShareNote());
        selected.setFavorited(entity.getFavorited() != null && entity.getFavorited() == 1);
        selected.setFavoriteTime(entity.getFavoriteTime());
        selected.setIsPublic(entity.getIsPublic() != null && entity.getIsPublic() == 1);
        selected.setLastSavedAt(entity.getUpdateTime() == null ? LocalDateTime.now() : entity.getUpdateTime());
        return selected;
    }

    @Transactional
    public void markFavorite(Long userId, Long itineraryId, boolean favorited) {
        SavedItinerary entity = requireOwnedForUpdate(userId, itineraryId);
        entity.setFavorited(favorited ? 1 : 0);
        entity.setFavoriteTime(favorited ? LocalDateTime.now() : null);
        savedItineraryMapper.updateById(entity);
        evictCommunityCacheIfPublic(entity);
    }

    @Transactional
    public ItineraryVO updatePublicStatus(Long userId, Long itineraryId, boolean isPublic, PublicStatusReqDTO req) {
        SavedItinerary entity = requireOwnedForUpdate(userId, itineraryId);
        if (isPublic) {
            ItineraryVO itinerary = deserialize(entity);
            ItineraryVO selected = selectOptionInPlace(itinerary, req == null ? null : req.getSelectedOptionKey());
            String customTitle = normalizeTitle(req == null ? null : req.getTitle());
            String shareNote = normalizeShareNote(req == null ? null : req.getShareNote());

            if (StringUtils.hasText(customTitle)) {
                selected.setCustomTitle(customTitle);
                entity.setCustomTitle(customTitle);
            }
            selected.setShareNote(shareNote);
            entity.setShareNote(shareNote);
            entity.setItineraryJson(writeJson(selected));
            entity.setNodeCount(selected.getNodes() == null ? 0 : selected.getNodes().size());
            entity.setTotalDuration(selected.getTotalDuration());
            entity.setTotalCost(selected.getTotalCost());
            entity.setRouteSignature(signature(selected));
        }
        entity.setIsPublic(isPublic ? 1 : 0);
        savedItineraryMapper.updateById(entity);
        communityItineraryCacheService.markCommunityPageCacheDirty();
        entity = savedItineraryMapper.selectById(entity.getId());
        return deserialize(entity);
    }

    @Transactional
    public ItineraryVO save(Long userId, Long itineraryId, GenerateReqDTO req, ItineraryVO itinerary) {
        itinerary.setOriginalReq(req);
        if (userId == null) {
            return itinerary;
        }

        SavedItinerary entity = findOwnedForUpdate(userId, itineraryId);
        if (entity == null) {
            entity = new SavedItinerary();
            entity.setUserId(userId);
            entity.setFavorited(0);
            entity.setIsPublic(0);
        }
        if (StringUtils.hasText(entity.getCustomTitle()) && !StringUtils.hasText(itinerary.getCustomTitle())) {
            itinerary.setCustomTitle(entity.getCustomTitle());
        }
        if (entity.getShareNote() != null && !StringUtils.hasText(itinerary.getShareNote())) {
            itinerary.setShareNote(entity.getShareNote());
        }

        entity.setRequestJson(writeJson(req));
        entity.setItineraryJson(writeJson(itinerary));
        entity.setNodeCount(itinerary.getNodes() == null ? 0 : itinerary.getNodes().size());
        entity.setTotalDuration(itinerary.getTotalDuration());
        entity.setTotalCost(itinerary.getTotalCost());
        entity.setRouteSignature(signature(itinerary));

        if (entity.getId() == null) {
            savedItineraryMapper.insert(entity);
        } else {
            savedItineraryMapper.updateById(entity);
        }
        evictCommunityCacheIfPublic(entity);

        itinerary.setId(entity.getId());
        itinerary.setCustomTitle(entity.getCustomTitle());
        itinerary.setShareNote(entity.getShareNote());
        itinerary.setFavorited(entity.getFavorited() != null && entity.getFavorited() == 1);
        itinerary.setFavoriteTime(entity.getFavoriteTime());
        itinerary.setIsPublic(entity.getIsPublic() != null && entity.getIsPublic() == 1);
        itinerary.setLastSavedAt(entity.getUpdateTime() == null ? LocalDateTime.now() : entity.getUpdateTime());
        return itinerary;
    }

    private SavedItinerary findOwned(Long userId, Long itineraryId) {
        if (userId == null || itineraryId == null) {
            return null;
        }
        SavedItinerary entity = savedItineraryMapper.selectById(itineraryId);
        if (entity == null || !Objects.equals(entity.getUserId(), userId)) {
            return null;
        }
        return entity;
    }

    private SavedItinerary findOwnedForUpdate(Long userId, Long itineraryId) {
        if (userId == null || itineraryId == null) {
            return null;
        }
        SavedItinerary entity = savedItineraryMapper.selectOwnedForUpdate(userId, itineraryId);
        if (entity == null) {
            return null;
        }
        if (!Objects.equals(entity.getUserId(), userId)) {
            throw new SystemBusyException("The itinerary is being updated by another request");
        }
        return entity;
    }

    private SavedItinerary requireOwned(Long userId, Long itineraryId) {
        SavedItinerary entity = findOwned(userId, itineraryId);
        if (entity == null) {
            throw new NotFoundException("鏈壘鍒板搴旂殑琛岀▼璁板綍");
        }
        return entity;
    }

    private SavedItinerary requireOwnedForUpdate(Long userId, Long itineraryId) {
        SavedItinerary entity = findOwnedForUpdate(userId, itineraryId);
        if (entity == null) {
            throw new NotFoundException("The itinerary record was not found");
        }
        return entity;
    }

    private SavedItinerary requirePublic(Long itineraryId) {
        SavedItinerary entity = itineraryId == null ? null : savedItineraryMapper.selectById(itineraryId);
        if (entity == null || entity.getIsPublic() == null || entity.getIsPublic() != 1) {
            throw new NotFoundException("未找到公开的社区路线");
        }
        return entity;
    }

    private ItinerarySummaryVO toSummary(SavedItinerary entity) {
        try {
            GenerateReqDTO req = objectMapper.readValue(entity.getRequestJson(), GenerateReqDTO.class);
            ItineraryVO itinerary = objectMapper.readValue(entity.getItineraryJson(), ItineraryVO.class);

            ItinerarySummaryVO summary = new ItinerarySummaryVO();
            summary.setId(entity.getId());
            summary.setTitle(buildTitle(req, itinerary));
            summary.setTripDate(req == null ? null : req.getTripDate());
            summary.setStartTime(req == null ? null : req.getStartTime());
            summary.setEndTime(req == null ? null : req.getEndTime());
            summary.setNodeCount(entity.getNodeCount());
            summary.setTotalDuration(entity.getTotalDuration());
            summary.setTotalCost(entity.getTotalCost());
            summary.setBudgetLevel(req == null ? null : req.getBudgetLevel());
            summary.setCompanionType(req == null ? null : req.getCompanionType());
            summary.setRainy(req != null && Boolean.TRUE.equals(req.getIsRainy()));
            summary.setNight(req != null && Boolean.TRUE.equals(req.getIsNight()));
            summary.setFavorited(entity.getFavorited() != null && entity.getFavorited() == 1);
            summary.setFavoriteTime(entity.getFavoriteTime());
            summary.setIsPublic(entity.getIsPublic() != null && entity.getIsPublic() == 1);
            summary.setUpdatedAt(entity.getUpdateTime());
            summary.setThemes(req == null || req.getThemes() == null ? Collections.emptyList() : req.getThemes());

            List<ItineraryNodeVO> nodes = itinerary == null || itinerary.getNodes() == null ? Collections.emptyList() : itinerary.getNodes();
            if (!nodes.isEmpty()) {
                summary.setFirstPoiName(nodes.get(0).getPoiName());
                summary.setLastPoiName(nodes.get(nodes.size() - 1).getPoiName());
            }
            return summary;
        } catch (JsonProcessingException ex) {
            throw new RuntimeException("鏃犳硶璇诲彇鍘嗗彶琛岀▼鎽樿", ex);
        }
    }

    private CommunityItineraryVO toCommunitySummary(SavedItinerary entity, User author, Long commentCount, Long likeCount) {
        try {
            GenerateReqDTO req = objectMapper.readValue(entity.getRequestJson(), GenerateReqDTO.class);
            ItineraryVO itinerary = objectMapper.readValue(entity.getItineraryJson(), ItineraryVO.class);

            CommunityItineraryVO summary = new CommunityItineraryVO();
            summary.setId(entity.getId());
            summary.setTitle(buildTitle(req, itinerary));
            summary.setTotalDuration(entity.getTotalDuration());
            summary.setTotalCost(entity.getTotalCost());
            summary.setNodeCount(entity.getNodeCount());
            summary.setUpdatedAt(entity.getUpdateTime());
            summary.setAuthorLabel(resolveAuthorLabel(author));
            summary.setShareNote(resolveShareNote(entity, itinerary));
            summary.setRouteSummary(buildRouteSummary(itinerary));
            summary.setHighlights(resolveHighlights(itinerary));
            summary.setLikeCount(likeCount == null ? 0L : likeCount);
            summary.setLiked(false);
            summary.setCommentCount(commentCount == null ? 0L : commentCount);
            return summary;
        } catch (JsonProcessingException ex) {
            throw new RuntimeException("鏃犳硶璇诲彇绀惧尯琛岀▼鎽樿", ex);
        }
    }

    private ItineraryVO deserialize(SavedItinerary entity) {
        try {
            ItineraryVO itinerary = objectMapper.readValue(entity.getItineraryJson(), ItineraryVO.class);
            GenerateReqDTO req = objectMapper.readValue(entity.getRequestJson(), GenerateReqDTO.class);
            itinerary.setId(entity.getId());
            itinerary.setCustomTitle(entity.getCustomTitle());
            itinerary.setShareNote(entity.getShareNote());
            itinerary.setOriginalReq(req);
            itinerary.setFavorited(entity.getFavorited() != null && entity.getFavorited() == 1);
            itinerary.setFavoriteTime(entity.getFavoriteTime());
            itinerary.setIsPublic(entity.getIsPublic() != null && entity.getIsPublic() == 1);
            itinerary.setLastSavedAt(entity.getUpdateTime());
            return itinerary;
        } catch (JsonProcessingException ex) {
            throw new RuntimeException("鏃犳硶鎭㈠鍘嗗彶琛岀▼", ex);
        }
    }

    private String buildTitle(GenerateReqDTO req, ItineraryVO itinerary) {
        if (StringUtils.hasText(itinerary == null ? null : itinerary.getCustomTitle())) {
            return itinerary.getCustomTitle().trim();
        }
        List<String> themes = req == null || req.getThemes() == null ? Collections.emptyList() : req.getThemes();
        if (!themes.isEmpty()) {
            return String.join(" / ", themes) + "璺嚎";
        }
        List<ItineraryNodeVO> nodes = itinerary == null || itinerary.getNodes() == null ? Collections.emptyList() : itinerary.getNodes();
        if (!nodes.isEmpty()) {
            return nodes.get(0).getPoiName() + "鍑哄彂璺嚎";
        }
        String tripDate = req == null ? null : req.getTripDate();
        return StringUtils.hasText(tripDate) ? tripDate + "琛岀▼" : "鎴戠殑琛岀▼";
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new RuntimeException("淇濆瓨琛岀▼澶辫触", ex);
        }
    }

    private String signature(ItineraryVO itinerary) {
        if (itinerary.getNodes() == null || itinerary.getNodes().isEmpty()) {
            return "";
        }
        return itinerary.getNodes().stream()
                .map(node -> String.valueOf(node.getPoiId()))
                .reduce((left, right) -> left + "-" + right)
                .orElse("");
    }

    private ItineraryVO selectOptionInPlace(ItineraryVO itinerary, String selectedOptionKey) {
        if (itinerary == null) {
            return null;
        }
        if (itinerary.getOptions() == null || itinerary.getOptions().isEmpty()) {
            itinerary.setSelectedOptionKey(null);
            return itinerary;
        }

        ItineraryOptionVO selected = itinerary.getOptions().stream()
                .filter(option -> Objects.equals(option.getOptionKey(), selectedOptionKey))
                .findFirst()
                .orElse(itinerary.getOptions().get(0));

        itinerary.setSelectedOptionKey(selected.getOptionKey());
        itinerary.setNodes(selected.getNodes());
        itinerary.setTotalDuration(selected.getTotalDuration());
        itinerary.setTotalCost(selected.getTotalCost());
        itinerary.setRecommendReason(selected.getRecommendReason());
        itinerary.setRecommendationSource(selected.getRecommendationSource());
        itinerary.setAiDecorated(selected.getAiDecorated());
        itinerary.setAlerts(selected.getAlerts());
        return itinerary;
    }

    private String normalizeTitle(String title) {
        if (!StringUtils.hasText(title)) {
            return null;
        }
        String value = title.trim();
        return value.length() > 60 ? value.substring(0, 60) : value;
    }

    private String normalizeShareNote(String shareNote) {
        if (!StringUtils.hasText(shareNote)) {
            return null;
        }
        String value = shareNote.trim();
        return value.length() > 300 ? value.substring(0, 300) : value;
    }

    private String normalizeCommentContent(String content) {
        if (!StringUtils.hasText(content)) {
            return null;
        }
        String value = content.trim();
        return value.length() > 300 ? value.substring(0, 300) : value;
    }

    private Map<Long, User> loadUserMap(List<SavedItinerary> entities) {
        Set<Long> userIds = entities.stream()
                .map(SavedItinerary::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return loadUsersByIds(userIds);
    }

    private Map<Long, User> loadUsersByIds(Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return userMapper.selectBatchIds(userIds).stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(User::getId, user -> user, (left, right) -> left, LinkedHashMap::new));
    }

    private String resolveAuthorLabel(User author) {
        if (author == null) {
            return "鍖垮悕鏃呬汉";
        }
        if (StringUtils.hasText(author.getNickname())) {
            return author.getNickname().trim();
        }
        if (StringUtils.hasText(author.getUsername())) {
            return author.getUsername().trim();
        }
        return "鍩庡競鏃呬汉";
    }

    private String buildRouteSummary(ItineraryVO itinerary) {
        List<ItineraryNodeVO> nodes = itinerary == null || itinerary.getNodes() == null ? Collections.emptyList() : itinerary.getNodes();
        if (nodes.isEmpty()) {
            return "鏆傛棤璺嚎鎽樿";
        }
        return nodes.get(0).getPoiName() + " -> " + nodes.get(nodes.size() - 1).getPoiName();
    }

    private String resolveShareNote(SavedItinerary entity, ItineraryVO itinerary) {
        if (itinerary != null && StringUtils.hasText(itinerary.getShareNote())) {
            return itinerary.getShareNote().trim();
        }
        if (entity != null && StringUtils.hasText(entity.getShareNote())) {
            return entity.getShareNote().trim();
        }
        return null;
    }

    private List<String> resolveHighlights(ItineraryVO itinerary) {
        if (itinerary != null && itinerary.getOptions() != null && !itinerary.getOptions().isEmpty()) {
            ItineraryOptionVO selected = itinerary.getOptions().stream()
                    .filter(option -> Objects.equals(option.getOptionKey(), itinerary.getSelectedOptionKey()))
                    .findFirst()
                    .orElse(itinerary.getOptions().get(0));
            if (selected.getHighlights() != null && !selected.getHighlights().isEmpty()) {
                return selected.getHighlights().stream()
                        .filter(StringUtils::hasText)
                        .limit(4)
                        .toList();
            }
        }

        List<ItineraryNodeVO> nodes = itinerary == null || itinerary.getNodes() == null ? Collections.emptyList() : itinerary.getNodes();
        return nodes.stream()
                .map(ItineraryNodeVO::getPoiName)
                .filter(StringUtils::hasText)
                .limit(4)
                .toList();
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

    private CommunityCommentVO toCommunityComment(CommunityComment comment, User author, Long currentUserId) {
        CommunityCommentVO vo = new CommunityCommentVO();
        vo.setId(comment.getId());
        vo.setItineraryId(comment.getItineraryId());
        vo.setParentId(comment.getParentId());
        vo.setContent(comment.getContent());
        vo.setAuthorLabel(resolveAuthorLabel(author));
        vo.setCreateTime(comment.getCreateTime());
        vo.setMine(currentUserId != null && Objects.equals(currentUserId, comment.getUserId()));
        return vo;
    }

    private void evictCommunityCacheIfPublic(SavedItinerary entity) {
        if (entity != null && entity.getIsPublic() != null && entity.getIsPublic() == 1) {
            communityItineraryCacheService.markCommunityPageCacheDirty();
        }
    }
}

