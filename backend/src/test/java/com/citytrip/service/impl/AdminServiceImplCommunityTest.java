package com.citytrip.service.impl;

import com.citytrip.assembler.ItinerarySummaryAssembler;
import com.citytrip.mapper.CommunityCommentMapper;
import com.citytrip.mapper.CommunityLikeMapper;
import com.citytrip.mapper.PoiMapper;
import com.citytrip.mapper.UserMapper;
import com.citytrip.model.entity.SavedItinerary;
import com.citytrip.service.application.community.CommunityCacheInvalidationService;
import com.citytrip.service.geo.CityResolverService;
import com.citytrip.service.persistence.itinerary.SavedItineraryCodec;
import com.citytrip.service.persistence.itinerary.SavedItineraryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServiceImplCommunityTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private PoiMapper poiMapper;

    @Mock
    private SavedItineraryRepository savedItineraryRepository;

    @Mock
    private SavedItineraryCodec savedItineraryCodec;

    @Mock
    private CommunityCommentMapper communityCommentMapper;

    @Mock
    private CommunityLikeMapper communityLikeMapper;

    @Mock
    private CommunityCacheInvalidationService communityCacheInvalidationService;

    @Mock
    private ItinerarySummaryAssembler itinerarySummaryAssembler;

    @Mock
    private CityResolverService cityResolverService;

    @InjectMocks
    private AdminServiceImpl adminService;

    @Test
    void updateCommunityPostPinMarksPinnedState() {
        SavedItinerary entity = new SavedItinerary();
        entity.setId(9L);
        entity.setIsPublic(1);
        entity.setIsDeleted(0);

        when(savedItineraryRepository.requireForUpdate(9L)).thenReturn(entity);

        adminService.updateCommunityPostPin(99L, 9L, true);

        assertThat(entity.getIsGlobalPinned()).isEqualTo(1);
        assertThat(entity.getGlobalPinnedBy()).isEqualTo(99L);
        assertThat(entity.getGlobalPinnedAt()).isNotNull();
    }

    @Test
    void getCommunityPostPageShouldOnlyReadPublicVisibleCommunityRecords() {
        when(savedItineraryRepository.listPublicVisible()).thenReturn(List.of());

        adminService.getCommunityPostPage(1, 10, "hotel", null, null);

        verify(savedItineraryRepository).listPublicVisible();
        verify(savedItineraryRepository, never()).listAll();
    }
}
