package com.citytrip.service.application.community;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.citytrip.assembler.ItinerarySummaryAssembler;
import com.citytrip.common.BadRequestException;
import com.citytrip.mapper.CommunityCommentMapper;
import com.citytrip.mapper.CommunityLikeMapper;
import com.citytrip.mapper.UserMapper;
import com.citytrip.model.dto.CommunityCommentReqDTO;
import com.citytrip.model.entity.CommunityComment;
import com.citytrip.model.entity.CommunityLike;
import com.citytrip.model.entity.SavedItinerary;
import com.citytrip.model.entity.User;
import com.citytrip.model.vo.CommunityCommentVO;
import com.citytrip.model.vo.CommunityItineraryDetailVO;
import com.citytrip.service.persistence.itinerary.SavedItineraryRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CommunityInteractionService {

    private final SavedItineraryRepository savedItineraryRepository;
    private final CommunityCommentMapper communityCommentMapper;
    private final CommunityLikeMapper communityLikeMapper;
    private final UserMapper userMapper;
    private final ItinerarySummaryAssembler itinerarySummaryAssembler;
    private final CommunityCacheInvalidationService communityCacheInvalidationService;
    private final CommunityItineraryQueryService communityItineraryQueryService;

    public CommunityInteractionService(SavedItineraryRepository savedItineraryRepository,
                                       CommunityCommentMapper communityCommentMapper,
                                       CommunityLikeMapper communityLikeMapper,
                                       UserMapper userMapper,
                                       ItinerarySummaryAssembler itinerarySummaryAssembler,
                                       CommunityCacheInvalidationService communityCacheInvalidationService,
                                       CommunityItineraryQueryService communityItineraryQueryService) {
        this.savedItineraryRepository = savedItineraryRepository;
        this.communityCommentMapper = communityCommentMapper;
        this.communityLikeMapper = communityLikeMapper;
        this.userMapper = userMapper;
        this.itinerarySummaryAssembler = itinerarySummaryAssembler;
        this.communityCacheInvalidationService = communityCacheInvalidationService;
        this.communityItineraryQueryService = communityItineraryQueryService;
    }

    public List<CommunityCommentVO> listComments(Long itineraryId, Long currentUserId) {
        SavedItinerary entity = savedItineraryRepository.requirePublic(itineraryId);
        QueryWrapper<CommunityComment> wrapper = new QueryWrapper<>();
        wrapper.eq("itinerary_id", entity.getId()).orderByAsc("create_time").orderByAsc("id");
        List<CommunityComment> comments = communityCommentMapper.selectList(wrapper);
        Map<Long, User> userMap = loadUsersByIds(comments.stream()
                .map(CommunityComment::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new)));

        Long pinnedCommentId = entity.getPinnedCommentId();
        boolean authorCanPin = currentUserId != null && Objects.equals(currentUserId, entity.getUserId());

        Map<Long, List<CommunityCommentVO>> repliesMap = comments.stream()
                .filter(comment -> comment.getParentId() != null)
                .map(comment -> toCommunityComment(comment, userMap.get(comment.getUserId()), currentUserId, false, false))
                .collect(Collectors.groupingBy(CommunityCommentVO::getParentId, LinkedHashMap::new, Collectors.toList()));

        return comments.stream()
                .filter(comment -> comment.getParentId() == null)
                .map(comment -> {
                    boolean pinned = Objects.equals(comment.getId(), pinnedCommentId);
                    CommunityCommentVO root = toCommunityComment(comment, userMap.get(comment.getUserId()), currentUserId, pinned, authorCanPin);
                    root.setReplies(repliesMap.getOrDefault(root.getId(), Collections.emptyList()));
                    return root;
                })
                .sorted(Comparator.comparing((CommunityCommentVO vo) -> !Boolean.TRUE.equals(vo.getPinned()))
                        .thenComparing(CommunityCommentVO::getCreateTime, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(CommunityCommentVO::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    @Transactional
    public CommunityCommentVO addComment(Long userId, Long itineraryId, CommunityCommentReqDTO req) {
        if (userId == null) {
            throw new BadRequestException("login is required");
        }

        SavedItinerary entity = savedItineraryRepository.requirePublic(itineraryId);
        String content = normalizeCommentContent(req == null ? null : req.getContent());
        if (!StringUtils.hasText(content)) {
            throw new BadRequestException("comment content is required");
        }

        Long parentId = req == null ? null : req.getParentId();
        if (parentId != null) {
            CommunityComment parent = communityCommentMapper.selectById(parentId);
            if (parent == null || !Objects.equals(parent.getItineraryId(), entity.getId())) {
                throw new BadRequestException("comment parent does not belong to the itinerary");
            }
            if (parent.getParentId() != null) {
                throw new BadRequestException("only one-level replies are supported");
            }
        }

        CommunityComment comment = new CommunityComment();
        comment.setItineraryId(entity.getId());
        comment.setUserId(userId);
        comment.setParentId(parentId);
        comment.setContent(content);
        communityCommentMapper.insert(comment);
        comment = communityCommentMapper.selectById(comment.getId());
        communityCacheInvalidationService.markDirty();

        User author = userMapper.selectById(userId);
        return toCommunityComment(comment, author, userId, false, false);
    }

    @Transactional
    public CommunityItineraryDetailVO like(Long userId, Long itineraryId) {
        if (userId == null) {
            throw new BadRequestException("login is required");
        }

        SavedItinerary entity = savedItineraryRepository.requirePublic(itineraryId);
        try {
            CommunityLike like = new CommunityLike();
            like.setItineraryId(entity.getId());
            like.setUserId(userId);
            communityLikeMapper.insert(like);
        } catch (DuplicateKeyException ignore) {
            // idempotent
        }
        communityCacheInvalidationService.markDirty();
        return communityItineraryQueryService.getPublicDetail(entity.getId(), userId);
    }

    @Transactional
    public CommunityItineraryDetailVO unlike(Long userId, Long itineraryId) {
        if (userId == null) {
            throw new BadRequestException("login is required");
        }

        SavedItinerary entity = savedItineraryRepository.requirePublic(itineraryId);
        QueryWrapper<CommunityLike> wrapper = new QueryWrapper<>();
        wrapper.eq("itinerary_id", entity.getId()).eq("user_id", userId);
        communityLikeMapper.delete(wrapper);
        communityCacheInvalidationService.markDirty();
        return communityItineraryQueryService.getPublicDetail(entity.getId(), userId);
    }

    @Transactional
    public void deletePost(Long userId, Long itineraryId) {
        SavedItinerary entity = savedItineraryRepository.requireOwnedForUpdate(userId, itineraryId);
        markPostDeleted(entity, userId);
        savedItineraryRepository.saveOrUpdate(entity);
        communityCacheInvalidationService.markDirty();
    }

    @Transactional
    public CommunityItineraryDetailVO pinComment(Long userId, Long itineraryId, Long commentId) {
        SavedItinerary entity = savedItineraryRepository.requireOwnedForUpdate(userId, itineraryId);
        if (!Integer.valueOf(1).equals(entity.getIsPublic()) || Integer.valueOf(1).equals(entity.getIsDeleted())) {
            throw new BadRequestException("only public posts can pin comments");
        }
        CommunityComment comment = communityCommentMapper.selectById(commentId);
        if (comment == null || !Objects.equals(comment.getItineraryId(), entity.getId())) {
            throw new BadRequestException("comment does not belong to the itinerary");
        }
        if (comment.getParentId() != null) {
            throw new BadRequestException("only root comments can be pinned");
        }

        entity.setPinnedCommentId(comment.getId());
        savedItineraryRepository.saveOrUpdate(entity);
        communityCacheInvalidationService.markDirty();
        return communityItineraryQueryService.getPublicDetail(entity.getId(), userId);
    }

    public void markPostDeleted(SavedItinerary entity, Long actorUserId) {
        entity.setIsDeleted(1);
        entity.setDeletedAt(LocalDateTime.now());
        entity.setDeletedBy(actorUserId);
        entity.setIsPublic(0);
        entity.setIsGlobalPinned(0);
        entity.setGlobalPinnedAt(null);
        entity.setGlobalPinnedBy(null);
        entity.setPinnedCommentId(null);
    }

    private Map<Long, User> loadUsersByIds(Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return userMapper.selectBatchIds(userIds).stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(User::getId, user -> user, (left, right) -> left, LinkedHashMap::new));
    }

    private CommunityCommentVO toCommunityComment(CommunityComment comment,
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
        vo.setCanPin(canPin && comment.getParentId() == null);
        return vo;
    }

    private String normalizeCommentContent(String content) {
        if (!StringUtils.hasText(content)) {
            return null;
        }
        String value = content.trim();
        return value.length() > 300 ? value.substring(0, 300) : value;
    }
}