package com.citytrip.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.citytrip.assembler.ItinerarySummaryAssembler;
import com.citytrip.common.BadRequestException;
import com.citytrip.mapper.CommunityCommentMapper;
import com.citytrip.mapper.CommunityLikeMapper;
import com.citytrip.mapper.PoiMapper;
import com.citytrip.mapper.UserMapper;
import com.citytrip.model.dto.GenerateReqDTO;
import com.citytrip.model.entity.CommunityComment;
import com.citytrip.model.entity.CommunityLike;
import com.citytrip.model.entity.Poi;
import com.citytrip.model.entity.SavedItinerary;
import com.citytrip.model.entity.User;
import com.citytrip.model.vo.AdminCommunityPostVO;
import com.citytrip.model.vo.AdminUserVO;
import com.citytrip.model.vo.ItineraryVO;
import com.citytrip.service.AdminService;
import com.citytrip.service.application.community.CommunityCacheInvalidationService;
import com.citytrip.service.geo.CityResolverService;
import com.citytrip.service.persistence.itinerary.SavedItineraryCodec;
import com.citytrip.service.persistence.itinerary.SavedItineraryRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AdminServiceImpl implements AdminService {

    private static final BigDecimal DEFAULT_PRIORITY_SCORE = new BigDecimal("3.0");
    private static final String DEFAULT_WALKING_LEVEL = "medium";
    private static final String DEFAULT_FROZEN_NOTE = "This POI has been temporarily frozen by an administrator.";
    private static final long MAX_PAGE_SIZE = 100L;

    private final UserMapper userMapper;
    private final PoiMapper poiMapper;
    private final SavedItineraryRepository savedItineraryRepository;
    private final SavedItineraryCodec savedItineraryCodec;
    private final CommunityCommentMapper communityCommentMapper;
    private final CommunityLikeMapper communityLikeMapper;
    private final CommunityCacheInvalidationService communityCacheInvalidationService;
    private final ItinerarySummaryAssembler itinerarySummaryAssembler;
    private final CityResolverService cityResolverService;

    public AdminServiceImpl(UserMapper userMapper,
                            PoiMapper poiMapper,
                            SavedItineraryRepository savedItineraryRepository,
                            SavedItineraryCodec savedItineraryCodec,
                            CommunityCommentMapper communityCommentMapper,
                            CommunityLikeMapper communityLikeMapper,
                            CommunityCacheInvalidationService communityCacheInvalidationService,
                            ItinerarySummaryAssembler itinerarySummaryAssembler,
                            CityResolverService cityResolverService) {
        this.userMapper = userMapper;
        this.poiMapper = poiMapper;
        this.savedItineraryRepository = savedItineraryRepository;
        this.savedItineraryCodec = savedItineraryCodec;
        this.communityCommentMapper = communityCommentMapper;
        this.communityLikeMapper = communityLikeMapper;
        this.communityCacheInvalidationService = communityCacheInvalidationService;
        this.itinerarySummaryAssembler = itinerarySummaryAssembler;
        this.cityResolverService = cityResolverService;
    }

    @Override
    public Page<AdminUserVO> getUserPage(int page, int size, String username) {
        long normalizedPage = normalizePage(page);
        long normalizedSize = normalizeSize(size);

        Page<AdminUserVO> result = new Page<>(normalizedPage, normalizedSize);
        result.setRecords(userMapper.selectAdminUserPageRecords(offset(normalizedPage, normalizedSize), normalizedSize, username)
                .stream()
                .map(this::toAdminUserVO)
                .toList());
        result.setTotal(userMapper.countAdminUsers(username));
        return result;
    }

    @Override
    public void updateUserStatus(Long userId, Integer status) {
        if (!isBinaryStatus(status)) {
            throw new BadRequestException("Invalid user status");
        }

        User user = userMapper.selectAdminUserById(userId);
        if (user == null) {
            throw new BadRequestException("User not found");
        }

        userMapper.updateAdminUserStatus(userId, status);
    }

    @Override
    public Page<Poi> getPoiPage(int page, int size, String name) {
        long normalizedPage = normalizePage(page);
        long normalizedSize = normalizeSize(size);

        Page<Poi> result = new Page<>(normalizedPage, normalizedSize);
        result.setRecords(poiMapper.selectAdminPoiPageRecords(offset(normalizedPage, normalizedSize), normalizedSize, name));
        result.setTotal(poiMapper.countAdminPois(name));
        return result;
    }

    @Override
    public Poi createPoi(Poi poi) {
        Poi prepared = preparePoiForSave(poi, null);
        prepared.setId(null);
        prepared.setStatusSource("admin");
        prepared.setStatusUpdatedAt(LocalDateTime.now());
        poiMapper.insertAdminPoi(prepared);
        return poiMapper.selectAdminPoiById(prepared.getId());
    }

    @Override
    public void updatePoi(Poi poi) {
        if (poi == null || poi.getId() == null) {
            throw new BadRequestException("Invalid POI payload");
        }

        Poi existing = requirePoi(poi.getId());
        Poi prepared = preparePoiForSave(poi, existing);
        prepared.setId(existing.getId());

        if (hasStatusChanged(existing, prepared)) {
            prepared.setStatusSource("admin");
            prepared.setStatusUpdatedAt(LocalDateTime.now());
        } else {
            prepared.setStatusSource(existing.getStatusSource());
            prepared.setStatusUpdatedAt(existing.getStatusUpdatedAt());
        }

        poiMapper.updateAdminPoi(prepared);
    }

    @Override
    public void deletePoi(Long poiId) {
        requirePoi(poiId);
        poiMapper.deleteAdminPoi(poiId);
    }

    @Override
    public void updatePoiTemporaryStatus(Long poiId, Integer temporarilyClosed, String statusNote) {
        if (!isBinaryStatus(temporarilyClosed)) {
            throw new BadRequestException("Invalid temporary status");
        }

        requirePoi(poiId);
        poiMapper.updatePoiTemporaryStatus(poiId, temporarilyClosed, resolveStatusNote(temporarilyClosed, statusNote));
    }

    @Override
    public Page<AdminCommunityPostVO> getCommunityPostPage(int page, int size, String keyword, Integer pinned, Integer deleted) {
        long normalizedPage = normalizePage(page);
        long normalizedSize = normalizeSize(size);
        String normalizedKeyword = trimToNull(keyword);

        List<SavedItinerary> entities = savedItineraryRepository.listPublicVisible();
        List<Long> itineraryIds = entities.stream().map(SavedItinerary::getId).filter(Objects::nonNull).toList();
        Map<Long, User> userMap = loadUsersByIds(entities.stream().map(SavedItinerary::getUserId).filter(Objects::nonNull).collect(Collectors.toSet()));
        Map<Long, Long> commentCountMap = loadCommentCountMap(itineraryIds);
        Map<Long, Long> likeCountMap = loadLikeCountMap(itineraryIds);

        List<AdminCommunityPostVO> filtered = entities.stream()
                .map(entity -> toAdminCommunityPost(entity, userMap.get(entity.getUserId()), commentCountMap, likeCountMap))
                .filter(Objects::nonNull)
                .filter(item -> matchesKeyword(item, normalizedKeyword))
                .filter(item -> pinned == null || Boolean.TRUE.equals(item.getGlobalPinned()) == Integer.valueOf(1).equals(pinned))
                .filter(item -> deleted == null || Boolean.TRUE.equals(item.getDeleted()) == Integer.valueOf(1).equals(deleted))
                .toList();

        int fromIndex = (int) Math.min(offset(normalizedPage, normalizedSize), filtered.size());
        int toIndex = (int) Math.min(fromIndex + normalizedSize, filtered.size());

        Page<AdminCommunityPostVO> result = new Page<>(normalizedPage, normalizedSize);
        result.setTotal(filtered.size());
        result.setRecords(filtered.subList(fromIndex, toIndex));
        return result;
    }

    @Override
    public void updateCommunityPostPin(Long adminUserId, Long itineraryId, boolean pinned) {
        SavedItinerary entity = savedItineraryRepository.requireForUpdate(itineraryId);
        if (pinned && Integer.valueOf(1).equals(entity.getIsDeleted())) {
            throw new BadRequestException("Deleted posts cannot be pinned");
        }
        if (pinned && !Integer.valueOf(1).equals(entity.getIsPublic())) {
            throw new BadRequestException("Only public posts can be pinned");
        }
        entity.setIsGlobalPinned(pinned ? 1 : 0);
        entity.setGlobalPinnedAt(pinned ? LocalDateTime.now() : null);
        entity.setGlobalPinnedBy(pinned ? adminUserId : null);
        savedItineraryRepository.saveOrUpdate(entity);
        communityCacheInvalidationService.markDirty();
    }

    @Override
    public void deleteCommunityPost(Long adminUserId, Long itineraryId) {
        SavedItinerary entity = savedItineraryRepository.requireForUpdate(itineraryId);
        entity.setIsDeleted(1);
        entity.setDeletedAt(LocalDateTime.now());
        entity.setDeletedBy(adminUserId);
        entity.setIsPublic(0);
        entity.setIsGlobalPinned(0);
        entity.setGlobalPinnedAt(null);
        entity.setGlobalPinnedBy(null);
        entity.setPinnedCommentId(null);
        savedItineraryRepository.saveOrUpdate(entity);
        communityCacheInvalidationService.markDirty();
    }

    private AdminCommunityPostVO toAdminCommunityPost(SavedItinerary entity,
                                                      User author,
                                                      Map<Long, Long> commentCountMap,
                                                      Map<Long, Long> likeCountMap) {
        try {
            GenerateReqDTO req = savedItineraryCodec.readRequest(entity);
            ItineraryVO itinerary = savedItineraryCodec.readItinerary(entity);
            AdminCommunityPostVO vo = new AdminCommunityPostVO();
            vo.setId(entity.getId());
            vo.setUserId(entity.getUserId());
            vo.setTitle(itinerarySummaryAssembler.buildTitle(req, itinerary));
            vo.setAuthorLabel(itinerarySummaryAssembler.resolveAuthorLabel(author));
            vo.setCoverImageUrl(itinerarySummaryAssembler.resolveCoverImage(itinerary));
            vo.setShareNote(itinerarySummaryAssembler.resolveShareNote(entity, itinerary));
            vo.setThemes(req == null || req.getThemes() == null ? Collections.emptyList() : req.getThemes());
            vo.setTotalDuration(entity.getTotalDuration());
            vo.setTotalCost(entity.getTotalCost());
            vo.setNodeCount(entity.getNodeCount());
            vo.setLikeCount(likeCountMap.getOrDefault(entity.getId(), 0L));
            vo.setCommentCount(commentCountMap.getOrDefault(entity.getId(), 0L));
            vo.setGlobalPinned(Integer.valueOf(1).equals(entity.getIsGlobalPinned()));
            vo.setDeleted(Integer.valueOf(1).equals(entity.getIsDeleted()));
            vo.setGlobalPinnedAt(entity.getGlobalPinnedAt());
            vo.setDeletedAt(entity.getDeletedAt());
            vo.setDeletedBy(entity.getDeletedBy());
            vo.setUpdatedAt(entity.getUpdateTime());
            return vo;
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private Map<Long, User> loadUsersByIds(Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return userMapper.selectBatchIds(userIds).stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(User::getId, user -> user, (left, right) -> left, LinkedHashMap::new));
    }

    private Map<Long, Long> loadCommentCountMap(List<Long> itineraryIds) {
        if (itineraryIds == null || itineraryIds.isEmpty()) {
            return Collections.emptyMap();
        }
        QueryWrapper<CommunityComment> wrapper = new QueryWrapper<>();
        wrapper.in("itinerary_id", itineraryIds);
        return communityCommentMapper.selectList(wrapper).stream()
                .filter(item -> item.getItineraryId() != null)
                .collect(Collectors.groupingBy(CommunityComment::getItineraryId, LinkedHashMap::new, Collectors.counting()));
    }

    private Map<Long, Long> loadLikeCountMap(List<Long> itineraryIds) {
        if (itineraryIds == null || itineraryIds.isEmpty()) {
            return Collections.emptyMap();
        }
        QueryWrapper<CommunityLike> wrapper = new QueryWrapper<>();
        wrapper.in("itinerary_id", itineraryIds);
        return communityLikeMapper.selectList(wrapper).stream()
                .filter(item -> item.getItineraryId() != null)
                .collect(Collectors.groupingBy(CommunityLike::getItineraryId, LinkedHashMap::new, Collectors.counting()));
    }

    private boolean matchesKeyword(AdminCommunityPostVO item, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return true;
        }
        String normalized = keyword.toLowerCase(Locale.ROOT);
        return containsIgnoreCase(item.getTitle(), normalized)
                || containsIgnoreCase(item.getAuthorLabel(), normalized)
                || containsIgnoreCase(item.getShareNote(), normalized);
    }

    private boolean containsIgnoreCase(String source, String keyword) {
        return StringUtils.hasText(source) && source.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private Poi requirePoi(Long poiId) {
        Poi poi = poiMapper.selectAdminPoiById(poiId);
        if (poi == null) {
            throw new BadRequestException("POI not found");
        }
        return poi;
    }

    private Poi preparePoiForSave(Poi source, Poi existing) {
        if (source == null) {
            throw new BadRequestException("Invalid POI payload");
        }
        if (!StringUtils.hasText(source.getName())) {
            throw new BadRequestException("POI name is required");
        }
        if (!StringUtils.hasText(source.getCategory())) {
            throw new BadRequestException("POI category is required");
        }
        if (source.getStayDuration() == null || source.getStayDuration() <= 0) {
            throw new BadRequestException("Stay duration must be greater than 0");
        }

        Poi prepared = new Poi();
        prepared.setName(source.getName().trim());
        String resolvedCityName = cityResolverService.resolveCityName(source.getCityName(), source.getCityCode());
        String resolvedCityCode = cityResolverService.resolveCityCode(source.getCityCode(), resolvedCityName);
        prepared.setCityName(resolvedCityName);
        prepared.setCityCode(resolvedCityCode);
        prepared.setCategory(source.getCategory().trim());
        prepared.setDistrict(trimToNull(source.getDistrict()));
        prepared.setAddress(trimToNull(source.getAddress()));
        prepared.setLatitude(source.getLatitude());
        prepared.setLongitude(source.getLongitude());
        prepared.setOpenTime(source.getOpenTime());
        prepared.setCloseTime(source.getCloseTime());
        prepared.setClosedWeekdays(normalizeClosedWeekdays(source.getClosedWeekdays()));
        prepared.setTemporarilyClosed(normalizeBinaryFlag(source.getTemporarilyClosed(), "Invalid temporary status"));
        prepared.setStatusNote(resolveStatusNote(prepared.getTemporarilyClosed(), source.getStatusNote()));
        prepared.setAvgCost(source.getAvgCost() == null ? BigDecimal.ZERO : source.getAvgCost());
        prepared.setStayDuration(source.getStayDuration());
        prepared.setIndoor(normalizeBinaryFlag(source.getIndoor(), "Invalid indoor flag"));
        prepared.setNightAvailable(normalizeBinaryFlag(source.getNightAvailable(), "Invalid night flag"));
        prepared.setRainFriendly(normalizeBinaryFlag(source.getRainFriendly(), "Invalid rain-friendly flag"));
        prepared.setWalkingLevel(textOrDefault(source.getWalkingLevel(), DEFAULT_WALKING_LEVEL));
        prepared.setTags(trimToNull(source.getTags()));
        prepared.setSuitableFor(trimToNull(source.getSuitableFor()));
        prepared.setDescription(trimToNull(source.getDescription()));
        prepared.setPriorityScore(source.getPriorityScore() == null ? DEFAULT_PRIORITY_SCORE : source.getPriorityScore());

        if (existing != null) {
            prepared.setStatusSource(existing.getStatusSource());
            prepared.setStatusUpdatedAt(existing.getStatusUpdatedAt());
        }
        return prepared;
    }

    private boolean hasStatusChanged(Poi existing, Poi prepared) {
        return !Objects.equals(existing.getTemporarilyClosed(), prepared.getTemporarilyClosed())
                || !Objects.equals(trimToNull(existing.getStatusNote()), prepared.getStatusNote())
                || !Objects.equals(normalizeClosedWeekdays(existing.getClosedWeekdays()), prepared.getClosedWeekdays())
                || !Objects.equals(existing.getOpenTime(), prepared.getOpenTime())
                || !Objects.equals(existing.getCloseTime(), prepared.getCloseTime());
    }

    private String resolveStatusNote(Integer temporarilyClosed, String statusNote) {
        String trimmed = trimToNull(statusNote);
        if (Integer.valueOf(1).equals(temporarilyClosed)) {
            return trimmed != null ? trimmed : DEFAULT_FROZEN_NOTE;
        }
        return trimmed;
    }

    private Integer normalizeBinaryFlag(Integer value, String errorMessage) {
        if (value == null) {
            return 0;
        }
        if (!isBinaryStatus(value)) {
            throw new BadRequestException(errorMessage);
        }
        return value;
    }

    private boolean isBinaryStatus(Integer value) {
        return Integer.valueOf(0).equals(value) || Integer.valueOf(1).equals(value);
    }

    private String normalizeClosedWeekdays(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String normalized = Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(value -> value.toUpperCase(Locale.ROOT))
                .distinct()
                .collect(Collectors.joining(","));
        return normalized.isEmpty() ? null : normalized;
    }

    private String textOrDefault(String value, String defaultValue) {
        String trimmed = trimToNull(value);
        return trimmed == null ? defaultValue : trimmed;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private long normalizePage(int page) {
        return Math.max(page, 1);
    }

    private long normalizeSize(int size) {
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }

    private long offset(long page, long size) {
        return (page - 1) * size;
    }

    private AdminUserVO toAdminUserVO(User user) {
        AdminUserVO vo = new AdminUserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setRole(user.getRole());
        vo.setStatus(user.getStatus());
        vo.setCreateTime(user.getCreateTime());
        vo.setUpdateTime(user.getUpdateTime());
        return vo;
    }
}
