package com.citytrip.service.application.community;

import com.citytrip.assembler.ItinerarySummaryAssembler;
import com.citytrip.mapper.CommunityCommentMapper;
import com.citytrip.mapper.CommunityLikeMapper;
import com.citytrip.mapper.UserMapper;
import com.citytrip.model.dto.GenerateReqDTO;
import com.citytrip.model.entity.CommunityComment;
import com.citytrip.model.entity.SavedItinerary;
import com.citytrip.model.entity.User;
import com.citytrip.model.vo.CommunityItineraryDetailVO;
import com.citytrip.model.vo.CommunityItineraryPageVO;
import com.citytrip.model.vo.CommunityItineraryVO;
import com.citytrip.model.vo.ItineraryNodeVO;
import com.citytrip.model.vo.ItineraryVO;
import com.citytrip.service.impl.CommunityItineraryCacheService;
import com.citytrip.service.persistence.itinerary.SavedItineraryCodec;
import com.citytrip.service.persistence.itinerary.SavedItineraryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommunityItineraryQueryServiceTest {

    @Test
    void listPublicShouldUseSemanticRankingWhenKeywordPresent() throws Exception {
        SavedItineraryRepository repository = mock(SavedItineraryRepository.class);
        CommunityCommentMapper commentMapper = mock(CommunityCommentMapper.class);
        CommunityLikeMapper likeMapper = mock(CommunityLikeMapper.class);
        UserMapper userMapper = mock(UserMapper.class);
        SavedItineraryCodec codec = mock(SavedItineraryCodec.class);
        CommunitySemanticSearchService semanticSearchService = mock(CommunitySemanticSearchService.class);

        CommunityItineraryQueryService service = new CommunityItineraryQueryService(
                repository,
                commentMapper,
                likeMapper,
                userMapper,
                codec,
                new ItinerarySummaryAssembler(),
                new CommunityItineraryCacheService(false, null, null, new ObjectMapper()),
                semanticSearchService
        );

        SavedItinerary first = buildEntity(1L, 7L);
        SavedItinerary second = buildEntity(2L, 7L);
        User author = new User();
        author.setId(7L);
        author.setNickname("Tester");

        when(repository.listPublicVisible()).thenReturn(List.of(first, second));
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(author));
        when(codec.readRequest(any())).thenReturn(buildReq("Citywalk"));
        when(codec.readItinerary(first)).thenReturn(buildItinerary("人民公园"));
        when(codec.readItinerary(second)).thenReturn(buildItinerary("九眼桥夜游"));
        when(commentMapper.selectList(any())).thenReturn(List.of());
        when(likeMapper.selectList(any())).thenReturn(List.of());
        when(semanticSearchService.isSemanticModelReady()).thenReturn(true);
        when(semanticSearchService.rank(any(), any())).thenReturn(List.of(
                new CommunitySemanticSearchService.ScoredCommunityCandidate(2L, 0.98D),
                new CommunitySemanticSearchService.ScoredCommunityCandidate(1L, 0.87D)
        ));

        CommunityItineraryPageVO page = service.listPublic(1, 12, "latest", "适合情侣夜游散步", null, null);

        assertThat(page.getRecords()).extracting(CommunityItineraryVO::getId).containsExactly(2L, 1L);
    }

    @Test
    void listPublicShouldUseKeywordFilterWhenSemanticModelIsNotReady() throws Exception {
        SavedItineraryRepository repository = mock(SavedItineraryRepository.class);
        CommunityCommentMapper commentMapper = mock(CommunityCommentMapper.class);
        CommunityLikeMapper likeMapper = mock(CommunityLikeMapper.class);
        UserMapper userMapper = mock(UserMapper.class);
        SavedItineraryCodec codec = mock(SavedItineraryCodec.class);
        CommunitySemanticSearchService semanticSearchService = mock(CommunitySemanticSearchService.class);

        CommunityItineraryQueryService service = new CommunityItineraryQueryService(
                repository,
                commentMapper,
                likeMapper,
                userMapper,
                codec,
                new ItinerarySummaryAssembler(),
                new CommunityItineraryCacheService(false, null, null, new ObjectMapper()),
                semanticSearchService
        );

        SavedItinerary museum = buildEntity(1L, 7L);
        SavedItinerary nightWalk = buildEntity(2L, 7L);
        User author = new User();
        author.setId(7L);
        author.setNickname("Tester");

        when(repository.listPublicVisible()).thenReturn(List.of(museum, nightWalk));
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(author));
        when(codec.readRequest(museum)).thenReturn(buildReq("museum"));
        when(codec.readRequest(nightWalk)).thenReturn(buildReq("nightlife"));
        when(codec.readItinerary(museum)).thenReturn(buildItinerary("City Museum"));
        when(codec.readItinerary(nightWalk)).thenReturn(buildItinerary("River Bar Street"));
        when(commentMapper.selectList(any())).thenReturn(List.of());
        when(likeMapper.selectList(any())).thenReturn(List.of());
        when(semanticSearchService.isSemanticModelReady()).thenReturn(false);

        CommunityItineraryPageVO page = service.listPublic(1, 12, "latest", "museum", null, null);

        assertThat(page.getRecords()).extracting(CommunityItineraryVO::getId).containsExactly(1L);
        verify(semanticSearchService, never()).rank(any(), any());
    }

    @Test
    void listPublicFallsBackToZeroCountsWhenCommunityTablesAreUnavailable() throws Exception {
        SavedItineraryRepository repository = mock(SavedItineraryRepository.class);
        CommunityCommentMapper commentMapper = mock(CommunityCommentMapper.class);
        CommunityLikeMapper likeMapper = mock(CommunityLikeMapper.class);
        UserMapper userMapper = mock(UserMapper.class);
        SavedItineraryCodec codec = mock(SavedItineraryCodec.class);

        CommunityItineraryQueryService service = new CommunityItineraryQueryService(
                repository,
                commentMapper,
                likeMapper,
                userMapper,
                codec,
                new ItinerarySummaryAssembler(),
                new CommunityItineraryCacheService(false, null, null, new ObjectMapper())
        );

        SavedItinerary entity = new SavedItinerary();
        entity.setId(88L);
        entity.setUserId(7L);
        entity.setNodeCount(1);
        entity.setTotalDuration(90);
        entity.setTotalCost(new BigDecimal("30"));
        entity.setUpdateTime(LocalDateTime.now());

        User author = new User();
        author.setId(7L);
        author.setNickname("Tester");

        GenerateReqDTO req = new GenerateReqDTO();
        req.setThemes(List.of("museum"));
        ItineraryNodeVO node = new ItineraryNodeVO();
        node.setPoiName("City Museum");
        ItineraryVO itinerary = new ItineraryVO();
        itinerary.setNodes(List.of(node));

        when(repository.listPublicVisible()).thenReturn(List.of(entity));
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(author));
        when(codec.readRequest(entity)).thenReturn(req);
        when(codec.readItinerary(entity)).thenReturn(itinerary);
        when(commentMapper.selectList(any())).thenThrow(new DataAccessResourceFailureException("comment table down"));
        when(likeMapper.selectList(any())).thenThrow(new DataAccessResourceFailureException("like table down"));

        CommunityItineraryPageVO page = service.listPublic(1, 12);

        assertThat(page.getTotal()).isEqualTo(1L);
        assertThat(page.getRecords()).hasSize(1);
        assertThat(page.getRecords().get(0).getCommentCount()).isZero();
        assertThat(page.getRecords().get(0).getLikeCount()).isZero();
        assertThat(page.getRecords().get(0).getAuthorLabel()).isEqualTo("Tester");
    }

    @Test
    void getPublicDetailIncludesPinnedCommentAndPermissionFlags() throws Exception {
        SavedItineraryRepository repository = mock(SavedItineraryRepository.class);
        CommunityCommentMapper commentMapper = mock(CommunityCommentMapper.class);
        CommunityLikeMapper likeMapper = mock(CommunityLikeMapper.class);
        UserMapper userMapper = mock(UserMapper.class);
        SavedItineraryCodec codec = mock(SavedItineraryCodec.class);

        CommunityItineraryQueryService service = new CommunityItineraryQueryService(
                repository,
                commentMapper,
                likeMapper,
                userMapper,
                codec,
                new ItinerarySummaryAssembler(),
                new CommunityItineraryCacheService(false, null, null, new ObjectMapper())
        );

        SavedItinerary entity = new SavedItinerary();
        entity.setId(9L);
        entity.setUserId(7L);
        entity.setIsPublic(1);
        entity.setIsDeleted(0);
        entity.setIsGlobalPinned(1);
        entity.setPinnedCommentId(101L);
        entity.setNodeCount(1);
        entity.setTotalDuration(80);
        entity.setTotalCost(new BigDecimal("66"));
        entity.setUpdateTime(LocalDateTime.now());

        User author = new User();
        author.setId(7L);
        author.setNickname("Alice");
        author.setRole(0);

        GenerateReqDTO req = new GenerateReqDTO();
        req.setThemes(List.of("Citywalk"));

        ItineraryNodeVO node = new ItineraryNodeVO();
        node.setPoiName("Jiangtan");
        ItineraryVO itinerary = new ItineraryVO();
        itinerary.setNodes(List.of(node));

        CommunityComment pinnedComment = new CommunityComment();
        pinnedComment.setId(101L);
        pinnedComment.setItineraryId(9L);
        pinnedComment.setUserId(8L);
        pinnedComment.setContent("Sunset works best for this route");

        when(repository.requirePublic(9L)).thenReturn(entity);
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(author));
        when(userMapper.selectById(7L)).thenReturn(author);
        when(codec.readRequest(entity)).thenReturn(req);
        when(codec.readItinerary(entity)).thenReturn(itinerary);
        when(commentMapper.selectCount(any())).thenReturn(1L);
        when(likeMapper.selectCount(any())).thenReturn(2L);
        when(likeMapper.selectOne(any())).thenReturn(null);
        when(commentMapper.selectById(101L)).thenReturn(pinnedComment);

        CommunityItineraryDetailVO detail = service.getPublicDetail(9L, 7L);

        assertThat(detail.getGlobalPinned()).isTrue();
        assertThat(detail.getPinnedCommentId()).isEqualTo(101L);
        assertThat(detail.getPinnedComment().getId()).isEqualTo(101L);
        assertThat(detail.getCanDelete()).isTrue();
        assertThat(detail.getCanPinComment()).isTrue();
    }

    private SavedItinerary buildEntity(Long id, Long userId) {
        SavedItinerary entity = new SavedItinerary();
        entity.setId(id);
        entity.setUserId(userId);
        entity.setNodeCount(1);
        entity.setTotalDuration(90);
        entity.setTotalCost(new BigDecimal("30"));
        entity.setUpdateTime(LocalDateTime.now());
        entity.setIsPublic(1);
        entity.setIsDeleted(0);
        return entity;
    }

    private GenerateReqDTO buildReq(String theme) {
        GenerateReqDTO req = new GenerateReqDTO();
        req.setThemes(List.of(theme));
        return req;
    }

    private ItineraryVO buildItinerary(String poiName) {
        ItineraryNodeVO node = new ItineraryNodeVO();
        node.setPoiName(poiName);
        ItineraryVO itinerary = new ItineraryVO();
        itinerary.setNodes(List.of(node));
        return itinerary;
    }
}
