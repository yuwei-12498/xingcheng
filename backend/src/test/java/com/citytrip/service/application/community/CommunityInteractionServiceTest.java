package com.citytrip.service.application.community;

import com.citytrip.common.BadRequestException;
import com.citytrip.mapper.CommunityCommentMapper;
import com.citytrip.mapper.CommunityLikeMapper;
import com.citytrip.mapper.UserMapper;
import com.citytrip.model.entity.CommunityComment;
import com.citytrip.model.entity.SavedItinerary;
import com.citytrip.service.persistence.itinerary.SavedItineraryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommunityInteractionServiceTest {

    @Mock
    private SavedItineraryRepository savedItineraryRepository;

    @Mock
    private CommunityCommentMapper communityCommentMapper;

    @Mock
    private CommunityLikeMapper communityLikeMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private CommunityCacheInvalidationService communityCacheInvalidationService;

    @Mock
    private CommunityItineraryQueryService communityItineraryQueryService;

    @InjectMocks
    private CommunityInteractionService communityInteractionService;

    @Test
    void pinCommentRejectsNestedReply() {
        SavedItinerary entity = new SavedItinerary();
        entity.setId(9L);
        entity.setUserId(5L);
        entity.setIsPublic(1);

        CommunityComment reply = new CommunityComment();
        reply.setId(101L);
        reply.setItineraryId(9L);
        reply.setParentId(88L);

        when(savedItineraryRepository.requireOwnedForUpdate(5L, 9L)).thenReturn(entity);
        when(communityCommentMapper.selectById(101L)).thenReturn(reply);

        assertThatThrownBy(() -> communityInteractionService.pinComment(5L, 9L, 101L))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("only root comments can be pinned");
    }

    @Test
    void deletePostClearsPublicAndPinnedState() {
        SavedItinerary entity = new SavedItinerary();
        entity.setId(9L);
        entity.setUserId(5L);
        entity.setIsPublic(1);
        entity.setIsGlobalPinned(1);
        entity.setPinnedCommentId(101L);

        when(savedItineraryRepository.requireOwnedForUpdate(5L, 9L)).thenReturn(entity);

        communityInteractionService.deletePost(5L, 9L);

        verify(savedItineraryRepository).saveOrUpdate(argThat(saved ->
                Integer.valueOf(1).equals(saved.getIsDeleted())
                        && Integer.valueOf(0).equals(saved.getIsPublic())
                        && Integer.valueOf(0).equals(saved.getIsGlobalPinned())
                        && saved.getPinnedCommentId() == null
                        && Long.valueOf(5L).equals(saved.getDeletedBy())
                        && saved.getDeletedAt() != null));
    }
}