package com.citytrip.service.application.itinerary;

import com.citytrip.common.BadRequestException;
import com.citytrip.model.dto.GenerateReqDTO;
import com.citytrip.model.dto.PublicStatusReqDTO;
import com.citytrip.model.entity.SavedItinerary;
import com.citytrip.model.vo.ItineraryVO;
import com.citytrip.service.application.community.CommunityCacheInvalidationService;
import com.citytrip.service.persistence.itinerary.SavedItineraryCodec;
import com.citytrip.service.persistence.itinerary.SavedItineraryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SavedItineraryCommandServiceTest {

    @Mock
    private SavedItineraryRepository savedItineraryRepository;

    @Mock
    private SavedItineraryCodec savedItineraryCodec;

    @Mock
    private CommunityCacheInvalidationService communityCacheInvalidationService;

    @InjectMocks
    private SavedItineraryCommandService savedItineraryCommandService;

    @Test
    void saveReturnsOriginalItineraryWhenUserIsAnonymous() {
        GenerateReqDTO req = new GenerateReqDTO();
        ItineraryVO itinerary = new ItineraryVO();

        ItineraryVO result = savedItineraryCommandService.save(null, null, req, itinerary);

        assertThat(result).isSameAs(itinerary);
        assertThat(itinerary.getOriginalReq()).isSameAs(req);
        verifyNoInteractions(savedItineraryRepository, savedItineraryCodec, communityCacheInvalidationService);
    }

    @Test
    void updatePublicStatusRejectsMissingIsPublicFlag() {
        PublicStatusReqDTO req = new PublicStatusReqDTO();

        assertThatThrownBy(() -> savedItineraryCommandService.updatePublicStatus(1L, 2L, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("isPublic is required");

        verifyNoInteractions(savedItineraryRepository, savedItineraryCodec, communityCacheInvalidationService);
    }

    @Test
    void updatePublicStatusPersistsThemesAndShareNote() {
        SavedItinerary entity = new SavedItinerary();
        entity.setId(2L);
        entity.setUserId(1L);

        GenerateReqDTO originalReq = new GenerateReqDTO();
        originalReq.setThemes(List.of("Old"));

        ItineraryVO itinerary = new ItineraryVO();
        itinerary.setOriginalReq(originalReq);

        PublicStatusReqDTO req = new PublicStatusReqDTO();
        req.setIsPublic(true);
        req.setTitle("周末路线帖");
        req.setShareNote("适合第一次来的人");
        req.setCoverImageUrl("/community-covers/cover-food.svg");
        req.setThemes(List.of("Citywalk", "拍照"));

        when(savedItineraryRepository.requireOwnedForUpdate(1L, 2L)).thenReturn(entity);
        when(savedItineraryCodec.deserialize(entity)).thenReturn(itinerary);
        when(savedItineraryCodec.selectOptionInPlace(itinerary, null)).thenReturn(itinerary);
        when(savedItineraryCodec.writeJson(argThat(value -> value instanceof GenerateReqDTO generateReq
                && List.of("Citywalk", "拍照").equals(generateReq.getThemes())))).thenReturn("request-json");
        when(savedItineraryCodec.writeJson(same(itinerary))).thenReturn("itinerary-json");
        when(savedItineraryCodec.signature(itinerary)).thenReturn("route-signature");
        when(savedItineraryCodec.applyEntityMetadata(itinerary, entity)).thenReturn(itinerary);

        ItineraryVO result = savedItineraryCommandService.updatePublicStatus(1L, 2L, req);

        assertThat(result).isSameAs(itinerary);
        assertThat(itinerary.getCoverImageUrl()).isEqualTo("/community-covers/cover-food.svg");
        verify(savedItineraryRepository).saveOrUpdate(argThat(saved ->
                "周末路线帖".equals(saved.getCustomTitle())
                        && "适合第一次来的人".equals(saved.getShareNote())
                        && Integer.valueOf(1).equals(saved.getIsPublic())
                        && "request-json".equals(saved.getRequestJson())));
    }

    @Test
    void updatePublicStatusRejectsUnsupportedCoverImage() {
        SavedItinerary entity = new SavedItinerary();
        entity.setId(2L);
        entity.setUserId(1L);

        ItineraryVO itinerary = new ItineraryVO();

        PublicStatusReqDTO req = new PublicStatusReqDTO();
        req.setIsPublic(true);
        req.setCoverImageUrl("https://example.com/cover.jpg");

        when(savedItineraryRepository.requireOwnedForUpdate(1L, 2L)).thenReturn(entity);
        when(savedItineraryCodec.deserialize(entity)).thenReturn(itinerary);
        when(savedItineraryCodec.selectOptionInPlace(itinerary, null)).thenReturn(itinerary);

        assertThatThrownBy(() -> savedItineraryCommandService.updatePublicStatus(1L, 2L, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Unsupported cover image format");
    }
}
