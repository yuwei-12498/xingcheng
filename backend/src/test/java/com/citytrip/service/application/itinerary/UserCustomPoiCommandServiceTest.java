package com.citytrip.service.application.itinerary;

import com.citytrip.model.dto.ItineraryEditOperationDTO;
import com.citytrip.model.entity.UserCustomPoi;
import com.citytrip.service.geo.PlaceDisambiguationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserCustomPoiCommandServiceTest {

    @Mock
    private UserCustomPoiRepository userCustomPoiRepository;

    @Mock
    private PlaceDisambiguationService placeDisambiguationService;

    @InjectMocks
    private UserCustomPoiCommandService userCustomPoiCommandService;

    @Test
    void resolveForInsertion_shouldUseResolvedDraftWithoutAdditionalLookup() {
        ItineraryEditOperationDTO.CustomPoiDraft draft = new ItineraryEditOperationDTO.CustomPoiDraft();
        draft.setName("成都大学");
        draft.setRoughLocation("成洛大道2025");
        draft.setReason("聊天替换建议");
        draft.setCategory("高等教育");
        draft.setAddress("成洛大道2025");
        draft.setLatitude(new BigDecimal("30.650035"));
        draft.setLongitude(new BigDecimal("104.187516"));
        draft.setGeoSource("vivo-geo");

        ItineraryEditOperationDTO operation = new ItineraryEditOperationDTO();
        operation.setType("insert_inline_custom_poi");
        operation.setStayDuration(120);
        operation.setCustomPoiDraft(draft);

        when(userCustomPoiRepository.save(org.mockito.ArgumentMatchers.any(UserCustomPoi.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UserCustomPoi saved = userCustomPoiCommandService.resolveForInsertion(7L, "成都", operation);

        ArgumentCaptor<UserCustomPoi> captor = ArgumentCaptor.forClass(UserCustomPoi.class);
        verify(userCustomPoiRepository).save(captor.capture());
        verifyNoInteractions(placeDisambiguationService);

        UserCustomPoi entity = captor.getValue();
        assertThat(saved).isSameAs(entity);
        assertThat(entity.getUserId()).isEqualTo(7L);
        assertThat(entity.getCityName()).isEqualTo("成都");
        assertThat(entity.getName()).isEqualTo("成都大学");
        assertThat(entity.getAddress()).isEqualTo("成洛大道2025");
        assertThat(entity.getLatitude()).isEqualByComparingTo("30.650035");
        assertThat(entity.getLongitude()).isEqualByComparingTo("104.187516");
        assertThat(entity.getGeoSource()).isEqualTo("vivo-geo");
        assertThat(entity.getSuggestedStayDuration()).isEqualTo(120);
    }
}
